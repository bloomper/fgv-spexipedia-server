package nu.fgv.spexipedia.server.image;

/**
 * The variants made of every image, as WebP and JPEG; none is wider or taller than the original.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public enum ImageSize {
    THUMBNAIL(320),
    PAGE(1280),
    FULL(2560);

    private final int size;

    ImageSize(final int size) {
        this.size = size;
    }

    public int size() {
        return size;
    }

    public String fileName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
