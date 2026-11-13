package nu.fgv.spexipedia.server.util;

import nu.fgv.spexipedia.server.util.error.BadRequestException;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Reading ids from the API, where an id may also be a slug.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class Ids {

    private Ids() {
    }

    /**
     * The UUID, or empty when the text is not one (e.g. a slug).
     */
    public static Optional<UUID> parse(final @Nullable String id) {
        if (id == null || id.length() != 36) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(id));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * The UUID, or a bad request.
     */
    public static UUID require(final @Nullable String id) {
        return parse(id).orElseThrow(() -> new BadRequestException("error.id.invalid", String.valueOf(id)));
    }

    public static @Nullable UUID optional(final @Nullable String id) {
        return id == null || id.isBlank() ? null : require(id);
    }
}
