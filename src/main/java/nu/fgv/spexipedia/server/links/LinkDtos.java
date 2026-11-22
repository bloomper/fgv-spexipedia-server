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
        SPEXGRAM_ALBUM,
        SPEXFLIX_CHAPTER,
        ARCHIVE_DOCUMENT,
        SPEXIFY_SPEX,
        SPEXFLIX_SPEX,
        SPEXGRAM_SPEX,
        ARCHIVE_SPEX
    }

    /**
     * @param detail more about it: a document's kind, a chapter's video
     */
    public record AppLinkDto(AppLinkKind kind, String title, String url, @Nullable String detail) {

        public AppLinkDto(final AppLinkKind kind, final String title, final String url) {
            this(kind, title, url, null);
        }
    }

    /**
     * A record of the spex: an album of Spexify's discography (spec 4.4), shown to everyone; {@code url} leads to it in
     * Spexify for users with access there.
     */
    public record RecordDto(UUID id, String title, @Nullable Integer year, String kindNameSv, String kindNameEn,
                            @Nullable String mediumNameSv, @Nullable String mediumNameEn, @Nullable String catalogueNumber,
                            @Nullable String released, @Nullable String recorded, @Nullable String conductor,
                            @Nullable UUID spexId, @Nullable String coverUrl, @Nullable String backCoverUrl,
                            @Nullable String url, List<RecordTrackDto> tracks) {
    }

    /**
     * A track of a record: {@code numberId} is the number it is a recording of; {@code url} plays it in Spexify for users
     * with access, when it has audio; {@code audioUrl}, a signed link to the audio, only in a section that shows the
     * record (spec 4.2.1), for the same users.
     */
    public record RecordTrackDto(UUID id, @Nullable String side, int discNumber, int position, String title,
                                 @Nullable UUID numberId, boolean hasAudio, @Nullable String url, @Nullable String audioUrl) {
    }

    /**
     * A Spexflix video shown in a section of a spex page (spec 4.2.1). Without access to Spexflix, only that it is there:
     * no poster, chapters or playback.
     *
     * @param url Spexflix's page of the video, for users with access there
     */
    public record EmbeddedVideoDto(UUID id, String title, @Nullable Integer year, @Nullable Integer durationSeconds,
                                   boolean access, @Nullable String url, @Nullable String posterUrl,
                                   @Nullable Integer startSeconds, List<VideoChapterDto> chapters,
                                   @Nullable VideoPlaybackDto playback) {
    }

    public record VideoChapterDto(String title, int startSeconds) {
    }

    public record VideoPlaybackDto(String masterUrl, @Nullable String thumbnailsUrl, List<VideoSubtitleDto> subtitles,
                                   String expiresAt) {
    }

    public record VideoSubtitleDto(String language, String label, String url) {
    }

    /**
     * A Spexgram album, or some of its items, shown in a section: the photos as {@link PhotoSetDto} says.
     */
    public record EmbeddedPhotosDto(UUID albumId, String title, @Nullable String url, PhotoSetDto photos) {
    }

    /**
     * A Spexarkivet document shown in a section. Without access to Spexarkivet, only that it is there: no files.
     */
    public record EmbeddedDocumentDto(UUID id, String title, @Nullable String year, @Nullable String kindName,
                                      boolean access, @Nullable String url, List<DocumentPreviewDto> files) {
    }

    /**
     * @param previewUrl a signed link to the first page, as an image; null when the file has none
     */
    public record DocumentPreviewDto(UUID id, String title, @Nullable Integer pages, @Nullable String previewUrl) {
    }

    /**
     * A book a number is in: a book of Spexarkivet (spec 4.4), shown to everyone; {@code url} leads to it in Spexarkivet
     * for users with access there.
     */
    public record NumberBookDto(UUID id, String title, @Nullable Integer volume, @Nullable String publishedYear,
                                String kindNameSv, String kindNameEn, int position, boolean textOnly, @Nullable String url) {
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
