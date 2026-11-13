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
