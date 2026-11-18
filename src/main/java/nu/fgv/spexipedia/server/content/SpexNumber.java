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

package nu.fgv.spexipedia.server.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nu.fgv.spexipedia.server.basedata.Spex;
import nu.fgv.spexipedia.server.kind.Kind;
import nu.fgv.spexipedia.server.image.Image;
import nu.fgv.spexipedia.server.util.AbstractAuditable;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A song of a spex, in the order of the show. Base data: Spexarkivet's arrangements, Spexify's tracks and\n * Spexflix's chapters refer to numbers.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "spex_number")
@Getter
@Setter
public class SpexNumber extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spex_id", nullable = false)
    private Spex spex;

    @Column(name = "act", nullable = false)
    private int act;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "title", nullable = false)
    private String title;

    /** A number kind kept by admins (spec 4.6). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kind_id")
    private @Nullable Kind kind;

    @Column(name = "original_title")
    private @Nullable String originalTitle;

    @Column(name = "original_artist")
    private @Nullable String originalArtist;

    /** Where the original song is from, e.g. a musical or a film. */
    @Column(name = "from_work")
    private @Nullable String fromWork;

    @Column(name = "first_line")
    private @Nullable String firstLine;

    @Column(name = "retired", nullable = false)
    private boolean retired;
}
