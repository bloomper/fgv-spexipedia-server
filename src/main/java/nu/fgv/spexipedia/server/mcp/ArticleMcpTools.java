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

import nu.fgv.spexipedia.server.article.Article;
import nu.fgv.spexipedia.server.article.ArticleCategory;
import nu.fgv.spexipedia.server.article.ArticleDtos.ArticleCategoryDto;
import nu.fgv.spexipedia.server.article.ArticleDtos.ArticleDto;
import nu.fgv.spexipedia.server.article.ArticleService;
import nu.fgv.spexipedia.server.article.Visibility;
import nu.fgv.spexipedia.server.page.SpexPageService;
import nu.fgv.spexipedia.server.revision.Revision;
import nu.fgv.spexipedia.server.revision.RevisionDto;
import nu.fgv.spexipedia.server.revision.RevisionService;
import nu.fgv.spexipedia.server.revision.RevisionTarget;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Ids;
import nu.fgv.spexipedia.server.util.StoredText;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The MCP tools for articles, article categories and the recent changes. Visitors' rules apply as in the GraphQL API:
 * every user reads published articles outside the trash, and the unpublished ones they have written; authors read all.
 * Users write articles as in the web, but only
 * unpublished ones: an article created here starts unpublished, a published one is changed in the web, and publishing
 * is not a tool.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Component
public class ArticleMcpTools {

    private final MessageSource messages;
    private final ArticleService articles;
    private final RevisionService revisions;
    private final SpexPageService pages;
    private final CallerService callerService;
    private final McpToolSupport support;

    public ArticleMcpTools(final ArticleService articles, final RevisionService revisions, final SpexPageService pages,
                           final CallerService callerService, final McpToolSupport support, final MessageSource messages) {
        this.messages = messages;
        this.articles = articles;
        this.revisions = revisions;
        this.pages = pages;
        this.callerService = callerService;
        this.support = support;
    }

    @McpTool(name = "list_articles",
            description = "List the articles you may read, by title, optionally in one article category. Without the body; get_article has it.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<McpArticleRef> listArticles(
            @McpToolParam(required = false, description = "Only articles in this article category") final @Nullable String categoryId) {
        return support.call(() -> {
            final Map<Long, ArticleCategoryDto> categories = categories();

            return new McpToolSupport.ListResult<>(articles.articles(categoryId == null ? null : category(categoryId).id()).stream()
                    .map(article -> McpArticleRef.of(article, categories))
                    .toList());
        });
    }

    @McpTool(name = "get_article",
            description = "Get an article you may read by id or slug, with its body in Markdown ([[Title]] links to other articles) and its latest revision's id.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpArticle getArticle(@McpToolParam(description = "Article id or slug") final String id) {
        return support.call(() -> toMcp(article(id)));
    }

    @McpTool(name = "create_article",
            description = "USER: write a new article in Markdown, with [[Title]] links to other articles. It is saved unpublished, as the first revision; an author publishes it in the web.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public McpArticle createArticle(
            @McpToolParam(description = "The title") final String title,
            @McpToolParam(description = "The body, in Markdown") final String body,
            @McpToolParam(description = "PUBLIC (anyone, once published) or MEMBERS (logged-in users only)") final Visibility visibility,
            @McpToolParam(required = false, description = "Article category ids") final @Nullable List<String> categoryIds,
            @McpToolParam(required = false, description = "What was written, for the history") final @Nullable String comment) {
        return support.call(() -> toMcp(articles.create(new ArticleService.ArticleInput(title, body, visibility, categoryIds(categoryIds), comment))));
    }

    @McpTool(name = "update_article",
            description = "USER: change an unpublished article you have written (an author: any) as a new revision. Leave out what stays as it is; categoryIds replaces the categories. A published article is changed in the web. Give baseRevisionId (from get_article) to be refused if someone else has saved since.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public McpArticle updateArticle(
            @McpToolParam(description = "Article id or slug") final String id,
            @McpToolParam(required = false, description = "The title") final @Nullable String title,
            @McpToolParam(required = false, description = "The whole body, in Markdown") final @Nullable String body,
            @McpToolParam(required = false, description = "PUBLIC or MEMBERS") final @Nullable Visibility visibility,
            @McpToolParam(required = false, description = "Article category ids") final @Nullable List<String> categoryIds,
            @McpToolParam(required = false, description = "What was changed, for the history") final @Nullable String comment,
            @McpToolParam(required = false, description = "The revision the change is based on") final @Nullable String baseRevisionId) {
        return support.call(() -> {
            final ArticleDto article = article(id);

            if (article.published()) {
                throw new BadRequestException("error.mcp.articlePublished");
            }
            return toMcp(articles.update(article.id(), new ArticleService.ArticleInput(Objects.requireNonNullElse(title, article.title()),
                    body != null ? body : article.body(), Objects.requireNonNullElse(visibility, article.visibility()), categoryIds(categoryIds),
                    comment), baseRevisionId == null ? null : Ids.require(baseRevisionId)));
        });
    }

    @McpTool(name = "list_article_categories",
            description = "List the article categories by name, with how many articles you may read in each.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpToolSupport.ListResult<McpArticleCategory> listArticleCategories() {
        return support.call(() -> new McpToolSupport.ListResult<>(articles.categories().stream().map(McpArticleCategory::of).toList()));
    }

    @McpTool(name = "get_article_category",
            description = "Get an article category by id or slug, with the articles in it you may read.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpArticleCategoryDetails getArticleCategory(@McpToolParam(description = "Article category id or slug") final String id) {
        return support.call(() -> {
            final ArticleCategoryDto category = category(id);
            final Map<Long, ArticleCategoryDto> categories = categories();

            return new McpArticleCategoryDetails(McpArticleCategory.of(category), articles.articles(category.id()).stream()
                    .map(article -> McpArticleRef.of(article, categories))
                    .toList());
        });
    }

    @McpTool(name = "list_recent_changes",
            description = "USER: the latest revisions of articles and spex page texts you may see, newest first, without their texts. Authors see who saved them.",
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    public McpRecentChanges listRecentChanges(
            @McpToolParam(required = false, description = "Page, from 0") final @Nullable Integer page,
            @McpToolParam(required = false, description = "Revisions per page, 1-200, default 50") final @Nullable Integer size) {
        return support.call(() -> {
            callerService.current().requireUser();

            // As the GraphQL API's recentChanges: unpublished pages' revisions only for authors
            final Set<UUID> visiblePages = pages.isAuthor() ? null : pages.publishedSpexIds();
            final Page<Revision> found = revisions.visible(articles.visibleIds(), visiblePages, page == null ? 0 : page, size == null ? 50 : size);

            return new McpRecentChanges(revisions.toDtos(found.getContent()).stream().map(revision -> McpRevision.of(revision, messages)).toList(),
                    (int) found.getTotalElements(), found.getNumber(), found.getSize());
        });
    }

    private ArticleDto article(final String id) {
        return articles.article(id).orElseThrow(() -> new ResourceNotFoundException(Article.class, id));
    }

    private ArticleCategoryDto category(final String id) {
        return articles.category(id).orElseThrow(() -> new ResourceNotFoundException(ArticleCategory.class, id));
    }

    private Map<Long, ArticleCategoryDto> categories() {
        return articles.categories().stream().collect(java.util.stream.Collectors.toMap(ArticleCategoryDto::key, category -> category));
    }

    private static @Nullable List<UUID> categoryIds(final @Nullable List<String> ids) {
        return ids == null ? null : ids.stream().map(Ids::require).toList();
    }

    private McpArticle toMcp(final ArticleDto article) {
        final Map<Long, ArticleCategoryDto> categories = categories();
        final List<RevisionDto> history = revisions.history(RevisionTarget.ARTICLE, article.id());

        return new McpArticle(McpArticleRef.of(article, categories), article.body(), history.isEmpty() ? null : history.getFirst().id());
    }

    public record McpArticleRef(UUID id, String slug, String title, Visibility visibility, boolean published,
                                List<String> categories, @Nullable Instant lastModifiedAt) {

        static McpArticleRef of(final ArticleDto article, final Map<Long, ArticleCategoryDto> categories) {
            return new McpArticleRef(article.id(), article.slug(), article.title(), article.visibility(), article.published(),
                    article.categoryKeys().stream().map(categories::get).filter(Objects::nonNull).map(ArticleCategoryDto::name).toList(),
                    article.lastModifiedAt());
        }
    }

    /**
     * An article with its body.
     *
     * @param revisionId its latest revision, the base for {@code update_article}
     */
    public record McpArticle(McpArticleRef article, @Nullable String body, @Nullable UUID revisionId) {
    }

    public record McpArticleCategory(UUID id, String slug, String name, @Nullable String description,
                                     int articleCount) {

        static McpArticleCategory of(final ArticleCategoryDto category) {
            return new McpArticleCategory(category.id(), category.slug(), category.name(), category.description(), category.articleCount());
        }
    }

    public record McpArticleCategoryDetails(McpArticleCategory category, List<McpArticleRef> articles) {
    }

    /**
     * A revision without its text.
     *
     * @param authorName for authors only
     */
    public record McpRevision(UUID id, RevisionTarget target, UUID targetId, String targetTitle, @Nullable String field,
                              @Nullable String comment, boolean hidden, @Nullable String authorName,
                              Instant createdAt) {

        static McpRevision of(final RevisionDto revision, final MessageSource messages) {
            return new McpRevision(revision.id(), revision.target(), revision.targetId(), revision.targetTitle(), revision.field(),
                    revision.comment() == null ? null : StoredText.decode(revision.comment()).in(messages, Locale.ENGLISH), revision.hidden(), revision.authorName(), revision.createdAt());
        }
    }

    public record McpRecentChanges(List<McpRevision> entries, int totalCount, int page, int size) {
    }
}
