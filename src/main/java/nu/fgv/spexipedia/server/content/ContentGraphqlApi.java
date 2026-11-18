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

package nu.fgv.spexipedia.server.content;

import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataGraphqlApi;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.content.ContentDtos.CharacterDto;
import nu.fgv.spexipedia.server.content.ContentDtos.NumberDto;
import nu.fgv.spexipedia.server.content.ContentDtos.RecordingDto;
import nu.fgv.spexipedia.server.content.ContentDtos.SceneDto;
import nu.fgv.spexipedia.server.content.ContentDtos.TrackDto;
import nu.fgv.spexipedia.server.content.ListStore.Credit;
import nu.fgv.spexipedia.server.content.ListStore.CreditRef;
import nu.fgv.spexipedia.server.content.ListStore.PlacedImage;
import nu.fgv.spexipedia.server.content.ListStore.RevivalChange;
import nu.fgv.spexipedia.server.content.ListStore.RevivalChangeKind;
import nu.fgv.spexipedia.server.image.Image;
import nu.fgv.spexipedia.server.image.ImageDto;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.kind.Kind;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.links.LinkDtos.AppLinkDto;
import nu.fgv.spexipedia.server.links.LinkDtos.PhotoSetDto;
import nu.fgv.spexipedia.server.links.LinkService;
import nu.fgv.spexipedia.server.page.SpexPageService;
import nu.fgv.spexipedia.server.people.PeopleService;
import nu.fgv.spexipedia.server.people.PersonDto;
import nu.fgv.spexipedia.server.util.Ids;
import org.jspecify.annotations.Nullable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GraphQL for characters, scenes, numbers, recordings and revivals' changes.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class ContentGraphqlApi {

    private final ContentService service;
    private final RecordingService recordingService;
    private final BaseDataService baseData;
    private final BaseDataGraphqlApi baseDataApi;
    private final SpexPageService pageService;
    private final ImageService imageService;
    private final PeopleService peopleService;
    private final LinkService linkService;
    private final KindService kindService;

    @SuppressWarnings("java:S107")
    public ContentGraphqlApi(final ContentService service, final RecordingService recordingService, final BaseDataService baseData,
                             final BaseDataGraphqlApi baseDataApi, final SpexPageService pageService, final ImageService imageService,
                             final PeopleService peopleService, final LinkService linkService, final KindService kindService) {
        this.service = service;
        this.recordingService = recordingService;
        this.baseData = baseData;
        this.baseDataApi = baseDataApi;
        this.pageService = pageService;
        this.imageService = imageService;
        this.peopleService = peopleService;
        this.linkService = linkService;
        this.kindService = kindService;
    }

    // Queries ----------------------------------------------------------------------------------------------------------

    @QueryMapping
    public @Nullable CharacterDto character(@Argument final String id) {
        return service.character(id).orElse(null);
    }

    @QueryMapping
    public List<CharacterDto> charactersByIds(@Argument final List<String> ids) {
        return service.charactersByIds(ids);
    }

    @QueryMapping
    public @Nullable NumberDto number(@Argument final String id) {
        return service.number(id).orElse(null);
    }

    @QueryMapping
    public List<NumberDto> numbersByIds(@Argument final List<String> ids) {
        return service.numbersByIds(ids);
    }

    @QueryMapping
    public List<RecordingDto> recordings() {
        return recordingService.recordings();
    }

    @QueryMapping
    public @Nullable RecordingDto recording(@Argument final String id) {
        return recordingService.recording(id).orElse(null);
    }

    // Mutations --------------------------------------------------------------------------------------------------------

    @MutationMapping
    public CharacterDto characterCreate(@Argument final String spexId, @Argument final Map<String, String> input) {
        return service.createCharacter(Ids.require(spexId), input.get("name"), input.get("description"));
    }

    @MutationMapping
    public CharacterDto characterUpdate(@Argument final String id, @Argument final Map<String, String> input) {
        return service.updateCharacter(Ids.require(id), input.get("name"), input.get("description"));
    }

    @MutationMapping
    public boolean characterDelete(@Argument final String id) {
        return service.deleteCharacter(Ids.require(id));
    }

    @MutationMapping
    public List<CharacterDto> charactersPaste(@Argument final String spexId, @Argument final String text) {
        return service.pasteCharacters(Ids.require(spexId), text);
    }

    @MutationMapping
    public List<CharacterDto> charactersReorder(@Argument final String spexId, @Argument final List<String> ids) {
        return service.reorderCharacters(Ids.require(spexId), ids.stream().map(Ids::require).toList());
    }

    @MutationMapping
    public CharacterDto characterSetImages(@Argument final String id, @Argument final List<Map<String, String>> images) {
        return service.setCharacterImages(Ids.require(id), imageRefs(images));
    }

    public static List<ContentService.ImageRef> imageRefs(final List<Map<String, String>> images) {
        return images.stream().map(image -> new ContentService.ImageRef(Ids.require(image.get("imageId")),
                Ids.optional(image.get("kindId")), image.get("caption"))).toList();
    }

    @MutationMapping
    public SceneDto sceneCreate(@Argument final String spexId, @Argument final Map<String, Object> input) {
        return service.createScene(Ids.require(spexId), scene(input));
    }

    @MutationMapping
    public SceneDto sceneUpdate(@Argument final String id, @Argument final Map<String, Object> input) {
        return service.updateScene(Ids.require(id), scene(input));
    }

    @MutationMapping
    public boolean sceneDelete(@Argument final String id) {
        return service.deleteScene(Ids.require(id));
    }

    @MutationMapping
    public List<SceneDto> scenesPaste(@Argument final String spexId, @Argument final String text) {
        return service.pasteScenes(Ids.require(spexId), text);
    }

    @MutationMapping
    public List<SceneDto> scenesReorder(@Argument final String spexId, @Argument final List<Map<String, Object>> items) {
        return service.reorderScenes(Ids.require(spexId), order(items));
    }

    private static ContentService.SceneInput scene(final Map<String, Object> input) {
        return new ContentService.SceneInput(((Number) input.get("act")).intValue(), (String) input.get("title"),
                (String) input.get("place"), Boolean.TRUE.equals(input.get("intervalAfter")));
    }

    private static List<ContentService.Order> order(final List<Map<String, Object>> items) {
        return items.stream().map(item -> new ContentService.Order(Ids.require((String) item.get("id")),
                ((Number) item.get("act")).intValue())).toList();
    }

    @MutationMapping
    public NumberDto numberCreate(@Argument final String spexId, @Argument final Map<String, Object> input) {
        return service.createNumber(Ids.require(spexId), number(input));
    }

    @MutationMapping
    public NumberDto numberUpdate(@Argument final String id, @Argument final Map<String, Object> input) {
        return service.updateNumber(Ids.require(id), number(input));
    }

    @MutationMapping
    public boolean numberDelete(@Argument final String id) {
        return service.deleteNumber(Ids.require(id));
    }

    @MutationMapping
    public List<NumberDto> numbersPaste(@Argument final String spexId, @Argument final String text) {
        return service.pasteNumbers(Ids.require(spexId), text);
    }

    @MutationMapping
    public List<NumberDto> numbersReorder(@Argument final String spexId, @Argument final List<Map<String, Object>> items) {
        return service.reorderNumbers(Ids.require(spexId), order(items));
    }

    @SuppressWarnings("unchecked")
    static ContentService.NumberInput number(final Map<String, Object> input) {
        return new ContentService.NumberInput(((Number) input.get("act")).intValue(), (String) input.get("title"),
                Ids.optional((String) input.get("kindId")), (String) input.get("originalTitle"), (String) input.get("originalArtist"),
                (String) input.get("fromWork"), (String) input.get("firstLine"),
                input.get("credits") == null ? null : ((List<Map<String, Object>>) input.get("credits")).stream()
                        .map(credit -> new ContentService.NumberCreditInput(Ids.require((String) credit.get("kindId")),
                                Ids.optional((String) credit.get("spexareId")), (String) credit.get("name"),
                                // An unresolved credit saved again with the others stays unresolved (spec 5.3)
                                Boolean.TRUE.equals(credit.get("unresolved"))))
                        .toList(),
                input.get("characterIds") == null ? null : ((List<String>) input.get("characterIds")).stream().map(Ids::require).toList());
    }

    @QueryMapping
    public UnresolvedCreditsDto unresolvedCredits(@Argument final int limit) {
        final List<ListStore.UnresolvedCredit> credits = service.unresolvedCredits(limit);
        final Map<Long, NumberDto> numbers = service.numbersByKeys(credits.stream().map(ListStore.UnresolvedCredit::numberId).collect(Collectors.toSet()));
        final Map<Long, Kind> kinds = kindService.byKeys(credits.stream().map(ListStore.UnresolvedCredit::kindId).collect(Collectors.toSet()));

        return new UnresolvedCreditsDto(service.countUnresolvedCredits(), credits.stream()
                .filter(credit -> numbers.containsKey(credit.numberId()))
                .map(credit -> new UnresolvedCreditDto(numbers.get(credit.numberId()), kinds.get(credit.kindId()), credit.position(), credit.name()))
                .toList());
    }

    @MutationMapping
    public NumberDto creditResolve(@Argument final String numberId, @Argument final String kindId, @Argument final int position,
                                   @Argument final Map<String, String> credit) {
        return service.resolveCredit(Ids.require(numberId), Ids.require(kindId), position, credit(credit));
    }

    public static CreditRef credit(final Map<String, String> credit) {
        return new CreditRef(Ids.optional(credit.get("spexareId")), credit.get("name"));
    }

    @MutationMapping
    public SpexDto revivalSetChanges(@Argument final String revivalId, @Argument final RevivalChangeKind kind,
                                     @Argument final List<Map<String, Object>> changes) {
        service.setRevivalChanges(Ids.require(revivalId), kind, changes.stream()
                .map(change -> new RevivalChange(kind, Ids.require((String) change.get("recordId")), Boolean.TRUE.equals(change.get("cut")),
                        change.get("act") == null ? null : ((Number) change.get("act")).intValue(),
                        change.get("position") == null ? null : ((Number) change.get("position")).intValue()))
                .toList());
        return baseData.spex(revivalId).orElseThrow();
    }

    @MutationMapping
    public RecordingDto recordingCreate(@Argument final Map<String, Object> input) {
        return recordingService.create(recording(input));
    }

    @MutationMapping
    public RecordingDto recordingUpdate(@Argument final String id, @Argument final Map<String, Object> input) {
        return recordingService.update(Ids.require(id), recording(input));
    }

    @SuppressWarnings("unchecked")
    private static RecordingService.RecordingInput recording(final Map<String, Object> input) {
        return new RecordingService.RecordingInput((String) input.get("catalogueNumber"), (String) input.get("title"),
                Ids.require((String) input.get("mediumId")), Ids.optional((String) input.get("spexId")),
                (String) input.get("released"), (String) input.get("recorded"),
                input.get("conductor") == null ? null : credit((Map<String, String>) input.get("conductor")),
                (String) input.get("description"), Ids.optional((String) input.get("frontImageId")),
                Ids.optional((String) input.get("backImageId")), Ids.optional((String) input.get("spexifyAlbumId")));
    }

    @MutationMapping
    public RecordingDto recordingSetTracks(@Argument final String id, @Argument final List<Map<String, String>> tracks) {
        return recordingService.setTracks(Ids.require(id), tracks.stream()
                .map(track -> new RecordingService.TrackInput(track.get("side"), Ids.optional(track.get("numberId")), track.get("title")))
                .toList());
    }

    @MutationMapping
    public RecordingDto recordingPasteTracks(@Argument final String id, @Argument final String text) {
        return recordingService.pasteTracks(Ids.require(id), text);
    }

    @MutationMapping
    public RecordingDto recordingSetPublished(@Argument final String id, @Argument final boolean published) {
        return recordingService.setPublished(Ids.require(id), published);
    }

    @MutationMapping
    public boolean recordingDelete(@Argument final String id) {
        return recordingService.delete(Ids.require(id));
    }

    // Spex's parts -----------------------------------------------------------------------------------------------------

    @SchemaMapping(typeName = "Spex")
    public List<CharacterDto> characters(final SpexDto spex) {
        return service.characters(spex.key());
    }

    @SchemaMapping(typeName = "Spex")
    public List<SceneDto> scenes(final SpexDto spex) {
        return service.scenes(spex.key());
    }

    @SchemaMapping(typeName = "Spex")
    public List<NumberDto> numbers(final SpexDto spex) {
        return service.numbers(spex.key());
    }

    @SchemaMapping(typeName = "Spex")
    public List<RevivalChange> revivalChanges(final SpexDto spex) {
        return spex.revival() ? service.revivalChanges(spex.key()) : List.of();
    }

    @SchemaMapping(typeName = "Spex")
    public List<RecordingDto> recordings(final SpexDto spex) {
        return recordingService.recordingsOfSpex(spex.key());
    }

    private Map<Long, SpexDto> spexOf(final Collection<Long> keys) {
        return baseData.spexByKeys(Set.copyOf(keys));
    }

    // Character --------------------------------------------------------------------------------------------------------

    @BatchMapping(typeName = "Character", field = "spex")
    public Map<CharacterDto, SpexDto> characterSpex(final List<CharacterDto> characters) {
        final Map<Long, SpexDto> spex = spexOf(characters.stream().map(CharacterDto::spexKey).toList());

        return characters.stream().collect(Collectors.toMap(c -> c, c -> spex.get(c.spexKey()), (a, _) -> a));
    }

    @BatchMapping(typeName = "Character", field = "images")
    public Map<CharacterDto, List<PageImageDto>> characterImages(final List<CharacterDto> characters) {
        final Map<Long, List<PlacedImage>> placed = service.characterImages(characters.stream().map(CharacterDto::key).toList());
        final Map<Long, Image> images = imageService.byIds(placed.values().stream().flatMap(List::stream).map(PlacedImage::imageId)
                .collect(Collectors.toSet()));
        final Map<Long, Kind> kinds = kindService.byKeys(placed.values().stream().flatMap(List::stream).map(PlacedImage::kindId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));

        return characters.stream().collect(Collectors.toMap(c -> c,
                c -> pageImages(placed.getOrDefault(c.key(), List.of()), images, kinds), (a, _) -> a));
    }

    /**
     * Placed images as the viewer sees them; images in the trash are left out.
     */
    public List<PageImageDto> pageImages(final List<PlacedImage> placed, final Map<Long, Image> images, final Map<Long, Kind> kinds) {
        final List<PageImageDto> result = new ArrayList<>();

        for (final PlacedImage image : placed) {
            final ImageDto dto = imageService.visible(images.get(image.imageId()));

            if (dto != null) {
                result.add(new PageImageDto(dto, image.kindId() == null ? null : kinds.get(image.kindId()),
                        image.caption() != null ? image.caption() : dto.caption()));
            }
        }
        return result;
    }

    @SchemaMapping(typeName = "Character")
    public List<PlayedByDto> playedBy(final CharacterDto character) {
        final SpexDto spex = baseData.spexByKey(character.spexKey());
        final List<SpexDto> productions = baseDataApi.productions(spex);
        final Map<UUID, SpexDto> byId = productions.stream().collect(Collectors.toMap(SpexDto::id, s -> s));

        return peopleService.participants(productions.stream().map(SpexDto::id).toList()).orElse(List.of()).stream()
                .filter(shown -> shown.characterIds().contains(character.id()) && byId.containsKey(shown.spexId()))
                .map(shown -> new PlayedByDto(shown.person(), byId.get(shown.spexId())))
                .sorted(java.util.Comparator.comparing((PlayedByDto p) -> p.spex().year()))
                .toList();
    }

    @SchemaMapping(typeName = "Character")
    public PhotoSetDto photos(final CharacterDto character) {
        return linkService.photos(pageService.photos(character.spexKey(), "ACTOR", character.key()));
    }

    @SchemaMapping(typeName = "Character", field = "numbers")
    public List<NumberDto> characterNumbers(final CharacterDto character) {
        return service.numbersOfCharacter(character.key());
    }

    // Scene ------------------------------------------------------------------------------------------------------------

    @BatchMapping(typeName = "Scene", field = "spex")
    public Map<SceneDto, SpexDto> sceneSpex(final List<SceneDto> scenes) {
        final Map<Long, SpexDto> spex = spexOf(scenes.stream().map(SceneDto::spexKey).toList());

        return scenes.stream().collect(Collectors.toMap(s -> s, s -> spex.get(s.spexKey()), (a, _) -> a));
    }

    // Number -----------------------------------------------------------------------------------------------------------

    @BatchMapping(typeName = "Number", field = "spex")
    public Map<NumberDto, SpexDto> numberSpex(final List<NumberDto> numbers) {
        final Map<Long, SpexDto> spex = spexOf(numbers.stream().map(NumberDto::spexKey).toList());

        return numbers.stream().collect(Collectors.toMap(n -> n, n -> spex.get(n.spexKey()), (a, _) -> a));
    }

    @BatchMapping(typeName = "Number", field = "kind")
    public Map<NumberDto, @Nullable Kind> numberKind(final List<NumberDto> numbers) {
        final Map<Long, Kind> kinds = kindService.byKeys(numbers.stream().map(NumberDto::kindKey).filter(Objects::nonNull).collect(Collectors.toSet()));
        final Map<NumberDto, @Nullable Kind> result = new HashMap<>();

        numbers.forEach(n -> result.put(n, n.kindKey() == null ? null : kinds.get(n.kindKey())));
        return result;
    }

    /**
     * A number's credits, by credit kind in the kinds' order; unresolved ones only for EDITOR (spec 5.3).
     */
    @BatchMapping(typeName = "Number", field = "credits")
    public Map<NumberDto, List<NumberCreditDto>> credits(final List<NumberDto> numbers) {
        final List<Credit> credits = service.credits(numbers.stream().map(NumberDto::key).toList());
        final Map<UUID, PersonDto> people = persons(credits.stream().filter(credit -> !credit.unresolved()).map(Credit::ref).toList());
        final Map<Long, Kind> kinds = kindService.byKeys(credits.stream().map(Credit::kindId).collect(Collectors.toSet()));

        return numbers.stream().collect(Collectors.toMap(n -> n, n -> {
            final List<NumberCreditDto> result = new ArrayList<>();
            final Map<Long, Integer> positions = new HashMap<>();

            for (final Credit credit : credits) {
                if (credit.numberId() != n.key()) {
                    continue;
                }

                final int position = positions.merge(credit.kindId(), 1, Integer::sum) - 1;
                final Optional<CreditDto> shown = credit.unresolved()
                        ? Optional.of(new CreditDto(null, credit.ref().name(), true))
                        : toCredit(credit.ref(), people);

                shown.ifPresent(dto -> result.add(new NumberCreditDto(kinds.get(credit.kindId()), position, dto)));
            }
            return result;
        }, (a, _) -> a));
    }

    /**
     * The spexare among the credits, as the viewer may see them.
     */
    public Map<UUID, PersonDto> persons(final List<CreditRef> refs) {
        return peopleService.spexare(refs.stream().map(CreditRef::spexareId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .orElse(Map.of());
    }

    /**
     * A credit as the viewer sees it: nothing for a spexare they may not see.
     */
    public static Optional<CreditDto> toCredit(final @Nullable CreditRef ref, final Map<UUID, PersonDto> people) {
        if (ref == null) {
            return Optional.empty();
        }
        if (ref.spexareId() == null) {
            return Optional.of(new CreditDto(null, ref.name(), false));
        }
        return Optional.ofNullable(people.get(ref.spexareId())).map(person -> new CreditDto(person, null, false));
    }

    @BatchMapping(typeName = "Number", field = "characters")
    public Map<NumberDto, List<CharacterDto>> numberCharacters(final List<NumberDto> numbers) {
        final Map<Long, List<Long>> keys = service.numberCharacters(numbers.stream().map(NumberDto::key).toList());
        final Map<Long, CharacterDto> characters = service.charactersByKeys(keys.values().stream().flatMap(List::stream)
                .collect(Collectors.toSet()));

        return numbers.stream().collect(Collectors.toMap(n -> n, n -> keys.getOrDefault(n.key(), List.of()).stream()
                .map(characters::get).filter(Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(CharacterDto::position)).toList(), (a, _) -> a));
    }

    @BatchMapping(typeName = "Number", field = "links")
    public Map<NumberDto, List<AppLinkDto>> numberLinks(final List<NumberDto> numbers) {
        final List<UUID> ids = numbers.stream().map(NumberDto::id).sorted().toList();

        return numbers.stream().collect(Collectors.toMap(n -> n, n -> linkService.numberLinks(n.id(), ids), (a, _) -> a));
    }

    // Recording --------------------------------------------------------------------------------------------------------

    @SchemaMapping(typeName = "Recording", field = "spex")
    public @Nullable SpexDto recordingSpex(final RecordingDto recording) {
        return recording.spexKey() == null ? null : baseData.spexByKey(recording.spexKey());
    }

    @SchemaMapping(typeName = "Recording")
    public @Nullable CreditDto conductor(final RecordingDto recording) {
        return recording.conductor() == null ? null
                : toCredit(recording.conductor(), persons(List.of(recording.conductor()))).orElse(null);
    }

    @SchemaMapping(typeName = "Recording", field = "medium")
    public @Nullable Kind recordingMedium(final RecordingDto recording) {
        return kindService.byKeys(List.of(recording.mediumKey())).get(recording.mediumKey());
    }

    @SchemaMapping(typeName = "Recording")
    public List<TrackDto> tracks(final RecordingDto recording) {
        return recordingService.tracks(recording.key());
    }

    @SchemaMapping(typeName = "Recording")
    public @Nullable ImageDto front(final RecordingDto recording) {
        return recording.frontKey() == null ? null : imageService.visible(imageService.byIds(List.of(recording.frontKey())).get(recording.frontKey()));
    }

    @SchemaMapping(typeName = "Recording")
    public @Nullable ImageDto back(final RecordingDto recording) {
        return recording.backKey() == null ? null : imageService.visible(imageService.byIds(List.of(recording.backKey())).get(recording.backKey()));
    }

    @SchemaMapping(typeName = "Recording", field = "links")
    public List<AppLinkDto> recordingLinks(final RecordingDto recording) {
        return linkService.recordingLinks(recording.spexifyAlbumId(), recording.title());
    }

    @BatchMapping(typeName = "Track", field = "number")
    public Map<TrackDto, @Nullable NumberDto> trackNumber(final List<TrackDto> tracks) {
        final Map<Long, NumberDto> numbers = service.numbersByKeys(tracks.stream().map(TrackDto::numberKey).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        final Map<TrackDto, @Nullable NumberDto> result = new HashMap<>();

        tracks.forEach(t -> result.put(t, t.numberKey() == null ? null : numbers.get(t.numberKey())));
        return result;
    }

    /**
     * A credit as the viewer sees it. An unresolved one (only editors see those) carries the imported name.
     */
    public record CreditDto(@Nullable PersonDto person, @Nullable String name, boolean unresolved) {
    }

    public record NumberCreditDto(@Nullable Kind kind, int position, CreditDto credit) {
    }

    public record UnresolvedCreditDto(NumberDto number, @Nullable Kind kind, int position, String name) {
    }

    public record UnresolvedCreditsDto(long total, List<UnresolvedCreditDto> credits) {
    }

    public record PageImageDto(ImageDto image, @Nullable Kind kind, @Nullable String caption) {
    }

    public record PlayedByDto(PersonDto person, SpexDto spex) {
    }
}
