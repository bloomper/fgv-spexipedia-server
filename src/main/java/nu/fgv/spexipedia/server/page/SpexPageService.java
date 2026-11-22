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

package nu.fgv.spexipedia.server.page;

import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.util.StoredText;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.basedata.Spex;
import nu.fgv.spexipedia.server.content.ContentService;
import nu.fgv.spexipedia.server.content.ListStore;
import nu.fgv.spexipedia.server.content.ListStore.CreditRef;
import nu.fgv.spexipedia.server.content.ListStore.PlacedImage;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.kind.FactValueType;
import nu.fgv.spexipedia.server.kind.Kind;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.kind.KindType;
import nu.fgv.spexipedia.server.kind.KindUse;
import nu.fgv.spexipedia.server.revision.Revision;
import nu.fgv.spexipedia.server.revision.RevisionDto;
import nu.fgv.spexipedia.server.revision.RevisionService;
import nu.fgv.spexipedia.server.revision.RevisionTarget;
import nu.fgv.spexipedia.server.search.IndexEvent;
import nu.fgv.spexipedia.server.search.SearchKind;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Texts;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.error.ConflictException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spex pages (section 4.2 of the spec). Visitors see published pages; AUTHOR sees all and changes texts and images;
 * EDITOR publishes. Texts are revised like articles: every changed text is a revision, and saving over someone else's
 * newer revision is refused.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class SpexPageService {

    public static final String INTRODUCTION = "INTRODUCTION";
    public static final String DESCRIPTION = "DESCRIPTION";
    public static final String SYNOPSIS = "SYNOPSIS";
    public static final String SCENOGRAPHY = "SCENOGRAPHY";
    public static final String VEST = "VEST";
    public static final String OTHER = "OTHER";

    private final SpexPageRepository repository;
    private final BaseDataService baseData;
    private final ListStore lists;
    private final ImageService imageService;
    private final KindService kindService;
    private final RevisionService revisionService;
    private final CallerService callerService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    @SuppressWarnings("java:S107")
    public SpexPageService(final SpexPageRepository repository, final BaseDataService baseData, final ListStore lists,
                           final ImageService imageService, final KindService kindService, final RevisionService revisionService,
                           final CallerService callerService, final AuditService auditService,
                           final ApplicationEventPublisher events) {
        this.repository = repository;
        this.baseData = baseData;
        this.lists = lists;
        this.imageService = imageService;
        this.kindService = kindService;
        this.revisionService = revisionService;
        this.callerService = callerService;
        this.auditService = auditService;
        this.events = events;
    }

    private boolean author() {
        return callerService.currentIfAny().map(Caller::isAuthor).orElse(false);
    }

    /**
     * Whether the viewer sees unpublished pages.
     */
    public boolean isAuthor() {
        return author();
    }

    private static SpexPageDto toDto(final SpexPage page) {
        final CreditRef wornBy = page.getVestWornBySpexare() == null && page.getVestWornByName() == null ? null
                : new CreditRef(page.getVestWornBySpexare(), page.getVestWornByName());

        return new SpexPageDto(page.getSpex().getId(), page.getSpex().getUuid(),
                page.getPremiere() == null ? null : page.getPremiere().toString(), page.getIntroduction(), page.getShortDescription(),
                page.getDescription(),
                page.getSynopsis(), page.getRebus() == null ? null : page.getRebus().getId(),
                page.getRebusSolution() == null ? null : page.getRebusSolution().getId(), page.getVestDescription(), wornBy,
                page.isPublished(), page.getLastModifiedAt());
    }

    /**
     * The page as the viewer may see it: null for visitors and members while it is unpublished.
     */
    @Transactional(readOnly = true)
    public Optional<SpexPageDto> page(final long spexKey) {
        final Spex spex = baseData.requireSpexByKey(spexKey);
        final SpexPageDto page = repository.findBySpex(spex).map(SpexPageService::toDto)
                .orElseGet(() -> SpexPageDto.empty(spex.getId(), spex.getUuid()));

        return page.published() || author() ? Optional.of(page) : Optional.empty();
    }

    /**
     * Pages of many spex, as the viewer may see them, by spex key.
     */
    @Transactional(readOnly = true)
    public Map<Long, SpexPageDto> pages(final Collection<Long> spexKeys) {
        final boolean author = author();

        return repository.findBySpexKeys(spexKeys).stream()
                .filter(page -> author || page.isPublished())
                .collect(Collectors.toMap(page -> page.getSpex().getId(), SpexPageService::toDto));
    }

    /**
     * A revival's parent's page, whether published or not: a published revival's page shows its parent's content.
     */
    @Transactional(readOnly = true)
    public Optional<SpexPageDto> parentPage(final long parentKey) {
        return repository.findBySpexKeys(List.of(parentKey)).stream().findFirst().map(SpexPageService::toDto);
    }

    @Transactional(readOnly = true)
    public Set<Long> publishedSpexKeys() {
        return new HashSet<>(repository.findPublishedSpexKeys());
    }

    @Transactional(readOnly = true)
    public Set<UUID> publishedSpexIds() {
        return new HashSet<>(repository.findPublishedSpexIds());
    }

    @Transactional(readOnly = true)
    public List<SpexPageDto> publishedWithPremiere() {
        return repository.findPublishedWithPremiere().stream().map(SpexPageService::toDto).toList();
    }

    private SpexPage pageOf(final Spex spex) {
        return repository.findBySpex(spex).orElseGet(() -> {
            final SpexPage page = new SpexPage();

            page.setSpex(spex);
            return repository.save(page);
        });
    }

    /**
     * AUTHOR. The page's texts; each changed text is a revision. Refused when a base revision given is no longer the
     * text's latest: someone else saved in between, and the editor is shown the difference to merge.
     */
    @Transactional
    public SpexPageDto update(final UUID spexId, final PageInput input) {
        callerService.current().requireAuthor();

        final Spex spex = baseData.requireSpex(spexId);
        final SpexPage page = pageOf(spex);
        final Map<String, String> texts = new LinkedHashMap<>();

        texts.put(INTRODUCTION, Texts.optional(input.introduction(), 2_000, "introduction"));
        texts.put(DESCRIPTION, Texts.optional(input.description(), 200_000, "description"));
        texts.put(SYNOPSIS, Texts.optional(input.synopsis(), 200_000, "synopsis"));

        final Map<String, String> current = Map.of(
                INTRODUCTION, Objects.requireNonNullElse(page.getIntroduction(), ""),
                DESCRIPTION, Objects.requireNonNullElse(page.getDescription(), ""),
                SYNOPSIS, Objects.requireNonNullElse(page.getSynopsis(), ""));

        for (final Map.Entry<String, String> text : texts.entrySet()) {
            final String field = text.getKey();
            final String value = Objects.requireNonNullElse(text.getValue(), "");

            if (value.equals(current.get(field))) {
                continue;
            }

            final UUID base = input.baseRevisionIds() == null ? null : input.baseRevisionIds().get(field);
            final UUID latest = revisionService.latest(RevisionTarget.SPEX_PAGE, spex.getUuid(), field).map(Revision::getUuid).orElse(null);

            if (input.baseRevisionIds() != null && !Objects.equals(base, latest)) {
                throw new ConflictException("error.revision.conflict", field);
            }
            revisionService.record(RevisionTarget.SPEX_PAGE, spex.getUuid(), field, spex.label(), value, null);
        }
        page.setPremiere(date(input.premiere()));
        page.setIntroduction(texts.get(INTRODUCTION));
        page.setShortDescription(Texts.optional(input.shortDescription(), 1000, "shortDescription"));
        page.setDescription(texts.get(DESCRIPTION));
        page.setSynopsis(texts.get(SYNOPSIS));
        page.setVestDescription(Texts.optional(input.vestDescription(), 20_000, "vestDescription"));

        final CreditRef wornBy = input.vestWornBy() == null || (input.vestWornBy().spexareId() == null
                && Texts.clean(input.vestWornBy().name()) == null) ? null : ContentService.credit(input.vestWornBy());

        page.setVestWornBySpexare(wornBy == null ? null : wornBy.spexareId());
        page.setVestWornByName(wornBy == null ? null : wornBy.name());
        events.publishEvent(new IndexEvent(SearchKind.SPEX, spex.getUuid()));
        auditService.record("UPDATE", "SPEX_PAGE", spex.getUuid(), StoredText.of("audit.spexPage.update", spex.label()));
        return toDto(page);
    }

    private static @Nullable LocalDate date(final @Nullable String text) {
        final String cleaned = Texts.clean(text);

        try {
            return cleaned == null ? null : LocalDate.parse(cleaned);
        } catch (final DateTimeParseException e) {
            throw new BadRequestException("error.date.invalid", cleaned);
        }
    }

    /**
     * AUTHOR. The images of a section's group, in order, with captions: SCENOGRAPHY and OTHER per image kind of that use
     * (the first kind when none is given), VEST without kinds.
     */
    @Transactional
    public SpexPageDto setImages(final UUID spexId, final String section, final @Nullable UUID kindId,
                                 final List<ContentService.ImageRef> images) {
        callerService.current().requireAuthor();

        final Spex spex = baseData.requireSpex(spexId);
        final KindUse use = switch (section) {
            case SCENOGRAPHY -> KindUse.SCENOGRAPHY;
            case OTHER -> KindUse.PAGE;
            case VEST -> null;
            default -> throw new BadRequestException("error.field.invalid", "section");
        };
        Kind kind = null;

        if (use != null) {
            kind = kindId == null
                    ? kindService.first(KindType.IMAGE, use).orElseThrow(() -> new BadRequestException("error.field.invalid", "kindId"))
                    : kindService.require(kindId, KindType.IMAGE, use, null);
        } else if (kindId != null) {
            throw new BadRequestException("error.field.invalid", "kindId");
        }

        final Long kindKey = kind == null ? null : kind.getId();

        lists.setPageImages(spex.getId(), section, kindKey, images.stream()
                .map(image -> new PlacedImage(imageService.requireUsable(image.imageId()).getId(), kindKey,
                        Texts.optional(image.caption(), 1000, "caption")))
                .toList());
        auditService.record("UPDATE", "SPEX_PAGE", spex.getUuid(),
                StoredText.of("audit.spexPage.images", kind == null ? StoredText.of("audit.spexPage.section." + section)
                : StoredText.of("audit.name", kind.getNameSv(), kind.getNameEn()), spex.label()));
        return toDto(pageOf(spex));
    }

    /**
     * AUTHOR. The facts of a spex (spec 4.2), replacing its earlier ones; at most one per fact kind.
     */
    @Transactional
    public SpexPageDto setFacts(final UUID spexId, final List<FactInput> facts) {
        callerService.current().requireAuthor();

        final Spex spex = baseData.requireSpex(spexId);
        final Map<Long, ListStore.Fact> byKind = new LinkedHashMap<>();

        for (final FactInput fact : facts) {
            final Kind kind = kindService.require(fact.kindId(), KindType.FACT, null, null);
            final String value = kind.getValueType() == FactValueType.DATE
                    ? Texts.partialDate(fact.value()) : Texts.optional(fact.value(), 1000, "value");

            if (value != null) {
                byKind.put(kind.getId(), new ListStore.Fact(kind.getId(), value));
            }
        }
        lists.setFacts(spex.getId(), List.copyOf(byKind.values()));
        auditService.record("UPDATE", "SPEX_PAGE", spex.getUuid(), StoredText.of("audit.spexPage.facts", spex.label()));
        return toDto(pageOf(spex));
    }

    public List<ListStore.Fact> facts(final long spexKey) {
        return lists.facts(spexKey);
    }

    public List<PlacedImage> images(final long spexKey, final String section) {
        return lists.pageImages(spexKey, section);
    }

    /**
     * AUTHOR. Photos from Spexgram for a section: GROUP, BOARD, or ACTOR with a character.
     */
    @Transactional
    public SpexPageDto setPhotos(final UUID spexId, final String section, final @Nullable Long characterKey, final List<UUID> itemIds) {
        callerService.current().requireAuthor();

        final Spex spex = baseData.requireSpex(spexId);

        if ("ACTOR".equals(section) == (characterKey == null)) {
            throw new BadRequestException("error.photos.character");
        }
        if (itemIds.size() > 200) {
            throw new BadRequestException("error.tooMany", 200);
        }
        lists.setPhotos(spex.getId(), section, characterKey == null ? 0 : characterKey, itemIds.stream().distinct().toList());
        auditService.record("UPDATE", "SPEX_PAGE", spex.getUuid(),
                StoredText.of("audit.spexPage.photos", StoredText.of("audit.spexPage.section." + section), spex.label()));
        return toDto(pageOf(spex));
    }

    public List<UUID> photos(final long spexKey, final String section, final long characterKey) {
        return lists.photos(spexKey, section, characterKey);
    }

    /**
     * AUTHOR. The rebus and its solution.
     */
    @Transactional
    public SpexPageDto setRebus(final UUID spexId, final @Nullable UUID rebusImageId, final @Nullable UUID solutionImageId) {
        callerService.current().requireAuthor();

        final Spex spex = baseData.requireSpex(spexId);
        final SpexPage page = pageOf(spex);

        page.setRebus(rebusImageId == null ? null : imageService.requireUsable(rebusImageId));
        page.setRebusSolution(solutionImageId == null ? null : imageService.requireUsable(solutionImageId));
        auditService.record("UPDATE", "SPEX_PAGE", spex.getUuid(), StoredText.of("audit.spexPage.rebus", spex.label()));
        return toDto(page);
    }

    /**
     * EDITOR. New pages start unpublished.
     */
    @Transactional
    public SpexPageDto setPublished(final UUID spexId, final boolean published) {
        callerService.current().requireEditor();

        final Spex spex = baseData.requireSpex(spexId);
        final SpexPage page = pageOf(spex);

        page.setPublished(published);
        events.publishEvent(new IndexEvent(SearchKind.SPEX, spex.getUuid()));
        auditService.record(published ? "PUBLISH" : "UNPUBLISH", "SPEX_PAGE", spex.getUuid(),
                StoredText.of(published ? "audit.spexPage.publish" : "audit.spexPage.unpublish", spex.label()));
        return toDto(page);
    }

    /**
     * The ids of the latest revisions of the texts, to pass back when saving.
     */
    @Transactional(readOnly = true)
    public RevisionIdsDto revisionIds(final UUID spexId) {
        return new RevisionIdsDto(latestRevision(spexId, INTRODUCTION), latestRevision(spexId, DESCRIPTION),
                latestRevision(spexId, SYNOPSIS));
    }

    private @Nullable UUID latestRevision(final UUID spexId, final String field) {
        return revisionService.latest(RevisionTarget.SPEX_PAGE, spexId, field).map(Revision::getUuid).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<RevisionDto> history(final UUID spexId) {
        callerService.current().requireUser();
        return revisionService.history(RevisionTarget.SPEX_PAGE, spexId);
    }

    public record RevisionIdsDto(@Nullable UUID introduction, @Nullable UUID description, @Nullable UUID synopsis) {
    }

    public record PageInput(@Nullable String premiere, @Nullable String introduction,
                            @Nullable String shortDescription, @Nullable String description, @Nullable String synopsis, @Nullable String vestDescription,
                            @Nullable CreditRef vestWornBy, @Nullable Map<String, UUID> baseRevisionIds) {
    }

    public record FactInput(UUID kindId, @Nullable String value) {
    }
}
