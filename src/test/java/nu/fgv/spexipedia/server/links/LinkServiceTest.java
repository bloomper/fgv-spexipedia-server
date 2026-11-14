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
 * Section 5.4 of the spec: who sees which photos from Spexgram.
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
    void visitorsSeePublicPhotosAndAreNotToldOfTheOthers() {
        when(callerService.currentIfAny()).thenReturn(Optional.empty());

        final LinkDtos.PhotoSetDto photos = service.photos(List.of(PUBLIC, MEMBERS));

        assertThat(photos.photos()).extracting(LinkDtos.PhotoDto::itemId).containsExactly(PUBLIC);
        assertThat(photos.hiddenCount()).isZero();
        assertThat(photos.itemIds()).isEmpty();
        verify(client, never()).spexgramItems(any(), eq(true));
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
        when(callerService.currentIfAny()).thenReturn(Optional.empty());
        when(client.spexgramItems(any(), anyBoolean())).thenReturn(Optional.empty());

        assertThat(service.photos(List.of(PUBLIC)).available()).isFalse();
    }
}
