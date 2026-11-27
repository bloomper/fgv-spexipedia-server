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

package nu.fgv.spexipedia.server.kind;

import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.util.StoredText;
import nu.fgv.spexipedia.server.basedata.BaseDataKind;
import nu.fgv.spexipedia.server.feed.ChangeFeedService;
import nu.fgv.spexipedia.server.feed.FeedRecords;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Texts;
import nu.fgv.spexipedia.server.util.Uuids;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The kinds admins keep (spec 4.6). Anyone reads them; ADMIN changes them. A kind in use is retired or merged into
 * another of the same group; only a kind nothing refers to is deleted. The import finds kinds by name or alias ({@link #match}).
 *
 * <p>A group is a type of kinds, for image kinds also their use, and for image kinds of an image section that section
 * (a section kind of content {@link SectionContent#IMAGES}). Section kinds have a content: the contents with one kind
 * ({@link SectionContent#single()}) are created by the first start, never by admins, and are not merged.</p>
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class KindService {

    /**
     * Where each type of kind is referred to, and what a merge does where both kinds are there: a number or a
     * character's image has one kind, so it just changes; credits and a page's images are lists per kind, so the merged
     * kind's come after the other's; a spex has one fact of a kind, so the one already there is kept.
     */
    private static final Map<KindType, List<Reference>> REFERENCES = Map.of(
            KindType.NUMBER, List.of(new Reference("spex_number", List.of(), false)),
            KindType.CREDIT, List.of(new Reference("number_credit", List.of("number_id"), true)),
            KindType.IMAGE, List.of(new Reference("character_image", List.of(), false),
                    new Reference("spex_page_image", List.of("spex_id", "section_id"), true)),
            KindType.FACT, List.of(new Reference("spex_fact", List.of("spex_id"), false)),
            KindType.SECTION, List.of());

    private final KindRepository repository;
    private final JdbcClient jdbc;
    private final CallerService callerService;
    private final AuditService auditService;
    private final ChangeFeedService feed;

    public KindService(final KindRepository repository, final JdbcClient jdbc, final CallerService callerService,
                       final AuditService auditService, final ChangeFeedService feed) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.callerService = callerService;
        this.auditService = auditService;
        this.feed = feed;
    }

    // Reading --------------------------------------------------------------------------------------------------------

    /**
     * The kinds of a type (and use, for image kinds other than an image section's), in order.
     */
    @Transactional(readOnly = true)
    public List<Kind> kinds(final KindType type, final @Nullable KindUse use, final boolean includeRetired) {
        requireUse(type, use, null);
        return group(type, use, null, includeRetired);
    }

    /**
     * The image kinds of an image section, in order.
     */
    @Transactional(readOnly = true)
    public List<Kind> imageKinds(final Kind section, final boolean includeRetired) {
        requireImageSection(section);
        return group(KindType.IMAGE, KindUse.SECTION, section.getId(), includeRetired);
    }

    /**
     * The image kinds of many image sections, by section key, in order.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<Kind>> imageKinds(final Collection<Long> sectionKeys, final boolean includeRetired) {
        return sectionKeys.isEmpty() ? Map.of() : repository.findBySectionKeyInOrderByPositionAsc(sectionKeys).stream()
                .filter(kind -> includeRetired || !kind.isRetired())
                .collect(Collectors.groupingBy(Kind::getSectionKey, java.util.LinkedHashMap::new, Collectors.toList()));
    }

    private List<Kind> group(final KindType type, final @Nullable KindUse use, final @Nullable Long sectionKey,
                             final boolean includeRetired) {
        return repository.findGroup(type, use == null ? "" : use.name(), sectionKey).stream()
                .filter(kind -> includeRetired || !kind.isRetired())
                .toList();
    }

    private List<Kind> groupOf(final Kind kind, final boolean includeRetired) {
        return group(kind.getType(), kind.getUse(), kind.getSectionKey(), includeRetired);
    }

    private static boolean sameGroup(final Kind a, final Kind b) {
        return a.getType() == b.getType() && a.getUse() == b.getUse() && java.util.Objects.equals(a.getSectionKey(), b.getSectionKey());
    }

    /**
     * The section kind of a content with one kind (e.g. VEST); empty when it was never created.
     */
    @Transactional(readOnly = true)
    public Optional<Kind> section(final SectionContent content) {
        return group(KindType.SECTION, null, null, true).stream().filter(kind -> kind.getContent() == content).findFirst();
    }

    /**
     * A section kind of content IMAGES; a bad request otherwise.
     */
    @Transactional(readOnly = true)
    public Kind requireImageSection(final UUID id) {
        final Kind section = repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Kind.class, id));

        requireImageSection(section);
        return section;
    }

    private static void requireImageSection(final Kind section) {
        if (section.getType() != KindType.SECTION || section.getContent() != SectionContent.IMAGES) {
            throw new BadRequestException("error.kind.wrongType", section.getNameSv());
        }
    }

    @Transactional(readOnly = true)
    public Optional<Kind> kind(final UUID id) {
        return repository.findByUuid(id);
    }

    @Transactional(readOnly = true)
    public Map<Long, Kind> byKeys(final Collection<Long> keys) {
        return repository.findAllById(keys).stream().collect(Collectors.toMap(Kind::getId, Function.identity()));
    }

    /**
     * The kind with this id, which must be of the type and use; a bad request otherwise. Retired kinds are refused for
     * new use unless {@code allowRetired}, which keeps a record's own old kind when it is saved again.
     */
    @Transactional(readOnly = true)
    public Kind require(final UUID id, final KindType type, final @Nullable KindUse use, final @Nullable Kind current) {
        final Kind kind = repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Kind.class, id));

        if (kind.getType() != type || kind.getUse() != use || (use != KindUse.SECTION && kind.getSectionKey() != null)) {
            throw new BadRequestException("error.kind.wrongType", kind.getNameSv());
        }
        if (kind.isRetired() && (current == null || !current.getId().equals(kind.getId()))) {
            throw new BadRequestException("error.kind.retired", kind.getNameSv());
        }
        return kind;
    }

    /**
     * An image kind of the image section, as {@link #require}.
     */
    @Transactional(readOnly = true)
    public Kind requireImageKind(final UUID id, final Kind section, final @Nullable Kind current) {
        final Kind kind = require(id, KindType.IMAGE, KindUse.SECTION, current);

        if (!section.getId().equals(kind.getSectionKey())) {
            throw new BadRequestException("error.kind.wrongType", kind.getNameSv());
        }
        return kind;
    }

    /**
     * The first kind of a type and use that is not retired: the default.
     */
    @Transactional(readOnly = true)
    public Optional<Kind> first(final KindType type, final @Nullable KindUse use) {
        return kinds(type, use, false).stream().findFirst();
    }

    /**
     * The first image kind of an image section that is not retired: the default.
     */
    @Transactional(readOnly = true)
    public Optional<Kind> firstImageKind(final Kind section) {
        return imageKinds(section, false).stream().findFirst();
    }

    /**
     * The kind whose Swedish or English name or one of whose aliases is the text, ignoring case, accents, punctuation
     * and blanks; retired kinds too, since old material uses old names. Empty when nothing matches.
     */
    @Transactional(readOnly = true)
    public Optional<Kind> match(final KindType type, final @Nullable KindUse use, final @Nullable String text) {
        return match(kinds(type, use, true), text);
    }

    /**
     * An image kind of the image section by name or alias, as {@link #match(KindType, KindUse, String)}.
     */
    @Transactional(readOnly = true)
    public Optional<Kind> matchImageKind(final Kind section, final @Nullable String text) {
        return match(imageKinds(section, true), text);
    }

    private static Optional<Kind> match(final List<Kind> kinds, final @Nullable String text) {
        final String wanted = normalize(text);

        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        return kinds.stream().filter(kind -> !kind.isRetired() && names(kind).contains(wanted)).findFirst()
                .or(() -> kinds.stream().filter(kind -> names(kind).contains(wanted)).findFirst());
    }

    private static Set<String> names(final Kind kind) {
        final Set<String> names = new java.util.HashSet<>();

        names.add(normalize(kind.getNameSv()));
        names.add(normalize(kind.getNameEn()));
        kind.aliasList().forEach(alias -> names.add(normalize(alias)));
        return names;
    }

    /**
     * Lower case, without accents except å, ä and ö, punctuation and blanks: "Sång, trio med komp." and "sång trio med
     * komp" are the same.
     */
    public static String normalize(final @Nullable String text) {
        if (text == null) {
            return "";
        }

        final String lower = text.strip().toLowerCase(Locale.ROOT)
                .replace("å", "\u0001").replace("ä", "\u0002").replace("ö", "\u0003");
        final String plain = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");

        return plain.replaceAll("[^\\p{L}\\p{N}\u0001\u0002\u0003]+", " ").strip()
                .replace("\u0001", "å").replace("\u0002", "ä").replace("\u0003", "ö");
    }

    // Changing -------------------------------------------------------------------------------------------------------

    @Transactional
    public Kind save(final @Nullable UUID id, final KindInput input) {
        callerService.current().requireAdmin();

        final Kind kind = id == null ? newKind(input) : repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Kind.class, id));

        kind.setNameSv(Texts.required(input.nameSv(), 100, "nameSv"));
        kind.setNameEn(Texts.required(input.nameEn(), 100, "nameEn"));
        kind.setAliases(aliases(input.aliases()));
        if (kind.getType() == KindType.FACT) {
            kind.setValueType(input.valueType() == null ? FactValueType.TEXT : input.valueType());
        } else if (input.valueType() != null) {
            throw new BadRequestException("error.field.invalid", "valueType");
        }
        if (kind.getType() == KindType.SECTION) {
            kind.setSubtitleSv(Texts.optional(input.subtitleSv(), 200, "subtitleSv"));
            kind.setSubtitleEn(Texts.optional(input.subtitleEn(), 200, "subtitleEn"));
        } else if (input.subtitleSv() != null || input.subtitleEn() != null) {
            throw new BadRequestException("error.field.invalid", "subtitle");
        }
        repository.save(kind);
        if (id == null) {
            firstImageKind(kind, kind.getNameSv(), kind.getNameEn());
        }
        auditService.record(id == null ? "CREATE" : "UPDATE", "KIND", kind.getUuid(),
                StoredText.of(id == null ? "audit.kind.create" : "audit.kind.update", name(kind), type(kind.getType())));
        return kind;
    }

    /**
     * IMPORT. A new kind with this name in both languages, for an import that may create the kinds it meets
     * ({@code createMissingKinds}).
     */
    @Transactional
    public Kind createForImport(final KindType type, final @Nullable KindUse use, final String name) {
        return createForImport(new KindInput(type, use, null, null, null, name, name, null, null, null));
    }

    /**
     * IMPORT. A new image section with this name, with one image kind of the same name; see {@link #createForImport}.
     */
    @Transactional
    public Kind createImageSectionForImport(final String name) {
        return createForImport(new KindInput(KindType.SECTION, null, null, SectionContent.IMAGES, null, name, name, null, null, null));
    }

    /**
     * IMPORT. A new image kind of an image section with this name; see {@link #createForImport}.
     */
    @Transactional
    public Kind createImageKindForImport(final Kind section, final String name) {
        return createForImport(new KindInput(KindType.IMAGE, KindUse.SECTION, section.getUuid(), null, null, name, name, null, null, null));
    }

    private Kind createForImport(final KindInput input) {
        callerService.current().requireImport();

        final Kind kind = newKind(input);
        final KindType type = kind.getType();

        kind.setNameSv(Texts.required(input.nameSv(), 100, "name"));
        kind.setNameEn(kind.getNameSv());
        if (type == KindType.FACT) {
            kind.setValueType(FactValueType.TEXT);
        }
        repository.save(kind);
        firstImageKind(kind, kind.getNameSv(), kind.getNameEn());
        auditService.record("CREATE", "KIND", kind.getUuid(), StoredText.of("audit.kind.createForImport", name(kind), type(type)));
        return kind;
    }

    private Kind newKind(final KindInput input) {
        if (input.type() == null) {
            throw new BadRequestException("error.field.invalid", "type");
        }

        final Kind section = input.sectionId() == null ? null : requireImageSection(input.sectionId());

        requireUse(input.type(), input.use(), section);

        final Kind kind = new Kind();

        kind.setUuid(Uuids.v7());
        kind.setType(input.type());
        kind.setUse(input.use());
        kind.setSectionKey(section == null ? null : section.getId());
        if (input.type() == KindType.SECTION) {
            // The contents with one section kind have theirs from the first start
            if (input.content() == null || input.content().single()) {
                throw new BadRequestException("error.kind.content");
            }
            kind.setContent(input.content());
        } else if (input.content() != null) {
            throw new BadRequestException("error.field.invalid", "content");
        }
        kind.setPosition(repository.maxPosition(input.type(), input.use() == null ? "" : input.use().name(), kind.getSectionKey()) + 1);
        return kind;
    }

    /**
     * A new image section gets an image kind of its own name, so that its images have a kind from the start.
     */
    private void firstImageKind(final Kind section, final String nameSv, final String nameEn) {
        if (section.getType() != KindType.SECTION || section.getContent() != SectionContent.IMAGES) {
            return;
        }

        final Kind kind = new Kind();

        kind.setUuid(Uuids.v7());
        kind.setType(KindType.IMAGE);
        kind.setUse(KindUse.SECTION);
        kind.setSectionKey(section.getId());
        kind.setNameSv(nameSv);
        kind.setNameEn(nameEn);
        kind.setPosition(1);
        repository.save(kind);
    }

    private static String aliases(final @Nullable List<String> aliases) {
        if (aliases == null) {
            return "";
        }

        final String joined = aliases.stream().map(Texts::clean).filter(java.util.Objects::nonNull).distinct()
                .collect(Collectors.joining("\n"));

        if (joined.length() > 2000) {
            throw new BadRequestException("error.field.invalid", "aliases");
        }
        return joined;
    }

    /**
     * Image kinds have a use, and those of use SECTION an image section; other kinds have neither.
     */
    private static void requireUse(final KindType type, final @Nullable KindUse use, final @Nullable Kind section) {
        if ((type == KindType.IMAGE) != (use != null)) {
            throw new BadRequestException("error.field.invalid", "use");
        }
        if ((use == KindUse.SECTION) != (section != null)) {
            throw new BadRequestException("error.field.invalid", "sectionId");
        }
    }

    /**
     * The kinds of a group in the given order; every kind of them must be listed. For image kinds of use SECTION, the
     * image section is given. The order of the section kinds is the pages' default order.
     */
    @Transactional
    public List<Kind> reorder(final KindType type, final @Nullable KindUse use, final @Nullable UUID sectionId,
                              final List<UUID> ids) {
        callerService.current().requireAdmin();

        final Kind section = sectionId == null ? null : requireImageSection(sectionId);

        requireUse(type, use, section);

        final Long sectionKey = section == null ? null : section.getId();
        final List<Kind> kinds = group(type, use, sectionKey, true);
        final Map<UUID, Kind> byId = kinds.stream().collect(Collectors.toMap(Kind::getUuid, Function.identity()));

        if (ids.size() != kinds.size() || !byId.keySet().equals(Set.copyOf(ids))) {
            throw new BadRequestException("error.order.incomplete");
        }
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).setPosition(i + 1);
        }
        auditService.record("UPDATE", "KIND", ids.getFirst(), StoredText.of("audit.kind.reorder", type(type)));
        return group(type, use, sectionKey, true);
    }

    @Transactional
    public Kind retire(final UUID id, final boolean retired) {
        callerService.current().requireAdmin();

        final Kind kind = repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Kind.class, id));

        // A section kind may be the last of its content: retired, it is on no page
        if (retired && kind.getType() != KindType.SECTION
                && groupOf(kind, false).stream().allMatch(other -> other.getId().equals(kind.getId()))) {
            throw new BadRequestException("error.kind.last");
        }
        kind.setRetired(retired);
        auditService.record("UPDATE", "KIND", id, StoredText.of(retired ? "audit.kind.retire" : "audit.kind.unretire", name(kind)));
        return kind;
    }

    /**
     * Moves everything that refers to a kind onto another of the same type and use, keeps the merged kind's names as
     * aliases of the other, and deletes it.
     */
    @Transactional
    public Kind merge(final UUID id, final UUID intoId) {
        callerService.current().requireAdmin();

        final Kind from = repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Kind.class, id));
        final Kind into = repository.findByUuid(intoId).orElseThrow(() -> new ResourceNotFoundException(Kind.class, intoId));

        if (from.getId().equals(into.getId()) || !sameGroup(from, into)) {
            throw new BadRequestException("error.kind.wrongType", into.getNameSv());
        }
        if (from.getType() == KindType.SECTION) {
            if (from.getContent() != into.getContent() || from.getContent() == null || from.getContent().single()) {
                throw new BadRequestException("error.kind.content");
            }
            mergeSection(from, into);
        }
        // The numbers of a merged number kind change kind, which the feed's followers copy
        final List<Long> numbers = from.getType() != KindType.NUMBER ? List.of()
                : jdbc.sql("SELECT id FROM spex_number WHERE kind_id = :from").param("from", from.getId()).query(Long.class).list();

        for (final Reference reference : REFERENCES.get(from.getType())) {
            move(reference, from.getId(), into.getId());
        }

        final List<String> aliases = new java.util.ArrayList<>(into.aliasList());

        aliases.add(from.getNameSv());
        aliases.add(from.getNameEn());
        aliases.addAll(from.aliasList());
        into.setAliases(aliases(aliases));
        repository.delete(from);
        FeedRecords.numbers(jdbc, numbers).forEach(number -> feed.upsert(BaseDataKind.NUMBER, number.id(), number));
        auditService.record("MERGE", "KIND", intoId, StoredText.of("audit.kind.merge", name(from), name(into)));
        return into;
    }

    /**
     * Deletes a kind that nothing refers to: one made by mistake, or one whose records are gone. A kind in use is
     * merged or retired instead; the last kind of a group in use stays (it is the default), and the sections of a
     * content with one kind are never deleted. An image section goes with its image kinds, which no image may have,
     * and with the places pages keep for it in their own order; a section of another application that a page shows
     * stays.
     */
    @Transactional
    public void delete(final UUID id) {
        callerService.current().requireAdmin();

        final Kind kind = repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Kind.class, id));

        if (kind.getType() == KindType.SECTION) {
            if (kind.getContent() == null || kind.getContent().single()) {
                throw new BadRequestException("error.kind.content");
            }
            if (count("SELECT COUNT(*) FROM spex_section WHERE kind_id = :id AND (ref_id IS NOT NULL OR title IS NOT NULL)", kind) > 0
                    || count("SELECT COUNT(*) FROM spex_page_image WHERE section_id = :id", kind) > 0) {
                throw new BadRequestException("error.kind.inUse", kind.getNameSv());
            }
            final List<Kind> imageKinds = group(KindType.IMAGE, KindUse.SECTION, kind.getId(), true);

            for (final Kind imageKind : imageKinds) {
                requireUnused(imageKind);
            }
            jdbc.sql("DELETE FROM spex_section WHERE kind_id = :id").param("id", kind.getId()).update();
            repository.deleteAll(imageKinds);
        } else {
            if (!kind.isRetired() && groupOf(kind, false).stream().allMatch(other -> other.getId().equals(kind.getId()))) {
                throw new BadRequestException("error.kind.lastDeleted");
            }
            requireUnused(kind);
        }
        repository.delete(kind);
        auditService.record("DELETE", "KIND", id, StoredText.of("audit.kind.delete", name(kind), type(kind.getType())));
    }

    private void requireUnused(final Kind kind) {
        for (final Reference reference : REFERENCES.get(kind.getType())) {
            if (count("SELECT COUNT(*) FROM " + reference.table() + " WHERE kind_id = :id", kind) > 0) {
                throw new BadRequestException("error.kind.inUse", kind.getNameSv());
            }
        }
    }

    private long count(final String sql, final Kind kind) {
        return jdbc.sql(sql).param("id", kind.getId()).query(Long.class).single();
    }

    /**
     * Moves what is of one section kind onto another of the same content: the spex' sections (an image section appears
     * once on a page, so where both are, the merged one goes), and for image sections their image kinds, which follow
     * the other's, and the images of the pages.
     */
    private void mergeSection(final Kind from, final Kind into) {
        if (from.getContent() == SectionContent.IMAGES) {
            jdbc.sql("DELETE moved FROM spex_section moved JOIN spex_section kept ON kept.spex_id = moved.spex_id AND kept.kind_id = :to WHERE moved.kind_id = :from")
                    .param("to", into.getId()).param("from", from.getId()).update();
            jdbc.sql("UPDATE kind SET section_id = :to, position = position + :offset WHERE section_id = :from")
                    .param("to", into.getId()).param("from", from.getId())
                    .param("offset", repository.maxPosition(KindType.IMAGE, KindUse.SECTION.name(), into.getId())).update();
            jdbc.sql("UPDATE spex_page_image SET section_id = :to WHERE section_id = :from").param("to", into.getId()).param("from", from.getId()).update();
        }
        jdbc.sql("UPDATE spex_section SET kind_id = :to WHERE kind_id = :from").param("to", into.getId()).param("from", from.getId()).update();
    }

    private void move(final Reference reference, final long from, final long to) {
        final String table = reference.table();

        if (reference.list()) {
            // The merged kind's rows go after the other kind's of the same owner, in their own order
            final String owner = String.join(", ", reference.owner());
            final String sameOwner = reference.owner().stream().map(column -> "kept." + column + " = moved." + column)
                    .collect(Collectors.joining(" AND "));

            jdbc.sql("""
                            UPDATE %s moved
                            LEFT JOIN (SELECT %s, MAX(position) + 1 AS next FROM %s WHERE kind_id = :to GROUP BY %s) kept ON %s
                            SET moved.kind_id = :to, moved.position = moved.position + COALESCE(kept.next, 0)
                            WHERE moved.kind_id = :from""".formatted(table, owner, table, owner, sameOwner))
                    .param("to", to).param("from", from).update();
            return;
        }
        if (!reference.owner().isEmpty()) {
            // One of a kind per owner: the one already there is kept
            jdbc.sql("UPDATE IGNORE " + table + " SET kind_id = :to WHERE kind_id = :from").param("to", to).param("from", from).update();
            jdbc.sql("DELETE FROM " + table + " WHERE kind_id = :from").param("from", from).update();
            return;
        }
        jdbc.sql("UPDATE " + table + " SET kind_id = :to WHERE kind_id = :from").param("to", to).param("from", from).update();
    }

    /**
     * A table that refers to kinds in {@code kind_id}.
     *
     * @param owner the columns that with the kind (and, for a list, the position) are the row's key; none when the kind
     *              is no part of the key
     * @param list  whether an owner has several rows of a kind, in {@code position}
     */
    private record Reference(String table, List<String> owner, boolean list) {
    }

    /**
     * @param sectionId for a new image kind of use SECTION: its image section
     * @param content   for a new section kind: IMAGES or a content of another application
     */
    public record KindInput(@Nullable KindType type, @Nullable KindUse use, @Nullable UUID sectionId,
                            @Nullable SectionContent content, @Nullable FactValueType valueType,
                            String nameSv, String nameEn, @Nullable List<String> aliases,
                            @Nullable String subtitleSv, @Nullable String subtitleEn) {
    }

    /**
     * A kind's name for the audit log, in the reader's language.
     */
    private static StoredText name(final Kind kind) {
        return StoredText.of("audit.name", kind.getNameSv(), kind.getNameEn());
    }

    private static StoredText type(final KindType type) {
        return StoredText.of("audit.kindType." + type.name());
    }
}
