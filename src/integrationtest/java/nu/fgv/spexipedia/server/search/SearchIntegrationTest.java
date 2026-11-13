package nu.fgv.spexipedia.server.search;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

/**
 * Search (5.1 of the spec) finds what the viewer may see, and never people.
 */
class SearchIntegrationTest extends AbstractIntegrationTest {

    @Test
    void visitorsFindPublishedPagesOnly() {
        final String published = createSpex("1984", "Lasse-Maja", null);

        createSpex("1986", "Victoria", null);
        publish(published);

        await().untilAsserted(() -> graphQl(null).document("{ search(q: \"lasse\") { kind title path } }").execute()
                .path("search[0].title").entity(String.class).isEqualTo("Lasse-Maja (1984)")
                .path("search[0].path").entity(String.class).isEqualTo("/spex/1984-lasse-maja"));
        graphQl(null).document("{ search(q: \"victoria\") { title } }").execute().path("search").entityList(Object.class).hasSize(0);
        graphQl(AUTHOR).document("{ search(q: \"victoria\") { title } }").execute().path("search[*].title")
                .entityList(String.class).containsExactly("Victoria (1986)");
    }
}
