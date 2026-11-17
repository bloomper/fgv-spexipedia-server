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

package nu.fgv.spexipedia.server.content;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContentGraphqlApiTest {

    private static final String LYRICS = "01900000-0000-7000-8000-000000000201";
    private static final String SPEXARE = "01900000-0000-7000-8000-00000000a001";

    private static Map<String, Object> credit(final String spexareId, final String name, final Boolean unresolved) {
        final Map<String, Object> credit = new HashMap<>();

        credit.put("kindId", LYRICS);
        credit.put("spexareId", spexareId);
        credit.put("name", name);
        if (unresolved != null) {
            credit.put("unresolved", unresolved);
        }
        return credit;
    }

    @Test
    void creditsKeepWhetherTheyAreUnresolved() {
        final Map<String, Object> input = new HashMap<>(Map.of("act", 1, "title", "Entré"));

        input.put("credits", List.of(credit(SPEXARE, null, null), credit(null, "Benny", false), credit(null, "Kalle K", true)));

        final List<ContentService.NumberCreditInput> credits = ContentGraphqlApi.number(input).credits();

        assertThat(credits).containsExactly(
                new ContentService.NumberCreditInput(UUID.fromString(LYRICS), UUID.fromString(SPEXARE), null, false),
                new ContentService.NumberCreditInput(UUID.fromString(LYRICS), null, "Benny", false),
                new ContentService.NumberCreditInput(UUID.fromString(LYRICS), null, "Kalle K", true));
    }

    @Test
    void creditsLeftOutAreLeftAsTheyAre() {
        final ContentService.NumberInput input = ContentGraphqlApi.number(new HashMap<>(Map.of("act", 2, "title", "Final")));

        assertThat(input.credits()).isNull();
        assertThat(input.characterIds()).isNull();
        assertThat(input.act()).isEqualTo(2);
    }
}
