package nu.fgv.spexipedia.server.basedata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link IdAlias}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public interface IdAliasRepository extends JpaRepository<IdAlias, UUID> {

    Optional<IdAlias> findByAliasAndKind(UUID alias, BaseDataKind kind);

    /**
     * When a record is merged on, the aliases that led to it lead to its new target.
     */
    @Modifying
    @Query("UPDATE IdAlias a SET a.target = :to WHERE a.target = :from")
    int retarget(UUID from, UUID to);
}
