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

package nu.fgv.spexipedia.server.spexpackage;

import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.util.Locales;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The template's sheets and their columns (content-import.md, 5.1): each with its Swedish name first, as the template
 * writes it, then the other names it is recognised by. The archive's own spreadsheets use some of these, so an old
 * {@code Musik - <spex>.xlsx} can be pasted into {@code Nummer} as it is. Columns of kinds (a credit kind per column
 * in {@code Nummer}, a fact kind per row in {@code Spex}) come from the kinds, not from here.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class PackageColumns {

    public static final String SPEX = "Spex";
    public static final String CHARACTERS = "Karaktärer";
    public static final String SCENES = "Scener";
    public static final String NUMBERS = "Nummer";
    public static final String INSTRUCTIONS = "Instruktioner";
    public static final String LISTS = "Listor";

    /**
     * The sheets by their Swedish name, with the names they are recognised by: Swedish first, then English.
     */
    public static final Map<String, List<String>> SHEETS = ordered(
            SPEX, List.of(SPEX, "Spex"),
            CHARACTERS, List.of(CHARACTERS, "Characters"),
            SCENES, List.of(SCENES, "Scenes"),
            NUMBERS, List.of(NUMBERS, "Numbers"),
            INSTRUCTIONS, List.of(INSTRUCTIONS, "Instructions"),
            LISTS, List.of(LISTS, "Lists"));

    /**
     * The rows of the Spex sheet, by field.
     */
    public static final Map<String, List<String>> SPEX_FIELDS = ordered(
            "title", List.of("Titel", "Title"),
            "year", List.of("År", "Year"),
            "category", List.of("Kategori", "Category"),
            "parent", List.of("Nyuppsättning av", "Revival of", "Original"),
            "introduction", List.of("Introduktion", "Introduction"),
            "shortDescription", List.of("Kort beskrivning", "Short description", "Spexbeskrivning - kort"),
            "description", List.of("Beskrivning", "Description", "Spexbeskrivning"),
            "synopsis", List.of("Synopsis", "Synopsis", "Handling"),
            "premiere", List.of("Premiär", "Premiere"));

    public static final Map<String, List<String>> CHARACTER_COLUMNS = ordered(
            "name", List.of("Namn", "Name", "Roll", "Karaktär"),
            "description", List.of("Beskrivning", "Description"));

    public static final Map<String, List<String>> SCENE_COLUMNS = ordered(
            "act", List.of("Akt", "Act"),
            "title", List.of("Titel", "Title", "Scen"),
            "place", List.of("Plats", "Place"),
            "intervalAfter", List.of("Paus efter", "Interval after", "Paus"));

    public static final Map<String, List<String>> NUMBER_COLUMNS = ordered(
            "act", List.of("Akt", "Act"),
            "position", List.of("Ordning", "Position", "Nr"),
            "title", List.of("Titel", "Title"),
            "kind", List.of("Typ", "Kind"),
            "originalTitle", List.of("Originalsång", "Original song", "Orginalsång"),
            "originalArtist", List.of("Originalartist", "Original artist"),
            "fromWork", List.of("Ur", "From"),
            "firstLine", List.of("Första strofen", "First line", "Första raden"),
            "characters", List.of("Karaktärer", "Characters"));

    /**
     * The zip's top folders (content-import.md, 5.2), by what they become, with the names they are recognised by. A
     * top folder that is none of these is a document kind of Spexarkivet, e.g. {@code Manus/} or {@code Program/}.
     */
    public static final Map<String, List<String>> FOLDERS = ordered(
            "poster", List.of("Affisch", "Poster"),
            "rebus", List.of("Rebus"),
            "vest", List.of("Väst", "Vest"),
            "characters", List.of("Karaktärer", "Characters"),
            "arrangements", List.of("Arrangemang", "Arrangements"),
            "recordings", List.of("Inspelningar", "Recordings", "Ljudinspelning"),
            "videos", List.of("Video", "Videos", "Videoinspelning"),
            "photos", List.of("Foton", "Photos"));

    /**
     * The rebus' subfolder for its solution.
     */
    public static final List<String> SOLUTION = List.of("Lösning", "Solution");

    /**
     * Columns of the archive's spreadsheets that a package does not use.
     */
    public static final List<String> IGNORED = List.of("Tillstånd", "Övrigt (mapp)");

    private PackageColumns() {
    }

    /**
     * The name to write in the locale's language: the first for Swedish, the second for English.
     */
    public static String name(final List<String> names, final Locale locale) {
        return Locale.ENGLISH.equals(Locales.supported(locale)) && names.size() > 1 ? names.get(1) : names.getFirst();
    }

    /**
     * The field the header names, or null.
     */
    public static String field(final Map<String, List<String>> fields, final String header) {
        final String wanted = KindService.normalize(header);

        for (final Map.Entry<String, List<String>> field : fields.entrySet()) {
            if (field.getValue().stream().map(KindService::normalize).anyMatch(wanted::equals)) {
                return field.getKey();
            }
        }
        return null;
    }

    public static boolean ignored(final String header) {
        final String wanted = KindService.normalize(header);

        return wanted.isEmpty() || wanted.endsWith(" fil") || IGNORED.stream().map(KindService::normalize).anyMatch(wanted::equals);
    }

    private static Map<String, List<String>> ordered(final Object... pairs) {
        final Map<String, List<String>> map = new LinkedHashMap<>();

        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked") final List<String> names = (List<String>) pairs[i + 1];

            map.put((String) pairs[i], names);
        }
        return map;
    }
}
