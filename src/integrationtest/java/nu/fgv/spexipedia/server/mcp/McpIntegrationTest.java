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

package nu.fgv.spexipedia.server.mcp;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The MCP endpoint: only {@code fgv-spexipedia-mcp}'s tokens with its address as audience; the base data tools that
 * moved here from Spexregister, the pages, articles and search; and writes only where the GraphQL API allows them.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
class McpIntegrationTest extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private static Map<String, Object> args(final Object... pairs) {
        final Map<String, Object> map = new HashMap<>();

        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private JsonNode rpc(final String user, final String method, final Map<String, Object> params) {
        final byte[] body = mcp(mcpToken(user, MCP_CLIENT_ID), method, params)
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        return JSON.readTree(Objects.requireNonNull(body));
    }

    private JsonNode call(final String user, final String tool, final Map<String, Object> arguments) {
        return rpc(user, "tools/call", Map.of("name", tool, "arguments", arguments));
    }

    private JsonNode ok(final String user, final String tool, final Map<String, Object> arguments) {
        final JsonNode result = call(user, tool, arguments).get("result");

        if (result == null || result.path("isError").asBoolean(false)) {
            throw new AssertionError("Tool error from %s: %s".formatted(tool, result));
        }
        return JSON.readTree(result.get("content").get(0).get("text").asString());
    }

    private static List<String> names(final JsonNode items, final String field) {
        final List<String> names = new ArrayList<>();

        items.forEach(item -> names.add(item.get(field).asString()));
        return names;
    }

    private String createRevival(final String parentId, final String year) {
        return graphQl(EDITOR).document("mutation($p: ID!, $y: String!) { spexRevivalCreate(parentId: $p, year: $y) { id } }")
                .variable("p", parentId).variable("y", year).execute().path("spexRevivalCreate.id").entity(String.class).get();
    }

    private void retire(final String kind, final String id) {
        graphQl(ADMIN).document("mutation($k: BaseDataKind!, $id: ID!) { baseDataRetire(kind: $k, id: $id, retired: true) }")
                .variable("k", kind).variable("id", id).execute().path("baseDataRetire").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    void theToolsReadAndWriteTextsButNeverPublishMergeRetireOrDelete() {
        final List<String> names = new ArrayList<>();
        final List<String> writes = new ArrayList<>();

        for (final JsonNode tool : rpc(MEMBER, "tools/list", Map.of()).path("result").path("tools")) {
            final String name = tool.get("name").asString();

            names.add(name);
            assertThat(tool.path("annotations").path("destructiveHint").asBoolean(true)).as(name).isFalse();
            assertThat(tool.get("description").asString()).doesNotContain("kept in Spexipedia");
            if (!tool.path("annotations").path("readOnlyHint").asBoolean(false)) {
                writes.add(name);
            }
        }

        assertThat(names).contains("list_spex", "get_spex", "list_spex_categories", "get_spex_category", "list_tasks", "get_task",
                        "list_task_categories", "get_task_category", "search", "search_spex", "list_spex_years", "get_character",
                        "list_numbers", "get_recording", "list_articles", "get_article_category", "list_recent_changes")
                .noneMatch(name -> name.contains("publish") || name.contains("merge") || name.contains("retire") || name.contains("trash")
                        || name.contains("delete") || name.contains("redirect") || name.contains("setting") || name.contains("image")
                        || name.contains("hide") || name.contains("consumer") || name.contains("maintenance") || name.contains("restore"));
        assertThat(writes).containsExactlyInAnyOrder("update_spex_page", "create_article", "update_article", "paste_characters",
                "paste_scenes", "paste_numbers", "paste_recording_tracks");
    }

    @Test
    void anEditorPastesAndAnAuthorWritesThePageButAMemberIsRefused() {
        final String spex = createSpex("1948", "Bojan", null);
        final JsonNode refused = call(MEMBER, "paste_characters", args("spexId", spex, "text", "Bojan")).get("result");

        assertThat(refused.path("isError").asBoolean(false)).isTrue();
        assertThat(refused.toString()).contains("FORBIDDEN");
        assertThat(call(MEMBER, "update_spex_page", args("spexId", spex, "synopsis", "x")).get("result").toString()).contains("FORBIDDEN");

        assertThat(names(ok(EDITOR, "paste_characters", args("spexId", spex, "text", "Bojan – en björn\nKungen")).get("items"), "name"))
                .containsExactly("Bojan", "Kungen");
        assertThat(names(ok(EDITOR, "paste_scenes", args("spexId", spex, "text", "Skogen – Hallen\n--\nAkt 2\nSlottet")).get("items"), "act"))
                .containsExactly("1", "2");
        assertThat(names(ok(EDITOR, "paste_numbers", args("spexId", spex, "text", "Björnvisan (Hej tomtegubbar – Trad.)")).get("items"),
                "originalTitle")).containsExactly("Hej tomtegubbar");

        final JsonNode page = ok(AUTHOR, "update_spex_page", args("spexId", spex, "synopsis", "Bojan vaknar.", "premiere", "1948-04-01"));

        assertThat(page.get("synopsis").asString()).isEqualTo("Bojan vaknar.");
        assertThat(page.get("published").asBoolean()).isFalse();
        assertThat(ok(AUTHOR, "update_spex_page", args("spexId", spex, "introduction", "Om en björn.")).get("synopsis").asString())
                .isEqualTo("Bojan vaknar.");
        assertThat(ok(MEMBER, "get_spex", args("id", spex)).get("page").isNull()).isTrue();

        final JsonNode details = ok(AUTHOR, "get_spex", args("id", spex));

        assertThat(details.get("page").get("introduction").asString()).isEqualTo("Om en björn.");
        assertThat(details.get("scenes").size()).isEqualTo(2);
        assertThat(details.get("numbers").get(0).get("title").asString()).isEqualTo("Björnvisan");
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM revision WHERE target = 'SPEX_PAGE'").query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM audit_entry WHERE entity_type IN ('CHARACTER', 'SCENE', 'NUMBER', 'SPEX_PAGE')")
                .query(Integer.class).single()).isGreaterThanOrEqualTo(5);

        final String character = details.get("characters").get(0).get("id").asString();

        assertThat(ok(MEMBER, "get_character", args("id", character)).get("spex").get("id").asString()).isEqualTo(spex);
    }

    @Test
    void membersWriteUnpublishedArticlesAndSeeTheRecentChanges() {
        final JsonNode created = ok(MEMBER, "create_article", args("title", "Spexets historia", "body", "Det började [[Bojan]].",
                "visibility", "PUBLIC", "comment", "Första utkast"));
        final String id = created.get("article").get("id").asString();

        assertThat(created.get("article").get("published").asBoolean()).isFalse();

        final JsonNode updated = ok(MEMBER, "update_article", args("id", id, "body", "Det började 1948.",
                "baseRevisionId", created.get("revisionId").asString()));

        assertThat(updated.get("body").asString()).isEqualTo("Det började 1948.");
        assertThat(updated.get("article").get("title").asString()).isEqualTo("Spexets historia");
        assertThat(call(MEMBER, "update_article", args("id", id, "body", "x", "baseRevisionId", created.get("revisionId").asString()))
                .get("result").toString()).contains("CONFLICT");
        assertThat(names(ok(MEMBER, "list_articles", args()).get("items"), "id")).containsExactly(id);
        assertThat(ok(MEMBER, "list_recent_changes", args()).get("totalCount").asInt()).isEqualTo(2);

        graphQl(AUTHOR).document("mutation($id: ID!) { articleSetPublished(id: $id, published: true) { published } }").variable("id", id)
                .execute().path("articleSetPublished.published").entity(Boolean.class).isEqualTo(true);
        assertThat(call(MEMBER, "update_article", args("id", id, "body", "y")).get("result").toString()).contains("BAD_REQUEST");
    }

    @Test
    void spexCategoriesRevivalsAndCharacters() {
        final String category = createCategory("Chalmersspexet");
        final String original = createSpex("1948", "Bojan", category);
        final String revival = createRevival(original, "1998");
        final String other = createSpex("1950", "Fritiof", null);
        final String character = createCharacter(original, "Bojan");
        final String retiredCharacter = createCharacter(original, "Kungen");

        publish(original);
        retire("CHARACTER", retiredCharacter);
        retire("SPEX", other);

        final JsonNode originals = ok(MEMBER, "list_spex", args()).get("items");

        assertThat(names(originals, "title")).containsExactly("Bojan");
        assertThat(originals.get(0).get("category").get("name").asString()).isEqualTo("Chalmersspexet");
        assertThat(originals.get(0).get("published").asBoolean()).isTrue();
        assertThat(names(ok(MEMBER, "list_spex", args("includeRevivals", true)).get("items"), "year")).containsExactly("1948", "1998");
        assertThat(names(ok(MEMBER, "list_spex", args("includeRetired", true)).get("items"), "title")).containsExactly("Bojan", "Fritiof");
        assertThat(names(ok(MEMBER, "search_spex", args("query", "boj")).get("items"), "id")).containsExactly(original, revival);
        assertThat(ok(MEMBER, "list_spex_years", args()).get("items").get(0).get("year").asString()).isEqualTo("1998");
        await().untilAsserted(() -> assertThat(names(ok(MEMBER, "search", args("query", "bojan")).get("items"), "kind")).contains("SPEX"));
        assertThat(ok(MEMBER, "list_spex", args("year", "1950", "includeRetired", true, "categoryId", category)).get("items").isEmpty()).isTrue();

        final JsonNode spex = ok(MEMBER, "get_spex", args("id", original));

        assertThat(spex.get("page").isObject()).isTrue();
        assertThat(spex.get("people").isNull()).isTrue();
        assertThat(ok(MEMBER, "get_spex", args("id", original, "includePeople", true)).get("people").has("available")).isTrue();

        assertThat(names(spex.get("revivals"), "id")).containsExactly(revival);
        assertThat(names(spex.get("characters"), "id")).containsExactly(character);
        assertThat(names(ok(EDITOR, "get_spex", args("id", original)).get("characters"), "id")).containsExactly(character, retiredCharacter);

        final JsonNode bySlug = ok(MEMBER, "get_spex", args("id", spex.get("slug").asString()));
        final JsonNode revivalDetails = ok(MEMBER, "get_spex", args("id", revival));

        assertThat(bySlug.get("id").asString()).isEqualTo(original);
        assertThat(revivalDetails.get("parent").get("id").asString()).isEqualTo(original);
        assertThat(revivalDetails.get("title").asString()).isEqualTo("Bojan");
        assertThat(names(revivalDetails.get("characters"), "id")).containsExactly(character);

        assertThat(names(ok(MEMBER, "list_spex_categories", args()).get("items"), "name")).containsExactly("Chalmersspexet");
        assertThat(names(ok(MEMBER, "get_spex_category", args("id", category)).get("spex"), "id")).containsExactly(original);

        final JsonNode missing = call(MEMBER, "get_spex", args("id", "00000000-0000-7000-8000-000000000000")).get("result");

        assertThat(missing.path("isError").asBoolean(false)).isTrue();
        assertThat(missing.toString()).contains("NOT_FOUND");
    }

    @Test
    void tasksAndTaskCategories() {
        final String orchestra = graphQl(EDITOR).document("""
                        mutation { c: taskCategoryCreate(input: {name: "Orkester", actorPresent: false, instruments: true}) { id } }""")
                .execute().path("c.id").entity(String.class).get();
        final String actors = graphQl(EDITOR).document("""
                        mutation { c: taskCategoryCreate(input: {name: "Skådespelare", actorPresent: true}) { id } }""")
                .execute().path("c.id").entity(String.class).get();
        final String violin = createTask("Fiol", orchestra);
        final String flute = createTask("Flöjt", orchestra);

        createTask("Aktör", actors);
        retire("TASK", flute);

        assertThat(names(ok(MEMBER, "list_tasks", args("categoryId", orchestra)).get("items"), "name")).containsExactly("Fiol");
        assertThat(names(ok(MEMBER, "list_tasks", args("categoryId", orchestra, "includeRetired", true)).get("items"), "name"))
                .containsExactly("Fiol", "Flöjt");
        assertThat(ok(MEMBER, "list_tasks", args()).get("items").size()).isEqualTo(2);

        final JsonNode task = ok(MEMBER, "get_task", args("id", violin));

        assertThat(task.get("category").get("name").asString()).isEqualTo("Orkester");

        final JsonNode categories = ok(MEMBER, "list_task_categories", args()).get("items");

        assertThat(names(categories, "name")).containsExactly("Orkester", "Skådespelare");
        assertThat(categories.get(0).get("instruments").asBoolean()).isTrue();
        assertThat(categories.get(1).get("actorPresent").asBoolean()).isTrue();
        assertThat(names(ok(MEMBER, "get_task_category", args("id", orchestra)).get("tasks"), "id")).containsExactly(violin);
        assertThat(call(MEMBER, "get_task_category", args("id", "nonsense")).get("result").toString()).contains("NOT_FOUND");
    }

    @Test
    void tokensFromOtherClientsAreRefused() {
        for (final String token : List.of(token(MEMBER), mcpToken(MEMBER, UNBOUND_MCP_CLIENT_ID))) {
            mcp(token, "tools/list", Map.of()).expectStatus().isUnauthorized();
        }
    }

    @Test
    void noTokenIsRefusedWithResourceMetadata() {
        mcp(null, "tools/list", Map.of())
                .expectStatus().isUnauthorized()
                .expectHeader().value(HttpHeaders.WWW_AUTHENTICATE, value -> assertThat(value).contains("resource_metadata"));
    }

    @Test
    void mcpTokensAreRefusedByTheRestOfTheApi() {
        WebTestClient.bindToServer().baseUrl("http://localhost:%d".formatted(localPort)).build()
                .post()
                .uri("/api/graphql")
                .header(HttpHeaders.AUTHORIZATION, mcpToken(MEMBER, MCP_CLIENT_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", "{ me { username } }"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String createTask(final String name, final String categoryId) {
        return graphQl(EDITOR).document("mutation($n: String!, $c: ID!) { taskCreate(input: {name: $n, categoryId: $c}) { id } }")
                .variable("n", name).variable("c", categoryId).execute().path("taskCreate.id").entity(String.class).get();
    }
}
