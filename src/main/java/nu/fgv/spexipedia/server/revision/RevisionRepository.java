package nu.fgv.spexipedia.server.revision;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
