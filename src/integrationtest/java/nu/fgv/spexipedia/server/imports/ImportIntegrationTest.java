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

package nu.fgv.spexipedia.server.imports;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The import API (spec 5.11): only IMPORT reaches it, source keys make imports repeatable, editors' changes are kept,
 * kinds are matched by name and alias, credits by name, and dry runs keep nothing.
 */
class ImportIntegrationTest extends AbstractIntegrationTest {

    private static final String MIGRATION = "fgv-content-migration";
    private static final String BATCH = """
            mutation($input: ImportBatchInput!, $dryRun: Boolean!) { importBatch(input: $input, dryRun: $dryRun) {
                created updated unchanged kept failed items { kind sourceKey id outcome message warnings }
            } }""";
    private static final String NUMBERS = """
            query($id: ID!) { spex(id: $id) { numbers { title kind { nameSv } firstLine fromWork
                credits { kind { nameSv } position credit { name unresolved person { id } } } } } }""";

    private Map<String, Object> number(final String spexId, final String key, final String title, final String kind) {
        return Map.of("sourceKey", key, "spexId", spexId, "position", 1, "title", title, "kind", kind,
                "firstLine", "Hör hur det låter", "fromWork", "Mamma Mia!",
                "credits", List.of(Map.of("kind", "kompositör", "name", "Benny Andersson", "outsideFgv", true),
                        Map.of("kind", "Textförfattare", "name", "Anna Spexare")));
    }

    @Test
    void onlyImportReachesTheImportApi() {
        expectError(graphQl(ADMIN).document(BATCH).variable("input", Map.of()).variable("dryRun", false).execute(), "FORBIDDEN");
        expectError(graphQl(null).document(BATCH).variable("input", Map.of()).variable("dryRun", false).execute(), "UNAUTHORIZED");
        // The migration's account is no editor outside an import
        expectError(graphQlWithToken(serviceToken(MIGRATION)).document("mutation { figureCreate(input: {name: \"Bellman\"}) { id } }").execute(), "FORBIDDEN");
    }

    @Test
    void numbersAreImportedWithKindsByAliasAndCreditsByName() {
        final String spex = createSpex("1984", "Lasse-Maja", null);

        spexregisterAnswers("SpexipediaSpexareSearch", "{\"spexipediaSpexareSearch\": []}");
        graphQlWithToken(serviceToken(MIGRATION)).document(BATCH)
                .variable("input", Map.of("numbers", List.of(number(spex, "test:1984/1", "Haralds entré", "Sång, trio med komp"))))
                .variable("dryRun", false).execute()
                .path("importBatch.created").entity(Integer.class).isEqualTo(1);

        // Editors see the unresolved lyricist; visitors see only the composer outside FGV (spec 5.3)
        graphQl(EDITOR).document(NUMBERS).variable("id", spex).execute()
                .path("spex.numbers[0].kind.nameSv").entity(String.class).isEqualTo("Sång")
                .path("spex.numbers[0].firstLine").entity(String.class).isEqualTo("Hör hur det låter")
                .path("spex.numbers[0].credits[*].credit.unresolved").entityList(Boolean.class).containsExactly(true, false);
        graphQl(null).document(NUMBERS).variable("id", spex).execute()
                .path("spex.numbers[0].credits[*].credit.name").entityList(String.class).containsExactly("Benny Andersson");
        graphQl(EDITOR).document("{ unresolvedCredits { total credits { name kind { nameSv } } } }").execute()
                .path("unresolvedCredits.total").entity(Integer.class).isEqualTo(1)
                .path("unresolvedCredits.credits[0].name").entity(String.class).isEqualTo("Anna Spexare");
    }

    @Test
    void theSameSourceKeyUpdatesAndEditorsChangesAreKept() {
        final String spex = createSpex("1984", "Lasse-Maja", null);
        final String token = serviceToken(MIGRATION);
        final Map<String, Object> first = Map.of("scenes", List.of(Map.of("sourceKey", "test:scene/1", "spexId", spex, "title", "Scen 1", "place", "Torget")));

        graphQlWithToken(token).document(BATCH).variable("input", first).variable("dryRun", false).execute()
                .path("importBatch.created").entity(Integer.class).isEqualTo(1);
        graphQlWithToken(token).document(BATCH).variable("input", first).variable("dryRun", false).execute()
                .path("importBatch.unchanged").entity(Integer.class).isEqualTo(1);

        final String id = graphQlWithToken(token).document(BATCH)
                .variable("input", Map.of("scenes", List.of(Map.of("sourceKey", "test:scene/1", "spexId", spex, "title", "Scen 1", "place", "Krogen"))))
                .variable("dryRun", false).execute()
                .path("importBatch.updated").entity(Integer.class).isEqualTo(1)
                .path("importBatch.items[0].id").entity(String.class).get();

        graphQl(EDITOR).document("mutation($id: ID!) { sceneUpdate(id: $id, input: {act: 2, title: \"Scen 1\", place: \"Slottet\", intervalAfter: false}) { id } }")
                .variable("id", id).execute().path("sceneUpdate.id").hasValue();
        graphQlWithToken(token).document(BATCH)
                .variable("input", Map.of("scenes", List.of(Map.of("sourceKey", "test:scene/1", "spexId", spex, "title", "Scen 1", "place", "Stallet"))))
                .variable("dryRun", false).execute()
                .path("importBatch.kept").entity(Integer.class).isEqualTo(1);
        graphQl(EDITOR).document("query($id: ID!) { spex(id: $id) { scenes { place act } } }").variable("id", spex).execute()
                .path("spex.scenes[0].place").entity(String.class).isEqualTo("Slottet");
    }

    @Test
    void aDryRunKeepsNothingAndUnknownKindsFailWithTheKnownOnes() {
        final String spex = createSpex("1984", "Lasse-Maja", null);
        final String token = serviceToken(MIGRATION);

        spexregisterAnswers("SpexipediaSpexareSearch", "{\"spexipediaSpexareSearch\": []}");
        graphQlWithToken(token).document(BATCH)
                .variable("input", Map.of("numbers", List.of(number(spex, "test:1984/1", "Haralds entré", "sång"),
                        number(spex, "test:1984/2", "Polkan", "Polka"))))
                .variable("dryRun", true).execute()
                .path("importBatch.created").entity(Integer.class).isEqualTo(1)
                .path("importBatch.failed").entity(Integer.class).isEqualTo(1)
                .path("importBatch.items[1].message").entity(String.class).satisfies(message -> assertThat(message).contains("Polka").contains("Sång"));
        graphQl(EDITOR).document("query($id: ID!) { spex(id: $id) { numbers { title } } }").variable("id", spex).execute()
                .path("spex.numbers").entityList(Object.class).hasSize(0);

        // With createMissingKinds, the unknown kind becomes a new one
        graphQlWithToken(token).document(BATCH)
                .variable("input", Map.of("createMissingKinds", true, "numbers", List.of(number(spex, "test:1984/2", "Polkan", "Polka"))))
                .variable("dryRun", false).execute()
                .path("importBatch.created").entity(Integer.class).isEqualTo(1);
        graphQl(null).document("{ kinds(type: NUMBER) { nameSv } }").execute()
                .path("kinds[*].nameSv").entityList(String.class).contains("Polka");
    }

    @Test
    void filesAreUploadedByTheirHashAndBecomeImages() throws Exception {
        final String spex = createSpex("1984", "Lasse-Maja", null);
        final String token = serviceToken(MIGRATION);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        ImageIO.write(new BufferedImage(600, 840, BufferedImage.TYPE_INT_RGB), "png", out);

        final byte[] png = out.toByteArray();
        final String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));

        assertThat(status(http().head().uri("/api/import/files/" + sha).header("Authorization", token))).isEqualTo(404);
        // Content that does not match its hash is refused
        assertThat(status(http().put().uri("/api/import/files/" + "0".repeat(64)).header("Authorization", token).body(png))).isEqualTo(400);
        assertThat(status(http().put().uri("/api/import/files/" + sha).header("Authorization", token).body(png))).isEqualTo(201);
        assertThat(status(http().put().uri("/api/import/files/" + sha).header("Authorization", getEditorToken()).body(png))).isEqualTo(403);

        graphQlWithToken(token).document(BATCH)
                .variable("input", Map.of("pages", List.of(Map.of("sourceKey", "test:page/1984", "spexId", spex,
                        "introduction", "Ett spex om en tjuv.", "poster", Map.of("sha256", sha, "name", "affisch.png"),
                        "facts", List.of(Map.of("kind", "lokal", "value", "RunAn")),
                        "images", List.of(Map.of("section", "SCENOGRAPHY", "kind", "fonder", "file", Map.of("sha256", sha), "caption", "Fonden"))))))
                .variable("dryRun", false).execute()
                .path("importBatch.created").entity(Integer.class).isEqualTo(1);
        graphQl(EDITOR).document("""
                        query($id: ID!) { spex(id: $id) { poster { width } page { introduction facts { kind { nameSv } value }
                            images(section: SCENOGRAPHY) { kind { nameSv } caption } } } }""")
                .variable("id", spex).execute()
                .path("spex.poster.width").entity(Integer.class).isEqualTo(600)
                .path("spex.page.facts[0].kind.nameSv").entity(String.class).isEqualTo("Spelplats")
                .path("spex.page.images[0].kind.nameSv").entity(String.class).isEqualTo("Fond");
    }

    @Test
    void forgetRemovesWhatIsStillUnchanged() {
        final String spex = createSpex("1984", "Lasse-Maja", null);
        final String token = serviceToken(MIGRATION);

        graphQlWithToken(token).document(BATCH)
                .variable("input", Map.of("scenes", List.of(Map.of("sourceKey", "test:forget/1", "spexId", spex, "title", "Scen 1"),
                        Map.of("sourceKey", "test:forget/2", "spexId", spex, "title", "Scen 2"))))
                .variable("dryRun", false).execute()
                .path("importBatch.created").entity(Integer.class).isEqualTo(2);
        graphQlWithToken(token).document("mutation { importForget(sourcePrefix: \"test:forget/\") }").execute()
                .path("importForget").entity(Integer.class).isEqualTo(2);
        graphQl(EDITOR).document("query($id: ID!) { spex(id: $id) { scenes { title } } }").variable("id", spex).execute()
                .path("spex.scenes").entityList(Object.class).hasSize(0);
    }

    private static int status(final org.springframework.web.client.RestClient.RequestHeadersSpec<?> request) {
        final Integer status = request.exchange((_, response) -> response.getStatusCode().value());

        return status == null ? 0 : status;
    }

    private static String getEditorToken() {
        return token(EDITOR);
    }
}
