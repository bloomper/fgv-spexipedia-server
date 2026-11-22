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

package nu.fgv.spexipedia.server.imports;

import nu.fgv.spexipedia.server.article.ArticleDtos.ArticleDto;
import nu.fgv.spexipedia.server.article.ArticleService;
import nu.fgv.spexipedia.server.article.Visibility;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexCategoryDto;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.basedata.Spex;
import nu.fgv.spexipedia.server.content.ContentDtos.CharacterDto;
import nu.fgv.spexipedia.server.content.ContentDtos.NumberDto;
import nu.fgv.spexipedia.server.content.ContentService;
import nu.fgv.spexipedia.server.content.ContentService.NumberCreditInput;
import nu.fgv.spexipedia.server.image.Image;
import nu.fgv.spexipedia.server.image.ImageKind;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.image.ImageSize;
import nu.fgv.spexipedia.server.imports.ImportEngine.Batch;
import nu.fgv.spexipedia.server.imports.ImportEngine.Handler;
import nu.fgv.spexipedia.server.imports.ImportEngine.ItemResult;
import nu.fgv.spexipedia.server.imports.ImportRecords.FileRef;
import nu.fgv.spexipedia.server.imports.ImportRecords.ImportArticle;
import nu.fgv.spexipedia.server.imports.ImportRecords.ImportArticleCategory;
import nu.fgv.spexipedia.server.imports.ImportRecords.ImportCharacter;
import nu.fgv.spexipedia.server.imports.ImportRecords.ImportCredit;
import nu.fgv.spexipedia.server.imports.ImportRecords.ImportNumber;
import nu.fgv.spexipedia.server.imports.ImportRecords.ImportPage;
import nu.fgv.spexipedia.server.imports.ImportRecords.ImportRedirect;
import nu.fgv.spexipedia.server.imports.ImportRecords.ImportScene;
import nu.fgv.spexipedia.server.imports.ImportRecords.ImportSpex;
import nu.fgv.spexipedia.server.kind.Kind;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.kind.KindType;
import nu.fgv.spexipedia.server.kind.KindUse;
import nu.fgv.spexipedia.server.kind.SectionContent;
import nu.fgv.spexipedia.server.page.SpexPageDto;
import nu.fgv.spexipedia.server.page.SpexPageRepository;
import nu.fgv.spexipedia.server.page.SpexPageService;
import nu.fgv.spexipedia.server.people.PeopleService;
import nu.fgv.spexipedia.server.people.PersonDto;
import nu.fgv.spexipedia.server.redirect.RedirectService;
import nu.fgv.spexipedia.server.util.Texts;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Spexipedia's import API (spec 5.11): what each kind of record becomes. The records go through the same services as
 * editors' changes, so the same rules apply; {@link ImportEngine} keeps track of the source keys.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class SpexipediaImport {

    public static final String SPEX = "SPEX";
    public static final String PAGE = "SPEX_PAGE";
    public static final String CHARACTER = "CHARACTER";
    public static final String SCENE = "SCENE";
    public static final String NUMBER = "NUMBER";
    public static final String ARTICLE_CATEGORY = "ARTICLE_CATEGORY";
    public static final String ARTICLE = "ARTICLE";
    public static final String REDIRECT = "REDIRECT";

    private static final Pattern IMPORT_IMAGE = Pattern.compile("\\(import:([^)\\s]+)\\)");

    private final ImportEngine engine;
    private final ImportFileStore files;
    private final BaseDataService baseData;
    private final ContentService content;
    private final SpexPageService pages;
    private final SpexPageRepository pageRepository;
    private final ImageService images;
    private final KindService kinds;
    private final PeopleService people;
    private final ArticleService articles;
    private final RedirectService redirects;

    @SuppressWarnings("java:S107")
    public SpexipediaImport(final ImportEngine engine, final ImportFileStore files, final BaseDataService baseData,
                            final ContentService content, final SpexPageService pages,
                            final SpexPageRepository pageRepository, final ImageService images, final KindService kinds,
                            final PeopleService people, final ArticleService articles, final RedirectService redirects) {
        this.engine = engine;
        this.files = files;
        this.baseData = baseData;
        this.content = content;
        this.pages = pages;
        this.pageRepository = pageRepository;
        this.images = images;
        this.kinds = kinds;
        this.people = people;
        this.articles = articles;
        this.redirects = redirects;
    }

    /**
     * One call of the import API: the records in the order they depend on each other.
     */
    public List<ItemResult> run(final ImportRecords.Batch input, final boolean dryRun) {
        final int size = sizeOf(input.spex()) + sizeOf(input.pages()) + sizeOf(input.characters()) + sizeOf(input.scenes())
                + sizeOf(input.numbers()) + sizeOf(input.articleCategories())
                + sizeOf(input.articles()) + sizeOf(input.redirects());

        if (size > ImportEngine.MAX_RECORDS) {
            throw new BadRequestException("error.import.tooMany", ImportEngine.MAX_RECORDS);
        }

        final Batch batch = new Batch(dryRun, Boolean.TRUE.equals(input.createMissingKinds()));
        final List<ItemResult> results = new ArrayList<>();

        results.addAll(engine.run(spexHandler, input.spex(), batch));
        results.addAll(engine.run(pageHandler, input.pages(), batch));
        results.addAll(engine.run(characterHandler, input.characters(), batch));
        results.addAll(engine.run(sceneHandler, input.scenes(), batch));
        results.addAll(engine.run(numberHandler, input.numbers(), batch));
        results.addAll(engine.run(categoryHandler, input.articleCategories(), batch));
        results.addAll(engine.run(articleHandler, input.articles(), batch));
        results.addAll(engine.run(redirectHandler, input.redirects(), batch));
        return results;
    }

    public int forget(final String sourcePrefix) {
        return engine.forget(sourcePrefix, List.of(redirectHandler, articleHandler, categoryHandler,
                numberHandler, sceneHandler, characterHandler, pageHandler, spexHandler));
    }

    private static int sizeOf(final @Nullable List<?> list) {
        return list == null ? 0 : list.size();
    }

    // Helpers ----------------------------------------------------------------------------------------------------------

    private UUID spexOf(final @Nullable UUID spexId, final @Nullable String spexSourceKey) {
        if (spexId != null) {
            return baseData.requireSpex(spexId).getUuid();
        }

        final UUID imported = engine.recordOf(SPEX, spexSourceKey);

        if (imported == null) {
            throw new BadRequestException("error.field.invalid", "spexId");
        }
        return imported;
    }

    /**
     * The kind a name or alias means; created when the batch allows it, else an error listing the known names.
     */
    private @Nullable Kind kindNamed(final KindType type, final @Nullable KindUse use, final @Nullable String name, final Batch batch) {
        final String cleaned = Texts.clean(name);

        if (cleaned == null) {
            return null;
        }
        return kinds.match(type, use, cleaned).orElseGet(() -> {
            if (batch.createMissingKinds()) {
                batch.warn("import.warning.newKind", cleaned);
                return kinds.createForImport(type, use, cleaned);
            }
            throw new BadRequestException("error.kind.unknown", cleaned,
                    kinds.kinds(type, use, false).stream().map(Kind::getNameSv).collect(Collectors.joining(", ")));
        });
    }

    /**
     * The section a page image goes in: an image section or the vest, by name or alias; an image section is created when
     * the batch allows it, else an error lists the known names.
     */
    private Kind imageSection(final String name, final Batch batch) {
        final String cleaned = Texts.clean(name);
        final List<Kind> sections = kinds.kinds(KindType.SECTION, null, true).stream()
                .filter(section -> section.getContent() == SectionContent.IMAGES || section.getContent() == SectionContent.VEST)
                .toList();
        final Optional<Kind> found = cleaned == null ? Optional.empty() : kinds.match(KindType.SECTION, null, cleaned)
                .filter(sections::contains);

        return found.orElseGet(() -> {
            if (cleaned != null && batch.createMissingKinds()) {
                batch.warn("import.warning.newKind", cleaned);
                return kinds.createImageSectionForImport(cleaned);
            }
            throw new BadRequestException("error.kind.unknown", Objects.requireNonNullElse(cleaned, ""),
                    sections.stream().filter(section -> !section.isRetired()).map(Kind::getNameSv).collect(Collectors.joining(", ")));
        });
    }

    /**
     * An image kind of an image section by name or alias, as {@link #kindNamed}.
     */
    private @Nullable Kind imageKindNamed(final Kind section, final @Nullable String name, final Batch batch) {
        final String cleaned = Texts.clean(name);

        if (cleaned == null) {
            return null;
        }
        return kinds.matchImageKind(section, cleaned).orElseGet(() -> {
            if (batch.createMissingKinds()) {
                batch.warn("import.warning.newKind", cleaned);
                return kinds.createImageKindForImport(section, cleaned);
            }
            throw new BadRequestException("error.kind.unknown", cleaned,
                    kinds.imageKinds(section, false).stream().map(Kind::getNameSv).collect(Collectors.joining(", ")));
        });
    }

    /**
     * The image of an uploaded file, or null in a dry run, which only checks that the file is there.
     */
    private @Nullable Image image(final @Nullable FileRef file, final ImageKind kind, final Batch batch) {
        if (file == null) {
            return null;
        }

        final String sha256 = ImportFileStore.normalize(file.sha256());
        final Path path = files.require(sha256);

        return batch.dryRun() ? null : images.importImage(path, sha256, file.name(), kind);
    }

    /**
     * A credit by name (spec 5.3): exactly one published spexare with the name becomes a reference to them; otherwise
     * someone outside FGV when the record says so, else unresolved, for editors.
     */
    private NumberCreditInput credit(final ImportCredit credit, final Batch batch) {
        final Kind kind = Objects.requireNonNull(kindNamed(KindType.CREDIT, null, credit.kind(), batch));
        final String name = Texts.required(credit.name(), 255, "name");
        List<PersonDto> matches = List.of();

        try {
            matches = people.search(name).stream()
                    .filter(person -> person.name() != null && person.name().equalsIgnoreCase(name))
                    .toList();
        } catch (final RuntimeException e) {
            batch.warn("import.warning.spexregisterDown", name);
        }
        if (matches.size() == 1 && matches.getFirst().id() != null) {
            return new NumberCreditInput(kind.getUuid(), matches.getFirst().id(), null);
        }
        if (Boolean.TRUE.equals(credit.outsideFgv())) {
            return new NumberCreditInput(kind.getUuid(), null, name);
        }
        return new NumberCreditInput(kind.getUuid(), null, name, true);
    }

    // Spex -------------------------------------------------------------------------------------------------------------

    private final Handler<ImportSpex> spexHandler = new Handler<>() {

        @Override
        public String kind() {
            return SPEX;
        }

        @Override
        public String sourceKey(final ImportSpex record) {
            return record.sourceKey();
        }

        @Override
        public UUID apply(final ImportSpex record, final @Nullable UUID existing, final Batch batch) {
            UUID id = existing != null ? existing : record.id();
            final UUID parentId = record.parentId() != null ? record.parentId() : engine.recordOf(SPEX, record.parentSourceKey());

            if (id == null) {
                if (parentId != null) {
                    id = baseData.createRevival(parentId, Texts.year(record.year())).id();
                } else {
                    id = baseData.createSpex(Texts.year(record.year()), record.title(), category(record.category())).id();
                }
            } else if (parentId == null && record.title() != null) {
                final SpexDto spex = baseData.spex(id.toString()).orElseThrow();

                baseData.updateSpex(id, record.year(), record.title(),
                        record.category() == null ? baseData.requireSpex(id).getCategory() == null ? null
                                : baseData.requireSpex(id).getCategory().getUuid() : category(record.category()));
                if (!spex.year().equals(record.year())) {
                    batch.warn("import.warning.yearChanged", spex.year());
                }
            }

            final Image poster = image(record.poster(), ImageKind.POSTER, batch);

            if (poster != null) {
                baseData.setPoster(id, poster.getUuid());
            }
            return id;
        }

        private @Nullable UUID category(final @Nullable String name) {
            final String cleaned = Texts.clean(name);

            if (cleaned == null) {
                return null;
            }
            return baseData.categories(false).stream().filter(category -> category.name().equalsIgnoreCase(cleaned))
                    .findFirst().map(SpexCategoryDto::id)
                    .orElseThrow(() -> new BadRequestException("error.kind.unknown", cleaned,
                            baseData.categories(false).stream().map(SpexCategoryDto::name).collect(Collectors.joining(", "))));
        }

        @Override
        public @Nullable Long version(final UUID id) {
            return baseData.spex(id.toString()).map(_ -> baseData.requireSpex(id).getVersion()).orElse(null);
        }

        @Override
        public boolean forget(final UUID id) {
            // Spex are base data that other applications copy at once; they are never removed by an import
            return false;
        }
    };

    // Spex pages -------------------------------------------------------------------------------------------------------

    private final Handler<ImportPage> pageHandler = new Handler<>() {

        @Override
        public String kind() {
            return PAGE;
        }

        @Override
        public String sourceKey(final ImportPage record) {
            return record.sourceKey();
        }

        @Override
        public UUID apply(final ImportPage record, final @Nullable UUID existing, final Batch batch) {
            final UUID spexId = spexOf(record.spexId(), record.spexSourceKey());
            final Spex spex = baseData.requireSpex(spexId);
            final SpexPageDto current = pages.page(spex.getId()).orElseGet(() -> SpexPageDto.empty(spex.getId(), spexId));

            pages.update(spexId, new SpexPageService.PageInput(
                    either(record.premiere(), current.premiere()),
                    either(record.introduction(), current.introduction()),
                    either(record.shortDescription(), current.shortDescription()),
                    either(record.description(), current.description()),
                    either(record.synopsis(), current.synopsis()),
                    either(record.vestDescription(), current.vestDescription()),
                    current.vestWornBy(), null));

            final Image poster = image(record.poster(), ImageKind.POSTER, batch);

            if (poster != null) {
                baseData.setPoster(spexId, poster.getUuid());
            }

            final Image rebus = image(record.rebus(), ImageKind.REBUS, batch);
            final Image solution = image(record.rebusSolution(), ImageKind.REBUS, batch);

            if (rebus != null || solution != null) {
                pages.setRebus(spexId, rebus != null ? rebus.getUuid() : current.rebusKey() == null ? null : images.byIds(List.of(current.rebusKey())).get(current.rebusKey()).getUuid(),
                        solution != null ? solution.getUuid() : current.rebusSolutionKey() == null ? null
                                : images.byIds(List.of(current.rebusSolutionKey())).get(current.rebusSolutionKey()).getUuid());
            }
            if (record.facts() != null) {
                final List<SpexPageService.FactInput> facts = new ArrayList<>();

                for (final ImportRecords.ImportFact fact : record.facts()) {
                    final Kind kind = kindNamed(KindType.FACT, null, fact.kind(), batch);

                    if (kind != null && Texts.clean(fact.value()) != null) {
                        facts.add(new SpexPageService.FactInput(kind.getUuid(), fact.value()));
                    }
                }
                pages.setFacts(spexId, facts);
            }
            if (record.images() != null) {
                // One list per section and kind, in the order given
                final Map<String, List<ContentService.ImageRef>> groups = new LinkedHashMap<>();
                final Map<String, @Nullable UUID> groupKinds = new LinkedHashMap<>();

                for (final ImportRecords.ImportPageImage pageImage : record.images()) {
                    final Kind section = imageSection(pageImage.section(), batch);
                    final boolean images = section.getContent() == SectionContent.IMAGES;
                    final Kind kind = images ? imageKindNamed(section, pageImage.kind(), batch) : null;
                    final UUID kindId = kind != null ? kind.getUuid()
                            : images ? kinds.firstImageKind(section).map(Kind::getUuid).orElse(null) : null;
                    final String group = section.getUuid() + "/" + kindId;
                    final Image image = image(pageImage.file(), ImageKind.SECTION, batch);

                    groupKinds.put(group, kindId);
                    if (image != null) {
                        groups.computeIfAbsent(group, _ -> new ArrayList<>()).add(new ContentService.ImageRef(image.getUuid(), null, pageImage.caption()));
                    }
                }
                groups.forEach((group, refs) -> pages.setImages(spexId, UUID.fromString(group.substring(0, group.indexOf('/'))),
                        groupKinds.get(group), refs));
            }
            return spexId;
        }

        @Override
        public @Nullable Long version(final UUID id) {
            return baseData.spex(id.toString()).flatMap(_ -> pageRepository.findBySpex(baseData.requireSpex(id)))
                    .map(page -> page.getVersion()).orElse(0L);
        }

        @Override
        public boolean forget(final UUID id) {
            // A spex page belongs to its spex; an unpublished one is left for editors, its key forgotten
            return pageRepository.findBySpex(baseData.requireSpex(id)).map(page -> !page.isPublished()).orElse(true);
        }
    };

    private static <T> @Nullable T either(final @Nullable T imported, final @Nullable T current) {
        return imported != null ? imported : current;
    }

    // Characters, scenes, numbers --------------------------------------------------------------------------------------

    private final Handler<ImportCharacter> characterHandler = new Handler<>() {

        @Override
        public String kind() {
            return CHARACTER;
        }

        @Override
        public String sourceKey(final ImportCharacter record) {
            return record.sourceKey();
        }

        @Override
        public UUID apply(final ImportCharacter record, final @Nullable UUID existing, final Batch batch) {
            final UUID spexId = spexOf(record.spexId(), record.spexSourceKey());
            final CharacterDto character = content.importCharacter(existing, spexId, record.name(), record.description(),
                    record.position());

            if (record.images() != null && !record.images().isEmpty()) {
                final List<ContentService.ImageRef> refs = new ArrayList<>();

                for (final ImportRecords.ImportCharacterImage characterImage : record.images()) {
                    final Kind kind = kindNamed(KindType.IMAGE, KindUse.CHARACTER, characterImage.kind(), batch);
                    final Image image = image(characterImage.file(), ImageKind.CHARACTER, batch);

                    if (image != null) {
                        refs.add(new ContentService.ImageRef(image.getUuid(), kind == null ? null : kind.getUuid(), characterImage.caption()));
                    }
                }
                if (!batch.dryRun()) {
                    content.setCharacterImages(character.id(), refs);
                }
            }
            return character.id();
        }

        @Override
        public @Nullable Long version(final UUID id) {
            return content.characterVersion(id);
        }

        @Override
        public boolean forget(final UUID id) {
            try {
                return content.deleteCharacter(id);
            } catch (final RuntimeException e) {
                return false;
            }
        }
    };

    private final Handler<ImportScene> sceneHandler = new Handler<>() {

        @Override
        public String kind() {
            return SCENE;
        }

        @Override
        public String sourceKey(final ImportScene record) {
            return record.sourceKey();
        }

        @Override
        public UUID apply(final ImportScene record, final @Nullable UUID existing, final Batch batch) {
            return content.importScene(existing, spexOf(record.spexId(), record.spexSourceKey()),
                    new ContentService.SceneInput(record.act() == null ? 1 : record.act(), record.title(), record.place(),
                            Boolean.TRUE.equals(record.intervalAfter())),
                    record.position()).id();
        }

        @Override
        public @Nullable Long version(final UUID id) {
            return content.sceneVersion(id);
        }

        @Override
        public boolean forget(final UUID id) {
            return content.deleteScene(id);
        }
    };

    private final Handler<ImportNumber> numberHandler = new Handler<>() {

        @Override
        public String kind() {
            return NUMBER;
        }

        @Override
        public String sourceKey(final ImportNumber record) {
            return record.sourceKey();
        }

        @Override
        public UUID apply(final ImportNumber record, final @Nullable UUID existing, final Batch batch) {
            final UUID spexId = spexOf(record.spexId(), record.spexSourceKey());
            final Kind kind = kindNamed(KindType.NUMBER, null, record.kind(), batch);
            final List<NumberCreditInput> credits = record.credits() == null ? null
                    : record.credits().stream().map(credit -> credit(credit, batch)).toList();
            final List<UUID> characters = record.characters() == null ? null : record.characters().stream()
                    .map(Texts::clean).filter(Objects::nonNull)
                    .map(name -> content.characterByName(spexId, name).map(CharacterDto::id).orElseGet(() -> {
                        batch.warn("import.warning.unknownCharacter", name);
                        return null;
                    }))
                    .filter(Objects::nonNull)
                    .toList();

            return content.importNumber(existing, spexId, new ContentService.NumberInput(record.act() == null ? 1 : record.act(),
                    record.title(), kind == null ? null : kind.getUuid(), record.originalTitle(), record.originalArtist(),
                    record.fromWork(), record.firstLine(), credits, characters), record.position()).id();
        }

        @Override
        public @Nullable Long version(final UUID id) {
            return content.numberVersion(id);
        }

        @Override
        public boolean forget(final UUID id) {
            try {
                return content.deleteNumber(id);
            } catch (final RuntimeException e) {
                return false;
            }
        }
    };

    // Articles ---------------------------------------------------------------------------------------------------------

    private final Handler<ImportArticleCategory> categoryHandler = new Handler<>() {

        @Override
        public String kind() {
            return ARTICLE_CATEGORY;
        }

        @Override
        public String sourceKey(final ImportArticleCategory record) {
            return record.sourceKey();
        }

        @Override
        public UUID apply(final ImportArticleCategory record, final @Nullable UUID existing, final Batch batch) {
            return articles.importCategory(existing, record.name(), record.description(), record.parents()).id();
        }

        @Override
        public @Nullable Long version(final UUID id) {
            return articles.categoryVersion(id);
        }

        @Override
        public boolean forget(final UUID id) {
            return articles.forgetCategory(id);
        }
    };

    private final Handler<ImportArticle> articleHandler = new Handler<>() {

        @Override
        public String kind() {
            return ARTICLE;
        }

        @Override
        public String sourceKey(final ImportArticle record) {
            return record.sourceKey();
        }

        @Override
        public UUID apply(final ImportArticle record, final @Nullable UUID existing, final Batch batch) {
            String body = Objects.requireNonNullElse(record.body(), "");

            if (record.images() != null) {
                final Map<String, String> addresses = new LinkedHashMap<>();

                for (final ImportRecords.ImportArticleImage articleImage : record.images()) {
                    final Image image = image(articleImage.file(), ImageKind.ARTICLE, batch);

                    addresses.put(articleImage.key(), image == null ? "#" : images.url(image.getUuid(), ImageSize.PAGE, "webp"));
                }

                final Matcher matcher = IMPORT_IMAGE.matcher(body);
                final StringBuilder replaced = new StringBuilder();

                while (matcher.find()) {
                    final String address = addresses.get(matcher.group(1));

                    if (address == null) {
                        batch.warn("import.warning.unknownImage", matcher.group(1));
                    }
                    matcher.appendReplacement(replaced, Matcher.quoteReplacement("(" + (address == null ? "#" : address) + ")"));
                }
                matcher.appendTail(replaced);
                body = replaced.toString();
            }

            final Visibility visibility = record.visibility() == null ? Visibility.PUBLIC
                    : Visibility.valueOf(record.visibility().strip().toUpperCase(Locale.ROOT));
            final List<UUID> categories = articles.categoryIds(record.categories());
            final UUID parentId = engine.recordOf(ARTICLE, record.parentSourceKey());
            final ArticleService.ArticleInput input = new ArticleService.ArticleInput(record.title(), body, visibility,
                    categories, "Importerad", parentId);

            if (existing != null) {
                final ArticleDto updated = articles.update(existing, input, null);

                if (parentId != null && !Objects.equals(updated.parentKey(), articleKey(parentId))) {
                    articles.move(existing, parentId, null);
                }
                return updated.id();
            }
            return articles.create(input).id();
        }

        private @Nullable Long articleKey(final UUID id) {
            return articles.article(id.toString()).map(ArticleDto::key).orElse(null);
        }

        @Override
        public @Nullable Long version(final UUID id) {
            return articles.version(id);
        }

        @Override
        public boolean forget(final UUID id) {
            return articles.forget(id);
        }
    };

    private final Handler<ImportRedirect> redirectHandler = new Handler<>() {

        @Override
        public String kind() {
            return REDIRECT;
        }

        @Override
        public String sourceKey(final ImportRedirect record) {
            return record.sourceKey();
        }

        @Override
        public UUID apply(final ImportRedirect record, final @Nullable UUID existing, final Batch batch) {
            String target = Texts.clean(record.target());

            if (target == null) {
                final UUID article = engine.recordOf(ARTICLE, record.targetSourceKey());
                final UUID spex = engine.recordOf(SPEX, record.targetSourceKey());
                final UUID page = engine.recordOf(PAGE, record.targetSourceKey());

                if (article != null) {
                    target = articles.article(article.toString()).map(a -> "/articles/" + a.slug()).orElse(null);
                } else if (spex != null || page != null) {
                    target = baseData.spex(Objects.requireNonNullElse(spex, page).toString()).map(s -> "/spex/" + s.slug()).orElse(null);
                }
            }
            if (target == null) {
                throw new BadRequestException("error.redirect.target");
            }

            final UUID id = existing != null ? existing : redirects.idOf(record.oldTitle()).orElse(null);

            return redirects.put(id, record.oldTitle(), target).id();
        }

        @Override
        public @Nullable Long version(final UUID id) {
            return redirects.byId(id).map(_ -> 0L).orElse(null);
        }

        @Override
        public boolean forget(final UUID id) {
            return redirects.remove(id);
        }
    };
}
