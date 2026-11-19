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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nu.fgv.spexipedia.server.util.AbstractAuditable;

import java.util.UUID;

/**
 * A group of tasks, e.g. Ensemble, Direktion, Orkester.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "task_category")
@Getter
@Setter
public class TaskCategory extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Whether its tasks have a character and a vocal part.
     */
    @Column(name = "actor_present", nullable = false)
    private boolean actorPresent;

    /**
     * Whether its tasks are instruments, which Spexarkivet offers for arrangements' parts.
     */
    @Column(name = "instruments", nullable = false)
    private boolean instruments;

    @Column(name = "retired", nullable = false)
    private boolean retired;
}
