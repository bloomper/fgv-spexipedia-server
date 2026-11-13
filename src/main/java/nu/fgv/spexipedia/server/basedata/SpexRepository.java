package nu.fgv.spexipedia.server.basedata;

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

    @Query("SELECT s FROM Spex s LEFT JOIN s.parent p WHERE s.category = :category OR p.category = :category ORDER BY s.year, s.id")
    List<Spex> findAllOfCategory(SpexCategory category);
}
