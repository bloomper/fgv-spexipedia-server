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

import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Readable parts of addresses: {@code "Lasse-Maja"} in 1984 becomes {@code 1984-lasse-maja}, {@code "Bröderna
 * Lumière"} becomes {@code broderna-lumiere}. Slugs are unique per kind; a taken one gets {@code -2}, {@code -3} and so
 * on.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class Slugs {

    private static final int MAX = 200;

    private Slugs() {
    }

    public static String of(final @Nullable String text) {
        if (text == null) {
            return "";
        }
        final String folded = Normalizer.normalize(text.replace("æ", "ae").replace("Æ", "Ae").replace("ø", "o")
                        .replace("Ø", "O").replace("ß", "ss"), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");

        return folded.length() > MAX ? folded.substring(0, MAX).replaceAll("-+$", "") : folded;
    }

    /**
     * A slug of the text that {@code taken} says is free, or {@code fallback} when the text has no letters or digits.
     */
    public static String unique(final @Nullable String text, final String fallback, final Predicate<String> taken) {
        final String base = of(text).isEmpty() ? fallback : of(text);
        String candidate = base;

        for (int i = 2; taken.test(candidate); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }
}
