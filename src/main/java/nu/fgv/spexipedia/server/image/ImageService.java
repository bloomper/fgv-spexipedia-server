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
import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.audit.AuditText;
import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Texts;
import nu.fgv.spexipedia.server.util.Uuids;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Uploaded images: keeps the original, makes the variants the pages need, and gives their public addresses. Images are
 * public, so their addresses are plain and long-lived; the media server serves them with long cache times.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Service
public class ImageService {

    static final String DONE = Arrays.stream(ImageSize.values()).map(ImageSize::fileName).collect(Collectors.joining(","));
    private static final int MAX_CAPTION = 1000;

    private final ImageRepository repository;
    private final ImageStorage storage;
    private final ImageMagick magick;
    private final CallerService callerService;
    private final AuditService auditService;
    private final Clock clock;
    private final String baseUrl;
    private final long maxBytes;

    public ImageService(final ImageRepository repository, final ImageStorage storage, final ImageMagick magick,
                        final CallerService callerService, final AuditService auditService, final Clock clock,
                        final SpexipediaProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.magick = magick;
        this.callerService = callerService;
        this.auditService = auditService;
        this.clock = clock;
        this.baseUrl = properties.media().baseUrl().replaceAll("/+$", "");
        this.maxBytes = properties.media().maxUploadSize().toBytes();
    }

    private static String extension(final String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/tiff" -> "tif";
            default -> "heic";
        };
    }

    private static String safeName(final @Nullable String name) {
        final String base = StringUtils.hasText(name) ? Path.of(name).getFileName().toString() : "bild";

        return base.length() > 200 ? base.substring(0, 200) : base;
    }

    /**
     * Stores an upload by an author: checks it, keeps the original and makes the variants.
     */
    @Transactional
    public ImageDto upload(final InputStream content, final @Nullable String fileName, final ImageKind kind) {
        final Caller caller = callerService.current();

        caller.requireAuthor();

        final Image image = store(content, fileName, kind);

        auditService.record("UPLOAD", "IMAGE", image.getUuid(), AuditText.of("audit.image.upload", image.getOriginalFileName()));
        return toDto(image, true);
    }

    /**
     * EDITOR (an import). The image with this content if Spexipedia has it, else the file stored as a new image.
     */
    @Transactional
    public Image importImage(final java.nio.file.Path file, final String sha256, final @Nullable String fileName, final ImageKind kind) {
        callerService.current().requireAuthor();

        return repository.findFirstBySha256AndTrashedAtIsNull(sha256).orElseGet(() -> {
            try (InputStream in = Files.newInputStream(file)) {
                final Image image = store(in, fileName, kind);

                auditService.record("UPLOAD", "IMAGE", image.getUuid(), AuditText.of("audit.image.import", image.getOriginalFileName()));
                return image;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Stores an image without checking the caller, for the sample data.
     */
    @Transactional
    public Image store(final InputStream content, final @Nullable String fileName, final ImageKind kind) {
        final Path upload;
        final String sha256;

        try {
            upload = storage.newUpload();

            final MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (DigestInputStream in = new DigestInputStream(content, digest)) {
                final long size = Files.copy(in, upload, StandardCopyOption.REPLACE_EXISTING);

                if (size == 0) {
                    throw new BadRequestException("error.upload.empty");
                }
                if (size > maxBytes) {
                    Files.deleteIfExists(upload);
                    throw new BadRequestException("error.upload.tooLarge");
                }
            }
            sha256 = HexFormat.of().formatHex(digest.digest());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        final UUID id = Uuids.v7();

        try {
            final ImageMagick.Info info = magick.identify(upload);
            final Image image = new Image();

            Files.createDirectories(storage.folder(id));
            image.setSizeBytes(Files.size(upload));
            Files.move(upload, storage.original(id), StandardCopyOption.REPLACE_EXISTING);
            image.setUuid(id);
            image.setKind(kind);
            image.setOriginalFileName(safeName(fileName));
            image.setContentType(info.contentType());
            image.setWidth(info.width());
            image.setHeight(info.height());
            image.setSha256(sha256);
            makeVariants(image);
            return repository.save(image);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final RuntimeException e) {
            try {
                Files.deleteIfExists(upload);
                FileSystemUtils.deleteRecursively(storage.folder(id));
            } catch (final IOException ignored) {
                // Best effort; the upload cleanup job removes what is left
            }
            throw e;
        }
    }

    /**
     * Makes the image's variants; on failure they are left to the maintenance job.
     */
    void makeVariants(final Image image) {
        try {
            for (final ImageSize size : ImageSize.values()) {
                magick.resize(storage.original(image.getUuid()), size.size(), storage.variant(image.getUuid(), size, "webp"));
                magick.resize(storage.original(image.getUuid()), size.size(), storage.variant(image.getUuid(), size, "jpg"));
            }
            image.setVariantWidths(DONE);
        } catch (final ImageMagick.ConversionException e) {
            log.warn("Could not make the variants of image {}: {}", image.getUuid(), e.getMessage());
            image.setVariantWidths("");
        }
    }

    /**
     * Makes the variants that are missing, e.g. after a failure or a new size. The maintenance job IMAGE_VARIANTS.
     */
    @Transactional
    public void makeMissingVariants() {
        for (final Image image : repository.findByVariantWidthsAndTrashedAtIsNull("")) {
            if (Files.exists(storage.original(image.getUuid()))) {
                makeVariants(image);
            }
        }
        repository.findAll().stream()
                .filter(image -> image.getTrashedAt() == null && image.hasVariants())
                .filter(image -> Arrays.stream(ImageSize.values())
                        .anyMatch(size -> !Files.exists(storage.variant(image.getUuid(), size, "webp"))))
                .forEach(this::makeVariants);
    }

    /**
     * Removes uploads that were never finished, older than a day.
     */
    public void cleanUpUploads() {
        final Instant before = clock.instant().minusSeconds(86_400);

        try (var files = Files.list(storage.uploads())) {
            files.filter(file -> {
                try {
                    return Files.getLastModifiedTime(file).toInstant().isBefore(before);
                } catch (final IOException e) {
                    return false;
                }
            }).forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (final IOException e) {
                    log.warn("Could not remove {}", file);
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional(readOnly = true)
    public Image require(final UUID id) {
        return repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Image.class, id));
    }

    /**
     * An image to place on a page: exists and is not in the trash.
     */
    @Transactional(readOnly = true)
    public Image requireUsable(final UUID id) {
        final Image image = require(id);

        if (image.getTrashedAt() != null) {
            throw new BadRequestException("error.image.trashed");
        }
        return image;
    }

    @Transactional(readOnly = true)
    public Map<Long, Image> byIds(final Collection<Long> ids) {
        return repository.findAllById(ids).stream().collect(Collectors.toMap(Image::getId, Function.identity()));
    }

    @Transactional
    public ImageDto update(final UUID id, final @Nullable String caption, final @Nullable String alt) {
        callerService.current().requireAuthor();

        final Image image = require(id);

        image.setCaption(Texts.optional(caption, MAX_CAPTION, "caption"));
        image.setAlt(Texts.optional(alt, MAX_CAPTION, "alt"));
        auditService.record("UPDATE", "IMAGE", id, AuditText.of("audit.image.caption", image.getOriginalFileName()));
        return toDto(repository.save(image), true);
    }

    @Transactional
    public boolean trash(final UUID id) {
        callerService.current().requireAuthor();

        final Image image = require(id);

        if (image.getTrashedAt() == null) {
            image.setTrashedAt(clock.instant());
            storage.moveToTrash(id);
            auditService.record("TRASH", "IMAGE", id, AuditText.of("audit.image.trash", image.getOriginalFileName()));
        }
        return true;
    }

    @Transactional
    public ImageDto restore(final UUID id) {
        callerService.current().requireAuthor();

        final Image image = require(id);

        if (image.getTrashedAt() != null) {
            image.setTrashedAt(null);
            storage.restoreFromTrash(id);
            auditService.record("RESTORE", "IMAGE", id, AuditText.of("audit.image.restore", image.getOriginalFileName()));
        }
        return toDto(image, true);
    }

    @Transactional(readOnly = true)
    public List<ImageDto> trashed() {
        callerService.current().requireAuthor();
        return repository.findByTrashedAtIsNotNullOrderByTrashedAtDesc().stream().map(image -> toDto(image, true)).toList();
    }

    /**
     * Deletes trashed images for good; those still on a page are kept, since the database refers to them.
     *
     * @return how many were deleted
     */
    @Transactional
    public int emptyTrash() {
        int deleted = 0;

        for (final Image image : repository.findByTrashedAtIsNotNullOrderByTrashedAtDesc()) {
            try {
                repository.delete(image);
                repository.flush();
                storage.deleteForGood(image.getUuid());
                deleted++;
            } catch (final org.springframework.dao.DataIntegrityViolationException e) {
                log.info("Kept trashed image {}: it is still used", image.getUuid());
            }
        }
        return deleted;
    }

    public String url(final UUID id, final ImageSize size, final String extension) {
        return "%s/images/%s/%s.%s".formatted(baseUrl, id, size.fileName(), extension);
    }

    public ImageDto toDto(final Image image, final boolean author) {
        final UUID id = image.getUuid();
        final String alt = Objects.requireNonNullElse(StringUtils.hasText(image.getAlt()) ? image.getAlt() : image.getCaption(), "");
        final ImageDto dto = new ImageDto(id, image.getKind(), image.getCaption(), alt, image.getWidth(), image.getHeight(),
                url(id, ImageSize.THUMBNAIL, "webp"), url(id, ImageSize.PAGE, "webp"), url(id, ImageSize.FULL, "webp"),
                url(id, ImageSize.PAGE, "jpg"), image.getOriginalFileName(), image.getTrashedAt());

        return author ? dto : dto.forPublic();
    }

    /**
     * The image for the viewer, or null when there is none or it is in the trash.
     */
    public @Nullable ImageDto visible(final @Nullable Image image) {
        if (image == null || image.getTrashedAt() != null) {
            return null;
        }
        return toDto(image, callerService.currentIfAny().map(Caller::isAuthor).orElse(false));
    }
}
