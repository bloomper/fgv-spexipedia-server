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

package nu.fgv.spexipedia.server.basedata;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import nu.fgv.spexipedia.server.feed.SyncNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The change feed for the applications that keep a copy of base data (section 5.8 of the spec).
 */
class FeedIntegrationTest extends AbstractIntegrationTest {

    private static final String CHANGES = """
            query($after: String) { baseDataChanges(after: $after, first: 50) {
                cursor hasMore changes { sequence kind id change mergedIntoId spex { title year parentId categoryId } task { name } taskCategory { instruments } }
            } }""";

    @Autowired
    private SyncNotifier notifier;

    @Test
    void onlyServiceAccountsWithSyncReadTheFeed() {
        createSpex("1984", "Lasse-Maja", null);

        expectError(graphQl(null).document(CHANGES).execute(), "UNAUTHORIZED");
        expectError(graphQl(ADMIN).document(CHANGES).execute(), "FORBIDDEN");
        graphQlWithToken(serviceToken("fgv-spexregister-service")).document(CHANGES).execute()
                .path("baseDataChanges.changes[*].spex.title").entityList(String.class).containsExactly("Lasse-Maja");
    }

    @Test
    void changesComeInOrderAfterTheCursorWithTheRecordAsItWas() {
        final String category = createCategory("Chalmersspexet");
        final String spex = createSpex("1984", "Lasse-Maja", category);
        final String sync = serviceToken("fgv-spexgram-service");
        final String cursor = graphQlWithToken(sync).document(CHANGES).execute()
                .path("baseDataChanges.changes[*].kind").entityList(String.class).containsExactly("SPEX_CATEGORY", "SPEX")
                .path("baseDataChanges.cursor").entity(String.class).get();

        graphQl(EDITOR).document("mutation($id: ID!) { spexUpdate(id: $id, input: {year: \"1984\", title: \"Lasse-Maja!\"}) { id } }")
                .variable("id", spex).execute().path("spexUpdate.id").hasValue();
        graphQlWithToken(sync).document(CHANGES).variable("after", cursor).execute()
                .path("baseDataChanges.changes[*].spex.title").entityList(String.class).containsExactly("Lasse-Maja!")
                .path("baseDataChanges.hasMore").entity(Boolean.class).isEqualTo(false);
    }

    @Test
    void instrumentCategoriesAreMarkedForSpexarkivet() {
        final String orchestra = graphQl(EDITOR).document("""
                        mutation { c: taskCategoryCreate(input: {name: "Orkester", actorPresent: false, instruments: true}) { id instruments } }""")
                .execute().path("c.instruments").entity(Boolean.class).isEqualTo(true)
                .path("c.id").entity(String.class).get();

        graphQlWithToken(serviceToken("fgv-archive-service")).document(CHANGES).execute()
                .path("baseDataChanges.changes[0].id").entity(String.class).isEqualTo(orchestra)
                .path("baseDataChanges.changes[0].taskCategory.instruments").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    void aMergeTellsWhereReferencesGo() {
        final String task = graphQl(EDITOR).document("""
                        mutation { c: taskCategoryCreate(input: {name: "Kommitté", actorPresent: false}) { id } }""")
                .execute().path("c.id").entity(String.class).get();
        final String directeur = graphQl(EDITOR).document("mutation($c: ID!) { taskCreate(input: {name: \"Directeur\", categoryId: $c, board: true}) { id } }")
                .variable("c", task).execute().path("taskCreate.id").entity(String.class).get();
        final String duplicate = graphQl(EDITOR).document("mutation($c: ID!) { taskCreate(input: {name: \"Direktör\", categoryId: $c}) { id } }")
                .variable("c", task).execute().path("taskCreate.id").entity(String.class).get();

        graphQl(ADMIN).document("mutation($id: ID!, $into: ID!) { baseDataMerge(kind: TASK, id: $id, intoId: $into) }")
                .variable("id", duplicate).variable("into", directeur).execute().path("baseDataMerge").entity(Boolean.class).isEqualTo(true);

        final List<String> changes = graphQlWithToken(serviceToken("fgv-spexregister-service")).document(CHANGES).execute()
                .path("baseDataChanges.changes[*].change").entityList(String.class).get();

        assertThat(changes).last().isEqualTo("MERGE");
        graphQlWithToken(serviceToken("fgv-spexregister-service")).document(CHANGES).execute()
                .path("baseDataChanges.changes[3].id").entity(String.class).isEqualTo(duplicate)
                .path("baseDataChanges.changes[3].mergedIntoId").entity(String.class).isEqualTo(directeur);
    }

    @Test
    void consumersAreToldOnlyAtTheApplicationsAddresses() {
        // Notifications carry Spexipedia's service token, so an address outside the applications' APIs is refused
        expectError(graphQl(ADMIN).document("""
                        mutation($url: String) { syncConsumerSave(input: {name: "Någon", clientId: "fgv-spexregister-service", notifyUrl: $url, enabled: true}) { id } }""")
                .variable("url", "https://elsewhere.example.org/notify").execute(), "BAD_REQUEST");
        expectError(graphQl(ADMIN).document("""
                        mutation($url: String) { syncConsumerSave(input: {name: "Någon", clientId: "fgv-spexregister-service", notifyUrl: $url, enabled: true}) { id } }""")
                .variable("url", WIREMOCK.baseUrl().replace("://", "://user:pass@") + "/notify").execute(), "BAD_REQUEST");
    }

    @Test
    void consumersAreToldAndTheirReadingIsRemembered() {
        WIREMOCK.stubFor(post(urlEqualTo("/notify")).willReturn(aResponse().withStatus(204)));
        graphQl(ADMIN).document("""
                        mutation($url: String) { syncConsumerSave(input: {name: "Spexregister", clientId: "fgv-spexregister-service", notifyUrl: $url, enabled: true}) { id } }""")
                .variable("url", WIREMOCK.baseUrl() + "/notify").execute().path("syncConsumerSave.id").hasValue();
        createSpex("1984", "Lasse-Maja", null);
        notifier.notifyConsumers();

        await().untilAsserted(() -> WIREMOCK.verify(postRequestedFor(urlEqualTo("/notify")).withHeader("Authorization", matching("Bearer .+"))));

        final String cursor = graphQlWithToken(serviceToken("fgv-spexregister-service")).document(CHANGES).execute()
                .path("baseDataChanges.cursor").entity(String.class).get();

        graphQlWithToken(serviceToken("fgv-spexregister-service")).document(CHANGES).variable("after", cursor).execute()
                .path("baseDataChanges.changes").entityList(Object.class).hasSize(0);
        graphQl(ADMIN).document("{ syncConsumers { name acknowledged lag lastError } }").execute()
                .path("syncConsumers[0].acknowledged").entity(String.class).isEqualTo(cursor)
                .path("syncConsumers[0].lag").entity(Integer.class).isEqualTo(0);
    }
}
