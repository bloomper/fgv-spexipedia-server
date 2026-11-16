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

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Articles as the API shows them.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class ArticleDtos {

    private ArticleDtos() {
    }

    public record ArticleDto(long key, UUID id, String slug, String title, @Nullable String body, Visibility visibility,
                             boolean published, List<Long> categoryKeys, @Nullable Instant trashedAt, Instant createdAt,
                             @Nullable Instant lastModifiedAt) {
    }

    public record ArticleCategoryDto(long key, UUID id, String slug, String name, @Nullable String description,
                                     int articleCount) {
    }
}
