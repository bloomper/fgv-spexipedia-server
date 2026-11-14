/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    /**
     * A photo or short video in Spexgram.
     *
     * @param video    whether it is a video; then {@code videoUrl} plays it and {@code previewUrl} is its poster frame
     * @param videoUrl Spexgram's signed address of the video, for the browser
     */
    public record PhotoDto(UUID itemId, @Nullable String caption, String thumbnailUrl, String previewUrl,
                           @Nullable Integer width, @Nullable Integer height, String url, boolean isPublic,
                           boolean video, @Nullable String videoUrl) {
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
