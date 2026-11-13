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
 * A song of a spex, in the order of the show. Base data: Spexarkivet's arrangements, Spexify's tracks and\n * Spexflix's chapters refer to numbers.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "spex_number")
@Getter
@Setter
public class SpexNumber extends AbstractAuditable {

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

    @Column(name = "original_title")
    private @Nullable String originalTitle;

    @Column(name = "original_artist")
    private @Nullable String originalArtist;

    @Column(name = "retired", nullable = false)
    private boolean retired;
}
