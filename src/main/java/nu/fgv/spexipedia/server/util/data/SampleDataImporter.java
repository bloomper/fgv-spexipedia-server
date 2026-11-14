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

import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import nu.fgv.spexipedia.server.article.ArticleDtos.ArticleCategoryDto;
import nu.fgv.spexipedia.server.article.ArticleDtos.ArticleDto;
import nu.fgv.spexipedia.server.article.ArticleService;
import nu.fgv.spexipedia.server.article.Visibility;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.TaskDto;
import nu.fgv.spexipedia.server.basedata.BaseDataKind;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.basedata.MergeService;
import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.content.ContentDtos.CharacterDto;
import nu.fgv.spexipedia.server.content.ContentDtos.NumberDto;
import nu.fgv.spexipedia.server.content.ContentDtos.RecordingDto;
import nu.fgv.spexipedia.server.content.ContentService;
import nu.fgv.spexipedia.server.content.ListStore.CreditRef;
import nu.fgv.spexipedia.server.content.ListStore.RevivalChange;
import nu.fgv.spexipedia.server.content.ListStore.RevivalChangeKind;
import nu.fgv.spexipedia.server.content.Medium;
import nu.fgv.spexipedia.server.content.RecordingService;
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
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
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
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Replaces everything with sample data at every start while {@code spexipedia.data.import-sample-data} is set
 * (section 5.13 of the spec); refuses to run in the {@code production} profile.
 *
 * <p>Spexipedia comes first: it is the source of the sample spex, categories and tasks, with fixed ids made from their
 * names, which Spexregister copies from the change feed and the other applications read. Images are generated. Credits
 * refer to a few spexare that Spexregister's sample data creates with the same fixed ids ({@link #SPEXARE}), published
 * and unpublished, with and without nicknames, so that section 5.3 can be tested; who played which character comes from
 * Spexregister's sample actors, which it links to these characters.</p>
 *
 * <p>Everything is made through the services, as a sample administrator, so the feed, the audit log and the search
 * index are as in use. The feed's sequence keeps counting across imports, so consumers read everything again.</p>
 *
 * <p>Spexregister deletes every Keycloak user when it imports its sample data, so the sample users' roles are granted
 * in the background once its users exist: start Spexipedia, then Spexregister, then the other applications.</p>
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
    // Spexregister's sample users, which its sample data import creates; they get roles here
    static final String ADMIN = "admin@fgv.nu";
    static final String EDITOR = "editor@fgv.nu";
    static final String USER = "user@fgv.nu";
    // Created here, with USER and AUTHOR
    static final String AUTHOR = "author@fgv.nu";
    private static final List<String> TABLES = List.of("article_editing", "article_article_category", "article", "article_category",
            "revision", "redirect", "search_document", "recording_track", "recording", "number_character", "number_credit",
            "spex_number", "character_image", "spex_character", "figure", "scene", "revival_change", "spex_page_photo",
            "spex_page_image", "spex_page", "spex", "spex_category", "task", "task_category", "id_alias", "sync_consumer",
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
    private static final Map<String, String> FIGURES = Map.of(
            "Napoleon", "Kejsare av Frankrike och återkommande gestalt i spexen, oftast kortare än han själv tror.",
            "Kleopatra", "Egyptens drottning; spexens favorit bland antikens härskare.",
            "Gustav Vasa", "Landsfader och skidlöpare.",
            "Bellman", "Carl Michael Bellman, skald och flitig gäst i spex om 1700-talet.");

    private final SpexipediaProperties properties;
    private final Environment environment;
    private final JdbcClient jdbc;
    private final BaseDataService baseData;
    private final MergeService mergeService;
    private final ContentService contentService;
    private final RecordingService recordingService;
    private final SpexPageService pageService;
    private final ArticleService articleService;
    private final ImageService imageService;
    private final RedirectService redirectService;
    private final SettingsService settingsService;
    private final SyncConsumerService consumerService;
    private final SearchService searchService;
    private final Keycloak keycloak;

    @SuppressWarnings("java:S107")
    public SampleDataImporter(final SpexipediaProperties properties, final Environment environment, final JdbcClient jdbc,
                              final BaseDataService baseData, final MergeService mergeService,
                              final ContentService contentService, final RecordingService recordingService,
                              final SpexPageService pageService, final ArticleService articleService,
                              final ImageService imageService, final RedirectService redirectService,
                              final SettingsService settingsService, final SyncConsumerService consumerService,
                              final SearchService searchService, final Keycloak keycloak) {
        this.properties = properties;
        this.environment = environment;
        this.jdbc = jdbc;
        this.baseData = baseData;
        this.mergeService = mergeService;
        this.contentService = contentService;
        this.recordingService = recordingService;
        this.pageService = pageService;
        this.articleService = articleService;
        this.imageService = imageService;
        this.redirectService = redirectService;
        this.settingsService = settingsService;
        this.consumerService = consumerService;
        this.searchService = searchService;
        this.keycloak = keycloak;
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
        Executors.newVirtualThreadPerTaskExecutor().execute(this::grantRolesWhenUsersExist);
    }

    public void importSampleData() {
        final long started = System.nanoTime();
        final boolean large = "large".equalsIgnoreCase(properties.data().sampleDataSize());

        log.info("Importing {} sample data", large ? "large" : "small");
        wipe();
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

    // Users --------------------------------------------------------------------------------------------------------------

    /**
     * Waits for Spexregister's sample users (for up to half an hour), then gives them their roles on this client and
     * creates {@code author@fgv.nu}. Keycloak's admin notification is suspended meanwhile.
     */
    private void grantRolesWhenUsersExist() {
        final Instant deadline = Instant.now().plus(Duration.ofMinutes(30));

        while (Instant.now().isBefore(deadline)) {
            try {
                final RealmResource realm = keycloak.realm(properties.keycloak().realm());

                if (!realm.users().searchByEmail(ADMIN, true).isEmpty()) {
                    NotificationSuspender.whileSuspended(realm, () -> {
                        grantRoles(realm);
                        return null;
                    });
                    log.info("Gave the sample users their roles");
                    return;
                }
                log.info("Waiting for Spexregister's sample users");
            } catch (final RuntimeException e) {
                log.info("Waiting for Keycloak and Spexregister's sample users: {}", e.getMessage());
            }
            try {
                Thread.sleep(Duration.ofSeconds(15));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("Spexregister's sample users never appeared; restart Spexipedia after Spexregister to give them roles");
    }

    private void grantRoles(final RealmResource realm) {
        final ClientResource client = realm.clients().get(realm.clients().findByClientId(SecurityUtil.CLIENT_ID).getFirst().getId());

        grant(realm, client, ADMIN, "USER", "AUTHOR", "EDITOR", "ADMIN");
        grant(realm, client, EDITOR, "USER", "AUTHOR", "EDITOR");
        grant(realm, client, USER, "USER");
        if (realm.users().searchByEmail(AUTHOR, true).isEmpty()) {
            final CredentialRepresentation password = new CredentialRepresentation();
            final UserRepresentation user = new UserRepresentation();

            password.setType(CredentialRepresentation.PASSWORD);
            password.setValue(properties.data().sampleUserPassword());
            password.setTemporary(false);
            user.setUsername(AUTHOR);
            user.setEmail(AUTHOR);
            user.setFirstName("Agnes");
            user.setLastName("Author");
            user.setEnabled(true);
            user.setEmailVerified(true);
            user.setCredentials(List.of(password));
            try (Response response = realm.users().create(user)) {
                if (response.getStatus() >= 300) {
                    log.warn("Could not create {}: {}", AUTHOR, response.getStatus());
                    return;
                }
            }
        }
        grant(realm, client, AUTHOR, "USER", "AUTHOR");
    }

    private static void grant(final RealmResource realm, final ClientResource client, final String email, final String... roles) {
        final List<UserRepresentation> users = realm.users().searchByEmail(email, true);

        if (users.isEmpty()) {
            log.warn("The sample user {} is missing", email);
            return;
        }

        final List<RoleRepresentation> representations = Arrays.stream(roles).map(role -> client.roles().get(role).toRepresentation()).toList();

        realm.users().get(users.getFirst().getId()).roles().clientLevel(client.toRepresentation().getId()).add(representations);
    }

    // Content ------------------------------------------------------------------------------------------------------------

    private final class Generator {

        private final boolean large;
        private final Map<String, UUID> categories = new LinkedHashMap<>();
        private final Map<String, UUID> taskCategories = new LinkedHashMap<>();
        private final Map<String, UUID> figures = new HashMap<>();
        private final List<Image> pool = new ArrayList<>();
        private int images;

        Generator(final boolean large) {
            this.large = large;
        }

        void generate() {
            baseData();
            settings();
            consumers();
            figures();

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
                    pageService.update(spex.id(), new SpexPageService.PageInput(generation(spex), null,
                            "%s – ett spex från %s.".formatted(spex.title(), spex.year()), null, null, null, null, null));
                    if (large) {
                        pageService.setPublished(spex.id(), true);
                    }
                }
            }
            revivals(all);
            recordings(all);
            articles();
            redirects();
            retiredAndMerged();
            searchService.rebuild();
        }

        // Base data ----------------------------------------------------------------------------------------------------

        private void baseData() {
            for (final String[] row : rows("spex-categories.tsv")) {
                final UUID id = uuid("spex-category", row[0]);

                baseData.importCategory(id, row[0], row[1], false);
                categories.put(row[0], id);
            }

            final Map<String, UUID> originals = new HashMap<>();

            for (final String[] row : rows("spex.tsv")) {
                final String year = row[0];
                final String title = row[1];

                if (row.length < 4 || row[3].isEmpty()) {
                    final UUID id = uuid("spex", year, title);

                    baseData.importSpex(id, year, title, categories.get(row[2]), null, false);
                    originals.put(title + "|" + year, id);
                } else {
                    final UUID parent = originals.get(title + "|" + row[3]);

                    if (parent != null) {
                        baseData.importSpex(uuid("spex", year, title, "revival"), year, null, null, parent, false);
                    }
                }
            }
            for (final String[] row : rows("task-categories.tsv")) {
                final UUID id = uuid("task-category", row[0]);

                // The committee is shown as the board on spex pages; the musicians' tasks are Spexarkivet's instruments
                baseData.importTaskCategory(id, row[0], "1".equals(row[1]), "Kommitté".equals(row[0]),
                        Set.of("Orkester", "Bandet", "Symphonin").contains(row[0]));
                taskCategories.put(row[0], id);
            }
            for (final String[] row : rows("tasks.tsv")) {
                baseData.importTask(uuid("task", row[1], row[0]), row[0], taskCategories.get(row[1]));
            }
        }

        private List<String[]> rows(final String file) {
            try {
                return new String(new ClassPathResource("sampledata/" + file).getContentAsByteArray(), StandardCharsets.UTF_8)
                        .lines().filter(line -> !line.isBlank()).map(line -> line.split("\t", -1)).toList();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void settings() {
            settingsService.save(new SettingsService.SettingsDto(
                    "Välkommen till Spexipedia! Här finns allt om spexen, och som inloggad ser du även vilka som var med.",
                    "Welcome to Spexipedia! Everything about the spex, and as a member you also see who took part.",
                    "Spexipedia är FGV:s uppslagsverk om Chalmersspexet och de andra spexen. Exempeldata – inget här är på riktigt.",
                    "Spexipedia is FGV's encyclopedia of the spex. Sample data – nothing here is real.",
                    "spexipedia@fgv.nu", null), "sample-data");
        }

        private void consumers() {
            consumerService.register("Spexregister", "spexregister-service", "http://spexregister-api:8080/api/sync/notify");
            consumerService.register("Spexify", "fgv-spexify-service", "http://spexify-api:8080/api/sync/notify");
            consumerService.register("Spexflix", "fgv-spexflix-service", "http://spexflix-api:8080/api/sync/notify");
            consumerService.register("Spexgram", "fgv-spexgram-service", "http://spexgram-api:8080/api/sync/notify");
            consumerService.register("Spexarkivet", "fgv-archive-service", "http://archive-api:8080/api/sync/notify");
        }

        private void figures() {
            FIGURES.forEach((name, description) -> figures.put(name, contentService.createFigure(name, description).id()));
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

        private String generation(final SpexDto spex) {
            final int year = Integer.parseInt(spex.year());

            return year < 2003 ? "Gamla Chalmersspexet" : year < 2020 ? "Bob & Vera" : "Nya Chalmersspexet";
        }

        /**
         * A page with every section filled (full), or only a few.
         */
        private void page(final SpexDto spex, final int index, final boolean full) {
            baseData.setPoster(spex.id(), image(ImageKind.POSTER, 900, 1260, "Chalmersspexet " + spex.year(), spex.title()).getUuid());

            final List<CharacterDto> characters = characters(spex, index);

            scenes(spex);
            numbers(spex, characters, index);
            pageService.update(spex.id(), new SpexPageService.PageInput(generation(spex),
                    index == 0 ? LocalDate.now().withYear(Integer.parseInt(spex.year())).toString()
                            : "%s-02-%02d".formatted(spex.year(), 1 + index % 27),
                    "%s – ett spex om makt, kärlek och en och annan felräknad tronföljd.".formatted(spex.title()),
                    description(spex), full ? synopsis(spex) : null,
                    full ? "Västen till %s är broderad i guld på mörkgrönt, med %s som motiv.".formatted(spex.title(), spex.title().toLowerCase(Locale.ROOT)) : null,
                    full ? new CreditRef(spexare(index % SPEXARE.size()), null) : null, null));
            if (full) {
                pageService.setImages(spex.id(), "BACKDROP", List.of(placed(ImageKind.SCENOGRAPHY, "Fond: " + PLACES[index % PLACES.length], spex),
                        placed(ImageKind.SCENOGRAPHY, "Fond: " + PLACES[(index + 1) % PLACES.length], spex)));
                pageService.setImages(spex.id(), "STAGE", List.of(placed(ImageKind.SCENOGRAPHY, "Scenbygget", spex)));
                pageService.setImages(spex.id(), "PROPS", List.of(placed(ImageKind.SCENOGRAPHY, "Kronan", spex),
                        placed(ImageKind.SCENOGRAPHY, "Tronen", spex)));
                pageService.setImages(spex.id(), "VEST", List.of(placed(ImageKind.VEST, "Västen, framsida", spex)));
                pageService.setImages(spex.id(), "OTHER", List.of(placed(ImageKind.OTHER, "Programbladet", spex)));
                pageService.setRebus(spex.id(), image(ImageKind.REBUS, 1000, 700, "Programrebus", "☺ + 🎭 = ?").getUuid(),
                        image(ImageKind.REBUS, 1000, 700, "Lösning", spex.title()).getUuid());
            }
            if (index == 0) {
                // Spexgram's sample photos of its first album, about the earliest spex: public ones and members' ones
                pageService.setPhotos(spex.id(), "GROUP", null, List.of(spexgramItem(1), spexgramItem(2), spexgramItem(21), spexgramItem(22)));
                pageService.setPhotos(spex.id(), "BOARD", null, List.of(spexgramItem(3), spexgramItem(23)));
                pageService.setPhotos(spex.id(), "ACTOR", characters.getFirst().key(), List.of(spexgramItem(4), spexgramItem(24)));
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
                    **%s** sattes upp av %s %s. Exempeldata: texten är påhittad, men visar hur en sida ser ut med *Markdown*, \
                    länkar till [[Spexets traditioner]] och till [andra spex](/spex).

                    Spexet minns för sin finalkör, som publiken krävde tre gånger, och för en kuliss som föll i fel ögonblick \
                    – se [[Anekdot: Kulissen som föll|anekdoten]]. Åäö fungerar.""".formatted(spex.title(), generation(spex), spex.year());
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
            final UUID figure = figures.entrySet().stream().filter(entry -> spex.title().contains(entry.getKey().split(" ")[0]))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            final int count = large ? 4 : 5 + index % 4;

            created.add(contentService.createCharacter(spex.id(), titleRole, figure,
                    "Titelrollen. %s är både hjälte och skurk, beroende på akt.".formatted(titleRole), uuid("character", spex.id().toString(), "0")));
            for (int i = 0; i < count; i++) {
                final String role = ROLES[(index + i) % ROLES.length];
                final UUID roleFigure = i == 1 && index % 3 == 0 ? figures.get("Bellman") : null;
                final String name = roleFigure != null ? "Bellman" : role;

                created.add(contentService.createCharacter(spex.id(), name, roleFigure,
                        i % 2 == 0 ? "%s, som alla misstänker men ingen förstår.".formatted(name) : null,
                        uuid("character", spex.id().toString(), String.valueOf(i + 1))));
            }
            if (!large || index < 3) {
                final CharacterDto first = created.getFirst();

                contentService.setCharacterImages(first.id(), List.of(
                        new ContentService.ImageRef(image(ImageKind.SKETCH, 700, 1000, "Skiss", first.name()).getUuid(), ImageKind.SKETCH, "Skiss"),
                        new ContentService.ImageRef(image(ImageKind.MAKEUP, 700, 1000, "Sminkskiss", first.name()).getUuid(), ImageKind.MAKEUP, "Sminkskiss"),
                        new ContentService.ImageRef(image(ImageKind.COSTUME, 700, 1000, "Kostymdocka", first.name()).getUuid(), ImageKind.COSTUME, null)));
            }
            return created;
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
                final List<CreditRef> lyrics = i % 3 == 2 ? List.of(new CreditRef(null, "Okänd textförfattare"))
                        : List.of(new CreditRef(spexare((index + i) % SPEXARE.size()), null));

                contentService.createNumber(spex.id(), new ContentService.NumberInput(i < count / 2 ? 1 : 2, NUMBER_TITLES[i % NUMBER_TITLES.length],
                        original[0], original[1], lyrics, List.of(new CreditRef(null, original[2])),
                        List.of(new CreditRef(spexare((index + i + 1) % SPEXARE.size()), null)),
                        i % 4 == 0 ? List.of(new CreditRef(spexare(4), null)) : List.of(),
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
            contentService.createCharacter(revival.id(), "Turisten", null, "Ny i nyuppsättningen: en vilsen turist från framtiden.",
                    uuid("character", revival.id().toString(), "0"));
            contentService.createNumber(revival.id(), new ContentService.NumberInput(2, "Turistens tango", "Euphoria", "Loreen",
                    List.of(new CreditRef(spexare(0), null)), List.of(new CreditRef(null, "Thomas G:son")), List.of(), List.of(), null),
                    uuid("number", revival.id().toString(), "0"));
            pageService.update(revival.id(), new SpexPageService.PageInput("Gamla Chalmersspexet", null,
                    "Nyuppsättningen av Lasse-Maja, med en turist från framtiden.", null, null, null, null, null));
            baseData.setPoster(revival.id(), image(ImageKind.POSTER, 900, 1260, "Nyuppsättning " + revival.year(), "Lasse-Maja").getUuid());
            pageService.setPublished(revival.id(), true);
            pageService.setPublished(lasseMaja.get(1).id(), true);
        }

        // Recordings ---------------------------------------------------------------------------------------------------

        private void recordings(final List<SpexDto> all) {
            final SpexDto lasseMaja = all.stream().filter(spex -> "Lasse-Maja".equals(spex.title()) && !spex.revival()).findFirst().orElse(null);
            final SpexDto napoleon = all.stream().filter(spex -> "Napoleon".equals(spex.title()) && !spex.revival()).findFirst().orElse(null);

            if (lasseMaja == null || napoleon == null) {
                return;
            }

            final RecordingDto ep = recordingService.create(new RecordingService.RecordingInput("CEP 019", "Lasse-Maja", Medium.EP,
                    lasseMaja.id(), "1984-12-01", "1984-11", new CreditRef(spexare(2), null),
                    "Spexets EP, inspelad i kårhuset efter sista föreställningen.",
                    image(ImageKind.COVER, 1000, 1000, "CEP 019", "Lasse-Maja").getUuid(),
                    image(ImageKind.COVER, 1000, 1000, "Baksida", "CEP 019").getUuid(), null));

            recordingService.pasteTracks(ep.id(), "Sida A\n" + String.join("\n", contentService.numbers(lasseMaja.key()).stream()
                    .limit(3).map(NumberDto::title).toList()) + "\nSida B\n" + String.join("\n", contentService.numbers(lasseMaja.key()).stream()
                    .skip(3).limit(3).map(NumberDto::title).toList()));
            recordingService.setPublished(ep.id(), true);

            final RecordingDto lp = recordingService.create(new RecordingService.RecordingInput("CLP 100", "Det bästa från Chalmersspexet",
                    Medium.LP, null, "1988", null, new CreditRef(null, "Gästdirigent Harald Hornblåsare"), "En samlingsskiva.",
                    image(ImageKind.COVER, 1000, 1000, "CLP 100", "Det bästa").getUuid(), null, null));
            final List<RecordingService.TrackInput> tracks = new ArrayList<>();

            contentService.numbers(napoleon.key()).stream().limit(2).forEach(n -> tracks.add(new RecordingService.TrackInput("A", n.id(), null)));
            contentService.numbers(lasseMaja.key()).stream().limit(2).forEach(n -> tracks.add(new RecordingService.TrackInput("B", n.id(), null)));
            tracks.add(new RecordingService.TrackInput("B", null, "Applåder och extranummer"));
            recordingService.setTracks(lp.id(), tracks);
            recordingService.setPublished(lp.id(), true);
            recordingService.create(new RecordingService.RecordingInput("CCD 042", "Napoleon – jubileumsutgåva", Medium.CD, napoleon.id(),
                    "2006", "2006-03", null, "Opublicerad: väntar på omslaget.", null, null, null));
        }

        // Articles -----------------------------------------------------------------------------------------------------

        private void articles() {
            final Map<String, UUID> articleCategories = new LinkedHashMap<>();

            for (final String name : List.of("Traditioner", "Organisationer", "Turnéer", "Anekdoter", "Spexikon", "Adrianpokalen")) {
                final ArticleCategoryDto category = articleService.createCategory(name, "Artiklar om " + name.toLowerCase(Locale.ROOT) + ".");

                articleCategories.put(name, category.id());
            }

            final ArticleDto traditions = article("Spexets traditioner", Visibility.PUBLIC, true, List.of(articleCategories.get("Traditioner")),
                    "Chalmersspexet har många traditioner. Den viktigaste är **tackkören**, som sjungs efter varje föreställning.");

            update(traditions, """
                    Chalmersspexet har många traditioner. Den viktigaste är **tackkören**, som sjungs efter varje föreställning.

                    | Tradition | Sedan |
                    |-----------|-------|
                    | Tackkören | 1948  |
                    | Västen    | 1960  |

                    Se även [[Adrianpokalen]] och [[FGV]].""", "Lade till en tabell");
            article("Adrianpokalen", Visibility.PUBLIC, true, List.of(articleCategories.get("Adrianpokalen")),
                    "Adrianpokalen delas ut till den som under året gjort spexet störst tjänst. Ingen vet vem Adrian var.");
            article("FGV", Visibility.PUBLIC, true, List.of(articleCategories.get("Organisationer")),
                    "Föreningen Gamla Västar, FGV, samlar alla som varit med i Chalmersspexet.");
            article("Turnén till Helsingfors 1972", Visibility.PUBLIC, false, List.of(articleCategories.get("Turnéer")),
                    "Opublicerad: väntar på granskning av en författare.");
            article("Spexikon", Visibility.PUBLIC, true, List.of(articleCategories.get("Spexikon")), """
                    | Ord | Betydelse |
                    |-----|-----------|
                    | Omstart | När publiken kräver att ett nummer tas om |
                    | Directeur | Den som leder spexet |""");

            final ArticleDto anecdote = article("Anekdot: Kulissen som föll", Visibility.MEMBERS, true, List.of(articleCategories.get("Anekdoter")),
                    "Under premiären föll en kuliss rakt ner på scenen. Ensemblen improviserade vidare som om ingenting hänt.");

            final ArticleDto withName = update(anecdote, "Under premiären föll en kuliss rakt ner på Basen. Ensemblen improviserade vidare.", "Lade till vem");

            // The revision that named someone is hidden; a later one leaves the name out
            revisionHidden(withName);
            update(withName, "Under premiären föll en kuliss rakt ner på scenen. Ensemblen improviserade vidare som om ingenting hänt.",
                    "Tog bort namnet igen");
            article("Ölrepet", Visibility.MEMBERS, false, List.of(articleCategories.get("Traditioner")), "Utkast om ölrepet.");
            // Written by user@fgv.nu, as far as the history tells
            jdbc.sql("UPDATE revision SET author = 'sample-user', author_name = 'Ulla Användare' WHERE target = 'ARTICLE' AND comment IN ('Lade till vem', 'Tog bort namnet igen')").update();
        }

        private ArticleDto article(final String title, final Visibility visibility, final boolean published, final List<UUID> categories,
                                   final String body) {
            final ArticleDto article = articleService.create(new ArticleService.ArticleInput(title, body, visibility, categories, "Skapade artikeln"));

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
            redirectService.put(null, "Alexander - CEP 019", "/recordings/cep-019");
            redirectService.put(null, "Spexets_traditioner", "/articles/spexets-traditioner");
            redirectService.put(null, "Spexets tradditioner", "/articles/spexets-traditioner");
            redirectService.put(null, "Kategori:Anekdoter", "/article-categories/anekdoter");
        }

        private void retiredAndMerged() {
            final UUID test = uuid("spex-category", "Testspexet");

            baseData.importCategory(test, "Testspexet", "1999", false);
            mergeService.retire(BaseDataKind.SPEX_CATEGORY, test, true);

            final UUID wrong = uuid("spex", "1999", "Felregistrerat spex");

            baseData.importSpex(wrong, "1999", "Felregistrerat spex", categories.get("Gamla Chalmersspexet"), null, false);
            mergeService.retire(BaseDataKind.SPEX, wrong, true);

            final UUID directeur = uuid("task", "Kommitté", "Directeur");
            final TaskDto duplicate = baseData.importTask(uuid("task", "Kommitté", "Direktör"), "Direktör", taskCategories.get("Kommitté"));

            mergeService.merge(BaseDataKind.TASK, duplicate.id(), directeur);
        }
    }
}
