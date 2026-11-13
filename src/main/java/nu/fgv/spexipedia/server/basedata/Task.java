package nu.fgv.spexipedia.server.basedata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nu.fgv.spexipedia.server.util.AbstractAuditable;

import java.util.UUID;

/**
 * A job in a spex, e.g. Regissör or Fondmålare.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "task")
@Getter
@Setter
public class Task extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private TaskCategory category;

    @Column(name = "retired", nullable = false)
    private boolean retired;
}
