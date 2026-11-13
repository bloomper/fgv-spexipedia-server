package nu.fgv.spexipedia.server.basedata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Task}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByUuid(UUID uuid);

    List<Task> findByUuidIn(Collection<UUID> uuids);

    List<Task> findByCategoryOrderByNameAsc(TaskCategory category);

    @Query("SELECT t FROM Task t JOIN FETCH t.category c ORDER BY c.name, t.name")
    List<Task> findAllWithCategories();
}
