package nu.fgv.spexipedia.server.image;

import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * The folders: {@code images/<uuid>/} (served by the media server under {@code /media/images/}), {@code trash/<uuid>/}
 * (not served) and {@code uploads/} (unfinished uploads).
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Component
public class ImageStorage {

    private final Path images;
    private final Path trash;
    private final Path uploads;

    public ImageStorage(final SpexipediaProperties properties) {
        this.images = properties.media().root().resolve("images").toAbsolutePath().normalize();
        this.trash = properties.media().root().resolve("trash").toAbsolutePath().normalize();
        this.uploads = properties.media().uploads().toAbsolutePath().normalize();
        try {
            Files.createDirectories(images);
            Files.createDirectories(trash);
            Files.createDirectories(uploads);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path folder(final UUID id) {
        return images.resolve(id.toString());
    }

    public Path original(final UUID id) {
        return folder(id).resolve("original");
    }

    public Path variant(final UUID id, final ImageSize size, final String extension) {
        return folder(id).resolve(size.fileName() + "." + extension);
    }

    public Path newUpload() throws IOException {
        return Files.createTempFile(uploads, "upload-", ".tmp");
    }

    public Path uploads() {
        return uploads;
    }

    public void moveToTrash(final UUID id) {
        move(folder(id), trash.resolve(id.toString()));
    }

    public void restoreFromTrash(final UUID id) {
        move(trash.resolve(id.toString()), folder(id));
    }

    public void deleteForGood(final UUID id) {
        try {
            FileSystemUtils.deleteRecursively(trash.resolve(id.toString()));
            FileSystemUtils.deleteRecursively(folder(id));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void move(final Path from, final Path to) {
        try {
            if (Files.exists(from)) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
