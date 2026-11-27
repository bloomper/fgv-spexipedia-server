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
 * What an image shows, which decides where it is offered. Images must not show identifiable people: photos of people
 * belong in Spexgram.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public enum ImageKind {
    POSTER,
    LOGO,
    /**
     * A character's images: sketches, make-up sketches, costume dolls (by the character image kinds).
     */
    CHARACTER,
    /**
     * Images of a section of a spex page: an image section (scenography …) or the vest.
     */
    SECTION,
    REBUS,
    COVER,
    ARTICLE,
    OTHER
}
