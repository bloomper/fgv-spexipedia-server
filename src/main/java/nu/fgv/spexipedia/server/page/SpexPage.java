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

package nu.fgv.spexipedia.server.page;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nu.fgv.spexipedia.server.basedata.Spex;
import nu.fgv.spexipedia.server.image.Image;
import nu.fgv.spexipedia.server.util.AbstractAuditable;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The encyclopedia's page of a spex (section 4.2 of the spec): its texts, rebus and vest. Its images and photos are in
 * lists of their own; its characters, scenes and numbers are records of their own. A spex without a row has an empty,
 * unpublished page.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "spex_page")
@Getter
@Setter
public class SpexPage extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spex_id", nullable = false, updatable = false)
    private Spex spex;

    @Column(name = "generation")
    private @Nullable String generation;

    @Column(name = "premiere")
    private @Nullable LocalDate premiere;

    @Column(name = "introduction")
    private @Nullable String introduction;

    @Column(name = "description")
    private @Nullable String description;

    @Column(name = "synopsis")
    private @Nullable String synopsis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rebus_image_id")
    private @Nullable Image rebus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rebus_solution_id")
    private @Nullable Image rebusSolution;

    @Column(name = "vest_description")
    private @Nullable String vestDescription;

    @Column(name = "vest_worn_by_spexare")
    private @Nullable UUID vestWornBySpexare;

    @Column(name = "vest_worn_by_name")
    private @Nullable String vestWornByName;

    @Column(name = "published", nullable = false)
    private boolean published;
}
