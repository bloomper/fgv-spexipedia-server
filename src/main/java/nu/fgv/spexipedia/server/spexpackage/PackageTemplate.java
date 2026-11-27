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
import nu.fgv.spexipedia.server.kind.SectionContent;
import nu.fgv.spexipedia.server.util.Locales;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.MessageSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The spex package template (content-import.md, 5.1): the sheets with their columns, a row per fact kind in
 * {@code Spex} and a column per credit kind in {@code Nummer}, drop-down lists of the categories and number kinds,
 * and the instructions with the zip's folders and the kinds they take. Made from the kinds as they are now, in the
 * downloader's language: sheet and column names, kind names and instructions. Either language's names are read back.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class PackageTemplate {

    private static final int ROWS = 500;

    private final MessageSource messages;
    private final Locale locale;
    private final boolean english;

    private PackageTemplate(final MessageSource messages, final Locale locale) {
        this.messages = messages;
        this.locale = locale;
        this.english = Locale.ENGLISH.equals(Locales.supported(locale));
    }

    public static byte[] create(final List<SpexCategoryDto> categories, final Function<KindType, List<Kind>> kinds,
                                final Function<KindUse, List<Kind>> imageKinds, final MessageSource messages,
                                final Locale locale) throws IOException {
        return new PackageTemplate(messages, locale).write(categories, kinds, imageKinds);
    }

    /**
     * The file name to offer, in the locale's language.
     */
    public static String fileName(final MessageSource messages, final Locale locale) {
        return messages.getMessage("package.template.fileName", null, Locales.supported(locale));
    }

    private byte[] write(final List<SpexCategoryDto> categories, final Function<KindType, List<Kind>> kinds,
                         final Function<KindUse, List<Kind>> imageKinds) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final CellStyle bold = workbook.createCellStyle();
            final Font font = workbook.createFont();

            font.setBold(true);
            bold.setFont(font);

            final List<String> numberKinds = names(kinds.apply(KindType.NUMBER));
            final List<String> creditKinds = names(kinds.apply(KindType.CREDIT));
            final List<String> factKinds = names(kinds.apply(KindType.FACT));
            final Sheet lists = workbook.createSheet(sheet(PackageColumns.LISTS));

            list(workbook, lists, 0, "Kategorier", categories.stream().map(SpexCategoryDto::name).toList());
            list(workbook, lists, 1, "Nummertyper", numberKinds);
            list(workbook, lists, 2, "JaNej", List.of(text("package.template.yes"), text("package.template.no")));

            instructions(workbook.createSheet(sheet(PackageColumns.INSTRUCTIONS)), bold, creditKinds, imageKinds,
                    kinds.apply(KindType.SECTION).stream().filter(kind -> kind.getContent() == SectionContent.IMAGES).toList());

            // Spex: a field and its value per row, then a row per fact kind
            final Sheet spex = workbook.createSheet(sheet(PackageColumns.SPEX));

            header(spex, bold, List.of(text("package.template.field"), text("package.template.value")));

            int r = 1;
            int categoryRow = -1;

            for (final Map.Entry<String, List<String>> field : PackageColumns.SPEX_FIELDS.entrySet()) {
                if ("category".equals(field.getKey())) {
                    categoryRow = r;
                }
                spex.createRow(r++).createCell(0).setCellValue(PackageColumns.name(field.getValue(), locale));
            }
            for (final String fact : factKinds) {
                spex.createRow(r++).createCell(0).setCellValue(fact);
            }
            spex.setColumnWidth(0, 30 * 256);
            spex.setColumnWidth(1, 80 * 256);
            if (categoryRow > 0 && !categories.isEmpty()) {
                dropDown(spex, "Kategorier", categoryRow, categoryRow, 1);
            }

            header(workbook.createSheet(sheet(PackageColumns.CHARACTERS)), bold, columns(PackageColumns.CHARACTER_COLUMNS));

            final Sheet scenes = workbook.createSheet(sheet(PackageColumns.SCENES));
            final List<String> sceneColumns = columns(PackageColumns.SCENE_COLUMNS);

            header(scenes, bold, sceneColumns);
            dropDown(scenes, "JaNej", 1, ROWS, sceneColumns.size() - 1);

            // Nummer: the template's columns, a column per credit kind before Karaktärer
            final Sheet numbers = workbook.createSheet(sheet(PackageColumns.NUMBERS));
            final List<String> numberColumns = new ArrayList<>(columns(PackageColumns.NUMBER_COLUMNS));

            numberColumns.addAll(numberColumns.size() - 1, creditKinds);
            header(numbers, bold, numberColumns);
            if (!numberKinds.isEmpty()) {
                dropDown(numbers, "Nummertyper", 1, ROWS, numberColumns.indexOf(PackageColumns.name(PackageColumns.NUMBER_COLUMNS.get("kind"), locale)));
            }

            workbook.setSheetHidden(workbook.getSheetIndex(lists), true);
            workbook.setActiveSheet(workbook.getSheetIndex(spex));
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void instructions(final Sheet sheet, final CellStyle bold, final List<String> creditKinds,
                              final Function<KindUse, List<Kind>> imageKinds, final List<Kind> imageSections) {
        final List<String[]> lines = new ArrayList<>();

        lines.add(new String[]{text("package.template.title"), ""});
        lines.add(new String[]{"", text("package.template.fill", sheet(PackageColumns.SPEX), sheet(PackageColumns.CHARACTERS),
                sheet(PackageColumns.SCENES), sheet(PackageColumns.NUMBERS))});
        lines.add(new String[]{"", text("package.template.upload")});
        lines.add(new String[]{"", text("package.template.credits", String.join(", ", creditKinds))});
        lines.add(new String[]{"", ""});
        lines.add(new String[]{text("package.template.folders"), ""});
        lines.add(new String[]{folder("poster") + "/", text("package.template.folder.poster")});
        lines.add(new String[]{folder("rebus") + "/, " + folder("rebus") + "/" + PackageColumns.name(PackageColumns.SOLUTION, locale) + "/",
                text("package.template.folder.rebus")});
        lines.add(new String[]{folder("vest") + "/", text("package.template.folder.vest")});
        // Image sections are data (spec 4.2): a folder per section, by its name, with a folder per image kind
        lines.add(new String[]{placeholder("imageSection") + "/" + placeholder("imageKind") + "/",
                text("package.template.folder.imageSections", String.join(", ", names(imageSections)))});
        lines.add(new String[]{folder("characters") + "/" + placeholder("character") + "/" + placeholder("imageKind") + "/",
                text("package.template.folder.characters", String.join(", ", names(imageKinds.apply(KindUse.CHARACTER))))});
        lines.add(new String[]{text("package.template.folder.documentsPath"), text("package.template.folder.documents")});
        lines.add(new String[]{folder("arrangements") + "/" + placeholder("number") + "/", text("package.template.folder.arrangements")});
        lines.add(new String[]{folder("recordings") + "/", text("package.template.folder.recordings")});
        lines.add(new String[]{folder("videos") + "/" + placeholder("videoKind") + "/", text("package.template.folder.videos")});
        lines.add(new String[]{folder("photos") + "/" + placeholder("albumKind") + "/" + placeholder("albumTitle") + "/",
                text("package.template.folder.photos")});

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

    private String text(final String code, final Object... args) {
        return messages.getMessage(code, args, Locales.supported(locale));
    }

    private String sheet(final String sheet) {
        return PackageColumns.name(PackageColumns.SHEETS.get(sheet), locale);
    }

    private String folder(final String folder) {
        return PackageColumns.name(PackageColumns.FOLDERS.get(folder), locale);
    }

    private String placeholder(final String what) {
        return "<" + text("package.template.placeholder." + what) + ">";
    }

    private List<String> columns(final Map<String, List<String>> columns) {
        return columns.values().stream().map(names -> PackageColumns.name(names, locale)).toList();
    }

    private List<String> names(final List<Kind> kinds) {
        return kinds.stream().filter(k -> !k.isRetired()).map(k -> english ? k.getNameEn() : k.getNameSv()).toList();
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
            named.setRefersToFormula("'" + lists.getSheetName() + "'!$" + letter + "$1:$" + letter + "$" + values.size());
        }
    }

    private static void dropDown(final Sheet sheet, final String list, final int firstRow, final int lastRow, final int column) {
        final DataValidationHelper helper = sheet.getDataValidationHelper();
        final DataValidation validation = helper.createValidation(helper.createFormulaListConstraint(list),
                new CellRangeAddressList(firstRow, lastRow, column, column));

        validation.setShowErrorBox(false);
        sheet.addValidationData(validation);
    }
}
