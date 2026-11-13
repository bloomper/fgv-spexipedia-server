package nu.fgv.spexipedia.server.people;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A spexare as the viewer may see them: visitors get the nickname only; logged-in users the id and name too, and a link
 * to Spexregister when they have access to it.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public record PersonDto(@Nullable UUID id, @Nullable String nickname, @Nullable String name, @Nullable String url) {
}
