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

package nu.fgv.spexipedia.server.page;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How much of a spex's page is filled in, by section of its editing page, for authors to see what is left to write.
 * Every item weighs the same, and an item for a list counts by the share of its rows that have a value (characters
 * with a description, numbers with credits). Counted with a few grouped queries for any number of spex, so the list of
 * all spex can show it.
 * <p>
 * A revival shows its original's content where it has none of its own (spec 5.6), so it is measured by what belongs to
 * the production itself: its own poster, premiere, facts and photos.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class SpexCompletenessService {

    /** A section of the spex's editing page. */
    public enum Section { BASE, TEXTS, FACTS, MEDIA, CHARACTERS, SCENES, NUMBERS }

    /** What the page can have. */
    public enum Item {
        POSTER, PREMIERE, INTRODUCTION, SHORT_DESCRIPTION, DESCRIPTION, SYNOPSIS, VEST_DESCRIPTION, VEST_WORN_BY, FACTS,
        IMAGES, VEST, REBUS, REBUS_SOLUTION, GROUP_PHOTOS, BOARD_PHOTOS, CHARACTERS, CHARACTER_DESCRIPTIONS,
        CHARACTER_IMAGES, SCENES, SCENE_PLACES, NUMBERS, NUMBER_ORIGINALS, NUMBER_CREDITS, NUMBER_CHARACTERS
    }

    /** One item, filled from 0 to 1. */
    public record ItemDto(Item item, double filled) {
    }

    public record SectionDto(Section section, double share, List<ItemDto> items) {
    }

    public record CompletenessDto(double share, List<SectionDto> sections) {
    }

    /** A text that is more than blanks. */
    private static final String FILLED = "(NULLIF(TRIM(%s), '') IS NOT NULL)";

    /** An image that is set and not in the trash, as the pages show only those. */
    private static final String IMAGE = "EXISTS (SELECT 1 FROM image i WHERE i.id = %s AND i.trashed_at IS NULL)";

    private final JdbcClient jdbc;

    public SpexCompletenessService(final JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** What is known of one spex, as the queries find it. */
    private static final class Counts {
        boolean revival;
        boolean poster;
        final Map<Item, Boolean> page = new HashMap<>();
        int facts;
        final Map<String, Integer> images = new HashMap<>();
        final Map<String, Integer> photos = new HashMap<>();
        int characters;
        int characterDescriptions;
        int characterImages;
        int scenes;
        int scenePlaces;
        int numbers;
        int numberOriginals;
        int numberCredits;
        int numberCharacters;
    }

    /**
     * The completeness of each spex by its key; keys of spex that do not exist are left out.
     */
    @Transactional(readOnly = true)
    public Map<Long, CompletenessDto> of(final Collection<Long> spexKeys) {
        final Map<Long, Counts> counts = new HashMap<>();

        if (spexKeys.isEmpty()) {
            return Map.of();
        }
        each("SELECT id, parent_id IS NOT NULL, %s FROM spex WHERE id IN (:ids)".formatted(IMAGE.formatted("poster_id")), spexKeys,
                rs -> {
                    final Counts c = new Counts();

                    c.revival = rs.getBoolean(2);
                    c.poster = rs.getBoolean(3);
                    counts.put(rs.getLong(1), c);
                });
        each("SELECT spex_id, premiere IS NOT NULL, %s, %s, %s, %s, %s, vest_worn_by_spexare IS NOT NULL OR %s, %s, %s FROM spex_page WHERE spex_id IN (:ids)"
                        .formatted(FILLED.formatted("introduction"), FILLED.formatted("short_description"), FILLED.formatted("description"),
                                FILLED.formatted("synopsis"), FILLED.formatted("vest_description"), FILLED.formatted("vest_worn_by_name"),
                                IMAGE.formatted("rebus_image_id"), IMAGE.formatted("rebus_solution_id")), spexKeys,
                rs -> {
                    final Counts c = counts.get(rs.getLong(1));

                    if (c != null) {
                        final Item[] items = {Item.PREMIERE, Item.INTRODUCTION, Item.SHORT_DESCRIPTION, Item.DESCRIPTION, Item.SYNOPSIS,
                                Item.VEST_DESCRIPTION, Item.VEST_WORN_BY, Item.REBUS, Item.REBUS_SOLUTION};

                        for (int i = 0; i < items.length; i++) {
                            c.page.put(items[i], rs.getBoolean(i + 2));
                        }
                    }
                });
        each("SELECT f.spex_id, COUNT(*) FROM spex_fact f JOIN kind k ON k.id = f.kind_id AND k.retired = 0 WHERE f.spex_id IN (:ids) AND %s GROUP BY f.spex_id"
                        .formatted(FILLED.formatted("f.value")), spexKeys,
                rs -> with(counts, rs.getLong(1), c -> c.facts = rs.getInt(2)));
        // By the sections' content: the image sections together (IMAGES), and the vest
        each("""
                        SELECT p.spex_id, s.content, COUNT(*) FROM spex_page_image p JOIN image i ON i.id = p.image_id AND i.trashed_at IS NULL
                        JOIN kind s ON s.id = p.section_id AND s.retired = 0 WHERE p.spex_id IN (:ids) GROUP BY p.spex_id, s.content""", spexKeys,
                rs -> with(counts, rs.getLong(1), c -> c.images.put(rs.getString(2), rs.getInt(3))));
        each("SELECT spex_id, section, COUNT(*) FROM spex_page_photo WHERE spex_id IN (:ids) AND character_id = 0 GROUP BY spex_id, section", spexKeys,
                rs -> with(counts, rs.getLong(1), c -> c.photos.put(rs.getString(2), rs.getInt(3))));
        each("""
                        SELECT c.spex_id, COUNT(*), SUM(%s), SUM(EXISTS (SELECT 1 FROM character_image ci JOIN image i ON i.id = ci.image_id AND i.trashed_at IS NULL WHERE ci.character_id = c.id))
                        FROM spex_character c WHERE c.spex_id IN (:ids) AND c.retired = 0 GROUP BY c.spex_id""".formatted(FILLED.formatted("c.description")), spexKeys,
                rs -> with(counts, rs.getLong(1), c -> {
                    c.characters = rs.getInt(2);
                    c.characterDescriptions = rs.getInt(3);
                    c.characterImages = rs.getInt(4);
                }));
        each("SELECT spex_id, COUNT(*), SUM(%s) FROM scene WHERE spex_id IN (:ids) GROUP BY spex_id".formatted(FILLED.formatted("place")), spexKeys,
                rs -> with(counts, rs.getLong(1), c -> {
                    c.scenes = rs.getInt(2);
                    c.scenePlaces = rs.getInt(3);
                }));
        each("""
                        SELECT n.spex_id, COUNT(*), SUM(%s),
                               SUM(EXISTS (SELECT 1 FROM number_credit nc WHERE nc.number_id = n.id)),
                               SUM(EXISTS (SELECT 1 FROM number_character nh WHERE nh.number_id = n.id))
                        FROM spex_number n WHERE n.spex_id IN (:ids) AND n.retired = 0 GROUP BY n.spex_id""".formatted(FILLED.formatted("n.original_title")), spexKeys,
                rs -> with(counts, rs.getLong(1), c -> {
                    c.numbers = rs.getInt(2);
                    c.numberOriginals = rs.getInt(3);
                    c.numberCredits = rs.getInt(4);
                    c.numberCharacters = rs.getInt(5);
                }));

        final int factKinds = jdbc.sql("SELECT COUNT(*) FROM kind WHERE type = 'FACT' AND retired = 0").query(Integer.class).single();
        final Map<Long, CompletenessDto> result = new HashMap<>();

        counts.forEach((key, c) -> result.put(key, completeness(c, factKinds)));
        return result;
    }

    private void each(final String sql, final Collection<Long> spexKeys, final RowCallbackHandler handler) {
        jdbc.sql(sql).param("ids", spexKeys).query(handler);
    }

    private interface Update {
        void apply(Counts counts) throws java.sql.SQLException;
    }

    private static void with(final Map<Long, Counts> counts, final long key, final Update update) throws java.sql.SQLException {
        final Counts c = counts.get(key);

        if (c != null) {
            update.apply(c);
        }
    }

    private static double one(final boolean filled) {
        return filled ? 1 : 0;
    }

    private static double share(final int filled, final int of) {
        return of == 0 ? 0 : (double) filled / of;
    }

    private static CompletenessDto completeness(final Counts c, final int factKinds) {
        final List<SectionDto> sections = new ArrayList<>();
        final List<ItemDto> photos = List.of(
                new ItemDto(Item.GROUP_PHOTOS, one(c.photos.getOrDefault("GROUP", 0) > 0)),
                new ItemDto(Item.BOARD_PHOTOS, one(c.photos.getOrDefault("BOARD", 0) > 0)));

        sections.add(section(Section.BASE, List.of(new ItemDto(Item.POSTER, one(c.poster)))));
        if (c.revival) {
            sections.add(section(Section.TEXTS, List.of(page(c, Item.PREMIERE))));
        } else {
            sections.add(section(Section.TEXTS, List.of(page(c, Item.PREMIERE), page(c, Item.INTRODUCTION), page(c, Item.SHORT_DESCRIPTION),
                    page(c, Item.DESCRIPTION), page(c, Item.SYNOPSIS), page(c, Item.VEST_DESCRIPTION), page(c, Item.VEST_WORN_BY))));
        }
        if (factKinds > 0) {
            sections.add(section(Section.FACTS, List.of(new ItemDto(Item.FACTS, share(Math.min(c.facts, factKinds), factKinds)))));
        }
        if (c.revival) {
            sections.add(section(Section.MEDIA, photos));
        } else {
            final List<ItemDto> media = new ArrayList<>(List.of(
                    new ItemDto(Item.IMAGES, one(c.images.getOrDefault("IMAGES", 0) > 0)),
                    new ItemDto(Item.VEST, one(c.images.getOrDefault("VEST", 0) > 0)),
                    page(c, Item.REBUS), page(c, Item.REBUS_SOLUTION)));

            media.addAll(photos);
            sections.add(section(Section.MEDIA, media));
            sections.add(section(Section.CHARACTERS, List.of(
                    new ItemDto(Item.CHARACTERS, one(c.characters > 0)),
                    new ItemDto(Item.CHARACTER_DESCRIPTIONS, share(c.characterDescriptions, c.characters)),
                    new ItemDto(Item.CHARACTER_IMAGES, share(c.characterImages, c.characters)))));
            sections.add(section(Section.SCENES, List.of(
                    new ItemDto(Item.SCENES, one(c.scenes > 0)),
                    new ItemDto(Item.SCENE_PLACES, share(c.scenePlaces, c.scenes)))));
            sections.add(section(Section.NUMBERS, List.of(
                    new ItemDto(Item.NUMBERS, one(c.numbers > 0)),
                    new ItemDto(Item.NUMBER_ORIGINALS, share(c.numberOriginals, c.numbers)),
                    new ItemDto(Item.NUMBER_CREDITS, share(c.numberCredits, c.numbers)),
                    new ItemDto(Item.NUMBER_CHARACTERS, share(c.numberCharacters, c.numbers)))));
        }

        final List<ItemDto> items = sections.stream().flatMap(section -> section.items().stream()).toList();

        return new CompletenessDto(items.stream().mapToDouble(ItemDto::filled).sum() / items.size(), sections);
    }

    private static ItemDto page(final Counts c, final Item item) {
        return new ItemDto(item, one(c.page.getOrDefault(item, false)));
    }

    private static SectionDto section(final Section section, final List<ItemDto> items) {
        return new SectionDto(section, items.stream().mapToDouble(ItemDto::filled).sum() / items.size(), items);
    }
}
