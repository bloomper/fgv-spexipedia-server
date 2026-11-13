package nu.fgv.spexipedia.server.image;

/**
 * What an image shows, which decides where it is offered. Images must not show identifiable people: photos of people
 * belong in Spexgram.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public enum ImageKind {
    POSTER,
    LOGO,
    SKETCH,
    MAKEUP,
    COSTUME,
    SCENOGRAPHY,
    REBUS,
    VEST,
    COVER,
    ARTICLE,
    OTHER
}
