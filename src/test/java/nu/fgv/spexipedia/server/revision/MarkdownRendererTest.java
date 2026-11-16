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

package nu.fgv.spexipedia.server.revision;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void rawHtmlIsEscaped() {
        assertThat(renderer.render("<script>alert(1)</script>\n\nHej <img src=x onerror=alert(1)> **fet**", _ -> null))
                .doesNotContain("<script>")
                .contains("&lt;script&gt;")
                .doesNotContain("<img")
                .contains("<strong>fet</strong>");
    }

    @Test
    void onlySafeLinksAreKept() {
        assertThat(renderer.render("[x](javascript:alert(1)) [y](https://fgv.nu) [z](/spex/1984-lasse-maja) [m](mailto:a@b.se)", _ -> null))
                .doesNotContain("javascript:")
                .contains("href=\"https://fgv.nu\"")
                .contains("href=\"/spex/1984-lasse-maja\"")
                .contains("href=\"mailto:a@b.se\"");
    }

    @Test
    void wikiLinksGoToArticlesOrToASearch() {
        final String html = renderer.render("Se [[Spexets traditioner]] och [[Okänd sida|den här]].",
                title -> title.equals("Spexets traditioner") ? "/articles/spexets-traditioner" : null);

        assertThat(html).contains("<a href=\"/articles/spexets-traditioner\">Spexets traditioner</a>")
                .contains("href=\"/search?q=Ok%C3%A4nd+sida\">den här</a>");
    }

    @Test
    void tablesAreRendered() {
        assertThat(renderer.render("| A | B |\n|---|---|\n| 1 | 2 |", _ -> null)).contains("<table>").contains("<td>2</td>");
    }

    @Test
    void plainTextHasNoMarkup() {
        assertThat(renderer.plain("**Bojan** och [[Adrianpokalen]]")).isEqualTo("Bojan och Adrianpokalen");
    }
}
