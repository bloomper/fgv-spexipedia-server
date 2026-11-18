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

package nu.fgv.spexipedia.server.page;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Spex pages: texts with revisions (4.5), revivals (5.6) and people from Spexregister (5.3).
 */
class PageIntegrationTest extends AbstractIntegrationTest {

    private static final String SPEXARE = "00000000-0000-7000-a000-000000000001";

    @Test
    void authorsWriteTextsAndSavingOverANewerRevisionIsRefused() {
        final String spex = createSpex("1984", "Lasse-Maja", null);
        final String first = graphQl(AUTHOR).document("""
                        mutation($s: ID!) { spexPageUpdate(spexId: $s, input: {introduction: "Första"}) { revisionIds { introduction } } }""")
                .variable("s", spex).execute().path("spexPageUpdate.revisionIds.introduction").entity(String.class).get();

        graphQl(EDITOR).document("""
                        mutation($s: ID!, $b: ID) { spexPageUpdate(spexId: $s, input: {introduction: "Andra", baseRevisionIds: {introduction: $b}}) { introduction } }""")
                .variable("s", spex).variable("b", first).execute().path("spexPageUpdate.introduction").entity(String.class).isEqualTo("Andra");
        expectError(graphQl(AUTHOR).document("""
                        mutation($s: ID!, $b: ID) { spexPageUpdate(spexId: $s, input: {introduction: "Tredje", baseRevisionIds: {introduction: $b}}) { introduction } }""")
                .variable("s", spex).variable("b", first).execute(), "CONFLICT");
        expectError(graphQl(MEMBER).document("mutation($s: ID!) { spexPageUpdate(spexId: $s, input: {introduction: \"x\"}) { introduction } }")
                .variable("s", spex).execute(), "FORBIDDEN");
        graphQl(AUTHOR).document("query($s: ID!) { spex(id: $s) { page { history { body authorName } } } }").variable("s", spex).execute()
                .path("spex.page.history[*].body").entityList(String.class).containsExactly("Andra", "Första")
                .path("spex.page.history[0].authorName").entity(String.class).isEqualTo("Erik Test");
    }

    @Test
    void aRevivalShowsItsParentsPageWithItsChanges() {
        final String spex = createSpex("1984", "Lasse-Maja", null);
        final String cut = createCharacter(spex, "Narren");

        createCharacter(spex, "Lasse-Maja");
        graphQl(AUTHOR).document("mutation($s: ID!) { spexPageUpdate(spexId: $s, input: {introduction: \"Om Lasse-Maja\"}) { introduction } }")
                .variable("s", spex).execute().path("spexPageUpdate.introduction").hasValue();

        final String revival = graphQl(EDITOR).document("mutation($p: ID!) { spexRevivalCreate(parentId: $p, year: \"2013\") { id } }")
                .variable("p", spex).execute().path("spexRevivalCreate.id").entity(String.class).get();

        createCharacter(revival, "Turisten");
        graphQl(EDITOR).document("mutation($r: ID!, $c: ID!) { revivalSetChanges(revivalId: $r, kind: CHARACTER, changes: [{recordId: $c, cut: true}]) { id } }")
                .variable("r", revival).variable("c", cut).execute().path("revivalSetChanges.id").hasValue();
        publish(revival);

        graphQl(null).document("query($r: ID!) { spex(id: $r) { title page { introduction } characters { name } } }").variable("r", revival).execute()
                .path("spex.title").entity(String.class).isEqualTo("Lasse-Maja")
                .path("spex.page.introduction").entity(String.class).isEqualTo("Om Lasse-Maja")
                .path("spex.characters[*].name").entityList(String.class).containsExactly("Lasse-Maja", "Turisten");
    }

    @Test
    void authorsSeeHowMuchOfAPageIsFilledInAndARevivalIsMeasuredByItsOwn() {
        final String spex = createSpex("1984", "Lasse-Maja", null);

        createCharacter(spex, "Narren");
        graphQl(AUTHOR).document("mutation($s: ID!) { spexPageUpdate(spexId: $s, input: {introduction: \"Om Lasse-Maja\", synopsis: \"  \"}) { introduction } }")
                .variable("s", spex).execute().path("spexPageUpdate.introduction").hasValue();

        final String revival = graphQl(EDITOR).document("mutation($p: ID!) { spexRevivalCreate(parentId: $p, year: \"2013\") { id } }")
                .variable("p", spex).execute().path("spexRevivalCreate.id").entity(String.class).get();
        final String query = "query($s: ID!) { spex(id: $s) { completeness { share sections { section share items { item filled } } } } }";

        graphQl(AUTHOR).document(query).variable("s", spex).execute()
                .path("spex.completeness.sections[*].section").entityList(String.class)
                .containsExactly("BASE", "TEXTS", "FACTS", "MEDIA", "CHARACTERS", "SCENES", "NUMBERS")
                .path("spex.completeness.sections[1].share").entity(Double.class).isEqualTo(1.0 / 7)
                .path("spex.completeness.sections[4].items[*].filled").entityList(Double.class).containsExactly(1.0, 0.0, 0.0);
        // The revival's page shows the original's introduction, but it has nothing of its own
        graphQl(AUTHOR).document(query).variable("s", revival).execute()
                .path("spex.completeness.sections[*].section").entityList(String.class).containsExactly("BASE", "TEXTS", "FACTS", "MEDIA")
                .path("spex.completeness.share").entity(Double.class).isEqualTo(0.0);
        graphQl(MEMBER).document(query).variable("s", spex).execute().path("spex.completeness").valueIsNull();
    }

    @Test
    void visitorsSeeNicknamesAndSpexregisterIsNotAskedForTheirNames() {
        final String spex = createSpex("1984", "Lasse-Maja", null);

        publish(spex);
        graphQl(AUTHOR).document("mutation($s: ID!, $p: ID) { spexPageUpdate(spexId: $s, input: {vestWornBy: {spexareId: $p}}) { premiere } }")
                .variable("s", spex).variable("p", SPEXARE).execute().path("spexPageUpdate").hasValue();
        WIREMOCK.stubFor(post(urlEqualTo("/spexregister/graphql")).withRequestBody(matchingJsonPath("$.variables.names", equalTo("false")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"data": {"spexipediaSpexare": [{"id": "%s", "nickname": "Basen", "name": null}]}}""".formatted(SPEXARE))));
        WIREMOCK.stubFor(post(urlEqualTo("/spexregister/graphql")).withRequestBody(matchingJsonPath("$.variables.names", equalTo("true")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"data": {"spexipediaSpexare": [{"id": "%s", "nickname": "Basen", "name": "Bertil Basson"}]}}""".formatted(SPEXARE))));

        graphQl(null).document("query($s: ID!) { spex(id: $s) { page { vestWornBy { person { id nickname name url } } } } }").variable("s", spex).execute()
                .path("spex.page.vestWornBy.person.nickname").entity(String.class).isEqualTo("Basen")
                .path("spex.page.vestWornBy.person.name").valueIsNull()
                .path("spex.page.vestWornBy.person.id").valueIsNull();
        WIREMOCK.verify(0, postRequestedFor(urlEqualTo("/spexregister/graphql")).withRequestBody(matchingJsonPath("$.variables.names", equalTo("true"))));

        graphQl(MEMBER).document("query($s: ID!) { spex(id: $s) { page { vestWornBy { person { name url } } } } }").variable("s", spex).execute()
                .path("spex.page.vestWornBy.person.name").entity(String.class).isEqualTo("Bertil Basson")
                .path("spex.page.vestWornBy.person.url").entity(String.class).satisfies(url -> org.assertj.core.api.Assertions.assertThat(url).endsWith("/spexare/" + SPEXARE));
    }

    @Test
    void anUnavailableSpexregisterLeavesTheRestOfThePage() {
        final String spex = createSpex("1984", "Lasse-Maja", null);

        publish(spex);
        WIREMOCK.stubFor(post(urlEqualTo("/spexregister/graphql")).willReturn(aResponse().withStatus(503)));

        graphQl(null).document("query($s: ID!) { spex(id: $s) { title people { available board { task { name } } } } }").variable("s", spex).execute()
                .path("spex.title").entity(String.class).isEqualTo("Lasse-Maja")
                .path("spex.people.available").entity(Boolean.class).isEqualTo(false);
    }
}
