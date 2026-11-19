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
 * another of the same type and use, never deleted. The import finds kinds by name or alias ({@link #match}).
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class KindService {

    /**
     * Where each type of kind is referred to: table and column. A merge moves these references.
     */
    private static final Map<KindType, List<String[]>> REFERENCES = Map.of(
            KindType.NUMBER, List.<String[]>of(new String[]{"spex_number", "kind_id"}),
            KindType.CREDIT, List.<String[]>of(new String[]{"number_credit", "kind_id"}),
            KindType.MEDIUM, List.<String[]>of(new String[]{"recording", "medium_id"}),
            KindType.IMAGE, List.of(new String[]{"character_image", "kind_id"}, new String[]{"spex_page_image", "kind_id"}),
            KindType.FACT, List.<String[]>of(new String[]{"spex_fact", "kind_id"}),
            KindType.BOOK, List.<String[]>of(new String[]{"book", "kind_id"}));

    private final KindRepository repository;
    private final JdbcClient jdbc;
    private final CallerService callerService;
    private final AuditService auditService;

    public KindService(final KindRepository repository, final JdbcClient jdbc, final CallerService callerService,
                       final AuditService auditService) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.callerService = callerService;
        this.auditService = auditService;
    }

    // Reading --------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Kind> kinds(final KindType type, final @Nullable KindUse use, final boolean includeRetired) {
        requireUse(type, use);
        return repository.findByTypeAndUseForOrderByPositionAsc(type, use == null ? "" : use.name()).stream()
                .filter(kind -> includeRetired || !kind.isRetired())
                .toList();
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

        if (kind.getType() != type || kind.getUse() != use) {
            throw new BadRequestException("error.kind.wrongType", kind.getNameSv());
        }
        if (kind.isRetired() && (current == null || !current.getId().equals(kind.getId()))) {
            throw new BadRequestException("error.kind.retired", kind.getNameSv());
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
     * The kind whose Swedish or English name or one of whose aliases is the text, ignoring case, accents, punctuation
     * and blanks; retired kinds too, since old material uses old names. Empty when nothing matches.
     */
    @Transactional(readOnly = true)
    public Optional<Kind> match(final KindType type, final @Nullable KindUse use, final @Nullable String text) {
        final String wanted = normalize(text);

        if (wanted.isEmpty()) {
            return Optional.empty();
        }

        final List<Kind> kinds = kinds(type, use, true);

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
        repository.save(kind);
        auditService.record(id == null ? "CREATE" : "UPDATE", "KIND", kind.getUuid(),
                (id == null ? "Skapade " : "Ändrade ") + "sorten " + kind.getNameSv() + " (" + kind.getType() + ")");
        return kind;
    }

    /**
     * IMPORT. A new kind with this name in both languages, for an import that may create the kinds it meets
     * ({@code createMissingKinds}).
     */
    @Transactional
    public Kind createForImport(final KindType type, final @Nullable KindUse use, final String name) {
        callerService.current().requireImport();

        final Kind kind = newKind(new KindInput(type, use, null, name, name, null));

        kind.setNameSv(Texts.required(name, 100, "name"));
        kind.setNameEn(kind.getNameSv());
        if (type == KindType.FACT) {
            kind.setValueType(FactValueType.TEXT);
        }
        repository.save(kind);
        auditService.record("CREATE", "KIND", kind.getUuid(), "Skapade sorten " + kind.getNameSv() + " (" + type + ") vid import");
        return kind;
    }

    private Kind newKind(final KindInput input) {
        if (input.type() == null) {
            throw new BadRequestException("error.field.invalid", "type");
        }
        requireUse(input.type(), input.use());

        final Kind kind = new Kind();

        kind.setUuid(Uuids.v7());
        kind.setType(input.type());
        kind.setUse(input.use());
        kind.setPosition(repository.maxPosition(input.type(), input.use() == null ? "" : input.use().name()) + 1);
        return kind;
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

    private static void requireUse(final KindType type, final @Nullable KindUse use) {
        if ((type == KindType.IMAGE) != (use != null)) {
            throw new BadRequestException("error.field.invalid", "use");
        }
    }

    /**
     * The kinds of a type and use in the given order; every kind of them must be listed.
     */
    @Transactional
    public List<Kind> reorder(final KindType type, final @Nullable KindUse use, final List<UUID> ids) {
        callerService.current().requireAdmin();

        final List<Kind> kinds = kinds(type, use, true);
        final Map<UUID, Kind> byId = kinds.stream().collect(Collectors.toMap(Kind::getUuid, Function.identity()));

        if (ids.size() != kinds.size() || !byId.keySet().equals(Set.copyOf(ids))) {
            throw new BadRequestException("error.order.incomplete");
        }
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).setPosition(i + 1);
        }
        auditService.record("UPDATE", "KIND", ids.getFirst(), "Ändrade ordningen på sorterna (" + type + ")");
        return kinds(type, use, true);
    }

    @Transactional
    public Kind retire(final UUID id, final boolean retired) {
        callerService.current().requireAdmin();

        final Kind kind = repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(Kind.class, id));

        if (retired && kinds(kind.getType(), kind.getUse(), false).stream().allMatch(other -> other.getId().equals(kind.getId()))) {
            throw new BadRequestException("error.kind.last");
        }
        kind.setRetired(retired);
        auditService.record("UPDATE", "KIND", id, (retired ? "Deaktiverade " : "Aktiverade ") + "sorten " + kind.getNameSv());
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

        if (from.getId().equals(into.getId()) || from.getType() != into.getType() || from.getUse() != into.getUse()) {
            throw new BadRequestException("error.kind.wrongType", into.getNameSv());
        }
        for (final String[] reference : REFERENCES.get(from.getType())) {
            // A record may hold both kinds (a fact of each, credits of each): keep the one already there
            jdbc.sql("UPDATE IGNORE " + reference[0] + " SET " + reference[1] + " = :to WHERE " + reference[1] + " = :from")
                    .param("to", into.getId()).param("from", from.getId()).update();
            jdbc.sql("DELETE FROM " + reference[0] + " WHERE " + reference[1] + " = :from").param("from", from.getId()).update();
        }

        final List<String> aliases = new java.util.ArrayList<>(into.aliasList());

        aliases.add(from.getNameSv());
        aliases.add(from.getNameEn());
        aliases.addAll(from.aliasList());
        into.setAliases(aliases(aliases));
        repository.delete(from);
        auditService.record("MERGE", "KIND", intoId, "Slog ihop sorten " + from.getNameSv() + " med " + into.getNameSv());
        return into;
    }

    public record KindInput(@Nullable KindType type, @Nullable KindUse use, @Nullable FactValueType valueType,
                            String nameSv, String nameEn, @Nullable List<String> aliases) {
    }
}
