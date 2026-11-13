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
 * A released record: LP, EP, CD or digital, with a catalogue number such as CEP 019.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "recording")
@Getter
@Setter
public class Recording extends AbstractAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "catalogue_number", nullable = false)
    private String catalogueNumber;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "medium", nullable = false)
    private Medium medium;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spex_id")
    private @Nullable Spex spex;

    @Column(name = "released")
    private @Nullable String released;

    @Column(name = "recorded")
    private @Nullable String recorded;

    @Column(name = "conductor_spexare_id")
    private @Nullable UUID conductorSpexareId;

    @Column(name = "conductor_name")
    private @Nullable String conductorName;

    @Column(name = "description")
    private @Nullable String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "front_image_id")
    private @Nullable Image front;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "back_image_id")
    private @Nullable Image back;

    @Column(name = "spexify_album_id")
    private @Nullable UUID spexifyAlbumId;

    @Column(name = "published", nullable = false)
    private boolean published;
}
