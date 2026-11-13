package nu.fgv.spexipedia.server.revision;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A revision as the viewer may see it: the text of a hidden one and the author only for AUTHOR.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public record RevisionDto(UUID id, RevisionTarget target, UUID targetId, String targetTitle, @Nullable String field,
                          @Nullable String body, @Nullable UUID previousId, @Nullable String comment, boolean hidden,
                          @Nullable String authorName, Instant createdAt) {
}
