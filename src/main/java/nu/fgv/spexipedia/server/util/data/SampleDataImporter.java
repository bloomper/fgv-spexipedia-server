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

package nu.fgv.spexipedia.server.util.data;

import lombok.extern.slf4j.Slf4j;
import nu.fgv.spexipedia.server.article.ArticleDtos.ArticleCategoryDto;
import nu.fgv.spexipedia.server.article.ArticleDtos.ArticleDto;
import nu.fgv.spexipedia.server.article.ArticleService;
import nu.fgv.spexipedia.server.article.Visibility;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.TaskDto;
import nu.fgv.spexipedia.server.basedata.SeededBaseData;
import nu.fgv.spexipedia.server.basedata.BaseDataKind;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.basedata.MergeService;
import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.content.ContentDtos;
import nu.fgv.spexipedia.server.content.ContentDtos.CharacterDto;
import nu.fgv.spexipedia.server.content.ContentDtos.NumberDto;
import nu.fgv.spexipedia.server.content.ContentService;
import nu.fgv.spexipedia.server.content.ListStore.CreditRef;
import nu.fgv.spexipedia.server.content.ListStore.RevivalChange;
import nu.fgv.spexipedia.server.content.ListStore.RevivalChangeKind;
import nu.fgv.spexipedia.server.feed.SyncConsumerService;
import nu.fgv.spexipedia.server.image.Image;
import nu.fgv.spexipedia.server.image.ImageKind;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.page.SpexPageService;
import nu.fgv.spexipedia.server.redirect.RedirectService;
import nu.fgv.spexipedia.server.search.SearchService;
import nu.fgv.spexipedia.server.settings.SettingsService;
import nu.fgv.spexipedia.server.util.security.KeycloakJwtRolesConverter;
import nu.fgv.spexipedia.server.util.security.SecurityUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Replaces everything with sample data at every start while {@code spexipedia.data.import-sample-data} is set
 * (section 5.13 of the spec); refuses to run in the {@code production} profile.
 *
 * <p>Spexipedia comes first: it is the source of the spex, categories and tasks, which Spexregister copies from the change
 * feed and the other applications read. They are FGV's real ones, which a migration inserts with fixed ids (V1_5); the
 * import builds on them, and removes only the base data it added itself. Images are generated. Credits
 * refer to a few spexare that Spexregister's sample data creates with the same fixed ids ({@link #SPEXARE}), published
 * and unpublished, with and without nicknames, so that section 5.3 can be tested; who played which character comes from
 * Spexregister's sample actors, which it links to these characters.</p>
 *
 * <p>Everything is made through the services, as a sample administrator, so the feed, the audit log and the search
 * index are as in use. The feed's sequence keeps counting across imports, so consumers read everything again.</p>
 *
 * <p>The sample users and their roles are fgv-infra-deploy's (bin/seed-users.sh, sample-users.tsv): the import never
 * asks Keycloak, and refers to them by their fixed ids. Spexipedia comes before Spexregister and the other
 * applications only because they copy its base data.</p>
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Component
public class SampleDataImporter {

    /**
     * Spexare that Spexregister's sample data creates with these ids: [id, name, nickname or empty, published].
     */
    public static final List<String[]> SPEXARE = List.of(
            new String[]{"00000000-0000-7000-a000-000000000001", "Bertil Basson", "Basen", "true"},
            new String[]{"00000000-0000-7000-a000-000000000002", "Cecilia Cellodal", "Cellan", "true"},
            new String[]{"00000000-0000-7000-a000-000000000003", "David Dirigentsson", "", "true"},
            new String[]{"00000000-0000-7000-a000-000000000004", "Eva Ensemblé", "Evita", "false"},
            new String[]{"00000000-0000-7000-a000-000000000005", "Fredrik Fiolin", "Fiolen", "true"},
            new String[]{"00000000-0000-7000-a000-000000000006", "Greta Gitarrqvist", "", "false"});
    // user@fgv.nu's Keycloak id, fixed in fgv-infra-deploy's sample-users.tsv
    static final String USER_ID = "00000000-0000-4000-8000-000000000003";
    private static final List<String> TABLES = List.of("article_editing", "article_link", "article_article_category", "article", "article_category_parent", "article_category",
            "revision", "redirect", "search_document", "number_character", "number_credit",
            "spex_number", "character_image", "spex_character", "scene", "revival_change", "spex_page_photo",
            "spex_page_image", "spex_section", "spex_fact", "spex_page", "id_alias", "sync_consumer",
            "image", "audit_entry", "app_user");
    private static final String[][] ORIGINALS = {
            {"Dancing Queen", "ABBA", "Benny Andersson, Björn Ulvaeus"}, {"Waterloo", "ABBA", "Benny Andersson, Björn Ulvaeus"},
            {"Bohemian Rhapsody", "Queen", "Freddie Mercury"}, {"Hallelujah", "Leonard Cohen", "Leonard Cohen"},
            {"Vem kan segla förutan vind", "Trad.", "Trad."}, {"Feeling Good", "Nina Simone", "Anthony Newley"},
            {"Eye of the Tiger", "Survivor", "Frankie Sullivan"}, {"Sweet Caroline", "Neil Diamond", "Neil Diamond"},
            {"Euphoria", "Loreen", "Thomas G:son"}, {"Hooked on a Feeling", "Blue Swede", "Mark James"},
            {"Diggi-loo diggi-ley", "Herreys", "Torgny Söderberg"}, {"Under ytan", "Uno Svenningsson", "Uno Svenningsson"},
            {"Mamma Mia", "ABBA", "Benny Andersson, Björn Ulvaeus"}, {"I Will Survive", "Gloria Gaynor", "Freddie Perren"}};
    private static final String[] NUMBER_TITLES = {"Öppningskören", "Kärleksduett i månsken", "Kungens klagan", "Intrigernas vals",
            "Hovets galopp", "Spionens tango", "Narrens visa", "Slaget vid frukostbordet", "Ödets fanfar", "Drottningens dröm",
            "Bondens blues", "Finalen", "Tackkören", "Fängelsehålans kanon"};
    private static final String[] ROLES = {"Drottningen", "Narren", "Kardinalen", "Hovdamen Åsa", "Generalen", "Spionen",
            "Bonden Örjan", "Kocken", "Prinsessan", "Ärkebiskopen"};
    private static final String[] PLACES = {"Slottet", "Torget", "Kungliga sovgemaket", "Skogen", "Hamnen", "Tronsalen",
            "Krogen Gyldene Freden", "Fängelsehålan"};

    private final SpexipediaProperties properties;
    private final Environment environment;
    private final JdbcClient jdbc;
    private final SeededBaseData seededBaseData;
    private final BaseDataService baseData;
    private final MergeService mergeService;
    private final ContentService contentService;
    private final SpexPageService pageService;
    private final ArticleService articleService;
    private final ImageService imageService;
    private final RedirectService redirectService;
    private final SettingsService settingsService;
    private final SyncConsumerService consumerService;
    private final SearchService searchService;

    @SuppressWarnings("java:S107")
    public SampleDataImporter(final SpexipediaProperties properties, final Environment environment, final JdbcClient jdbc, final SeededBaseData seededBaseData,
                              final BaseDataService baseData, final MergeService mergeService,
                              final ContentService contentService,
                              final SpexPageService pageService, final ArticleService articleService,
                              final ImageService imageService, final RedirectService redirectService,
                              final SettingsService settingsService, final SyncConsumerService consumerService,
                              final SearchService searchService) {
        this.properties = properties;
        this.environment = environment;
        this.jdbc = jdbc;
        this.seededBaseData = seededBaseData;
        this.baseData = baseData;
        this.mergeService = mergeService;
        this.contentService = contentService;
        this.pageService = pageService;
        this.articleService = articleService;
        this.imageService = imageService;
        this.redirectService = redirectService;
        this.settingsService = settingsService;
        this.consumerService = consumerService;
        this.searchService = searchService;
    }

    /**
     * A fixed id from a kind and a name, the same on every import.
     */
    public static UUID uuid(final String kind, final String... names) {
        return UUID.nameUUIDFromBytes(("spexipedia-sample-%s-%s".formatted(kind, String.join("|", names))).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID spexare(final int index) {
        return UUID.fromString(SPEXARE.get(index)[0]);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onReady() {
        if (!properties.data().importSampleData()) {
            return;
        }
        if (Arrays.asList(environment.getActiveProfiles()).contains("production")) {
            throw new IllegalStateException("Refusing to import sample data in the production profile");
        }
        importSampleData();
    }

    public void importSampleData() {
        final long started = System.nanoTime();
        final boolean large = "large".equalsIgnoreCase(properties.data().sampleDataSize());

        log.info("Importing {} sample data", large ? "large" : "small");
        wipe();
        seededBaseData.publish();
        asSampleAdmin(() -> {
            new Generator(large).generate();
            return null;
        });
        log.info("Imported sample data in {} s", Duration.ofNanos(System.nanoTime() - started).toSeconds());
    }

    private void wipe() {
        jdbc.sql("SET FOREIGN_KEY_CHECKS = 0").update();
        for (final String table : TABLES) {
            jdbc.sql("TRUNCATE TABLE " + table).update();
        }
        // The base data the sample data added (retired and merged examples) goes; the seeded records are as migrated
        for (final String table : List.of("spex", "spex_category", "task", "task_category")) {
            jdbc.sql("DELETE FROM " + table + " WHERE created_by <> 'system'").update();
        }
        jdbc.sql("UPDATE spex SET poster_id = NULL, retired = 0").update();
        jdbc.sql("UPDATE spex_category SET logo_id = NULL, description = NULL, retired = 0").update();
        jdbc.sql("UPDATE task SET retired = 0").update();
        jdbc.sql("UPDATE task_category SET retired = 0").update();
        // Deleted, not truncated: the sequence goes on, so that consumers read the new sample data from their cursors
        jdbc.sql("DELETE FROM change_feed_entry").update();
        jdbc.sql("SET FOREIGN_KEY_CHECKS = 1").update();
        jdbc.sql("UPDATE settings SET welcome_sv = NULL, welcome_en = NULL, public_welcome_sv = NULL, public_welcome_en = NULL, contact_email = NULL").update();
        try {
            FileSystemUtils.deleteRecursively(properties.media().root().resolve("images"));
            FileSystemUtils.deleteRecursively(properties.media().root().resolve("trash"));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Runs the work as a sample administrator with every role, so that it goes through the services' checks.
     */
    private <T> T asSampleAdmin(final Supplier<T> work) {
        final Jwt jwt = Jwt.withTokenValue("sample-data")
                .header("alg", "none")
                .subject("sample-data")
                .claim("azp", SecurityUtil.CLIENT_ID)
                .claim("preferred_username", "exempeldata")
                .claim("name", "Exempeldata")
                .claim("resource_access", Map.of(SecurityUtil.CLIENT_ID, Map.of("roles", List.of("USER", "AUTHOR", "EDITOR", "ADMIN"))))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofHours(2)))
                .build();
        final var previous = SecurityContextHolder.getContext().getAuthentication();

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, new KeycloakJwtRolesConverter().convert(jwt)));
        try {
            return work.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    // Content ------------------------------------------------------------------------------------------------------------

    private final class Generator {

        private final boolean large;
        private final Map<String, UUID> categories = new LinkedHashMap<>();
        private final Map<String, UUID> taskCategories = new LinkedHashMap<>();
        private final List<Image> pool = new ArrayList<>();
        private int images;

        Generator(final boolean large) {
            this.large = large;
        }

        void generate() {
            baseData();
            settings();
            consumers();

            final List<SpexDto> all = baseData.spexAll(true);
            final List<SpexDto> full = full(all);
            final Set<UUID> fullIds = new java.util.HashSet<>(full.stream().map(SpexDto::id).toList());

            for (int i = 0; i < full.size(); i++) {
                page(full.get(i), i, i % 5 != 4);
            }
            // The rest get a poster and an introduction, and stay unpublished in the small size
            for (final SpexDto spex : all) {
                if (!fullIds.contains(spex.id()) && !spex.revival() && (large || spex.year().endsWith("0"))) {
                    baseData.setPoster(spex.id(), image(ImageKind.POSTER, 600, 840, spex.year(), spex.title()).getUuid());
                    pageService.update(spex.id(), new SpexPageService.PageInput(null,
                            "%s – ett spex från %s.".formatted(spex.title(), spex.year()), null, null, null, null, null, null));
                    if (large) {
                        pageService.setPublished(spex.id(), true);
                    }
                }
            }
            revivals(all);
            articles();
            redirects();
            retiredAndMerged();
            searchService.rebuild();
        }

        // Base data ----------------------------------------------------------------------------------------------------

        /**
         * The seeded categories and task categories by name, for the records the sample data adds itself.
         */
        private void baseData() {
            jdbc.sql("SELECT name, uuid FROM spex_category").query((rs, n) -> Map.entry(rs.getString(1), UUID.fromString(rs.getString(2))))
                    .list().forEach(entry -> categories.put(entry.getKey(), entry.getValue()));
            jdbc.sql("SELECT name, uuid FROM task_category").query((rs, n) -> Map.entry(rs.getString(1), UUID.fromString(rs.getString(2))))
                    .list().forEach(entry -> taskCategories.put(entry.getKey(), entry.getValue()));
        }

        private void settings() {
            settingsService.save(new SettingsService.SettingsDto(
                    "Välkommen till Spexipedia! Här finns allt om spexen, och som inloggad ser du även vilka som var med.",
                    "Welcome to Spexipedia! Everything about the spex, and as a member you also see who took part.",
                    "Spexipedia är FGV:s uppslagsverk om Chalmersspexet: alla spex sedan 1948, rollerna, musiken och traditionerna. Exempeldata – inget här är på riktigt.",
                    "Spexipedia is FGV's encyclopedia of the spex. Sample data – nothing here is real.",
                    "spexipedia@fgv.nu", null), "sample-data");
        }

        private void consumers() {
            consumerService.register("Spexregister", "fgv-spexregister-service", "http://spexregister-api:8080/api/sync/notify");
            consumerService.register("Spexify", "fgv-spexify-service", "http://spexify-api:8080/api/sync/notify");
            consumerService.register("Spexflix", "fgv-spexflix-service", "http://spexflix-api:8080/api/sync/notify");
            consumerService.register("Spexgram", "fgv-spexgram-service", "http://spexgram-api:8080/api/sync/notify");
            consumerService.register("Spexarkivet", "fgv-archive-service", "http://archive-api:8080/api/sync/notify");
        }

        // Spex pages ---------------------------------------------------------------------------------------------------

        /**
         * The spex that get full pages: about twenty in the small size, every original in the large.
         */
        private List<SpexDto> full(final List<SpexDto> all) {
            if (large) {
                return all.stream().filter(spex -> !spex.revival()).toList();
            }

            final Set<String> chosen = Set.of("Bojan|1948", "Erik XIV|1949", "Henrik 8|1954", "Napoleon|1956", "Statyerna|1957",
                    "Alexander|1967", "Richard III|1968", "Noak|1971", "Lasse-Maja|1984", "Victoria|1986", "Kleopatra|2005",
                    "Mata Hari|2003", "Gagarin|2003", "Carl von Linné|2020", "Aristoteles|2021", "H. C. Andersen|2022",
                    "Elisabeth I|2023", "Gauss|2024", "Ask och Embla|2025", "Abraham Lincoln|2026", "Cæsarion|1950");

            return all.stream().filter(spex -> !spex.revival() && chosen.contains(spex.title() + "|" + spex.year())).toList();
        }

        /**
         * A page with every section filled (full), or only a few.
         */
        private void page(final SpexDto spex, final int index, final boolean full) {
            baseData.setPoster(spex.id(), image(ImageKind.POSTER, 900, 1260, "Chalmersspexet " + spex.year(), spex.title()).getUuid());

            final List<CharacterDto> characters = characters(spex, index);

            scenes(spex);
            numbers(spex, characters, index);
            pageService.update(spex.id(), new SpexPageService.PageInput(
                    index == 0 ? LocalDate.now().withYear(Integer.parseInt(spex.year())).toString()
                            : "%s-02-%02d".formatted(spex.year(), 1 + index % 27),
                    "%s – ett spex om makt, kärlek och en och annan felräknad tronföljd.".formatted(spex.title()),
                    full ? "Ett spex om %s och hans hov.".formatted(spex.title()) : null,
                    description(spex), full ? synopsis(spex) : null,
                    full ? "Västen till %s är broderad i guld på mörkgrönt, med %s som motiv.".formatted(spex.title(), spex.title().toLowerCase(Locale.ROOT)) : null,
                    full ? new CreditRef(spexare(index % SPEXARE.size()), null) : null, null));
            if (full) {
                pageService.setImages(spex.id(), DefaultKinds.SCENOGRAPHY, DefaultKinds.BACKDROP, List.of(
                        placed(ImageKind.SECTION, "Fond: " + PLACES[index % PLACES.length], spex),
                        placed(ImageKind.SECTION, "Fond: " + PLACES[(index + 1) % PLACES.length], spex)));
                pageService.setImages(spex.id(), DefaultKinds.SCENOGRAPHY, DefaultKinds.STAGE, List.of(placed(ImageKind.SECTION, "Scenbygget", spex)));
                pageService.setImages(spex.id(), DefaultKinds.SCENOGRAPHY, DefaultKinds.PROPS, List.of(placed(ImageKind.SECTION, "Kronan", spex),
                        placed(ImageKind.SECTION, "Tronen", spex)));
                pageService.setImages(spex.id(), DefaultKinds.VEST, null, List.of(placed(ImageKind.SECTION, "Västen, framsida", spex)));
                pageService.setImages(spex.id(), DefaultKinds.OTHER_IMAGES_SECTION, DefaultKinds.OTHER_IMAGES, List.of(placed(ImageKind.OTHER, "Programbladet", spex)));
                pageService.setFacts(spex.id(), List.of(
                        new SpexPageService.FactInput(DefaultKinds.VENUE, index % 2 == 0 ? "Chalmers kårhus, RunAn" : "Lisebergsteatern"),
                        new SpexPageService.FactInput(DefaultKinds.PERFORMANCES, String.valueOf(8 + index % 7)),
                        new SpexPageService.FactInput(DefaultKinds.SETTING, PLACES[index % PLACES.length])));
                pageService.setRebus(spex.id(), image(ImageKind.REBUS, 1000, 700, "Programrebus", "☺ + 🎭 = ?").getUuid(),
                        image(ImageKind.REBUS, 1000, 700, "Lösning", spex.title()).getUuid());
            }
            if (index == 0) {
                // Spexgram's sample photos of its first album, about the earliest spex: public ones and members' ones
                pageService.setPhotos(spex.id(), "GROUP", null, List.of(spexgramItem(1), spexgramItem(2), spexgramItem(21), spexgramItem(22)));
                pageService.setPhotos(spex.id(), "BOARD", null, List.of(spexgramItem(3), spexgramItem(23)));
                // Actors of three roles; Spexgram's first album is public but for every fifth item, its second is for members
                pageService.setPhotos(spex.id(), "ACTOR", characters.getFirst().key(), List.of(spexgramItem(4), spexgramItem(24)));
                pageService.setPhotos(spex.id(), "ACTOR", characters.get(1).key(), List.of(spexgramItem(6), spexgramItem(25)));
                pageService.setPhotos(spex.id(), "ACTOR", characters.get(2).key(), List.of(spexgramItem(7), spexgramItem(10)));
            }
            pageService.setPublished(spex.id(), index % 7 != 6);
        }

        private UUID spexgramItem(final int n) {
            return UUID.nameUUIDFromBytes(("spexgram-sample-item-%d".formatted(n)).getBytes(StandardCharsets.UTF_8));
        }

        private ContentService.ImageRef placed(final ImageKind kind, final String caption, final SpexDto spex) {
            final Image image = large ? pooled(kind, caption) : image(kind, 1600, 1000, caption, spex.title());

            return new ContentService.ImageRef(image.getUuid(), null, caption);
        }

        /**
         * In the large size, a few images shared by every page, so that the import stays quick.
         */
        private Image pooled(final ImageKind kind, final String caption) {
            if (pool.size() < 12) {
                pool.add(image(kind, 1200, 800, caption, "Exempelbild"));
            }
            return pool.get(images++ % pool.size());
        }

        private Image image(final ImageKind kind, final int width, final int height, final String... lines) {
            final byte[] png = SampleImages.png(width, height, (images++ * 0.137f) % 1, images, List.of(lines));

            return imageService.store(new ByteArrayInputStream(png), String.join(" ", lines).replaceAll("[^\\p{L}\\p{N} ]", "") + ".png", kind);
        }

        private String description(final SpexDto spex) {
            return """
                    **%s** sattes upp av Chalmersspexet %s. Exempeldata: texten är påhittad, men visar hur en sida ser ut med *Markdown*, \
                    länkar till [[Spexets traditioner]] och till [andra spex](/spex).
                    
                    Spexet minns för sin finalkör, som publiken krävde tre gånger, och för en kuliss som föll i fel ögonblick \
                    – se [[Anekdot: Kulissen som föll|anekdoten]]. Åäö fungerar.""".formatted(spex.title(), spex.year());
        }

        private String synopsis(final SpexDto spex) {
            return """
                    ### Akt 1
                    Hovet samlas i tronsalen, där %s försöker hålla ordning på arvsföljden. Narren vet mer än han säger.
                    
                    ### Akt 2
                    Intrigerna tätnar på krogen Gyldene Freden. Allt avgörs vid frukostbordet, och kärleken segrar – nästan.""".formatted(spex.title());
        }

        private List<CharacterDto> characters(final SpexDto spex, final int index) {
            final List<CharacterDto> created = new ArrayList<>();
            final String titleRole = spex.title().replaceAll("\\s+\\d+$", "");
            final int count = large ? 4 : 5 + index % 4;

            created.add(contentService.createCharacter(spex.id(), titleRole,
                    "Titelrollen. %s är både hjälte och skurk, beroende på akt.".formatted(titleRole), uuid("character", spex.id().toString(), "0")));
            for (int i = 0; i < count; i++) {
                final String role = ROLES[(index + i) % ROLES.length];
                // Now and then a historical person, as in many spex
                final String name = i == 1 && index % 3 == 0 ? "Bellman" : role;

                created.add(contentService.createCharacter(spex.id(), name,
                        i % 2 == 0 ? "%s, som alla misstänker men ingen förstår.".formatted(name) : null,
                        uuid("character", spex.id().toString(), String.valueOf(i + 1))));
            }
            if (!large || index < 3) {
                // Sketches show no spexare, so visitors see them: every kind for the title role, a sketch and often a
                // make-up sketch for the next roles
                for (int i = 0; i < created.size(); i++) {
                    final CharacterDto character = created.get(i);

                    if (i == 0) {
                        contentService.setCharacterImages(character.id(), List.of(sketch(character, ImageKind.CHARACTER, DefaultKinds.SKETCH, "Skiss"),
                                sketch(character, ImageKind.CHARACTER, DefaultKinds.MAKEUP, "Sminkskiss"),
                                sketch(character, ImageKind.CHARACTER, DefaultKinds.COSTUME, "Kostymdocka")));
                    } else if (i <= 3) {
                        contentService.setCharacterImages(character.id(), (i + index) % 2 == 0
                                ? List.of(sketch(character, ImageKind.CHARACTER, DefaultKinds.SKETCH, "Skiss"),
                                sketch(character, ImageKind.CHARACTER, DefaultKinds.MAKEUP, "Sminkskiss"))
                                : List.of(sketch(character, ImageKind.CHARACTER, DefaultKinds.SKETCH, "Skiss")));
                    }
                }
            }
            return created;
        }

        private ContentService.ImageRef sketch(final CharacterDto character, final ImageKind kind, final UUID kindId, final String caption) {
            return new ContentService.ImageRef(image(kind, 700, 1000, caption, character.name()).getUuid(), kindId, caption);
        }

        private void scenes(final SpexDto spex) {
            final StringBuilder text = new StringBuilder("Akt 1\n");

            for (int i = 0; i < 4; i++) {
                text.append("Scen %d – %s\n".formatted(i + 1, PLACES[(i + spex.year().hashCode() & 0xff) % PLACES.length]));
            }
            text.append("--\nAkt 2\n");
            for (int i = 4; i < 7; i++) {
                text.append("Scen %d – %s\n".formatted(i + 1, PLACES[(i + spex.year().hashCode() & 0xff) % PLACES.length]));
            }
            contentService.pasteScenes(spex.id(), text.toString());
        }

        private void numbers(final SpexDto spex, final List<CharacterDto> characters, final int index) {
            final int count = large ? 6 : 8 + index % 6;

            for (int i = 0; i < count; i++) {
                final String[] original = ORIGINALS[(index + i) % ORIGINALS.length];
                final List<ContentService.NumberCreditInput> credits = new ArrayList<>();

                credits.add(i % 3 == 2 ? new ContentService.NumberCreditInput(DefaultKinds.LYRICS, null, "Okänd textförfattare")
                        : new ContentService.NumberCreditInput(DefaultKinds.LYRICS, spexare((index + i) % SPEXARE.size()), null));
                credits.add(new ContentService.NumberCreditInput(DefaultKinds.MUSIC, null, original[2]));
                credits.add(new ContentService.NumberCreditInput(DefaultKinds.ARRANGEMENT, spexare((index + i + 1) % SPEXARE.size()), null));
                if (i % 4 == 0) {
                    credits.add(new ContentService.NumberCreditInput(DefaultKinds.CHOREOGRAPHY, spexare(4), null));
                }
                if (index == 1 && i == 1) {
                    // An imported name nobody has resolved yet: only editors see it (spec 5.3)
                    credits.add(new ContentService.NumberCreditInput(DefaultKinds.CHOREOGRAPHY, null, "Kalle Koreograf", true));
                }
                contentService.createNumber(spex.id(), new ContentService.NumberInput(i < count / 2 ? 1 : 2, NUMBER_TITLES[i % NUMBER_TITLES.length],
                                i == 0 ? DefaultKinds.OVERTURE : i == count - 1 ? DefaultKinds.FINALE : DefaultKinds.SONG,
                                original[0], original[1], i % 5 == 1 ? "Mamma Mia!" : null,
                                "Hör hur %s sjunger i natten".formatted(NUMBER_TITLES[i % NUMBER_TITLES.length].toLowerCase(Locale.ROOT)),
                                credits,
                                List.of(characters.get(i % characters.size()).id(), characters.get((i + 1) % characters.size()).id())),
                        uuid("number", spex.id().toString(), String.valueOf(i)));
            }
        }

        // Revivals -----------------------------------------------------------------------------------------------------

        /**
         * Lasse-Maja's revivals: one cuts a character and a number and moves another, and adds a character and a number
         * of its own; one has a page of its own; one has none.
         */
        private void revivals(final List<SpexDto> all) {
            final List<SpexDto> lasseMaja = all.stream().filter(spex -> "Lasse-Maja".equals(spex.title()) && spex.revival())
                    .sorted(java.util.Comparator.comparing(SpexDto::year)).toList();

            if (lasseMaja.size() < 2) {
                return;
            }

            final SpexDto revival = lasseMaja.getFirst();
            final SpexDto original = baseData.spexByKey(revival.parentKey());
            final List<CharacterDto> characters = contentService.characters(original.key());
            final List<NumberDto> numbers = contentService.numbers(original.key());

            contentService.setRevivalChanges(revival.id(), RevivalChangeKind.CHARACTER,
                    List.of(new RevivalChange(RevivalChangeKind.CHARACTER, characters.getLast().id(), true, null, null)));
            contentService.setRevivalChanges(revival.id(), RevivalChangeKind.NUMBER, List.of(
                    new RevivalChange(RevivalChangeKind.NUMBER, numbers.get(1).id(), true, null, null),
                    new RevivalChange(RevivalChangeKind.NUMBER, numbers.getLast().id(), false, 1, 0)));
            contentService.createCharacter(revival.id(), "Turisten", "Ny i nyuppsättningen: en vilsen turist från framtiden.",
                    uuid("character", revival.id().toString(), "0"));
            contentService.createNumber(revival.id(), new ContentService.NumberInput(2, "Turistens tango", DefaultKinds.SONG, "Euphoria", "Loreen",
                            null, null, List.of(new ContentService.NumberCreditInput(DefaultKinds.LYRICS, spexare(0), null),
                            new ContentService.NumberCreditInput(DefaultKinds.MUSIC, null, "Thomas G:son")), null),
                    uuid("number", revival.id().toString(), "0"));
            pageService.update(revival.id(), new SpexPageService.PageInput(null,
                    "Nyuppsättningen av Lasse-Maja, med en turist från framtiden.", null, null, null, null, null, null));
            baseData.setPoster(revival.id(), image(ImageKind.POSTER, 900, 1260, "Nyuppsättning " + revival.year(), "Lasse-Maja").getUuid());
            pageService.setPublished(revival.id(), true);
            pageService.setPublished(lasseMaja.get(1).id(), true);
        }

        // Articles -----------------------------------------------------------------------------------------------------

        private void articles() {
            final Map<String, UUID> categories = new LinkedHashMap<>();

            for (final String name : List.of("Traditioner", "Organisationer", "Turnéer", "Anekdoter", "Spexikon", "Adrianpokalen")) {
                categories.put(name, articleService.createCategory(name, "Artiklar om " + name.toLowerCase(Locale.ROOT) + ".").id());
            }
            // Categories nest too
            categories.put("Sånger", articleService.createCategory("Sånger", "Sångerna som hör till traditionerna.",
                    List.of(categories.get("Traditioner"))).id());
            categories.put("Västen", articleService.createCategory("Västen", "Allt om västen.",
                    List.of(categories.get("Traditioner"))).id());

            // Articles form a tree, up to three levels deep here -----------------------------------------------------

            final ArticleDto traditions = article("Spexets traditioner", Visibility.PUBLIC, true, null, List.of(categories.get("Traditioner")),
                    "Chalmersspexet har många traditioner. Den viktigaste är **tackkören**, som sjungs efter varje föreställning.");

            update(traditions, """
                    Chalmersspexet har många traditioner. Den viktigaste är **tackkören**, som sjungs efter varje föreställning.
                    
                    ## Översikt
                    
                    | Tradition | Sedan |
                    |-----------|-------|
                    | Tackkören | 1948  |
                    | Västen    | 1960  |
                    
                    Läs mer i underartiklarna nedan. Se även [[FGV]] och [[Spexets historia]].""", "Lade till en tabell");
            article("Tackkören", Visibility.PUBLIC, true, traditions, List.of(categories.get("Traditioner"), categories.get("Sånger")), """
                    Tackkören sjungs av hela ensemblen efter varje föreställning, med publiken stående.
                    
                    ## Texten
                    
                    Texten har ändrats några gånger genom åren, men refrängen är densamma sedan premiären 1948.
                    
                    ## Se även
                    
                    - [[Spexets traditioner]]
                    - [[Ouvertyren]]""");
            article("Ouvertyren", Visibility.PUBLIC, true, traditions, List.of(categories.get("Sånger")),
                    "Orkestern spelar ouvertyren innan ridån går upp. Den är skriven på nytt för varje spex.");

            final ArticleDto vest = article("Västen", Visibility.PUBLIC, true, traditions, List.of(categories.get("Västen")), """
                    Västen är spexets mest kända symbol. Varje spex har sin egen väst, som bärs av den som gjort spexet störst tjänst.
                    
                    Se [[Västens färger]] och [[Västbärare]].""");
            article("Västens färger", Visibility.PUBLIC, true, vest, List.of(categories.get("Västen")), """
                    Varje väst har spexets egna färger. Ordningen går igen i affischen och i programmet.
                    
                    | Decennium | Vanligaste färg |
                    |-----------|-----------------|
                    | 1960-talet | Röd |
                    | 1980-talet | Blå |
                    | 2000-talet | Grön |""");
            article("Västbärare", Visibility.MEMBERS, true, vest, List.of(categories.get("Västen")),
                    "Västbärarna väljs av den förra västbäraren. Listan är bara för medlemmar.");
            article("Västens vård", Visibility.PUBLIC, false, vest, List.of(categories.get("Västen")),
                    "Utkast: hur västarna tvättas och förvaras.");
            article("Adrianpokalen", Visibility.PUBLIC, true, traditions, List.of(categories.get("Adrianpokalen"), categories.get("Traditioner")),
                    "Adrianpokalen delas ut till den som under året gjort spexet störst tjänst. Ingen vet vem Adrian var.");
            article("Ölrepet", Visibility.MEMBERS, false, traditions, List.of(categories.get("Traditioner")), "Utkast om ölrepet.");

            final ArticleDto organisations = article("Organisationerna kring spexet", Visibility.PUBLIC, true, null,
                    List.of(categories.get("Organisationer")), "Spexet är inte ensamt. Här är föreningarna och grupperna runt omkring.");
            final ArticleDto fgv = article("FGV", Visibility.PUBLIC, true, organisations, List.of(categories.get("Organisationer")), """
                    Föreningen Gamla Västar, FGV, samlar alla som varit med i Chalmersspexet.
                    
                    ## Verksamhet
                    
                    FGV håller [[Spexarkivet]] och ordnar nyuppsättningar av gamla spex.""");
            article("FGV:s styrelse", Visibility.PUBLIC, true, fgv, List.of(categories.get("Organisationer")),
                    "Styrelsen väljs på årsmötet och består av ordförande, kassör, sekreterare och ledamöter.");
            article("Spexarkivet", Visibility.PUBLIC, true, fgv, List.of(categories.get("Organisationer")),
                    "Spexarkivet samlar manus, noter, affischer och program från alla spex sedan 1948.");
            article("Spexkommittén", Visibility.PUBLIC, true, organisations, List.of(categories.get("Organisationer")),
                    "Spexkommittén leder årets spex, från idé till sista föreställning. Se även [[FGV]].");

            final ArticleDto tours = article("Turnéer", Visibility.PUBLIC, true, null, List.of(categories.get("Turnéer")),
                    "Spexet har turnerat i Norden många gånger. Turnéerna står i tidsordning nedan.");
            article("Turnén till Helsingfors 1972", Visibility.PUBLIC, false, tours, List.of(categories.get("Turnéer")),
                    "Opublicerad: väntar på granskning av en författare.");
            article("Turnén till Stockholm 1985", Visibility.PUBLIC, true, tours, List.of(categories.get("Turnéer")),
                    "Spexet spelade tre föreställningar i Stockholm och reste dit med nattåg.");
            article("Turnén till Trondheim 1994", Visibility.PUBLIC, true, tours, List.of(categories.get("Turnéer")),
                    "Turnén till Trondheim gjordes tillsammans med studentersamfundet där.");

            article("Spexikon", Visibility.PUBLIC, true, null, List.of(categories.get("Spexikon")), """
                    | Ord | Betydelse |
                    |-----|-----------|
                    | Omstart | När publiken kräver att ett nummer tas om |
                    | Directeur | Den som leder spexet |""");

            final ArticleDto anecdotes = article("Anekdoter", Visibility.PUBLIC, true, null, List.of(categories.get("Anekdoter")),
                    "Historier från scenen och bakom den. De flesta är bara för medlemmar.");
            final ArticleDto anecdote = article("Anekdot: Kulissen som föll", Visibility.MEMBERS, true, anecdotes, List.of(categories.get("Anekdoter")),
                    "Under premiären föll en kuliss rakt ner på scenen. Ensemblen improviserade vidare som om ingenting hänt.");

            final ArticleDto withName = update(anecdote, "Under premiären föll en kuliss rakt ner på Basen. Ensemblen improviserade vidare.", "Lade till vem");

            // The revision that named someone is hidden; a later one leaves the name out
            revisionHidden(withName);
            update(withName, "Under premiären föll en kuliss rakt ner på scenen. Ensemblen improviserade vidare som om ingenting hänt.",
                    "Tog bort namnet igen");
            article("Anekdot: Den försvunna kostymen", Visibility.MEMBERS, true, anecdotes, List.of(categories.get("Anekdoter")),
                    "En kostym försvann mellan två föreställningar och hittades året efter i en kuliss.");
            // Written by user@fgv.nu (its fixed id in fgv-infra-deploy's sample-users.tsv), as far as the history tells
            jdbc.sql("UPDATE revision SET author = :author, author_name = 'User Exempel' WHERE target = 'ARTICLE' AND comment IN ('Lade till vem', 'Tog bort namnet igen')")
                    .param("author", USER_ID).update();
        }

        private ArticleDto article(final String title, final Visibility visibility, final boolean published, final @Nullable ArticleDto parent,
                                   final List<UUID> categories, final String body) {
            final ArticleDto article = articleService.create(new ArticleService.ArticleInput(title, body, visibility, categories, "Skapade artikeln",
                    parent == null ? null : parent.id()));

            if (published) {
                articleService.setPublished(article.id(), true);
            }
            return article;
        }

        private ArticleDto update(final ArticleDto article, final String body, final String comment) {
            return articleService.update(article.id(), new ArticleService.ArticleInput(article.title(), body, article.visibility(), null, comment), null);
        }

        private void revisionHidden(final ArticleDto article) {
            jdbc.sql("UPDATE revision SET hidden = 1, hidden_by = 'sample-data' WHERE target = 'ARTICLE' AND target_id = :id ORDER BY id DESC LIMIT 1")
                    .param("id", article.id().toString()).update();
        }

        // Redirects, retired and merged records ------------------------------------------------------------------------

        private void redirects() {
            redirectService.put(null, "Huvudsida", "/");
            redirectService.put(null, "Lasse-Maja", "/spex/1984-lasse-maja");
            redirectService.put(null, "Spexets_traditioner", "/articles/spexets-traditioner");
            redirectService.put(null, "Spexets tradditioner", "/articles/spexets-traditioner");
            redirectService.put(null, "Kategori:Anekdoter", "/article-categories/anekdoter");
        }

        private void retiredAndMerged() {
            final UUID test = uuid("spex-category", "Testspexet");

            baseData.importCategory(test, "Testspexet", "1999", false);
            mergeService.retire(BaseDataKind.SPEX_CATEGORY, test, true);

            final UUID wrong = uuid("spex", "1999", "Felregistrerat spex");

            // Retired at once: it shares its year with the right one
            baseData.importSpex(wrong, "1999", "Felregistrerat spex", categories.get("Gamla Chalmersspexet"), null, true);
            mergeService.retire(BaseDataKind.SPEX, wrong, true);

            final UUID directeur = jdbc.sql("SELECT t.uuid FROM task t JOIN task_category c ON c.id = t.category_id WHERE t.name = 'Directeur' AND c.name = 'Kommitté'")
                    .query(String.class).optional().map(UUID::fromString).orElseThrow();
            final TaskDto duplicate = baseData.importTask(uuid("task", "Kommitté", "Direktör"), "Direktör", taskCategories.get("Kommitté"), true);

            mergeService.merge(BaseDataKind.TASK, duplicate.id(), directeur);
        }
    }
}
