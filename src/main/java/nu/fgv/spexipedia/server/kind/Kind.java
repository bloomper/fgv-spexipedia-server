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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A kind kept by admins: a number kind, a credit kind, an image kind or a fact kind (spec 4.6).
 * The import recognises it by its names and aliases.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "kind")
@Getter
@Setter
public class Kind extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private KindType type;

    /**
     * The use of an image kind as its name; empty for the other types.
     */
    @Column(name = "use_for", nullable = false, updatable = false)
    private String useFor = "";

    /**
     * For an image kind of use SECTION: the key of the image section it belongs to.
     */
    @Column(name = "section_id", updatable = false)
    private @Nullable Long sectionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type")
    private @Nullable FactValueType valueType;

    /**
     * For a section kind: what the section shows.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "content", updatable = false)
    private @Nullable SectionContent content;

    @Column(name = "name_sv", nullable = false)
    private String nameSv;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    /**
     * For a section kind: the line under its heading.
     */
    @Column(name = "subtitle_sv")
    private @Nullable String subtitleSv;

    @Column(name = "subtitle_en")
    private @Nullable String subtitleEn;

    /**
     * One per line.
     */
    @Column(name = "aliases", nullable = false)
    private String aliases = "";

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "retired", nullable = false)
    private boolean retired;

    public @Nullable KindUse getUse() {
        return useFor.isEmpty() ? null : KindUse.valueOf(useFor);
    }

    public void setUse(final @Nullable KindUse use) {
        this.useFor = use == null ? "" : use.name();
    }

    public List<String> aliasList() {
        return Arrays.stream(aliases.split("\\R")).map(String::strip).filter(alias -> !alias.isEmpty()).toList();
    }

    public String name(final Locale locale) {
        return Locale.ENGLISH.getLanguage().equals(locale.getLanguage()) ? nameEn : nameSv;
    }

    public @Nullable String subtitle(final Locale locale) {
        return Locale.ENGLISH.getLanguage().equals(locale.getLanguage()) ? subtitleEn : subtitleSv;
    }
}
