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

import nu.fgv.spexipedia.server.links.AppsClient.SpexgramItem;
import nu.fgv.spexipedia.server.links.LinkDtos.AppLinkDto;
import nu.fgv.spexipedia.server.links.LinkDtos.AppLinkKind;
import nu.fgv.spexipedia.server.links.LinkDtos.PhotoDto;
import nu.fgv.spexipedia.server.links.LinkDtos.PhotoSetDto;
import nu.fgv.spexipedia.server.links.LinkDtos.SpexgramAlbumDto;
import nu.fgv.spexipedia.server.user.App;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the viewer is shown from the other applications (sections 5.4 and 5.9 of the spec):
 * <ul>
 *     <li>Photos and short videos from Spexgram show people, so never visitors, even when public in Spexgram. Logged-in
 *     users see the public items; members' items, through Spexgram's {@code SPEXIPEDIA} role, only users with access to
 *     Spexgram, and other logged-in users get a count of what they may not see.</li>
 *     <li>Spexgram's public albums of a spex to everyone, its members' albums to users with access.</li>
 *     <li>Spexify's albums and tracks, Spexflix's videos and Spexarkivet's manuscripts and arrangements only to users
 *     with access to that application.</li>
 * </ul>
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class LinkService {

    private final AppsClient client;
    private final CallerService callerService;

    public LinkService(final AppsClient client, final CallerService callerService) {
        this.client = client;
        this.callerService = callerService;
    }

    private Optional<Caller> member() {
        return callerService.currentIfAny().filter(Caller::isUser);
    }

    private boolean access(final App app) {
        return member().map(caller -> caller.hasAccess(app)).orElse(false);
    }

    /**
     * The chosen Spexgram items, as the viewer may see them.
     */
    public PhotoSetDto photos(final List<UUID> itemIds) {
        final Optional<Caller> member = member();
        final boolean author = member.map(Caller::isAuthor).orElse(false);
        final boolean spexgram = member.map(caller -> caller.hasAccess(App.SPEXGRAM)).orElse(false);
        final List<UUID> chosen = author ? itemIds : List.of();

        // Visitors see no photos of people, and are not told there are any
        if (itemIds.isEmpty() || member.isEmpty()) {
            return new PhotoSetDto(List.of(), 0, true, chosen);
        }

        final Optional<List<SpexgramItem>> items = client.spexgramItems(itemIds, spexgram);

        if (items.isEmpty()) {
            return new PhotoSetDto(List.of(), 0, false, chosen);
        }

        final List<PhotoDto> photos = items.get().stream()
                .filter(item -> item.media() != null)
                .filter(item -> spexgram || item.isPublic())
                .map(this::photo)
                .toList();
        // Members without access to Spexgram see placeholders for what they may not see
        final int hidden = spexgram ? 0 : Math.max(0, itemIds.size() - photos.size());

        return new PhotoSetDto(photos, hidden, true, chosen);
    }

    private PhotoDto photo(final SpexgramItem item) {
        return new PhotoDto(item.id(), item.caption(), item.media().thumbnailUrl(), item.media().previewUrl(), item.width(),
                item.height(), client.web(App.SPEXGRAM).map(web -> web + "/items/" + item.id()).orElse(""), item.isPublic(),
                "VIDEO".equals(item.type()), item.media().videoUrl());
    }

    /**
     * AUTHOR. Spexgram's albums of the spex with their items, members' included, to pick photos from.
     */
    public List<SpexgramAlbumDto> spexgramAlbums(final UUID spexId) {
        callerService.current().requireAuthor();
        return client.spexgramAlbumItems(spexId)
                .orElseThrow(() -> new ResourceNotFoundException("error.spexgram.unavailable"))
                .stream()
                .map(album -> new SpexgramAlbumDto(album.id(), album.title(),
                        client.web(App.SPEXGRAM).map(web -> web + "/albums/" + album.id()).orElse(""),
                        album.items() == null || album.items().items() == null ? List.of()
                                : album.items().items().stream().filter(item -> item.media() != null).map(this::photo).toList()))
                .toList();
    }

    /**
     * Links about a spex: Spexgram's albums, Spexify's albums, Spexflix's videos and Spexarkivet's manuscripts.
     *
     * @param productions the spex and its revivals; the first is the spex itself
     */
    public List<AppLinkDto> spexLinks(final List<UUID> productions) {
        final List<AppLinkDto> links = new ArrayList<>();
        final boolean spexgram = access(App.SPEXGRAM);

        client.spexgramAlbums(productions.getFirst(), spexgram).ifPresent(albums -> albums.forEach(album ->
                link(App.SPEXGRAM, "/albums/" + album.id()).ifPresent(url -> links.add(new AppLinkDto(AppLinkKind.SPEXGRAM_ALBUM, album.title(), url)))));
        if (access(App.SPEXIFY)) {
            client.spexifyLinks(productions, List.of()).ifPresent(spexify -> nonNull(spexify.albums()).forEach(album ->
                    link(App.SPEXIFY, "/albums/" + album.id()).ifPresent(url -> links.add(new AppLinkDto(AppLinkKind.SPEXIFY_ALBUM, album.title(), url)))));
        }
        if (access(App.SPEXFLIX)) {
            client.spexflixVideos(productions).ifPresent(videos -> videos.forEach(video ->
                    link(App.SPEXFLIX, "/videos/" + video.id()).ifPresent(url -> links.add(new AppLinkDto(AppLinkKind.SPEXFLIX_VIDEO, video.title(), url)))));
        }
        if (access(App.ARCHIVE)) {
            client.archiveLinks(productions, List.of()).ifPresent(archive -> nonNull(archive.manuscripts()).forEach(manuscript ->
                    link(App.ARCHIVE, "/manuscripts/" + manuscript.id()).ifPresent(url -> links.add(new AppLinkDto(AppLinkKind.ARCHIVE_MANUSCRIPT, manuscript.title(), url)))));
        }
        return links;
    }

    /**
     * Links about a number: its tracks in Spexify and its arrangements in Spexarkivet.
     *
     * @param numberIds every number of the page, asked for at once and cached, so a page asks each application once
     */
    public List<AppLinkDto> numberLinks(final UUID numberId, final List<UUID> numberIds) {
        final List<AppLinkDto> links = new ArrayList<>();

        if (access(App.SPEXIFY)) {
            client.spexifyLinks(List.of(), numberIds).ifPresent(spexify -> nonNull(spexify.tracks()).stream()
                    .filter(track -> numberId.equals(track.numberId()) && track.albumId() != null)
                    .forEach(track -> link(App.SPEXIFY, "/albums/" + track.albumId() + "?track=" + track.id())
                            .ifPresent(url -> links.add(new AppLinkDto(AppLinkKind.SPEXIFY_TRACK, track.title(), url)))));
        }
        if (access(App.ARCHIVE)) {
            client.archiveLinks(List.of(), numberIds).ifPresent(archive -> nonNull(archive.arrangements()).stream()
                    .filter(arrangement -> numberId.equals(arrangement.numberId()))
                    .forEach(arrangement -> link(App.ARCHIVE, "/arrangements/" + arrangement.id())
                            .ifPresent(url -> links.add(new AppLinkDto(AppLinkKind.ARCHIVE_ARRANGEMENT, arrangement.title(), url)))));
        }
        return links;
    }

    /**
     * The link to a recording's album in Spexify, for users with access.
     */
    public List<AppLinkDto> recordingLinks(final @Nullable UUID spexifyAlbumId, final String title) {
        if (spexifyAlbumId == null || !access(App.SPEXIFY)) {
            return List.of();
        }
        return link(App.SPEXIFY, "/albums/" + spexifyAlbumId).map(url -> List.of(new AppLinkDto(AppLinkKind.SPEXIFY_ALBUM, title, url)))
                .orElse(List.of());
    }

    private Optional<String> link(final App app, final String path) {
        return client.web(app).map(web -> web + path);
    }

    private static <T> List<T> nonNull(final @Nullable List<T> list) {
        return list == null ? List.of() : list;
    }

    /**
     * The maintenance job LINK_CHECK: forgets cached links and photos, so that the next pages ask again.
     */
    public void check() {
        client.clearCaches();
    }
}
