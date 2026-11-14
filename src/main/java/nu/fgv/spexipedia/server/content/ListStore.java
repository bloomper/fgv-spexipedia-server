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

import nu.fgv.spexipedia.server.image.ImageKind;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The ordered lists that belong to a record and are always replaced as a whole: a number's credits and singers, a
 * character's images, a page's images and Spexgram photos, and a revival's changes. Kept with plain SQL, by the
 * records' internal keys.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Component
public class ListStore {

    private final JdbcClient jdbc;

    public ListStore(final JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Credits --------------------------------------------------------------------------------------------------------

    public enum CreditKind {
        LYRICS,
        COMPOSER,
        ARRANGER,
        CHOREOGRAPHER
    }

    /**
     * A spexare in Spexregister, or someone outside FGV by name.
     */
    public record CreditRef(@Nullable UUID spexareId, @Nullable String name) {
    }

    public record Credit(long numberId, CreditKind kind, CreditRef ref) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void setCredits(final long numberId, final CreditKind kind, final List<CreditRef> credits) {
        jdbc.sql("DELETE FROM number_credit WHERE number_id = :n AND kind = :k").param("n", numberId).param("k", kind.name()).update();
        for (int i = 0; i < credits.size(); i++) {
            final CreditRef credit = credits.get(i);

            jdbc.sql("INSERT INTO number_credit (number_id, kind, position, spexare_id, name) VALUES (:n, :k, :p, :s, :name)")
                    .param("n", numberId).param("k", kind.name()).param("p", i)
                    .param("s", credit.spexareId() == null ? null : credit.spexareId().toString())
                    .param("name", credit.name())
                    .update();
        }
    }

    public List<Credit> credits(final Collection<Long> numberIds) {
        if (numberIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT number_id, kind, spexare_id, name FROM number_credit WHERE number_id IN (:ids) ORDER BY number_id, kind, position")
                .param("ids", numberIds)
                .query((rs, _) -> new Credit(rs.getLong("number_id"), CreditKind.valueOf(rs.getString("kind")),
                        new CreditRef(rs.getString("spexare_id") == null ? null : UUID.fromString(rs.getString("spexare_id")), rs.getString("name"))))
                .list();
    }

    // Who sings a number ---------------------------------------------------------------------------------------------

    @Transactional(propagation = Propagation.MANDATORY)
    public void setNumberCharacters(final long numberId, final Collection<Long> characterIds) {
        jdbc.sql("DELETE FROM number_character WHERE number_id = :n").param("n", numberId).update();
        for (final Long characterId : characterIds) {
            jdbc.sql("INSERT INTO number_character (number_id, character_id) VALUES (:n, :c)").param("n", numberId).param("c", characterId).update();
        }
    }

    /**
     * Number id to character ids.
     */
    public Map<Long, List<Long>> numberCharacters(final Collection<Long> numberIds) {
        final Map<Long, List<Long>> result = new LinkedHashMap<>();

        if (!numberIds.isEmpty()) {
            jdbc.sql("SELECT number_id, character_id FROM number_character WHERE number_id IN (:ids)").param("ids", numberIds)
                    .query((rs, _) -> Map.entry(rs.getLong(1), rs.getLong(2))).list()
                    .forEach(entry -> result.computeIfAbsent(entry.getKey(), _ -> new ArrayList<>()).add(entry.getValue()));
        }
        return result;
    }

    /**
     * Character id to the ids of the numbers it sings.
     */
    public List<Long> numbersOfCharacter(final long characterId) {
        return jdbc.sql("SELECT number_id FROM number_character WHERE character_id = :c").param("c", characterId).query(Long.class).list();
    }

    // Images on pages ------------------------------------------------------------------------------------------------

    public record PlacedImage(long imageId, @Nullable ImageKind kind, @Nullable String caption) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void setCharacterImages(final long characterId, final List<PlacedImage> images) {
        jdbc.sql("DELETE FROM character_image WHERE character_id = :c").param("c", characterId).update();
        for (int i = 0; i < images.size(); i++) {
            final PlacedImage image = images.get(i);

            jdbc.sql("INSERT INTO character_image (character_id, position, image_id, kind, caption) VALUES (:c, :p, :i, :k, :cap)")
                    .param("c", characterId).param("p", i).param("i", image.imageId())
                    .param("k", image.kind() == null ? ImageKind.SKETCH.name() : image.kind().name())
                    .param("cap", image.caption())
                    .update();
        }
    }

    /**
     * Character id to its images, in order.
     */
    public Map<Long, List<PlacedImage>> characterImages(final Collection<Long> characterIds) {
        final Map<Long, List<PlacedImage>> result = new LinkedHashMap<>();

        if (!characterIds.isEmpty()) {
            jdbc.sql("SELECT character_id, image_id, kind, caption FROM character_image WHERE character_id IN (:ids) ORDER BY character_id, position")
                    .param("ids", characterIds)
                    .query((rs, _) -> Map.entry(rs.getLong(1), new PlacedImage(rs.getLong(2), ImageKind.valueOf(rs.getString(3)), rs.getString(4))))
                    .list()
                    .forEach(entry -> result.computeIfAbsent(entry.getKey(), _ -> new ArrayList<>()).add(entry.getValue()));
        }
        return result;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void setPageImages(final long spexId, final String section, final List<PlacedImage> images) {
        jdbc.sql("DELETE FROM spex_page_image WHERE spex_id = :s AND section = :sec").param("s", spexId).param("sec", section).update();
        for (int i = 0; i < images.size(); i++) {
            jdbc.sql("INSERT INTO spex_page_image (spex_id, section, position, image_id, caption) VALUES (:s, :sec, :p, :i, :cap)")
                    .param("s", spexId).param("sec", section).param("p", i).param("i", images.get(i).imageId())
                    .param("cap", images.get(i).caption())
                    .update();
        }
    }

    public List<PlacedImage> pageImages(final long spexId, final String section) {
        return jdbc.sql("SELECT image_id, caption FROM spex_page_image WHERE spex_id = :s AND section = :sec ORDER BY position")
                .param("s", spexId).param("sec", section)
                .query((rs, _) -> new PlacedImage(rs.getLong(1), null, rs.getString(2)))
                .list();
    }

    // Photos from Spexgram -------------------------------------------------------------------------------------------

    @Transactional(propagation = Propagation.MANDATORY)
    public void setPhotos(final long spexId, final String section, final long characterId, final List<UUID> itemIds) {
        jdbc.sql("DELETE FROM spex_page_photo WHERE spex_id = :s AND section = :sec AND character_id = :c")
                .param("s", spexId).param("sec", section).param("c", characterId).update();
        for (int i = 0; i < itemIds.size(); i++) {
            jdbc.sql("INSERT INTO spex_page_photo (spex_id, section, character_id, position, item_id) VALUES (:s, :sec, :c, :p, :i)")
                    .param("s", spexId).param("sec", section).param("c", characterId).param("p", i).param("i", itemIds.get(i).toString())
                    .update();
        }
    }

    /**
     * The chosen items of a section; characterId 0 for GROUP and BOARD.
     */
    public List<UUID> photos(final long spexId, final String section, final long characterId) {
        return jdbc.sql("SELECT item_id FROM spex_page_photo WHERE spex_id = :s AND section = :sec AND character_id = :c ORDER BY position")
                .param("s", spexId).param("sec", section).param("c", characterId)
                .query((rs, _) -> UUID.fromString(rs.getString(1)))
                .list();
    }

    // A revival's changes --------------------------------------------------------------------------------------------

    public enum RevivalChangeKind {
        CHARACTER,
        SCENE,
        NUMBER
    }

    public record RevivalChange(RevivalChangeKind kind, UUID recordId, boolean cut, @Nullable Integer act,
                                @Nullable Integer position) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void setRevivalChanges(final long revivalId, final RevivalChangeKind kind, final List<RevivalChange> changes) {
        jdbc.sql("DELETE FROM revival_change WHERE revival_id = :r AND kind = :k").param("r", revivalId).param("k", kind.name()).update();
        for (final RevivalChange change : changes) {
            jdbc.sql("INSERT INTO revival_change (revival_id, kind, record_id, cut, act, position) VALUES (:r, :k, :id, :cut, :act, :pos)")
                    .param("r", revivalId).param("k", kind.name()).param("id", change.recordId().toString())
                    .param("cut", change.cut()).param("act", change.act()).param("pos", change.position())
                    .update();
        }
    }

    public List<RevivalChange> revivalChanges(final long revivalId) {
        return jdbc.sql("SELECT kind, record_id, cut, act, position FROM revival_change WHERE revival_id = :r ORDER BY kind, act, position")
                .param("r", revivalId)
                .query((rs, _) -> new RevivalChange(RevivalChangeKind.valueOf(rs.getString(1)), UUID.fromString(rs.getString(2)),
                        rs.getBoolean(3), (Integer) rs.getObject(4), (Integer) rs.getObject(5)))
                .list();
    }

    // Merges ---------------------------------------------------------------------------------------------------------

    /**
     * Moves references to a merged character onto the one it was merged into.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void moveCharacter(final long from, final long to, final UUID fromUuid, final UUID toUuid) {
        jdbc.sql("UPDATE IGNORE number_character SET character_id = :to WHERE character_id = :from").param("to", to).param("from", from).update();
        jdbc.sql("DELETE FROM number_character WHERE character_id = :from").param("from", from).update();
        jdbc.sql("UPDATE spex_page_photo SET character_id = :to WHERE character_id = :from").param("to", to).param("from", from).update();
        jdbc.sql("UPDATE revival_change SET record_id = :to WHERE record_id = :from AND kind = 'CHARACTER'")
                .param("to", toUuid.toString()).param("from", fromUuid.toString()).update();
    }

    /**
     * Moves references to a merged number onto the one it was merged into.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void moveNumber(final long from, final long to, final UUID fromUuid, final UUID toUuid) {
        jdbc.sql("UPDATE recording_track SET number_id = :to WHERE number_id = :from").param("to", to).param("from", from).update();
        jdbc.sql("UPDATE revival_change SET record_id = :to WHERE record_id = :from AND kind = 'NUMBER'")
                .param("to", toUuid.toString()).param("from", fromUuid.toString()).update();
    }
}
