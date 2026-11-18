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

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Spex}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public interface SpexRepository extends JpaRepository<Spex, Long> {

    Optional<Spex> findByUuid(UUID uuid);

    Optional<Spex> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Spex> findByUuidIn(Collection<UUID> uuids);

    List<Spex> findByParentOrderByYearAsc(Spex parent);

    List<Spex> findByCategory(SpexCategory category);

    @Query("SELECT s FROM Spex s LEFT JOIN FETCH s.parent p LEFT JOIN FETCH s.category LEFT JOIN FETCH p.category ORDER BY s.year, s.id")
    List<Spex> findAllWithParents();

    /**
     * Whether the category has an original in the year, not retired, other than the spex with the id (null for a new one).
     */
    @Query("SELECT COUNT(s) > 0 FROM Spex s WHERE s.category = :category AND s.year = :year AND s.parent IS NULL AND s.retired = false AND (:id IS NULL OR s.id <> :id)")
    boolean existsOriginal(SpexCategory category, String year, @Nullable Long id);

    @Query("SELECT s FROM Spex s LEFT JOIN s.parent p WHERE s.category = :category OR p.category = :category ORDER BY s.year, s.id")
    List<Spex> findAllOfCategory(SpexCategory category);
}
