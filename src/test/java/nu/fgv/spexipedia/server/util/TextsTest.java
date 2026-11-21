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

import nu.fgv.spexipedia.server.util.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextsTest {

    @Test
    void textIsStrippedAndBlankIsNothing() {
        assertThat(Texts.clean("  Bojan \n")).isEqualTo("Bojan");
        assertThat(Texts.clean("   ")).isNull();
        assertThat(Texts.clean(null)).isNull();
    }

    @Test
    void requiredTextMustBeThereAndFit() {
        assertThat(Texts.required(" Lasse-Maja ", 10, "title")).isEqualTo("Lasse-Maja");
        assertThatThrownBy(() -> Texts.required(" ", 10, "title")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Texts.required(null, 10, "title")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Texts.required("Lasse-Maja!", 10, "title")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void optionalTextMayBeMissingButMustFit() {
        assertThat(Texts.optional(" ", 10, "place")).isNull();
        assertThat(Texts.optional(" Paris ", 10, "place")).isEqualTo("Paris");
        // The length is counted after stripping
        assertThat(Texts.optional("  1234567890  ", 10, "place")).isEqualTo("1234567890");
        assertThatThrownBy(() -> Texts.optional("12345678901", 10, "place")).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1948", "1999", "2000", " 2026 "})
    void aYearHasFourDigitsInThisOrTheLastCentury(final String year) {
        assertThat(Texts.year(year)).isEqualTo(year.strip());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "84", "1899", "2100", "19845", "198a", "1984-85"})
    void otherYearsAreRefused(final String year) {
        assertThatThrownBy(() -> Texts.year(year)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void aMissingYearIsRefused() {
        assertThatThrownBy(() -> Texts.year(null)).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1984", "1984-03", "1984-03-17", "2026-12-31"})
    void aDateMayStopAtTheYearOrTheMonth(final String date) {
        assertThat(Texts.partialDate(date)).isEqualTo(date);
    }

    @ParameterizedTest
    @ValueSource(strings = {"84", "1984-13", "1984-00", "1984-3", "1984-03-32", "1984-03-00", "1984/03/17", "17 mars 1984"})
    void otherDatesAreRefused(final String date) {
        assertThatThrownBy(() -> Texts.partialDate(date)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void noDateIsFine() {
        assertThat(Texts.partialDate(null)).isNull();
        assertThat(Texts.partialDate("  ")).isNull();
    }
}
