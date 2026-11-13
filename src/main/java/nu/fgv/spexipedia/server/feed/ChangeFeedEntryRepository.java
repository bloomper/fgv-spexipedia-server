package nu.fgv.spexipedia.server.feed;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Repository for {@link ChangeFeedEntry}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public interface ChangeFeedEntryRepository extends JpaRepository<ChangeFeedEntry, Long> {

    List<ChangeFeedEntry> findBySequenceGreaterThanOrderBySequenceAsc(long after, Pageable pageable);

    @Query("SELECT COALESCE(MAX(e.sequence), 0) FROM ChangeFeedEntry e")
    long latestSequence();

    long countBySequenceGreaterThan(long after);
}
