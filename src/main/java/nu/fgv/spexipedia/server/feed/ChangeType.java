package nu.fgv.spexipedia.server.feed;

/**
 * What happened to a record in the change feed.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public enum ChangeType {
    /**
     * Created or changed; also brought back from retirement.
     */
    UPSERT,
    /**
     * Retired: readable, not offered for new use.
     */
    RETIRE,
    /**
     * Merged into another record: consumers move their references to it.
     */
    MERGE
}
