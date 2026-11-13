package nu.fgv.spexipedia.server.article;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositories of articles and their categories.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class ArticleRepositories {

    private ArticleRepositories() {
    }

    public interface ArticleRepository extends JpaRepository<Article, Long> {
        Optional<Article> findByUuid(UUID uuid);

        Optional<Article> findBySlug(String slug);

        boolean existsBySlug(String slug);

        @Query("SELECT a FROM Article a WHERE LOWER(a.title) = LOWER(:title) AND a.trashedAt IS NULL")
        List<Article> findByTitle(String title);

        @Query("SELECT DISTINCT a FROM Article a LEFT JOIN FETCH a.categories WHERE a.trashedAt IS NULL ORDER BY a.title")
        List<Article> findAllLive();

        List<Article> findByTrashedAtIsNotNullOrderByTrashedAtDesc();
    }

    public interface ArticleCategoryRepository extends JpaRepository<ArticleCategory, Long> {
        Optional<ArticleCategory> findByUuid(UUID uuid);

        Optional<ArticleCategory> findBySlug(String slug);

        boolean existsBySlug(String slug);

        List<ArticleCategory> findAllByOrderByNameAsc();
    }
}
