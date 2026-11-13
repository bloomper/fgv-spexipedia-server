package nu.fgv.spexipedia.server.util;

import nu.fgv.spexipedia.server.util.error.BadRequestException;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

/**
 * Cleaning up text input.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class Texts {

    private Texts() {
    }

    /**
     * The text without surrounding blanks, or null when there is nothing.
     */
    public static @Nullable String clean(final @Nullable String text) {
        return StringUtils.hasText(text) ? text.strip() : null;
    }

    /**
     * The text without surrounding blanks; a bad request when it is empty or longer than {@code max}.
     */
    public static String required(final @Nullable String text, final int max, final String field) {
        final String cleaned = clean(text);

        if (cleaned == null || cleaned.length() > max) {
            throw new BadRequestException("error.field.invalid", field);
        }
        return cleaned;
    }

    /**
     * The text without surrounding blanks or null; a bad request when it is longer than {@code max}.
     */
    public static @Nullable String optional(final @Nullable String text, final int max, final String field) {
        final String cleaned = clean(text);

        if (cleaned != null && cleaned.length() > max) {
            throw new BadRequestException("error.field.invalid", field);
        }
        return cleaned;
    }

    /**
     * A year, yyyy.
     */
    public static String year(final @Nullable String text) {
        final String cleaned = clean(text);

        if (cleaned == null || !cleaned.matches("(19|20)\\d\\d")) {
            throw new BadRequestException("error.year.invalid", String.valueOf(text));
        }
        return cleaned;
    }

    /**
     * A date as precise as known: yyyy, yyyy-mm or yyyy-mm-dd; null for none.
     */
    public static @Nullable String partialDate(final @Nullable String text) {
        final String cleaned = clean(text);

        if (cleaned != null && !cleaned.matches("(19|20)\\d\\d(-(0[1-9]|1[0-2])(-(0[1-9]|[12]\\d|3[01]))?)?")) {
            throw new BadRequestException("error.date.invalid", cleaned);
        }
        return cleaned;
    }
}
