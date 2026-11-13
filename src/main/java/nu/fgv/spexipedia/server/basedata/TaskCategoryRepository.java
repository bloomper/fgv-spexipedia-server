package nu.fgv.spexipedia.server.basedata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link TaskCategory}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public interface TaskCategoryRepository extends JpaRepository<TaskCategory, Long> {

    Optional<TaskCategory> findByUuid(UUID uuid);

    List<TaskCategory> findAllByOrderByNameAsc();

    List<TaskCategory> findByBoardTrue();
}
