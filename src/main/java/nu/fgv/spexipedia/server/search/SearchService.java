package nu.fgv.spexipedia.server.search;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import nu.fgv.spexipedia.server.revision.MarkdownRenderer;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.CallerService;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Search across spex, characters, figures, numbers (original songs included), recordings and articles, with MySQL's
 * full-text index over one table of documents. A document carries who may find it: everyone (PUBLIC), logged-in users
 * (MEMBERS) or only those who may see unpublished pages (HIDDEN). People are never indexed or searched.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Service
public class SearchService {

    private static final int MAX_TEXT = 60_000;

    private final JdbcClient jdbc;
    private final CallerService callerService;
    private final MarkdownRenderer markdown;

    public SearchService(final JdbcClient jdbc, final CallerService callerService, final MarkdownRenderer markdown) {
        this.jdbc = jdbc;
        this.callerService = callerService;
        this.markdown = markdown;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onChange(final IndexEvent event) {
        index(event.kind(), event.recordId());
    }

    /**
     * Brings one record's document up to date, or removes it when the record is gone.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void index(final SearchKind kind, final UUID id) {
        jdbc.sql("DELETE FROM search_document WHERE kind = :k AND record_id = :id").param("k", kind.name()).param("id", id.toString()).update();
        document(kind, id).ifPresent(this::insert);
    }

    /**
     * The maintenance job SEARCH_REBUILD: every document made anew.
     */
    @SchedulerLock(name = "search-rebuild", lockAtMostFor = "PT30M")
    @Transactional
    public void rebuild() {
        jdbc.sql("DELETE FROM search_document").update();
        for (final SearchKind kind : SearchKind.values()) {
            for (final String id : jdbc.sql("SELECT uuid FROM " + table(kind)).query(String.class).list()) {
                document(kind, UUID.fromString(id)).ifPresent(this::insert);
            }
        }
        log.info("Rebuilt the search index: {} documents", jdbc.sql("SELECT COUNT(*) FROM search_document").query(Long.class).single());
    }

    private static String table(final SearchKind kind) {
        return switch (kind) {
            case SPEX -> "spex";
            case CHARACTER -> "spex_character";
            case FIGURE -> "figure";
            case NUMBER -> "spex_number";
            case RECORDING -> "recording";
            case ARTICLE -> "article";
        };
    }

    private void insert(final Document document) {
        jdbc.sql("""
                        INSERT INTO search_document (kind, record_id, title, context, body, path, visibility)
                        VALUES (:kind, :id, :title, :context, :body, :path, :visibility)""")
                .param("kind", document.kind().name())
                .param("id", document.id().toString())
                .param("title", truncate(document.title(), 500))
                .param("context", document.context() == null ? null : truncate(document.context(), 500))
                .param("body", document.body() == null ? null : truncate(document.body(), MAX_TEXT))
                .param("path", document.path())
                .param("visibility", document.visibility())
                .update();
    }

    private static String truncate(final String text, final int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    /**
     * A spex's page, by internal key: its slug, label and who may see it.
     */
    private record SpexPart(String slug, String label, String visibility) {
    }

    private Optional<SpexPart> spexPart(final long spexKey) {
        return jdbc.sql("""
                        SELECT s.slug, COALESCE(s.title, p.title) AS title, s.year, s.retired, COALESCE(pg.published, 0) AS published
                        FROM spex s LEFT JOIN spex p ON p.id = s.parent_id LEFT JOIN spex_page pg ON pg.spex_id = s.id
                        WHERE s.id = :id""")
                .param("id", spexKey)
                .query((rs, _) -> new SpexPart(rs.getString("slug"), "%s (%s)".formatted(rs.getString("title"), rs.getString("year")),
                        rs.getBoolean("published") && !rs.getBoolean("retired") ? "PUBLIC" : "HIDDEN"))
                .optional();
    }

    private Optional<Document> document(final SearchKind kind, final UUID id) {
        final String uuid = id.toString();

        return switch (kind) {
            case SPEX -> jdbc.sql("""
                            SELECT s.id, s.parent_id, COALESCE(c.name, pc.name) AS category, pg.introduction, pg.description, pg.synopsis
                            FROM spex s LEFT JOIN spex p ON p.id = s.parent_id LEFT JOIN spex_category c ON c.id = s.category_id
                            LEFT JOIN spex_category pc ON pc.id = p.category_id LEFT JOIN spex_page pg ON pg.spex_id = s.id
                            WHERE s.uuid = :id""")
                    .param("id", uuid)
                    .query((rs, _) -> new Object[]{rs.getLong(1), rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)})
                    .optional()
                    .flatMap(row -> spexPart((Long) row[0]).map(part -> new Document(kind, id, part.label(),
                            join(row[1] == null ? null : "Nyuppsättning", (String) row[2]),
                            join((String) row[3], markdown.plain((String) row[4]), markdown.plain((String) row[5])),
                            "/spex/" + part.slug(), part.visibility())));
            case CHARACTER -> jdbc.sql("SELECT spex_id, name, description, retired FROM spex_character WHERE uuid = :id")
                    .param("id", uuid)
                    .query((rs, _) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)})
                    .optional()
                    .flatMap(row -> spexPart((Long) row[0]).map(part -> new Document(kind, id, (String) row[1], part.label(),
                            markdown.plain((String) row[2]), "/spex/" + part.slug() + "#character-" + id,
                            (Boolean) row[3] ? "HIDDEN" : part.visibility())));
            case FIGURE -> jdbc.sql("SELECT slug, name, description FROM figure WHERE uuid = :id")
                    .param("id", uuid)
                    .query((rs, _) -> new Document(kind, id, rs.getString(2), null, markdown.plain(rs.getString(3)),
                            "/figures/" + rs.getString(1), "PUBLIC"))
                    .optional();
            case NUMBER -> jdbc.sql("SELECT spex_id, title, original_title, original_artist, retired FROM spex_number WHERE uuid = :id")
                    .param("id", uuid)
                    .query((rs, _) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)})
                    .optional()
                    .flatMap(row -> spexPart((Long) row[0]).map(part -> new Document(kind, id, (String) row[1],
                            join(part.label(), row[2] == null ? null : row[2] + (row[3] == null ? "" : " (" + row[3] + ")")), null,
                            "/spex/" + part.slug() + "#number-" + id, (Boolean) row[4] ? "HIDDEN" : part.visibility())));
            case RECORDING -> jdbc.sql("""
                            SELECT r.slug, r.catalogue_number, r.title, r.description, r.published,
                                   (SELECT GROUP_CONCAT(COALESCE(n.title, t.title) SEPARATOR ' ') FROM recording_track t
                                    LEFT JOIN spex_number n ON n.id = t.number_id WHERE t.recording_id = r.id) AS tracks
                            FROM recording r WHERE r.uuid = :id""")
                    .param("id", uuid)
                    .query((rs, _) -> new Document(kind, id, rs.getString(2) + " " + rs.getString(3), null,
                            join(markdown.plain(rs.getString(4)), rs.getString(6)), "/recordings/" + rs.getString(1),
                            rs.getBoolean(5) ? "PUBLIC" : "HIDDEN"))
                    .optional();
            case ARTICLE -> jdbc.sql("SELECT slug, title, body, visibility, published, trashed_at FROM article WHERE uuid = :id")
                    .param("id", uuid)
                    .query((rs, _) -> rs.getTimestamp(6) != null ? null : new Document(kind, id, rs.getString(2), null,
                            markdown.plain(rs.getString(3)), "/articles/" + rs.getString(1),
                            rs.getBoolean(5) && "PUBLIC".equals(rs.getString(4)) ? "PUBLIC" : "MEMBERS"))
                    .optional();
        };
    }

    private static @Nullable String join(final @Nullable String... parts) {
        final String joined = String.join(" · ", Arrays.stream(parts).filter(part -> part != null && !part.isBlank()).toList());

        return joined.isEmpty() ? null : joined;
    }

    /**
     * Records matching every word (a word also matches words starting with it), the best first.
     */
    @Transactional(readOnly = true)
    public List<Hit> search(final String q, final int limit) {
        final Optional<Caller> caller = callerService.currentIfAny().filter(Caller::isUser);
        final List<String> visibilities = caller.map(c -> c.isAuthor() ? List.of("PUBLIC", "MEMBERS", "HIDDEN") : List.of("PUBLIC", "MEMBERS"))
                .orElse(List.of("PUBLIC"));
        final List<String> words = Arrays.stream(q.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> !word.isBlank())
                .limit(10)
                .toList();

        if (words.isEmpty()) {
            return List.of();
        }

        final List<String> longWords = words.stream().filter(word -> word.length() >= 3).toList();
        final List<String> shortWords = words.stream().filter(word -> word.length() < 3).toList();
        final StringBuilder sql = new StringBuilder("SELECT kind, record_id, title, context, path");
        final List<Object> params = new ArrayList<>();

        if (!longWords.isEmpty()) {
            final String against = String.join(" ", longWords.stream().map(word -> "+" + word + "*").toList());

            // Relevance, and a title that starts with the first word first: "lasse" finds the spex Lasse-Maja before its numbers
            sql.append(", MATCH(title, context, body) AGAINST (? IN BOOLEAN MODE) + 2 * MATCH(title, context, body) AGAINST (? IN NATURAL LANGUAGE MODE)"
                    + " + 10 * (LOWER(title) LIKE ?) + 3 * (kind = 'SPEX') AS score");
            params.add(against);
            params.add(String.join(" ", longWords));
            params.add(longWords.getFirst() + "%");
            sql.append(" FROM search_document WHERE MATCH(title, context, body) AGAINST (? IN BOOLEAN MODE)");
            params.add(against);
        } else {
            sql.append(", 0 AS score FROM search_document WHERE 1 = 1");
        }
        for (final String word : shortWords) {
            sql.append(" AND (LOWER(title) LIKE ? OR LOWER(context) LIKE ?)");
            params.add("%" + word + "%");
            params.add("%" + word + "%");
        }
        sql.append(" AND visibility IN (").append(String.join(",", visibilities.stream().map(_ -> "?").toList())).append(")");
        params.addAll(visibilities);
        sql.append(" ORDER BY score DESC, title LIMIT ?");
        params.add(Math.clamp(limit, 1, 200));

        JdbcClient.StatementSpec statement = jdbc.sql(sql.toString());

        for (final Object param : params) {
            statement = statement.param(param);
        }
        return statement.query((rs, _) -> new Hit(SearchKind.valueOf(rs.getString(1)), UUID.fromString(rs.getString(2)),
                rs.getString(3), rs.getString(4), rs.getString(5))).list();
    }

    /**
     * Every public page with when it last changed, for the sitemap.
     */
    @Transactional(readOnly = true)
    public List<String> publicPaths() {
        return jdbc.sql("SELECT path FROM search_document WHERE visibility = 'PUBLIC' AND kind IN ('SPEX', 'FIGURE', 'RECORDING', 'ARTICLE') ORDER BY path")
                .query(String.class).list();
    }

    private record Document(SearchKind kind, UUID id, String title, @Nullable String context, @Nullable String body,
                            String path, String visibility) {
    }

    public record Hit(SearchKind kind, UUID id, String title, @Nullable String context, String path) {
    }
}
