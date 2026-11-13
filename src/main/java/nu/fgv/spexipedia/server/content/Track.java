package nu.fgv.spexipedia.server.content;

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
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A track of a recording: a number, or a free-text title.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "recording_track")
@Getter
@Setter
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recording_id", nullable = false)
    private Recording recording;

    @Column(name = "side")
    private @Nullable String side;

    @Column(name = "position", nullable = false)
    private int position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "number_id")
    private @Nullable SpexNumber number;

    @Column(name = "title")
    private @Nullable String title;
}
