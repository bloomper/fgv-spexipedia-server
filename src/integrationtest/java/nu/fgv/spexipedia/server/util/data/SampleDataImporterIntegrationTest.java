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

package nu.fgv.spexipedia.server.util.data;

import nu.fgv.spexipedia.server.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The small sample data, built on the base data the migration seeds, with what the content migration brings in: facts,
 * kinds, an unresolved credit, a record from the song register and a spex book whose tracks are the spex' numbers.
 */
class SampleDataImporterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SampleDataImporter importer;

    @Test
    void buildsOnTheSeededBaseData() {
        seedBaseData();
        importer.importSampleData();

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM spex WHERE created_by = 'system'").query(Long.class).single()).isEqualTo(SEEDED_SPEX);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM spex_fact").query(Long.class).single()).isPositive();
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM number_credit WHERE unresolved = 1").query(Long.class).single()).isPositive();
        assertThat(jdbcClient.sql("""
                SELECT COUNT(*) FROM recording_track t JOIN recording r ON r.id = t.recording_id JOIN kind k ON k.id = r.medium_id
                WHERE k.uuid = ? AND t.number_id IS NOT NULL""").param(DefaultKinds.SPEX_BOOK.toString()).query(Long.class).single())
                .isGreaterThan(10);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM recording WHERE catalogue_number = 'CCD 004' AND description IS NULL")
                .query(Long.class).single()).isEqualTo(1);

        // Again: the seeded spex stay, without the posters and the examples the first import added
        importer.importSampleData();

        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM spex").query(Long.class).single()).isEqualTo(SEEDED_SPEX + 1);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM spex_category").query(Long.class).single()).isEqualTo(5 + 1);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM spex WHERE poster_id IS NOT NULL AND poster_id NOT IN (SELECT id FROM image)")
                .query(Long.class).single()).isZero();
    }
}
