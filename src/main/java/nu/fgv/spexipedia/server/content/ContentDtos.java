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

import nu.fgv.spexipedia.server.content.ListStore.CreditRef;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * What makes up a spex, as the API shows it. The {@code key} fields are internal keys, for loading what a record refers
 * to; they are not in the schema.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public final class ContentDtos {

    private ContentDtos() {
    }

    public record FigureDto(long key, UUID id, String slug, String name, @Nullable String description) {
    }

    public record CharacterDto(long key, UUID id, long spexKey, String name, @Nullable Long figureKey,
                               @Nullable String description, int position, boolean retired) {
    }

    public record SceneDto(long key, UUID id, long spexKey, int act, int position, String title, @Nullable String place,
                           boolean intervalAfter) {
    }

    public record NumberDto(long key, UUID id, long spexKey, int act, int position, String title, @Nullable Long kindKey,
                            @Nullable String originalTitle, @Nullable String originalArtist, @Nullable String fromWork,
                            @Nullable String firstLine, boolean retired) {

        /**
         * The same number at another place in the show, as a revival moves it.
         */
        public NumberDto at(final int act, final int position) {
            return new NumberDto(key, id, spexKey, act, position, title, kindKey, originalTitle, originalArtist, fromWork,
                    firstLine, retired);
        }
    }

    public record RecordingDto(long key, UUID id, String slug, String catalogueNumber, String title, long mediumKey,
                               @Nullable Long spexKey, @Nullable String released, @Nullable String recorded,
                               @Nullable CreditRef conductor, @Nullable String description, @Nullable Long frontKey,
                               @Nullable Long backKey, @Nullable UUID spexifyAlbumId, boolean published) {
    }

    public record TrackDto(UUID id, @Nullable String side, int position, @Nullable Long numberKey, String title) {
    }

    public static FigureDto toDto(final Figure figure) {
        return new FigureDto(figure.getId(), figure.getUuid(), figure.getSlug(), figure.getName(), figure.getDescription());
    }

    public static CharacterDto toDto(final SpexCharacter character) {
        return new CharacterDto(character.getId(), character.getUuid(), character.getSpex().getId(), character.getName(),
                character.getFigure() == null ? null : character.getFigure().getId(), character.getDescription(),
                character.getPosition(), character.isRetired());
    }

    public static SceneDto toDto(final Scene scene) {
        return new SceneDto(scene.getId(), scene.getUuid(), scene.getSpex().getId(), scene.getAct(), scene.getPosition(),
                scene.getTitle(), scene.getPlace(), scene.isIntervalAfter());
    }

    public static NumberDto toDto(final SpexNumber number) {
        return new NumberDto(number.getId(), number.getUuid(), number.getSpex().getId(), number.getAct(), number.getPosition(),
                number.getTitle(), number.getKind() == null ? null : number.getKind().getId(), number.getOriginalTitle(),
                number.getOriginalArtist(), number.getFromWork(), number.getFirstLine(), number.isRetired());
    }

    public static RecordingDto toDto(final Recording recording) {
        final CreditRef conductor = recording.getConductorSpexareId() == null && recording.getConductorName() == null ? null
                : new CreditRef(recording.getConductorSpexareId(), recording.getConductorName());

        return new RecordingDto(recording.getId(), recording.getUuid(), recording.getSlug(), recording.getCatalogueNumber(),
                recording.getTitle(), recording.getMedium().getId(), recording.getSpex() == null ? null : recording.getSpex().getId(),
                recording.getReleased(), recording.getRecorded(), conductor, recording.getDescription(),
                recording.getFront() == null ? null : recording.getFront().getId(),
                recording.getBack() == null ? null : recording.getBack().getId(), recording.getSpexifyAlbumId(),
                recording.isPublished());
    }
}
