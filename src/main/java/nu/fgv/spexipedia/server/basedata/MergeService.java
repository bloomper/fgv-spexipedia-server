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

package nu.fgv.spexipedia.server.basedata;

import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.feed.ChangeFeedService;
import nu.fgv.spexipedia.server.feed.ChangeType;
import nu.fgv.spexipedia.server.feed.FeedRecords;
import nu.fgv.spexipedia.server.search.IndexEvent;
import nu.fgv.spexipedia.server.search.SearchKind;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Retiring and merging base data (ADMIN, section 4.1 of the spec). Base data is never deleted while anything may refer
 * to it: a retired record stays readable by id; a merged record's references are moved to the record it was merged
 * into, here and, through the feed's MERGE, in the other applications, and its id stays as an alias.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class MergeService {

    /**
     * The tables and columns that refer to each kind, by internal key; moved on a merge.
     */
    private static final Map<BaseDataKind, List<String>> REFERENCES = Map.of(
            BaseDataKind.SPEX_CATEGORY, List.of("spex.category_id"),
            BaseDataKind.SPEX, List.of("spex.parent_id", "spex_character.spex_id", "scene.spex_id", "spex_number.spex_id",
                    "recording.spex_id"),
            BaseDataKind.TASK_CATEGORY, List.of("task.category_id"),
            BaseDataKind.TASK, List.of(),
            BaseDataKind.CHARACTER, List.of("spex_page_photo.character_id"),
            BaseDataKind.NUMBER, List.of("recording_track.number_id", "book_song.number_id")
    );

    private final JdbcClient jdbc;
    private final BaseDataService baseData;
    private final IdAliasRepository aliasRepository;
    private final ChangeFeedService feed;
    private final CallerService callerService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public MergeService(final JdbcClient jdbc, final BaseDataService baseData, final IdAliasRepository aliasRepository,
                        final ChangeFeedService feed, final CallerService callerService, final AuditService auditService,
                        final ApplicationEventPublisher events, final Clock clock) {
        this.jdbc = jdbc;
        this.baseData = baseData;
        this.aliasRepository = aliasRepository;
        this.feed = feed;
        this.callerService = callerService;
        this.auditService = auditService;
        this.events = events;
        this.clock = clock;
    }

    private static String table(final BaseDataKind kind) {
        return switch (kind) {
            case SPEX_CATEGORY -> "spex_category";
            case SPEX -> "spex";
            case TASK_CATEGORY -> "task_category";
            case TASK -> "task";
            case CHARACTER -> "spex_character";
            case NUMBER -> "spex_number";
        };
    }

    private long key(final BaseDataKind kind, final UUID id) {
        return jdbc.sql("SELECT id FROM " + table(kind) + " WHERE uuid = :uuid")
                .param("uuid", baseData.resolve(kind, id).toString())
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound.record", id));
    }

    private UUID uuid(final BaseDataKind kind, final long key) {
        return UUID.fromString(jdbc.sql("SELECT uuid FROM " + table(kind) + " WHERE id = :id").param("id", key).query(String.class).single());
    }

    /**
     * Retires a record, or brings it back.
     */
    @Transactional
    public boolean retire(final BaseDataKind kind, final UUID id, final boolean retired) {
        callerService.current().requireAdmin();

        final long key = key(kind, id);
        final UUID uuid = uuid(kind, key);

        if (kind == BaseDataKind.SPEX && !retired) {
            baseData.requireFreeYearToUnretire(uuid);
        }
        jdbc.sql("UPDATE " + table(kind) + " SET retired = :retired WHERE id = :id").param("retired", retired).param("id", key).update();
        feed.record(kind, uuid, retired ? ChangeType.RETIRE : ChangeType.UPSERT, null, snapshot(kind, key));
        index(kind, uuid);
        auditService.record(retired ? "RETIRE" : "UNRETIRE", kind.name(), uuid,
                (retired ? "Deaktiverade " : "Aktiverade ") + label(kind, key));
        return true;
    }

    /**
     * Merges a record into another of the same kind: moves what refers to it, keeps its id as an alias, records MERGE
     * in the feed and deletes it.
     */
    @Transactional
    public boolean merge(final BaseDataKind kind, final UUID id, final UUID intoId) {
        callerService.current().requireAdmin();

        final long from = key(kind, id);
        final long into = key(kind, intoId);

        if (from == into) {
            throw new BadRequestException("error.merge.same");
        }

        final UUID fromUuid = uuid(kind, from);
        final UUID intoUuid = uuid(kind, into);
        final String summary = "Slog ihop %s med %s".formatted(label(kind, from), label(kind, into));

        if (kind == BaseDataKind.SPEX && jdbc.sql("SELECT COUNT(*) FROM spex WHERE id = :into AND parent_id = :from")
                .param("into", into).param("from", from).query(Long.class).single() > 0) {
            throw new BadRequestException("error.merge.revivalIntoParent");
        }
        moveReferences(kind, from, into, fromUuid, intoUuid);
        feed.record(kind, fromUuid, ChangeType.MERGE, intoUuid, snapshot(kind, into));
        aliasRepository.retarget(fromUuid, intoUuid);

        final IdAlias alias = new IdAlias();

        alias.setAlias(fromUuid);
        alias.setKind(kind);
        alias.setTarget(intoUuid);
        alias.setMergedAt(clock.instant());
        aliasRepository.save(alias);
        jdbc.sql("DELETE FROM " + table(kind) + " WHERE id = :id").param("id", from).update();
        index(kind, fromUuid);
        index(kind, intoUuid);
        auditService.record("MERGE", kind.name(), fromUuid, summary);
        return true;
    }

    private void moveReferences(final BaseDataKind kind, final long from, final long into, final UUID fromUuid, final UUID intoUuid) {
        for (final String reference : REFERENCES.get(kind)) {
            final String[] parts = reference.split("\\.");

            jdbc.sql("UPDATE %s SET %s = :into WHERE %s = :from".formatted(parts[0], parts[1], parts[1]))
                    .param("into", into).param("from", from).update();
        }
        switch (kind) {
            case SPEX -> {
                // The merged spex's page goes with it; the other's page is kept
                jdbc.sql("DELETE FROM spex_page_image WHERE spex_id = :from").param("from", from).update();
                jdbc.sql("DELETE FROM spex_page_photo WHERE spex_id = :from").param("from", from).update();
                jdbc.sql("DELETE FROM spex_page WHERE spex_id = :from").param("from", from).update();
                jdbc.sql("DELETE FROM revival_change WHERE revival_id = :from").param("from", from).update();
            }
            case CHARACTER -> {
                jdbc.sql("UPDATE IGNORE number_character SET character_id = :into WHERE character_id = :from")
                        .param("into", into).param("from", from).update();
                jdbc.sql("UPDATE IGNORE revival_change SET record_id = :into WHERE record_id = :from AND kind = 'CHARACTER'")
                        .param("into", intoUuid.toString()).param("from", fromUuid.toString()).update();
            }
            case NUMBER -> {
                jdbc.sql("UPDATE IGNORE revival_change SET record_id = :into WHERE record_id = :from AND kind = 'NUMBER'")
                        .param("into", intoUuid.toString()).param("from", fromUuid.toString()).update();
            }
            default -> {
                // Nothing beyond the references above
            }
        }
    }

    private void index(final BaseDataKind kind, final UUID uuid) {
        final SearchKind searchKind = switch (kind) {
            case SPEX -> SearchKind.SPEX;
            case CHARACTER -> SearchKind.CHARACTER;
            case NUMBER -> SearchKind.NUMBER;
            default -> null;
        };

        if (searchKind != null) {
            events.publishEvent(new IndexEvent(searchKind, uuid));
        }
    }

    private String label(final BaseDataKind kind, final long key) {
        return switch (kind) {
            case SPEX -> baseData.requireSpexByKey(key).label();
            case CHARACTER, NUMBER, SPEX_CATEGORY, TASK_CATEGORY -> "%s %s".formatted(kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '),
                    jdbc.sql("SELECT %s FROM %s WHERE id = :id".formatted(kind == BaseDataKind.NUMBER ? "title" : "name", table(kind)))
                            .param("id", key).query(String.class).single());
            case TASK -> "post " + jdbc.sql("SELECT name FROM task WHERE id = :id").param("id", key).query(String.class).single();
        };
    }

    /**
     * The record as the feed carries it, read fresh.
     */
    private Object snapshot(final BaseDataKind kind, final long key) {
        return switch (kind) {
            case SPEX_CATEGORY -> baseData.feedRecord(baseData.requireCategory(uuidOf("spex_category", key)));
            case SPEX -> baseData.feedRecord(baseData.requireSpexByKey(key));
            case TASK_CATEGORY -> baseData.feedRecord(baseData.requireTaskCategory(uuidOf("task_category", key)));
            case TASK -> baseData.feedRecord(baseData.requireTask(uuidOf("task", key)));
            case CHARACTER -> jdbc.sql("""
                            SELECT c.uuid, s.uuid AS spex, c.name, c.position, c.retired
                            FROM spex_character c JOIN spex s ON s.id = c.spex_id WHERE c.id = :id""")
                    .param("id", key)
                    .query((rs, _) -> new FeedRecords.FeedCharacter(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                            rs.getString(3), rs.getInt(4), rs.getBoolean(5)))
                    .single();
            case NUMBER -> jdbc.sql("""
                            SELECT n.uuid, s.uuid AS spex, n.act, n.position, n.title, n.original_title, n.original_artist, n.retired
                            FROM spex_number n JOIN spex s ON s.id = n.spex_id WHERE n.id = :id""")
                    .param("id", key)
                    .query((rs, _) -> new FeedRecords.FeedNumber(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                            rs.getInt(3), rs.getInt(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getBoolean(8)))
                    .single();
        };
    }

    private UUID uuidOf(final String table, final long key) {
        return UUID.fromString(jdbc.sql("SELECT uuid FROM " + table + " WHERE id = :id").param("id", key).query(String.class).single());
    }
}
