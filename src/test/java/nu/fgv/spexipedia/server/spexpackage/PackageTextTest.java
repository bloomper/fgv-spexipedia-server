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

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class PackageTextTest {

    private static final ResourceBundleMessageSource MESSAGES = new ResourceBundleMessageSource();

    static {
        MESSAGES.setBasename("messages");
        MESSAGES.setDefaultEncoding("UTF-8");
        MESSAGES.setFallbackToSystemLocale(false);
    }

    @Test
    void inTheReadersLanguageSwedishUnlessEnglish() {
        final PackageText text = PackageText.of("unmatched.noCharacter", "Karaktärer/Okänd/Skiss/a.png", "Okänd");

        assertThat(text.in(MESSAGES, Locale.of("sv"))).isEqualTo("Karaktärer/Okänd/Skiss/a.png: ingen karaktär i bladet Karaktärer heter Okänd.");
        assertThat(text.in(MESSAGES, null)).startsWith("Karaktärer/Okänd/Skiss/a.png: ingen karaktär");
        assertThat(text.in(MESSAGES, Locale.GERMAN)).startsWith("Karaktärer/Okänd/Skiss/a.png: ingen karaktär");
        assertThat(text.in(MESSAGES, Locale.ENGLISH)).isEqualTo("Karaktärer/Okänd/Skiss/a.png: no character of the Karaktärer sheet is called Okänd.");
        assertThat(PackageText.of("app.deferred").in(MESSAGES, Locale.of("sv"))).startsWith("Kontrolleras när importen tillämpas");
        assertThat(PackageText.literal("Connection refused").in(MESSAGES, Locale.of("sv"))).isEqualTo("Connection refused");
    }

    @Test
    void storedInTheMessageColumnAndReadBack() {
        final PackageText text = PackageText.of("failed.spreadsheet", "Kan inte: fel {0} 'x'");

        assertThat(PackageText.decode(text.encode())).isEqualTo(text);
        assertThat(PackageText.decode(PackageText.of("progress.reading").encode())).isEqualTo(PackageText.of("progress.reading"));
        // Text written before the codes, or by something else, is shown as it is
        assertThat(PackageText.decode("Läser kalkylarket")).isEqualTo(PackageText.literal("Läser kalkylarket"));
        assertThat(PackageText.decode(text.encode()).in(MESSAGES, Locale.ENGLISH)).isEqualTo("The spreadsheet could not be read: Kan inte: fel {0} 'x'");
    }

    @Test
    void reportsKeepTheirCodesAndReadOldPlainText() {
        final JsonMapper json = JsonMapper.builder().build();
        final PackageReport report = new PackageReport(List.of(new PackageReport.AppReport("SPEXIFY", "DEFERRED", 5, 4, 0, 0, 0, 0, 0,
                PackageText.of("app.deferred"), null, List.of())), List.of(PackageText.of("unmatched.top", "Läs mig.txt")), List.of());

        assertThat(json.readValue(json.writeValueAsString(report), PackageReport.class)).isEqualTo(report);

        final PackageReport old = json.readValue("""
                {"apps": [{"app": "SPEXIFY", "status": "DEFERRED", "records": 5, "files": 4, "created": 0, "updated": 0, "unchanged": 0,
                           "kept": 0, "failed": 0, "message": "checked when applied", "webUrl": null, "items": []}],
                 "unmatched": ["Läs mig.txt: goes nowhere"], "warnings": []}
                """, PackageReport.class);

        assertThat(old.unmatched()).containsExactly(PackageText.literal("Läs mig.txt: goes nowhere"));
        assertThat(old.apps().getFirst().message()).isEqualTo(PackageText.literal("checked when applied"));
    }
}
