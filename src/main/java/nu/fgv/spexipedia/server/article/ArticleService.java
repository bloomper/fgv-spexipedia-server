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

package nu.fgv.spexipedia.server.article;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.redirect.RedirectService;
import nu.fgv.spexipedia.server.article.ArticleDtos.ArticleCategoryDto;
import nu.fgv.spexipedia.server.article.ArticleDtos.ArticleDto;
import nu.fgv.spexipedia.server.article.ArticleRepositories.ArticleCategoryRepository;
import nu.fgv.spexipedia.server.article.ArticleRepositories.ArticleRepository;
import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.revision.MarkdownRenderer;
import nu.fgv.spexipedia.server.revision.Revision;
import nu.fgv.spexipedia.server.revision.RevisionService;
import nu.fgv.spexipedia.server.revision.RevisionTarget;
import nu.fgv.spexipedia.server.search.IndexEvent;
import nu.fgv.spexipedia.server.search.SearchKind;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Ids;
import nu.fgv.spexipedia.server.util.Slugs;
import nu.fgv.spexipedia.server.util.Texts;
import nu.fgv.spexipedia.server.util.Uuids;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.error.ConflictException;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Articles (section 4.5 of the spec). Visitors read published public articles; every logged-in user reads all,
 * unpublished and members' ones too, writes them and restores earlier revisions; AUTHOR publishes, unpublishes and
 * trashes them. New articles start unpublished; changes to published ones are shown at once.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class ArticleService {

    static final Duration EDITING = Duration.ofMinutes(5);
    private static final int MAX_BODY = 500_000;

    private final ArticleRepository repository;
    private final ArticleCategoryRepository categoryRepository;
    private final RevisionService revisionService;
    private final MarkdownRenderer markdown;
    private final CallerService callerService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final RedirectService redirects;
    private final BaseDataService baseData;

    @SuppressWarnings("java:S107")
    public ArticleService(final ArticleRepository repository, final ArticleCategoryRepository categoryRepository,
                          final RevisionService revisionService, final MarkdownRenderer markdown,
                          final CallerService callerService, final AuditService auditService,
                          final ApplicationEventPublisher events, final JdbcClient jdbc, final Clock clock,
                          final RedirectService redirects, final BaseDataService baseData) {
        this.redirects = redirects;
        this.baseData = baseData;
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.revisionService = revisionService;
        this.markdown = markdown;
        this.callerService = callerService;
        this.auditService = auditService;
        this.events = events;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private Optional<Caller> member() {
        return callerService.currentIfAny().filter(Caller::isUser);
    }

    /**
     * Whether the viewer may read the article: visitors only published public ones outside the trash; members all
     * outside the trash; authors also trashed ones.
     */
    static boolean visible(final Article article, final @Nullable Caller member) {
        if (article.getTrashedAt() != null) {
            return member != null && member.isAuthor();
        }
        return member != null || (article.isPublished() && article.getVisibility() == Visibility.PUBLIC);
    }

    private static ArticleDto toDto(final Article article) {
        return new ArticleDto(article.getId(), article.getUuid(), article.getSlug(), article.getTitle(), article.getBody(),
                article.getVisibility(), article.isPublished(),
                article.getCategories().stream().map(ArticleCategory::getId).toList(), article.getTrashedAt(),
                article.getCreatedAt(), article.getLastModifiedAt(), article.getParent() == null ? null : article.getParent().getId(),
                article.getPosition());
    }

    @Transactional(readOnly = true)
    public Optional<ArticleDto> article(final String idOrSlug) {
        final Caller member = member().orElse(null);

        return find(idOrSlug).filter(article -> visible(article, member)).map(ArticleService::toDto);
    }

    private Optional<Article> find(final String idOrSlug) {
        return Ids.parse(idOrSlug).flatMap(repository::findByUuid).or(() -> repository.findBySlug(idOrSlug.toLowerCase(Locale.ROOT)));
    }

    @Transactional(readOnly = true)
    public List<ArticleDto> articles(final @Nullable UUID categoryId) {
        return articles(categoryId, false);
    }

    /**
     * The articles the viewer may see, by title; in a category, and with includeSubcategories also in the categories
     * below it, at any depth.
     */
    @Transactional(readOnly = true)
    public List<ArticleDto> articles(final @Nullable UUID categoryId, final boolean includeSubcategories) {
        final Caller member = member().orElse(null);
        final Set<UUID> categories = categoryId == null ? Set.of()
                : includeSubcategories ? descendants(categoryId) : Set.of(categoryId);

        return repository.findAllLive().stream()
                .filter(article -> visible(article, member))
                .filter(article -> categoryId == null || article.getCategories().stream().anyMatch(c -> categories.contains(c.getUuid())))
                .map(ArticleService::toDto)
                .toList();
    }

    private Set<UUID> descendants(final UUID categoryId) {
        final List<ArticleCategory> all = categoryRepository.findAll();
        final Set<UUID> result = new java.util.HashSet<>(Set.of(categoryId));
        boolean added = true;

        while (added) {
            added = false;
            for (final ArticleCategory category : all) {
                if (!result.contains(category.getUuid()) && category.getParents().stream().anyMatch(parent -> result.contains(parent.getUuid()))) {
                    result.add(category.getUuid());
                    added = true;
                }
            }
        }
        return result;
    }

    // The tree of articles (spec 4.5) ----------------------------------------------------------------------------------

    /**
     * The children the viewer may see, in their order; the articles at the top for a null parent.
     */
    @Transactional(readOnly = true)
    public List<ArticleDto> children(final @Nullable Long parentKey) {
        final Caller member = member().orElse(null);
        final List<Article> children = parentKey == null ? repository.findRoots()
                : repository.findById(parentKey).map(repository::findChildren).orElse(List.of());

        return children.stream().filter(article -> visible(article, member)).map(ArticleService::toDto).toList();
    }

    /**
     * The path from the top down to the article's parent, for breadcrumbs. An ancestor the viewer may not see cuts
     * the path there.
     */
    @Transactional(readOnly = true)
    public List<ArticleDto> ancestors(final long articleKey) {
        final Caller member = member().orElse(null);
        final List<ArticleDto> path = new ArrayList<>();
        Article current = repository.findById(articleKey).map(Article::getParent).orElse(null);

        while (current != null && path.size() < 50) {
            if (!visible(current, member)) {
                break;
            }
            path.addFirst(toDto(current));
            current = current.getParent();
        }
        return path;
    }

    /**
     * AUTHOR. Moves an article under another (null for the top), last among its new siblings or at a position. Its
     * children move with it; an article never moves into its own subtree.
     */
    @Transactional
    public ArticleDto move(final UUID id, final @Nullable UUID parentId, final @Nullable Integer position) {
        callerService.current().requireAuthor();

        final Article article = require(id);
        final Article parent = parentId == null ? null : require(parentId);

        for (Article ancestor = parent; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getId().equals(article.getId())) {
                throw new BadRequestException("error.article.cycle");
            }
        }
        article.setParent(parent);

        final List<Article> siblings = new ArrayList<>(parent == null ? repository.findRoots() : repository.findChildren(parent));

        siblings.removeIf(sibling -> sibling.getId().equals(article.getId()));
        siblings.add(position == null ? siblings.size() : Math.clamp(position, 0, siblings.size()), article);
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setPosition(i + 1);
        }
        auditService.record("UPDATE", "ARTICLE", id, "Flyttade artikeln " + article.getTitle()
                + (parent == null ? " till toppen" : " under " + parent.getTitle()));
        return toDto(article);
    }

    /**
     * AUTHOR. The children of an article (or the articles at the top) in the given order; all must be listed.
     */
    @Transactional
    public List<ArticleDto> reorderChildren(final @Nullable UUID parentId, final List<UUID> ids) {
        callerService.current().requireAuthor();

        final Article parent = parentId == null ? null : require(parentId);
        final List<Article> children = parent == null ? repository.findRoots() : repository.findChildren(parent);
        final Map<UUID, Article> byId = children.stream().collect(Collectors.toMap(Article::getUuid, Function.identity()));

        if (ids.size() != children.size() || !byId.keySet().equals(Set.copyOf(ids))) {
            throw new BadRequestException("error.order.incomplete");
        }
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).setPosition(i + 1);
        }
        auditService.record("UPDATE", "ARTICLE", parentId, "Ändrade ordningen på underartiklarna"
                + (parent == null ? " på toppen" : " till " + parent.getTitle()));
        return children(parent == null ? null : parent.getId());
    }

    // Links between articles (spec 4.5) --------------------------------------------------------------------------------

    private void updateLinks(final Article article) {
        jdbc.sql("DELETE FROM article_link WHERE from_article_id = :a").param("a", article.getId()).update();

        final Map<String, String> titles = new LinkedHashMap<>();

        markdown.wikiLinks(article.getBody()).forEach(title -> titles.putIfAbsent(RedirectService.normalize(title), title));
        titles.forEach((normalized, title) -> {
            final Long to = repository.findByTitle(title).stream().findFirst().map(Article::getId).orElse(null);

            jdbc.sql("INSERT INTO article_link (from_article_id, normalized_title, title, to_article_id) VALUES (:a, :n, :t, :to)")
                    .param("a", article.getId()).param("n", truncate(normalized)).param("t", truncate(title)).param("to", to).update();
        });
    }

    /**
     * Links to the article's title now find it; links to its old title no longer do.
     */
    private void titleChanged(final Article article, final @Nullable String oldTitle) {
        if (oldTitle != null) {
            jdbc.sql("UPDATE article_link SET to_article_id = NULL WHERE to_article_id = :a AND normalized_title = :n")
                    .param("a", article.getId()).param("n", truncate(RedirectService.normalize(oldTitle))).update();
        }
        jdbc.sql("UPDATE article_link SET to_article_id = :a WHERE normalized_title = :n AND to_article_id IS NULL")
                .param("a", article.getId()).param("n", truncate(RedirectService.normalize(article.getTitle()))).update();
    }

    private static String truncate(final String text) {
        return text.length() > 255 ? text.substring(0, 255) : text;
    }

    /**
     * The articles the viewer may see that link to this one: What links here.
     */
    @Transactional(readOnly = true)
    public List<ArticleDto> backlinks(final long articleKey) {
        final Caller member = member().orElse(null);
        final List<Long> from = jdbc.sql("SELECT from_article_id FROM article_link WHERE to_article_id = :a")
                .param("a", articleKey).query(Long.class).list();

        return repository.findAllById(from).stream()
                .filter(article -> visible(article, member))
                .sorted(java.util.Comparator.comparing(Article::getTitle))
                .map(ArticleService::toDto).toList();
    }

    /**
     * USER. Titles that articles link to but no article has, most linked first. Titles that lead to a spex page or
     * through a redirect are not missing.
     */
    @Transactional(readOnly = true)
    public List<ArticleDtos.WantedArticleDto> wanted(final int limit) {
        callerService.current().requireUser();
        return jdbc.sql("SELECT MIN(l.title), COUNT(*) FROM article_link l JOIN article a ON a.id = l.from_article_id "
                        + "WHERE l.to_article_id IS NULL AND a.trashed_at IS NULL "
                        + "GROUP BY l.normalized_title ORDER BY COUNT(*) DESC, MIN(l.title) LIMIT :l")
                .param("l", Math.clamp(limit, 1, 500))
                .query((rs, _) -> new ArticleDtos.WantedArticleDto(rs.getString(1), rs.getInt(2)))
                .list().stream()
                .filter(wanted -> pathByTitle(wanted.title()) == null)
                .toList();
    }

    /**
     * USER. Articles nothing links to, with no parent, no children and no category: easy to lose.
     */
    @Transactional(readOnly = true)
    public List<ArticleDto> orphans() {
        callerService.current().requireUser();

        final Set<Long> linked = new java.util.HashSet<>(jdbc.sql("SELECT DISTINCT to_article_id FROM article_link WHERE to_article_id IS NOT NULL")
                .query(Long.class).list());
        final Set<Long> parents = new java.util.HashSet<>(jdbc.sql("SELECT DISTINCT parent_id FROM article WHERE parent_id IS NOT NULL AND trashed_at IS NULL")
                .query(Long.class).list());

        return repository.findAllLive().stream()
                .filter(article -> article.getParent() == null && article.getCategories().isEmpty()
                        && !linked.contains(article.getId()) && !parents.contains(article.getId()))
                .map(ArticleService::toDto).toList();
    }


    /**
     * The ids of the articles the viewer may see, for the recent changes.
     */
    @Transactional(readOnly = true)
    public Set<UUID> visibleIds() {
        final Caller member = member().orElse(null);

        return repository.findAllLive().stream().filter(article -> visible(article, member)).map(Article::getUuid)
                .collect(Collectors.toSet());
    }

    /**
     * The path of the article with the title, for {@code [[Title]]} links; null when there is none the viewer may see.
     */
    @Transactional(readOnly = true)
    public @Nullable String pathByTitle(final String title) {
        final Caller member = member().orElse(null);

        final String path = repository.findByTitle(title).stream().filter(article -> visible(article, member)).findFirst()
                .map(article -> "/articles/" + article.getSlug()).orElse(null);

        if (path != null) {
            return path;
        }
        // A renamed article's old title, and [[1987 Montgomery]] for spex pages
        return redirects.lookup(title).or(() -> spexPath(title)).orElse(null);
    }

    private static final java.util.regex.Pattern SPEX_TITLE = java.util.regex.Pattern.compile("^((?:19|20)\\d\\d)\\s+(.+)$");

    private Optional<String> spexPath(final String title) {
        final java.util.regex.Matcher matcher = SPEX_TITLE.matcher(title.strip());

        if (!matcher.matches()) {
            return Optional.empty();
        }
        return baseData.spexSearch(matcher.group(2), false).stream()
                .filter(spex -> spex.year().equals(matcher.group(1)) && spex.title().equalsIgnoreCase(matcher.group(2).strip()))
                .findFirst()
                .map(spex -> "/spex/" + spex.slug());
    }

    /**
     * The table of contents of rendered HTML, for articles with three headings or more.
     */
    public List<MarkdownRenderer.Heading> toc(final String html) {
        final List<MarkdownRenderer.Heading> headings = MarkdownRenderer.headings(html);

        return headings.size() >= 3 ? headings : List.of();
    }


    public String html(final @Nullable String body) {
        return markdown.render(body, this::pathByTitle);
    }

    @Transactional
    public ArticleDto create(final ArticleInput input) {
        final Caller caller = callerService.current();

        caller.requireUser();

        final Article article = new Article();

        article.setUuid(Uuids.v7());
        apply(article, input);
        article.setSlug(Slugs.unique(article.getTitle(), "artikel", repository::existsBySlug));
        if (input.parentId() != null) {
            final Article parent = require(input.parentId());

            article.setParent(parent);
            article.setPosition(repository.maxPosition(parent) + 1);
        } else {
            article.setPosition(repository.maxRootPosition() + 1);
        }
        repository.save(article);
        updateLinks(article);
        titleChanged(article, null);
        revisionService.record(RevisionTarget.ARTICLE, article.getUuid(), null, article.getTitle(), article.getBody(),
                Objects.requireNonNullElse(Texts.clean(input.comment()), "Skapade artikeln"));
        events.publishEvent(new IndexEvent(SearchKind.ARTICLE, article.getUuid()));
        auditService.record("CREATE", "ARTICLE", article.getUuid(), "Skapade artikeln " + article.getTitle());
        return toDto(article);
    }

    /**
     * Saves the article as a new revision. Refused when baseRevisionId is not the latest revision: someone else saved
     * in between, and the editor is shown the difference to merge.
     */
    @Transactional
    public ArticleDto update(final UUID id, final ArticleInput input, final @Nullable UUID baseRevisionId) {
        final Caller caller = callerService.current();

        caller.requireUser();

        final Article article = require(id);

        if (article.getTrashedAt() != null) {
            throw new BadRequestException("error.article.trashed");
        }

        final UUID latest = revisionService.latest(RevisionTarget.ARTICLE, article.getUuid(), null).map(Revision::getUuid).orElse(null);

        if (baseRevisionId != null && !baseRevisionId.equals(latest)) {
            throw new ConflictException("error.revision.conflict", article.getTitle());
        }

        final String before = article.getBody();
        final String titleBefore = article.getTitle();

        apply(article, input);
        if (!before.equals(article.getBody()) || !titleBefore.equals(article.getTitle())) {
            revisionService.record(RevisionTarget.ARTICLE, article.getUuid(), null, article.getTitle(), article.getBody(), input.comment());
        }
        updateLinks(article);
        if (!titleBefore.equals(article.getTitle())) {
            renamed(article, titleBefore);
        }
        stopEditing(article, caller);
        events.publishEvent(new IndexEvent(SearchKind.ARTICLE, article.getUuid()));
        auditService.record("UPDATE", "ARTICLE", article.getUuid(), "Ändrade artikeln " + article.getTitle());
        return toDto(article);
    }

    private void apply(final Article article, final ArticleInput input) {
        article.setTitle(Texts.required(input.title(), 255, "title"));

        final String body = input.body() == null ? "" : input.body().strip();

        if (body.length() > MAX_BODY) {
            throw new BadRequestException("error.field.invalid", "body");
        }
        article.setBody(body);
        article.setVisibility(input.visibility());
        if (input.categoryIds() != null) {
            final Set<ArticleCategory> categories = new LinkedHashSet<>();

            for (final UUID categoryId : input.categoryIds()) {
                categories.add(categoryRepository.findByUuid(categoryId)
                        .orElseThrow(() -> new ResourceNotFoundException(ArticleCategory.class, categoryId)));
            }
            article.setCategories(categories);
        }
    }

    /**
     * Restores an earlier revision's text as a new revision.
     */
    @Transactional
    public ArticleDto restore(final UUID id, final UUID revisionId) {
        final Caller caller = callerService.current();

        caller.requireUser();

        final Article article = require(id);
        final Revision revision = revisionService.find(revisionId)
                .filter(r -> r.getTarget() == RevisionTarget.ARTICLE && r.getTargetId().equals(article.getUuid()))
                .orElseThrow(() -> new ResourceNotFoundException(Revision.class, revisionId));

        if (revision.isHidden() && !caller.isAuthor()) {
            throw new BadRequestException("error.revision.hidden");
        }
        final String titleBefore = article.getTitle();

        article.setTitle(revision.getTitle());
        article.setBody(revision.getBody());
        updateLinks(article);
        if (!titleBefore.equals(article.getTitle())) {
            renamed(article, titleBefore);
        }
        revisionService.record(RevisionTarget.ARTICLE, article.getUuid(), null, article.getTitle(), article.getBody(),
                "Återställde versionen från " + revision.getCreatedAt());
        events.publishEvent(new IndexEvent(SearchKind.ARTICLE, article.getUuid()));
        auditService.record("RESTORE", "ARTICLE", article.getUuid(), "Återställde en tidigare version av " + article.getTitle());
        return toDto(article);
    }

    @Transactional
    public ArticleDto setPublished(final UUID id, final boolean published) {
        callerService.current().requireAuthor();

        final Article article = require(id);

        article.setPublished(published);
        events.publishEvent(new IndexEvent(SearchKind.ARTICLE, article.getUuid()));
        auditService.record(published ? "PUBLISH" : "UNPUBLISH", "ARTICLE", id,
                (published ? "Publicerade artikeln " : "Avpublicerade artikeln ") + article.getTitle());
        return toDto(article);
    }

    /**
     * A renamed article keeps a redirect from its old title (spec 4.5), so old links still lead to it.
     */
    private void renamed(final Article article, final String oldTitle) {
        titleChanged(article, oldTitle);
        if (redirects.lookup(oldTitle).isEmpty() && repository.findByTitle(oldTitle).isEmpty()) {
            redirects.put(null, oldTitle, "/articles/" + article.getSlug());
        }
    }

    @Transactional
    public boolean trash(final UUID id) {
        return trash(id, false);
    }

    /**
     * AUTHOR. Moves the article to the trash; one with children only with withChildren, which trashes them too.
     */
    @Transactional
    public boolean trash(final UUID id, final boolean withChildren) {
        callerService.current().requireAuthor();

        final Article article = require(id);
        final List<Article> children = repository.findChildren(article);

        if (!children.isEmpty() && !withChildren) {
            throw new BadRequestException("error.article.hasChildren");
        }
        for (final Article child : children) {
            trash(child.getUuid(), true);
        }
        article.setTrashedAt(clock.instant());
        events.publishEvent(new IndexEvent(SearchKind.ARTICLE, article.getUuid()));
        auditService.record("TRASH", "ARTICLE", id, "Lade artikeln " + article.getTitle() + " i papperskorgen");
        return true;
    }


    @Transactional
    public ArticleDto restoreFromTrash(final UUID id) {
        callerService.current().requireAuthor();

        final Article article = require(id);

        article.setTrashedAt(null);
        events.publishEvent(new IndexEvent(SearchKind.ARTICLE, article.getUuid()));
        auditService.record("RESTORE", "ARTICLE", id, "Återställde artikeln " + article.getTitle() + " ur papperskorgen");
        return toDto(article);
    }

    @Transactional(readOnly = true)
    public List<ArticleDto> trashed() {
        callerService.current().requireAuthor();
        return repository.findByTrashedAtIsNotNullOrderByTrashedAtDesc().stream().map(ArticleService::toDto).toList();
    }

    /**
     * ADMIN. Deletes trashed articles for good, with their revisions.
     */
    @Transactional
    public int emptyTrash() {
        callerService.current().requireAdmin();

        final List<Article> trashed = repository.findByTrashedAtIsNotNullOrderByTrashedAtDesc();

        for (final Article article : trashed) {
            jdbc.sql("DELETE FROM revision WHERE target = 'ARTICLE' AND target_id = :id").param("id", article.getUuid().toString()).update();
            repository.delete(article);
            events.publishEvent(new IndexEvent(SearchKind.ARTICLE, article.getUuid()));
        }
        auditService.record("DELETE", "TRASH", null, "Tömde papperskorgen på " + trashed.size() + " artiklar");
        return trashed.size();
    }

    // Who is editing ---------------------------------------------------------------------------------------------------

    /**
     * The caller is editing the article now; it is shown to others for a few minutes.
     */
    @Transactional
    public ArticleDto editing(final UUID id) {
        final Caller caller = callerService.current();

        caller.requireUser();
        callerService.record(caller);

        final Article article = require(id);

        jdbc.sql("INSERT INTO article_editing (article_id, subject, since) VALUES (:a, :s, :t) ON DUPLICATE KEY UPDATE since = :t")
                .param("a", article.getId()).param("s", caller.subject()).param("t", java.sql.Timestamp.from(clock.instant()))
                .update();
        return toDto(article);
    }

    private void stopEditing(final Article article, final Caller caller) {
        jdbc.sql("DELETE FROM article_editing WHERE article_id = :a AND subject = :s").param("a", article.getId()).param("s", caller.subject()).update();
    }

    /**
     * Who else is editing the article now, by display name.
     */
    @Transactional(readOnly = true)
    public List<String> editingBy(final long articleKey) {
        final Caller caller = member().orElse(null);

        if (caller == null) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT COALESCE(u.display_name, u.username) FROM article_editing e JOIN app_user u ON u.subject = e.subject
                        WHERE e.article_id = :a AND e.subject <> :s AND e.since > :since ORDER BY e.since""")
                .param("a", articleKey).param("s", caller.subject())
                .param("since", java.sql.Timestamp.from(clock.instant().minus(EDITING)))
                .query(String.class)
                .list();
    }

    /**
     * Forgets who was editing long ago. Part of the maintenance job RETENTION.
     */
    @Transactional
    public void cleanUpEditing() {
        jdbc.sql("DELETE FROM article_editing WHERE since < :since")
                .param("since", java.sql.Timestamp.from(clock.instant().minus(Duration.ofHours(1)))).update();
    }

    private Article require(final UUID id) {
        return repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Article.class, id));
    }

    // Categories -------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ArticleCategoryDto> categories() {
        final Map<Long, Integer> counts = counts();

        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> toDto(category, counts.getOrDefault(category.getId(), 0)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ArticleCategoryDto> category(final String idOrSlug) {
        final Map<Long, Integer> counts = counts();

        return Ids.parse(idOrSlug).flatMap(categoryRepository::findByUuid).or(() -> categoryRepository.findBySlug(idOrSlug))
                .map(category -> toDto(category, counts.getOrDefault(category.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public Map<Long, ArticleCategoryDto> categoriesByKeys(final Collection<Long> keys) {
        final Map<Long, Integer> counts = counts();

        return categoryRepository.findAllById(keys).stream()
                .map(category -> toDto(category, counts.getOrDefault(category.getId(), 0)))
                .collect(Collectors.toMap(ArticleCategoryDto::key, Function.identity()));
    }

    private Map<Long, Integer> counts() {
        final Caller member = member().orElse(null);

        return repository.findAllLive().stream()
                .filter(article -> visible(article, member))
                .flatMap(article -> article.getCategories().stream())
                .collect(Collectors.groupingBy(ArticleCategory::getId, Collectors.summingInt(_ -> 1)));
    }

    private static ArticleCategoryDto toDto(final ArticleCategory category, final int count) {
        return new ArticleCategoryDto(category.getId(), category.getUuid(), category.getSlug(), category.getName(),
                category.getDescription(), count, category.getParents().stream().map(ArticleCategory::getId).toList());
    }

    /**
     * The categories directly below a category, by name.
     */
    @Transactional(readOnly = true)
    public List<ArticleCategoryDto> subcategories(final long categoryKey) {
        final Map<Long, Integer> counts = counts();

        return categoryRepository.findAllByOrderByNameAsc().stream()
                .filter(category -> category.getParents().stream().anyMatch(parent -> parent.getId() == categoryKey))
                .map(category -> toDto(category, counts.getOrDefault(category.getId(), 0)))
                .toList();
    }

    @Transactional
    public ArticleCategoryDto createCategory(final String name, final @Nullable String description) {
        return createCategory(name, description, null);
    }

    @Transactional
    public ArticleCategoryDto createCategory(final String name, final @Nullable String description,
                                             final @Nullable List<UUID> parentIds) {
        callerService.current().requireUser();

        final ArticleCategory category = new ArticleCategory();

        category.setUuid(Uuids.v7());
        category.setName(Texts.required(name, 255, "name"));
        category.setDescription(Texts.optional(description, 10_000, "description"));
        category.setSlug(Slugs.unique(category.getName(), "kategori", categoryRepository::existsBySlug));
        categoryRepository.save(category);
        setParents(category, parentIds);
        auditService.record("CREATE", "ARTICLE_CATEGORY", category.getUuid(), "Skapade artikelkategorin " + category.getName());
        return toDto(category, 0);
    }

    @Transactional
    public ArticleCategoryDto updateCategory(final UUID id, final String name, final @Nullable String description) {
        return updateCategory(id, name, description, null);
    }

    /**
     * AUTHOR. Null parentIds leave the parents as they are.
     */
    @Transactional
    public ArticleCategoryDto updateCategory(final UUID id, final String name, final @Nullable String description,
                                             final @Nullable List<UUID> parentIds) {
        callerService.current().requireAuthor();

        final ArticleCategory category = categoryRepository.findByUuid(id)
                .orElseThrow(() -> new ResourceNotFoundException(ArticleCategory.class, id));

        category.setName(Texts.required(name, 255, "name"));
        category.setDescription(Texts.optional(description, 10_000, "description"));
        setParents(category, parentIds);
        auditService.record("UPDATE", "ARTICLE_CATEGORY", id, "Ändrade artikelkategorin " + category.getName());
        return toDto(category, counts().getOrDefault(category.getId(), 0));
    }

    /**
     * A category's parents; a category never becomes its own ancestor.
     */
    private void setParents(final ArticleCategory category, final @Nullable List<UUID> parentIds) {
        if (parentIds == null) {
            return;
        }

        final Set<ArticleCategory> parents = new LinkedHashSet<>();
        final Set<UUID> below = descendants(category.getUuid());

        for (final UUID parentId : parentIds) {
            final ArticleCategory parent = categoryRepository.findByUuid(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException(ArticleCategory.class, parentId));

            if (below.contains(parent.getUuid())) {
                throw new BadRequestException("error.article.cycle");
            }
            parents.add(parent);
        }
        category.setParents(parents);
    }

    /**
     * The category with the name, or a new one; for the import, which brings categories by name.
     */
    @Transactional
    public ArticleCategory categoryByName(final String name) {
        return categoryRepository.findByName(name.strip()).stream().findFirst().orElseGet(() -> {
            final ArticleCategoryDto created = createCategory(name, null);

            return categoryRepository.findByUuid(created.id()).orElseThrow();
        });
    }

    /**
     * The import's category: created or updated with its parents by name, which are created when missing.
     */
    @Transactional
    public ArticleCategoryDto importCategory(final @Nullable UUID existingId, final String name,
                                             final @Nullable String description, final @Nullable List<String> parents) {
        final List<UUID> parentIds = parents == null ? null : categoryIds(parents);

        return existingId == null ? createCategory(name, description, parentIds) : updateCategory(existingId, name, description, parentIds);
    }

    @Transactional(readOnly = true)
    public @Nullable Long version(final UUID id) {
        return repository.findByUuid(id).map(Article::getVersion).orElse(null);
    }

    @Transactional(readOnly = true)
    public @Nullable Long categoryVersion(final UUID id) {
        return categoryRepository.findByUuid(id).map(ArticleCategory::getVersion).orElse(null);
    }

    /**
     * The categories that an import's names refer to, created when missing.
     */
    @Transactional
    public List<UUID> categoryIds(final @Nullable List<String> names) {
        return names == null ? List.of() : names.stream().map(Texts::clean).filter(Objects::nonNull).distinct()
                .map(name -> categoryByName(name).getUuid()).toList();
    }

    /**
     * AUTHOR (an import). Deletes an unpublished article without children, with its revisions; returns whether it
     * was deleted.
     */
    @Transactional
    public boolean forget(final UUID id) {
        callerService.current().requireAuthor();

        final Article article = require(id);

        if (article.isPublished() || !repository.findChildren(article).isEmpty()) {
            return false;
        }
        jdbc.sql("DELETE FROM revision WHERE target = 'ARTICLE' AND target_id = :id").param("id", article.getUuid().toString()).update();
        repository.delete(article);
        events.publishEvent(new IndexEvent(SearchKind.ARTICLE, article.getUuid()));
        auditService.record("DELETE", "ARTICLE", id, "Tog bort den importerade artikeln " + article.getTitle());
        return true;
    }

    /**
     * AUTHOR (an import). Deletes a category no article is in and no category is below.
     */
    @Transactional
    public boolean forgetCategory(final UUID id) {
        callerService.current().requireAuthor();

        final ArticleCategory category = categoryRepository.findByUuid(id).orElse(null);

        if (category == null || counts().containsKey(category.getId()) || !subcategories(category.getId()).isEmpty()) {
            return false;
        }
        categoryRepository.delete(category);
        return true;
    }

    public record ArticleInput(String title, @Nullable String body, Visibility visibility, @Nullable List<UUID> categoryIds,
                               @Nullable String comment, @Nullable UUID parentId) {

        public ArticleInput(final String title, final @Nullable String body, final Visibility visibility,
                            final @Nullable List<UUID> categoryIds, final @Nullable String comment) {
            this(title, body, visibility, categoryIds, comment, null);
        }
    }

}
