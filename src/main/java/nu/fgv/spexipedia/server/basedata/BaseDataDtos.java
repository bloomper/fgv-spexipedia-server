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

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Base data as the API shows it. The {@code key} fields are internal keys, for loading what a record refers to; they
 * are not in the schema.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class BaseDataDtos {

    private BaseDataDtos() {
    }

    public record SpexCategoryDto(long key, UUID id, String name, String firstYear, @Nullable String description,
                                  @Nullable Long logoKey, boolean retired, Instant createdAt,
                                  @Nullable Instant lastModifiedAt) {
    }

    /**
     * @param title       a revival's is its parent's
     * @param categoryKey a revival's is its parent's
     * @param posterKey   its own poster, else its parent's
     */
    public record SpexDto(long key, UUID id, String slug, String year, String title, @Nullable Long categoryKey,
                          @Nullable Long parentKey, @Nullable Long posterKey, boolean retired, Instant createdAt,
                          @Nullable Instant lastModifiedAt) {

        public boolean revival() {
            return parentKey != null;
        }

        public String label() {
            return "%s (%s)".formatted(title, year);
        }
    }

    public record TaskCategoryDto(long key, UUID id, String name, boolean actorPresent, boolean instruments, boolean retired) {
    }

    public record TaskDto(long key, UUID id, String name, long categoryKey, boolean board, boolean retired) {
    }

    public static SpexCategoryDto toDto(final SpexCategory category) {
        return new SpexCategoryDto(category.getId(), category.getUuid(), category.getName(), category.getFirstYear(),
                category.getDescription(), category.getLogo() == null ? null : category.getLogo().getId(), category.isRetired(),
                category.getCreatedAt(), category.getLastModifiedAt());
    }

    public static SpexDto toDto(final Spex spex) {
        final SpexCategory category = spex.effectiveCategory();

        return new SpexDto(spex.getId(), spex.getUuid(), spex.getSlug(), spex.getYear(), spex.effectiveTitle(),
                category == null ? null : category.getId(), spex.getParent() == null ? null : spex.getParent().getId(),
                spex.effectivePoster() == null ? null : spex.effectivePoster().getId(), spex.isRetired(),
                spex.getCreatedAt(), spex.getLastModifiedAt());
    }

    public static TaskCategoryDto toDto(final TaskCategory category) {
        return new TaskCategoryDto(category.getId(), category.getUuid(), category.getName(), category.isActorPresent(),
                category.isInstruments(), category.isRetired());
    }

    public static TaskDto toDto(final Task task) {
        return new TaskDto(task.getId(), task.getUuid(), task.getName(), task.getCategory().getId(), task.isBoard(), task.isRetired());
    }
}
