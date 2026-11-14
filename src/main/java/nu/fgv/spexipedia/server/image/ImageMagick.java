/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nu.fgv.spexipedia.server.image;

import lombok.extern.slf4j.Slf4j;
import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Reads and converts images with ImageMagick ({@code magick} 7 or {@code convert} 6), which reads JPEG, PNG, WebP,
 * TIFF and HEIC. Only the first frame of a file is used; orientation is applied and metadata (positions, cameras) is
 * stripped from what it writes.
 * <p>
 * Uploads are untrusted: the format is told from the file's first bytes, never left to ImageMagick, and the input is
 * prefixed with its coder (e.g. {@code jpeg:}), so that no other coder (SVG, MVG, MSL, text, URLs) ever reads it.
 * The size is read with {@code -ping}, before anything is decoded, and every run has resource limits and a timeout.
 * The image's {@code policy.xml} (base/policy.xml) allows the same coders only, as a second line.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Component
public class ImageMagick {

    private static final Duration TIMEOUT = Duration.ofSeconds(120);
    private static final long MAX_PIXELS = 200_000_000L;
    private static final int HEADER_BYTES = 32;
    /**
     * The brands of an ISO base media file (after {@code ftyp}) that are HEIC or HEIF still images.
     */
    private static final Set<String> HEIF_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");
    /**
     * Orientations whose image is shown turned a quarter, so its width and height swap.
     */
    private static final Set<String> TURNED = Set.of("LeftTop", "RightTop", "RightBottom", "LeftBottom");
    /**
     * Limits for every run, below the policy's; they also hold where the policy is ImageMagick's default (development).
     */
    private static final List<String> LIMITS = List.of(
            "-limit", "memory", "512MiB",
            "-limit", "map", "1GiB",
            "-limit", "disk", "2GiB",
            "-limit", "width", "30000",
            "-limit", "height", "30000",
            "-limit", "area", String.valueOf(MAX_PIXELS),
            "-limit", "time", String.valueOf(TIMEOUT.toSeconds()));

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
                if (Files.isExecutable(Path.of(folder, "magick"))) {
                    return "magick";
                }
            }
        }
        return "convert";
    }

    /**
     * The accepted format of a file by its first bytes, or null when it is none of them.
     */
    static @Nullable Format sniff(final Path file) {
        final byte[] head;

        try (InputStream in = Files.newInputStream(file)) {
            head = in.readNBytes(HEADER_BYTES);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) {
            return Format.JPEG;
        }
        if (startsWith(head, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Format.PNG;
        }
        if (startsWith(head, 0, 'R', 'I', 'F', 'F') && startsWith(head, 8, 'W', 'E', 'B', 'P')) {
            return Format.WEBP;
        }
        if (startsWith(head, 0, 'I', 'I', 0x2A, 0x00) || startsWith(head, 0, 'M', 'M', 0x00, 0x2A)) {
            return Format.TIFF;
        }
        if (startsWith(head, 4, 'f', 't', 'y', 'p') && head.length >= 12
                && HEIF_BRANDS.contains(new String(head, 8, 4, StandardCharsets.US_ASCII))) {
            return Format.HEIC;
        }
        return null;
    }

    private static boolean startsWith(final byte[] bytes, final int offset, final int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static String input(final Format format, final Path file) {
        return format.coder() + ":" + file + "[0]";
    }

    /**
     * The format, as content type, and the size of an image as it is shown (orientation applied), read without
     * decoding the pixels.
     *
     * @throws BadRequestException when it is no image of an accepted format, or too large
     */
    public Info identify(final Path file) {
        final Format format = sniff(file);

        if (format == null) {
            throw new BadRequestException("error.image.format");
        }

        final String output;

        try {
            output = run(List.of("-ping", input(format, file), "-format", "%w %h %[orientation]", "info:"));
        } catch (final ConversionException e) {
            throw new BadRequestException("error.image.unreadable");
        }

        final String[] parts = output.strip().split("\\s+");
        final int width;
        final int height;

        try {
            width = Integer.parseInt(parts[0]);
            height = Integer.parseInt(parts[1]);
        } catch (final RuntimeException e) {
            throw new BadRequestException("error.image.unreadable");
        }
        if (width <= 0 || height <= 0) {
            throw new BadRequestException("error.image.unreadable");
        }
        if ((long) width * height > MAX_PIXELS) {
            throw new BadRequestException("error.image.tooLarge");
        }

        final boolean turned = parts.length > 2 && TURNED.contains(parts[2]);

        return new Info(format.contentType(), turned ? height : width, turned ? width : height);
    }

    /**
     * Writes the image no wider or taller than {@code size}, never enlarged, without metadata, as WebP or JPEG by the
     * target's extension.
     */
    public void resize(final Path source, final int size, final Path target) {
        final Format format = sniff(source);

        if (format == null) {
            throw new ConversionException("Not an accepted image: " + source);
        }

        final String extension = target.toString().endsWith(".jpg") ? "jpeg" : "webp";
        final List<String> arguments = new ArrayList<>(List.of(input(format, source), "-auto-orient", "-strip",
                "-resize", size + "x" + size + ">", "-quality", "82"));

        if ("jpeg".equals(extension)) {
            arguments.addAll(List.of("-background", "white", "-alpha", "remove", "-interlace", "Plane"));
        }
        arguments.add(extension + ":" + target);
        run(arguments);
    }

    private String run(final List<String> arguments) {
        final List<String> commandLine = new ArrayList<>(Arrays.asList(command.split(" ")));

        commandLine.addAll(LIMITS);
        commandLine.addAll(arguments);

        final Process process;

        try {
            process = new ProcessBuilder(commandLine).redirectErrorStream(true).start();
        } catch (final IOException e) {
            throw new ConversionException("ImageMagick could not be run: " + e.getMessage(), e);
        }
        // Read on another thread: reading here would block until the process ends, and the timeout would never apply
        final CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try (InputStream stream = process.getInputStream()) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (final IOException e) {
                return "";
            }
        });

        try {
            if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ConversionException("ImageMagick timed out");
            }
            if (process.exitValue() != 0) {
                throw new ConversionException("ImageMagick failed: " + output.join().strip());
            }
            return output.join();
        } catch (final InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ConversionException("Interrupted", e);
        }
    }

    /**
     * The accepted formats: ImageMagick's coder for each, and the content type they are stored as.
     */
    enum Format {
        JPEG("jpeg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp"),
        TIFF("tiff", "image/tiff"),
        HEIC("heic", "image/heic");

        private final String coder;
        private final String contentType;

        Format(final String coder, final String contentType) {
            this.coder = coder;
            this.contentType = contentType;
        }

        String coder() {
            return coder;
        }

        String contentType() {
            return contentType;
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
