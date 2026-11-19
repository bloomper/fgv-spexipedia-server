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

    public record FeedNumber(UUID id, UUID spexId, int act, int position, String title, @Nullable String originalTitle,
                             @Nullable String originalArtist, boolean retired) {
    }
}
