package nu.fgv.spexipedia.server.feed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nu.fgv.spexipedia.server.basedata.BaseDataKind;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * One change to base data, a character or a number, with the record as it was right after the change (payload, JSON).
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "change_feed_entry")
@Getter
@Setter
public class ChangeFeedEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence")
    private Long sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private BaseDataKind kind;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private ChangeType changeType;

    @Column(name = "merged_into_id")
    private @Nullable UUID mergedIntoId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "at", nullable = false)
    private Instant at;
}
