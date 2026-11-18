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

package nu.fgv.spexipedia.server.spexpackage;

import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A package's files: {@code imports/packages/<uuid>/} holds {@code package.xlsx}, {@code package.zip} while it
 * arrives and until it is unpacked, then {@code files/<sha256>} and {@code manifest.json}, the zip's paths with their
 * hashes. The zip is deleted once unpacked, so a 20 GB package takes its space once.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Component
public class PackageStore {

    /** A file of the zip: its path in the zip, its SHA-256 and size. */
    public record PackageFile(String path, String sha256, long size) {

        public String name() {
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    private final Path root;
    private final JsonMapper jsonMapper;

    public PackageStore(final SpexipediaProperties properties, final JsonMapper jsonMapper) {
        this.root = properties.imports().root().resolve("packages");
        this.jsonMapper = jsonMapper;
    }

    public Path folder(final UUID id) {
        return root.resolve(id.toString());
    }

    public Path spreadsheet(final UUID id) {
        return folder(id).resolve("package.xlsx");
    }

    public Path zip(final UUID id) {
        return folder(id).resolve("package.zip");
    }

    public Path file(final UUID id, final String sha256) {
        return folder(id).resolve("files").resolve(sha256);
    }

    public void saveSpreadsheet(final UUID id, final InputStream content) throws IOException {
        Files.createDirectories(folder(id));
        Files.copy(content, spreadsheet(id), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Appends a part of the zip at the offset, which must be what has arrived so far; returns the new length.
     */
    public long appendZip(final UUID id, final long offset, final InputStream content, final long limit) throws IOException {
        final Path zip = zip(id);

        Files.createDirectories(folder(id));

        final long current = Files.exists(zip) ? Files.size(zip) : 0;

        if (offset != current) {
            throw new BadRequestException("error.package.offset", current);
        }
        try (OutputStream out = Files.newOutputStream(zip, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            final byte[] buffer = new byte[1 << 16];
            long written = current;
            int n;

            while ((n = content.read(buffer)) > 0) {
                written += n;
                if (written > limit) {
                    throw new BadRequestException("error.package.tooLarge");
                }
                out.write(buffer, 0, n);
            }
            return written;
        }
    }

    public long zipLength(final UUID id) throws IOException {
        return Files.exists(zip(id)) ? Files.size(zip(id)) : 0;
    }

    /**
     * Unpacks the zip into {@code files/} by content hash and writes the manifest, then deletes the zip. Folders,
     * {@code __MACOSX/} and hidden files are left out. Done once: a second call reads the manifest.
     */
    public List<PackageFile> unpack(final UUID id, final LongConsumer progress) throws IOException {
        final Optional<List<PackageFile>> done = manifest(id);

        if (done.isPresent() || !Files.exists(zip(id))) {
            return done.orElse(List.of());
        }

        final List<PackageFile> files = new ArrayList<>();
        final Path folder = folder(id).resolve("files");
        long read = 0;

        Files.createDirectories(folder);
        try (ZipFile zip = new ZipFile(zip(id).toFile())) {
            final Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                final String path = entry.getName().replace('\\', '/');

                if (entry.isDirectory() || skipped(path)) {
                    continue;
                }

                final Path part = folder.resolve("unpacking.part");
                final MessageDigest digest = sha256();

                try (InputStream in = new DigestInputStream(zip.getInputStream(entry), digest)) {
                    Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
                }

                final String sha = HexFormat.of().formatHex(digest.digest());
                final long size = Files.size(part);

                Files.move(part, folder.resolve(sha), StandardCopyOption.REPLACE_EXISTING);
                files.add(new PackageFile(path, sha, size));
                read += entry.getCompressedSize() > 0 ? entry.getCompressedSize() : size;
                progress.accept(read);
            }
        }
        jsonMapper.writeValue(folder(id).resolve("manifest.json").toFile(), files);
        Files.delete(zip(id));
        return files;
    }

    public Optional<List<PackageFile>> manifest(final UUID id) {
        final Path manifest = folder(id).resolve("manifest.json");

        if (!Files.exists(manifest)) {
            return Optional.empty();
        }
        return Optional.of(jsonMapper.readValue(manifest.toFile(), new TypeReference<List<PackageFile>>() {
        }));
    }

    public void delete(final UUID id) {
        try {
            FileSystemUtils.deleteRecursively(folder(id));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean skipped(final String path) {
        for (final String part : path.split("/")) {
            if (part.startsWith(".") || part.equalsIgnoreCase("__MACOSX") || part.equalsIgnoreCase("Thumbs.db")
                    || part.toLowerCase(Locale.ROOT).equals("desktop.ini")) {
                return true;
            }
        }
        return path.isBlank();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
