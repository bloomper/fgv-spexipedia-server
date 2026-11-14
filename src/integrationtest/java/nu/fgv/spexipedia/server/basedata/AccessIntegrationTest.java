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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who may read and change base data (section 3 of the spec).
 */
class AccessIntegrationTest extends AbstractIntegrationTest {

    @Test
    void baseDataIsPublicButAnUnpublishedPageIsNot() {
        final String category = createCategory("Chalmersspexet");
        final String spex = createSpex("1984", "Lasse-Maja", category);

        graphQl(null).document("query($id: ID!) { spex(id: $id) { title slug category { name } page { introduction } published } }")
                .variable("id", spex).execute()
                .path("spex.title").entity(String.class).isEqualTo("Lasse-Maja")
                .path("spex.slug").entity(String.class).isEqualTo("1984-lasse-maja")
                .path("spex.category.name").entity(String.class).isEqualTo("Chalmersspexet")
                .path("spex.page").valueIsNull()
                .path("spex.published").entity(Boolean.class).isEqualTo(false);
        graphQl(AUTHOR).document("{ spex(id: \"1984-lasse-maja\") { page { published } } }").execute()
                .path("spex.page.published").entity(Boolean.class).isEqualTo(false);

        publish(spex);

        graphQl(null).document("{ spex(id: \"1984-lasse-maja\") { page { published } } }").execute()
                .path("spex.page.published").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    void onlyEditorsChangeBaseData() {
        expectError(graphQl(null).document("mutation { spexCreate(input: {year: \"1990\", title: \"X\"}) { id } }").execute(), "UNAUTHORIZED");
        expectError(graphQl(MEMBER).document("mutation { spexCreate(input: {year: \"1990\", title: \"X\"}) { id } }").execute(), "FORBIDDEN");
        expectError(graphQl(AUTHOR).document("mutation { spexCreate(input: {year: \"1990\", title: \"X\"}) { id } }").execute(), "FORBIDDEN");
        graphQl(EDITOR).document("mutation { spexCreate(input: {year: \"1990\", title: \"X\"}) { year } }").execute()
                .path("spexCreate.year").entity(String.class).isEqualTo("1990");
    }

    @Test
    void nyuppsattningCreatesRevivalsAndNothingElse() {
        final String spex = createSpex("1984", "Lasse-Maja", null);
        final String revival = graphQlWithToken(serviceToken("fgv-revival-service"))
                .document("mutation($p: ID!) { spexRevivalCreate(parentId: $p, year: \"2013\") { id title revival parent { year } } }")
                .variable("p", spex).execute()
                .path("spexRevivalCreate.title").entity(String.class).isEqualTo("Lasse-Maja")
                .path("spexRevivalCreate.revival").entity(Boolean.class).isEqualTo(true)
                .path("spexRevivalCreate.parent.year").entity(String.class).isEqualTo("1984")
                .path("spexRevivalCreate.id").entity(String.class).get();

        assertThat(revival).isNotBlank();
        expectError(graphQlWithToken(serviceToken("fgv-revival-service"))
                .document("mutation { spexCreate(input: {year: \"1990\", title: \"X\"}) { id } }").execute(), "FORBIDDEN");
    }

    @Test
    void aRetiredRecordIsReadableButNotListedAndAMergedIdStillResolves() {
        final String category = createCategory("Chalmersspexet");
        final String original = createSpex("1984", "Lasse-Maja", category);
        final String duplicate = createSpex("1985", "Lasse Maja", category);

        graphQl(ADMIN).document("mutation($id: ID!) { baseDataRetire(kind: SPEX, id: $id, retired: true) }").variable("id", duplicate).execute()
                .path("baseDataRetire").entity(Boolean.class).isEqualTo(true);
        graphQl(null).document("{ spexAll { title } }").execute().path("spexAll[*].title").entityList(String.class).containsExactly("Lasse-Maja");
        graphQl(null).document("query($id: ID!) { spex(id: $id) { retired } }").variable("id", duplicate).execute()
                .path("spex.retired").entity(Boolean.class).isEqualTo(true);

        expectError(graphQl(EDITOR).document("mutation($id: ID!, $into: ID!) { baseDataMerge(kind: SPEX, id: $id, intoId: $into) }")
                .variable("id", duplicate).variable("into", original).execute(), "FORBIDDEN");
        graphQl(ADMIN).document("mutation($id: ID!, $into: ID!) { baseDataMerge(kind: SPEX, id: $id, intoId: $into) }")
                .variable("id", duplicate).variable("into", original).execute().path("baseDataMerge").entity(Boolean.class).isEqualTo(true);
        graphQl(null).document("query($id: ID!) { spex(id: $id) { id year } }").variable("id", duplicate).execute()
                .path("spex.id").entity(String.class).isEqualTo(original)
                .path("spex.year").entity(String.class).isEqualTo("1984");
    }
}
