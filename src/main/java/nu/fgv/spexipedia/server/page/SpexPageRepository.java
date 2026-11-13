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
