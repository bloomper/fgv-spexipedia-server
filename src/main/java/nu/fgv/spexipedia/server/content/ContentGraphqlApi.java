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
import nu.fgv.spexipedia.server.content.ContentDtos.FigureDto;
import nu.fgv.spexipedia.server.content.ContentDtos.NumberDto;
import nu.fgv.spexipedia.server.content.ContentDtos.RecordingDto;
import nu.fgv.spexipedia.server.content.ContentDtos.SceneDto;
import nu.fgv.spexipedia.server.content.ContentDtos.TrackDto;
import nu.fgv.spexipedia.server.content.ListStore.CreditKind;
import nu.fgv.spexipedia.server.content.ListStore.CreditRef;
import nu.fgv.spexipedia.server.content.ListStore.PlacedImage;
import nu.fgv.spexipedia.server.content.ListStore.RevivalChange;
import nu.fgv.spexipedia.server.content.ListStore.RevivalChangeKind;
import nu.fgv.spexipedia.server.image.Image;
import nu.fgv.spexipedia.server.image.ImageDto;
import nu.fgv.spexipedia.server.image.ImageKind;
import nu.fgv.spexipedia.server.image.ImageService;
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
 * GraphQL for figures, characters, scenes, numbers, recordings and revivals' changes.
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

    @SuppressWarnings("java:S107")
    public ContentGraphqlApi(final ContentService service, final RecordingService recordingService, final BaseDataService baseData,
                             final BaseDataGraphqlApi baseDataApi, final SpexPageService pageService, final ImageService imageService,
                             final PeopleService peopleService, final LinkService linkService) {
        this.service = service;
        this.recordingService = recordingService;
        this.baseData = baseData;
        this.baseDataApi = baseDataApi;
        this.pageService = pageService;
        this.imageService = imageService;
        this.peopleService = peopleService;
        this.linkService = linkService;
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
    public List<FigureDto> figures() {
        return service.figures();
    }

    @QueryMapping
    public @Nullable FigureDto figure(@Argument final String id) {
        return service.figure(id).orElse(null);
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
    public FigureDto figureCreate(@Argument final Map<String, String> input) {
        return service.createFigure(input.get("name"), input.get("description"));
    }

    @MutationMapping
    public FigureDto figureUpdate(@Argument final String id, @Argument final Map<String, String> input) {
        return service.updateFigure(Ids.require(id), input.get("name"), input.get("description"));
    }

    @MutationMapping
    public boolean figureDelete(@Argument final String id) {
        return service.deleteFigure(Ids.require(id));
    }

    @MutationMapping
    public CharacterDto characterCreate(@Argument final String spexId, @Argument final Map<String, String> input) {
        return service.createCharacter(Ids.require(spexId), input.get("name"), Ids.optional(input.get("figureId")), input.get("description"));
    }

    @MutationMapping
    public CharacterDto characterUpdate(@Argument final String id, @Argument final Map<String, String> input) {
        return service.updateCharacter(Ids.require(id), input.get("name"), Ids.optional(input.get("figureId")), input.get("description"));
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
                image.get("kind") == null ? null : ImageKind.valueOf(image.get("kind")), image.get("caption"))).toList();
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
    private static ContentService.NumberInput number(final Map<String, Object> input) {
        return new ContentService.NumberInput(((Number) input.get("act")).intValue(), (String) input.get("title"),
                (String) input.get("originalTitle"), (String) input.get("originalArtist"),
                credits((List<Map<String, String>>) input.get("lyrics")), credits((List<Map<String, String>>) input.get("composers")),
                credits((List<Map<String, String>>) input.get("arrangers")),
                credits((List<Map<String, String>>) input.get("choreographers")),
                input.get("characterIds") == null ? null : ((List<String>) input.get("characterIds")).stream().map(Ids::require).toList());
    }

    private static @Nullable List<CreditRef> credits(final @Nullable List<Map<String, String>> credits) {
        return credits == null ? null : credits.stream().map(ContentGraphqlApi::credit).toList();
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
                Medium.valueOf((String) input.get("medium")), Ids.optional((String) input.get("spexId")),
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

    // Figure -----------------------------------------------------------------------------------------------------------

    @SchemaMapping(typeName = "Figure", field = "characters")
    public List<CharacterDto> figureCharacters(final FigureDto figure) {
        final Set<Long> published = pageService.publishedSpexKeys();
        final boolean author = pageService.isAuthor();

        return service.charactersOfFigure(figure.key()).stream()
                .filter(character -> author || published.contains(character.spexKey()))
                .toList();
    }

    // Character --------------------------------------------------------------------------------------------------------

    @BatchMapping(typeName = "Character", field = "spex")
    public Map<CharacterDto, SpexDto> characterSpex(final List<CharacterDto> characters) {
        final Map<Long, SpexDto> spex = spexOf(characters.stream().map(CharacterDto::spexKey).toList());

        return characters.stream().collect(Collectors.toMap(c -> c, c -> spex.get(c.spexKey()), (a, _) -> a));
    }

    @BatchMapping(typeName = "Character", field = "figure")
    public Map<CharacterDto, @Nullable FigureDto> characterFigure(final List<CharacterDto> characters) {
        final Map<Long, FigureDto> figures = service.figuresByKeys(characters.stream().map(CharacterDto::figureKey)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        final Map<CharacterDto, @Nullable FigureDto> result = new HashMap<>();

        characters.forEach(c -> result.put(c, c.figureKey() == null ? null : figures.get(c.figureKey())));
        return result;
    }

    @BatchMapping(typeName = "Character", field = "images")
    public Map<CharacterDto, List<PageImageDto>> characterImages(final List<CharacterDto> characters) {
        final Map<Long, List<PlacedImage>> placed = service.characterImages(characters.stream().map(CharacterDto::key).toList());
        final Map<Long, Image> images = imageService.byIds(placed.values().stream().flatMap(List::stream).map(PlacedImage::imageId)
                .collect(Collectors.toSet()));

        return characters.stream().collect(Collectors.toMap(c -> c,
                c -> pageImages(placed.getOrDefault(c.key(), List.of()), images), (a, _) -> a));
    }

    /**
     * Placed images as the viewer sees them; images in the trash are left out.
     */
    public List<PageImageDto> pageImages(final List<PlacedImage> placed, final Map<Long, Image> images) {
        final List<PageImageDto> result = new ArrayList<>();

        for (final PlacedImage image : placed) {
            final ImageDto dto = imageService.visible(images.get(image.imageId()));

            if (dto != null) {
                result.add(new PageImageDto(dto, image.kind(), image.caption() != null ? image.caption() : dto.caption()));
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

    @BatchMapping(typeName = "Number")
    public Map<NumberDto, List<CreditDto>> lyrics(final List<NumberDto> numbers) {
        return credits(numbers, CreditKind.LYRICS);
    }

    @BatchMapping(typeName = "Number")
    public Map<NumberDto, List<CreditDto>> composers(final List<NumberDto> numbers) {
        return credits(numbers, CreditKind.COMPOSER);
    }

    @BatchMapping(typeName = "Number")
    public Map<NumberDto, List<CreditDto>> arrangers(final List<NumberDto> numbers) {
        return credits(numbers, CreditKind.ARRANGER);
    }

    @BatchMapping(typeName = "Number")
    public Map<NumberDto, List<CreditDto>> choreographers(final List<NumberDto> numbers) {
        return credits(numbers, CreditKind.CHOREOGRAPHER);
    }

    private Map<NumberDto, List<CreditDto>> credits(final List<NumberDto> numbers, final CreditKind kind) {
        final List<ListStore.Credit> credits = service.credits(numbers.stream().map(NumberDto::key).toList()).stream()
                .filter(credit -> credit.kind() == kind).toList();
        final Map<UUID, PersonDto> people = persons(credits.stream().map(credit -> credit.ref()).toList());

        return numbers.stream().collect(Collectors.toMap(n -> n, n -> credits.stream()
                .filter(credit -> credit.numberId() == n.key())
                .map(credit -> toCredit(credit.ref(), people))
                .flatMap(Optional::stream)
                .toList(), (a, _) -> a));
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
            return Optional.of(new CreditDto(null, ref.name()));
        }
        return Optional.ofNullable(people.get(ref.spexareId())).map(person -> new CreditDto(person, null));
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

    public record CreditDto(@Nullable PersonDto person, @Nullable String name) {
    }

    public record PageImageDto(ImageDto image, @Nullable ImageKind kind, @Nullable String caption) {
    }

    public record PlayedByDto(PersonDto person, SpexDto spex) {
    }
}
