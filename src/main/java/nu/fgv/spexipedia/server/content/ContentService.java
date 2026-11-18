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

import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.basedata.BaseDataKind;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.basedata.IdAlias;
import nu.fgv.spexipedia.server.basedata.IdAliasRepository;
import nu.fgv.spexipedia.server.basedata.Spex;
import nu.fgv.spexipedia.server.content.ContentDtos.CharacterDto;
import nu.fgv.spexipedia.server.content.ContentDtos.FigureDto;
import nu.fgv.spexipedia.server.content.ContentDtos.NumberDto;
import nu.fgv.spexipedia.server.content.ContentDtos.SceneDto;
import nu.fgv.spexipedia.server.content.ContentRepositories.CharacterRepository;
import nu.fgv.spexipedia.server.content.ContentRepositories.FigureRepository;
import nu.fgv.spexipedia.server.content.ContentRepositories.NumberRepository;
import nu.fgv.spexipedia.server.content.ContentRepositories.SceneRepository;
import nu.fgv.spexipedia.server.content.ContentRepositories.TrackRepository;
import nu.fgv.spexipedia.server.content.ListStore.Credit;
import nu.fgv.spexipedia.server.content.ListStore.CreditRef;
import nu.fgv.spexipedia.server.content.ListStore.PlacedImage;
import nu.fgv.spexipedia.server.content.ListStore.RevivalChange;
import nu.fgv.spexipedia.server.content.ListStore.RevivalChangeKind;
import nu.fgv.spexipedia.server.feed.ChangeFeedService;
import nu.fgv.spexipedia.server.feed.ChangeType;
import nu.fgv.spexipedia.server.feed.FeedRecords;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.kind.Kind;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.kind.KindType;
import nu.fgv.spexipedia.server.kind.KindUse;
import nu.fgv.spexipedia.server.search.IndexEvent;
import nu.fgv.spexipedia.server.search.SearchKind;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Ids;
import nu.fgv.spexipedia.server.util.Slugs;
import nu.fgv.spexipedia.server.util.Texts;
import nu.fgv.spexipedia.server.util.Uuids;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Figures, characters, scenes and numbers, and what a revival changes of its parent's. EDITOR changes them; AUTHOR
 * changes characters' images. Characters and numbers are base data: their changes go to the change feed.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class ContentService {

    static final int MAX_BY_IDS = 500;
    private static final Pattern ACT = Pattern.compile("^(?:akt|act|akt nr\\.?)\\s*(\\d+)\\s*:?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DASH = Pattern.compile("\\s+[–—-]\\s+|\\t");
    private static final Pattern ORIGINAL = Pattern.compile("^(.*?)\\s*\\((.+)\\)\\s*$");

    private final FigureRepository figureRepository;
    private final CharacterRepository characterRepository;
    private final SceneRepository sceneRepository;
    private final NumberRepository numberRepository;
    private final TrackRepository trackRepository;
    private final IdAliasRepository aliasRepository;
    private final ListStore lists;
    private final BaseDataService baseData;
    private final ImageService imageService;
    private final KindService kindService;
    private final ChangeFeedService feed;
    private final CallerService callerService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    @SuppressWarnings("java:S107")
    public ContentService(final FigureRepository figureRepository, final CharacterRepository characterRepository,
                          final SceneRepository sceneRepository, final NumberRepository numberRepository,
                          final TrackRepository trackRepository, final IdAliasRepository aliasRepository,
                          final ListStore lists, final BaseDataService baseData, final ImageService imageService,
                          final KindService kindService, final ChangeFeedService feed, final CallerService callerService, final AuditService auditService,
                          final ApplicationEventPublisher events) {
        this.figureRepository = figureRepository;
        this.characterRepository = characterRepository;
        this.sceneRepository = sceneRepository;
        this.numberRepository = numberRepository;
        this.trackRepository = trackRepository;
        this.aliasRepository = aliasRepository;
        this.lists = lists;
        this.baseData = baseData;
        this.imageService = imageService;
        this.kindService = kindService;
        this.feed = feed;
        this.callerService = callerService;
        this.auditService = auditService;
        this.events = events;
    }

    private UUID resolve(final BaseDataKind kind, final UUID id) {
        return aliasRepository.findByAliasAndKind(id, kind).map(IdAlias::getTarget).orElse(id);
    }

    private boolean editor() {
        return callerService.currentIfAny().map(caller -> caller.isEditor()).orElse(false);
    }

    // Figures ----------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<FigureDto> figures() {
        return figureRepository.findAllByOrderByNameAsc().stream().map(ContentDtos::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<FigureDto> figure(final String idOrSlug) {
        return Ids.parse(idOrSlug).flatMap(figureRepository::findByUuid).or(() -> figureRepository.findBySlug(idOrSlug))
                .map(ContentDtos::toDto);
    }

    @Transactional(readOnly = true)
    public Map<Long, FigureDto> figuresByKeys(final Collection<Long> keys) {
        return figureRepository.findAllById(keys).stream().map(ContentDtos::toDto).collect(Collectors.toMap(FigureDto::key, Function.identity()));
    }

    @Transactional
    public FigureDto createFigure(final String name, final @Nullable String description) {
        callerService.current().requireEditor();

        final Figure figure = new Figure();

        figure.setUuid(Uuids.v7());
        figure.setName(Texts.required(name, 255, "name"));
        figure.setDescription(Texts.optional(description, 50_000, "description"));
        figure.setSlug(Slugs.unique(figure.getName(), "figur", figureRepository::existsBySlug));
        figureRepository.save(figure);
        events.publishEvent(new IndexEvent(SearchKind.FIGURE, figure.getUuid()));
        auditService.record("CREATE", "FIGURE", figure.getUuid(), "Skapade gestalten " + figure.getName());
        return ContentDtos.toDto(figure);
    }

    @Transactional
    public FigureDto updateFigure(final UUID id, final String name, final @Nullable String description) {
        callerService.current().requireEditor();

        final Figure figure = requireFigure(id);

        figure.setName(Texts.required(name, 255, "name"));
        figure.setDescription(Texts.optional(description, 50_000, "description"));
        events.publishEvent(new IndexEvent(SearchKind.FIGURE, figure.getUuid()));
        auditService.record("UPDATE", "FIGURE", id, "Ändrade gestalten " + figure.getName());
        return ContentDtos.toDto(figure);
    }

    @Transactional
    public boolean deleteFigure(final UUID id) {
        callerService.current().requireEditor();

        final Figure figure = requireFigure(id);

        if (characterRepository.countByFigure(figure) > 0) {
            throw new BadRequestException("error.figure.inUse");
        }
        figureRepository.delete(figure);
        events.publishEvent(new IndexEvent(SearchKind.FIGURE, id));
        auditService.record("DELETE", "FIGURE", id, "Tog bort gestalten " + figure.getName());
        return true;
    }

    private Figure requireFigure(final UUID id) {
        return figureRepository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Figure.class, id));
    }

    @Transactional(readOnly = true)
    public List<CharacterDto> charactersOfFigure(final long figureKey) {
        return figureRepository.findById(figureKey).map(figure -> characterRepository.findByFigure(figure).stream()
                .filter(character -> !character.isRetired())
                .sorted(Comparator.comparing((SpexCharacter character) -> character.getSpex().getYear()))
                .map(ContentDtos::toDto)
                .toList()).orElse(List.of());
    }

    // Characters -------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<CharacterDto> character(final String id) {
        return Ids.parse(id).flatMap(uuid -> characterRepository.findByUuid(resolve(BaseDataKind.CHARACTER, uuid))).map(ContentDtos::toDto);
    }

    @Transactional(readOnly = true)
    public List<CharacterDto> charactersByIds(final List<String> ids) {
        if (ids.size() > MAX_BY_IDS) {
            throw new BadRequestException("error.tooMany", MAX_BY_IDS);
        }

        final Map<UUID, UUID> resolved = new LinkedHashMap<>();

        ids.forEach(id -> Ids.parse(id).ifPresent(uuid -> resolved.put(uuid, resolve(BaseDataKind.CHARACTER, uuid))));

        final Map<UUID, CharacterDto> found = characterRepository.findByUuidIn(resolved.values()).stream().map(ContentDtos::toDto)
                .collect(Collectors.toMap(CharacterDto::id, Function.identity()));

        return resolved.values().stream().map(found::get).filter(Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, CharacterDto> charactersByKeys(final Collection<Long> keys) {
        return characterRepository.findAllById(keys).stream().map(ContentDtos::toDto)
                .collect(Collectors.toMap(CharacterDto::key, Function.identity()));
    }

    /**
     * The characters on a spex's page: an original's own, a revival's parent's after its changes and its own. Retired
     * ones only for EDITOR.
     */
    @Transactional(readOnly = true)
    public List<CharacterDto> characters(final long spexKey) {
        final Spex spex = baseData.requireSpexByKey(spexKey);
        final boolean editor = editor();
        final List<SpexCharacter> own = characterRepository.findBySpexOrderByPositionAsc(spex);

        if (!spex.isRevival()) {
            return own.stream().filter(c -> editor || !c.isRetired()).map(ContentDtos::toDto).toList();
        }

        final Map<UUID, RevivalChange> changes = changes(spex, RevivalChangeKind.CHARACTER);
        final List<CharacterDto> result = new ArrayList<>();

        for (final SpexCharacter character : characterRepository.findBySpexOrderByPositionAsc(spex.getParent())) {
            final RevivalChange change = changes.get(character.getUuid());

            if ((editor || !character.isRetired()) && (change == null || !change.cut())) {
                final CharacterDto dto = ContentDtos.toDto(character);

                result.add(change == null || change.position() == null ? dto : new CharacterDto(dto.key(), dto.id(), dto.spexKey(),
                        dto.name(), dto.figureKey(), dto.description(), change.position(), dto.retired()));
            }
        }
        own.stream().filter(c -> editor || !c.isRetired()).map(ContentDtos::toDto).forEach(result::add);
        result.sort(Comparator.comparingInt(CharacterDto::position));
        return result;
    }

    @Transactional
    public CharacterDto createCharacter(final UUID spexId, final String name, final @Nullable UUID figureId,
                                        final @Nullable String description) {
        return createCharacter(spexId, name, figureId, description, null);
    }

    /**
     * EDITOR. A character with a given id, as the sample data and the migration make them.
     */
    @Transactional
    public CharacterDto createCharacter(final UUID spexId, final String name, final @Nullable UUID figureId,
                                        final @Nullable String description, final @Nullable UUID id) {
        callerService.current().requireEditor();

        final Spex spex = baseData.requireSpex(spexId);
        final SpexCharacter character = new SpexCharacter();

        character.setUuid(id == null ? Uuids.v7() : id);
        character.setSpex(spex);
        character.setPosition(nextCharacterPosition(spex));
        applyCharacter(character, name, figureId, description);
        characterRepository.save(character);
        changed(character);
        auditService.record("CREATE", "CHARACTER", character.getUuid(), "Skapade karaktären " + character.getName() + " i " + spex.label());
        return ContentDtos.toDto(character);
    }

    private int nextCharacterPosition(final Spex spex) {
        final int own = characterRepository.maxPosition(spex);

        return 1 + (spex.isRevival() ? Math.max(own, characterRepository.maxPosition(spex.getParent())) : own);
    }

    @Transactional
    public CharacterDto updateCharacter(final UUID id, final String name, final @Nullable UUID figureId,
                                        final @Nullable String description) {
        callerService.current().requireEditor();

        final SpexCharacter character = requireCharacter(id);

        applyCharacter(character, name, figureId, description);
        changed(character);
        auditService.record("UPDATE", "CHARACTER", character.getUuid(), "Ändrade karaktären " + character.getName() + " i " + character.getSpex().label());
        return ContentDtos.toDto(character);
    }

    private void applyCharacter(final SpexCharacter character, final String name, final @Nullable UUID figureId,
                                final @Nullable String description) {
        character.setName(Texts.required(name, 255, "name"));
        character.setFigure(figureId == null ? null : requireFigure(figureId));
        character.setDescription(Texts.optional(description, 50_000, "description"));
    }

    /**
     * Deletes a character nothing on Spexipedia refers to. Other applications may have copied it, so the feed tells them
     * it is retired; they keep their copy.
     */
    @Transactional
    public boolean deleteCharacter(final UUID id) {
        callerService.current().requireEditor();

        final SpexCharacter character = requireCharacter(id);

        character.setRetired(true);
        feed.record(BaseDataKind.CHARACTER, character.getUuid(), ChangeType.RETIRE, null, feedRecord(character));
        characterRepository.delete(character);
        events.publishEvent(new IndexEvent(SearchKind.CHARACTER, character.getUuid()));
        auditService.record("DELETE", "CHARACTER", id, "Tog bort karaktären " + character.getName() + " i " + character.getSpex().label());
        return true;
    }

    /**
     * Characters as text, one per line: the name, optionally followed by a dash or a tab and a description.
     */
    @Transactional
    public List<CharacterDto> pasteCharacters(final UUID spexId, final String text) {
        callerService.current().requireEditor();

        final Spex spex = baseData.requireSpex(spexId);
        int position = nextCharacterPosition(spex);

        for (final String line : lines(text)) {
            final String[] parts = DASH.split(line, 2);
            final SpexCharacter character = new SpexCharacter();

            character.setUuid(Uuids.v7());
            character.setSpex(spex);
            character.setPosition(position++);
            applyCharacter(character, parts[0], null, parts.length > 1 ? parts[1] : null);
            characterRepository.save(character);
            changed(character);
        }
        auditService.record("CREATE", "CHARACTER", spex.getUuid(), "Klistrade in karaktärer i " + spex.label());
        return characters(spex.getId());
    }

    @Transactional
    public List<CharacterDto> reorderCharacters(final UUID spexId, final List<UUID> ids) {
        callerService.current().requireEditor();

        final Spex spex = baseData.requireSpex(spexId);
        final Map<UUID, SpexCharacter> own = characterRepository.findBySpexOrderByPositionAsc(spex).stream()
                .collect(Collectors.toMap(SpexCharacter::getUuid, Function.identity()));

        if (!own.keySet().equals(Set.copyOf(ids)) || ids.size() != own.size()) {
            throw new BadRequestException("error.order.incomplete");
        }
        for (int i = 0; i < ids.size(); i++) {
            final SpexCharacter character = own.get(ids.get(i));

            if (character.getPosition() != i + 1) {
                character.setPosition(i + 1);
                changed(character);
            }
        }
        auditService.record("UPDATE", "CHARACTER", spex.getUuid(), "Ändrade ordningen på karaktärerna i " + spex.label());
        return characters(spex.getId());
    }

    /**
     * AUTHOR. A character's images, in order.
     */
    @Transactional
    public CharacterDto setCharacterImages(final UUID id, final List<ImageRef> images) {
        callerService.current().requireAuthor();

        final SpexCharacter character = requireCharacter(id);
        final List<PlacedImage> placed = new ArrayList<>();

        for (final ImageRef image : images) {
            final Kind kind = image.kindId() == null
                    ? kindService.first(KindType.IMAGE, KindUse.CHARACTER).orElseThrow(() -> new BadRequestException("error.field.invalid", "kindId"))
                    : kindService.require(image.kindId(), KindType.IMAGE, KindUse.CHARACTER, null);

            placed.add(new PlacedImage(imageService.requireUsable(image.imageId()).getId(), kind.getId(),
                    Texts.optional(image.caption(), 1000, "caption")));
        }
        lists.setCharacterImages(character.getId(), placed);
        auditService.record("UPDATE", "CHARACTER", id, "Ändrade bilderna på karaktären " + character.getName() + " i " + character.getSpex().label());
        return ContentDtos.toDto(character);
    }

    public Map<Long, List<PlacedImage>> characterImages(final Collection<Long> characterKeys) {
        return lists.characterImages(characterKeys);
    }

    @Transactional(readOnly = true)
    public List<NumberDto> numbersOfCharacter(final long characterKey) {
        return numberRepository.findAllById(lists.numbersOfCharacter(characterKey)).stream()
                .sorted(Comparator.comparingInt(SpexNumber::getAct).thenComparingInt(SpexNumber::getPosition))
                .map(ContentDtos::toDto).toList();
    }

    SpexCharacter requireCharacter(final UUID id) {
        return characterRepository.findByUuid(resolve(BaseDataKind.CHARACTER, id)).orElseThrow(() -> new ResourceNotFoundException(SpexCharacter.class, id));
    }

    void changed(final SpexCharacter character) {
        feed.upsert(BaseDataKind.CHARACTER, character.getUuid(), feedRecord(character));
        events.publishEvent(new IndexEvent(SearchKind.CHARACTER, character.getUuid()));
    }

    FeedRecords.FeedCharacter feedRecord(final SpexCharacter character) {
        return new FeedRecords.FeedCharacter(character.getUuid(), character.getSpex().getUuid(), character.getName(),
                character.getPosition(), character.isRetired());
    }

    // Scenes -----------------------------------------------------------------------------------------------------------

    /**
     * The scenes on a spex's page: an original's own, a revival's parent's after its changes and its own.
     */
    @Transactional(readOnly = true)
    public List<SceneDto> scenes(final long spexKey) {
        final Spex spex = baseData.requireSpexByKey(spexKey);
        final List<SceneDto> own = sceneRepository.findBySpexOrderByActAscPositionAsc(spex).stream().map(ContentDtos::toDto).toList();

        if (!spex.isRevival()) {
            return own;
        }

        final Map<UUID, RevivalChange> changes = changes(spex, RevivalChangeKind.SCENE);
        final List<SceneDto> result = new ArrayList<>();

        for (final Scene scene : sceneRepository.findBySpexOrderByActAscPositionAsc(spex.getParent())) {
            final RevivalChange change = changes.get(scene.getUuid());

            if (change == null || !change.cut()) {
                final SceneDto dto = ContentDtos.toDto(scene);

                result.add(change == null || change.position() == null ? dto : new SceneDto(dto.key(), dto.id(), dto.spexKey(),
                        change.act() == null ? dto.act() : change.act(), change.position(), dto.title(), dto.place(), dto.intervalAfter()));
            }
        }
        result.addAll(own);
        result.sort(Comparator.comparingInt(SceneDto::act).thenComparingInt(SceneDto::position));
        return result;
    }

    @Transactional
    public SceneDto createScene(final UUID spexId, final SceneInput input) {
        callerService.current().requireEditor();

        final Spex spex = baseData.requireSpex(spexId);
        final Scene scene = new Scene();

        scene.setUuid(Uuids.v7());
        scene.setSpex(spex);
        scene.setPosition(sceneRepository.maxPosition(spex) + 1);
        applyScene(scene, input);
        sceneRepository.save(scene);
        auditService.record("CREATE", "SCENE", scene.getUuid(), "Skapade scenen " + scene.getTitle() + " i " + spex.label());
        return ContentDtos.toDto(scene);
    }

    @Transactional
    public SceneDto updateScene(final UUID id, final SceneInput input) {
        callerService.current().requireEditor();

        final Scene scene = requireScene(id);

        applyScene(scene, input);
        auditService.record("UPDATE", "SCENE", id, "Ändrade scenen " + scene.getTitle() + " i " + scene.getSpex().label());
        return ContentDtos.toDto(scene);
    }

    @Transactional
    public boolean deleteScene(final UUID id) {
        callerService.current().requireEditor();

        final Scene scene = requireScene(id);

        sceneRepository.delete(scene);
        auditService.record("DELETE", "SCENE", id, "Tog bort scenen " + scene.getTitle() + " i " + scene.getSpex().label());
        return true;
    }

    private static void applyScene(final Scene scene, final SceneInput input) {
        if (input.act() < 1 || input.act() > 9) {
            throw new BadRequestException("error.field.invalid", "act");
        }
        scene.setAct(input.act());
        scene.setTitle(Texts.required(input.title(), 255, "title"));
        scene.setPlace(Texts.optional(input.place(), 255, "place"));
        scene.setIntervalAfter(input.intervalAfter());
    }

    /**
     * Scenes as text, one per line: "Title" or "Title – Place"; a line "Akt 2" starts an act and a line "--" marks an
     * interval after the scene before.
     */
    @Transactional
    public List<SceneDto> pasteScenes(final UUID spexId, final String text) {
        callerService.current().requireEditor();

        final Spex spex = baseData.requireSpex(spexId);
        int act = 1;
        int position = sceneRepository.maxPosition(spex) + 1;
        Scene previous = null;

        for (final String line : lines(text)) {
            final Matcher actLine = ACT.matcher(line);

            if (actLine.matches()) {
                act = Integer.parseInt(actLine.group(1));
                continue;
            }
            if (line.matches("-{2,}|(?i)paus|(?i)interval")) {
                if (previous != null) {
                    previous.setIntervalAfter(true);
                }
                continue;
            }

            final String[] parts = DASH.split(line, 2);
            final Scene scene = new Scene();

            scene.setUuid(Uuids.v7());
            scene.setSpex(spex);
            scene.setPosition(position++);
            applyScene(scene, new SceneInput(Math.clamp(act, 1, 9), parts[0], parts.length > 1 ? parts[1] : null, false));
            previous = sceneRepository.save(scene);
        }
        auditService.record("CREATE", "SCENE", spex.getUuid(), "Klistrade in scener i " + spex.label());
        return scenes(spex.getId());
    }

    @Transactional
    public List<SceneDto> reorderScenes(final UUID spexId, final List<Order> order) {
        callerService.current().requireEditor();

        final Spex spex = baseData.requireSpex(spexId);
        final Map<UUID, Scene> own = sceneRepository.findBySpexOrderByActAscPositionAsc(spex).stream()
                .collect(Collectors.toMap(Scene::getUuid, Function.identity()));

        requireComplete(own.keySet(), order);
        for (int i = 0; i < order.size(); i++) {
            final Scene scene = own.get(order.get(i).id());

            scene.setAct(Math.clamp(order.get(i).act(), 1, 9));
            scene.setPosition(i + 1);
        }
        auditService.record("UPDATE", "SCENE", spex.getUuid(), "Ändrade ordningen på scenerna i " + spex.label());
        return scenes(spex.getId());
    }

    private Scene requireScene(final UUID id) {
        return sceneRepository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Scene.class, id));
    }

    // Numbers ----------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<NumberDto> number(final String id) {
        return Ids.parse(id).flatMap(uuid -> numberRepository.findByUuid(resolve(BaseDataKind.NUMBER, uuid))).map(ContentDtos::toDto);
    }

    @Transactional(readOnly = true)
    public List<NumberDto> numbersByIds(final List<String> ids) {
        if (ids.size() > MAX_BY_IDS) {
            throw new BadRequestException("error.tooMany", MAX_BY_IDS);
        }

        final Map<UUID, UUID> resolved = new LinkedHashMap<>();

        ids.forEach(id -> Ids.parse(id).ifPresent(uuid -> resolved.put(uuid, resolve(BaseDataKind.NUMBER, uuid))));

        final Map<UUID, NumberDto> found = numberRepository.findByUuidIn(resolved.values()).stream().map(ContentDtos::toDto)
                .collect(Collectors.toMap(NumberDto::id, Function.identity()));

        return resolved.values().stream().map(found::get).filter(Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, NumberDto> numbersByKeys(final Collection<Long> keys) {
        return numberRepository.findAllById(keys).stream().map(ContentDtos::toDto).collect(Collectors.toMap(NumberDto::key, Function.identity()));
    }

    /**
     * The numbers on a spex's page: an original's own, a revival's parent's after its changes and its own. Retired ones
     * only for EDITOR.
     */
    @Transactional(readOnly = true)
    public List<NumberDto> numbers(final long spexKey) {
        final Spex spex = baseData.requireSpexByKey(spexKey);
        final boolean editor = editor();
        final List<NumberDto> own = numberRepository.findBySpexOrderByActAscPositionAsc(spex).stream()
                .filter(n -> editor || !n.isRetired()).map(ContentDtos::toDto).toList();

        if (!spex.isRevival()) {
            return own;
        }

        final Map<UUID, RevivalChange> changes = changes(spex, RevivalChangeKind.NUMBER);
        final List<NumberDto> result = new ArrayList<>();

        for (final SpexNumber number : numberRepository.findBySpexOrderByActAscPositionAsc(spex.getParent())) {
            final RevivalChange change = changes.get(number.getUuid());

            if ((editor || !number.isRetired()) && (change == null || !change.cut())) {
                final NumberDto dto = ContentDtos.toDto(number);

                result.add(change == null || change.position() == null ? dto
                        : dto.at(change.act() == null ? dto.act() : change.act(), change.position()));
            }
        }
        result.addAll(own);
        result.sort(Comparator.comparingInt(NumberDto::act).thenComparingInt(NumberDto::position));
        return result;
    }

    @Transactional
    public NumberDto createNumber(final UUID spexId, final NumberInput input) {
        return createNumber(spexId, input, null);
    }

    /**
     * EDITOR. A number with a given id, as the sample data and the migration make them.
     */
    @Transactional
    public NumberDto createNumber(final UUID spexId, final NumberInput input, final @Nullable UUID id) {
        callerService.current().requireEditor();

        final Spex spex = baseData.requireSpex(spexId);
        final SpexNumber number = new SpexNumber();

        number.setUuid(id == null ? Uuids.v7() : id);
        number.setSpex(spex);
        number.setPosition(numberRepository.maxPosition(spex) + 1);
        applyNumber(number, input, null);
        numberRepository.save(number);
        applyNumberLists(number, input);
        changed(number);
        auditService.record("CREATE", "NUMBER", number.getUuid(), "Skapade numret " + number.getTitle() + " i " + spex.label());
        return ContentDtos.toDto(number);
    }

    @Transactional
    public NumberDto updateNumber(final UUID id, final NumberInput input) {
        callerService.current().requireEditor();

        final SpexNumber number = requireNumber(id);

        applyNumber(number, input, number.getKind());
        applyNumberLists(number, input);
        changed(number);
        auditService.record("UPDATE", "NUMBER", number.getUuid(), "Ändrade numret " + number.getTitle() + " i " + number.getSpex().label());
        return ContentDtos.toDto(number);
    }

    /**
     * Deletes a number no track refers to. Other applications may have copied it, so the feed tells them it is retired;
     * they keep their copy.
     */
    @Transactional
    public boolean deleteNumber(final UUID id) {
        callerService.current().requireEditor();

        final SpexNumber number = requireNumber(id);

        if (trackRepository.countByNumber(number) > 0) {
            throw new BadRequestException("error.number.inUse");
        }
        number.setRetired(true);
        feed.record(BaseDataKind.NUMBER, number.getUuid(), ChangeType.RETIRE, null, feedRecord(number));
        numberRepository.delete(number);
        events.publishEvent(new IndexEvent(SearchKind.NUMBER, number.getUuid()));
        auditService.record("DELETE", "NUMBER", id, "Tog bort numret " + number.getTitle() + " i " + number.getSpex().label());
        return true;
    }

    private void applyNumber(final SpexNumber number, final NumberInput input, final @Nullable Kind currentKind) {
        if (input.act() < 1 || input.act() > 9) {
            throw new BadRequestException("error.field.invalid", "act");
        }
        number.setAct(input.act());
        number.setTitle(Texts.required(input.title(), 255, "title"));
        number.setKind(input.kindId() == null ? null : kindService.require(input.kindId(), KindType.NUMBER, null, currentKind));
        number.setOriginalTitle(Texts.optional(input.originalTitle(), 255, "originalTitle"));
        number.setOriginalArtist(Texts.optional(input.originalArtist(), 255, "originalArtist"));
        number.setFromWork(Texts.optional(input.fromWork(), 255, "fromWork"));
        number.setFirstLine(Texts.optional(input.firstLine(), 500, "firstLine"));
    }

    private void applyNumberLists(final SpexNumber number, final NumberInput input) {
        if (input.credits() != null) {
            final List<Credit> credits = new ArrayList<>();

            for (final NumberCreditInput credit : input.credits()) {
                final Kind kind = kindService.require(credit.kindId(), KindType.CREDIT, null, null);

                credits.add(credit.unresolved()
                        ? new Credit(number.getId(), kind.getId(), new CreditRef(null, Texts.required(credit.name(), 255, "name")), true)
                        : new Credit(number.getId(), kind.getId(), credit(new CreditRef(credit.spexareId(), credit.name()))));
            }
            lists.setCredits(number.getId(), credits);
        }
        if (input.characterIds() != null) {
            final Set<Long> allowed = characters(number.getSpex().getId()).stream().map(CharacterDto::key).collect(Collectors.toSet());
            final List<Long> keys = new ArrayList<>();

            for (final UUID characterId : input.characterIds()) {
                final SpexCharacter character = requireCharacter(characterId);

                if (!allowed.contains(character.getId())) {
                    throw new BadRequestException("error.character.otherSpex");
                }
                keys.add(character.getId());
            }
            lists.setNumberCharacters(number.getId(), keys);
        }
    }

    /**
     * A credit refers to a spexare, or names someone outside FGV; never both, never neither.
     */
    public static CreditRef credit(final CreditRef ref) {
        final String name = Texts.optional(ref.name(), 255, "name");

        if ((ref.spexareId() == null) == (name == null)) {
            throw new BadRequestException("error.credit.invalid");
        }
        return new CreditRef(ref.spexareId(), ref.spexareId() == null ? name : null);
    }

    /**
     * Numbers as text, one per line: "Title" or "Title (Original song – Original artist)"; a line "Akt 2" starts an act.
     */
    @Transactional
    public List<NumberDto> pasteNumbers(final UUID spexId, final String text) {
        callerService.current().requireEditor();

        final Spex spex = baseData.requireSpex(spexId);
        int act = 1;
        int position = numberRepository.maxPosition(spex) + 1;

        for (final String line : lines(text)) {
            final Matcher actLine = ACT.matcher(line);

            if (actLine.matches()) {
                act = Integer.parseInt(actLine.group(1));
                continue;
            }

            final Matcher original = ORIGINAL.matcher(line);
            String title = line;
            String originalTitle = null;
            String originalArtist = null;

            if (original.matches()) {
                final String[] parts = DASH.split(original.group(2), 2);

                title = original.group(1);
                originalTitle = parts[0];
                originalArtist = parts.length > 1 ? parts[1] : null;
            }

            final SpexNumber number = new SpexNumber();

            number.setUuid(Uuids.v7());
            number.setSpex(spex);
            number.setPosition(position++);
            applyNumber(number, new NumberInput(Math.clamp(act, 1, 9), title, null, originalTitle, originalArtist, null, null, null, null), null);
            numberRepository.save(number);
            changed(number);
        }
        auditService.record("CREATE", "NUMBER", spex.getUuid(), "Klistrade in nummer i " + spex.label());
        return numbers(spex.getId());
    }

    @Transactional
    public List<NumberDto> reorderNumbers(final UUID spexId, final List<Order> order) {
        callerService.current().requireEditor();

        final Spex spex = baseData.requireSpex(spexId);
        final Map<UUID, SpexNumber> own = numberRepository.findBySpexOrderByActAscPositionAsc(spex).stream()
                .collect(Collectors.toMap(SpexNumber::getUuid, Function.identity()));

        requireComplete(own.keySet(), order);
        for (int i = 0; i < order.size(); i++) {
            final SpexNumber number = own.get(order.get(i).id());
            final int act = Math.clamp(order.get(i).act(), 1, 9);

            if (number.getPosition() != i + 1 || number.getAct() != act) {
                number.setAct(act);
                number.setPosition(i + 1);
                changed(number);
            }
        }
        auditService.record("UPDATE", "NUMBER", spex.getUuid(), "Ändrade ordningen på numren i " + spex.label());
        return numbers(spex.getId());
    }

    /**
     * The credits of numbers; unresolved ones only for EDITOR (spec 5.3).
     */
    public List<Credit> credits(final Collection<Long> numberKeys) {
        final boolean editor = editor();

        return lists.credits(numberKeys).stream().filter(credit -> editor || !credit.unresolved()).toList();
    }

    /**
     * EDITOR. Credits that an import brought as names nobody has resolved yet, oldest numbers first.
     */
    @Transactional(readOnly = true)
    public List<ListStore.UnresolvedCredit> unresolvedCredits(final int limit) {
        callerService.current().requireEditor();
        return lists.unresolvedCredits(Math.clamp(limit, 1, 500));
    }

    @Transactional(readOnly = true)
    public long countUnresolvedCredits() {
        callerService.current().requireEditor();
        return lists.countUnresolvedCredits();
    }

    /**
     * EDITOR. Resolves an unresolved credit: the spexare it means, or the name as someone outside FGV.
     */
    @Transactional
    public NumberDto resolveCredit(final UUID numberId, final UUID kindId, final int position, final CreditRef ref) {
        callerService.current().requireEditor();

        final SpexNumber number = requireNumber(numberId);
        final Kind kind = kindService.require(kindId, KindType.CREDIT, null, null);

        if (!lists.resolveCredit(number.getId(), kind.getId(), position, credit(ref))) {
            throw new ResourceNotFoundException(Credit.class, numberId);
        }
        auditService.record("UPDATE", "NUMBER", number.getUuid(), "Löste en kredit på numret " + number.getTitle() + " i " + number.getSpex().label());
        return ContentDtos.toDto(number);
    }

    public Map<Long, List<Long>> numberCharacters(final Collection<Long> numberKeys) {
        return lists.numberCharacters(numberKeys);
    }

    SpexNumber requireNumber(final UUID id) {
        return numberRepository.findByUuid(resolve(BaseDataKind.NUMBER, id)).orElseThrow(() -> new ResourceNotFoundException(SpexNumber.class, id));
    }

    void changed(final SpexNumber number) {
        feed.upsert(BaseDataKind.NUMBER, number.getUuid(), feedRecord(number));
        events.publishEvent(new IndexEvent(SearchKind.NUMBER, number.getUuid()));
    }

    FeedRecords.FeedNumber feedRecord(final SpexNumber number) {
        return new FeedRecords.FeedNumber(number.getUuid(), number.getSpex().getUuid(), number.getAct(), number.getPosition(),
                number.getTitle(), number.getOriginalTitle(), number.getOriginalArtist(), number.isRetired());
    }

    // Imports (spec 5.11) ----------------------------------------------------------------------------------------------

    /**
     * EDITOR (an import). Creates or updates a character at a given position, with a figure found or created by name.
     */
    @Transactional
    public CharacterDto importCharacter(final @Nullable UUID existingId, final UUID spexId, final String name,
                                        final @Nullable String figureName, final @Nullable String description,
                                        final @Nullable Integer position) {
        callerService.current().requireEditor();

        final UUID figureId = Texts.clean(figureName) == null ? null : figureRepository.findAllByOrderByNameAsc().stream()
                .filter(figure -> figure.getName().equalsIgnoreCase(figureName.strip())).findFirst()
                .map(Figure::getUuid)
                .orElseGet(() -> createFigure(figureName, null).id());
        final CharacterDto dto = existingId == null ? createCharacter(spexId, name, figureId, description)
                : updateCharacter(existingId, name, figureId, description);

        if (position != null && position != dto.position()) {
            final SpexCharacter character = requireCharacter(dto.id());

            character.setPosition(Math.max(1, position));
            changed(character);
            return ContentDtos.toDto(character);
        }
        return dto;
    }

    /**
     * EDITOR (an import). Creates or updates a scene at a given position.
     */
    @Transactional
    public SceneDto importScene(final @Nullable UUID existingId, final UUID spexId, final SceneInput input,
                                final @Nullable Integer position) {
        callerService.current().requireEditor();

        final SceneDto dto = existingId == null ? createScene(spexId, input) : updateScene(existingId, input);

        if (position != null && position != dto.position()) {
            final Scene scene = requireScene(dto.id());

            scene.setPosition(Math.max(1, position));
            return ContentDtos.toDto(scene);
        }
        return dto;
    }

    /**
     * EDITOR (an import). Creates or updates a number at a given position.
     */
    @Transactional
    public NumberDto importNumber(final @Nullable UUID existingId, final UUID spexId, final NumberInput input,
                                 final @Nullable Integer position) {
        callerService.current().requireEditor();

        final NumberDto dto = existingId == null ? createNumber(spexId, input) : updateNumber(existingId, input);

        if (position != null && position != dto.position()) {
            final SpexNumber number = requireNumber(dto.id());

            number.setPosition(Math.max(1, position));
            changed(number);
            return ContentDtos.toDto(number);
        }
        return dto;
    }

    /**
     * The character of a spex (or of its parent, for a revival) with a name, ignoring case.
     */
    @Transactional(readOnly = true)
    public Optional<CharacterDto> characterByName(final UUID spexId, final String name) {
        return characters(baseData.requireSpex(spexId).getId()).stream()
                .filter(character -> character.name().equalsIgnoreCase(name.strip())).findFirst();
    }

    @Transactional(readOnly = true)
    public @Nullable Long characterVersion(final UUID id) {
        return characterRepository.findByUuid(id).map(SpexCharacter::getVersion).orElse(null);
    }

    @Transactional(readOnly = true)
    public @Nullable Long sceneVersion(final UUID id) {
        return sceneRepository.findByUuid(id).map(Scene::getVersion).orElse(null);
    }

    @Transactional(readOnly = true)
    public @Nullable Long numberVersion(final UUID id) {
        return numberRepository.findByUuid(id).map(SpexNumber::getVersion).orElse(null);
    }

    // Revivals ---------------------------------------------------------------------------------------------------------

    private Map<UUID, RevivalChange> changes(final Spex revival, final RevivalChangeKind kind) {
        return lists.revivalChanges(revival.getId()).stream().filter(change -> change.kind() == kind)
                .collect(Collectors.toMap(RevivalChange::recordId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<RevivalChange> revivalChanges(final long spexKey) {
        return lists.revivalChanges(spexKey);
    }

    /**
     * EDITOR. Which of its parent's records of a kind a revival cuts or moves; replaces the revival's earlier changes of
     * that kind.
     */
    @Transactional
    public void setRevivalChanges(final UUID revivalId, final RevivalChangeKind kind, final List<RevivalChange> changes) {
        callerService.current().requireEditor();

        final Spex revival = baseData.requireSpex(revivalId);

        if (!revival.isRevival()) {
            throw new BadRequestException("error.spex.notRevival");
        }

        final Set<UUID> parents = switch (kind) {
            case CHARACTER -> characterRepository.findBySpexOrderByPositionAsc(revival.getParent()).stream().map(SpexCharacter::getUuid).collect(Collectors.toSet());
            case SCENE -> sceneRepository.findBySpexOrderByActAscPositionAsc(revival.getParent()).stream().map(Scene::getUuid).collect(Collectors.toSet());
            case NUMBER -> numberRepository.findBySpexOrderByActAscPositionAsc(revival.getParent()).stream().map(SpexNumber::getUuid).collect(Collectors.toSet());
        };

        for (final RevivalChange change : changes) {
            if (!parents.contains(change.recordId()) || change.kind() != kind) {
                throw new BadRequestException("error.revival.notParents");
            }
        }
        lists.setRevivalChanges(revival.getId(), kind, changes);
        auditService.record("UPDATE", "SPEX", revivalId, "Ändrade vad nyuppsättningen " + revival.label() + " ändrar");
    }

    // Helpers ----------------------------------------------------------------------------------------------------------

    private static List<String> lines(final String text) {
        final List<String> lines = text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();

        if (lines.size() > 500) {
            throw new BadRequestException("error.tooMany", 500);
        }
        return lines;
    }

    private static void requireComplete(final Set<UUID> own, final List<Order> order) {
        if (order.size() != own.size() || !own.equals(order.stream().map(Order::id).collect(Collectors.toSet()))) {
            throw new BadRequestException("error.order.incomplete");
        }
    }

    /**
     * An image in a list; kindId is an image kind of the list's use, or null for the first one.
     */
    public record ImageRef(UUID imageId, @Nullable UUID kindId, @Nullable String caption) {
    }

    public record SceneInput(int act, String title, @Nullable String place, boolean intervalAfter) {
    }

    /**
     * A number's fields. Null lists are left as they are; credits replace all of the number's credits.
     */
    public record NumberInput(int act, String title, @Nullable UUID kindId, @Nullable String originalTitle,
                              @Nullable String originalArtist, @Nullable String fromWork, @Nullable String firstLine,
                              @Nullable List<NumberCreditInput> credits, @Nullable List<UUID> characterIds) {
    }

    /**
     * A credit of a credit kind: a spexare, someone outside FGV by name, or (imports only) an unresolved name.
     */
    public record NumberCreditInput(UUID kindId, @Nullable UUID spexareId, @Nullable String name, boolean unresolved) {

        public NumberCreditInput(final UUID kindId, final @Nullable UUID spexareId, final @Nullable String name) {
            this(kindId, spexareId, name, false);
        }
    }

    public record Order(UUID id, int act) {
    }
}
