package nu.fgv.spexipedia.server.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import nu.fgv.spexipedia.server.basedata.Spex;
import nu.fgv.spexipedia.server.image.Image;
import nu.fgv.spexipedia.server.util.AbstractAuditable;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A role in a spex, e.g. Abraham Lincoln. Belongs to an original spex, or to a revival that added it. Base data:\n * Spexregister's actors point at characters.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "spex_character")
@Getter
@Setter
public class SpexCharacter extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spex_id", nullable = false)
    private Spex spex;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "figure_id")
    private @Nullable Figure figure;

    @Column(name = "description")
    private @Nullable String description;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "retired", nullable = false)
    private boolean retired;
}
