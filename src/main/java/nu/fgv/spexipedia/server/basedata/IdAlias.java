package nu.fgv.spexipedia.server.basedata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The id of a merged record, which still resolves to the record it was merged into, so that references nobody moved
 * keep working.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "id_alias")
@Getter
@Setter
public class IdAlias {

    @Id
    @Column(name = "alias")
    private UUID alias;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private BaseDataKind kind;

    @Column(name = "target", nullable = false)
    private UUID target;

    @Column(name = "merged_at", nullable = false)
    private Instant mergedAt;
}
