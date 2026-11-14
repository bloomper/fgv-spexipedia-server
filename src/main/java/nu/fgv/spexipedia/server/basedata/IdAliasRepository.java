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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link IdAlias}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public interface IdAliasRepository extends JpaRepository<IdAlias, UUID> {

    Optional<IdAlias> findByAliasAndKind(UUID alias, BaseDataKind kind);

    /**
     * When a record is merged on, the aliases that led to it lead to its new target.
     */
    @Modifying
    @Query("UPDATE IdAlias a SET a.target = :to WHERE a.target = :from")
    int retarget(UUID from, UUID to);
}
