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

    public record NumberDto(long key, UUID id, long spexKey, int act, int position, String title,
                            @Nullable String originalTitle, @Nullable String originalArtist, boolean retired) {
    }

    public record RecordingDto(long key, UUID id, String slug, String catalogueNumber, String title, Medium medium,
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
                number.getTitle(), number.getOriginalTitle(), number.getOriginalArtist(), number.isRetired());
    }

    public static RecordingDto toDto(final Recording recording) {
        final CreditRef conductor = recording.getConductorSpexareId() == null && recording.getConductorName() == null ? null
                : new CreditRef(recording.getConductorSpexareId(), recording.getConductorName());

        return new RecordingDto(recording.getId(), recording.getUuid(), recording.getSlug(), recording.getCatalogueNumber(),
                recording.getTitle(), recording.getMedium(), recording.getSpex() == null ? null : recording.getSpex().getId(),
                recording.getReleased(), recording.getRecorded(), conductor, recording.getDescription(),
                recording.getFront() == null ? null : recording.getFront().getId(),
                recording.getBack() == null ? null : recording.getBack().getId(), recording.getSpexifyAlbumId(),
                recording.isPublished());
    }
}
