package nu.fgv.spexipedia.server.links;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Links to the other applications and photos from Spexgram, as the API shows them.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class LinkDtos {

    private LinkDtos() {
    }

    public enum AppLinkKind {
        SPEXREGISTER,
        SPEXIFY_ALBUM,
        SPEXIFY_TRACK,
        SPEXFLIX_VIDEO,
        ARCHIVE_MANUSCRIPT,
        ARCHIVE_ARRANGEMENT,
        SPEXGRAM_ALBUM
    }

    public record AppLinkDto(AppLinkKind kind, String title, String url) {
    }

    public record PhotoDto(UUID itemId, @Nullable String caption, String thumbnailUrl, String previewUrl,
                           @Nullable Integer width, @Nullable Integer height, String url, boolean isPublic) {
        /**
         * GraphQL's {@code public}, a reserved word in Java.
         */
        public boolean getPublic() {
            return isPublic;
        }
    }

    /**
     * @param hiddenCount members' photos the viewer may not see; zero for visitors, who are not told
     * @param itemIds     the chosen ids, for authors; empty for everyone else
     */
    public record PhotoSetDto(List<PhotoDto> photos, int hiddenCount, boolean available, List<UUID> itemIds) {
    }

    public record SpexgramAlbumDto(UUID id, String title, String url, List<PhotoDto> items) {
    }
}
