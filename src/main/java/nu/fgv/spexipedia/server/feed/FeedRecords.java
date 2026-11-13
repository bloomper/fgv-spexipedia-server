package nu.fgv.spexipedia.server.feed;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The records in the change feed, as they were right after a change. They are what other applications copy, and
 * nothing more.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class FeedRecords {

    private FeedRecords() {
    }

    public record FeedSpexCategory(UUID id, String name, String firstYear, @Nullable String logoUrl, boolean retired) {
    }

    public record FeedSpex(UUID id, String year, String title, @Nullable UUID categoryId, @Nullable UUID parentId,
                           @Nullable String posterUrl, boolean retired) {
    }

    public record FeedTaskCategory(UUID id, String name, boolean actorPresent, boolean board, boolean retired) {
    }

    public record FeedTask(UUID id, String name, @Nullable UUID categoryId, boolean retired) {
    }

    public record FeedCharacter(UUID id, UUID spexId, String name, int position, boolean retired) {
    }

    public record FeedNumber(UUID id, UUID spexId, int act, int position, String title, @Nullable String originalTitle,
                             @Nullable String originalArtist, boolean retired) {
    }
}
