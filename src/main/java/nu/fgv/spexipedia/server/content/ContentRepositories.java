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

package nu.fgv.spexipedia.server.content;

import nu.fgv.spexipedia.server.basedata.Spex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The repositories of what makes up a spex.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class ContentRepositories {

    private ContentRepositories() {
    }

    public interface FigureRepository extends JpaRepository<Figure, Long> {
        Optional<Figure> findByUuid(UUID uuid);

        Optional<Figure> findBySlug(String slug);

        boolean existsBySlug(String slug);

        List<Figure> findAllByOrderByNameAsc();
    }

    public interface CharacterRepository extends JpaRepository<SpexCharacter, Long> {
        Optional<SpexCharacter> findByUuid(UUID uuid);

        List<SpexCharacter> findByUuidIn(Collection<UUID> uuids);

        List<SpexCharacter> findBySpexOrderByPositionAsc(Spex spex);

        List<SpexCharacter> findBySpexInOrderByPositionAsc(Collection<Spex> spex);

        List<SpexCharacter> findByFigure(Figure figure);

        long countByFigure(Figure figure);

        @Query("SELECT COALESCE(MAX(c.position), 0) FROM SpexCharacter c WHERE c.spex = :spex")
        int maxPosition(Spex spex);
    }

    public interface SceneRepository extends JpaRepository<Scene, Long> {
        Optional<Scene> findByUuid(UUID uuid);

        List<Scene> findBySpexOrderByActAscPositionAsc(Spex spex);

        List<Scene> findBySpexInOrderByActAscPositionAsc(Collection<Spex> spex);

        @Query("SELECT COALESCE(MAX(s.position), 0) FROM Scene s WHERE s.spex = :spex")
        int maxPosition(Spex spex);
    }

    public interface NumberRepository extends JpaRepository<SpexNumber, Long> {
        Optional<SpexNumber> findByUuid(UUID uuid);

        List<SpexNumber> findByUuidIn(Collection<UUID> uuids);

        List<SpexNumber> findBySpexOrderByActAscPositionAsc(Spex spex);

        List<SpexNumber> findBySpexInOrderByActAscPositionAsc(Collection<Spex> spex);

        @Query("SELECT COALESCE(MAX(n.position), 0) FROM SpexNumber n WHERE n.spex = :spex")
        int maxPosition(Spex spex);
    }

    public interface RecordingRepository extends JpaRepository<Recording, Long> {
        Optional<Recording> findByUuid(UUID uuid);

        Optional<Recording> findBySlug(String slug);

        boolean existsBySlug(String slug);

        List<Recording> findAllByOrderByCatalogueNumberAsc();

        List<Recording> findBySpexOrderByReleasedAsc(Spex spex);
    }

    public interface TrackRepository extends JpaRepository<Track, Long> {
        List<Track> findByRecordingOrderByPositionAsc(Recording recording);

        void deleteByRecording(Recording recording);

        long countByNumber(SpexNumber number);
    }
}
