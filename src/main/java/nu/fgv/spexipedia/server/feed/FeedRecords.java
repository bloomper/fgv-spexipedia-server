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

package nu.fgv.spexipedia.server.feed;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The records in the change feed, as they were right after a change. They are what other applications copy, and
 * nothing more.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class FeedRecords {

    private FeedRecords() {
    }

    public record FeedSpexCategory(UUID id, String name, String firstYear, @Nullable String logoUrl, boolean retired) {
    }

    public record FeedSpex(UUID id, String year, String title, @Nullable UUID categoryId, @Nullable UUID parentId,
                           @Nullable String posterUrl, boolean retired) {
    }

    public record FeedTaskCategory(UUID id, String name, boolean actorPresent, boolean instruments, boolean retired) {
    }

    public record FeedTask(UUID id, String name, @Nullable UUID categoryId, boolean board, boolean retired) {
    }

    public record FeedCharacter(UUID id, UUID spexId, String name, int position, boolean retired) {
    }

    public record FeedNumber(UUID id, UUID spexId, int act, int position, String title, @Nullable UUID kindId,
                             @Nullable String originalTitle, @Nullable String originalArtist, boolean retired) {
    }

    /**
     * The numbers with the given keys as the feed gives them, e.g. after a change made in SQL.
     */
    public static List<FeedNumber> numbers(final JdbcClient jdbc, final Collection<Long> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT n.uuid, s.uuid AS spex, n.act, n.position, n.title, k.uuid AS kind, n.original_title, n.original_artist, n.retired
                        FROM spex_number n JOIN spex s ON s.id = n.spex_id LEFT JOIN kind k ON k.id = n.kind_id
                        WHERE n.id IN (:ids) ORDER BY n.id""")
                .param("ids", keys)
                .query((rs, _) -> new FeedNumber(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                        rs.getInt(3), rs.getInt(4), rs.getString(5), rs.getString(6) == null ? null : UUID.fromString(rs.getString(6)),
                        rs.getString(7), rs.getString(8), rs.getBoolean(9)))
                .list();
    }
}
