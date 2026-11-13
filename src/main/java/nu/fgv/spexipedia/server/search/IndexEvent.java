package nu.fgv.spexipedia.server.search;

import java.util.UUID;

/**
 * A searchable record changed; the search index is brought up to date once the change is committed.
 *
 * @param kind     what changed
 * @param recordId its id
 * @author Anders Jacobsson
 * @since 1.0
 */
public record IndexEvent(SearchKind kind, UUID recordId) {
}
