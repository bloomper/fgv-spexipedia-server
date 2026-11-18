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

import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexCategoryDto;
import nu.fgv.spexipedia.server.kind.Kind;
import nu.fgv.spexipedia.server.kind.KindType;
import nu.fgv.spexipedia.server.kind.KindUse;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The spex package template (content-import.md, 5.1): the sheets with their columns, a row per fact kind in
 * {@code Spex} and a column per credit kind in {@code Nummer}, drop-down lists of the categories and number kinds,
 * and the instructions with the zip's folders and the kinds they take. Made from the kinds as they are now.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class PackageTemplate {

    private static final int ROWS = 500;

    private PackageTemplate() {
    }

    public static byte[] create(final List<SpexCategoryDto> categories, final Function<KindType, List<Kind>> kinds,
                                final Function<KindUse, List<Kind>> imageKinds) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final CellStyle bold = workbook.createCellStyle();
            final Font font = workbook.createFont();

            font.setBold(true);
            bold.setFont(font);

            final List<String> numberKinds = names(kinds.apply(KindType.NUMBER));
            final List<String> creditKinds = names(kinds.apply(KindType.CREDIT));
            final List<String> factKinds = names(kinds.apply(KindType.FACT));
            final Sheet lists = workbook.createSheet("Listor");

            list(workbook, lists, 0, "Kategorier", categories.stream().map(SpexCategoryDto::name).toList());
            list(workbook, lists, 1, "Nummertyper", numberKinds);
            list(workbook, lists, 2, "JaNej", List.of("Ja", "Nej"));

            instructions(workbook.createSheet(PackageColumns.INSTRUCTIONS), bold, creditKinds, imageKinds);

            // Spex: a field and its value per row, then a row per fact kind
            final Sheet spex = workbook.createSheet(PackageColumns.SPEX);

            header(spex, bold, List.of("Fält", "Värde"));

            int r = 1;
            int categoryRow = -1;

            for (final Map.Entry<String, List<String>> field : PackageColumns.SPEX_FIELDS.entrySet()) {
                if ("category".equals(field.getKey())) {
                    categoryRow = r;
                }
                spex.createRow(r++).createCell(0).setCellValue(field.getValue().getFirst());
            }
            for (final String fact : factKinds) {
                spex.createRow(r++).createCell(0).setCellValue(fact);
            }
            spex.setColumnWidth(0, 30 * 256);
            spex.setColumnWidth(1, 80 * 256);
            if (categoryRow > 0 && !categories.isEmpty()) {
                dropDown(spex, "Kategorier", categoryRow, categoryRow, 1);
            }

            header(workbook.createSheet(PackageColumns.CHARACTERS), bold, firsts(PackageColumns.CHARACTER_COLUMNS));

            final Sheet scenes = workbook.createSheet(PackageColumns.SCENES);
            final List<String> sceneColumns = firsts(PackageColumns.SCENE_COLUMNS);

            header(scenes, bold, sceneColumns);
            dropDown(scenes, "JaNej", 1, ROWS, sceneColumns.size() - 1);

            // Nummer: the template's columns, a column per credit kind before Karaktärer
            final Sheet numbers = workbook.createSheet(PackageColumns.NUMBERS);
            final List<String> numberColumns = new ArrayList<>(firsts(PackageColumns.NUMBER_COLUMNS));

            numberColumns.addAll(numberColumns.size() - 1, creditKinds);
            header(numbers, bold, numberColumns);
            if (!numberKinds.isEmpty()) {
                dropDown(numbers, "Nummertyper", 1, ROWS, numberColumns.indexOf(PackageColumns.NUMBER_COLUMNS.get("kind").getFirst()));
            }

            workbook.setSheetHidden(workbook.getSheetIndex(lists), true);
            workbook.setActiveSheet(workbook.getSheetIndex(spex));
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void instructions(final Sheet sheet, final CellStyle bold, final List<String> creditKinds,
                                     final Function<KindUse, List<Kind>> imageKinds) {
        final List<String[]> lines = new ArrayList<>();

        lines.add(new String[]{"Spexpaket", ""});
        lines.add(new String[]{"", "Fyll i bladen Spex, Karaktärer, Scener och Nummer. Ett blad som är tomt ändrar ingenting."});
        lines.add(new String[]{"", "Ladda upp kalkylarket i Spexipedia, med en zip-fil om du har filer. Du får se vad som skapas i varje applikation innan något händer."});
        lines.add(new String[]{"", "Namn i kolumnerna för medverkande (" + String.join(", ", creditKinds) + ") skiljs åt med kommatecken."});
        lines.add(new String[]{"", ""});
        lines.add(new String[]{"Zip-filens mappar", ""});
        lines.add(new String[]{"Affisch/", "Affischen (Spexipedia)"});
        lines.add(new String[]{"Rebus/, Rebus/Lösning/", "Rebusen och dess lösning"});
        lines.add(new String[]{"Väst/", "Västbilder"});
        lines.add(new String[]{"Scenografi/<bildtyp>/", "Scenografi; bildtyper: " + String.join(", ", names(imageKinds.apply(KindUse.SCENOGRAPHY)))});
        lines.add(new String[]{"Karaktärer/<karaktär>/<bildtyp>/", "Karaktärsbilder; bildtyper: " + String.join(", ", names(imageKinds.apply(KindUse.CHARACTER)))});
        lines.add(new String[]{"Bilder/", "Övriga bilder på sidan"});
        lines.add(new String[]{"<dokumenttyp>/, t.ex. Manus/, Program/", "Ett dokument av den typen i Spexarkivet"});
        lines.add(new String[]{"Arrangemang/<nummer>/", "Ett arrangemang av numret (ordning eller titel) i Spexarkivet"});
        lines.add(new String[]{"Inspelningar/", "Ett album i Spexify; filer som heter \"<ordning> <titel>\" blir spår för de numren"});
        lines.add(new String[]{"Video/<videotyp>/", "Videor i Spexflix"});
        lines.add(new String[]{"Foton/<albumtyp>/<albumtitel>/", "Ett album med foton och filmer i Spexgram"});

        for (int i = 0; i < lines.size(); i++) {
            final Row row = sheet.createRow(i);

            row.createCell(0).setCellValue(lines.get(i)[0]);
            row.createCell(1).setCellValue(lines.get(i)[1]);
            if (!lines.get(i)[0].isEmpty() && lines.get(i)[1].isEmpty()) {
                row.getCell(0).setCellStyle(bold);
            }
        }
        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 110 * 256);
    }

    private static void header(final Sheet sheet, final CellStyle bold, final List<String> columns) {
        final Row row = sheet.createRow(0);

        for (int c = 0; c < columns.size(); c++) {
            row.createCell(c).setCellValue(columns.get(c));
            row.getCell(c).setCellStyle(bold);
            sheet.setColumnWidth(c, Math.max(12, columns.get(c).length() + 4) * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    private static void list(final XSSFWorkbook workbook, final Sheet lists, final int column, final String name, final List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            final Row row = lists.getRow(i) == null ? lists.createRow(i) : lists.getRow(i);

            row.createCell(column).setCellValue(values.get(i));
        }
        if (!values.isEmpty()) {
            final Name named = workbook.createName();
            final char letter = (char) ('A' + column);

            named.setNameName(name);
            named.setRefersToFormula("Listor!$" + letter + "$1:$" + letter + "$" + values.size());
        }
    }

    private static void dropDown(final Sheet sheet, final String list, final int firstRow, final int lastRow, final int column) {
        final DataValidationHelper helper = sheet.getDataValidationHelper();
        final DataValidation validation = helper.createValidation(helper.createFormulaListConstraint(list),
                new CellRangeAddressList(firstRow, lastRow, column, column));

        validation.setShowErrorBox(false);
        sheet.addValidationData(validation);
    }

    private static List<String> names(final List<Kind> kinds) {
        return kinds.stream().filter(k -> !k.isRetired()).map(Kind::getNameSv).toList();
    }

    private static List<String> firsts(final Map<String, List<String>> columns) {
        return columns.values().stream().map(List::getFirst).toList();
    }
}
