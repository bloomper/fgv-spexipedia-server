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

package nu.fgv.spexipedia.server.mcp;

import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexCategoryDto;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataGraphqlApi;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.basedata.Spex;
import nu.fgv.spexipedia.server.basedata.SpexCategory;
import nu.fgv.spexipedia.server.content.ContentDtos.CharacterDto;
import nu.fgv.spexipedia.server.content.ContentDtos.NumberDto;
import nu.fgv.spexipedia.server.content.ContentDtos.SceneDto;
import nu.fgv.spexipedia.server.content.ContentService;
import nu.fgv.spexipedia.server.links.LinkDtos.AppLinkDto;
import nu.fgv.spexipedia.server.links.LinkService;
import nu.fgv.spexipedia.server.page.PageGraphqlApi;
import nu.fgv.spexipedia.server.page.SpexPageDto;
import nu.fgv.spexipedia.server.page.SpexPageService;
import nu.fgv.spexipedia.server.people.PersonDto;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.util.StringUtils.hasText;

/**
 * The MCP tools for spex, their revivals and pages, and spex categories. Base data is public, so anyone with access
 * reads it, retired records included when asked for; the page, retired characters and numbers and people are shown as the GraphQL API shows them to the caller. AUTHOR changes a page's texts.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Component
public class SpexMcpTools {

    private final BaseDataService baseData;
    private final BaseDataGraphqlApi baseDataApi;
    private final ContentService content;
    private final SpexPageService pages;
    private final PageGraphqlApi pageApi;
    private final LinkService links;
    private final McpToolSupport support;

    @SuppressWarnings("java:S107")
    public SpexMcpTools(final BaseDataService baseData, final BaseDataGraphqlApi baseDataApi, final ContentService content,
                        final SpexPageService pages, final PageGraphqlApi pageApi,
                        final LinkService links, final McpToolSupport support) {
        this.baseData = baseData;
        this.baseDataApi = baseDataApi;
        this.content = content;
        this.pages = pages;
        this.pageApi = pageApi;
        this.links = links;
        this.support = support;
    }

    @McpTool(name = "list_spex",
            description = "List spex by year then title: originals, and their revivals when includeRevivals is true. Optionally narrowed to a category or a year. Each spex has its category and whether its page is published.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<McpSpex> listSpex(
            @McpToolParam(required = false, description = "Only spex of this spex category") final @Nullable String categoryId,
            @McpToolParam(required = false, description = "Only spex of this year, yyyy") final @Nullable String year,
            @McpToolParam(required = false, description = "Also revivals, defaults to false") final @Nullable Boolean includeRevivals,
            @McpToolParam(required = false, description = "Also retired spex, defaults to false") final @Nullable Boolean includeRetired) {
        return support.call(() -> {
            final @Nullable Long categoryKey = hasText(categoryId) ? category(categoryId).key() : null;

            return new McpToolSupport.ListResult<>(toMcp(baseData.spexAll(Boolean.TRUE.equals(includeRetired)).stream()
                    .filter(s -> Boolean.TRUE.equals(includeRevivals) || !s.revival())
                    .filter(s -> categoryKey == null || categoryKey.equals(s.categoryKey()))
                    .filter(s -> !hasText(year) || year.strip().equals(s.year()))
                    .toList()));
        });
    }

    @McpTool(name = "search_spex",
            description = "Search spex whose title, year or category name contain all the words, newest first, each original followed by its revivals. At most 50 originals.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<McpSpex> searchSpex(
            @McpToolParam(description = "Words, e.g. a title or a year") final String query,
            @McpToolParam(required = false, description = "Also retired spex, defaults to false") final @Nullable Boolean includeRetired) {
        return support.call(() -> new McpToolSupport.ListResult<>(toMcp(baseData.spexSearch(query, Boolean.TRUE.equals(includeRetired)))));
    }

    @McpTool(name = "list_spex_years",
            description = "List the years that have spex, newest first, with how many.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<BaseDataService.YearCount> listSpexYears() {
        return support.call(() -> new McpToolSupport.ListResult<>(baseData.years()));
    }

    @McpTool(name = "get_spex",
            description = "Get a spex by id or slug with its whole page as you may see it: category, the original for a revival or the revivals of an original, the page's texts in Markdown (introduction, description, synopsis; null while the page is unpublished unless you are an author), the characters, scenes and numbers on the page (a revival's after its changes to its original's), records (Spexify's discography), and links to the other FGV applications. With includePeople, also the board and who held which task, from Spexregister (visitors' rules apply: nicknames only for unpublished names).",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpSpexDetails getSpex(@McpToolParam(description = "Spex id or slug") final String id,
                                  @McpToolParam(required = false, description = "Also the board and task holders from Spexregister, defaults to false") final @Nullable Boolean includePeople) {
        return support.call(() -> {
            final SpexDto spex = spex(id);
            final McpSpex summary = toMcp(List.of(spex)).getFirst();
            final List<McpSpex> revivals = spex.revival() ? List.of() : toMcp(baseData.revivals(spex.key()));
            final @Nullable McpSpexRef parent = spex.parentKey() == null ? null : McpSpexRef.of(baseData.spexByKey(spex.parentKey()));
            final @Nullable SpexPageDto page = pageApi.page(spex);

            return new McpSpexDetails(summary.id(), summary.slug(), summary.year(), summary.title(), summary.revival(), parent,
                    summary.category(), summary.published(), summary.retired(), revivals, page == null ? null : McpPage.of(page),
                    characters(spex), content.scenes(spex.key()).stream().map(McpScene::of).toList(),
                    content.numbers(spex.key()).stream().map(McpNumber::of).toList(),
                    links.records(baseDataApi.productions(spex).stream().map(SpexDto::id).toList()).stream().map(McpRecordRef::of).toList(),
                    links.spexLinks(baseDataApi.productions(spex).stream().map(SpexDto::id).toList()),
                    Boolean.TRUE.equals(includePeople) ? McpPeople.of(baseDataApi.people(spex)) : null);
        });
    }

    @McpTool(name = "update_spex_page",
            description = "AUTHOR: change a spex page's texts, in Markdown with [[Title]] links. Leave out what stays as it is; an empty string clears it. Each changed text is saved as a revision. Publishing, images and photos are done in the web.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public McpPage updateSpexPage(
            @McpToolParam(description = "Spex id or slug") final String spexId,
            @McpToolParam(required = false, description = "The short introduction, at most 2000 characters") final @Nullable String introduction,
            @McpToolParam(required = false, description = "The description: the spex's story and background") final @Nullable String description,
            @McpToolParam(required = false, description = "The synopsis: the plot, act by act") final @Nullable String synopsis,
            @McpToolParam(required = false, description = "The premiere, yyyy-MM-dd") final @Nullable String premiere,
            @McpToolParam(required = false, description = "About the vest of the year") final @Nullable String vestDescription) {
        return support.call(() -> {
            final SpexDto spex = spex(spexId);
            // The page's own texts, not a revival's parent's: what is left out stays as it is
            final SpexPageDto current = pages.page(spex.key()).orElseGet(() -> SpexPageDto.empty(spex.key(), spex.id()));

            return McpPage.of(pages.update(spex.id(), new SpexPageService.PageInput(
                    either(premiere, current.premiere()),
                    either(introduction, current.introduction()),
                    current.shortDescription(),
                    either(description, current.description()),
                    either(synopsis, current.synopsis()),
                    either(vestDescription, current.vestDescription()),
                    current.vestWornBy(), null)));
        });
    }

    @McpTool(name = "list_spex_categories",
            description = "List spex categories (e.g. the different spex ensembles) by first year, with their descriptions.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<McpSpexCategory> listSpexCategories(
            @McpToolParam(required = false, description = "Also retired categories, defaults to false") final @Nullable Boolean includeRetired) {
        return support.call(() -> new McpToolSupport.ListResult<>(baseData.categories(Boolean.TRUE.equals(includeRetired)).stream()
                .map(McpSpexCategory::of).toList()));
    }

    @McpTool(name = "get_spex_category",
            description = "Get a spex category, with its original spex (not retired) by year.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpSpexCategoryDetails getSpexCategory(@McpToolParam(description = "Spex category id") final String id) {
        return support.call(() -> {
            final SpexCategoryDto category = category(id);

            return new McpSpexCategoryDetails(McpSpexCategory.of(category), baseData.spexOfCategory(category.key(), false).stream()
                    .filter(spex -> !spex.revival())
                    .map(McpSpexRef::of)
                    .toList());
        });
    }

    private static @Nullable String either(final @Nullable String given, final @Nullable String current) {
        return given != null ? given : current;
    }

    private SpexDto spex(final String id) {
        return baseData.spex(id).orElseThrow(() -> new ResourceNotFoundException(Spex.class, id));
    }

    private SpexCategoryDto category(final String id) {
        return baseData.category(id).orElseThrow(() -> new ResourceNotFoundException(SpexCategory.class, id));
    }

    private List<McpSpex> toMcp(final List<SpexDto> spex) {
        final Map<Long, SpexCategoryDto> categories = baseData.categoriesByKeys(spex.stream().map(SpexDto::categoryKey)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        final Set<Long> published = pages.publishedSpexKeys();

        return spex.stream()
                .map(s -> new McpSpex(s.id(), s.slug(), s.year(), s.title(), s.revival(),
                        s.categoryKey() == null ? null : McpCategoryRef.of(categories.get(s.categoryKey())),
                        published.contains(s.key()), s.retired()))
                .toList();
    }

    private List<ContentMcpTools.McpCharacter> characters(final SpexDto spex) {
        final List<CharacterDto> characters = content.characters(spex.key());
        return characters.stream().map(ContentMcpTools.McpCharacter::of).toList();
    }

    /**
     * A spex as the lists return it. A revival's title and category are its original's.
     */
    public record McpSpex(UUID id, String slug, String year, String title, boolean revival,
                          @Nullable McpCategoryRef category, boolean published, boolean retired) {
    }

    /**
     * A spex as {@code get_spex} returns it.
     *
     * @param parent   the original, for a revival
     * @param revivals the revivals, for an original
     * @param page     the page's texts; null while it is unpublished, for callers who are not authors
     * @param people   with {@code includePeople} only
     */
    public record McpSpexDetails(UUID id, String slug, String year, String title, boolean revival,
                                 @Nullable McpSpexRef parent, @Nullable McpCategoryRef category, boolean published,
                                 boolean retired, List<McpSpex> revivals, @Nullable McpPage page,
                                 List<ContentMcpTools.McpCharacter> characters, List<McpScene> scenes,
                                 List<McpNumber> numbers, List<McpRecordRef> records, List<AppLinkDto> links,
                                 @Nullable McpPeople people) {
    }

    /**
     * A spex page's texts, in Markdown.
     */
    public record McpPage(@Nullable String premiere, @Nullable String introduction,
                          @Nullable String description, @Nullable String synopsis, @Nullable String vestDescription,
                          boolean published) {

        static McpPage of(final SpexPageDto page) {
            return new McpPage(page.premiere(), page.introduction(), page.description(), page.synopsis(),
                    page.vestDescription(), page.published());
        }
    }

    public record McpScene(UUID id, int act, int position, String title, @Nullable String place, boolean intervalAfter) {

        static McpScene of(final SceneDto scene) {
            return new McpScene(scene.id(), scene.act(), scene.position(), scene.title(), scene.place(), scene.intervalAfter());
        }
    }

    public record McpNumber(UUID id, int act, int position, String title, @Nullable String originalTitle,
                            @Nullable String originalArtist, boolean retired) {

        static McpNumber of(final NumberDto number) {
            return new McpNumber(number.id(), number.act(), number.position(), number.title(), number.originalTitle(),
                    number.originalArtist(), number.retired());
        }
    }

    /**
     * A record of the spex: an album of Spexify's discography.
     */
    public record McpRecordRef(UUID id, @Nullable String catalogueNumber, String title, @Nullable String medium,
                               @Nullable String released, @Nullable String url) {

        static McpRecordRef of(final nu.fgv.spexipedia.server.links.LinkDtos.RecordDto record) {
            return new McpRecordRef(record.id(), record.catalogueNumber(), record.title(), record.mediumNameSv(),
                    record.released(), record.url());
        }
    }

    /**
     * The board and the task holders of a spex and its revivals, from Spexregister.
     *
     * @param available false while Spexregister cannot be reached
     */
    public record McpPeople(boolean available, List<McpTaskHolders> board, List<McpTaskHolders> tasks) {

        static McpPeople of(final BaseDataGraphqlApi.SpexPeopleDto people) {
            return new McpPeople(people.available(), people.board().stream().map(McpTaskHolders::of).toList(),
                    people.tasks().stream().map(McpTaskHolders::of).toList());
        }
    }

    public record McpTaskHolders(String year, String task, List<PersonDto> people) {

        static McpTaskHolders of(final BaseDataGraphqlApi.TaskHoldersDto holders) {
            return new McpTaskHolders(holders.spex().year(), holders.task().name(), holders.people());
        }
    }

    public record McpSpexRef(UUID id, String slug, String year, String title) {

        static McpSpexRef of(final SpexDto spex) {
            return new McpSpexRef(spex.id(), spex.slug(), spex.year(), spex.title());
        }
    }

    public record McpCategoryRef(UUID id, String name) {

        static @Nullable McpCategoryRef of(final @Nullable SpexCategoryDto category) {
            return category == null ? null : new McpCategoryRef(category.id(), category.name());
        }
    }

    public record McpSpexCategory(UUID id, String name, String firstYear, @Nullable String description, boolean retired) {

        static McpSpexCategory of(final SpexCategoryDto category) {
            return new McpSpexCategory(category.id(), category.name(), category.firstYear(), category.description(), category.retired());
        }
    }

    public record McpSpexCategoryDetails(McpSpexCategory category, List<McpSpexRef> spex) {
    }
}
