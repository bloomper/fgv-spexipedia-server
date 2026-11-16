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

        @Query("SELECT a FROM Article a WHERE a.parent = :parent AND a.trashedAt IS NULL ORDER BY a.position, a.title")
        List<Article> findChildren(Article parent);

        @Query("SELECT a FROM Article a WHERE a.parent IS NULL AND a.trashedAt IS NULL ORDER BY a.position, a.title")
        List<Article> findRoots();

        @Query("SELECT COALESCE(MAX(a.position), 0) FROM Article a WHERE a.parent = :parent")
        int maxPosition(Article parent);

        @Query("SELECT COALESCE(MAX(a.position), 0) FROM Article a WHERE a.parent IS NULL")
        int maxRootPosition();
    }

    public interface ArticleCategoryRepository extends JpaRepository<ArticleCategory, Long> {
        Optional<ArticleCategory> findByUuid(UUID uuid);

        Optional<ArticleCategory> findBySlug(String slug);

        boolean existsBySlug(String slug);

        List<ArticleCategory> findAllByOrderByNameAsc();

        @Query("SELECT c FROM ArticleCategory c WHERE LOWER(c.name) = LOWER(:name)")
        List<ArticleCategory> findByName(String name);
    }
}
