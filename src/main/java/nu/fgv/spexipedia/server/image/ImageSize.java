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

/**
 * The variants made of every image, as WebP and JPEG; none is wider or taller than the original.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public enum ImageSize {
    THUMBNAIL(320),
    PAGE(1280),
    FULL(2560);

    private final int size;

    ImageSize(final int size) {
        this.size = size;
    }

    public int size() {
        return size;
    }

    public String fileName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
