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
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nu.fgv.spexipedia.server.image.Image;
import nu.fgv.spexipedia.server.kind.Kind;
import nu.fgv.spexipedia.server.util.AbstractAuditable;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A book: something printed, e.g. a book such as Chalmersspex I. Not a recording; its songs, if any, are the spex'
 * numbers.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "book")
@Getter
@Setter
public class Book extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "title", nullable = false)
    private String title;

    /** A book kind kept by admins (spec 4.6). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kind_id", nullable = false)
    private Kind kind;

    /** Its number in the series: 1 for Chalmersspex I. */
    @Column(name = "volume")
    private @Nullable Integer volume;

    /** yyyy, yyyy-mm or yyyy-mm-dd. */
    @Column(name = "published_year")
    private @Nullable String publishedYear;

    @Column(name = "description")
    private @Nullable String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_image_id")
    private @Nullable Image cover;

    @Column(name = "published", nullable = false)
    private boolean published;
}
