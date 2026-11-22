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

package nu.fgv.spexipedia.server.revision;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository for {@link Revision}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public interface RevisionRepository extends JpaRepository<Revision, Long> {

    Optional<Revision> findByUuid(UUID uuid);

    List<Revision> findByTargetAndTargetIdOrderByIdDesc(RevisionTarget target, UUID targetId);

    Optional<Revision> findFirstByTargetAndTargetIdAndFieldOrderByIdDesc(RevisionTarget target, UUID targetId, String field);

    Optional<Revision> findFirstByTargetAndTargetIdOrderByIdDesc(RevisionTarget target, UUID targetId);

    /**
     * The articles the user has saved a revision of.
     */
    @Query("SELECT DISTINCT r.targetId FROM Revision r WHERE r.target = nu.fgv.spexipedia.server.revision.RevisionTarget.ARTICLE "
            + "AND r.author = :author")
    Set<UUID> findArticlesWrittenBy(String author);

    /**
     * Revisions of the given targets, newest first: the articles and spex pages the viewer may see.
     */
    @Query("SELECT r FROM Revision r WHERE (r.target = nu.fgv.spexipedia.server.revision.RevisionTarget.ARTICLE AND r.targetId IN :articles) "
            + "OR (r.target = nu.fgv.spexipedia.server.revision.RevisionTarget.SPEX_PAGE AND r.targetId IN :pages) ORDER BY r.id DESC")
    Page<Revision> findVisible(Collection<UUID> articles, Collection<UUID> pages, Pageable pageable);

    /**
     * Revisions of the given articles and of every spex page, newest first: for those who see unpublished pages.
     */
    @Query("SELECT r FROM Revision r WHERE (r.target = nu.fgv.spexipedia.server.revision.RevisionTarget.ARTICLE AND r.targetId IN :articles) "
            + "OR r.target = nu.fgv.spexipedia.server.revision.RevisionTarget.SPEX_PAGE ORDER BY r.id DESC")
    Page<Revision> findVisibleAllPages(Collection<UUID> articles, Pageable pageable);
}
