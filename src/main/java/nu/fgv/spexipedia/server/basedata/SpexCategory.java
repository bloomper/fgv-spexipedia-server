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
import nu.fgv.spexipedia.server.image.Image;
import nu.fgv.spexipedia.server.util.AbstractAuditable;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The group that puts on spex, e.g. Chalmersspexet.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "spex_category")
@Getter
@Setter
public class SpexCategory extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "first_year", nullable = false)
    private String firstYear;

    @Column(name = "description")
    private @Nullable String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_id")
    private @Nullable Image logo;

    @Column(name = "retired", nullable = false)
    private boolean retired;
}
