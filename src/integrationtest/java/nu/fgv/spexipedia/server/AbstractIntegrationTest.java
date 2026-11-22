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

package nu.fgv.spexipedia.server;

import com.github.tomakehurst.wiremock.WireMockServer;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL and Keycloak (with the committed copy of {@code fgv-keycloak}'s realm plus fictitious users) in Testcontainers;
 * WireMock stands in for Spexregister, Spexgram and the other applications, and for their consumers' notify endpoints.
 * Every test starts from empty tables, except the settings, and from empty caches.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integrationtest")
public abstract class AbstractIntegrationTest {

    protected static final String DOMAIN = "@spexipedia.test";
    protected static final String MEMBER = "medlem" + DOMAIN;
    // A second member without roles: for what one member writes and another may not see
    protected static final String OTHER_MEMBER = "medlem2" + DOMAIN;
    protected static final String AUTHOR = "forfattare" + DOMAIN;
    protected static final String EDITOR = "redaktor" + DOMAIN;
    protected static final String ADMIN = "admin" + DOMAIN;
    protected static final String PASSWORD = "s3cr3t-s3cr3t";
    protected static final String CLIENT_ID = "fgv-spexipedia";
    protected static final String MCP_CLIENT_ID = "fgv-spexipedia-mcp";
    // A public client in the MCP allow-list whose tokens lack the MCP endpoint as audience
    protected static final String UNBOUND_MCP_CLIENT_ID = "fgv-spexipedia-mcp-unbound";
    protected static final String MCP_PATH = "/api/mcp";
    protected static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());
    protected static final Path DATA = Path.of("build/integrationtest-data").toAbsolutePath();
    private static final String REALM = "fgv";
    private static final JacksonJsonParser JSON = new JacksonJsonParser();
    private static final Map<String, String> SECRETS = clientSecrets();
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();
    private static final List<String> TABLES = List.of("import_key", "spex_package", "spex_fact", "article_link",
            "article_category_parent", "article_editing", "article_article_category", "article", "article_category",
            "revision", "redirect", "search_document", "number_character", "number_credit",
            "spex_number", "character_image", "spex_character", "scene", "revival_change", "spex_page_photo",
            "spex_page_image", "spex_section", "spex_page", "spex", "spex_category", "task", "task_category", "id_alias", "sync_consumer",
            "change_feed_entry", "image", "audit_entry", "app_user");

    @ServiceConnection
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withCommand("mysqld", "--innodb-flush-log-at-trx-commit=0", "--innodb-doublewrite=0", "--skip-log-bin", "--performance-schema=OFF");
    private static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.8").withRealmImportFile("/realm/fgv.json");

    static {
        Startables.deepStart(MYSQL, KEYCLOAK).join();
        WIREMOCK.start();
        prepareKeycloak();
    }

    @LocalServerPort
    protected int localPort;
    @Autowired
    protected JdbcClient jdbcClient;
    @Autowired
    protected DataSource dataSource;
    @Autowired
    private CacheManager cacheManager;

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM);
        registry.add("spexipedia.keycloak.url", KEYCLOAK::getAuthServerUrl);
        registry.add("spexipedia.keycloak.service-client-secret", () -> SECRETS.get("fgv-spexipedia-service"));
        registry.add("spexipedia.apps.spexregister.graphql-url", () -> WIREMOCK.baseUrl() + "/spexregister/graphql");
        registry.add("spexipedia.apps.spexgram.graphql-url", () -> WIREMOCK.baseUrl() + "/spexgram/graphql");
        registry.add("spexipedia.apps.spexify.graphql-url", () -> WIREMOCK.baseUrl() + "/spexify/graphql");
        registry.add("spexipedia.apps.spexflix.graphql-url", () -> "");
        registry.add("spexipedia.apps.archive.graphql-url", () -> "");
    }

    /**
     * The client secrets in the committed realm copy, which are not real secrets.
     */
    private static Map<String, String> clientSecrets() {
        try (InputStream stream = AbstractIntegrationTest.class.getResourceAsStream("/realm/fgv.json")) {
            final Map<String, Object> realm = JSON.parseMap(new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8));
            final Map<String, String> secrets = new ConcurrentHashMap<>();

            for (final Object client : (List<?>) realm.get("clients")) {
                if (client instanceof Map<?, ?> map && map.get("secret") instanceof String secret) {
                    secrets.put(String.valueOf(map.get("clientId")), secret);
                }
            }
            return secrets;
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void prepareKeycloak() {
        try (Keycloak admin = KEYCLOAK.getKeycloakAdminClient()) {
            final RealmResource realm = admin.realm(REALM);
            final ClientRepresentation web = realm.clients().findByClientId(CLIENT_ID).getFirst();

            web.setDirectAccessGrantsEnabled(true);
            realm.clients().get(web.getId()).update(web);

            final ClientRepresentation mcp = realm.clients().findByClientId(MCP_CLIENT_ID).getFirst();

            mcp.setDirectAccessGrantsEnabled(true);
            mcp.setConsentRequired(false);
            realm.clients().get(mcp.getId()).update(mcp);

            final ClientRepresentation unbound = new ClientRepresentation();

            unbound.setClientId(UNBOUND_MCP_CLIENT_ID);
            unbound.setPublicClient(true);
            unbound.setDirectAccessGrantsEnabled(true);
            realm.clients().create(unbound).close();
            // USER comes with the realm's default role, as for every approved user
            createUser(realm, MEMBER, "Lisa", List.of(), List.of("fgv-spexregister"));
            createUser(realm, OTHER_MEMBER, "Olle", List.of(), List.of());
            createUser(realm, AUTHOR, "Agnes", List.of("AUTHOR"), List.of());
            createUser(realm, EDITOR, "Erik", List.of("EDITOR"), List.of("fgv-spexgram"));
            createUser(realm, ADMIN, "Anna", List.of("ADMIN"), List.of());
        }
    }

    private static void createUser(final RealmResource realm, final String email, final String firstName, final List<String> roles,
                                   final List<String> otherApps) {
        final CredentialRepresentation credential = new CredentialRepresentation();
        final UserRepresentation user = new UserRepresentation();

        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(PASSWORD);
        credential.setTemporary(false);
        user.setUsername(email);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName("Test");
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setCredentials(List.of(credential));

        try (Response response = realm.users().create(user)) {
            final String path = response.getLocation().getPath();
            final String userId = path.substring(path.lastIndexOf('/') + 1);

            grantClientRoles(realm, userId, CLIENT_ID, roles);
            for (final String app : otherApps) {
                grantClientRoles(realm, userId, app, List.of("USER"));
            }
        }
    }

    private static void grantClientRoles(final RealmResource realm, final String userId, final String clientId, final List<String> roles) {
        if (roles.isEmpty()) {
            return;
        }

        final ClientResource client = realm.clients().get(realm.clients().findByClientId(clientId).getFirst().getId());
        final List<RoleRepresentation> representations = roles.stream().map(role -> client.roles().get(role).toRepresentation()).toList();

        realm.users().get(userId).roles().clientLevel(client.toRepresentation().getId()).add(representations);
    }

    /**
     * The client roles a user has on {@code fgv-spexipedia}, its default role included, or empty when the user does not
     * exist.
     */
    protected static List<String> spexipediaRoles(final String email) {
        try (Keycloak admin = KEYCLOAK.getKeycloakAdminClient()) {
            final RealmResource realm = admin.realm(REALM);
            final List<UserRepresentation> users = realm.users().searchByEmail(email, true);

            if (users.isEmpty()) {
                return List.of();
            }

            final String clientUuid = realm.clients().findByClientId(CLIENT_ID).getFirst().getId();

            return realm.users().get(users.getFirst().getId()).roles().clientLevel(clientUuid).listEffective().stream()
                    .map(RoleRepresentation::getName).sorted().toList();
        }
    }

    /**
     * A user with roles on Spexregister's client only, as Spexregister's sample data creates them.
     */
    protected static void createSpexregisterUser(final String email) {
        try (Keycloak admin = KEYCLOAK.getKeycloakAdminClient()) {
            createUser(admin.realm(REALM), email, "Spexregister", List.of(), List.of("fgv-spexregister"));
        }
    }

    /**
     * A user's access token from {@code fgv-spexipedia}, by password grant.
     */
    protected static String token(final String user) {
        return TOKENS.computeIfAbsent(user, _ -> "Bearer " + password(CLIENT_ID, user).get("access_token"));
    }

    /**
     * A service account's access token, by client credentials, e.g. {@code fgv-spexregister-service}'s, which holds SYNC.
     */
    protected static String serviceToken(final String clientId) {
        return TOKENS.computeIfAbsent("client:" + clientId, _ -> {
            final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

            Map.of("grant_type", "client_credentials", "client_id", clientId, "client_secret", SECRETS.get(clientId)).forEach(form::add);
            return "Bearer " + tokenRequest(form).get("access_token");
        });
    }

    /**
     * A user's access token from a public MCP client, {@code fgv-spexipedia-mcp} or the unbound one, by password grant.
     */
    protected static String mcpToken(final String user, final String clientId) {
        return TOKENS.computeIfAbsent(clientId + ":" + user, _ -> {
            final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

            Map.of("grant_type", "password", "client_id", clientId, "username", user, "password", PASSWORD, "scope", "openid").forEach(form::add);
            return "Bearer " + tokenRequest(form).get("access_token");
        });
    }

    private static Map<String, Object> password(final String clientId, final String user) {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        Map.of("grant_type", "password", "client_id", clientId, "client_secret", SECRETS.get(clientId),
                "username", user, "password", PASSWORD, "scope", "openid").forEach(form::add);
        return tokenRequest(form);
    }

    private static Map<String, Object> tokenRequest(final MultiValueMap<String, String> form) {
        final String response = RestClient.create()
                .post()
                .uri(KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        return JSON.parseMap(response);
    }

    /**
     * Asserts that the response has an error with the given {@code extensions.classification}, e.g. {@code FORBIDDEN}.
     */
    protected static void expectError(final GraphQlTester.Response response, final String code) {
        response.errors().satisfy(errors -> assertThat(errors)
                .as("errors")
                .anySatisfy(error -> assertThat(String.valueOf(error.getErrorType())).isEqualTo(code)));
    }

    /**
     * Spexregister answers with the given JSON for the given query name.
     */
    protected static void spexregisterAnswers(final String query, final String data) {
        WIREMOCK.stubFor(post(urlEqualTo("/spexregister/graphql")).withRequestBody(containing(query))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"data\": " + data + "}")));
    }

    @BeforeEach
    void resetState() {
        jdbcClient.sql("SET FOREIGN_KEY_CHECKS = 0").update();
        for (final String table : TABLES) {
            jdbcClient.sql("TRUNCATE TABLE " + table).update();
        }
        // The default kinds come from Flyway; tests' own kinds go
        jdbcClient.sql("DELETE FROM kind WHERE created_by <> 'system'").update();
        jdbcClient.sql("UPDATE kind SET retired = 0").update();
        jdbcClient.sql("SET FOREIGN_KEY_CHECKS = 1").update();
        jdbcClient.sql("UPDATE shedlock SET lock_until = UTC_TIMESTAMP(3)").update();
        jdbcClient.sql("UPDATE settings SET welcome_sv = NULL, welcome_en = NULL, public_welcome_sv = NULL, public_welcome_en = NULL, contact_email = NULL").update();
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
        WIREMOCK.resetAll();
    }

    /** The spex, revivals, categories and tasks of V1_5__base_data.sql, which the tests' reset removes. */
    protected static final long SEEDED_SPEX = 153;

    /**
     * Inserts the base data of V1_5__base_data.sql again, after the reset emptied the tables.
     */
    protected void seedBaseData() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1_5__base_data.sql")).execute(dataSource);
    }

    protected HttpGraphQlTester graphQl(final @Nullable String user) {
        return graphQlWithToken(user == null ? null : token(user));
    }

    protected HttpGraphQlTester graphQlWithToken(final @Nullable String authorization) {
        return HttpGraphQlTester.create(WebTestClient.bindToServer()
                .baseUrl("http://localhost:%d/api/graphql".formatted(localPort))
                .responseTimeout(Duration.ofSeconds(60))
                .defaultHeaders(headers -> {
                    if (authorization != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                    headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en");
                })
                .build());
    }

    /**
     * A JSON-RPC request to the MCP endpoint, e.g. {@code tools/list}, with a token or none, for its HTTP status and body.
     */
    protected WebTestClient.ResponseSpec mcp(final @Nullable String authorization, final String method, final Map<String, Object> params) {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:%d".formatted(localPort))
                .responseTimeout(Duration.ofSeconds(60))
                .build()
                .post()
                .uri(MCP_PATH)
                .headers(headers -> {
                    if (authorization != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .bodyValue(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params))
                .exchange();
    }

    protected RestClient http() {
        return RestClient.builder().baseUrl("http://localhost:" + localPort).build();
    }

    // Making records -------------------------------------------------------------------------------------------------------

    protected String createCategory(final String name) {
        return graphQl(EDITOR).document("mutation($n: String!) { spexCategoryCreate(input: {name: $n, firstYear: \"1948\"}) { id } }")
                .variable("n", name).execute().path("spexCategoryCreate.id").entity(String.class).get();
    }

    protected String createSpex(final String year, final String title, final @Nullable String categoryId) {
        return graphQl(EDITOR).document("mutation($y: String!, $t: String, $c: ID) { spexCreate(input: {year: $y, title: $t, categoryId: $c}) { id } }")
                .variable("y", year).variable("t", title).variable("c", categoryId)
                .execute().path("spexCreate.id").entity(String.class).get();
    }

    protected void publish(final String spexId) {
        graphQl(EDITOR).document("mutation($id: ID!) { spexPageSetPublished(spexId: $id, published: true) { published } }")
                .variable("id", spexId).execute().path("spexPageSetPublished.published").entity(Boolean.class).isEqualTo(true);
    }

    protected String createCharacter(final String spexId, final String name) {
        return graphQl(EDITOR).document("mutation($s: ID!, $n: String!) { characterCreate(spexId: $s, input: {name: $n}) { id } }")
                .variable("s", spexId).variable("n", name).execute().path("characterCreate.id").entity(String.class).get();
    }
}
