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
import nu.fgv.spexipedia.server.util.Uuids;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A spex package (spec 5.14): a spreadsheet and optionally a zip, uploaded by an editor, previewed as dry runs in every
 * application and then applied. Its files are in {@code imports/packages/<uuid>/}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "spex_package")
@Getter
@Setter
public class SpexPackage extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false, length = 36)
    private UUID uuid = Uuids.v7();

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private PackageState state = PackageState.UPLOADED;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "zip_file_name")
    private @Nullable String zipFileName;

    /** The zip's size, as announced before its parts arrive; null for a package without a zip. */
    @Column(name = "zip_size")
    private @Nullable Long zipSize;

    @Column(name = "zip_received", nullable = false)
    private long zipReceived;

    /** The spex the package created or added to, once known. */
    @Column(name = "spex_id", length = 36)
    private @Nullable UUID spexId;

    /** The preview, as JSON ({@link PackageReport}). */
    @Column(name = "preview", columnDefinition = "MEDIUMTEXT")
    private @Nullable String preview;

    /** What applying it gave, as JSON ({@link PackageReport}). */
    @Column(name = "results", columnDefinition = "MEDIUMTEXT")
    private @Nullable String results;

    /** 0–100 while a preview or an apply runs. */
    @Column(name = "progress", nullable = false)
    private int progress;

    /** What runs now, or why it failed. */
    @Column(name = "message", length = 1000)
    private @Nullable String message;

    @Column(name = "uploaded_by", nullable = false, length = 64)
    private String uploadedBy;

    @Column(name = "uploaded_by_name")
    private @Nullable String uploadedByName;

    @Column(name = "applied_at")
    private @Nullable Instant appliedAt;

    /** Whether the whole zip has arrived (or there is none). */
    public boolean zipComplete() {
        return zipSize == null || zipReceived >= zipSize;
    }
}
