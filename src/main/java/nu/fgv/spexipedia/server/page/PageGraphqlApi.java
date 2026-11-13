package nu.fgv.spexipedia.server.page;

import nu.fgv.spexipedia.server.basedata.BaseDataDtos.SpexDto;
import nu.fgv.spexipedia.server.basedata.BaseDataService;
import nu.fgv.spexipedia.server.content.ContentGraphqlApi;
import nu.fgv.spexipedia.server.content.ContentGraphqlApi.CreditDto;
import nu.fgv.spexipedia.server.content.ContentGraphqlApi.PageImageDto;
import nu.fgv.spexipedia.server.content.ListStore.PlacedImage;
import nu.fgv.spexipedia.server.image.ImageDto;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.links.LinkDtos.PhotoSetDto;
import nu.fgv.spexipedia.server.links.LinkDtos.SpexgramAlbumDto;
import nu.fgv.spexipedia.server.links.LinkService;
import nu.fgv.spexipedia.server.revision.RevisionDto;
import nu.fgv.spexipedia.server.util.Ids;
import org.jspecify.annotations.Nullable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GraphQL for spex pages. A revival's page shows what it changed and falls back to its parent's page for the rest: its
 * texts, rebus, vest and images where it has none of its own.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class PageGraphqlApi {

    private final SpexPageService service;
    private final BaseDataService baseData;
    private final ImageService imageService;
    private final LinkService linkService;
    private final ContentGraphqlApi contentApi;

    public PageGraphqlApi(final SpexPageService service, final BaseDataService baseData, final ImageService imageService,
                          final LinkService linkService, final ContentGraphqlApi contentApi) {
        this.service = service;
        this.baseData = baseData;
        this.imageService = imageService;
        this.linkService = linkService;
        this.contentApi = contentApi;
    }

    private static <T> @Nullable T either(final @Nullable T own, final @Nullable T parent) {
        return own != null ? own : parent;
    }

    @SchemaMapping(typeName = "Spex")
    public @Nullable SpexPageDto page(final SpexDto spex) {
        final SpexPageDto own = service.page(spex.key()).orElse(null);

        if (own == null || spex.parentKey() == null) {
            return own;
        }

        final SpexPageDto parent = service.parentPage(spex.parentKey()).orElse(null);

        if (parent == null) {
            return own;
        }
        return new SpexPageDto(own.spexKey(), own.spexId(), either(own.generation(), parent.generation()), own.premiere(),
                either(own.introduction(), parent.introduction()), either(own.description(), parent.description()),
                either(own.synopsis(), parent.synopsis()), either(own.rebusKey(), parent.rebusKey()),
                either(own.rebusSolutionKey(), parent.rebusSolutionKey()), either(own.vestDescription(), parent.vestDescription()),
                either(own.vestWornBy(), parent.vestWornBy()), own.published(), own.lastModifiedAt());
    }

    @QueryMapping
    public List<SpexgramAlbumDto> spexgramAlbums(@Argument final String spexId) {
        return linkService.spexgramAlbums(baseData.requireSpex(spexId).getUuid());
    }

    // Mutations --------------------------------------------------------------------------------------------------------

    @MutationMapping
    @SuppressWarnings("unchecked")
    public SpexPageDto spexPageUpdate(@Argument final String spexId, @Argument final Map<String, Object> input) {
        final Map<String, String> base = (Map<String, String>) input.get("baseRevisionIds");
        Map<String, UUID> baseIds = null;

        if (base != null) {
            baseIds = new HashMap<>();
            for (final String field : List.of("introduction", "description", "synopsis")) {
                baseIds.put(field.toUpperCase(java.util.Locale.ROOT), Ids.optional(base.get(field)));
            }
        }
        return service.update(Ids.require(spexId), new SpexPageService.PageInput((String) input.get("generation"),
                (String) input.get("premiere"), (String) input.get("introduction"), (String) input.get("description"),
                (String) input.get("synopsis"), (String) input.get("vestDescription"),
                input.get("vestWornBy") == null ? null : ContentGraphqlApi.credit((Map<String, String>) input.get("vestWornBy")),
                baseIds));
    }

    @MutationMapping
    public SpexPageDto spexPageSetImages(@Argument final String spexId, @Argument final String section,
                                         @Argument final List<Map<String, String>> images) {
        return service.setImages(Ids.require(spexId), section, ContentGraphqlApi.imageRefs(images));
    }

    @MutationMapping
    public SpexPageDto spexPageSetPhotos(@Argument final String spexId, @Argument final String section,
                                         @Argument final @Nullable String characterId, @Argument final List<String> itemIds) {
        final Long characterKey = characterId == null ? null
                : contentApi.character(characterId) == null ? null : contentApi.character(characterId).key();

        if (characterId != null && characterKey == null) {
            throw new nu.fgv.spexipedia.server.util.error.ResourceNotFoundException("error.notFound.SpexCharacter", characterId);
        }
        return service.setPhotos(Ids.require(spexId), section, characterKey, itemIds.stream().map(Ids::require).toList());
    }

    @MutationMapping
    public SpexPageDto spexPageSetRebus(@Argument final String spexId, @Argument final @Nullable String rebusImageId,
                                        @Argument final @Nullable String solutionImageId) {
        return service.setRebus(Ids.require(spexId), Ids.optional(rebusImageId), Ids.optional(solutionImageId));
    }

    @MutationMapping
    public SpexPageDto spexPageSetPublished(@Argument final String spexId, @Argument final boolean published) {
        return service.setPublished(Ids.require(spexId), published);
    }

    // SpexPage ---------------------------------------------------------------------------------------------------------

    @SchemaMapping(typeName = "SpexPage", field = "spex")
    public SpexDto pageSpex(final SpexPageDto page) {
        return baseData.spexByKey(page.spexKey());
    }

    @SchemaMapping(typeName = "SpexPage")
    public Map<String, @Nullable UUID> revisionIds(final SpexPageDto page) {
        return service.revisionIds(page.spexId());
    }

    @SchemaMapping(typeName = "SpexPage")
    public @Nullable ImageDto rebus(final SpexPageDto page) {
        return image(page.rebusKey());
    }

    @SchemaMapping(typeName = "SpexPage")
    public @Nullable ImageDto rebusSolution(final SpexPageDto page) {
        return image(page.rebusSolutionKey());
    }

    private @Nullable ImageDto image(final @Nullable Long key) {
        return key == null ? null : imageService.visible(imageService.byIds(List.of(key)).get(key));
    }

    @SchemaMapping(typeName = "SpexPage")
    public @Nullable CreditDto vestWornBy(final SpexPageDto page) {
        return page.vestWornBy() == null ? null
                : ContentGraphqlApi.toCredit(page.vestWornBy(), contentApi.persons(List.of(page.vestWornBy()))).orElse(null);
    }

    @SchemaMapping(typeName = "SpexPage")
    public List<PageImageDto> images(final SpexPageDto page, @Argument final String section) {
        List<PlacedImage> placed = service.images(page.spexKey(), section);

        if (placed.isEmpty()) {
            final SpexDto spex = baseData.spexByKey(page.spexKey());

            if (spex.parentKey() != null) {
                placed = service.images(spex.parentKey(), section);
            }
        }
        return contentApi.pageImages(placed, imageService.byIds(placed.stream().map(PlacedImage::imageId).collect(Collectors.toSet())));
    }

    @SchemaMapping(typeName = "SpexPage")
    public PhotoSetDto photos(final SpexPageDto page, @Argument final String section) {
        return linkService.photos(service.photos(page.spexKey(), section, 0));
    }

    @SchemaMapping(typeName = "SpexPage")
    public List<RevisionDto> history(final SpexPageDto page) {
        return service.history(page.spexId());
    }
}
