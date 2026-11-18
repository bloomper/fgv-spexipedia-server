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

package nu.fgv.spexipedia.server.basedata;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FGV's spex, revivals, categories and tasks as V1_5 seeds them, and their change feed entries written at start.
 */
class SeededBaseDataIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SeededBaseData seeded;

    @Test
    void publishesEverySeededRecordOnceParentsFirst() {
        seedBaseData();

        assertThat(seeded.publish()).isEqualTo(5 + SEEDED_SPEX + 6 + 144);
        assertThat(seeded.publish()).isZero();
        // Every revival's entry comes after its original's
        assertThat(jdbcClient.sql("""
                SELECT COUNT(*) FROM spex r JOIN spex p ON p.id = r.parent_id
                JOIN change_feed_entry fr ON fr.record_id = r.uuid JOIN change_feed_entry fp ON fp.record_id = p.uuid
                WHERE fr.sequence < fp.sequence""").query(Long.class).single()).isZero();
    }

    @Test
    void seedsTheSpexAsCheckedAgainstTheArchiveAndTheWiki() {
        seedBaseData();

        assertThat(revivalYears("Cæsarion", "1950")).isEqualTo("1978,2010");
        assertThat(revivalYears("Cæsarion", "1953")).isEqualTo("2008");
        assertThat(revivalYears("Katarina II", "1959")).isEqualTo("1977,1989,2012");
        assertThat(revivalYears("Lasse-Maja", "1984")).isEqualTo("1994,2013");
        assertThat(revivalYears("Snorre", "1995")).isEqualTo("2022");
        assertThat(revivalYears("Krösus", "1992")).isEqualTo("2024");
        assertThat(jdbcClient.sql("SELECT slug FROM spex WHERE title = 'Gustaf E:son Vasa'").query(String.class).single()).isEqualTo("1955-gustaf-e-son-vasa");
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM task t JOIN task_category c ON c.id = t.category_id WHERE c.board = 1 AND t.name IN ('Ordförande', 'Skivocent', 'Festfixare', 'Vice Directeur')")
                .query(Long.class).single()).isEqualTo(4);
    }

    private String revivalYears(final String title, final String year) {
        return String.join(",", jdbcClient.sql("SELECT r.year FROM spex r JOIN spex p ON p.id = r.parent_id WHERE p.title = ? AND p.year = ? ORDER BY r.year")
                .param(title).param(year).query(String.class).list());
    }
}
