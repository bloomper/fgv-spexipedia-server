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
 * What a section of a spex page shows (spec 4.2): the content of a section kind. The contents are fixed by the code;
 * the section kinds, their names, subtitles and order are data.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public enum SectionContent {
    GROUP,
    CHARACTERS,
    /**
     * Images uploaded to Spexipedia, grouped by the section's own image kinds; admins add more image sections.
     */
    IMAGES,
    MANUSCRIPT,
    MUSIC,
    RECORDS,
    REBUS,
    VEST,
    BOARD,
    TASKS,
    LINKS,
    /**
     * A video in Spexflix (spec 4.2.1).
     */
    SPEXFLIX_VIDEO,
    /**
     * An album in Spexgram, or some of its items.
     */
    SPEXGRAM,
    /**
     * An album in Spexify.
     */
    SPEXIFY_ALBUM,
    /**
     * A document in Spexarkivet.
     */
    ARCHIVE_DOCUMENT;

    /**
     * Whether the content has exactly one section kind, which the first start creates and which is never created,
     * merged or deleted.
     */
    public boolean single() {
        return this != IMAGES && !external();
    }

    /**
     * Whether it shows material of another application, which an author picks per spex; such sections are not on
     * pages until added, and a page may have several of a kind.
     */
    public boolean external() {
        return this == SPEXFLIX_VIDEO || this == SPEXGRAM || this == SPEXIFY_ALBUM || this == ARCHIVE_DOCUMENT;
    }
}
