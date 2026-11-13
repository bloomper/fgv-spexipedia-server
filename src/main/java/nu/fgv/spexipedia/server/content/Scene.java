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
 * A scene of a spex, in the order of the show.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "scene")
@Getter
@Setter
public class Scene extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spex_id", nullable = false)
    private Spex spex;

    @Column(name = "act", nullable = false)
    private int act;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "place")
    private @Nullable String place;

    @Column(name = "interval_after", nullable = false)
    private boolean intervalAfter;
}
