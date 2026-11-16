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
