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

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The files of spex packages (spec 5.14): the template, the spreadsheet, and the zip in parts so a large upload can be
 * resumed: {@code HEAD} answers {@code Upload-Offset}, how much has arrived; {@code PATCH} with that
 * {@code Upload-Offset} appends the next part. EDITOR; the service checks.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@RestController
@RequestMapping("/api/packages")
public class PackageController {

    static final String OFFSET = "Upload-Offset";
    static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PackageService service;

    public PackageController(final PackageService service) {
        this.service = service;
    }

    @GetMapping("/template.xlsx")
    public ResponseEntity<byte[]> template() throws IOException {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("Importmall.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(service.template());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> create(@RequestPart("spreadsheet") final MultipartFile spreadsheet,
                                                      @RequestParam(required = false) final @Nullable String zipFileName,
                                                      @RequestParam(required = false) final @Nullable Long zipSize) throws IOException {
        try (InputStream in = spreadsheet.getInputStream()) {
            final SpexPackage pkg = service.create(Objects.requireNonNullElse(spreadsheet.getOriginalFilename(), "package.xlsx"), in, zipFileName, zipSize);

            return ResponseEntity.status(201).body(Map.of("id", pkg.getUuid().toString()));
        }
    }

    @RequestMapping(path = "/{id}/zip", method = RequestMethod.HEAD)
    public ResponseEntity<Void> zipOffset(@PathVariable final UUID id) throws IOException {
        return ResponseEntity.ok().header(OFFSET, String.valueOf(service.zipReceived(id))).build();
    }

    @RequestMapping(path = "/{id}/zip", method = RequestMethod.PATCH)
    public ResponseEntity<Void> zipPart(@PathVariable final UUID id, @RequestHeader(OFFSET) final long offset,
                                        final HttpServletRequest request) throws IOException {
        return ResponseEntity.noContent().header(OFFSET, String.valueOf(service.appendZip(id, offset, request.getInputStream()))).build();
    }
}
