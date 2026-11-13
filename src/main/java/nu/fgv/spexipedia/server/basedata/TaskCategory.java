package nu.fgv.spexipedia.server.basedata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nu.fgv.spexipedia.server.util.AbstractAuditable;

import java.util.UUID;

/**
 * A group of tasks, e.g. Ensemble, Direktion, Orkester.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "task_category")
@Getter
@Setter
public class TaskCategory extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Whether its tasks have a character and a vocal part.
     */
    @Column(name = "actor_present", nullable = false)
    private boolean actorPresent;

    /**
     * Whether its tasks are shown as the board on spex pages.
     */
    @Column(name = "board", nullable = false)
    private boolean board;

    /**
     * Whether its tasks are instruments, which Spexarkivet offers for arrangements' parts.
     */
    @Column(name = "instruments", nullable = false)
    private boolean instruments;

    @Column(name = "retired", nullable = false)
    private boolean retired;
}
