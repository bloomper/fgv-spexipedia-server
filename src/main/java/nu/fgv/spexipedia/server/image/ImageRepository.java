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

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Image}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public interface ImageRepository extends JpaRepository<Image, Long> {

    Optional<Image> findByUuid(UUID uuid);

    Optional<Image> findFirstBySha256AndTrashedAtIsNull(String sha256);

    List<Image> findByUuidIn(Collection<UUID> uuids);

    List<Image> findByTrashedAtIsNotNullOrderByTrashedAtDesc();

    List<Image> findByVariantWidthsAndTrashedAtIsNull(String variantWidths);

    List<Image> findByTrashedAtBefore(Instant before);
}
