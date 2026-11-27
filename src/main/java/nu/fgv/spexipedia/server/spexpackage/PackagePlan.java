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

import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexCategoryDto;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.imports.ImportRecords;
import nu.fgv.spexipedia.server.imports.ImportRecords.FileRef;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.spexpackage.PackageSheets.CharacterRow;
import nu.fgv.spexipedia.server.spexpackage.PackageSheets.Content;
import nu.fgv.spexipedia.server.spexpackage.PackageSheets.NumberRow;
import nu.fgv.spexipedia.server.spexpackage.PackageStore.PackageFile;
import nu.fgv.spexipedia.server.user.App;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a package becomes (content-import.md, 5): Spexipedia's batch, and one for each of the other applications in
 * their import APIs' own shape. The others refer to the spex and its numbers by placeholders ({@link #SPEX},
 * {@link #ORIGINAL}, {@link #number(String)}), filled in with Spexipedia's ids before they are sent. Source keys are
 * by spex, not by package, so a corrected package updates what an earlier one made.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class PackagePlan {

    public static final String SPEX = "{{spex}}";
    /**
     * The original spex: Spexarkivet files a revival's documents under it.
     */
    public static final String ORIGINAL = "{{original}}";

    private static final Pattern LEADING = Pattern.compile("^\\s*(\\d{1,3})\\b[\\s.\\-_)]*(.*)$");
    private static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> AUDIO = Set.of("mp3", "flac", "wav", "m4a", "aac", "ogg", "aif", "aiff");
    private static final Set<String> VIDEO = Set.of("mp4", "mov", "m4v", "mkv", "avi", "mts", "mpg", "mpeg", "wmv");
    private static final Set<String> PHOTOS = Set.of("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "tif", "tiff",
            "dng", "cr2", "cr3", "nef", "arw", "mp4", "mov", "m4v");

    /**
     * One application's records: lists in the order they are sent; {@code parents} are the lists the others refer
     * to, sent again with every part when the records are more than one call takes.
     */
    public record AppBatch(Map<String, List<Map<String, Object>>> lists, Set<String> parents,
                           Map<String, PackageFile> files) {

        public int records() {
            return lists.values().stream().mapToInt(List::size).sum();
        }

        /**
         * The calls to make: each at most {@code max} records, the parents in every one.
         */
        public List<Map<String, Object>> parts(final int max) {
            final Map<String, Object> base = new LinkedHashMap<>();
            final List<Map.Entry<String, Map<String, Object>>> children = new ArrayList<>();

            lists.forEach((list, records) -> {
                if (parents.contains(list)) {
                    base.put(list, records);
                } else {
                    records.forEach(record -> children.add(Map.entry(list, record)));
                }
            });

            final int room = Math.max(1, max - base.values().stream().mapToInt(v -> ((List<?>) v).size()).sum());
            final List<Map<String, Object>> parts = new ArrayList<>();

            for (int i = 0; i == 0 || i < children.size(); i += room) {
                final Map<String, Object> part = new LinkedHashMap<>(base);

                for (final Map.Entry<String, Map<String, Object>> child : children.subList(i, Math.min(children.size(), i + room))) {
                    @SuppressWarnings("unchecked") final List<Map<String, Object>> list =
                            (List<Map<String, Object>>) part.computeIfAbsent(child.getKey(), k -> new ArrayList<Map<String, Object>>());

                    list.add(child.getValue());
                }
                parts.add(part);
            }
            return parts;
        }
    }

    private final String spexKey;
    private final @Nullable UUID existingSpexId;
    private final @Nullable UUID originalId;
    private final ImportRecords.Batch spexipedia;
    private final Map<String, PackageFile> spexipediaFiles;
    private final Map<App, AppBatch> others;
    private final List<PackageText> unmatched;
    private final List<PackageText> warnings;

    private PackagePlan(final String spexKey, final @Nullable UUID existingSpexId, final @Nullable UUID originalId,
                        final ImportRecords.Batch spexipedia, final Map<String, PackageFile> spexipediaFiles,
                        final Map<App, AppBatch> others, final List<PackageText> unmatched, final List<PackageText> warnings) {
        this.spexKey = spexKey;
        this.existingSpexId = existingSpexId;
        this.originalId = originalId;
        this.spexipedia = spexipedia;
        this.spexipediaFiles = spexipediaFiles;
        this.others = others;
        this.unmatched = unmatched;
        this.warnings = warnings;
    }

    public static String number(final String sourceKey) {
        return "{{number:" + sourceKey + "}}";
    }

    public String spexSourceKey() {
        return "package:" + spexKey;
    }

    public @Nullable UUID existingSpexId() {
        return existingSpexId;
    }

    public @Nullable UUID originalId() {
        return originalId;
    }

    public ImportRecords.Batch spexipedia() {
        return spexipedia;
    }

    public Map<String, PackageFile> spexipediaFiles() {
        return spexipediaFiles;
    }

    public Map<App, AppBatch> others() {
        return others;
    }

    public List<PackageText> unmatched() {
        return unmatched;
    }

    public List<PackageText> warnings() {
        return warnings;
    }

    /**
     * The plan for the content and the zip's files. {@code spex} and {@code categories} are Spexipedia's, to find
     * the spex the package adds to and a revival's original.
     *
     * @param imageSection whether a top folder names an image section (by its names and aliases), whose images it holds
     */
    public static PackagePlan of(final Content content, final List<PackageFile> files, final List<SpexDto> spex,
                                 final List<SpexCategoryDto> categories, final java.util.function.Predicate<String> imageSection) {
        return new Builder(content, files, spex, categories, imageSection).build();
    }

    private static final class Builder {

        private final Content content;
        private final List<PackageFile> files;
        private final List<SpexDto> allSpex;
        private final List<SpexCategoryDto> categories;
        private final java.util.function.Predicate<String> imageSection;
        private final List<PackageText> unmatched = new ArrayList<>();
        private final List<PackageText> warnings = new ArrayList<>();
        private final Map<String, PackageFile> spexipediaFiles = new LinkedHashMap<>();
        private final Map<App, AppBatch> others = new EnumMap<>(App.class);
        private final Map<Integer, String> numberKeys = new LinkedHashMap<>();
        private String spexKey = "";
        private String key = "";
        private String display = "";

        Builder(final Content content, final List<PackageFile> files, final List<SpexDto> spex, final List<SpexCategoryDto> categories,
                final java.util.function.Predicate<String> imageSection) {
            this.imageSection = imageSection;
            this.content = content;
            this.files = files;
            this.allSpex = spex;
            this.categories = categories;
            this.warnings.addAll(content.warnings());
        }

        PackagePlan build() {
            final String year = content.year();
            final String parentText = content.spex().get("parent");
            final @Nullable SpexDto parent = parentText == null ? null : original(parentText);

            if (parentText != null && parent == null) {
                warnings.add(PackageText.of("warning.parent", parentText));
            }

            final String title = parent != null ? parent.title() : content.title();

            if (year.isBlank() || title.isBlank() && parent == null) {
                throw new PackageException(PackageText.of("failed.titleAndYear"));
            }
            spexKey = KindService.normalize(year + " " + title).replace(' ', '-') + (parent != null ? "-revival" : "");
            key = "package:" + spexKey;
            display = title + " " + year;

            final Optional<SpexDto> existing = allSpex.stream()
                    .filter(s -> s.year().equals(year) && (parent != null ? parent.key() == (s.parentKey() == null ? -1 : s.parentKey())
                            : s.parentKey() == null && KindService.normalize(s.title()).equals(KindService.normalize(title))))
                    .findFirst();
            final @Nullable String category = content.spex().get("category");

            if (category != null && categories.stream().noneMatch(c -> KindService.normalize(c.name()).equals(KindService.normalize(category)))) {
                warnings.add(PackageText.of("warning.category", category));
            }

            final ImportRecords.ImportSpex spexRecord = new ImportRecords.ImportSpex(key, existing.map(SpexDto::id).orElse(null),
                    parent == null ? title : null, year, parent == null ? category : null, parent == null ? null : parent.id(), null, null);
            final List<ImportRecords.ImportCharacter> characters = characters();
            final List<ImportRecords.ImportNumber> numbers = numbers();
            final List<ImportRecords.ImportScene> scenes = scenes();
            final PageFiles page = new PageFiles();

            placeFiles(page, characters);

            final Map<String, String> fields = content.spex();
            final ImportRecords.ImportPage pageRecord = new ImportRecords.ImportPage(key + ":page", null, key,
                    fields.get("premiere"), fields.get("introduction"), fields.get("shortDescription"),
                    fields.get("description"), fields.get("synopsis"), null, page.poster, page.rebus, page.rebusSolution,
                    content.facts().entrySet().stream().map(f -> new ImportRecords.ImportFact(f.getKey(), f.getValue())).toList(),
                    page.images);
            final ImportRecords.Batch batch = new ImportRecords.Batch(false, List.of(spexRecord), List.of(pageRecord),
                    page.characters(characters), scenes, numbers, null, null, null);

            return new PackagePlan(spexKey, existing.map(SpexDto::id).orElse(null), parent == null ? null : parent.id(), batch,
                    spexipediaFiles, others, unmatched, warnings);
        }

        private @Nullable SpexDto original(final String text) {
            final Matcher m = Pattern.compile("^((?:19|20)\\d\\d)\\s+(.+)$").matcher(text.strip());
            final List<SpexDto> originals = allSpex.stream().filter(s -> s.parentKey() == null).toList();

            if (m.matches()) {
                return originals.stream().filter(s -> s.year().equals(m.group(1))
                        && KindService.normalize(s.title()).equals(KindService.normalize(m.group(2)))).findFirst().orElse(null);
            }

            final List<SpexDto> named = originals.stream().filter(s -> KindService.normalize(s.title()).equals(KindService.normalize(text))).toList();

            return named.size() == 1 ? named.getFirst() : null;
        }

        private List<ImportRecords.ImportCharacter> characters() {
            final List<ImportRecords.ImportCharacter> characters = new ArrayList<>();

            for (int i = 0; i < content.characters().size(); i++) {
                final CharacterRow row = content.characters().get(i);

                characters.add(new ImportRecords.ImportCharacter(key + ":character:" + KindService.normalize(row.name()).replace(' ', '-'),
                        null, key, row.name(), row.description(), i + 1, null));
            }
            return characters;
        }

        private List<ImportRecords.ImportScene> scenes() {
            final List<ImportRecords.ImportScene> scenes = new ArrayList<>();
            final Map<Integer, Integer> positions = new LinkedHashMap<>();

            for (final PackageSheets.SceneRow row : content.scenes()) {
                final int act = row.act() == null ? 1 : row.act();
                final int position = positions.merge(act, 1, Integer::sum);

                scenes.add(new ImportRecords.ImportScene(key + ":scene:" + act + ":" + position, null, key, act, position,
                        row.title(), row.place(), row.intervalAfter() ? Boolean.TRUE : null));
            }
            return scenes;
        }

        private List<ImportRecords.ImportNumber> numbers() {
            final List<ImportRecords.ImportNumber> numbers = new ArrayList<>();

            for (int i = 0; i < content.numbers().size(); i++) {
                final NumberRow row = content.numbers().get(i);
                final int position = row.position() == null ? i + 1 : row.position();
                final String sourceKey = key + ":number:" + (row.act() == null ? "" : row.act() + ":") + position;
                final List<ImportRecords.ImportCredit> credits = new ArrayList<>();

                row.credits().forEach((kind, names) -> names.forEach(name -> credits.add(new ImportRecords.ImportCredit(kind, name, null))));
                numberKeys.put(i, sourceKey);
                numbers.add(new ImportRecords.ImportNumber(sourceKey, null, key, row.act(), position, row.title(), row.kind(),
                        row.fields().get("originalTitle"), row.fields().get("originalArtist"), row.fields().get("fromWork"),
                        row.fields().get("firstLine"), credits, row.characters()));
            }
            return numbers;
        }

        /**
         * The number a folder or file names: by its leading number (the position) or its title.
         */
        private Optional<String> numberFor(final String name) {
            final Matcher m = LEADING.matcher(name);
            final String title = KindService.normalize(m.matches() ? m.group(2) : name);
            final List<NumberRow> rows = content.numbers();

            for (int i = 0; i < rows.size(); i++) {
                final int position = rows.get(i).position() == null ? i + 1 : rows.get(i).position();

                if (m.matches() && Integer.parseInt(m.group(1)) == position) {
                    return Optional.of(numberKeys.get(i));
                }
            }
            for (int i = 0; i < rows.size(); i++) {
                final String own = KindService.normalize(rows.get(i).title());

                if (!title.isEmpty() && (own.equals(title) || own.length() > 3 && title.contains(own))) {
                    return Optional.of(numberKeys.get(i));
                }
            }
            return Optional.empty();
        }

        /**
         * Spexipedia's own images of the page and the characters.
         */
        private final class PageFiles {

            @Nullable FileRef poster;
            @Nullable FileRef rebus;
            @Nullable FileRef rebusSolution;
            final List<ImportRecords.ImportPageImage> images = new ArrayList<>();
            final Map<String, List<ImportRecords.ImportCharacterImage>> characterImages = new LinkedHashMap<>();

            List<ImportRecords.ImportCharacter> characters(final List<ImportRecords.ImportCharacter> characters) {
                return characters.stream().map(c -> new ImportRecords.ImportCharacter(c.sourceKey(), c.spexId(), c.spexSourceKey(),
                        c.name(), c.description(), c.position(), characterImages.get(c.sourceKey()))).toList();
            }
        }

        private void placeFiles(final PageFiles page, final List<ImportRecords.ImportCharacter> characters) {
            final Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
            final Map<String, Map<String, Object>> arrangements = new LinkedHashMap<>();
            final AppLists archive = new AppLists(Set.of("documents", "arrangements"), "documents", "documentVersions", "arrangements", "arrangementVersions");
            final AppLists spexify = new AppLists(Set.of("albums"), "albums", "tracks");
            final AppLists spexflix = new AppLists(Set.of(), "videos");
            final AppLists spexgram = new AppLists(Set.of("albums"), "albums", "items");

            for (final PackageFile file : files) {
                final List<String> parts = List.of(file.path().split("/"));
                final String top = parts.size() > 1 ? PackageColumns.field(PackageColumns.FOLDERS, parts.getFirst()) : null;
                final String extension = extension(file.name());
                final FileRef ref = new FileRef(file.sha256(), file.name());

                if (parts.size() == 1) {
                    unmatched.add(PackageText.of("unmatched.top", file.path()));
                    continue;
                }
                if (top == null && imageSection.test(parts.getFirst())) {
                    // An image section, e.g. Scenografi/Fond/: the section and the image kind by name
                    if (!IMAGES.contains(extension)) {
                        unmatched.add(PackageText.of("unmatched.notImage", file.path()));
                        continue;
                    }
                    spexipediaFiles.put(file.sha256(), file);
                    page.images.add(new ImportRecords.ImportPageImage(parts.getFirst(), parts.size() > 2 ? parts.get(1) : null, ref, null));
                    continue;
                }
                if (top == null) {
                    // A document kind of Spexarkivet: one document of the kind, its files one version
                    final String kind = parts.getFirst();
                    final String documentKey = key + ":document:" + KindService.normalize(kind).replace(' ', '-');
                    final Map<String, Object> version = documents.computeIfAbsent(documentKey, k -> {
                        archive.add("documents", record("sourceKey", k, "spexId", ORIGINAL, "kind", kind, "title", kind + " – " + display));
                        return archive.add("documentVersions", record("sourceKey", k + ":original", "parentSourceKey", k, "label", "Original",
                                "usedInSpexId", SPEX, "files", new ArrayList<Map<String, Object>>()));
                    });

                    files(version).add(record("file", fileRef(file), "title", base(file.name())));
                    archive.file(file);
                    continue;
                }
                switch (top) {
                    case "poster", "rebus", "vest" -> {
                        if (!IMAGES.contains(extension)) {
                            unmatched.add(PackageText.of("unmatched.notImage", file.path()));
                            continue;
                        }
                        spexipediaFiles.put(file.sha256(), file);
                        if ("poster".equals(top) && page.poster == null) {
                            page.poster = ref;
                        } else if ("rebus".equals(top) && parts.size() > 2 && PackageColumns.SOLUTION.stream()
                                .anyMatch(s -> KindService.normalize(s).equals(KindService.normalize(parts.get(1))))) {
                            page.rebusSolution = ref;
                        } else if ("rebus".equals(top)) {
                            page.rebus = ref;
                        } else if ("vest".equals(top)) {
                            page.images.add(new ImportRecords.ImportPageImage("VEST", null, ref, null));
                        } else {
                            unmatched.add(PackageText.of("unmatched.onePoster", file.path()));
                            spexipediaFiles.remove(file.sha256());
                        }
                    }
                    case "characters" -> {
                        final Optional<ImportRecords.ImportCharacter> character = parts.size() > 2 ? characterFor(characters, parts.get(1)) : Optional.empty();

                        if (character.isEmpty() || !IMAGES.contains(extension)) {
                            unmatched.add(character.isEmpty() ? parts.size() > 2 ? PackageText.of("unmatched.noCharacter", file.path(), parts.get(1))
                                    : PackageText.of("unmatched.characterFolder", file.path())
                                    : PackageText.of("unmatched.notImage", file.path()));
                            continue;
                        }
                        spexipediaFiles.put(file.sha256(), file);
                        page.characterImages.computeIfAbsent(character.get().sourceKey(), k -> new ArrayList<>())
                                .add(new ImportRecords.ImportCharacterImage(parts.size() > 3 ? parts.get(2) : null, ref, null));
                    }
                    case "arrangements" -> {
                        final Optional<String> number = parts.size() > 2 ? numberFor(parts.get(1)) : Optional.empty();

                        if (number.isEmpty()) {
                            unmatched.add(parts.size() > 2 ? PackageText.of("unmatched.noNumber", file.path(), parts.get(1))
                                    : PackageText.of("unmatched.numberFolder", file.path()));
                            continue;
                        }

                        final String arrangementKey = key + ":arrangement:" + number.get().substring(number.get().lastIndexOf(":number:") + 8);
                        final Map<String, Object> version = arrangements.computeIfAbsent(arrangementKey, k -> {
                            archive.add("arrangements", record("sourceKey", k, "numberId", number(number.get())));
                            return archive.add("arrangementVersions", record("sourceKey", k + ":original", "parentSourceKey", k,
                                    "label", "Original", "usedInSpexId", SPEX, "files", new ArrayList<Map<String, Object>>()));
                        });

                        files(version).add(record("file", fileRef(file), "title", base(file.name())));
                        archive.file(file);
                    }
                    case "recordings" -> {
                        if (!AUDIO.contains(extension)) {
                            unmatched.add(PackageText.of("unmatched.notAudio", file.path()));
                            continue;
                        }

                        final String album = key + ":album";

                        if (spexify.lists.get("albums").isEmpty()) {
                            spexify.add("albums", record("sourceKey", album, "spexIds", List.of(SPEX), "title", display, "year", year()));
                        }

                        final Matcher m = LEADING.matcher(base(file.name()));
                        final Map<String, Object> track = record("sourceKey", key + ":track:" + KindService.normalize(file.path()).replace(' ', '-'),
                                "albumSourceKey", album, "trackNumber", m.matches() ? Integer.valueOf(m.group(1)) : null,
                                "title", m.matches() && !m.group(2).isBlank() ? m.group(2) : base(file.name()), "file", fileRef(file));

                        numberFor(base(file.name())).ifPresent(n -> track.put("numberId", number(n)));
                        spexify.add("tracks", track);
                        spexify.file(file);
                    }
                    case "videos" -> {
                        if (!VIDEO.contains(extension)) {
                            unmatched.add(PackageText.of("unmatched.notVideo", file.path()));
                            continue;
                        }
                        spexflix.add("videos", record("sourceKey", key + ":video:" + KindService.normalize(file.path()).replace(' ', '-'),
                                "spexIds", List.of(SPEX), "kind", parts.size() > 2 ? parts.get(1) : null, "title", base(file.name()),
                                "year", year(), "file", fileRef(file)));
                        spexflix.file(file);
                    }
                    case "photos" -> {
                        if (!PHOTOS.contains(extension)) {
                            unmatched.add(PackageText.of("unmatched.notPhoto", file.path()));
                            continue;
                        }

                        final String kind = parts.size() > 2 ? parts.get(1) : null;
                        final String albumTitle = parts.size() > 3 ? parts.get(2) : kind != null ? kind : "Foton";
                        final String album = key + ":photos:" + KindService.normalize((kind == null ? "" : kind + " ") + albumTitle).replace(' ', '-');

                        if (spexgram.lists.get("albums").stream().noneMatch(a -> album.equals(a.get("sourceKey")))) {
                            spexgram.add("albums", record("sourceKey", album, "spexIds", List.of(SPEX), "kind", kind,
                                    "title", albumTitle + " – " + display));
                        }
                        spexgram.add("items", record("sourceKey", key + ":photo:" + KindService.normalize(file.path()).replace(' ', '-'),
                                "albumSourceKey", album, "file", fileRef(file)));
                        spexgram.file(file);
                    }
                    default -> unmatched.add(PackageText.of("unmatched.nowhere", file.path()));
                }
            }
            archive.into(App.ARCHIVE);
            spexify.into(App.SPEXIFY);
            spexflix.into(App.SPEXFLIX);
            spexgram.into(App.SPEXGRAM);
        }

        private @Nullable Integer year() {
            return content.year().matches("\\d{4}") ? Integer.valueOf(content.year()) : null;
        }

        private Optional<ImportRecords.ImportCharacter> characterFor(final List<ImportRecords.ImportCharacter> characters, final String name) {
            final Matcher m = LEADING.matcher(name);

            return characters.stream().filter(c -> KindService.normalize(c.name()).equals(KindService.normalize(name))
                    || m.matches() && m.group(2).isBlank() && c.position() != null && c.position() == Integer.parseInt(m.group(1))).findFirst();
        }

        /**
         * An application's lists while they are filled.
         */
        private final class AppLists {

            final Map<String, List<Map<String, Object>>> lists = new LinkedHashMap<>();
            final Set<String> parents;
            final Map<String, PackageFile> appFiles = new LinkedHashMap<>();

            AppLists(final Set<String> parents, final String... names) {
                this.parents = parents;
                for (final String name : names) {
                    lists.put(name, new ArrayList<>());
                }
            }

            Map<String, Object> add(final String list, final Map<String, Object> record) {
                lists.get(list).add(record);
                return record;
            }

            void file(final PackageFile file) {
                appFiles.put(file.sha256(), file);
            }

            void into(final App app) {
                lists.values().removeIf(List::isEmpty);
                if (!lists.isEmpty()) {
                    others.put(app, new AppBatch(lists, new LinkedHashSet<>(parents), appFiles));
                }
            }
        }
    }

    private static Map<String, Object> fileRef(final PackageFile file) {
        return record("sha256", file.sha256(), "name", file.name());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> files(final Map<String, Object> version) {
        return (List<Map<String, Object>>) version.get("files");
    }

    /**
     * A record without its null values.
     */
    private static Map<String, Object> record(final Object... pairs) {
        final Map<String, Object> map = new LinkedHashMap<>();

        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                map.put((String) pairs[i], pairs[i + 1]);
            }
        }
        return map;
    }

    private static String extension(final String name) {
        final int dot = name.lastIndexOf('.');

        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String base(final String name) {
        final int dot = name.lastIndexOf('.');

        return dot <= 0 ? name : name.substring(0, dot);
    }
}
