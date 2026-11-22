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

package nu.fgv.spexipedia.server.spexpackage;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spex packages (spec 5.14): the template, an upload in parts, the preview and the apply, with Spexify answering
 * through WireMock and Spexarkivet not configured.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
class PackageIntegrationTest extends AbstractIntegrationTest {

    private static final String PACKAGE = """
            query($id: ID!) { spexPackage(id: $id) {
                state progress message zipReceived spex { id title year }
                preview { unmatched warnings apps { app status records files created failed message items { kind outcome message } } }
                results { apps { app status created failed message items { kind message warnings } } }
            } }""";

    @Test
    void theTemplateHasAColumnPerCreditKindAndARowPerFactKind() throws Exception {
        final byte[] template = http().get().uri("/api/packages/template.xlsx").header(HttpHeaders.AUTHORIZATION, token(EDITOR))
                .retrieve().body(byte[].class);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(template))) {
            final Sheet numbers = workbook.getSheet("Nummer");

            assertThat(cells(numbers, 0)).contains("Titel", "Typ", "Musik", "Text", "Karaktärer");
            assertThat(column(workbook.getSheet("Spex"), 0)).contains("Titel", "År", "Spelplats");
        }
        assertThatThrownBy(() -> http().get().uri("/api/packages/template.xlsx").header(HttpHeaders.AUTHORIZATION, token(MEMBER))
                .retrieve().body(byte[].class)).isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void aPackageIsUploadedInPartsPreviewedAndApplied() throws Exception {
        WIREMOCK.stubFor(head(urlMatching("/spexify/import/files/.*")).willReturn(aResponse().withStatus(200)));
        WIREMOCK.stubFor(post(urlEqualTo("/spexify/graphql")).withRequestBody(containing("importBatch")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("""
                        {"data": {"importBatch": {"created": 2, "updated": 0, "unchanged": 0, "kept": 0, "failed": 0, "items": [
                          {"kind": "ALBUM", "sourceKey": "a", "id": "019a0000-0000-7000-8000-000000000001", "outcome": "CREATED",
                           "messageSv": null, "messageEn": null, "warningsSv": ["Ny albumsort: Live"], "warningsEn": ["New album kind: Live"]},
                          {"kind": "TRACK", "sourceKey": "t", "id": "019a0000-0000-7000-8000-000000000002", "outcome": "CREATED",
                           "messageSv": null, "messageEn": null, "warningsSv": [], "warningsEn": []}
                        ]}}}""")));

        final byte[] zip = zip(Map.of(
                "Affisch/affisch.png", png(),
                "Inspelningar/1 Ouvertyr.mp3", new byte[]{1, 2, 3},
                "Manus/Manus.pdf", "%PDF-1.4".getBytes(),
                "Lös fil.txt", "x".getBytes()));
        final String id = upload(spreadsheet(), zip);

        // The zip in two parts; a part at the wrong offset is refused with what has arrived
        final int half = zip.length / 2;

        assertThat(offset(id)).isZero();
        part(id, 0, Arrays.copyOfRange(zip, 0, half));
        assertThatThrownBy(() -> part(id, 0, Arrays.copyOfRange(zip, half, zip.length))).isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThat(offset(id)).isEqualTo(half);
        part(id, half, Arrays.copyOfRange(zip, half, zip.length));

        graphQl(EDITOR).document("mutation($id: ID!) { spexPackagePreview(id: $id) { state } }").variable("id", id).execute()
                .path("spexPackagePreview.state").entity(String.class).isEqualTo("PREVIEWING");
        await(id, "PREVIEWED");

        graphQl(EDITOR).mutate().header(HttpHeaders.ACCEPT_LANGUAGE, "sv").build().document(PACKAGE).variable("id", id).execute()
                .path("spexPackage.preview.apps[0].app").entity(String.class).isEqualTo("SPEXIPEDIA")
                .path("spexPackage.preview.apps[0].status").entity(String.class).isEqualTo("CHECKED")
                .path("spexPackage.preview.apps[0].failed").entity(Integer.class).isEqualTo(0)
                .path("spexPackage.preview.apps[?(@.app == 'ARCHIVE')].status").entityList(String.class).containsExactly("UNAVAILABLE")
                .path("spexPackage.preview.apps[?(@.app == 'SPEXIFY')].status").entityList(String.class).containsExactly("DEFERRED")
                .path("spexPackage.preview.unmatched").entityList(String.class)
                .contains("Lös fil.txt: en fil överst i zip-filen hamnar ingenstans. Lägg den i en mapp.")
                .path("spexPackage.preview.apps[?(@.app == 'SPEXIFY')].message").entityList(String.class)
                .containsExactly("Kontrolleras när importen tillämpas: den behöver spexet och numren som Spexipedia skapar.")
                .path("spexPackage.spex").valueIsNull();
        // The report is in the reader's language
        graphQl(EDITOR).mutate().header(HttpHeaders.ACCEPT_LANGUAGE, "en").build().document(PACKAGE).variable("id", id).execute()
                .path("spexPackage.preview.unmatched").entityList(String.class)
                .contains("Lös fil.txt: a file at the top of the zip goes nowhere. Put it in a folder.")
                .path("spexPackage.preview.apps[?(@.app == 'ARCHIVE')].message").entityList(String.class).containsExactly("Not in this environment.");
        // A dry run made nothing
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM spex WHERE title = 'Paketspexet'").query(Integer.class).single()).isZero();

        graphQl(EDITOR).document("mutation($id: ID!) { spexPackageApply(id: $id) { state } }").variable("id", id).execute()
                .path("spexPackageApply.state").entity(String.class).isEqualTo("APPLYING");
        // Spexarkivet is not deployed here: its part is left out, the rest applied
        await(id, "APPLIED");
        graphQl(EDITOR).document(PACKAGE).variable("id", id).execute()
                .path("spexPackage.spex.title").entity(String.class).isEqualTo("Paketspexet")
                .path("spexPackage.results.apps[0].status").entity(String.class).isEqualTo("APPLIED")
                .path("spexPackage.results.apps[?(@.app == 'ARCHIVE')].status").entityList(String.class).containsExactly("UNAVAILABLE")
                .path("spexPackage.results.apps[?(@.app == 'SPEXIFY')].status").entityList(String.class).containsExactly("APPLIED");
        // Another application's warnings were asked for in both languages; the report shows the reader's
        for (final String[] language : new String[][]{{"sv", "Ny albumsort: Live"}, {"en", "New album kind: Live"}}) {
            graphQl(EDITOR).mutate().header(HttpHeaders.ACCEPT_LANGUAGE, language[0]).build().document(PACKAGE).variable("id", id).execute()
                    .path("spexPackage.results.apps[?(@.app == 'SPEXIFY')].items[0].warnings[0]").entityList(String.class)
                    .containsExactly(language[1]);
        }
        WIREMOCK.verify(postRequestedFor(urlEqualTo("/spexify/graphql")).withRequestBody(containing("message(locale: \\\"en\\\")")));
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM spex_number n JOIN spex s ON s.id = n.spex_id WHERE s.title = 'Paketspexet'")
                .query(Integer.class).single()).isEqualTo(1);
        // Spexify got the spex' and the number's ids
        WIREMOCK.verify(postRequestedFor(urlEqualTo("/spexify/graphql")).withRequestBody(containing("\"numberId\"")));

        // Applied again, nothing new in Spexipedia
        graphQl(EDITOR).document("mutation($id: ID!) { spexPackageApply(id: $id) { state } }").variable("id", id).execute();
        await(id, "APPLIED");
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM spex WHERE title = 'Paketspexet'").query(Integer.class).single()).isEqualTo(1);

        graphQl(EDITOR).document("mutation($id: ID!) { spexPackageDelete(id: $id) }").variable("id", id).execute();
        graphQl(EDITOR).document(PACKAGE).variable("id", id).execute().path("spexPackage").valueIsNull();
    }

    @Test
    void onlyEditorsHandlePackages() {
        expectError(graphQl(AUTHOR).document("{ spexPackages { id } }").execute(), "FORBIDDEN");
    }

    private String upload(final byte[] spreadsheet, final byte[] zip) {
        final MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();

        form.add("spreadsheet", new ByteArrayResource(spreadsheet) {
            @Override
            public String getFilename() {
                return "Paketspexet.xlsx";
            }
        });
        form.add("zipFileName", "Paketspexet.zip");
        form.add("zipSize", String.valueOf(zip.length));

        final Map<?, ?> created = http().post().uri("/api/packages").header(HttpHeaders.AUTHORIZATION, token(EDITOR))
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(Map.class);

        return String.valueOf(created.get("id"));
    }

    private long offset(final String id) {
        final ResponseEntity<Void> answer = http().head().uri("/api/packages/" + id + "/zip").header(HttpHeaders.AUTHORIZATION, token(EDITOR))
                .retrieve().toBodilessEntity();

        return Long.parseLong(answer.getHeaders().getFirst(PackageController.OFFSET));
    }

    private void part(final String id, final long offset, final byte[] bytes) {
        http().patch().uri("/api/packages/" + id + "/zip").header(HttpHeaders.AUTHORIZATION, token(EDITOR))
                .header(PackageController.OFFSET, String.valueOf(offset)).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes).retrieve().toBodilessEntity();
    }

    private void await(final String id, final String state) throws InterruptedException {
        final Instant until = Instant.now().plus(Duration.ofSeconds(60));
        String now = "";

        while (Instant.now().isBefore(until)) {
            now = graphQl(EDITOR).document(PACKAGE).variable("id", id).execute().path("spexPackage.state").entity(String.class).get();
            if (state.equals(now)) {
                return;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("The package is " + now + ", not " + state + ": "
                + jdbcClient.sql("SELECT CONCAT_WS(' | ', message, results) FROM spex_package WHERE uuid = ?").param(id).query(String.class).single());
    }

    private static byte[] spreadsheet() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            rows(workbook.createSheet("Spex"), List.of("Fält", "Värde"), List.of("Titel", "Paketspexet"), List.of("År", "2031"),
                    List.of("Kort beskrivning", "Ett spex i ett paket"), List.of("Spelplats", "Kårhuset"));
            rows(workbook.createSheet("Karaktärer"), List.of("Namn", "Beskrivning"), List.of("Kungen", "Regerar"));
            rows(workbook.createSheet("Nummer"), List.of("Akt", "Ordning", "Titel", "Typ", "Musik"), List.of("1", "1", "Ouvertyr", "Ouvertyr", ""));
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void rows(final Sheet sheet, final List<?>... rows) {
        for (int r = 0; r < rows.length; r++) {
            final var row = sheet.createRow(r);

            for (int c = 0; c < rows[r].size(); c++) {
                row.createCell(c).setCellValue(String.valueOf(rows[r].get(c)));
            }
        }
    }

    private static List<String> cells(final Sheet sheet, final int row) {
        final var cells = sheet.getRow(row);

        return java.util.stream.IntStream.range(0, cells.getLastCellNum()).mapToObj(c -> cells.getCell(c).getStringCellValue()).toList();
    }

    private static List<String> column(final Sheet sheet, final int column) {
        final List<String> values = new java.util.ArrayList<>();

        sheet.forEach(row -> values.add(row.getCell(column) == null ? "" : row.getCell(column).getStringCellValue()));
        return values;
    }

    private static byte[] zip(final Map<String, byte[]> files) throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (final Map.Entry<String, byte[]> file : new java.util.TreeMap<>(files).entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] png() throws Exception {
        final BufferedImage image = new BufferedImage(40, 60, BufferedImage.TYPE_INT_RGB);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
