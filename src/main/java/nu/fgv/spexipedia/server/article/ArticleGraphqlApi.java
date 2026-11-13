package nu.fgv.spexipedia.server.article;

import nu.fgv.spexipedia.server.article.ArticleDtos.ArticleCategoryDto;
import nu.fgv.spexipedia.server.article.ArticleDtos.ArticleDto;
import nu.fgv.spexipedia.server.page.SpexPageService;
import nu.fgv.spexipedia.server.revision.Revision;
import nu.fgv.spexipedia.server.revision.RevisionDto;
import nu.fgv.spexipedia.server.revision.RevisionService;
import nu.fgv.spexipedia.server.revision.RevisionTarget;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Ids;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GraphQL for articles, their categories and revisions, and the recent changes.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class ArticleGraphqlApi {

    private final ArticleService service;
    private final RevisionService revisionService;
    private final SpexPageService pageService;
    private final CallerService callerService;

    public ArticleGraphqlApi(final ArticleService service, final RevisionService revisionService,
                             final SpexPageService pageService, final CallerService callerService) {
        this.service = service;
        this.revisionService = revisionService;
        this.pageService = pageService;
        this.callerService = callerService;
    }

    @QueryMapping
    public @Nullable ArticleDto article(@Argument final String id) {
        return service.article(id).orElse(null);
    }

    @QueryMapping
    public List<ArticleDto> articles(@Argument final @Nullable String categoryId) {
        return service.articles(Ids.optional(categoryId));
    }

    @QueryMapping
    public List<ArticleCategoryDto> articleCategories() {
        return service.categories();
    }

    @QueryMapping
    public @Nullable ArticleCategoryDto articleCategory(@Argument final String id) {
        return service.category(id).orElse(null);
    }

    @QueryMapping
    public Map<String, Object> recentChanges(@Argument final int page, @Argument final int size) {
        callerService.current().requireUser();

        final Set<UUID> pages = pageService.isAuthor() ? null : pageService.publishedSpexIds();
        final Page<Revision> revisions = revisionService.visible(service.visibleIds(), pages, page, size);

        return Map.of("entries", revisionService.toDtos(revisions.getContent()), "totalCount", (int) revisions.getTotalElements(),
                "page", revisions.getNumber(), "size", revisions.getSize());
    }

    /**
     * The latest revisions the viewer may see: for the start page, visitors too.
     */
    public List<RevisionDto> latest(final int limit) {
        final Set<UUID> pages = pageService.isAuthor() ? null : pageService.publishedSpexIds();

        return revisionService.toDtos(revisionService.visible(service.visibleIds(), pages, 0, limit).getContent());
    }

    /**
     * The Markdown as the page would show it, for the editor's preview. Logged-in users only, as they are the writers.
     */
    @QueryMapping
    public String markdownPreview(@Argument final String markdown) {
        callerService.current().requireUser();
        return service.html(markdown);
    }

    @QueryMapping
    public @Nullable RevisionDto revision(@Argument final String id) {
        return revisionService.find(Ids.require(id))
                .filter(revision -> revision.getTarget() == RevisionTarget.ARTICLE
                        ? service.visibleIds().contains(revision.getTargetId())
                        : pageService.isAuthor() || pageService.publishedSpexIds().contains(revision.getTargetId()))
                .map(revision -> revisionService.toDtos(List.of(revision)).getFirst())
                .orElse(null);
    }

    @MutationMapping
    public ArticleDto articleCreate(@Argument final Map<String, Object> input) {
        return service.create(input(input));
    }

    @MutationMapping
    public ArticleDto articleUpdate(@Argument final String id, @Argument final Map<String, Object> input,
                                    @Argument final @Nullable String baseRevisionId) {
        return service.update(Ids.require(id), input(input), Ids.optional(baseRevisionId));
    }

    @SuppressWarnings("unchecked")
    private static ArticleService.ArticleInput input(final Map<String, Object> input) {
        return new ArticleService.ArticleInput((String) input.get("title"), (String) input.get("body"),
                Visibility.valueOf((String) input.get("visibility")),
                input.get("categoryIds") == null ? null : ((List<String>) input.get("categoryIds")).stream().map(Ids::require).toList(),
                (String) input.get("comment"));
    }

    @MutationMapping
    public ArticleDto articleEditing(@Argument final String id) {
        return service.editing(Ids.require(id));
    }

    @MutationMapping
    public ArticleDto articleRestore(@Argument final String id, @Argument final String revisionId) {
        return service.restore(Ids.require(id), Ids.require(revisionId));
    }

    @MutationMapping
    public ArticleDto articleSetPublished(@Argument final String id, @Argument final boolean published) {
        return service.setPublished(Ids.require(id), published);
    }

    @MutationMapping
    public boolean articleTrash(@Argument final String id) {
        return service.trash(Ids.require(id));
    }

    @MutationMapping
    public ArticleDto articleRestoreFromTrash(@Argument final String id) {
        return service.restoreFromTrash(Ids.require(id));
    }

    @MutationMapping
    public RevisionDto revisionSetHidden(@Argument final String id, @Argument final boolean hidden) {
        return revisionService.setHidden(Ids.require(id), hidden);
    }

    @MutationMapping
    public ArticleCategoryDto articleCategoryCreate(@Argument final Map<String, String> input) {
        return service.createCategory(input.get("name"), input.get("description"));
    }

    @MutationMapping
    public ArticleCategoryDto articleCategoryUpdate(@Argument final String id, @Argument final Map<String, String> input) {
        return service.updateCategory(Ids.require(id), input.get("name"), input.get("description"));
    }

    // Article ----------------------------------------------------------------------------------------------------------

    @SchemaMapping(typeName = "Article")
    public String html(final ArticleDto article) {
        return service.html(article.body());
    }

    @SchemaMapping(typeName = "Article")
    public List<ArticleCategoryDto> categories(final ArticleDto article) {
        final Map<Long, ArticleCategoryDto> categories = service.categoriesByKeys(article.categoryKeys());

        return article.categoryKeys().stream().map(categories::get).filter(java.util.Objects::nonNull).toList();
    }

    @SchemaMapping(typeName = "Article")
    public @Nullable RevisionDto revision(final ArticleDto article) {
        final List<RevisionDto> history = revisionService.history(RevisionTarget.ARTICLE, article.id());

        return history.isEmpty() ? null : history.getFirst();
    }

    @SchemaMapping(typeName = "Article")
    public List<RevisionDto> history(final ArticleDto article) {
        callerService.current().requireUser();
        return revisionService.history(RevisionTarget.ARTICLE, article.id());
    }

    @SchemaMapping(typeName = "Article")
    public List<String> editingBy(final ArticleDto article) {
        return service.editingBy(article.key());
    }
}
