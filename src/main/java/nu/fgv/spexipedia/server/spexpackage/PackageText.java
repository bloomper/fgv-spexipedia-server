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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import nu.fgv.spexipedia.server.util.Locales;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A text of a package's progress or report: a message code of {@code messages*.properties} with its arguments, put
 * in the reader's language when it is read, not when the job writes it (the job runs without a request). A text from
 * elsewhere, such as an exception's message, is {@link #literal}: shown as it is.
 *
 * @param code the message code; {@code package.literal} for a text shown as it is
 * @param args the arguments, e.g. a file's path
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public record PackageText(String code, List<String> args) {

    static final String LITERAL = "package.literal";
    private static final String PREFIX = "package.";
    private static final char SEPARATOR = '\u001F';
    private static final Pattern CODE = Pattern.compile("package\\.[A-Za-z.]+");

    @JsonCreator
    public PackageText(@JsonProperty("code") final String code, @JsonProperty("args") final @Nullable List<String> args) {
        this.code = code;
        this.args = args == null ? List.of() : List.copyOf(args);
    }

    public static PackageText of(final String code, final Object... args) {
        return new PackageText(PREFIX + code, Arrays.stream(args).map(String::valueOf).toList());
    }

    public static PackageText literal(final String text) {
        return new PackageText(LITERAL, List.of(text));
    }

    /** A report written before texts were codes held plain text. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PackageText fromString(final String text) {
        return literal(text);
    }

    /** The text in the locale's language, Swedish unless English was asked for. */
    public String in(final MessageSource messages, final @Nullable Locale locale) {
        if (LITERAL.equals(code)) {
            return args.isEmpty() ? "" : args.getFirst();
        }
        return messages.getMessage(code, args.toArray(), code, Locales.supported(locale));
    }

    /** For the package's message column: the code and its arguments, apart by a unit separator. */
    public String encode() {
        final StringBuilder out = new StringBuilder(code);

        args.forEach(arg -> out.append(SEPARATOR).append(arg));
        return out.toString();
    }

    /** What {@link #encode} wrote; any other text is a literal. */
    public static PackageText decode(final String stored) {
        final List<String> parts = List.of(stored.split(String.valueOf(SEPARATOR), -1));

        return CODE.matcher(parts.getFirst()).matches() ? new PackageText(parts.getFirst(), parts.subList(1, parts.size())) : literal(stored);
    }
}
