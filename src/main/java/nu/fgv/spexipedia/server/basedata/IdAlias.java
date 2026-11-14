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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The id of a merged record, which still resolves to the record it was merged into, so that references nobody moved
 * keep working.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "id_alias")
@Getter
@Setter
public class IdAlias {

    @Id
    @Column(name = "alias")
    private UUID alias;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private BaseDataKind kind;

    @Column(name = "target", nullable = false)
    private UUID target;

    @Column(name = "merged_at", nullable = false)
    private Instant mergedAt;
}
