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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nu.fgv.spexipedia.server.util.AbstractAuditable;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * An uploaded image. Its original and variants are files in {@code images/<uuid>/}, which the media server serves to
 * everyone; a trashed image's folder is moved to {@code trash/}, which it doesn't serve.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "image")
@Getter
@Setter
public class Image extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private ImageKind kind;

    @Column(name = "caption")
    private @Nullable String caption;

    @Column(name = "alt")
    private @Nullable String alt;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "sha256", nullable = false)
    private String sha256;

    @Column(name = "variant_widths", nullable = false)
    private String variantWidths = "";

    @Column(name = "trashed_at")
    private @Nullable Instant trashedAt;

    public boolean hasVariants() {
        return !variantWidths.isEmpty();
    }
}
