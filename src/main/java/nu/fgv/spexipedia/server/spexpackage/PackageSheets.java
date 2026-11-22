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

import nu.fgv.spexipedia.server.kind.Kind;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.kind.KindType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a package's spreadsheet (content-import.md, 5.1). A sheet that is missing or empty gives nothing; a column or
 * a field it does not know is reported as a warning, never guessed.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class PackageSheets {

    /** What the spreadsheet holds. {@code facts} are by fact kind name; {@code credits} by credit kind name. */
    public record Content(Map<String, String> spex, Map<String, String> facts, List<CharacterRow> characters,
                          List<SceneRow> scenes, List<NumberRow> numbers, List<PackageText> warnings) {

        public String title() {
            return spex.getOrDefault("title", "").strip();
        }

        public String year() {
            return spex.getOrDefault("year", "").strip().replaceFirst("\\.0+$", "");
        }
    }

    public record CharacterRow(int row, String name, @Nullable String description) {
    }

    public record SceneRow(int row, @Nullable Integer act, String title, @Nullable String place, boolean intervalAfter) {
    }

    public record NumberRow(int row, @Nullable Integer act, @Nullable Integer position, String title, @Nullable String kind,
                            Map<String, String> fields, Map<String, List<String>> credits, List<String> characters) {
    }

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);

    private PackageSheets() {
    }

    public static Content read(final Path xlsx, final KindService kinds) throws IOException {
        try (InputStream in = Files.newInputStream(xlsx); Workbook workbook = WorkbookFactory.create(in)) {
            final List<PackageText> warnings = new ArrayList<>();
            final Map<String, String> spex = new LinkedHashMap<>();
            final Map<String, String> facts = new LinkedHashMap<>();

            for (final List<String> row : rows(workbook, PackageColumns.SPEX)) {
                if (row.isEmpty() || row.getFirst().isBlank()) {
                    continue;
                }

                final String label = row.getFirst();
                final String value = row.size() > 1 ? row.get(1).strip() : "";
                final String field = PackageColumns.field(PackageColumns.SPEX_FIELDS, label);

                if (field != null) {
                    if (!value.isEmpty()) {
                        spex.put(field, value);
                    }
                } else if (KindService.normalize(label).equals(KindService.normalize("Fält"))
                        || KindService.normalize(label).equals("field")) {
                    continue;
                } else {
                    final Optional<Kind> fact = kinds.match(KindType.FACT, null, label);

                    if (fact.isPresent()) {
                        if (!value.isEmpty()) {
                            facts.put(fact.get().getNameSv(), value);
                        }
                    } else {
                        warnings.add(PackageText.of("warning.spexField", label));
                    }
                }
            }
            return new Content(spex, facts, characters(workbook, warnings), scenes(workbook, warnings),
                    numbers(workbook, kinds, warnings), warnings);
        }
    }

    private static List<CharacterRow> characters(final Workbook workbook, final List<PackageText> warnings) {
        final List<CharacterRow> characters = new ArrayList<>();
        final Table table = Table.of(rows(workbook, PackageColumns.CHARACTERS), PackageColumns.CHARACTER_COLUMNS);

        table.unknown().forEach(header -> warnings.add(PackageText.of("warning.column", PackageColumns.CHARACTERS, header)));
        for (int r = 0; r < table.rows().size(); r++) {
            final Map<String, String> row = table.rows().get(r);
            final String name = row.getOrDefault("name", "");

            if (!name.isBlank()) {
                characters.add(new CharacterRow(r + 2, name, blank(row.get("description"))));
            }
        }
        return characters;
    }

    private static List<SceneRow> scenes(final Workbook workbook, final List<PackageText> warnings) {
        final List<SceneRow> scenes = new ArrayList<>();
        final Table table = Table.of(rows(workbook, PackageColumns.SCENES), PackageColumns.SCENE_COLUMNS);

        table.unknown().forEach(header -> warnings.add(PackageText.of("warning.column", PackageColumns.SCENES, header)));
        for (int r = 0; r < table.rows().size(); r++) {
            final Map<String, String> row = table.rows().get(r);
            final String title = row.getOrDefault("title", "");

            if (!title.isBlank()) {
                scenes.add(new SceneRow(r + 2, number(row.get("act")), title, blank(row.get("place")), yes(row.get("intervalAfter"))));
            }
        }
        return scenes;
    }

    private static List<NumberRow> numbers(final Workbook workbook, final KindService kinds, final List<PackageText> warnings) {
        final List<List<String>> rows = rows(workbook, PackageColumns.NUMBERS);
        final List<NumberRow> numbers = new ArrayList<>();

        if (rows.isEmpty()) {
            return numbers;
        }

        // Columns: the template's, else a credit kind's, else ignored or reported
        final List<String> header = rows.getFirst();
        final Map<Integer, String> fields = new LinkedHashMap<>();
        final Map<Integer, String> credits = new LinkedHashMap<>();

        for (int c = 0; c < header.size(); c++) {
            final String name = header.get(c);
            final String field = PackageColumns.field(PackageColumns.NUMBER_COLUMNS, name);

            if (field != null) {
                fields.put(c, field);
            } else {
                final Optional<Kind> credit = kinds.match(KindType.CREDIT, null, name);

                if (credit.isPresent()) {
                    credits.put(c, credit.get().getNameSv());
                } else if (!PackageColumns.ignored(name)) {
                    warnings.add(PackageText.of("warning.numberColumn", PackageColumns.NUMBERS, name));
                }
            }
        }
        for (int r = 1; r < rows.size(); r++) {
            final List<String> row = rows.get(r);
            final Map<String, String> values = new LinkedHashMap<>();
            final Map<String, List<String>> names = new LinkedHashMap<>();

            fields.forEach((c, field) -> {
                if (c < row.size() && !row.get(c).isBlank()) {
                    values.put(field, row.get(c).strip());
                }
            });
            credits.forEach((c, kind) -> {
                if (c < row.size()) {
                    final List<String> list = names(row.get(c));

                    if (!list.isEmpty()) {
                        names.put(kind, list);
                    }
                }
            });

            final String title = values.getOrDefault("title", "");

            if (title.isBlank()) {
                continue;
            }

            final Map<String, String> rest = new LinkedHashMap<>(values);

            List.of("act", "position", "title", "kind", "characters").forEach(rest::remove);
            numbers.add(new NumberRow(r + 1, number(values.get("act")), number(values.get("position")), title, values.get("kind"),
                    rest, names, names(values.get("characters"))));
        }
        return numbers;
    }

    /** "Anna, Pelle och Lisa" as three names; "-" and "?" as none. */
    static List<String> names(final @Nullable String cell) {
        if (cell == null || cell.isBlank()) {
            return List.of();
        }
        return Arrays.stream(cell.split("\\s*(?:,|;|/|\\n|\\boch\\b|&)\\s*"))
                .map(String::strip).filter(name -> !name.isEmpty() && !name.matches("[-?]+")).toList();
    }

    private static List<List<String>> rows(final Workbook workbook, final String sheetName) {
        Sheet sheet = null;

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            if (KindService.normalize(workbook.getSheetName(i)).equals(KindService.normalize(sheetName))) {
                sheet = workbook.getSheetAt(i);
            }
        }

        final List<List<String>> rows = new ArrayList<>();

        if (sheet == null) {
            return rows;
        }
        for (final Row row : sheet) {
            final List<String> cells = new ArrayList<>();

            for (int c = 0; c < Math.max(0, row.getLastCellNum()); c++) {
                final Cell cell = row.getCell(c);

                cells.add(cell == null ? "" : FORMATTER.formatCellValue(cell).strip());
            }
            while (rows.size() < row.getRowNum()) {
                rows.add(new ArrayList<>());
            }
            rows.add(cells);
        }
        return rows;
    }

    private static @Nullable Integer number(final @Nullable String text) {
        if (text == null) {
            return null;
        }

        final String plain = text.strip().replaceFirst("[.,]0+$", "");

        return plain.matches("\\d{1,4}") ? Integer.valueOf(plain) : null;
    }

    private static boolean yes(final @Nullable String text) {
        final String value = KindService.normalize(text);

        return value.equals("ja") || value.equals("yes") || value.equals("x") || value.equals("1");
    }

    private static @Nullable String blank(final @Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** A sheet with a header row: each row by the field its column names; headers that name nothing. */
    private record Table(List<Map<String, String>> rows, List<String> unknown) {

        static Table of(final List<List<String>> rows, final Map<String, List<String>> columns) {
            final List<Map<String, String>> out = new ArrayList<>();
            final List<String> unknown = new ArrayList<>();

            if (rows.isEmpty()) {
                return new Table(out, unknown);
            }

            final List<String> header = rows.getFirst();
            final Map<Integer, String> fields = new LinkedHashMap<>();

            for (int c = 0; c < header.size(); c++) {
                final String field = PackageColumns.field(columns, header.get(c));

                if (field != null) {
                    fields.put(c, field);
                } else if (!PackageColumns.ignored(header.get(c))) {
                    unknown.add(header.get(c));
                }
            }
            for (int r = 1; r < rows.size(); r++) {
                final List<String> row = rows.get(r);
                final Map<String, String> values = new LinkedHashMap<>();

                fields.forEach((c, field) -> values.put(field, c < row.size() ? row.get(c) : ""));
                out.add(values);
            }
            return new Table(out, unknown);
        }
    }
}
