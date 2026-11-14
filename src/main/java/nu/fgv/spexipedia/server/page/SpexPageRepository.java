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

import nu.fgv.spexipedia.server.basedata.Spex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SpexPage}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public interface SpexPageRepository extends JpaRepository<SpexPage, Long> {

    Optional<SpexPage> findBySpex(Spex spex);

    @Query("SELECT p.spex.id FROM SpexPage p WHERE p.published = true")
    List<Long> findPublishedSpexKeys();

    @Query("SELECT p.spex.uuid FROM SpexPage p WHERE p.published = true")
    List<UUID> findPublishedSpexIds();

    @Query("SELECT p FROM SpexPage p JOIN FETCH p.spex WHERE p.spex.id IN :spexKeys")
    List<SpexPage> findBySpexKeys(Collection<Long> spexKeys);

    @Query("SELECT p FROM SpexPage p JOIN FETCH p.spex WHERE p.published = true AND p.premiere IS NOT NULL")
    List<SpexPage> findPublishedWithPremiere();
}
