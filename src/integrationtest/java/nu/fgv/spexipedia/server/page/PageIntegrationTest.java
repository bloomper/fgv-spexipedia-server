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
    void visitorsSeeNicknamesAndSpexregisterIsNotAskedForTheirNames() {
        final String spex = createSpex("1984", "Lasse-Maja", null);

        publish(spex);
        graphQl(AUTHOR).document("mutation($s: ID!, $p: ID) { spexPageUpdate(spexId: $s, input: {vestWornBy: {spexareId: $p}}) { generation } }")
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
