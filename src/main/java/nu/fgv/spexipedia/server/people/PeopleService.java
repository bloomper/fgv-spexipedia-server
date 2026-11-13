package nu.fgv.spexipedia.server.people;

import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.people.SpexregisterClient.Participant;
import nu.fgv.spexipedia.server.people.SpexregisterClient.PeopleUnavailableException;
import nu.fgv.spexipedia.server.user.App;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.error.SpexregisterUnavailableException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * People on pages (section 5.3 of the spec). Personal data reaches pages only from Spexregister, when a page is shown,
 * and only for spexare published there. A visitor sees a published spexare's nickname, or nothing when they have none;
 * a logged-in user ({@code USER}) also their name, and a link to Spexregister when they have access to it.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class PeopleService {

    private static final int MIN_QUERY = 2;

    private final SpexregisterClient client;
    private final CallerService callerService;
    private final @Nullable String spexregisterWeb;

    public PeopleService(final SpexregisterClient client, final CallerService callerService, final SpexipediaProperties properties) {
        this.client = client;
        this.callerService = callerService;
        this.spexregisterWeb = StringUtils.hasText(properties.apps().spexregister().webUrl())
                ? properties.apps().spexregister().webUrl().replaceAll("/+$", "") : null;
    }

    private Optional<Caller> member() {
        return callerService.currentIfAny().filter(Caller::isUser);
    }

    /**
     * The participants of the productions, as the viewer may see them, or empty when Spexregister is unavailable.
     */
    public Optional<List<Shown>> participants(final List<UUID> spexIds) {
        final Optional<Caller> member = member();

        try {
            return Optional.of(client.participants(spexIds, member.isPresent()).stream()
                    .map(participant -> shown(participant, member.orElse(null)))
                    .flatMap(Optional::stream)
                    .toList());
        } catch (final PeopleUnavailableException e) {
            return Optional.empty();
        }
    }

    /**
     * Spexare by id, as the viewer may see them; unpublished ones, and for visitors those without a nickname, are left
     * out. Empty when Spexregister is unavailable.
     */
    public Optional<Map<UUID, PersonDto>> spexare(final Collection<UUID> ids) {
        final Optional<Caller> member = member();

        if (ids.isEmpty()) {
            return Optional.of(Map.of());
        }
        try {
            final Map<UUID, PersonDto> people = new java.util.LinkedHashMap<>();

            // Keyed by the spexare's id, which a visitor's PersonDto doesn't show
            for (final SpexregisterClient.Spexare spexare : client.spexare(ids, member.isPresent())) {
                person(spexare.id(), spexare.nickname(), spexare.name(), member.orElse(null))
                        .ifPresent(person -> people.putIfAbsent(spexare.id(), person));
            }
            return Optional.of(people);
        } catch (final PeopleUnavailableException e) {
            return Optional.empty();
        }
    }

    /**
     * USER. Published spexare matching a name, for picking credits.
     */
    public List<PersonDto> search(final String q) {
        final Caller caller = callerService.current();

        caller.requireUser();
        if (q.strip().length() < MIN_QUERY) {
            throw new BadRequestException("error.search.short");
        }
        try {
            return client.search(q.strip()).stream()
                    .map(spexare -> person(spexare.id(), spexare.nickname(), spexare.name(), caller))
                    .flatMap(Optional::stream)
                    .toList();
        } catch (final PeopleUnavailableException e) {
            throw new SpexregisterUnavailableException(e);
        }
    }

    private Optional<Shown> shown(final Participant participant, final @Nullable Caller member) {
        return person(participant.spexareId(), participant.nickname(), participant.name(), member)
                .map(person -> new Shown(person, participant.spexId(),
                        participant.taskIds() == null ? List.of() : participant.taskIds(),
                        participant.characterIds() == null ? List.of() : participant.characterIds()));
    }

    /**
     * What the viewer may see of a published spexare; nothing for a visitor when they have no nickname.
     */
    private Optional<PersonDto> person(final UUID id, final @Nullable String nickname, final @Nullable String name,
                                       final @Nullable Caller member) {
        final String shownNickname = StringUtils.hasText(nickname) ? nickname.strip() : null;

        if (member == null) {
            return shownNickname == null ? Optional.empty() : Optional.of(new PersonDto(null, shownNickname, null, null));
        }
        if (shownNickname == null && !StringUtils.hasText(name)) {
            return Optional.empty();
        }
        return Optional.of(new PersonDto(id, shownNickname, StringUtils.hasText(name) ? name.strip() : null,
                spexregisterWeb != null && member.hasAccess(App.SPEXREGISTER) ? spexregisterWeb + "/spexare/" + id : null));
    }

    /**
     * A person in one production, with what they did there.
     */
    public record Shown(PersonDto person, UUID spexId, List<UUID> taskIds, List<UUID> characterIds) {
    }
}
