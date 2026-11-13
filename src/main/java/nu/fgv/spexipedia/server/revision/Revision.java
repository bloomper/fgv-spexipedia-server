package nu.fgv.spexipedia.server.revision;

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
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * One save of an article, or of one of a spex page's texts. A hidden revision is kept, but its text is shown only to
 * authors.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "revision")
@Getter
@Setter
public class Revision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false)
    private RevisionTarget target;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    /**
     * For a spex page: INTRODUCTION, DESCRIPTION or SYNOPSIS.
     */
    @Column(name = "field")
    private @Nullable String field;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "previous_id")
    private @Nullable Long previousId;

    @Column(name = "comment")
    private @Nullable String comment;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "hidden_by")
    private @Nullable String hiddenBy;

    @Column(name = "author", nullable = false)
    private String author;

    @Column(name = "author_name")
    private @Nullable String authorName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
