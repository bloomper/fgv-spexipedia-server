package nu.fgv.spexipedia.server.feed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link SyncConsumer}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public interface SyncConsumerRepository extends JpaRepository<SyncConsumer, Long> {

    Optional<SyncConsumer> findByUuid(UUID uuid);

    Optional<SyncConsumer> findByClientId(String clientId);

    List<SyncConsumer> findAllByOrderByNameAsc();

    List<SyncConsumer> findByEnabledTrue();
}
