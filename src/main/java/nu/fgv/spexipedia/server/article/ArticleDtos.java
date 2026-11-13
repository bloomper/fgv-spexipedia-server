package nu.fgv.spexipedia.server.article;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Articles as the API shows them.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class ArticleDtos {

    private ArticleDtos() {
    }

    public record ArticleDto(long key, UUID id, String slug, String title, @Nullable String body, Visibility visibility,
                             boolean published, List<Long> categoryKeys, @Nullable Instant trashedAt, Instant createdAt,
                             @Nullable Instant lastModifiedAt) {
    }

    public record ArticleCategoryDto(long key, UUID id, String slug, String name, @Nullable String description,
                                     int articleCount) {
    }
}
