package nu.fgv.spexipedia.server.image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Image}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public interface ImageRepository extends JpaRepository<Image, Long> {

    Optional<Image> findByUuid(UUID uuid);

    List<Image> findByUuidIn(Collection<UUID> uuids);

    List<Image> findByTrashedAtIsNotNullOrderByTrashedAtDesc();

    List<Image> findByVariantWidthsAndTrashedAtIsNull(String variantWidths);

    List<Image> findByTrashedAtBefore(Instant before);
}
