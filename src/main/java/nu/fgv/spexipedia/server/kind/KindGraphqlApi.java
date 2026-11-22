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

import nu.fgv.spexipedia.server.kind.KindService.KindInput;
import nu.fgv.spexipedia.server.util.Ids;
import nu.fgv.spexipedia.server.util.Locales;
import org.jspecify.annotations.Nullable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * GraphQL for kinds (spec 4.6).
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class KindGraphqlApi {

    private final KindService service;

    public KindGraphqlApi(final KindService service) {
        this.service = service;
    }

    @QueryMapping
    public List<Kind> kinds(@Argument final KindType type, @Argument final @Nullable KindUse use,
                            @Argument final @Nullable String sectionId, @Argument final boolean includeRetired) {
        return sectionId == null ? service.kinds(type, use, includeRetired)
                : type == KindType.IMAGE && use == KindUse.SECTION ? service.imageKinds(service.requireImageSection(Ids.require(sectionId)), includeRetired)
                : List.of();
    }

    @MutationMapping
    public Kind kindSave(@Argument final @Nullable String id, @Argument final Map<String, Object> input) {
        return service.save(Ids.optional(id), kindInput(input));
    }

    @MutationMapping
    public List<Kind> kindsReorder(@Argument final KindType type, @Argument final @Nullable KindUse use,
                                   @Argument final @Nullable String sectionId, @Argument final List<String> ids) {
        return service.reorder(type, use, Ids.optional(sectionId), ids.stream().map(Ids::require).toList());
    }

    @MutationMapping
    public Kind kindRetire(@Argument final String id, @Argument final boolean retired) {
        return service.retire(Ids.require(id), retired);
    }

    @MutationMapping
    public Kind kindMerge(@Argument final String id, @Argument final String intoId) {
        return service.merge(Ids.require(id), Ids.require(intoId));
    }

    @SchemaMapping(typeName = "Kind", field = "id")
    public UUID id(final Kind kind) {
        return kind.getUuid();
    }

    @SchemaMapping(typeName = "Kind", field = "name")
    public String name(final Kind kind, final @Nullable Locale locale) {
        // The request's locale (Accept-Language) from the GraphQL context: LocaleContextHolder is a thread-local that
        // is not set on the thread the field is resolved on
        return kind.name(Locales.supported(locale));
    }

    @SchemaMapping(typeName = "Kind", field = "aliases")
    public List<String> aliases(final Kind kind) {
        return kind.aliasList();
    }

    @SchemaMapping(typeName = "Kind", field = "subtitle")
    public @Nullable String subtitle(final Kind kind, final @Nullable Locale locale) {
        return kind.subtitle(Locales.supported(locale));
    }

    @SchemaMapping(typeName = "Kind", field = "section")
    public @Nullable Kind section(final Kind kind) {
        return kind.getSectionKey() == null ? null : service.byKeys(List.of(kind.getSectionKey())).get(kind.getSectionKey());
    }

    /**
     * The image kinds of an image section, retired ones too, as editors need them for images that keep theirs.
     */
    @BatchMapping(typeName = "Kind", field = "imageKinds")
    public Map<Kind, List<Kind>> imageKinds(final List<Kind> kinds) {
        final Map<Long, List<Kind>> byKey = service.imageKinds(kinds.stream()
                .filter(kind -> kind.getContent() == SectionContent.IMAGES).map(Kind::getId).toList(), true);
        final Map<Kind, List<Kind>> result = new java.util.LinkedHashMap<>();

        kinds.forEach(kind -> result.put(kind, byKey.getOrDefault(kind.getId(), List.of())));
        return result;
    }

    @SuppressWarnings("unchecked")
    static KindInput kindInput(final Map<String, Object> input) {
        return new KindInput(
                input.get("type") == null ? null : KindType.valueOf((String) input.get("type")),
                input.get("use") == null ? null : KindUse.valueOf((String) input.get("use")),
                Ids.optional((String) input.get("sectionId")),
                input.get("content") == null ? null : SectionContent.valueOf((String) input.get("content")),
                input.get("valueType") == null ? null : FactValueType.valueOf((String) input.get("valueType")),
                (String) input.get("nameSv"),
                (String) input.get("nameEn"),
                (List<String>) input.get("aliases"),
                (String) input.get("subtitleSv"),
                (String) input.get("subtitleEn"));
    }
}
