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

import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.basedata.Spex;
import nu.fgv.spexipedia.server.kind.Kind;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.kind.KindType;
import nu.fgv.spexipedia.server.kind.SectionContent;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.StoredText;
import nu.fgv.spexipedia.server.util.Texts;
import nu.fgv.spexipedia.server.util.Uuids;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The order of the sections of spex pages (spec 4.2). The section kinds' order is every page's default; a spex may keep
 * its own list (table {@code spex_section}), which hides sections, moves them and adds sections that show material of
 * the other applications (4.2.1). A revival without a list shows its original's. Section kinds the list does not name
 * take their place by the default order, so that a kind added later is on every page.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class SpexSectionService {

    static final int MAX_SECTIONS = 100;
    static final int MAX_ITEMS = 100;

    private final JdbcClient jdbc;
    private final KindService kindService;
    private final BaseDataService baseData;
    private final CallerService callerService;
    private final AuditService auditService;

    public SpexSectionService(final JdbcClient jdbc, final KindService kindService, final BaseDataService baseData,
                              final CallerService callerService, final AuditService auditService) {
        this.jdbc = jdbc;
        this.kindService = kindService;
        this.baseData = baseData;
        this.callerService = callerService;
        this.auditService = auditService;
    }

    /**
     * Where a page's order comes from.
     */
    public enum SectionOrder {
        /** The section kinds' order. */
        DEFAULT,
        /** The spex' own list. */
        OWN,
        /** A revival's original's list. */
        ORIGINAL
    }

    /**
     * A section of a spex page, in the page's order.
     *
     * @param id       the row's id in the spex' (or original's) list; the kind's id for a section by the default order
     * @param spexKey  the spex the page is of
     * @param ownTitle the section's own heading; the kind's name is shown when null
     */
    public record PageSectionDto(UUID id, Kind kind, long spexKey, boolean hidden, @Nullable String ownTitle,
                                 @Nullable UUID refId, List<UUID> itemIds, @Nullable Integer startSeconds) {

        public SectionContent content() {
            return kind.getContent();
        }
    }

    /**
     * A section an author places; {@code id} keeps a row's id from one save to the next.
     */
    public record SectionInput(@Nullable UUID id, UUID kindId, boolean hidden, @Nullable String title,
                               @Nullable UUID refId, @Nullable List<UUID> itemIds, @Nullable Integer startSeconds) {
    }

    private record Row(UUID id, long kindKey, boolean hidden, @Nullable String title, @Nullable UUID refId,
                       List<UUID> itemIds, @Nullable Integer startSeconds) {
    }

    private List<Row> rows(final long spexKey) {
        return jdbc.sql("SELECT uuid, kind_id, hidden, title, ref_id, item_ids, start_seconds FROM spex_section WHERE spex_id = :s ORDER BY position")
                .param("s", spexKey)
                .query((rs, _) -> new Row(UUID.fromString(rs.getString(1)), rs.getLong(2), rs.getBoolean(3), rs.getString(4),
                        rs.getString(5) == null ? null : UUID.fromString(rs.getString(5)), itemIds(rs.getString(6)),
                        rs.getObject(7) == null ? null : rs.getInt(7)))
                .list();
    }

    private static List<UUID> itemIds(final @Nullable String text) {
        return text == null || text.isBlank() ? List.of()
                : Arrays.stream(text.split("\\R")).map(String::strip).filter(id -> !id.isEmpty()).map(UUID::fromString).toList();
    }

    @Transactional(readOnly = true)
    public SectionOrder order(final SpexDto spex) {
        if (hasRows(spex.key())) {
            return SectionOrder.OWN;
        }
        return spex.parentKey() != null && hasRows(spex.parentKey()) ? SectionOrder.ORIGINAL : SectionOrder.DEFAULT;
    }

    private boolean hasRows(final long spexKey) {
        return jdbc.sql("SELECT COUNT(*) FROM spex_section WHERE spex_id = :s").param("s", spexKey).query(Integer.class).single() > 0;
    }

    /**
     * The sections of a spex' page, in order: its own list, else (for a revival) its original's, merged with the default
     * order. Retired section kinds are left out, and hidden sections unless asked for.
     */
    @Transactional(readOnly = true)
    public List<PageSectionDto> sections(final SpexDto spex, final boolean includeHidden) {
        List<Row> rows = rows(spex.key());

        if (rows.isEmpty() && spex.parentKey() != null) {
            rows = rows(spex.parentKey());
        }

        final List<Kind> all = kindService.kinds(KindType.SECTION, null, true);
        final Map<Long, Kind> kinds = all.stream().collect(Collectors.toMap(Kind::getId, Function.identity()));
        final List<PageSectionDto> sections = new ArrayList<>();

        for (final Row row : rows) {
            final Kind kind = kinds.get(row.kindKey());

            if (kind != null && !kind.isRetired()) {
                sections.add(new PageSectionDto(row.id(), kind, spex.key(), row.hidden(), row.title(), row.refId(),
                        row.itemIds(), row.startSeconds()));
            }
        }

        final Set<Long> placed = sections.stream().map(section -> section.kind().getId()).collect(Collectors.toSet());

        for (final Kind kind : all) {
            if (kind.isRetired() || kind.getContent() == null || kind.getContent().external() || placed.contains(kind.getId())) {
                continue;
            }
            // After the last section of the list that comes before it in the default order
            int at = 0;

            for (int i = 0; i < sections.size(); i++) {
                final Kind other = sections.get(i).kind();

                if (!other.getContent().external() && other.getPosition() < kind.getPosition()) {
                    at = i + 1;
                }
            }
            sections.add(at, new PageSectionDto(kind.getUuid(), kind, spex.key(), false, null, null, List.of(), null));
        }
        return includeHidden ? sections : sections.stream().filter(section -> !section.hidden()).toList();
    }

    /**
     * AUTHOR. The spex' own list of sections, replacing the one it had; an empty list goes back to the default order (a
     * revival to its original's). A section kind of a content with one kind, or an image section, may be in the list
     * once; sections of the other applications need the id of their material there.
     */
    @Transactional
    public void set(final UUID spexId, final List<SectionInput> sections) {
        callerService.current().requireAuthor();

        final Spex spex = baseData.requireSpex(spexId);

        if (sections.size() > MAX_SECTIONS) {
            throw new BadRequestException("error.tooMany", MAX_SECTIONS);
        }

        final Map<Long, Row> current = rows(spex.getId()).stream().collect(Collectors.toMap(Row::kindKey, Function.identity(), (a, _) -> a));
        final Set<UUID> currentIds = rows(spex.getId()).stream().map(Row::id).collect(Collectors.toSet());
        final Set<Long> once = new HashSet<>();
        final Set<UUID> ids = new HashSet<>();
        final List<Row> rows = new ArrayList<>();

        for (final SectionInput input : sections) {
            final Kind kind = kindService.require(input.kindId(), KindType.SECTION, null,
                    current.containsKey(kindKey(input.kindId())) ? kindService.kind(input.kindId()).orElse(null) : null);
            final SectionContent content = kind.getContent();
            final boolean external = content != null && content.external();

            if (!external && !once.add(kind.getId())) {
                throw new BadRequestException("error.section.twice", kind.getNameSv());
            }
            if (external != (input.refId() != null)) {
                throw new BadRequestException("error.section.material", kind.getNameSv());
            }

            final List<UUID> items = content == SectionContent.SPEXGRAM && input.itemIds() != null
                    ? input.itemIds().stream().distinct().toList() : List.of();

            if (items.size() > MAX_ITEMS) {
                throw new BadRequestException("error.tooMany", MAX_ITEMS);
            }

            final Integer start = content == SectionContent.SPEXFLIX_VIDEO && input.startSeconds() != null
                    ? Math.max(0, input.startSeconds()) : null;
            // A row keeps its id across saves; new rows, and ids given twice or not the spex', get new ones
            final UUID id = input.id() != null && currentIds.contains(input.id()) && ids.add(input.id()) ? input.id() : Uuids.v7();

            ids.add(id);
            rows.add(new Row(id, kind.getId(), input.hidden(), Texts.optional(input.title(), 200, "title"),
                    external ? input.refId() : null, items, start));
        }

        jdbc.sql("DELETE FROM spex_section WHERE spex_id = :s").param("s", spex.getId()).update();
        for (int i = 0; i < rows.size(); i++) {
            final Row row = rows.get(i);

            jdbc.sql("""
                            INSERT INTO spex_section (uuid, spex_id, kind_id, position, hidden, title, ref_id, item_ids, start_seconds)
                            VALUES (:uuid, :s, :k, :p, :h, :t, :r, :items, :start)""")
                    .param("uuid", row.id().toString()).param("s", spex.getId()).param("k", row.kindKey()).param("p", i)
                    .param("h", row.hidden()).param("t", row.title())
                    .param("r", row.refId() == null ? null : row.refId().toString())
                    .param("items", row.itemIds().isEmpty() ? null : row.itemIds().stream().map(UUID::toString).collect(Collectors.joining("\n")))
                    .param("start", row.startSeconds())
                    .update();
        }
        auditService.record("UPDATE", "SPEX_PAGE", spex.getUuid(), StoredText.of(rows.isEmpty()
                ? "audit.spexPage.sectionsDefault" : "audit.spexPage.sections", spex.label()));
    }

    private long kindKey(final UUID kindId) {
        return kindService.kind(kindId).map(Kind::getId).orElse(-1L);
    }
}
