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

package nu.fgv.spexipedia.server.image;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * An image as the API shows it, with its public addresses.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public record ImageDto(UUID id, ImageKind kind, @Nullable String caption, String alt, int width, int height,
                       String thumbnailUrl, String pageUrl, String fullUrl, String pageJpegUrl,
                       @Nullable String originalFileName, @Nullable Instant trashedAt) {

    /**
     * The same image, without what only authors see.
     */
    public ImageDto forPublic() {
        return new ImageDto(id, kind, caption, alt, width, height, thumbnailUrl, pageUrl, fullUrl, pageJpegUrl, null, trashedAt);
    }
}
