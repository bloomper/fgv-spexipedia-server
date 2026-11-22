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
import java.util.Map;

/**
 * Kinds are data that admins keep (spec 4.6).
 */
class KindIntegrationTest extends AbstractIntegrationTest {

    private static final String SAVE = "mutation($type: KindType, $n: String!) { kindSave(input: {type: $type, nameSv: $n, nameEn: $n, aliases: [\"polka\"]}) { id nameSv position } }";

    @Test
    void anyoneReadsKindsAndOnlyAdminsChangeThem() {
        graphQl(null).document("{ kinds(type: NUMBER) { nameSv } }").execute()
                .path("kinds[0].nameSv").entity(String.class).isEqualTo("Sång");
        graphQl(null).document("{ kinds(type: IMAGE, use: SECTION, sectionId: \"01900000-0000-7000-8000-000000000703\") { nameSv } }").execute()
                .path("kinds[*].nameSv").entityList(String.class).containsExactly("Fond", "Scen", "Rekvisita");
        expectError(graphQl(EDITOR).document(SAVE).variable("type", "NUMBER").variable("n", "Polka").execute(), "FORBIDDEN");
        graphQl(ADMIN).document(SAVE).variable("type", "NUMBER").variable("n", "Polka").execute()
                .path("kindSave.nameSv").entity(String.class).isEqualTo("Polka");
    }

    @Test
    void kindsAreNamedInTheRequestsLanguage() {
        final String query = "{ kinds(type: IMAGE, use: SECTION, sectionId: \"01900000-0000-7000-8000-000000000703\") { name } }";

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
        // The feed's followers copy the number's kind: once when it was made and again with the kind it was merged into
        graphQlWithToken(serviceToken("fgv-spexify-service")).document("{ baseDataChanges(first: 50) { changes { number { kindId } } } }").execute()
                .path("baseDataChanges.changes[*].number.kindId").entityList(String.class).containsExactly(polka, dance);
    }

    @Test
    void aMergedCreditKindPutsItsCreditsAfterTheOthers() {
        final String spex = createSpex("1984", "Lasse-Maja", null);
        final String text = graphQl(ADMIN).document(SAVE).variable("type", "CREDIT").variable("n", "Textförfattare").execute()
                .path("kindSave.id").entity(String.class).get();
        final String lyrics = graphQl(ADMIN).document(SAVE).variable("type", "CREDIT").variable("n", "Sångtext").execute()
                .path("kindSave.id").entity(String.class).get();
        final String number = graphQl(EDITOR).document("""
                        mutation($s: ID!, $text: ID!, $lyrics: ID!) { numberCreate(spexId: $s, input: {act: 1, title: "Finalen", credits: [
                            {kindId: $text, name: "Tage Text"}, {kindId: $lyrics, name: "Lisa Lyrik"}, {kindId: $lyrics, name: "Lars Lyrik"}]}) { id } }""")
                .variable("s", spex).variable("text", text).variable("lyrics", lyrics).execute()
                .path("numberCreate.id").entity(String.class).get();

        graphQl(ADMIN).document("mutation($id: ID!, $into: ID!) { kindMerge(id: $id, intoId: $into) { id } }")
                .variable("id", text).variable("into", lyrics).execute().path("kindMerge.id").entity(String.class).isEqualTo(lyrics);

        // Nobody's credit is lost: the merged kind's come after those the number already had of the other kind
        graphQl(EDITOR).document("query($id: ID!) { number(id: $id) { credits { kind { id } position credit { name } } } }")
                .variable("id", number).execute()
                .path("number.credits[*].credit.name").entityList(String.class).containsExactly("Lisa Lyrik", "Lars Lyrik", "Tage Text")
                .path("number.credits[*].position").entityList(Integer.class).containsExactly(0, 1, 2)
                .path("number.credits[*].kind.id").entityList(String.class).containsExactly(lyrics, lyrics, lyrics);
    }

    @Test
    void aMergedImageKindPutsItsImagesAfterTheOthersOnAPage() throws Exception {
        final String spex = createSpex("1984", "Lasse-Maja", null);
        final String kind = "mutation($n: String!) { kindSave(input: {type: IMAGE, use: SECTION, sectionId: \"01900000-0000-7000-8000-000000000711\", nameSv: $n, nameEn: $n}) { id } }";
        final String sketches = graphQl(ADMIN).document(kind).variable("n", "Skisser").execute().path("kindSave.id").entity(String.class).get();
        final String drawings = graphQl(ADMIN).document(kind).variable("n", "Teckningar").execute().path("kindSave.id").entity(String.class).get();
        final String sketch = upload("skiss.png");
        final String first = upload("teckning-1.png");
        final String second = upload("teckning-2.png");
        final String set = "mutation($s: ID!, $k: ID!, $images: [ImageRefInput!]!) { spexPageSetImages(spexId: $s, sectionId: \"01900000-0000-7000-8000-000000000711\", kindId: $k, images: $images) { published } }";

        graphQl(AUTHOR).document(set).variable("s", spex).variable("k", sketches).variable("images", List.of(Map.of("imageId", sketch))).execute()
                .path("spexPageSetImages.published").hasValue();
        graphQl(AUTHOR).document(set).variable("s", spex).variable("k", drawings)
                .variable("images", List.of(Map.of("imageId", first), Map.of("imageId", second))).execute()
                .path("spexPageSetImages.published").hasValue();

        graphQl(ADMIN).document("mutation($id: ID!, $into: ID!) { kindMerge(id: $id, intoId: $into) { id } }")
                .variable("id", sketches).variable("into", drawings).execute().path("kindMerge.id").entity(String.class).isEqualTo(drawings);

        graphQl(AUTHOR).document("query($id: ID!) { spex(id: $id) { page { images(sectionId: \"01900000-0000-7000-8000-000000000711\") { image { id } kind { id } } } } }")
                .variable("id", spex).execute()
                .path("spex.page.images[*].image.id").entityList(String.class).containsExactly(first, second, sketch)
                .path("spex.page.images[*].kind.id").entityList(String.class).containsExactly(drawings, drawings, drawings);
    }

    private String upload(final String name) throws Exception {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final org.springframework.http.client.MultipartBodyBuilder body = new org.springframework.http.client.MultipartBodyBuilder();

        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(160, 90, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        body.part("file", new org.springframework.core.io.ByteArrayResource(out.toByteArray()) {
            @Override
            public String getFilename() {
                return name;
            }
        });
        return String.valueOf(http().post().uri("/api/images?kind=OTHER")
                .header(HttpHeaders.AUTHORIZATION, token(AUTHOR))
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .body(Map.class).get("id"));
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
