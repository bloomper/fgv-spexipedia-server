package nu.fgv.spexipedia.server.page;

import nu.fgv.spexipedia.server.content.ListStore.CreditRef;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A spex page as the API shows it. The {@code key} fields are internal keys.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public record SpexPageDto(long spexKey, UUID spexId, @Nullable String generation, @Nullable String premiere,
                          @Nullable String introduction, @Nullable String description, @Nullable String synopsis,
                          @Nullable Long rebusKey, @Nullable Long rebusSolutionKey, @Nullable String vestDescription,
                          @Nullable CreditRef vestWornBy, boolean published, @Nullable Instant lastModifiedAt) {

    public static SpexPageDto empty(final long spexKey, final UUID spexId) {
        return new SpexPageDto(spexKey, spexId, null, null, null, null, null, null, null, null, null, false, null);
    }
}
