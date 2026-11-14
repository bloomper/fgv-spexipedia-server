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
import nu.fgv.spexipedia.server.util.AbstractAuditable;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * A production: an original spex, or a revival of one (a spex with a parent), which takes its title and category from
 * its parent.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "spex")
@Getter
@Setter
public class Spex extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "year", nullable = false)
    private String year;

    /**
     * Null for a revival.
     */
    @Column(name = "title")
    private @Nullable String title;

    /**
     * Null for a revival.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private @Nullable SpexCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private @Nullable Spex parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poster_id")
    private @Nullable Image poster;

    @Column(name = "retired", nullable = false)
    private boolean retired;

    public boolean isRevival() {
        return parent != null;
    }

    /**
     * The original: itself, or its parent for a revival.
     */
    public Spex original() {
        return parent == null ? this : parent;
    }

    public String effectiveTitle() {
        return Objects.requireNonNullElse(original().getTitle(), "");
    }

    public @Nullable SpexCategory effectiveCategory() {
        return original().getCategory();
    }

    /**
     * Its own poster, or its parent's for a revival without one.
     */
    public @Nullable Image effectivePoster() {
        return poster != null ? poster : parent != null ? parent.getPoster() : null;
    }

    /**
     * e.g. "Lasse-Maja (1984)".
     */
    public String label() {
        return "%s (%s)".formatted(effectiveTitle(), year);
    }
}
