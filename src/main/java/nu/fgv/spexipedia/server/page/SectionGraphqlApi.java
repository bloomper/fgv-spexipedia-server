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

import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.content.ContentGraphqlApi;
import nu.fgv.spexipedia.server.content.ContentGraphqlApi.PageImageDto;
import nu.fgv.spexipedia.server.content.ListStore.PlacedImage;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.kind.KindService;
import nu.fgv.spexipedia.server.kind.SectionContent;
import nu.fgv.spexipedia.server.links.LinkDtos;
import nu.fgv.spexipedia.server.links.LinkService;
import nu.fgv.spexipedia.server.page.SpexSectionService.PageSectionDto;
import nu.fgv.spexipedia.server.page.SpexSectionService.SectionInput;
import nu.fgv.spexipedia.server.util.Ids;
import nu.fgv.spexipedia.server.util.Locales;
import org.jspecify.annotations.Nullable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * GraphQL for the sections of spex pages (spec 4.2 and 4.2.1): their order, and what each shows. The material of the
 * other applications is asked for when a section's field is, so a page asks only for what it shows.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class SectionGraphqlApi {

    private final SpexSectionService service;
    private final SpexPageService pageService;
    private final BaseDataService baseData;
    private final ImageService imageService;
    private final KindService kindService;
    private final ContentGraphqlApi contentApi;
    private final LinkService linkService;

    public SectionGraphqlApi(final SpexSectionService service, final SpexPageService pageService, final BaseDataService baseData,
                             final ImageService imageService, final KindService kindService, final ContentGraphqlApi contentApi,
                             final LinkService linkService) {
        this.service = service;
        this.pageService = pageService;
        this.baseData = baseData;
        this.imageService = imageService;
        this.kindService = kindService;
        this.contentApi = contentApi;
        this.linkService = linkService;
    }

    @SchemaMapping(typeName = "Spex")
    public List<PageSectionDto> sections(final SpexDto spex, @Argument final boolean includeHidden) {
        return service.sections(spex, includeHidden && pageService.isAuthor());
    }

    @SchemaMapping(typeName = "Spex")
    public SpexSectionService.SectionOrder sectionOrder(final SpexDto spex) {
        return service.order(spex);
    }

    @MutationMapping
    @SuppressWarnings("unchecked")
    public SpexDto spexSetSections(@Argument final String spexId, @Argument final List<Map<String, Object>> sections) {
        final java.util.UUID id = Ids.require(spexId);

        service.set(id, sections.stream().map(section -> new SectionInput(
                Ids.optional((String) section.get("id")),
                Ids.require((String) section.get("kindId")),
                Boolean.TRUE.equals(section.get("hidden")),
                (String) section.get("title"),
                Ids.optional((String) section.get("refId")),
                section.get("itemIds") == null ? null : ((List<String>) section.get("itemIds")).stream().map(Ids::require).toList(),
                (Integer) section.get("startSeconds"))).toList());
        return baseData.spexByKey(baseData.requireSpex(id).getId());
    }

    // PageSection ------------------------------------------------------------------------------------------------------

    @SchemaMapping(typeName = "PageSection")
    public String title(final PageSectionDto section, final @Nullable Locale locale) {
        return section.ownTitle() != null ? section.ownTitle() : section.kind().name(Locales.supported(locale));
    }

    @SchemaMapping(typeName = "PageSection")
    public @Nullable String subtitle(final PageSectionDto section, final @Nullable Locale locale) {
        return section.kind().subtitle(Locales.supported(locale));
    }

    /**
     * The images of an image section or the vest: the spex' own, else (a revival) its original's.
     */
    @SchemaMapping(typeName = "PageSection")
    public List<PageImageDto> images(final PageSectionDto section) {
        final SectionContent content = section.content();

        if (content != SectionContent.IMAGES && content != SectionContent.VEST) {
            return List.of();
        }

        final long sectionKey = section.kind().getId();
        List<PlacedImage> placed = pageService.images(section.spexKey(), sectionKey);

        if (placed.isEmpty()) {
            final SpexDto spex = baseData.spexByKey(section.spexKey());

            if (spex.parentKey() != null) {
                placed = pageService.images(spex.parentKey(), sectionKey);
            }
        }
        return contentApi.pageImages(placed, imageService.byIds(placed.stream().map(PlacedImage::imageId).collect(Collectors.toSet())),
                kindService.byKeys(placed.stream().map(PlacedImage::kindId).filter(Objects::nonNull).collect(Collectors.toSet())));
    }

    @SchemaMapping(typeName = "PageSection")
    public LinkDtos.@Nullable EmbeddedVideoDto video(final PageSectionDto section) {
        return section.content() == SectionContent.SPEXFLIX_VIDEO && section.refId() != null
                ? linkService.video(section.refId(), section.startSeconds()).orElse(null) : null;
    }

    @SchemaMapping(typeName = "PageSection")
    public LinkDtos.@Nullable EmbeddedPhotosDto photos(final PageSectionDto section) {
        return section.content() == SectionContent.SPEXGRAM && section.refId() != null
                ? linkService.album(section.refId(), section.itemIds()).orElse(null) : null;
    }

    @SchemaMapping(typeName = "PageSection")
    public LinkDtos.@Nullable RecordDto record(final PageSectionDto section) {
        return section.content() == SectionContent.SPEXIFY_ALBUM && section.refId() != null
                ? linkService.record(section.refId()).orElse(null) : null;
    }

    @SchemaMapping(typeName = "PageSection")
    public LinkDtos.@Nullable EmbeddedDocumentDto document(final PageSectionDto section) {
        return section.content() == SectionContent.ARCHIVE_DOCUMENT && section.refId() != null
                ? linkService.document(section.refId()).orElse(null) : null;
    }
}
