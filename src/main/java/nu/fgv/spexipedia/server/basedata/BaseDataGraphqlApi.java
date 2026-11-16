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

import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexCategoryDto;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.TaskCategoryDto;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.TaskDto;
import nu.fgv.spexipedia.server.image.ImageDto;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.links.LinkDtos.AppLinkDto;
import nu.fgv.spexipedia.server.links.LinkDtos.PhotoSetDto;
import nu.fgv.spexipedia.server.links.LinkService;
import nu.fgv.spexipedia.server.page.SpexPageService;
import nu.fgv.spexipedia.server.people.PeopleService;
import nu.fgv.spexipedia.server.people.PersonDto;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Ids;
import org.jspecify.annotations.Nullable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * GraphQL for base data: spex categories, spex and revivals, task categories and tasks; and the parts of a spex that
 * come from elsewhere: who held its tasks (Spexregister) and links to the other applications.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class BaseDataGraphqlApi {

    private final BaseDataService service;
    private final MergeService mergeService;
    private final SpexPageService pageService;
    private final ImageService imageService;
    private final PeopleService peopleService;
    private final LinkService linkService;
    private final CallerService callerService;

    public BaseDataGraphqlApi(final BaseDataService service, final MergeService mergeService, final SpexPageService pageService,
                              final ImageService imageService, final PeopleService peopleService, final LinkService linkService,
                              final CallerService callerService) {
        this.service = service;
        this.mergeService = mergeService;
        this.pageService = pageService;
        this.imageService = imageService;
        this.peopleService = peopleService;
        this.linkService = linkService;
        this.callerService = callerService;
    }

    // Queries ----------------------------------------------------------------------------------------------------------

    @QueryMapping
    public List<SpexCategoryDto> spexCategories(@Argument final boolean includeRetired) {
        return service.categories(includeRetired);
    }

    @QueryMapping
    public @Nullable SpexCategoryDto spexCategory(@Argument final String id) {
        return service.category(id).orElse(null);
    }

    @QueryMapping
    public List<SpexDto> spexAll(@Argument final boolean includeRetired) {
        return service.spexAll(includeRetired);
    }

    @QueryMapping
    public @Nullable SpexDto spex(@Argument final String id) {
        return service.spex(id).orElse(null);
    }

    @QueryMapping
    public List<SpexDto> spexSearch(@Argument final String q, @Argument final boolean includeRetired) {
        return service.spexSearch(q, includeRetired);
    }

    @QueryMapping
    public List<BaseDataService.YearCount> spexYears() {
        return service.years();
    }

    @QueryMapping
    public List<TaskCategoryDto> taskCategories(@Argument final boolean includeRetired) {
        return service.taskCategories(includeRetired);
    }

    @QueryMapping
    public @Nullable TaskCategoryDto taskCategory(@Argument final String id) {
        return service.taskCategory(id).orElse(null);
    }

    @QueryMapping
    public @Nullable TaskDto task(@Argument final String id) {
        return service.task(id).orElse(null);
    }

    @QueryMapping
    public List<TaskDto> tasks(@Argument final boolean includeRetired) {
        return service.tasks(includeRetired);
    }

    // Mutations --------------------------------------------------------------------------------------------------------

    @MutationMapping
    public SpexCategoryDto spexCategoryCreate(@Argument final Map<String, String> input) {
        return service.createCategory(input.get("name"), input.get("firstYear"), input.get("description"));
    }

    @MutationMapping
    public SpexCategoryDto spexCategoryUpdate(@Argument final String id, @Argument final Map<String, String> input) {
        return service.updateCategory(Ids.require(id), input.get("name"), input.get("firstYear"), input.get("description"));
    }

    @MutationMapping
    public SpexCategoryDto spexCategorySetLogo(@Argument final String id, @Argument final @Nullable String imageId) {
        return service.setLogo(Ids.require(id), Ids.optional(imageId));
    }

    @MutationMapping
    public SpexDto spexCreate(@Argument final Map<String, String> input) {
        return service.createSpex(input.get("year"), input.get("title"), Ids.optional(input.get("categoryId")));
    }

    @MutationMapping
    public SpexDto spexUpdate(@Argument final String id, @Argument final Map<String, String> input) {
        return service.updateSpex(Ids.require(id), input.get("year"), input.get("title"), Ids.optional(input.get("categoryId")));
    }

    @MutationMapping
    public SpexDto spexRevivalCreate(@Argument final String parentId, @Argument final String year) {
        return service.createRevival(Ids.require(parentId), year);
    }

    @MutationMapping
    public SpexDto spexSetPoster(@Argument final String id, @Argument final @Nullable String imageId) {
        return service.setPoster(Ids.require(id), Ids.optional(imageId));
    }

    @MutationMapping
    public TaskCategoryDto taskCategoryCreate(@Argument final Map<String, Object> input) {
        return service.createTaskCategory((String) input.get("name"), Boolean.TRUE.equals(input.get("actorPresent")),
                Boolean.TRUE.equals(input.get("board")), Boolean.TRUE.equals(input.get("instruments")));
    }

    @MutationMapping
    public TaskCategoryDto taskCategoryUpdate(@Argument final String id, @Argument final Map<String, Object> input) {
        return service.updateTaskCategory(Ids.require(id), (String) input.get("name"), Boolean.TRUE.equals(input.get("actorPresent")),
                Boolean.TRUE.equals(input.get("board")), Boolean.TRUE.equals(input.get("instruments")));
    }

    @MutationMapping
    public TaskDto taskCreate(@Argument final Map<String, String> input) {
        return service.createTask(input.get("name"), Ids.require(input.get("categoryId")));
    }

    @MutationMapping
    public TaskDto taskUpdate(@Argument final String id, @Argument final Map<String, String> input) {
        return service.updateTask(Ids.require(id), input.get("name"), Ids.require(input.get("categoryId")));
    }

    @MutationMapping
    public boolean baseDataRetire(@Argument final BaseDataKind kind, @Argument final String id, @Argument final boolean retired) {
        return mergeService.retire(kind, Ids.require(id), retired);
    }

    @MutationMapping
    public boolean baseDataMerge(@Argument final BaseDataKind kind, @Argument final String id, @Argument final String intoId) {
        return mergeService.merge(kind, Ids.require(id), Ids.require(intoId));
    }

    // SpexCategory -----------------------------------------------------------------------------------------------------

    @SchemaMapping(typeName = "SpexCategory")
    public @Nullable ImageDto logo(final SpexCategoryDto category) {
        return category.logoKey() == null ? null : imageService.visible(imageService.byIds(List.of(category.logoKey())).get(category.logoKey()));
    }

    @SchemaMapping(typeName = "SpexCategory", field = "spex")
    public List<SpexDto> categorySpex(final SpexCategoryDto category, @Argument final boolean includeRetired) {
        return service.spexOfCategory(category.key(), includeRetired);
    }

    // Spex -------------------------------------------------------------------------------------------------------------

    @BatchMapping(typeName = "Spex")
    public Map<SpexDto, @Nullable SpexCategoryDto> category(final List<SpexDto> spex) {
        final Map<Long, SpexCategoryDto> categories = service.categoriesByKeys(spex.stream().map(SpexDto::categoryKey)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        final Map<SpexDto, @Nullable SpexCategoryDto> result = new HashMap<>();

        spex.forEach(s -> result.put(s, s.categoryKey() == null ? null : categories.get(s.categoryKey())));
        return result;
    }

    @BatchMapping(typeName = "Spex")
    public Map<SpexDto, @Nullable SpexDto> parent(final List<SpexDto> spex) {
        final Map<Long, SpexDto> parents = service.spexByKeys(spex.stream().map(SpexDto::parentKey).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        final Map<SpexDto, @Nullable SpexDto> result = new HashMap<>();

        spex.forEach(s -> result.put(s, s.parentKey() == null ? null : parents.get(s.parentKey())));
        return result;
    }

    @BatchMapping(typeName = "Spex")
    public Map<SpexDto, @Nullable ImageDto> poster(final List<SpexDto> spex) {
        final var images = imageService.byIds(spex.stream().map(SpexDto::posterKey).filter(Objects::nonNull).collect(Collectors.toSet()));
        final Map<SpexDto, @Nullable ImageDto> result = new HashMap<>();

        spex.forEach(s -> result.put(s, s.posterKey() == null ? null : imageService.visible(images.get(s.posterKey()))));
        return result;
    }

    @BatchMapping(typeName = "Spex")
    public Map<SpexDto, Boolean> published(final List<SpexDto> spex) {
        final Set<Long> published = pageService.publishedSpexKeys();

        return spex.stream().collect(Collectors.toMap(s -> s, s -> published.contains(s.key()), (a, _) -> a));
    }

    @SchemaMapping(typeName = "Spex")
    public boolean revival(final SpexDto spex) {
        return spex.revival();
    }

    @SchemaMapping(typeName = "Spex")
    public List<SpexDto> revivals(final SpexDto spex) {
        return spex.revival() ? List.of() : service.revivals(spex.key());
    }

    @SchemaMapping(typeName = "Spex")
    public @Nullable SpexDto previous(final SpexDto spex) {
        return neighbour(spex, -1);
    }

    @SchemaMapping(typeName = "Spex")
    public @Nullable SpexDto next(final SpexDto spex) {
        return neighbour(spex, 1);
    }

    /**
     * The previous or next published original of the category, by year.
     */
    private @Nullable SpexDto neighbour(final SpexDto spex, final int step) {
        if (spex.categoryKey() == null) {
            return null;
        }

        final Set<Long> published = pageService.publishedSpexKeys();
        final SpexDto original = spex.revival() ? service.spexByKey(spex.parentKey()) : spex;
        final List<SpexDto> originals = service.spexOfCategory(spex.categoryKey(), false).stream()
                .filter(s -> !s.revival() && (published.contains(s.key()) || s.key() == original.key()))
                .sorted(Comparator.comparing(SpexDto::year).thenComparing(SpexDto::title))
                .toList();
        final int index = originals.indexOf(originals.stream().filter(s -> s.key() == original.key()).findFirst().orElse(null));

        if (index < 0 || index + step < 0 || index + step >= originals.size()) {
            return null;
        }
        return originals.get(index + step);
    }

    /**
     * The productions whose people a spex's page shows: an original and its revivals, or a revival alone.
     */
    public List<SpexDto> productions(final SpexDto spex) {
        return spex.revival() ? List.of(spex) : Stream.concat(Stream.of(spex), service.revivals(spex.key()).stream()).toList();
    }

    @SchemaMapping(typeName = "Spex")
    public SpexPeopleDto people(final SpexDto spex) {
        final List<SpexDto> productions = productions(spex);
        final Map<UUID, SpexDto> byId = productions.stream().collect(Collectors.toMap(SpexDto::id, s -> s));
        final PhotoSetDto boardPhotos = linkService.photos(pageService.photos(spex.key(), "BOARD", 0));
        final var participants = peopleService.participants(productions.stream().map(SpexDto::id).toList());

        if (participants.isEmpty()) {
            return new SpexPeopleDto(false, List.of(), List.of(), boardPhotos);
        }

        final Map<UUID, TaskDto> tasks = service.tasksByIds(participants.get().stream().flatMap(p -> p.taskIds().stream())
                .collect(Collectors.toSet()));
        final Map<Long, TaskCategoryDto> categories = service.taskCategoriesByKeys(tasks.values().stream().map(TaskDto::categoryKey)
                .collect(Collectors.toSet()));
        final Map<List<Object>, TaskHoldersDto> holders = new LinkedHashMap<>();

        for (final PeopleService.Shown shown : participants.get()) {
            final SpexDto production = byId.get(shown.spexId());

            if (production == null) {
                continue;
            }
            for (final UUID taskId : shown.taskIds()) {
                final TaskDto task = tasks.get(taskId);

                if (task != null) {
                    holders.computeIfAbsent(List.of(production.key(), task.key()), _ -> new TaskHoldersDto(production, task, new ArrayList<>()))
                            .people().add(shown.person());
                }
            }
        }

        final Comparator<TaskHoldersDto> order = Comparator.comparing((TaskHoldersDto h) -> h.spex().year())
                .thenComparing(h -> categories.get(h.task().categoryKey()).name())
                .thenComparing(h -> h.task().name());
        final List<TaskHoldersDto> sorted = holders.values().stream()
                .map(h -> new TaskHoldersDto(h.spex(), h.task(), h.people().stream()
                        .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.name() != null ? p.name() : p.nickname(), ""))).toList()))
                .sorted(order)
                .toList();

        return new SpexPeopleDto(true,
                sorted.stream().filter(h -> categories.get(h.task().categoryKey()).board()).toList(),
                sorted.stream().filter(h -> !categories.get(h.task().categoryKey()).board()).toList(),
                boardPhotos);
    }

    @SchemaMapping(typeName = "Spex")
    public List<AppLinkDto> links(final SpexDto spex) {
        return linkService.spexLinks(productions(spex).stream().map(SpexDto::id).toList());
    }

    // TaskCategory and Task --------------------------------------------------------------------------------------------

    @SchemaMapping(typeName = "TaskCategory", field = "tasks")
    public List<TaskDto> categoryTasks(final TaskCategoryDto category, @Argument final boolean includeRetired) {
        return service.tasksOfCategory(category.key(), includeRetired);
    }

    @BatchMapping(typeName = "Task", field = "category")
    public Map<TaskDto, TaskCategoryDto> taskCategoryOf(final List<TaskDto> tasks) {
        final Map<Long, TaskCategoryDto> categories = service.taskCategoriesByKeys(tasks.stream().map(TaskDto::categoryKey)
                .collect(Collectors.toSet()));

        return tasks.stream().collect(Collectors.toMap(t -> t, t -> categories.get(t.categoryKey()), (a, _) -> a));
    }

    public record SpexPeopleDto(boolean available, List<TaskHoldersDto> board, List<TaskHoldersDto> tasks,
                                PhotoSetDto boardPhotos) {
    }

    public record TaskHoldersDto(SpexDto spex, TaskDto task, List<PersonDto> people) {
    }
}
