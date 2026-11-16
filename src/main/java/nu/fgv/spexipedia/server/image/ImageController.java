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

package nu.fgv.spexipedia.server.image;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Image uploads, by {@code AUTHOR}: one file per request, as {@code multipart/form-data}. JPEG, PNG, WebP, TIFF and
 * HEIC up to 50 MB. The answer is the image, with its addresses; it is placed on a page with the GraphQL API.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService service;

    public ImageController(final ImageService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ImageDto> upload(@RequestPart("file") final MultipartFile file,
                                           @RequestParam(name = "kind", defaultValue = "OTHER") final ImageKind kind) throws IOException {
        try (InputStream content = file.getInputStream()) {
            return ResponseEntity.ok(service.upload(content, file.getOriginalFilename(), kind));
        }
    }
}
