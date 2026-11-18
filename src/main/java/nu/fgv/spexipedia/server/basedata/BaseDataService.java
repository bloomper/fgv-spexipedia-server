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

package nu.fgv.spexipedia.server.basedata;

import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexCategoryDto;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.TaskCategoryDto;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.TaskDto;
import nu.fgv.spexipedia.server.feed.ChangeFeedService;
import nu.fgv.spexipedia.server.feed.FeedRecords;
import nu.fgv.spexipedia.server.image.Image;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.image.ImageSize;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Base data: spex categories, spex and revivals, task categories and tasks. Anyone reads it, published or not; EDITOR
 * changes it (REVIVAL creates revivals too). Every change is audited and goes to the change feed (section 5.8 of the
 * spec), which the other applications copy it from.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class BaseDataService {

    private static final int MAX_SEARCH = 50;

    private final SpexCategoryRepository categoryRepository;
    private final SpexRepository spexRepository;
    private final TaskCategoryRepository taskCategoryRepository;
    private final TaskRepository taskRepository;
    private final IdAliasRepository aliasRepository;
    private final ImageService imageService;
    private final ChangeFeedService feed;
    private final CallerService callerService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    @SuppressWarnings("java:S107")
    public BaseDataService(final SpexCategoryRepository categoryRepository, final SpexRepository spexRepository,
                           final TaskCategoryRepository taskCategoryRepository, final TaskRepository taskRepository,
                           final IdAliasRepository aliasRepository, final ImageService imageService,
                           final ChangeFeedService feed, final CallerService callerService,
                           final AuditService auditService, final ApplicationEventPublisher events) {
        this.categoryRepository = categoryRepository;
        this.spexRepository = spexRepository;
        this.taskCategoryRepository = taskCategoryRepository;
        this.taskRepository = taskRepository;
        this.aliasRepository = aliasRepository;
        this.imageService = imageService;
        this.feed = feed;
        this.callerService = callerService;
        this.auditService = auditService;
        this.events = events;
    }

    // Reading ----------------------------------------------------------------------------------------------------------

    /**
     * The id itself, or the record a merged id was merged into.
     */
    UUID resolve(final BaseDataKind kind, final UUID id) {
        return aliasRepository.findByAliasAndKind(id, kind).map(IdAlias::getTarget).orElse(id);
    }

    @Transactional(readOnly = true)
    public List<SpexCategoryDto> categories(final boolean includeRetired) {
        return categoryRepository.findAllByOrderByFirstYearAscNameAsc().stream()
                .filter(category -> includeRetired || !category.isRetired())
                .map(BaseDataDtos::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SpexCategoryDto> category(final String id) {
        return Ids.parse(id).flatMap(uuid -> categoryRepository.findByUuid(resolve(BaseDataKind.SPEX_CATEGORY, uuid)))
                .map(BaseDataDtos::toDto);
    }

    @Transactional(readOnly = true)
    public Map<Long, SpexCategoryDto> categoriesByKeys(final Collection<Long> keys) {
        return categoryRepository.findAllById(keys).stream().map(BaseDataDtos::toDto)
                .collect(Collectors.toMap(SpexCategoryDto::key, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<SpexDto> spexAll(final boolean includeRetired) {
        return spexRepository.findAllWithParents().stream()
                .filter(spex -> includeRetired || !spex.isRetired())
                .map(BaseDataDtos::toDto)
                .sorted(Comparator.comparing(SpexDto::year).thenComparing(SpexDto::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * A spex by id (or the id of a spex merged into it) or by slug.
     */
    @Transactional(readOnly = true)
    public Optional<SpexDto> spex(final String idOrSlug) {
        return findSpex(idOrSlug).map(BaseDataDtos::toDto);
    }

    Optional<Spex> findSpex(final String idOrSlug) {
        return Ids.parse(idOrSlug)
                .flatMap(uuid -> spexRepository.findByUuid(resolve(BaseDataKind.SPEX, uuid)))
                .or(() -> spexRepository.findBySlug(idOrSlug.toLowerCase(Locale.ROOT)));
    }

    @Transactional(readOnly = true)
    public Map<Long, SpexDto> spexByKeys(final Collection<Long> keys) {
        return spexRepository.findAllById(keys).stream().map(BaseDataDtos::toDto)
                .collect(Collectors.toMap(SpexDto::key, Function.identity()));
    }

    @Transactional(readOnly = true)
    public SpexDto spexByKey(final long key) {
        return spexRepository.findById(key).map(BaseDataDtos::toDto).orElseThrow(() -> new ResourceNotFoundException(Spex.class, key));
    }

    @Transactional(readOnly = true)
    public List<SpexDto> revivals(final long key) {
        return spexRepository.findById(key).map(spex -> spexRepository.findByParentOrderByYearAsc(spex).stream()
                .map(BaseDataDtos::toDto).toList()).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<SpexDto> spexOfCategory(final long categoryKey, final boolean includeRetired) {
        return categoryRepository.findById(categoryKey).map(category -> spexRepository.findAllOfCategory(category).stream()
                        .filter(spex -> includeRetired || !spex.isRetired())
                        .map(BaseDataDtos::toDto)
                        .sorted(Comparator.comparing(SpexDto::revival).thenComparing(SpexDto::year))
                        .toList())
                .orElse(List.of());
    }

    /**
     * Spex whose title, year or category match every word, each original followed by its revivals, newest first.
     */
    @Transactional(readOnly = true)
    public List<SpexDto> spexSearch(final String q, final boolean includeRetired) {
        final List<String> words = List.of(Slugs.of(q).split("-")).stream().filter(word -> !word.isEmpty()).toList();
        final List<Spex> all = spexRepository.findAllWithParents();
        final Map<Long, List<Spex>> revivals = all.stream().filter(Spex::isRevival)
                .collect(Collectors.groupingBy(spex -> spex.getParent().getId(), LinkedHashMap::new, Collectors.toList()));

        return all.stream()
                .filter(spex -> !spex.isRevival())
                .filter(spex -> includeRetired || !spex.isRetired())
                .filter(spex -> {
                    final String haystack = Slugs.of(spex.effectiveTitle() + " " + spex.getYear() + " "
                            + (spex.getCategory() == null ? "" : spex.getCategory().getName()) + " "
                            + revivals.getOrDefault(spex.getId(), List.of()).stream().map(Spex::getYear).collect(Collectors.joining(" ")));

                    return words.stream().allMatch(haystack::contains);
                })
                .sorted(Comparator.comparing(Spex::getYear).reversed())
                .limit(MAX_SEARCH)
                .flatMap(spex -> java.util.stream.Stream.concat(java.util.stream.Stream.of(spex),
                        revivals.getOrDefault(spex.getId(), List.of()).stream().filter(r -> includeRetired || !r.isRetired())))
                .map(BaseDataDtos::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<YearCount> years() {
        return spexRepository.findAll().stream()
                .filter(spex -> !spex.isRetired())
                .collect(Collectors.groupingBy(Spex::getYear, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByKey().reversed())
                .map(entry -> new YearCount(entry.getKey(), entry.getValue().intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskCategoryDto> taskCategories(final boolean includeRetired) {
        return taskCategoryRepository.findAllByOrderByNameAsc().stream()
                .filter(category -> includeRetired || !category.isRetired())
                .map(BaseDataDtos::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TaskCategoryDto> taskCategory(final String id) {
        return Ids.parse(id).flatMap(uuid -> taskCategoryRepository.findByUuid(resolve(BaseDataKind.TASK_CATEGORY, uuid)))
                .map(BaseDataDtos::toDto);
    }

    @Transactional(readOnly = true)
    public Map<Long, TaskCategoryDto> taskCategoriesByKeys(final Collection<Long> keys) {
        return taskCategoryRepository.findAllById(keys).stream().map(BaseDataDtos::toDto)
                .collect(Collectors.toMap(TaskCategoryDto::key, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<TaskDto> tasks(final boolean includeRetired) {
        return taskRepository.findAllWithCategories().stream()
                .filter(task -> includeRetired || !task.isRetired())
                .map(BaseDataDtos::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskDto> tasksOfCategory(final long categoryKey, final boolean includeRetired) {
        return taskCategoryRepository.findById(categoryKey).map(category -> taskRepository.findByCategoryOrderByNameAsc(category).stream()
                .filter(task -> includeRetired || !task.isRetired())
                .map(BaseDataDtos::toDto)
                .toList()).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Optional<TaskDto> task(final String id) {
        return Ids.parse(id).flatMap(uuid -> taskRepository.findByUuid(resolve(BaseDataKind.TASK, uuid))).map(BaseDataDtos::toDto);
    }

    /**
     * Tasks by id, merged ids resolved; unknown ids are left out.
     */
    @Transactional(readOnly = true)
    public Map<UUID, TaskDto> tasksByIds(final Collection<UUID> ids) {
        final Map<UUID, UUID> resolved = ids.stream().distinct()
                .collect(Collectors.toMap(Function.identity(), id -> resolve(BaseDataKind.TASK, id)));
        final Map<UUID, TaskDto> tasks = taskRepository.findByUuidIn(resolved.values()).stream().map(BaseDataDtos::toDto)
                .collect(Collectors.toMap(TaskDto::id, Function.identity()));
        final Map<UUID, TaskDto> result = new LinkedHashMap<>();

        resolved.forEach((id, target) -> Optional.ofNullable(tasks.get(target)).ifPresent(task -> result.put(id, task)));
        return result;
    }

    /**
     * Spex by id, merged ids resolved; unknown ids are left out.
     */
    @Transactional(readOnly = true)
    public Map<UUID, SpexDto> spexByIds(final Collection<UUID> ids) {
        final Map<UUID, UUID> resolved = ids.stream().distinct()
                .collect(Collectors.toMap(Function.identity(), id -> resolve(BaseDataKind.SPEX, id)));
        final Map<UUID, SpexDto> spex = spexRepository.findByUuidIn(resolved.values()).stream().map(BaseDataDtos::toDto)
                .collect(Collectors.toMap(SpexDto::id, Function.identity()));
        final Map<UUID, SpexDto> result = new LinkedHashMap<>();

        resolved.forEach((id, target) -> Optional.ofNullable(spex.get(target)).ifPresent(found -> result.put(id, found)));
        return result;
    }

    @Transactional(readOnly = true)
    public List<TaskCategoryDto> boardCategories() {
        return taskCategoryRepository.findByBoardTrue().stream().map(BaseDataDtos::toDto).toList();
    }

    // Spex categories --------------------------------------------------------------------------------------------------

    @Transactional
    public SpexCategoryDto createCategory(final String name, final String firstYear, final @Nullable String description) {
        callerService.current().requireEditor();

        final SpexCategory category = new SpexCategory();

        category.setUuid(Uuids.v7());
        apply(category, name, firstYear, description);
        categoryRepository.save(category);
        changed(category);
        auditService.record("CREATE", "SPEX_CATEGORY", category.getUuid(), "Skapade spexkategorin " + category.getName());
        return BaseDataDtos.toDto(category);
    }

    @Transactional
    public SpexCategoryDto updateCategory(final UUID id, final String name, final String firstYear, final @Nullable String description) {
        callerService.current().requireEditor();

        final SpexCategory category = requireCategory(id);

        apply(category, name, firstYear, description);
        changed(category);
        // Every spex of the category carries its name in the search index
        spexRepository.findAllOfCategory(category).forEach(spex -> events.publishEvent(new IndexEvent(SearchKind.SPEX, spex.getUuid())));
        auditService.record("UPDATE", "SPEX_CATEGORY", id, "Ändrade spexkategorin " + category.getName());
        return BaseDataDtos.toDto(category);
    }

    @Transactional
    public SpexCategoryDto setLogo(final UUID id, final @Nullable UUID imageId) {
        callerService.current().requireEditor();

        final SpexCategory category = requireCategory(id);

        category.setLogo(imageId == null ? null : imageService.requireUsable(imageId));
        changed(category);
        auditService.record("UPDATE", "SPEX_CATEGORY", id, (imageId == null ? "Tog bort logotypen för " : "Bytte logotyp för ") + category.getName());
        return BaseDataDtos.toDto(category);
    }

    private static void apply(final SpexCategory category, final String name, final String firstYear, final @Nullable String description) {
        category.setName(Texts.required(name, 255, "name"));
        category.setFirstYear(Texts.year(firstYear));
        category.setDescription(Texts.optional(description, 10_000, "description"));
    }

    SpexCategory requireCategory(final UUID id) {
        return categoryRepository.findByUuid(resolve(BaseDataKind.SPEX_CATEGORY, id))
                .orElseThrow(() -> new ResourceNotFoundException(SpexCategory.class, id));
    }

    void changed(final SpexCategory category) {
        feed.upsert(BaseDataKind.SPEX_CATEGORY, category.getUuid(), feedRecord(category));
    }

    FeedRecords.FeedSpexCategory feedRecord(final SpexCategory category) {
        return new FeedRecords.FeedSpexCategory(category.getUuid(), category.getName(), category.getFirstYear(),
                imageUrl(category.getLogo()), category.isRetired());
    }

    // Spex -------------------------------------------------------------------------------------------------------------

    @Transactional
    public SpexDto createSpex(final String year, final @Nullable String title, final @Nullable UUID categoryId) {
        callerService.current().requireEditor();

        final Spex spex = new Spex();

        spex.setUuid(Uuids.v7());
        spex.setYear(Texts.year(year));
        spex.setTitle(Texts.required(title, 255, "title"));
        spex.setCategory(categoryId == null ? null : requireCategory(categoryId));
        requireFreeYear(spex);
        spex.setSlug(slug(spex));
        spexRepository.save(spex);
        changed(spex);
        auditService.record("CREATE", "SPEX", spex.getUuid(), "Skapade spexet " + spex.label());
        return BaseDataDtos.toDto(spex);
    }

    @Transactional
    public SpexDto updateSpex(final UUID id, final String year, final @Nullable String title, final @Nullable UUID categoryId) {
        callerService.current().requireEditor();

        final Spex spex = requireSpex(id);

        spex.setYear(Texts.year(year));
        if (!spex.isRevival()) {
            spex.setTitle(Texts.required(title, 255, "title"));
            spex.setCategory(categoryId == null ? null : requireCategory(categoryId));
        }
        requireFreeYear(spex);
        changedWithRevivals(spex);
        auditService.record("UPDATE", "SPEX", id, "Ändrade spexet " + spex.label());
        return BaseDataDtos.toDto(spex);
    }

    /**
     * One original per category and year; a revival later than its original, and one revival of an original per year.
     * Revivals share their years with originals and with other originals' revivals. A retired spex takes no year.
     */
    private void requireFreeYear(final Spex spex) {
        final Spex parent = spex.getParent();

        if (spex.isRetired()) {
            return;
        }

        if (parent != null) {
            if (spex.getYear().compareTo(parent.getYear()) <= 0) {
                throw new BadRequestException("error.spex.revivalYear");
            }
            if (spexRepository.findByParentOrderByYearAsc(parent).stream()
                    .anyMatch(other -> !other.getUuid().equals(spex.getUuid()) && !other.isRetired() && other.getYear().equals(spex.getYear()))) {
                throw new BadRequestException("error.spex.revivalYearTaken", spex.getYear());
            }
            return;
        }
        if (spex.getId() != null && spexRepository.findByParentOrderByYearAsc(spex).stream()
                .anyMatch(revival -> revival.getYear().compareTo(spex.getYear()) <= 0)) {
            throw new BadRequestException("error.spex.revivalYear");
        }
        final SpexCategory category = spex.getCategory();

        if (category != null && spexRepository.existsOriginal(category, spex.getYear(), spex.getId())) {
            throw new BadRequestException("error.spex.yearTaken", category.getName(), spex.getYear());
        }
    }

    /**
     * Before a retired spex is brought back: it takes its year again.
     */
    void requireFreeYearToUnretire(final UUID id) {
        final Spex spex = requireSpex(id);

        if (spex.isRetired()) {
            spex.setRetired(false);
            requireFreeYear(spex);
        }
    }

    /**
     * A revival of an original: by EDITOR, or by Nyuppsättning's service account (REVIVAL).
     */
    @Transactional
    public SpexDto createRevival(final UUID parentId, final String year) {
        final Caller caller = callerService.current();

        if (!caller.isEditor() && !caller.isRevivalService()) {
            throw new AccessDeniedException("EDITOR or REVIVAL required");
        }

        final Spex parent = requireSpex(parentId);

        if (parent.isRevival()) {
            throw new BadRequestException("error.spex.revivalOfRevival");
        }

        final Spex revival = new Spex();

        revival.setUuid(Uuids.v7());
        revival.setYear(Texts.year(year));
        revival.setParent(parent);
        requireFreeYear(revival);
        revival.setSlug(slug(revival));
        spexRepository.save(revival);
        changed(revival);
        auditService.record("CREATE", "SPEX", revival.getUuid(), "Skapade nyuppsättningen " + revival.label());
        return BaseDataDtos.toDto(revival);
    }

    @Transactional
    public SpexDto setPoster(final UUID id, final @Nullable UUID imageId) {
        callerService.current().requireEditor();

        final Spex spex = requireSpex(id);

        spex.setPoster(imageId == null ? null : imageService.requireUsable(imageId));
        // Revivals without a poster of their own show this one
        changedWithRevivals(spex);
        auditService.record("UPDATE", "SPEX", id, (imageId == null ? "Tog bort affischen för " : "Bytte affisch för ") + spex.label());
        return BaseDataDtos.toDto(spex);
    }

    private String slug(final Spex spex) {
        return Slugs.unique(spex.getYear() + " " + spex.effectiveTitle(), spex.getYear(), spexRepository::existsBySlug);
    }

    public Spex requireSpex(final UUID id) {
        return spexRepository.findByUuid(resolve(BaseDataKind.SPEX, id)).orElseThrow(() -> new ResourceNotFoundException(Spex.class, id));
    }

    public Spex requireSpexByKey(final long key) {
        return spexRepository.findById(key).orElseThrow(() -> new ResourceNotFoundException(Spex.class, key));
    }

    public Spex requireSpex(final String idOrSlug) {
        return findSpex(idOrSlug).orElseThrow(() -> new ResourceNotFoundException(Spex.class, idOrSlug));
    }

    void changed(final Spex spex) {
        feed.upsert(BaseDataKind.SPEX, spex.getUuid(), feedRecord(spex));
        events.publishEvent(new IndexEvent(SearchKind.SPEX, spex.getUuid()));
    }

    /**
     * A change to an original also changes what its revivals take from it.
     */
    void changedWithRevivals(final Spex spex) {
        changed(spex);
        if (!spex.isRevival()) {
            spexRepository.findByParentOrderByYearAsc(spex).forEach(this::changed);
        }
    }

    FeedRecords.FeedSpex feedRecord(final Spex spex) {
        final SpexCategory category = spex.effectiveCategory();

        return new FeedRecords.FeedSpex(spex.getUuid(), spex.getYear(), spex.effectiveTitle(),
                category == null ? null : category.getUuid(), spex.getParent() == null ? null : spex.getParent().getUuid(),
                imageUrl(spex.effectivePoster()), spex.isRetired());
    }

    private @Nullable String imageUrl(final @Nullable Image image) {
        return image == null || image.getTrashedAt() != null ? null : imageService.url(image.getUuid(), ImageSize.PAGE, "jpg");
    }

    // Task categories and tasks ----------------------------------------------------------------------------------------

    @Transactional
    public TaskCategoryDto createTaskCategory(final String name, final boolean actorPresent, final boolean board, final boolean instruments) {
        callerService.current().requireEditor();

        final TaskCategory category = new TaskCategory();

        category.setUuid(Uuids.v7());
        category.setName(Texts.required(name, 255, "name"));
        category.setActorPresent(actorPresent);
        category.setBoard(board);
        category.setInstruments(instruments);
        taskCategoryRepository.save(category);
        changed(category);
        auditService.record("CREATE", "TASK_CATEGORY", category.getUuid(), "Skapade postkategorin " + category.getName());
        return BaseDataDtos.toDto(category);
    }

    @Transactional
    public TaskCategoryDto updateTaskCategory(final UUID id, final String name, final boolean actorPresent, final boolean board, final boolean instruments) {
        callerService.current().requireEditor();

        final TaskCategory category = requireTaskCategory(id);

        category.setName(Texts.required(name, 255, "name"));
        category.setActorPresent(actorPresent);
        category.setBoard(board);
        category.setInstruments(instruments);
        changed(category);
        auditService.record("UPDATE", "TASK_CATEGORY", id, "Ändrade postkategorin " + category.getName());
        return BaseDataDtos.toDto(category);
    }

    @Transactional
    public TaskDto createTask(final String name, final UUID categoryId) {
        callerService.current().requireEditor();

        final Task task = new Task();

        task.setUuid(Uuids.v7());
        task.setName(Texts.required(name, 255, "name"));
        task.setCategory(requireTaskCategory(categoryId));
        taskRepository.save(task);
        changed(task);
        auditService.record("CREATE", "TASK", task.getUuid(), "Skapade posten " + task.getName());
        return BaseDataDtos.toDto(task);
    }

    @Transactional
    public TaskDto updateTask(final UUID id, final String name, final UUID categoryId) {
        callerService.current().requireEditor();

        final Task task = requireTask(id);

        task.setName(Texts.required(name, 255, "name"));
        task.setCategory(requireTaskCategory(categoryId));
        changed(task);
        auditService.record("UPDATE", "TASK", id, "Ändrade posten " + task.getName());
        return BaseDataDtos.toDto(task);
    }

    TaskCategory requireTaskCategory(final UUID id) {
        return taskCategoryRepository.findByUuid(resolve(BaseDataKind.TASK_CATEGORY, id))
                .orElseThrow(() -> new ResourceNotFoundException(TaskCategory.class, id));
    }

    Task requireTask(final UUID id) {
        return taskRepository.findByUuid(resolve(BaseDataKind.TASK, id)).orElseThrow(() -> new ResourceNotFoundException(Task.class, id));
    }

    void changed(final TaskCategory category) {
        feed.upsert(BaseDataKind.TASK_CATEGORY, category.getUuid(), feedRecord(category));
    }

    FeedRecords.FeedTaskCategory feedRecord(final TaskCategory category) {
        return new FeedRecords.FeedTaskCategory(category.getUuid(), category.getName(), category.isActorPresent(),
                category.isBoard(), category.isInstruments(), category.isRetired());
    }

    void changed(final Task task) {
        feed.upsert(BaseDataKind.TASK, task.getUuid(), feedRecord(task));
    }

    FeedRecords.FeedTask feedRecord(final Task task) {
        return new FeedRecords.FeedTask(task.getUuid(), task.getName(), task.getCategory().getUuid(), task.isRetired());
    }

    // Import with given ids ----------------------------------------------------------------------------------------------

    /**
     * ADMIN. Creates or updates a spex category with a given id, as the migration from Spexregister and the sample data
     * do, so that every reference in the other applications stays valid.
     */
    @Transactional
    public SpexCategoryDto importCategory(final UUID id, final String name, final String firstYear, final boolean retired) {
        callerService.current().requireAdmin();

        final SpexCategory category = categoryRepository.findByUuid(id).orElseGet(() -> {
            final SpexCategory created = new SpexCategory();

            created.setUuid(id);
            return created;
        });

        apply(category, name, firstYear, category.getDescription());
        category.setRetired(retired);
        categoryRepository.save(category);
        changed(category);
        return BaseDataDtos.toDto(category);
    }

    /**
     * ADMIN. Creates or updates a spex (an original with a title and category, or a revival with a parent) with a given id.
     */
    @Transactional
    public SpexDto importSpex(final UUID id, final String year, final @Nullable String title, final @Nullable UUID categoryId,
                              final @Nullable UUID parentId, final boolean retired) {
        callerService.current().requireAdmin();

        final Spex spex = spexRepository.findByUuid(id).orElseGet(() -> {
            final Spex created = new Spex();

            created.setUuid(id);
            return created;
        });

        spex.setYear(Texts.year(year));
        spex.setParent(parentId == null ? null : requireSpex(parentId));
        spex.setTitle(parentId == null ? Texts.required(title, 255, "title") : null);
        spex.setCategory(parentId == null && categoryId != null ? requireCategory(categoryId) : null);
        spex.setRetired(retired);
        requireFreeYear(spex);
        if (spex.getSlug() == null) {
            spex.setSlug(slug(spex));
        }
        spexRepository.save(spex);
        changed(spex);
        return BaseDataDtos.toDto(spex);
    }

    /**
     * ADMIN. Creates or updates a task category with a given id.
     */
    @Transactional
    public TaskCategoryDto importTaskCategory(final UUID id, final String name, final boolean actorPresent, final boolean board, final boolean instruments) {
        callerService.current().requireAdmin();

        final TaskCategory category = taskCategoryRepository.findByUuid(id).orElseGet(() -> {
            final TaskCategory created = new TaskCategory();

            created.setUuid(id);
            return created;
        });

        category.setName(Texts.required(name, 255, "name"));
        category.setActorPresent(actorPresent);
        category.setBoard(board);
        category.setInstruments(instruments);
        taskCategoryRepository.save(category);
        changed(category);
        return BaseDataDtos.toDto(category);
    }

    /**
     * ADMIN. Creates or updates a task with a given id.
     */
    @Transactional
    public TaskDto importTask(final UUID id, final String name, final UUID categoryId) {
        callerService.current().requireAdmin();

        final Task task = taskRepository.findByUuid(id).orElseGet(() -> {
            final Task created = new Task();

            created.setUuid(id);
            return created;
        });

        task.setName(Texts.required(name, 255, "name"));
        task.setCategory(requireTaskCategory(categoryId));
        taskRepository.save(task);
        changed(task);
        return BaseDataDtos.toDto(task);
    }

    public record YearCount(String year, int count) {
    }
}
