package nu.fgv.spexipedia.server.image;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * An image as the API shows it, with its public addresses.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public record ImageDto(UUID id, ImageKind kind, @Nullable String caption, String alt, int width, int height,
                       String thumbnailUrl, String pageUrl, String fullUrl, String pageJpegUrl,
                       @Nullable String originalFileName, @Nullable Instant trashedAt) {

    /**
     * The same image, without what only authors see.
     */
    public ImageDto forPublic() {
        return new ImageDto(id, kind, caption, alt, width, height, thumbnailUrl, pageUrl, fullUrl, pageJpegUrl, null, trashedAt);
    }
}
