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

    /**
     * A spexare in Spexregister, or someone outside FGV by name.
     */
    public record CreditRef(@Nullable UUID spexareId, @Nullable String name) {
    }

    /**
     * A number's credit of a credit kind. An unresolved credit is a name an import brought that nobody has turned into
     * a spexare or confirmed as someone outside FGV; only editors see it (spec 5.3).
     */
    public record Credit(long numberId, long kindId, CreditRef ref, boolean unresolved) {

        public Credit(final long numberId, final long kindId, final CreditRef ref) {
            this(numberId, kindId, ref, false);
        }
    }

    /**
     * Replaces all of a number's credits; their order within each kind is the order given.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void setCredits(final long numberId, final List<Credit> credits) {
        jdbc.sql("DELETE FROM number_credit WHERE number_id = :n").param("n", numberId).update();

        final Map<Long, Integer> positions = new LinkedHashMap<>();

        for (final Credit credit : credits) {
            final int position = positions.merge(credit.kindId(), 1, Integer::sum) - 1;

            jdbc.sql("INSERT INTO number_credit (number_id, kind_id, position, spexare_id, name, unresolved) VALUES (:n, :k, :p, :s, :name, :u)")
                    .param("n", numberId).param("k", credit.kindId()).param("p", position)
                    .param("s", credit.ref().spexareId() == null ? null : credit.ref().spexareId().toString())
                    .param("name", credit.ref().name())
                    .param("u", credit.unresolved())
                    .update();
        }
    }

    /**
     * The credits of numbers, by number, credit kind (in the kinds' order) and position.
     */
    public List<Credit> credits(final Collection<Long> numberIds) {
        if (numberIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT c.number_id, c.kind_id, c.spexare_id, c.name, c.unresolved FROM number_credit c JOIN kind k ON k.id = c.kind_id
                        WHERE c.number_id IN (:ids) ORDER BY c.number_id, k.position, c.position""")
                .param("ids", numberIds)
                .query((rs, _) -> new Credit(rs.getLong("number_id"), rs.getLong("kind_id"),
                        new CreditRef(rs.getString("spexare_id") == null ? null : UUID.fromString(rs.getString("spexare_id")), rs.getString("name")),
                        rs.getBoolean("unresolved")))
                .list();
    }

    /**
     * Unresolved credits (spec 5.3), oldest numbers first: number id, credit kind id, position and the name.
     */
    public List<UnresolvedCredit> unresolvedCredits(final int limit) {
        return jdbc.sql("SELECT number_id, kind_id, position, name FROM number_credit WHERE unresolved = 1 ORDER BY number_id, kind_id, position LIMIT :l")
                .param("l", limit)
                .query((rs, _) -> new UnresolvedCredit(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4)))
                .list();
    }

    public long countUnresolvedCredits() {
        return jdbc.sql("SELECT COUNT(*) FROM number_credit WHERE unresolved = 1").query(Long.class).single();
    }

    public record UnresolvedCredit(long numberId, long kindId, int position, String name) {
    }

    /**
     * Resolves one credit: a spexare, or the name as someone outside FGV. Returns whether it was there.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean resolveCredit(final long numberId, final long kindId, final int position, final CreditRef ref) {
        return jdbc.sql("UPDATE number_credit SET spexare_id = :s, name = :name, unresolved = 0 WHERE number_id = :n AND kind_id = :k AND position = :p AND unresolved = 1")
                .param("s", ref.spexareId() == null ? null : ref.spexareId().toString()).param("name", ref.name())
                .param("n", numberId).param("k", kindId).param("p", position)
                .update() == 1;
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

    /**
     * An image in a list: of a character (kindId an image kind for characters), or of a page section (kindId the image
     * kind of the group it is in, or null for the vest).
     */
    public record PlacedImage(long imageId, @Nullable Long kindId, @Nullable String caption) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void setCharacterImages(final long characterId, final List<PlacedImage> images) {
        jdbc.sql("DELETE FROM character_image WHERE character_id = :c").param("c", characterId).update();
        for (int i = 0; i < images.size(); i++) {
            final PlacedImage image = images.get(i);

            jdbc.sql("INSERT INTO character_image (character_id, position, image_id, kind_id, caption) VALUES (:c, :p, :i, :k, :cap)")
                    .param("c", characterId).param("p", i).param("i", image.imageId())
                    .param("k", image.kindId())
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
            jdbc.sql("SELECT character_id, image_id, kind_id, caption FROM character_image WHERE character_id IN (:ids) ORDER BY character_id, position")
                    .param("ids", characterIds)
                    .query((rs, _) -> Map.entry(rs.getLong(1), new PlacedImage(rs.getLong(2), rs.getLong(3), rs.getString(4))))
                    .list()
                    .forEach(entry -> result.computeIfAbsent(entry.getKey(), _ -> new ArrayList<>()).add(entry.getValue()));
        }
        return result;
    }

    /**
     * Replaces the images of a page section's group: for SCENOGRAPHY and OTHER one group per image kind, for VEST one
     * group (kindId null).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void setPageImages(final long spexId, final String section, final @Nullable Long kindId, final List<PlacedImage> images) {
        final long kind = kindId == null ? 0 : kindId;

        jdbc.sql("DELETE FROM spex_page_image WHERE spex_id = :s AND section = :sec AND kind_id = :k")
                .param("s", spexId).param("sec", section).param("k", kind).update();
        for (int i = 0; i < images.size(); i++) {
            jdbc.sql("INSERT INTO spex_page_image (spex_id, section, kind_id, position, image_id, caption) VALUES (:s, :sec, :k, :p, :i, :cap)")
                    .param("s", spexId).param("sec", section).param("k", kind).param("p", i).param("i", images.get(i).imageId())
                    .param("cap", images.get(i).caption())
                    .update();
        }
    }

    /**
     * The images of a page section, in the order of their kinds and then their positions; kindId is null for the vest.
     */
    public List<PlacedImage> pageImages(final long spexId, final String section) {
        return jdbc.sql("""
                        SELECT i.image_id, i.kind_id, i.caption FROM spex_page_image i LEFT JOIN kind k ON k.id = i.kind_id
                        WHERE i.spex_id = :s AND i.section = :sec ORDER BY COALESCE(k.position, 0), i.position""")
                .param("s", spexId).param("sec", section)
                .query((rs, _) -> new PlacedImage(rs.getLong(1), rs.getLong(2) == 0 ? null : rs.getLong(2), rs.getString(3)))
                .list();
    }

    // Facts on spex pages -------------------------------------------------------------------------------------------

    /**
     * A fact of a fact kind (spec 4.2).
     */
    public record Fact(long kindId, String value) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void setFacts(final long spexId, final List<Fact> facts) {
        jdbc.sql("DELETE FROM spex_fact WHERE spex_id = :s").param("s", spexId).update();
        for (final Fact fact : facts) {
            jdbc.sql("INSERT INTO spex_fact (spex_id, kind_id, value) VALUES (:s, :k, :v)")
                    .param("s", spexId).param("k", fact.kindId()).param("v", fact.value()).update();
        }
    }

    /**
     * A spex's facts, in the order of their kinds.
     */
    public List<Fact> facts(final long spexId) {
        return jdbc.sql("SELECT f.kind_id, f.value FROM spex_fact f JOIN kind k ON k.id = f.kind_id WHERE f.spex_id = :s ORDER BY k.position")
                .param("s", spexId)
                .query((rs, _) -> new Fact(rs.getLong(1), rs.getString(2)))
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
}
