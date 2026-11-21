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

import nu.fgv.spexipedia.server.TestCallers;
import nu.fgv.spexipedia.server.links.AppsClient.Media;
import nu.fgv.spexipedia.server.links.AppsClient.SpexgramItem;
import nu.fgv.spexipedia.server.user.App;
import nu.fgv.spexipedia.server.user.CallerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sections 5.4 and 4.4 of the spec: who sees which photos from Spexgram, and the records and books from Spexify and
 * Spexarkivet.
 */
class LinkServiceTest {

    private static final UUID PUBLIC = UUID.randomUUID();
    private static final UUID MEMBERS = UUID.randomUUID();

    private final AppsClient client = mock(AppsClient.class);
    private final CallerService callerService = mock(CallerService.class);
    private final LinkService service = new LinkService(client, callerService);

    @BeforeEach
    void setUp() {
        when(client.web(App.SPEXGRAM)).thenReturn(Optional.of("https://gram.example"));
        when(client.spexgramItems(any(), eq(false))).thenReturn(Optional.of(List.of(item(PUBLIC, "PUBLIC"))));
        when(client.spexgramItems(any(), eq(true))).thenReturn(Optional.of(List.of(item(PUBLIC, "PUBLIC"), item(MEMBERS, "MEMBERS"))));
    }

    private static SpexgramItem item(final UUID id, final String visibility) {
        return new SpexgramItem(id, "PHOTO", null, 100, 100, visibility, new Media("t", "p", null));
    }

    @Test
    void visitorsSeeNoPhotosEvenPublicOnesAndAreNotToldOfThem() {
        when(callerService.currentIfAny()).thenReturn(Optional.empty());

        final LinkDtos.PhotoSetDto photos = service.photos(List.of(PUBLIC, MEMBERS));

        assertThat(photos.photos()).isEmpty();
        assertThat(photos.hiddenCount()).isZero();
        assertThat(photos.itemIds()).isEmpty();
        assertThat(photos.available()).isTrue();
        verify(client, never()).spexgramItems(any(), anyBoolean());
    }

    @Test
    void membersWithoutSpexgramSeePlaceholders() {
        when(callerService.currentIfAny()).thenReturn(Optional.of(TestCallers.member()));

        final LinkDtos.PhotoSetDto photos = service.photos(List.of(PUBLIC, MEMBERS));

        assertThat(photos.photos()).hasSize(1);
        assertThat(photos.hiddenCount()).isEqualTo(1);
    }

    @Test
    void membersWithSpexgramSeeEverything() {
        when(callerService.currentIfAny()).thenReturn(Optional.of(TestCallers.memberWithApps()));

        final LinkDtos.PhotoSetDto photos = service.photos(List.of(PUBLIC, MEMBERS));

        assertThat(photos.photos()).extracting(LinkDtos.PhotoDto::itemId).containsExactly(PUBLIC, MEMBERS);
        assertThat(photos.photos().getFirst().url()).isEqualTo("https://gram.example/items/" + PUBLIC);
        assertThat(photos.hiddenCount()).isZero();
    }

    @Test
    void anUnreachableSpexgramIsTold() {
        when(callerService.currentIfAny()).thenReturn(Optional.of(TestCallers.member()));
        when(client.spexgramItems(any(), anyBoolean())).thenReturn(Optional.empty());

        assertThat(service.photos(List.of(PUBLIC)).available()).isFalse();
    }

    private static AppsClient.DiscographyAlbum album(final UUID id, final Integer year, final UUID numberId) {
        return new AppsClient.DiscographyAlbum(id, "Lasse-Maja", year, "Studio", "Studio", "EP", "EP", "CEP 019", "1984-12",
                null, "Dirigenten", null, "https://spexify.example/media/cover", null, List.of(
                new AppsClient.DiscographyTrack(UUID.randomUUID(), "A", 1, 1, "Ouvertyr", numberId, true),
                new AppsClient.DiscographyTrack(UUID.randomUUID(), "B", 1, 2, "Finalen", null, false)));
    }

    @Test
    void everyoneSeesTheRecordsAndOnlyUsersOfSpexifyTheirLinks() {
        final UUID older = UUID.randomUUID();
        final UUID newer = UUID.randomUUID();
        final UUID number = UUID.randomUUID();

        when(client.web(App.SPEXIFY)).thenReturn(Optional.of("https://spexify.example"));
        when(client.spexifyDiscography(any())).thenReturn(Optional.of(List.of(album(newer, 1990, number), album(older, 1984, number))));
        when(callerService.currentIfAny()).thenReturn(Optional.empty());

        final List<LinkDtos.RecordDto> visitor = service.records(List.of(UUID.randomUUID()));

        assertThat(visitor).extracting(LinkDtos.RecordDto::id).containsExactly(older, newer);
        assertThat(visitor.getFirst().url()).isNull();
        assertThat(visitor.getFirst().tracks()).extracting(LinkDtos.RecordTrackDto::url).containsOnlyNulls();

        when(callerService.currentIfAny()).thenReturn(Optional.of(TestCallers.memberWithApps()));

        final LinkDtos.RecordDto record = service.records(List.of(UUID.randomUUID())).getFirst();

        assertThat(record.url()).isEqualTo("https://spexify.example/albums/" + older);
        // A track without audio has nothing to play
        assertThat(record.tracks().get(0).url()).startsWith("https://spexify.example/albums/" + older + "?track=");
        assertThat(record.tracks().get(1).url()).isNull();
        assertThat(record.tracks().get(0).numberId()).isEqualTo(number);
    }

    @Test
    void aNumberShowsTheBooksItIsIn() {
        final UUID number = UUID.randomUUID();
        final UUID other = UUID.randomUUID();
        final UUID book = UUID.randomUUID();

        when(client.web(App.ARCHIVE)).thenReturn(Optional.of("https://arkiv.example"));
        when(client.archiveLinks(any(), any())).thenReturn(Optional.of(new AppsClient.ArchiveLinks(List.of(), List.of(), List.of(
                new AppsClient.ArchiveBook(book, "Chalmersspex I", 1, "1975", "Spexbok", "Spex book", null, List.of(
                        new AppsClient.ArchiveBookSong(3, number, true), new AppsClient.ArchiveBookSong(4, other, false)))))));
        when(callerService.currentIfAny()).thenReturn(Optional.empty());

        assertThat(service.numberBooks(number, List.of(number, other))).singleElement().satisfies(found -> {
            assertThat(found.title()).isEqualTo("Chalmersspex I");
            assertThat(found.position()).isEqualTo(3);
            assertThat(found.textOnly()).isTrue();
            assertThat(found.url()).isNull();
        });

        when(callerService.currentIfAny()).thenReturn(Optional.of(new nu.fgv.spexipedia.server.user.Caller("s-archive", "a@spexipedia.test",
                "A", java.util.Set.of(nu.fgv.spexipedia.server.user.Role.USER), java.util.Set.of(App.ARCHIVE))));
        assertThat(service.numberBooks(number, List.of(number)).getFirst().url()).isEqualTo("https://arkiv.example/manuscripts/" + book);
    }

    private static nu.fgv.spexipedia.server.user.Caller with(final App... apps) {
        return new nu.fgv.spexipedia.server.user.Caller("s-apps", "a@spexipedia.test", "A",
                java.util.Set.of(nu.fgv.spexipedia.server.user.Role.USER), java.util.Set.of(apps));
    }

    @Test
    void aNumberLeadsToTheChaptersWhereItIsSeen() {
        final UUID number = UUID.randomUUID();
        final UUID track = UUID.randomUUID();
        final UUID video = UUID.randomUUID();

        when(client.web(App.SPEXFLIX)).thenReturn(Optional.of("https://flix.example"));
        when(client.spexifyLinks(any(), any())).thenReturn(Optional.of(new AppsClient.SpexifyLinks(List.of(),
                List.of(new AppsClient.SpexifyTrack(track, "Ouvertyr", number, UUID.randomUUID())))));
        when(client.spexflixChapters(any())).thenReturn(Optional.of(List.of(new AppsClient.SpexflixChapter(track, video, "Premiären", "Ouvertyr", 75))));
        when(callerService.currentIfAny()).thenReturn(Optional.of(with(App.SPEXFLIX)));

        assertThat(service.numberLinks(number, List.of(number))).singleElement().satisfies(link -> {
            assertThat(link.kind()).isEqualTo(LinkDtos.AppLinkKind.SPEXFLIX_CHAPTER);
            assertThat(link.url()).isEqualTo("https://flix.example/videos/" + video + "?t=75");
            assertThat(link.detail()).isEqualTo("Premiären");
        });
    }

    @Test
    void aSpexLeadsToEveryApplicationsPageOfItAndNamesTheKindsOfDocuments() {
        final UUID spex = UUID.randomUUID();

        when(client.web(App.ARCHIVE)).thenReturn(Optional.of("https://arkiv.example"));
        when(client.web(App.SPEXIFY)).thenReturn(Optional.of("https://spexify.example"));
        when(client.spexgramAlbums(any(), anyBoolean())).thenReturn(Optional.of(List.of()));
        when(client.spexifyLinks(any(), any())).thenReturn(Optional.of(new AppsClient.SpexifyLinks(List.of(), List.of())));
        when(client.archiveLinks(any(), any())).thenReturn(Optional.of(new AppsClient.ArchiveLinks(List.of(
                new AppsClient.ArchiveDocument(UUID.randomUUID(), "Lasse-Maja", spex, "Manus", "Manuscript"),
                new AppsClient.ArchiveDocument(UUID.randomUUID(), "Pressklipp 1984", spex, "Pressklipp", "Press cuttings")), List.of(), List.of())));
        when(callerService.currentIfAny()).thenReturn(Optional.of(with(App.ARCHIVE, App.SPEXIFY)));

        final List<LinkDtos.AppLinkDto> links = service.spexLinks(List.of(spex));

        assertThat(links).extracting(LinkDtos.AppLinkDto::kind).containsExactly(LinkDtos.AppLinkKind.ARCHIVE_MANUSCRIPT,
                LinkDtos.AppLinkKind.ARCHIVE_DOCUMENT, LinkDtos.AppLinkKind.SPEXIFY_SPEX, LinkDtos.AppLinkKind.ARCHIVE_SPEX);
        assertThat(links.get(1).detail()).isEqualTo("Pressklipp");
        assertThat(links.get(2).url()).isEqualTo("https://spexify.example/albums?spex=" + spex);
        assertThat(links.get(3).url()).isEqualTo("https://arkiv.example/spex/" + spex);
    }

    @Test
    void anArticleLinksToMaterialOnlyForThoseWithAccessThere() {
        final nu.fgv.spexipedia.server.revision.MarkdownRenderer.AppRef ref =
                nu.fgv.spexipedia.server.revision.MarkdownRenderer.AppRef.parse("spexify:album/0190aaaa-0000-7000-8000-000000000001").orElseThrow();

        when(client.web(App.SPEXIFY)).thenReturn(Optional.of("https://spexify.example"));
        when(callerService.currentIfAny()).thenReturn(Optional.empty());
        assertThat(service.materialLink(ref)).isEmpty();
        when(callerService.currentIfAny()).thenReturn(Optional.of(with(App.SPEXIFY)));
        assertThat(service.materialLink(ref)).contains("https://spexify.example/albums/0190aaaa-0000-7000-8000-000000000001");
        assertThat(service.materialLink(new nu.fgv.spexipedia.server.revision.MarkdownRenderer.AppRef("spexify", "konsert", ref.id()))).isEmpty();
    }
}
