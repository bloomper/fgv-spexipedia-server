package nu.fgv.spexipedia.server.image;

import lombok.extern.slf4j.Slf4j;
import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reads and converts images with ImageMagick ({@code magick} 7 or {@code convert} 6), which reads JPEG, PNG, WebP,
 * TIFF and HEIC. Only the first frame of a file is used; orientation is applied and metadata (positions, cameras) is
 * stripped from what it writes.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Component
public class ImageMagick {

    /**
     * The formats accepted, by ImageMagick's name, with the content type they are stored as.
     */
    static final Map<String, String> ACCEPTED = Map.of(
            "JPEG", "image/jpeg",
            "PNG", "image/png",
            "WEBP", "image/webp",
            "TIFF", "image/tiff",
            "HEIC", "image/heic",
            "HEIF", "image/heif"
    );
    private static final long TIMEOUT_SECONDS = 120;
    private static final long MAX_PIXELS = 200_000_000L;

    private final String command;

    public ImageMagick(final SpexipediaProperties properties) {
        this.command = "auto".equals(properties.media().magick()) ? detect() : properties.media().magick();
    }

    /**
     * ImageMagick 7's {@code magick} when it is on the path, else 6's {@code convert} (Ubuntu's package, in the image).
     */
    private static String detect() {
        final String path = System.getenv("PATH");

        if (path != null) {
            for (final String folder : path.split(java.io.File.pathSeparator)) {
                if (java.nio.file.Files.isExecutable(Path.of(folder, "magick"))) {
                    return "magick";
                }
            }
        }
        return "convert";
    }

    /**
     * The format, as content type, and the size of an image as it is shown (orientation applied).
     *
     * @throws BadRequestException when it is no image of an accepted format
     */
    public Info identify(final Path file) {
        final String output;

        try {
            output = run(List.of(file + "[0]", "-auto-orient", "-format", "%m %w %h", "info:"));
        } catch (final ConversionException e) {
            throw new BadRequestException("error.image.unreadable");
        }

        final String[] parts = output.strip().split("\\s+");

        if (parts.length != 3 || !ACCEPTED.containsKey(parts[0].toUpperCase(Locale.ROOT))) {
            throw new BadRequestException("error.image.format");
        }

        final int width = Integer.parseInt(parts[1]);
        final int height = Integer.parseInt(parts[2]);

        if ((long) width * height > MAX_PIXELS) {
            throw new BadRequestException("error.image.tooLarge");
        }
        return new Info(ACCEPTED.get(parts[0].toUpperCase(Locale.ROOT)), width, height);
    }

    /**
     * Writes the image no wider or taller than {@code size}, never enlarged, without metadata, as WebP or JPEG by the
     * target's extension.
     */
    public void resize(final Path source, final int size, final Path target) {
        final List<String> arguments = new ArrayList<>(List.of(source + "[0]", "-auto-orient", "-strip",
                "-resize", size + "x" + size + ">", "-quality", "82"));

        if (target.toString().endsWith(".jpg")) {
            arguments.addAll(List.of("-background", "white", "-alpha", "remove", "-interlace", "Plane"));
        }
        arguments.add(target.toString());
        run(arguments);
    }

    private String run(final List<String> arguments) {
        final List<String> commandLine = new ArrayList<>(List.of(command.split(" ")));

        commandLine.addAll(arguments);
        try {
            final Process process = new ProcessBuilder(commandLine).redirectErrorStream(true).start();
            final byte[] output = process.getInputStream().readAllBytes();

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ConversionException("ImageMagick timed out");
            }
            if (process.exitValue() != 0) {
                throw new ConversionException("ImageMagick failed: " + new String(output, StandardCharsets.UTF_8).strip());
            }
            return new String(output, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new ConversionException("ImageMagick could not be run: " + e.getMessage(), e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConversionException("Interrupted", e);
        }
    }

    public record Info(String contentType, int width, int height) {
    }

    /**
     * ImageMagick failed or could not be run.
     */
    public static class ConversionException extends RuntimeException {
        public ConversionException(final String message) {
            super(message);
        }

        public ConversionException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
