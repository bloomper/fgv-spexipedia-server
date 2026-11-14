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

package nu.fgv.spexipedia.server.util;

import nu.fgv.spexipedia.server.util.error.BadRequestException;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Reading ids from the API, where an id may also be a slug.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class Ids {

    private Ids() {
    }

    /**
     * The UUID, or empty when the text is not one (e.g. a slug).
     */
    public static Optional<UUID> parse(final @Nullable String id) {
        if (id == null || id.length() != 36) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(id));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * The UUID, or a bad request.
     */
    public static UUID require(final @Nullable String id) {
        return parse(id).orElseThrow(() -> new BadRequestException("error.id.invalid", String.valueOf(id)));
    }

    public static @Nullable UUID optional(final @Nullable String id) {
        return id == null || id.isBlank() ? null : require(id);
    }
}
