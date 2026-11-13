package nu.fgv.spexipedia.server.basedata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SpexCategory}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public interface SpexCategoryRepository extends JpaRepository<SpexCategory, Long> {

    Optional<SpexCategory> findByUuid(UUID uuid);

    List<SpexCategory> findAllByOrderByFirstYearAscNameAsc();
}
