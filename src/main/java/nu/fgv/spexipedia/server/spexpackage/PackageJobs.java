/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nu.fgv.spexipedia.server.spexpackage;

import lombok.extern.slf4j.Slf4j;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.feed.SyncNotifier;
import nu.fgv.spexipedia.server.imports.ImportEngine;
import nu.fgv.spexipedia.server.imports.ImportEngine.ItemResult;
import nu.fgv.spexipedia.server.imports.ImportEngine.Outcome;
import nu.fgv.spexipedia.server.imports.ImportFileStore;
import nu.fgv.spexipedia.server.imports.ImportText;
import nu.fgv.spexipedia.server.imports.SpexipediaImport;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.spexpackage.PackagePlan.AppBatch;
import nu.fgv.spexipedia.server.spexpackage.PackageReport.AppReport;
import nu.fgv.spexipedia.server.spexpackage.PackageReport.Item;
import nu.fgv.spexipedia.server.spexpackage.PackageStore.PackageFile;
import nu.fgv.spexipedia.server.user.App;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * A package's preview and apply (content-import.md, 5.3), run in the background as its uploader.
 * <ul>
 *     <li>Preview: unpacks the zip, reads the spreadsheet, and runs every application's batch as a dry run. The other
 *     applications can only check a package whose spex and numbers Spexipedia already has; for a new spex they are
 *     checked when applied (DEFERRED).</li>
 *     <li>Apply: Spexipedia first; then, with its ids for the spex and the numbers, each other application: files
 *     first, then the records. An application learns of a new spex from Spexipedia's change feed, so a batch that
 *     fails is tried again a few times before the failure is reported. Applying again is safe: source keys.</li>
 * </ul>
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Component
public class PackageJobs {

    private static final PackageText NOT_CONFIGURED = PackageText.of("app.notConfigured");
    private static final Pattern NUMBER = Pattern.compile("^\\{\\{number:(.+)}}$");

    private final SpexPackageRepository repository;
    private final PackageStore store;
    private final PackageApps apps;
    private final SpexipediaImport spexipedia;
    private final ImportEngine engine;
    private final ImportFileStore importFiles;
    private final BaseDataService baseData;
    private final KindService kinds;
    private final SyncNotifier syncNotifier;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final int attempts;
    private final Duration retryInterval;
    private final TransactionTemplate rollback;

    @SuppressWarnings("java:S107")
    public PackageJobs(final SpexPackageRepository repository, final PackageStore store, final PackageApps apps,
                       final SpexipediaImport spexipedia, final ImportEngine engine, final ImportFileStore importFiles,
                       final BaseDataService baseData, final KindService kinds, final SyncNotifier syncNotifier,
                       final JsonMapper jsonMapper, final Clock clock, final PlatformTransactionManager transactionManager,
                       @Value("${spexipedia.imports.package-attempts:10}") final int attempts,
                       @Value("${spexipedia.imports.package-retry-interval:30s}") final Duration retryInterval) {
        this.repository = repository;
        this.store = store;
        this.apps = apps;
        this.spexipedia = spexipedia;
        this.engine = engine;
        this.importFiles = importFiles;
        this.baseData = baseData;
        this.kinds = kinds;
        this.syncNotifier = syncNotifier;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.attempts = Math.max(1, attempts);
        this.retryInterval = retryInterval;
        this.rollback = new TransactionTemplate(transactionManager);
    }

    public void preview(final UUID id) {
        run(id, true);
    }

    public void apply(final UUID id) {
        run(id, false);
    }

    private void run(final UUID id, final boolean dryRun) {
        final SpexPackage pkg = repository.findByUuid(id).orElseThrow();

        try {
            RunAs.uploader(pkg.getUploadedBy(), pkg.getUploadedByName(), () -> {
                work(id, dryRun);
                return null;
            });
        } catch (final RuntimeException e) {
            log.warn("Spex package {} failed", id, e);
            update(id, p -> {
                p.setState(PackageState.FAILED);
                p.setProgress(0);
                p.setMessage(encode(e instanceof PackageException failure ? failure.text()
                        : PackageText.of("failed.unexpected", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
            });
        }
    }

    private void work(final UUID id, final boolean dryRun) {
        progress(id, 1, PackageText.of("progress.unpacking"));

        final long zipLength = length(id);
        final List<PackageFile> files;

        try {
            files = store.unpack(id, read -> progress(id, (int) Math.min(40, 1 + 39 * read / Math.max(1, zipLength)), PackageText.of("progress.unpacking")));
        } catch (final IOException e) {
            throw new PackageException(PackageText.of("failed.unpack", String.valueOf(e.getMessage())), e);
        }
        progress(id, 40, PackageText.of("progress.reading"));

        final PackagePlan plan;

        try {
            plan = PackagePlan.of(PackageSheets.read(store.spreadsheet(id), kinds), files, baseData.spexAll(true), baseData.categories(true),
                    folder -> kinds.match(nu.fgv.spexipedia.server.kind.KindType.SECTION, null, folder)
                            .filter(section -> section.getContent() == nu.fgv.spexipedia.server.kind.SectionContent.IMAGES).isPresent());
        } catch (final IOException e) {
            throw new PackageException(PackageText.of("failed.spreadsheet", String.valueOf(e.getMessage())), e);
        }

        // Spexipedia's own files, then its batch
        for (final PackageFile file : plan.spexipediaFiles().values()) {
            try (InputStream in = Files.newInputStream(store.file(id, file.sha256()))) {
                importFiles.put(file.sha256(), in);
            } catch (final IOException e) {
                throw new PackageException(PackageText.of("failed.file", file.path()), e);
            }
        }
        progress(id, 45, PackageText.of("progress.app", "Spexipedia"));

        // A dry run in one transaction that is rolled back, so that the page, characters and numbers see the new spex
        // the same call would make
        final List<ItemResult> own = dryRun ? Objects.requireNonNull(rollback.execute(status -> {
            status.setRollbackOnly();
            return spexipedia.run(plan.spexipedia(), true);
        })) : spexipedia.run(plan.spexipedia(), false);
        final List<AppReport> reports = new ArrayList<>();
        final @Nullable UUID spexId = dryRun ? plan.existingSpexId() : own.stream().filter(r -> SpexipediaImport.SPEX.equals(r.kind()) && r.id() != null)
                .map(ItemResult::id).findFirst().orElse(plan.existingSpexId());
        final boolean ownFailed = own.stream().anyMatch(r -> r.outcome() == Outcome.FAILED);

        reports.add(report("SPEXIPEDIA", dryRun ? "CHECKED" : ownFailed ? "FAILED" : "APPLIED", own.size(),
                plan.spexipediaFiles().size(), own.stream().map(PackageJobs::item).toList(), null, null));

        // The ids the others refer to: the new ones only once applied
        final Map<String, String> ids = new HashMap<>();
        final @Nullable UUID spex = dryRun ? plan.existingSpexId() : spexId;

        if (spex != null) {
            ids.put(PackagePlan.SPEX, spex.toString());
            ids.put(PackagePlan.ORIGINAL, plan.originalId() != null ? plan.originalId().toString() : spex.toString());
        }
        plan.spexipedia().numbers().forEach(n -> {
            final @Nullable UUID number = engine.recordOf(SpexipediaImport.NUMBER, n.sourceKey());

            if (number != null) {
                ids.put(PackagePlan.number(n.sourceKey()), number.toString());
            }
        });
        if (!dryRun && !ownFailed) {
            syncNotifier.notifyConsumers();
        }

        int step = 0;

        for (final App app : PackageApps.APPS) {
            final AppBatch batch = plan.others().get(app);

            step++;
            if (batch == null) {
                continue;
            }
            progress(id, 50 + 50 * (step - 1) / PackageApps.APPS.size(), PackageText.of("progress.app", app.name().charAt(0) + app.name().substring(1).toLowerCase()));
            reports.add(other(id, app, batch, ids, dryRun || ownFailed, dryRun));
        }

        final List<PackageText> unmatched = new ArrayList<>(plan.unmatched());
        final PackageReport report = new PackageReport(reports, unmatched, plan.warnings());
        // An application that is not deployed never takes its part; one that did not answer may, when applied again
        final boolean failed = reports.stream().anyMatch(r -> "FAILED".equals(r.status())
                || "UNAVAILABLE".equals(r.status()) && !NOT_CONFIGURED.equals(r.message()));
        final String json = jsonMapper.writeValueAsString(report);

        update(id, p -> {
            p.setSpexId(spex);
            p.setProgress(100);
            if (dryRun) {
                p.setPreview(json);
                p.setState(PackageState.PREVIEWED);
                p.setMessage(null);
            } else {
                p.setResults(json);
                p.setState(failed ? PackageState.FAILED : PackageState.APPLIED);
                p.setMessage(failed ? PackageText.of("failed.someRecords").encode() : null);
                p.setAppliedAt(failed ? p.getAppliedAt() : clock.instant());
            }
        });
    }

    /**
     * One other application's batch: checked, applied, deferred or unavailable.
     */
    private AppReport other(final UUID id, final App app, final AppBatch batch, final Map<String, String> ids, final boolean onlyCheck, final boolean dryRun) {
        final String web = apps.webUrl(app);

        if (!apps.configured(app)) {
            return report(app.name(), "UNAVAILABLE", batch.records(), batch.files().size(), List.of(), NOT_CONFIGURED, web);
        }

        final List<String> missing = new ArrayList<>();
        final List<Map<String, Object>> parts = batch.parts(ImportEngine.MAX_RECORDS).stream().map(p -> resolve(p, ids, missing)).toList();

        if (!missing.isEmpty() && (onlyCheck || missing.contains(PackagePlan.SPEX))) {
            return report(app.name(), onlyCheck && dryRun ? "DEFERRED" : "FAILED", batch.records(), batch.files().size(), List.of(),
                    PackageText.of(dryRun ? "app.deferred" : "app.missingRecords"), web);
        }

        Exception last = null;

        for (int attempt = 1; attempt <= (dryRun ? 1 : attempts); attempt++) {
            try {
                for (final PackageFile file : batch.files().values()) {
                    apps.upload(app, file.sha256(), store.file(id, file.sha256()));
                }

                final List<Item> items = new ArrayList<>();

                for (final Map<String, Object> part : parts) {
                    items.addAll(apps.importBatch(app, withKinds(part), dryRun).items());
                }

                final boolean failed = items.stream().anyMatch(i -> "FAILED".equals(i.outcome()));

                if (!failed || dryRun || attempt == attempts) {
                    return report(app.name(), failed ? "FAILED" : dryRun ? "CHECKED" : "APPLIED", batch.records(), batch.files().size(),
                            dedupe(items), missing.isEmpty() ? null : PackageText.of("app.leftOut"), web);
                }
            } catch (final RuntimeException e) {
                last = e;
                log.info("{} did not take the package yet (attempt {}): {}", app, attempt, e.getMessage());
            }
            sleep();
        }
        return report(app.name(), "UNAVAILABLE", batch.records(), batch.files().size(), List.of(),
                last == null ? PackageText.of("app.notAnswering") : PackageText.of("app.error", truncate(last.getMessage())), web);
    }

    private static Map<String, Object> withKinds(final Map<String, Object> part) {
        final Map<String, Object> input = new LinkedHashMap<>(part);

        input.put("createMissingKinds", false);
        return input;
    }

    /**
     * The part with the placeholders filled in; a track without its number keeps going, any other record waits.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> resolve(final Map<String, Object> part, final Map<String, String> ids, final List<String> missing) {
        final Map<String, Object> out = new LinkedHashMap<>();

        part.forEach((list, records) -> {
            final List<Map<String, Object>> kept = new ArrayList<>();

            for (final Map<String, Object> record : (List<Map<String, Object>>) records) {
                final List<String> absent = new ArrayList<>();
                final Map<String, Object> resolved = (Map<String, Object>) fill(record, ids, absent);

                if (absent.isEmpty()) {
                    kept.add(resolved);
                } else if (absent.stream().allMatch(a -> NUMBER.matcher(a).matches()) && resolved.get("numberId") instanceof String n
                        && NUMBER.matcher(n).matches() && "tracks".equals(list)) {
                    resolved.remove("numberId");
                    kept.add(resolved);
                } else {
                    missing.addAll(absent);
                }
            }
            if (!kept.isEmpty()) {
                out.put(list, kept);
            }
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object fill(final Object value, final Map<String, String> ids, final List<String> absent) {
        if (value instanceof String text && text.startsWith("{{") && text.endsWith("}}")) {
            final String id = ids.get(text);

            if (id == null) {
                absent.add(text);
                return text;
            }
            return id;
        }
        if (value instanceof Map<?, ?> map) {
            final Map<String, Object> out = new LinkedHashMap<>();

            ((Map<String, Object>) map).forEach((k, v) -> out.put(k, fill(v, ids, absent)));
            return out;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(v -> fill(v, ids, absent)).toList();
        }
        return value;
    }

    private static List<Item> dedupe(final List<Item> items) {
        // Parents are sent with every part: keep the first outcome of each
        final Map<String, Item> first = new LinkedHashMap<>();

        items.forEach(item -> first.putIfAbsent(item.kind() + "\t" + item.sourceKey(), item));
        return new ArrayList<>(first.values());
    }

    private static Item item(final ItemResult result) {
        return new Item(result.kind(), result.sourceKey(), result.id() == null ? null : result.id().toString(), result.outcome().name(),
                result.message() == null ? null : text(result.message()), result.warnings().stream().map(PackageJobs::text).toList());
    }

    /**
     * Spexipedia's own import texts are its message codes too.
     */
    private static PackageText text(final ImportText text) {
        return new PackageText(text.code(), text.args());
    }

    private static AppReport report(final String app, final String status, final int records, final int files, final List<Item> items,
                                    final @Nullable PackageText message, final @Nullable String web) {
        final Map<String, Integer> counts = new HashMap<>();

        items.forEach(item -> counts.merge(item.outcome(), 1, Integer::sum));
        return new AppReport(app, status, records, files, counts.getOrDefault("CREATED", 0), counts.getOrDefault("UPDATED", 0),
                counts.getOrDefault("UNCHANGED", 0), counts.getOrDefault("KEPT", 0), counts.getOrDefault("FAILED", 0), message, web, items);
    }

    private long length(final UUID id) {
        try {
            return store.zipLength(id);
        } catch (final IOException e) {
            return 0;
        }
    }

    private void sleep() {
        try {
            Thread.sleep(retryInterval);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    private void progress(final UUID id, final int progress, final PackageText message) {
        update(id, p -> {
            p.setProgress(progress);
            p.setMessage(encode(message));
        });
    }

    private void update(final UUID id, final Consumer<SpexPackage> change) {
        repository.findByUuid(id).ifPresent(p -> {
            change.accept(p);
            repository.save(p);
        });
    }

    /**
     * For the message column: arguments such as an exception's message are cut short to fit.
     */
    private static String encode(final PackageText text) {
        final String encoded = text.encode();

        return encoded.length() <= 1000 ? encoded
                : new PackageText(text.code(), text.args().stream().map(PackageJobs::truncate).map(a -> a.length() > 200 ? a.substring(0, 200) + "…" : a).toList()).encode();
    }

    private static String truncate(final @Nullable String text) {
        return text == null ? "" : text.length() > 990 ? text.substring(0, 990) + "…" : text;
    }
}
