package nu.fgv.spexipedia.server.people;

import nu.fgv.spexipedia.server.TestCallers;
import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.people.SpexregisterClient.Participant;
import nu.fgv.spexipedia.server.people.SpexregisterClient.PeopleUnavailableException;
import nu.fgv.spexipedia.server.people.SpexregisterClient.Spexare;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Section 5.3 of the spec: visitors get nicknames only, and Spexregister is never asked for names on their behalf.
 */
class PeopleServiceTest {

    private static final UUID SPEX = UUID.randomUUID();
    private static final UUID WITH_NICKNAME = UUID.randomUUID();
    private static final UUID WITHOUT_NICKNAME = UUID.randomUUID();

    private final SpexregisterClient client = mock(SpexregisterClient.class);
    private final CallerService callerService = mock(CallerService.class);
    private PeopleService service;

    @BeforeEach
    void setUp() {
        final SpexipediaProperties properties = mock(SpexipediaProperties.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

        when(properties.apps().spexregister().webUrl()).thenReturn("https://register.example/");
        service = new PeopleService(client, callerService, properties);
    }

    @Test
    void visitorsSeeNicknamesOnlyAndNamesAreNeverAskedFor() {
        when(callerService.currentIfAny()).thenReturn(Optional.empty());
        when(client.participants(List.of(SPEX), false)).thenReturn(List.of(
                new Participant(WITH_NICKNAME, "Basen", null, SPEX, List.of(), List.of()),
                new Participant(WITHOUT_NICKNAME, null, null, SPEX, List.of(), List.of())));

        final List<PeopleService.Shown> shown = service.participants(List.of(SPEX)).orElseThrow();

        assertThat(shown).singleElement().satisfies(s -> {
            assertThat(s.person().nickname()).isEqualTo("Basen");
            assertThat(s.person().name()).isNull();
            assertThat(s.person().id()).isNull();
            assertThat(s.person().url()).isNull();
        });
        verify(client).participants(List.of(SPEX), false);
    }

    @Test
    void membersSeeNamesAndALinkWhenTheyHaveAccessToSpexregister() {
        when(callerService.currentIfAny()).thenReturn(Optional.of(TestCallers.memberWithApps()));
        when(client.spexare(any(), eq(true))).thenReturn(List.of(new Spexare(WITHOUT_NICKNAME, null, "Greta Gitarr")));

        assertThat(service.spexare(List.of(WITHOUT_NICKNAME)).orElseThrow().get(WITHOUT_NICKNAME)).satisfies(person -> {
            assertThat(person.name()).isEqualTo("Greta Gitarr");
            assertThat(person.url()).isEqualTo("https://register.example/spexare/" + WITHOUT_NICKNAME);
        });
    }

    @Test
    void membersWithoutAccessToSpexregisterGetNoLink() {
        when(callerService.currentIfAny()).thenReturn(Optional.of(TestCallers.member()));
        when(client.spexare(any(), eq(true))).thenReturn(List.of(new Spexare(WITH_NICKNAME, "Basen", "Bertil Basson")));

        assertThat(service.spexare(List.of(WITH_NICKNAME)).orElseThrow().get(WITH_NICKNAME).url()).isNull();
    }

    @Test
    void anUnavailableSpexregisterIsTold() {
        when(callerService.currentIfAny()).thenReturn(Optional.empty());
        when(client.participants(any(), anyBoolean())).thenThrow(new PeopleUnavailableException("down"));

        assertThat(service.participants(List.of(SPEX))).isEmpty();
    }
}
