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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Merging two records that are the same thing: everything that referred to the merged one refers to the other, and
 * the merged one's id goes on leading there, for the applications that still hold it.
 */
class MergeIntegrationTest extends AbstractIntegrationTest {

    private static final String MERGE = "mutation($kind: BaseDataKind!, $id: ID!, $into: ID!) { baseDataMerge(kind: $kind, id: $id, intoId: $into) }";

    private void merge(final String kind, final String id, final String into) {
        graphQl(ADMIN).document(MERGE).variable("kind", kind).variable("id", id).variable("into", into).execute()
                .path("baseDataMerge").entity(Boolean.class).isEqualTo(true);
    }

    private String createNumber(final String spexId, final String title, final List<String> characterIds) {
        return graphQl(EDITOR).document("mutation($s: ID!, $t: String!, $c: [ID!]) { numberCreate(spexId: $s, input: {act: 1, title: $t, characterIds: $c}) { id } }")
                .variable("s", spexId).variable("t", title).variable("c", characterIds)
                .execute().path("numberCreate.id").entity(String.class).get();
    }

    @Test
    void aMergedCharacterGivesItsNumbersToTheOtherAndItsIdLeadsThere() {
        final String spex = createSpex("1984", "Lasse-Maja", createCategory("Chalmersspexet"));
        final String lasse = createCharacter(spex, "Lasse-Maja");
        final String duplicate = createCharacter(spex, "Lasse Maja");
        // One number is sung by both, one only by the duplicate
        final String both = createNumber(spex, "Ouvertyr", List.of(lasse, duplicate));
        final String onlyDuplicate = createNumber(spex, "Finalen", List.of(duplicate));

        merge("CHARACTER", duplicate, lasse);

        graphQl(null).document("query($id: ID!) { spex(id: $id) { characters { id } } }").variable("id", spex).execute()
                .path("spex.characters[*].id").entityList(String.class).containsExactly(lasse);
        graphQl(null).document("query($id: ID!) { number(id: $id) { characters { id } } }").variable("id", both).execute()
                .path("number.characters[*].id").entityList(String.class).containsExactly(lasse);
        graphQl(null).document("query($id: ID!) { number(id: $id) { characters { id } } }").variable("id", onlyDuplicate).execute()
                .path("number.characters[*].id").entityList(String.class).containsExactly(lasse);
        graphQl(null).document("query($id: ID!) { character(id: $id) { id name } }").variable("id", duplicate).execute()
                .path("character.id").entity(String.class).isEqualTo(lasse)
                .path("character.name").entity(String.class).isEqualTo("Lasse-Maja");
    }

    @Test
    void aMergedNumberIsGoneFromItsSpexAndItsIdLeadsToTheOther() {
        final String spex = createSpex("1984", "Lasse-Maja", createCategory("Chalmersspexet"));
        final String overture = createNumber(spex, "Ouvertyr", List.of());
        final String duplicate = createNumber(spex, "Uvertyr", List.of());

        merge("NUMBER", duplicate, overture);

        graphQl(null).document("query($id: ID!) { spex(id: $id) { numbers { title } } }").variable("id", spex).execute()
                .path("spex.numbers[*].title").entityList(String.class).containsExactly("Ouvertyr");
        graphQl(null).document("query($id: ID!) { number(id: $id) { id title } }").variable("id", duplicate).execute()
                .path("number.id").entity(String.class).isEqualTo(overture);
        graphQl(null).document("query($ids: [ID!]!) { numbersByIds(ids: $ids) { id } }").variable("ids", List.of(duplicate)).execute()
                .path("numbersByIds[*].id").entityList(String.class).containsExactly(overture);
        // The followers of the feed are told where the number went
        graphQlWithToken(serviceToken("fgv-spexify-service")).document("{ baseDataChanges(first: 50) { changes { kind change id mergedIntoId } } }").execute()
                .path("baseDataChanges.changes[-1].change").entity(String.class).isEqualTo("MERGE")
                .path("baseDataChanges.changes[-1].id").entity(String.class).isEqualTo(duplicate)
                .path("baseDataChanges.changes[-1].mergedIntoId").entity(String.class).isEqualTo(overture);
    }

    @Test
    void aMergedSpexGivesItsCharactersScenesNumbersAndRevivalsToTheOther() {
        final String category = createCategory("Chalmersspexet");
        final String original = createSpex("1984", "Lasse-Maja", category);
        final String duplicate = createSpex("1985", "Lasse Maja", category);
        final String revival = graphQl(EDITOR).document("mutation($p: ID!) { spexRevivalCreate(parentId: $p, year: \"1994\") { id } }")
                .variable("p", duplicate).execute().path("spexRevivalCreate.id").entity(String.class).get();

        createCharacter(original, "Lasse-Maja");
        createCharacter(duplicate, "Länsman");
        createNumber(duplicate, "Finalen", List.of());
        graphQl(EDITOR).document("mutation($s: ID!) { sceneCreate(spexId: $s, input: {act: 1, title: \"Torget\", intervalAfter: false}) { id } }")
                .variable("s", duplicate).execute().path("sceneCreate.id").hasValue();

        merge("SPEX", duplicate, original);

        graphQl(null).document("query($id: ID!) { spex(id: $id) { characters { name } numbers { title } scenes { title } revivals { id } } }")
                .variable("id", original).execute()
                .path("spex.characters[*].name").entityList(String.class).contains("Lasse-Maja", "Länsman").hasSize(2)
                .path("spex.numbers[*].title").entityList(String.class).containsExactly("Finalen")
                .path("spex.scenes[*].title").entityList(String.class).containsExactly("Torget")
                .path("spex.revivals[*].id").entityList(String.class).containsExactly(revival);
        graphQl(null).document("{ spexAll { title } }").execute()
                .path("spexAll[*].title").entityList(String.class).doesNotContain("Lasse Maja");
    }

    @Test
    void anIdMergedTwiceLeadsToTheLastRecord() {
        final String spex = createSpex("1984", "Lasse-Maja", createCategory("Chalmersspexet"));
        final String first = createCharacter(spex, "Lasse Maja");
        final String second = createCharacter(spex, "Lasse-Maja!");
        final String kept = createCharacter(spex, "Lasse-Maja");

        merge("CHARACTER", first, second);
        merge("CHARACTER", second, kept);

        graphQl(null).document("query($ids: [ID!]!) { charactersByIds(ids: $ids) { id } }").variable("ids", List.of(first, second)).execute()
                .path("charactersByIds[*].id").entityList(String.class).satisfies(ids -> assertThat(ids).isNotEmpty().containsOnly(kept));
    }

    @Test
    void aRecordIsNotMergedWithItselfAnotherKindOrItsOwnRevival() {
        final String category = createCategory("Chalmersspexet");
        final String original = createSpex("1984", "Lasse-Maja", category);
        final String revival = graphQl(EDITOR).document("mutation($p: ID!) { spexRevivalCreate(parentId: $p, year: \"1994\") { id } }")
                .variable("p", original).execute().path("spexRevivalCreate.id").entity(String.class).get();
        final String character = createCharacter(original, "Lasse-Maja");

        expectError(graphQl(ADMIN).document(MERGE).variable("kind", "SPEX").variable("id", original).variable("into", original).execute(), "BAD_REQUEST");
        // The revival would lose its original
        expectError(graphQl(ADMIN).document(MERGE).variable("kind", "SPEX").variable("id", original).variable("into", revival).execute(), "BAD_REQUEST");
        // A character's id is no number's
        expectError(graphQl(ADMIN).document(MERGE).variable("kind", "NUMBER").variable("id", character).variable("into", character).execute(), "NOT_FOUND");
        expectError(graphQl(EDITOR).document(MERGE).variable("kind", "CHARACTER").variable("id", character).variable("into", character).execute(), "FORBIDDEN");
    }
}
