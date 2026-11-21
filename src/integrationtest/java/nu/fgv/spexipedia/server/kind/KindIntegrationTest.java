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

package nu.fgv.spexipedia.server.kind;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.List;

/**
 * Kinds are data that admins keep (spec 4.6).
 */
class KindIntegrationTest extends AbstractIntegrationTest {

    private static final String SAVE = "mutation($type: KindType, $n: String!) { kindSave(input: {type: $type, nameSv: $n, nameEn: $n, aliases: [\"polka\"]}) { id nameSv position } }";

    @Test
    void anyoneReadsKindsAndOnlyAdminsChangeThem() {
        graphQl(null).document("{ kinds(type: NUMBER) { nameSv } }").execute()
                .path("kinds[0].nameSv").entity(String.class).isEqualTo("Sång");
        graphQl(null).document("{ kinds(type: IMAGE, use: SCENOGRAPHY) { nameSv } }").execute()
                .path("kinds[*].nameSv").entityList(String.class).containsExactly("Fond", "Scen", "Rekvisita");
        expectError(graphQl(EDITOR).document(SAVE).variable("type", "NUMBER").variable("n", "Polka").execute(), "FORBIDDEN");
        graphQl(ADMIN).document(SAVE).variable("type", "NUMBER").variable("n", "Polka").execute()
                .path("kindSave.nameSv").entity(String.class).isEqualTo("Polka");
    }

    @Test
    void kindsAreNamedInTheRequestsLanguage() {
        final String query = "{ kinds(type: IMAGE, use: SCENOGRAPHY) { name } }";

        graphQl(null).mutate().header(HttpHeaders.ACCEPT_LANGUAGE, "sv").build().document(query).execute()
                .path("kinds[*].name").entityList(String.class).containsExactly("Fond", "Scen", "Rekvisita");
        graphQl(null).document(query).execute()
                .path("kinds[*].name").entityList(String.class).containsExactly("Backdrop", "Stage", "Props");
    }

    @Test
    void aMergedKindMovesItsRecordsAndBecomesAnAlias() {
        final String spex = createSpex("1984", "Lasse-Maja", null);
        final String polka = graphQl(ADMIN).document(SAVE).variable("type", "NUMBER").variable("n", "Polka").execute()
                .path("kindSave.id").entity(String.class).get();
        final String dance = graphQl(ADMIN).document(SAVE).variable("type", "NUMBER").variable("n", "Dans").execute()
                .path("kindSave.id").entity(String.class).get();

        graphQl(EDITOR).document("mutation($s: ID!, $k: ID) { numberCreate(spexId: $s, input: {act: 1, title: \"Polkan\", kindId: $k}) { id } }")
                .variable("s", spex).variable("k", polka).execute().path("numberCreate.id").hasValue();
        graphQl(ADMIN).document("mutation($id: ID!, $into: ID!) { kindMerge(id: $id, intoId: $into) { aliases } }")
                .variable("id", polka).variable("into", dance).execute()
                .path("kindMerge.aliases").entityList(String.class).contains("Polka");
        graphQl(null).document("query($id: ID!) { spex(id: $id) { numbers { kind { nameSv } } } }").variable("id", spex).execute()
                .path("spex.numbers[0].kind.nameSv").entity(String.class).isEqualTo("Dans");
    }

    @Test
    void theLastKindInUseCannotBeRetired() {
        final List<String> media = graphQl(null).document("{ kinds(type: FACT) { id } }").execute()
                .path("kinds[*].id").entityList(String.class).get();

        for (int i = 0; i < media.size() - 1; i++) {
            graphQl(ADMIN).document("mutation($id: ID!) { kindRetire(id: $id, retired: true) { retired } }")
                    .variable("id", media.get(i)).execute().path("kindRetire.retired").entity(Boolean.class).isEqualTo(true);
        }
        expectError(graphQl(ADMIN).document("mutation($id: ID!) { kindRetire(id: $id, retired: true) { retired } }")
                .variable("id", media.getLast()).execute(), "BAD_REQUEST");
    }
}
