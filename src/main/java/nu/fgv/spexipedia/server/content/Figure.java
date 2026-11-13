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
 * A historical or invented figure that appears as a character in several spex, e.g. Bellman.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "figure")
@Getter
@Setter
public class Figure extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private @Nullable String description;
}
