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

package nu.fgv.spexipedia.server.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void swedishAndOtherLettersAreFolded() {
        assertThat(Slugs.of("1984 Lasse-Maja")).isEqualTo("1984-lasse-maja");
        assertThat(Slugs.of("Bröderna Lumière")).isEqualTo("broderna-lumiere");
        assertThat(Slugs.of("Cæsarion")).isEqualTo("caesarion");
        assertThat(Slugs.of("  Åsa & Örjan!  ")).isEqualTo("asa-orjan");
    }

    @Test
    void aTakenSlugGetsANumber() {
        final Set<String> taken = Set.of("1950-caesarion", "1950-caesarion-2");

        assertThat(Slugs.unique("1950 Cæsarion", "x", taken::contains)).isEqualTo("1950-caesarion-3");
        assertThat(Slugs.unique("!!!", "fallback", taken::contains)).isEqualTo("fallback");
    }

    @Test
    void foldingIgnoresCaseDiacriticsAndPunctuation() {
        assertThat(Slugs.fold("Kärleksduett i månsken")).isEqualTo(Slugs.fold("KARLEKSDUETT I MANSKEN!"));
    }
}
