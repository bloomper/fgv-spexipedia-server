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

package nu.fgv.spexipedia.server.kind;

/**
 * What a kind is for. The types are fixed by the code; the kinds of each type are data kept by admins (spec 4.6).
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public enum KindType {
    /** Kinds of numbers: song, overture, scene change … */
    NUMBER,
    /** Kinds of credits on numbers: lyrics, music, arrangement, choreography … */
    CREDIT,
    /** Image kinds, within a {@link KindUse}. */
    IMAGE,
    /** Facts on spex pages: venue, number of performances … */
    FACT
}
