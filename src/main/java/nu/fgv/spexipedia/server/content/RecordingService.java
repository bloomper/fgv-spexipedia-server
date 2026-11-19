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
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.basedata.Spex;
import nu.fgv.spexipedia.server.content.ContentDtos.NumberDto;
import nu.fgv.spexipedia.server.content.ContentDtos.RecordingDto;
import nu.fgv.spexipedia.server.content.ContentDtos.TrackDto;
import nu.fgv.spexipedia.server.content.ContentRepositories.NumberRepository;
import nu.fgv.spexipedia.server.content.ContentRepositories.RecordingRepository;
import nu.fgv.spexipedia.server.content.ContentRepositories.TrackRepository;
import nu.fgv.spexipedia.server.content.ListStore.CreditRef;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.kind.KindType;
import nu.fgv.spexipedia.server.search.IndexEvent;
import nu.fgv.spexipedia.server.search.SearchKind;
import nu.fgv.spexipedia.server.user.Caller;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Recordings and their tracks. Anyone reads published ones; EDITOR reads and changes all.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class RecordingService {

    private static final Pattern SIDE = Pattern.compile("^(?:sida|side)\\s+(\\S+)\\s*:?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBERED = Pattern.compile("^([A-Za-z]?)(\\d{1,2})[.:)]?\\s+(.+)$");

    private final RecordingRepository repository;
    private final TrackRepository trackRepository;
    private final NumberRepository numberRepository;
    private final BaseDataService baseData;
    private final ContentService contentService;
    private final ImageService imageService;
    private final KindService kindService;
    private final CallerService callerService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    @SuppressWarnings("java:S107")
    public RecordingService(final RecordingRepository repository, final TrackRepository trackRepository,
                            final NumberRepository numberRepository, final BaseDataService baseData,
                            final ContentService contentService, final ImageService imageService,
                            final KindService kindService, final CallerService callerService, final AuditService auditService,
                            final ApplicationEventPublisher events) {
        this.repository = repository;
        this.trackRepository = trackRepository;
        this.numberRepository = numberRepository;
        this.baseData = baseData;
        this.contentService = contentService;
        this.imageService = imageService;
        this.kindService = kindService;
        this.callerService = callerService;
        this.auditService = auditService;
        this.events = events;
    }

    private boolean editor() {
        return callerService.currentIfAny().map(Caller::isEditor).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<RecordingDto> recordings() {
        final boolean editor = editor();

        return repository.findAllByOrderByCatalogueNumberAsc().stream()
                .filter(recording -> editor || recording.isPublished())
                .map(ContentDtos::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<RecordingDto> recording(final String idOrSlug) {
        final boolean editor = editor();

        return Ids.parse(idOrSlug).flatMap(repository::findByUuid).or(() -> repository.findBySlug(idOrSlug))
                .filter(recording -> editor || recording.isPublished())
                .map(ContentDtos::toDto);
    }

    @Transactional(readOnly = true)
    public List<RecordingDto> recordingsOfSpex(final long spexKey) {
        final boolean editor = editor();
        final Spex spex = baseData.requireSpexByKey(spexKey);

        return repository.findBySpexOrderByReleasedAsc(spex).stream()
                .filter(recording -> editor || recording.isPublished())
                .map(ContentDtos::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrackDto> tracks(final long recordingKey) {
        return repository.findById(recordingKey).map(recording -> trackRepository.findByRecordingOrderByPositionAsc(recording).stream()
                .map(track -> new TrackDto(track.getUuid(), track.getSide(), track.getPosition(),
                        track.getNumber() == null ? null : track.getNumber().getId(),
                        track.getNumber() != null ? track.getNumber().getTitle() : String.valueOf(track.getTitle())))
                .toList()).orElse(List.of());
    }

    @Transactional
    public RecordingDto create(final RecordingInput input) {
        callerService.current().requireEditor();

        final Recording recording = new Recording();

        recording.setUuid(Uuids.v7());
        apply(recording, input);
        recording.setSlug(Slugs.unique(recording.getCatalogueNumber(), "inspelning", repository::existsBySlug));
        repository.save(recording);
        events.publishEvent(new IndexEvent(SearchKind.RECORDING, recording.getUuid()));
        auditService.record("CREATE", "RECORDING", recording.getUuid(), "Skapade inspelningen " + recording.getCatalogueNumber() + " " + recording.getTitle());
        return ContentDtos.toDto(recording);
    }

    @Transactional
    public RecordingDto update(final UUID id, final RecordingInput input) {
        callerService.current().requireEditor();

        final Recording recording = require(id);

        apply(recording, input);
        events.publishEvent(new IndexEvent(SearchKind.RECORDING, recording.getUuid()));
        auditService.record("UPDATE", "RECORDING", id, "Ändrade inspelningen " + recording.getCatalogueNumber() + " " + recording.getTitle());
        return ContentDtos.toDto(recording);
    }

    @Transactional
    public RecordingDto setPublished(final UUID id, final boolean published) {
        callerService.current().requireEditor();

        final Recording recording = require(id);

        recording.setPublished(published);
        events.publishEvent(new IndexEvent(SearchKind.RECORDING, recording.getUuid()));
        auditService.record(published ? "PUBLISH" : "UNPUBLISH", "RECORDING", id,
                (published ? "Publicerade inspelningen " : "Avpublicerade inspelningen ") + recording.getCatalogueNumber());
        return ContentDtos.toDto(recording);
    }

    @Transactional
    public boolean delete(final UUID id) {
        callerService.current().requireEditor();

        final Recording recording = require(id);

        repository.delete(recording);
        events.publishEvent(new IndexEvent(SearchKind.RECORDING, id));
        auditService.record("DELETE", "RECORDING", id, "Tog bort inspelningen " + recording.getCatalogueNumber() + " " + recording.getTitle());
        return true;
    }

    private void apply(final Recording recording, final RecordingInput input) {
        recording.setCatalogueNumber(Texts.required(input.catalogueNumber(), 50, "catalogueNumber"));
        recording.setTitle(Texts.required(input.title(), 255, "title"));
        recording.setMedium(kindService.require(input.mediumId(), KindType.MEDIUM, null, recording.getMedium()));
        recording.setSpex(input.spexId() == null ? null : baseData.requireSpex(input.spexId()));
        recording.setReleased(Texts.partialDate(input.released()));
        recording.setRecorded(Texts.partialDate(input.recorded()));

        final CreditRef conductor = input.conductor() == null || (input.conductor().spexareId() == null
                && Texts.clean(input.conductor().name()) == null) ? null : ContentService.credit(input.conductor());

        recording.setConductorSpexareId(conductor == null ? null : conductor.spexareId());
        recording.setConductorName(conductor == null ? null : conductor.name());
        recording.setDescription(Texts.optional(input.description(), 50_000, "description"));
        recording.setFront(input.frontImageId() == null ? null : imageService.requireUsable(input.frontImageId()));
        recording.setBack(input.backImageId() == null ? null : imageService.requireUsable(input.backImageId()));
        recording.setSpexifyAlbumId(input.spexifyAlbumId());
    }

    @Transactional
    public RecordingDto setTracks(final UUID id, final List<TrackInput> tracks) {
        callerService.current().requireEditor();

        final Recording recording = require(id);

        replaceTracks(recording, tracks);
        auditService.record("UPDATE", "RECORDING", id, "Ändrade spåren på inspelningen " + recording.getCatalogueNumber());
        return ContentDtos.toDto(recording);
    }

    private void replaceTracks(final Recording recording, final List<TrackInput> tracks) {
        trackRepository.deleteByRecording(recording);
        trackRepository.flush();
        for (int i = 0; i < tracks.size(); i++) {
            final TrackInput input = tracks.get(i);
            final Track track = new Track();

            track.setUuid(Uuids.v7());
            track.setRecording(recording);
            track.setSide(Texts.optional(input.side(), 10, "side"));
            track.setPosition(i + 1);
            if (input.numberId() != null) {
                track.setNumber(numberRepository.findByUuid(input.numberId())
                        .orElseThrow(() -> new ResourceNotFoundException(SpexNumber.class, input.numberId())));
            } else {
                track.setTitle(Texts.required(input.title(), 255, "title"));
            }
            trackRepository.save(track);
        }
        events.publishEvent(new IndexEvent(SearchKind.RECORDING, recording.getUuid()));
    }

    /**
     * Tracks as text, one per line: "A1 Title" or "Title"; a line "Sida B" starts a side. Titles are matched to the
     * numbers of the recording's spex, ignoring case, diacritics and punctuation.
     */
    @Transactional
    public RecordingDto pasteTracks(final UUID id, final String text) {
        callerService.current().requireEditor();

        final Recording recording = require(id);
        final Map<String, NumberDto> numbers = recording.getSpex() == null ? Map.of()
                : contentService.numbers(recording.getSpex().getId()).stream()
                .collect(Collectors.toMap(number -> Slugs.fold(number.title()), Function.identity(), (a, _) -> a));
        final List<TrackInput> tracks = new ArrayList<>();
        String side = null;

        for (final String line : text.lines().map(String::strip).filter(l -> !l.isEmpty()).toList()) {
            final Matcher sideLine = SIDE.matcher(line);

            if (sideLine.matches()) {
                side = sideLine.group(1).toUpperCase(java.util.Locale.ROOT);
                continue;
            }

            final Matcher numbered = NUMBERED.matcher(line);
            String title = line;

            if (numbered.matches()) {
                if (!numbered.group(1).isEmpty()) {
                    side = numbered.group(1).toUpperCase(java.util.Locale.ROOT);
                }
                title = numbered.group(3);
            }

            final NumberDto number = numbers.get(Slugs.fold(title));

            tracks.add(new TrackInput(side, number == null ? null : number.id(), number == null ? title : null));
        }
        if (tracks.size() > 200) {
            throw new BadRequestException("error.tooMany", 200);
        }
        replaceTracks(recording, tracks);
        auditService.record("UPDATE", "RECORDING", id, "Klistrade in spår på inspelningen " + recording.getCatalogueNumber());
        return ContentDtos.toDto(recording);
    }

    private Recording require(final UUID id) {
        return repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Recording.class, id));
    }

    public record RecordingInput(String catalogueNumber, String title, UUID mediumId, @Nullable UUID spexId,
                                 @Nullable String released, @Nullable String recorded, @Nullable CreditRef conductor,
                                 @Nullable String description, @Nullable UUID frontImageId, @Nullable UUID backImageId,
                                 @Nullable UUID spexifyAlbumId) {
    }

    public record TrackInput(@Nullable String side, @Nullable UUID numberId, @Nullable String title) {
    }
}
