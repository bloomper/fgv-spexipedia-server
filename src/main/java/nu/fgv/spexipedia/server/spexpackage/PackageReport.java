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

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A package's preview or what applying it gave: per application what it created, changed and could not, and what in
 * the package matched nothing. Kept as JSON on the package.
 *
 * @param apps      per application, Spexipedia first
 * @param unmatched files of the zip and rows of the spreadsheet that go nowhere, and why
 * @param warnings  what the author should know, e.g. a field the template does not have
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public record PackageReport(List<AppReport> apps, List<PackageText> unmatched, List<PackageText> warnings) {

    /**
     * One application's part.
     *
     * @param app       SPEXIPEDIA, ARCHIVE, SPEXIFY, SPEXFLIX or SPEXGRAM
     * @param status    CHECKED (a dry run), APPLIED, DEFERRED (checked when applied: it needs the new spex),
     *                  UNAVAILABLE (not configured or not answering) or FAILED
     * @param records   how many records the package has for it
     * @param files     how many files go to it
     * @param message   why it is deferred, unavailable or failed
     * @param webUrl    the application, for links to what was created
     */
    public record AppReport(String app, String status, int records, int files, int created, int updated, int unchanged,
                            int kept, int failed, @Nullable PackageText message, @Nullable String webUrl, List<Item> items) {
    }

    /**
     * One record's outcome: CREATED, UPDATED, UNCHANGED, KEPT or FAILED, with the application's message.
     */
    public record Item(String kind, String sourceKey, @Nullable String id, String outcome, @Nullable String message,
                       List<String> warnings) {
    }
}
