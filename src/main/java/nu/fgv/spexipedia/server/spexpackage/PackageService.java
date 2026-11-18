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
import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.kind.KindType;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spex packages (spec 5.14): an editor downloads the template, uploads a spreadsheet and optionally a zip in parts,
 * previews it and applies it. Previews and applies run one at a time in the background ({@link PackageJobs}); a
 * package is deleted with its files {@code keepPackages} after it was applied.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Service
public class PackageService {

    private final SpexPackageRepository repository;
    private final PackageStore store;
    private final PackageJobs jobs;
    private final CallerService callerService;
    private final KindService kinds;
    private final BaseDataService baseData;
    private final AuditService auditService;
    private final SpexipediaProperties properties;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("spex-package").daemon().factory());

    @SuppressWarnings("java:S107")
    public PackageService(final SpexPackageRepository repository, final PackageStore store, final PackageJobs jobs,
                          final CallerService callerService, final KindService kinds, final BaseDataService baseData,
                          final AuditService auditService, final SpexipediaProperties properties, final JsonMapper jsonMapper,
                          final Clock clock) {
        this.repository = repository;
        this.store = store;
        this.jobs = jobs;
        this.callerService = callerService;
        this.kinds = kinds;
        this.baseData = baseData;
        this.auditService = auditService;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** The template, made from the kinds and categories as they are now. EDITOR. */
    @Transactional(readOnly = true)
    public byte[] template() throws IOException {
        callerService.current().requireEditor();
        return PackageTemplate.create(baseData.categories(false), type -> kinds.kinds(type, null, false),
                use -> kinds.kinds(KindType.IMAGE, use, false));
    }

    /**
     * A new package from its spreadsheet; {@code zipFileName} and {@code zipSize} announce a zip that is to follow in
     * parts. EDITOR.
     */
    @Transactional
    public SpexPackage create(final String fileName, final InputStream spreadsheet, final @Nullable String zipFileName,
                              final @Nullable Long zipSize) throws IOException {
        final Caller caller = callerService.current();

        caller.requireEditor();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BadRequestException("error.package.notXlsx");
        }
        if (zipSize != null && (zipSize <= 0 || zipSize > properties.imports().maxPackageSize().toBytes())) {
            throw new BadRequestException("error.package.tooLarge");
        }

        final SpexPackage pkg = new SpexPackage();

        pkg.setFileName(fileName);
        pkg.setZipFileName(zipSize == null ? null : zipFileName);
        pkg.setZipSize(zipSize);
        pkg.setUploadedBy(caller.subject());
        pkg.setUploadedByName(caller.displayName());
        store.saveSpreadsheet(pkg.getUuid(), spreadsheet);
        try {
            PackageSheets.read(store.spreadsheet(pkg.getUuid()), kinds);
        } catch (final IOException | RuntimeException e) {
            store.delete(pkg.getUuid());
            throw new BadRequestException("error.package.unreadable");
        }
        repository.save(pkg);
        auditService.record("CREATE", "SPEX_PACKAGE", pkg.getUuid(), "Laddade upp spexpaketet " + fileName);
        return pkg;
    }

    /** A part of the zip, at the offset where the parts so far end; returns how much has arrived. EDITOR. */
    @Transactional
    public long appendZip(final UUID id, final long offset, final InputStream content) throws IOException {
        callerService.current().requireEditor();

        final SpexPackage pkg = require(id);

        if (pkg.getZipSize() == null || pkg.getState() != PackageState.UPLOADED) {
            throw new BadRequestException("error.package.noZipExpected");
        }

        final long received = store.appendZip(id, offset, content, pkg.getZipSize());

        pkg.setZipReceived(received);
        return received;
    }

    /** How much of the zip has arrived, to resume an upload. EDITOR. */
    @Transactional(readOnly = true)
    public long zipReceived(final UUID id) throws IOException {
        callerService.current().requireEditor();
        require(id);
        return store.zipLength(id);
    }

    @Transactional(readOnly = true)
    public List<SpexPackage> packages() {
        callerService.current().requireEditor();
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public @Nullable SpexPackage spexPackage(final UUID id) {
        callerService.current().requireEditor();
        return repository.findByUuid(id).orElse(null);
    }

    /** Starts the preview: dry runs in every application. EDITOR. */
    @Transactional
    public SpexPackage preview(final UUID id) {
        return start(id, PackageState.PREVIEWING, false);
    }

    /** Starts applying it: Spexipedia first, then the others. Again after a failure. EDITOR. */
    @Transactional
    public SpexPackage apply(final UUID id) {
        return start(id, PackageState.APPLYING, true);
    }

    @Transactional
    public void delete(final UUID id) {
        callerService.current().requireEditor();

        final SpexPackage pkg = require(id);

        if (pkg.getState().running()) {
            throw new BadRequestException("error.package.running");
        }
        repository.delete(pkg);
        store.delete(id);
        auditService.record("DELETE", "SPEX_PACKAGE", id, "Tog bort spexpaketet " + pkg.getFileName());
    }

    public PackageReport report(final @Nullable String json) {
        return json == null ? new PackageReport(List.of(), List.of(), List.of()) : jsonMapper.readValue(json, PackageReport.class);
    }

    /** Deletes the packages applied longer ago than {@code keepPackages}, with their files. */
    @Transactional
    public void cleanUp() {
        for (final SpexPackage pkg : repository.findByAppliedAtBefore(clock.instant().minus(properties.imports().keepPackages()))) {
            store.delete(pkg.getUuid());
            repository.delete(pkg);
            log.info("Deleted the spex package {}, applied {}", pkg.getUuid(), pkg.getAppliedAt());
        }
    }

    /** A preview or apply that a restart interrupted has failed; it can be started again. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterrupted() {
        repository.findByStateIn(List.of(PackageState.PREVIEWING, PackageState.APPLYING)).forEach(pkg -> {
            pkg.setState(PackageState.FAILED);
            pkg.setMessage("Interrupted by a restart; start it again");
        });
    }

    private SpexPackage start(final UUID id, final PackageState state, final boolean apply) {
        callerService.current().requireEditor();

        final SpexPackage pkg = require(id);

        if (pkg.getState().running()) {
            throw new BadRequestException("error.package.running");
        }
        if (!pkg.zipComplete()) {
            throw new BadRequestException("error.package.zipIncomplete");
        }
        if (apply && pkg.getState() == PackageState.UPLOADED) {
            throw new BadRequestException("error.package.previewFirst");
        }
        pkg.setState(state);
        pkg.setProgress(0);
        pkg.setMessage(null);
        repository.saveAndFlush(pkg);
        auditService.record(apply ? "APPLY" : "PREVIEW", "SPEX_PACKAGE", id, (apply ? "Tillämpade" : "Förhandsgranskade")
                + " spexpaketet " + pkg.getFileName());
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        executor.submit(() -> {
                            if (apply) {
                                jobs.apply(id);
                            } else {
                                jobs.preview(id);
                            }
                        });
                    }
                });
        return pkg;
    }

    private SpexPackage require(final UUID id) {
        return repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(SpexPackage.class, id));
    }
}
