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
import java.util.Map;
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
 *     <li>The records (Spexify's discography) and the books a number is in (Spexarkivet's books) to everyone, as
 *     Spexipedia's own recordings and books were; their links into those applications only to users with access.</li>
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
            // A manuscript is shown with the manuscript and programme section; documents of other kinds are named by kind
            client.archiveLinks(productions, List.of()).ifPresent(archive -> nonNull(archive.manuscripts()).forEach(document ->
                    link(App.ARCHIVE, "/manuscripts/" + document.id()).ifPresent(url -> links.add(new AppLinkDto(
                            manuscript(document) ? AppLinkKind.ARCHIVE_MANUSCRIPT : AppLinkKind.ARCHIVE_DOCUMENT, document.title(), url,
                            english() ? document.kindNameEn() : document.kindNameSv())))));
        }
        // Everything each application has about the spex, on its page there, for users with access
        final UUID spex = productions.getFirst();

        everything(links, App.SPEXIFY, AppLinkKind.SPEXIFY_SPEX, "/albums?spex=" + spex);
        everything(links, App.SPEXFLIX, AppLinkKind.SPEXFLIX_SPEX, "/spex/" + spex);
        everything(links, App.SPEXGRAM, AppLinkKind.SPEXGRAM_SPEX, "/spex/" + spex);
        everything(links, App.ARCHIVE, AppLinkKind.ARCHIVE_SPEX, "/spex/" + spex);
        return links;
    }

    private void everything(final List<AppLinkDto> links, final App app, final AppLinkKind kind, final String path) {
        if (access(app)) {
            link(app, path).ifPresent(url -> links.add(new AppLinkDto(kind, "", url)));
        }
    }

    /**
     * Whether a document of Spexarkivet is the spex' manuscript: of the kind named so, the archive's first kind.
     */
    private static boolean manuscript(final AppsClient.ArchiveDocument document) {
        return document.kindNameSv() == null || "manus".equalsIgnoreCase(document.kindNameSv().strip());
    }

    /**
     * Whether the request asked for English; Swedish otherwise, as everywhere in Spexipedia.
     */
    private static boolean english() {
        final org.springframework.context.i18n.LocaleContext context = org.springframework.context.i18n.LocaleContextHolder.getLocaleContext();

        return nu.fgv.spexipedia.server.util.Locales.supported(context == null ? null : context.getLocale()).equals(java.util.Locale.ENGLISH);
    }

    /**
     * Links about a number: its tracks in Spexify, the chapters of Spexflix's videos where it is seen, and its
     * arrangements in Spexarkivet.
     *
     * @param numberIds every number of the page, asked for at once and cached, so a page asks each application once
     */
    public List<AppLinkDto> numberLinks(final UUID numberId, final List<UUID> numberIds) {
        final List<AppLinkDto> links = new ArrayList<>();

        final List<AppsClient.SpexifyTrack> tracks = access(App.SPEXIFY) || access(App.SPEXFLIX)
                ? client.spexifyLinks(List.of(), numberIds).map(spexify -> nonNull(spexify.tracks())).orElse(List.of()) : List.of();
        final List<AppsClient.SpexifyTrack> own = tracks.stream().filter(track -> numberId.equals(track.numberId())).toList();

        if (access(App.SPEXIFY)) {
            own.stream().filter(track -> track.albumId() != null)
                    .forEach(track -> link(App.SPEXIFY, "/albums/" + track.albumId() + "?track=" + track.id())
                            .ifPresent(url -> links.add(new AppLinkDto(AppLinkKind.SPEXIFY_TRACK, track.title(), url))));
        }
        if (access(App.SPEXFLIX) && !own.isEmpty()) {
            // Asked for every track of the page at once, so the page asks Spexflix once
            final java.util.Set<UUID> mine = own.stream().map(AppsClient.SpexifyTrack::id).collect(java.util.stream.Collectors.toSet());

            client.spexflixChapters(tracks.stream().map(AppsClient.SpexifyTrack::id).sorted().toList()).ifPresent(chapters -> chapters.stream()
                    .filter(chapter -> mine.contains(chapter.trackId()))
                    .forEach(chapter -> link(App.SPEXFLIX, "/videos/" + chapter.videoId() + "?t=" + chapter.startSeconds())
                            .ifPresent(url -> links.add(new AppLinkDto(AppLinkKind.SPEXFLIX_CHAPTER, chapter.chapterTitle(), url, chapter.videoTitle())))));
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
     * The records of a spex and its revivals: Spexify's discography (spec 4.4), for everyone, oldest first. Links into
     * Spexify only for users with access there. Empty when Spexify is unavailable.
     *
     * @param productions the spex and its revivals
     */
    public List<LinkDtos.RecordDto> records(final List<UUID> productions) {
        final boolean spexify = access(App.SPEXIFY);

        return client.spexifyDiscography(productions).orElse(List.of()).stream()
                .sorted(java.util.Comparator.comparing((AppsClient.DiscographyAlbum album) -> album.year() == null ? Integer.MAX_VALUE : album.year())
                        .thenComparing(album -> String.valueOf(album.catalogueNumber())))
                .map(album -> new LinkDtos.RecordDto(album.id(), album.title(), album.year(), album.kindNameSv(), album.kindNameEn(),
                        album.mediumNameSv(), album.mediumNameEn(), album.catalogueNumber(), album.released(), album.recorded(),
                        album.conductor(), album.spexId(), album.coverUrl(), album.backCoverUrl(),
                        spexify ? link(App.SPEXIFY, "/albums/" + album.id()).orElse(null) : null,
                        nonNull(album.tracks()).stream()
                                .map(track -> new LinkDtos.RecordTrackDto(track.id(), track.side(), track.discNumber(), track.trackNumber(),
                                        track.title(), track.numberId(), track.hasAudio(), spexify && track.hasAudio()
                                        ? link(App.SPEXIFY, "/albums/" + album.id() + "?track=" + track.id()).orElse(null) : null, null))
                                .toList()))
                .toList();
    }

    // Sections that show material of the other applications (spec 4.2.1) -------------------------------------------

    /**
     * A Spexflix video for a section: to users with access to Spexflix with its poster, chapters and playback; to other
     * logged-in users only its title; to visitors nothing. Empty also when it is not found or Spexflix cannot be
     * reached.
     */
    public Optional<LinkDtos.EmbeddedVideoDto> video(final UUID videoId, final @Nullable Integer startSeconds) {
        if (member().isEmpty()) {
            return Optional.empty();
        }

        final boolean spexflix = access(App.SPEXFLIX);

        return client.spexflixVideo(videoId).map(video -> {
            final Integer duration = video.durationMs() == null ? null : (int) Math.round(video.durationMs() / 1000);
            final AppsClient.SpexflixPlayback playback = video.playback();

            return new LinkDtos.EmbeddedVideoDto(video.id(), video.title(), video.year(), duration, spexflix,
                    spexflix ? link(App.SPEXFLIX, "/videos/" + video.id()).orElse(null) : null,
                    spexflix ? video.posterUrl() : null, startSeconds,
                    spexflix ? nonNull(video.chapters()).stream().map(c -> new LinkDtos.VideoChapterDto(c.title(), c.startSeconds())).toList() : List.of(),
                    spexflix && playback != null ? new LinkDtos.VideoPlaybackDto(playback.masterUrl(), playback.thumbnailsUrl(),
                            nonNull(playback.subtitles()).stream().map(sub -> new LinkDtos.VideoSubtitleDto(sub.language(), sub.label(), sub.url())).toList(),
                            playback.expiresAt()) : null);
        });
    }

    /**
     * A Spexgram album for a section, or the picked items of it in their order: as photos from Spexgram are shown
     * (spec 5.4), public items to logged-in users, members' ones to users with access to Spexgram, and nothing to
     * visitors.
     */
    public Optional<LinkDtos.EmbeddedPhotosDto> album(final UUID albumId, final List<UUID> itemIds) {
        final Optional<Caller> member = member();

        if (member.isEmpty()) {
            return Optional.empty();
        }

        final boolean spexgram = access(App.SPEXGRAM);

        return client.spexgramAlbum(albumId, true).map(album -> {
            final List<SpexgramItem> items = album.items() == null || album.items().items() == null ? List.of()
                    : album.items().items().stream().filter(item -> item.media() != null).toList();
            final Map<UUID, SpexgramItem> byId = items.stream().collect(java.util.stream.Collectors.toMap(SpexgramItem::id, item -> item, (a, _) -> a));
            final List<SpexgramItem> chosen = itemIds.isEmpty() ? items : itemIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
            final List<PhotoDto> photos = chosen.stream().filter(item -> spexgram || item.isPublic()).map(this::photo).toList();
            final boolean author = member.get().isAuthor();

            return new LinkDtos.EmbeddedPhotosDto(album.id(), album.title(),
                    link(App.SPEXGRAM, "/albums/" + album.id()).orElse(null),
                    new PhotoSetDto(photos, spexgram ? 0 : chosen.size() - photos.size(), true, author ? itemIds : List.of()));
        });
    }

    /**
     * A Spexify album for a section: shown to everyone, as the records are (spec 4.4); its tracks play, and link to
     * Spexify, only for users with access there.
     */
    public Optional<LinkDtos.RecordDto> record(final UUID albumId) {
        final boolean spexify = access(App.SPEXIFY);

        return client.spexifyAlbum(albumId).map(album -> new LinkDtos.RecordDto(album.id(), album.title(), album.year(),
                album.kindNameSv(), album.kindNameEn(), album.mediumNameSv(), album.mediumNameEn(), album.catalogueNumber(),
                album.released(), album.recorded(), album.conductor(), album.spexId(), album.coverUrl(), album.backCoverUrl(),
                spexify ? link(App.SPEXIFY, "/albums/" + album.id()).orElse(null) : null,
                nonNull(album.tracks()).stream()
                        .map(track -> new LinkDtos.RecordTrackDto(track.id(), track.side(), track.discNumber(), track.trackNumber(),
                                track.title(), track.numberId(), track.hasAudio(),
                                spexify && track.hasAudio() ? link(App.SPEXIFY, "/albums/" + album.id() + "?track=" + track.id()).orElse(null) : null,
                                spexify && track.hasAudio() ? track.audioUrl() : null))
                        .toList()));
    }

    /**
     * A Spexarkivet document for a section: to users with access to Spexarkivet with its files' first pages; to other
     * logged-in users only its kind and title; to visitors nothing.
     */
    public Optional<LinkDtos.EmbeddedDocumentDto> document(final UUID documentId) {
        if (member().isEmpty()) {
            return Optional.empty();
        }

        final boolean archive = access(App.ARCHIVE);

        return client.archiveDocument(documentId).map(document -> new LinkDtos.EmbeddedDocumentDto(document.id(), document.title(),
                document.year(), english() ? document.kindNameEn() : document.kindNameSv(), archive,
                archive ? link(App.ARCHIVE, "/manuscripts/" + document.id()).orElse(null) : null,
                archive ? nonNull(document.files()).stream()
                        .map(file -> new LinkDtos.DocumentPreviewDto(file.id(), file.title(), file.pages(), file.previewUrl())).toList()
                        : List.of()));
    }

    /**
     * The books a number is in: Spexarkivet's books (spec 4.4), for everyone. Links into Spexarkivet only for users with
     * access there. Empty when the archive is unavailable.
     *
     * @param numberIds every number of the page, asked for at once and cached
     */
    public List<LinkDtos.NumberBookDto> numberBooks(final UUID numberId, final List<UUID> numberIds) {
        final boolean archive = access(App.ARCHIVE);

        return client.archiveLinks(List.of(), numberIds).map(links -> nonNull(links.books())).orElse(List.of()).stream()
                .flatMap(book -> nonNull(book.songs()).stream().filter(song -> numberId.equals(song.numberId()))
                        .map(song -> new LinkDtos.NumberBookDto(book.id(), book.title(), book.volume(), book.publishedYear(),
                                book.kindNameSv(), book.kindNameEn(), song.position(), song.textOnly(),
                                archive ? link(App.ARCHIVE, "/manuscripts/" + book.id()).orElse(null) : null)))
                .toList();
    }

    /**
     * The address of material in another application that an article links to (spec 4.5), for a viewer with access
     * there; empty for everyone else, who see the link's text only.
     */
    public Optional<String> materialLink(final nu.fgv.spexipedia.server.revision.MarkdownRenderer.AppRef ref) {
        final String id = ref.id().toString();
        final Optional<Map.Entry<App, String>> target = switch (ref.app() + ":" + ref.kind()) {
            case "spexify:album" -> Optional.of(Map.entry(App.SPEXIFY, "/albums/" + id));
            case "spexflix:video" -> Optional.of(Map.entry(App.SPEXFLIX, "/videos/" + id));
            case "spexflix:collection" -> Optional.of(Map.entry(App.SPEXFLIX, "/collections/" + id));
            case "spexgram:album" -> Optional.of(Map.entry(App.SPEXGRAM, "/albums/" + id));
            case "spexgram:item" -> Optional.of(Map.entry(App.SPEXGRAM, "/items/" + id));
            case "arkiv:document" -> Optional.of(Map.entry(App.ARCHIVE, "/manuscripts/" + id));
            case "arkiv:arrangement" -> Optional.of(Map.entry(App.ARCHIVE, "/arrangements/" + id));
            default -> Optional.empty();
        };

        return target.filter(entry -> access(entry.getKey())).flatMap(entry -> link(entry.getKey(), entry.getValue()));
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
