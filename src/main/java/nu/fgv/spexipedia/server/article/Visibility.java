package nu.fgv.spexipedia.server.article;

/**
 * Who may read an article: everyone, or logged-in users ({@code USER}). Anecdotes that name people are for members.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public enum Visibility {
    PUBLIC,
    MEMBERS
}
