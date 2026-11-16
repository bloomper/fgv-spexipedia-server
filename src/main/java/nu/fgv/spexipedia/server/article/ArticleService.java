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

    @SuppressWarnings("java:S107")
    public ArticleService(final ArticleRepository repository, final ArticleCategoryRepository categoryRepository,
                          final RevisionService revisionService, final MarkdownRenderer markdown,
                          final CallerService callerService, final AuditService auditService,
                          final ApplicationEventPublisher events, final JdbcClient jdbc, final Clock clock) {
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
                article.getCreatedAt(), article.getLastModifiedAt());
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
        final Caller member = member().orElse(null);

        return repository.findAllLive().stream()
                .filter(article -> visible(article, member))
                .filter(article -> categoryId == null || article.getCategories().stream().anyMatch(c -> c.getUuid().equals(categoryId)))
                .map(ArticleService::toDto)
                .toList();
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

        return repository.findByTitle(title).stream().filter(article -> visible(article, member)).findFirst()
                .map(article -> "/articles/" + article.getSlug()).orElse(null);
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
        repository.save(article);
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
        article.setTitle(revision.getTitle());
        article.setBody(revision.getBody());
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

    @Transactional
    public boolean trash(final UUID id) {
        callerService.current().requireAuthor();

        final Article article = require(id);

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
                category.getDescription(), count);
    }

    @Transactional
    public ArticleCategoryDto createCategory(final String name, final @Nullable String description) {
        callerService.current().requireUser();

        final ArticleCategory category = new ArticleCategory();

        category.setUuid(Uuids.v7());
        category.setName(Texts.required(name, 255, "name"));
        category.setDescription(Texts.optional(description, 10_000, "description"));
        category.setSlug(Slugs.unique(category.getName(), "kategori", categoryRepository::existsBySlug));
        categoryRepository.save(category);
        auditService.record("CREATE", "ARTICLE_CATEGORY", category.getUuid(), "Skapade artikelkategorin " + category.getName());
        return toDto(category, 0);
    }

    @Transactional
    public ArticleCategoryDto updateCategory(final UUID id, final String name, final @Nullable String description) {
        callerService.current().requireAuthor();

        final ArticleCategory category = categoryRepository.findByUuid(id)
                .orElseThrow(() -> new ResourceNotFoundException(ArticleCategory.class, id));

        category.setName(Texts.required(name, 255, "name"));
        category.setDescription(Texts.optional(description, 10_000, "description"));
        auditService.record("UPDATE", "ARTICLE_CATEGORY", id, "Ändrade artikelkategorin " + category.getName());
        return toDto(category, counts().getOrDefault(category.getId(), 0));
    }

    public record ArticleInput(String title, @Nullable String body, Visibility visibility, @Nullable List<UUID> categoryIds,
                               @Nullable String comment) {
    }
}
