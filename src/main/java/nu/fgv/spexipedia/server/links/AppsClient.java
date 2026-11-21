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

package nu.fgv.spexipedia.server.links;

import lombok.extern.slf4j.Slf4j;
import nu.fgv.spexipedia.server.config.CacheConfig;
import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.user.App;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The GraphQL APIs of Spexgram, Spexify, Spexflix and Spexarkivet, each called with {@code fgv-spexipedia-service}'s
 * token (their read-only role for Spexipedia) or, for what visitors see in Spexgram, without one. An application that
 * cannot be reached, or isn't configured, answers empty: its links are left out (section 5.9 of the spec). Answers are
 * cached for a few minutes.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Component
public class AppsClient {

    static final String ITEM_FIELDS = "id type caption width height effectiveVisibility media { thumbnailUrl previewUrl videoUrl }";
    private static final String PUBLIC_ITEMS = "query Items($ids: [ID!]!) { publicItems(ids: $ids) { " + ITEM_FIELDS + " } }";
    private static final String SPEX_ALBUMS = "query Albums($spexId: ID!) { spexAlbums(spexId: $spexId) { id title visibility } }";
    private static final String SPEX_ALBUM_ITEMS = """
            query AlbumItems($spexId: ID!) {
                spexAlbums(spexId: $spexId) { id title visibility items(page: 0, size: 200) { items { %s } } }
            }""".formatted(ITEM_FIELDS);
    private static final String SPEXIFY_LINKS = """
            query Links($spexIds: [ID!]!, $numberIds: [ID!]!) {
                spexipediaLinks(spexIds: $spexIds, numberIds: $numberIds) {
                    albums { id title spexId } tracks { id title numberId albumId }
                }
            }""";
    private static final String SPEXIFY_DISCOGRAPHY = """
            query Discography($spexIds: [ID!]!) {
                spexipediaDiscography(spexIds: $spexIds) {
                    id title year kindNameSv kindNameEn mediumNameSv mediumNameEn catalogueNumber released recorded conductor
                    spexId coverUrl backCoverUrl tracks { id side discNumber trackNumber title numberId hasAudio }
                }
            }""";
    private static final String SPEXFLIX_CHAPTERS = """
            query Chapters($trackIds: [ID!]!) { chapterLinks(trackIds: $trackIds) { trackId videoId videoTitle chapterTitle startSeconds } }""";
    private static final String SPEXFLIX_VIDEOS = """
            query Videos($spexIds: [ID!]!) { spexipediaVideos(spexIds: $spexIds) { id title spexId } }""";
    private static final String ARCHIVE_LINKS = """
            query Links($spexIds: [ID!]!, $numberIds: [ID!]!) {
                spexipediaLinks(spexIds: $spexIds, numberIds: $numberIds) {
                    manuscripts { id title spexId kindNameSv kindNameEn } arrangements { id title numberId }
                    books { id title volume publishedYear kindNameSv kindNameEn spexId songs { position numberId textOnly } }
                }
            }""";

    private final Map<App, HttpSyncGraphQlClient> service = new EnumMap<>(App.class);
    private final @Nullable HttpSyncGraphQlClient spexgramAnonymous;
    private final Map<App, String> webs = new EnumMap<>(App.class);

    public AppsClient(final SpexipediaProperties properties, final RestClient serviceRestClient, final RestClient anonymousRestClient) {
        final Map<App, SpexipediaProperties.App> apps = Map.of(
                App.SPEXGRAM, properties.apps().spexgram(),
                App.SPEXIFY, properties.apps().spexify(),
                App.SPEXFLIX, properties.apps().spexflix(),
                App.ARCHIVE, properties.apps().archive());

        apps.forEach((app, config) -> {
            if (StringUtils.hasText(config.graphqlUrl())) {
                service.put(app, HttpSyncGraphQlClient.builder(serviceRestClient).url(config.graphqlUrl()).build());
            }
            if (StringUtils.hasText(config.webUrl())) {
                webs.put(app, config.webUrl().replaceAll("/+$", ""));
            }
        });
        this.spexgramAnonymous = StringUtils.hasText(properties.apps().spexgram().graphqlUrl())
                ? HttpSyncGraphQlClient.builder(anonymousRestClient).url(properties.apps().spexgram().graphqlUrl()).build() : null;
    }

    /**
     * The application's web address, for links; empty when it isn't configured.
     */
    public Optional<String> web(final App app) {
        return Optional.ofNullable(webs.get(app));
    }

    /**
     * Spexgram's items among the ids: as visitors see them (members false), or with members' items too.
     */
    @Cacheable(cacheNames = CacheConfig.PHOTOS, key = "'items:' + #ids.toString() + ':' + #members")
    public Optional<List<SpexgramItem>> spexgramItems(final List<UUID> ids, final boolean members) {
        if (ids.isEmpty()) {
            return Optional.of(List.of());
        }
        return call(members ? service.get(App.SPEXGRAM) : spexgramAnonymous, App.SPEXGRAM, PUBLIC_ITEMS, "publicItems",
                SpexgramItem.class, Map.of("ids", ids.stream().map(UUID::toString).toList()));
    }

    /**
     * Spexgram's albums of a spex (and its revivals): public ones (members false), or members' too.
     */
    @Cacheable(cacheNames = CacheConfig.LINKS, key = "'albums:' + #spexId + ':' + #members")
    public Optional<List<SpexgramAlbum>> spexgramAlbums(final UUID spexId, final boolean members) {
        return call(members ? service.get(App.SPEXGRAM) : spexgramAnonymous, App.SPEXGRAM, SPEX_ALBUMS, "spexAlbums",
                SpexgramAlbum.class, Map.of("spexId", spexId.toString()));
    }

    /**
     * Spexgram's albums of a spex with their items, members' included, for authors picking photos. Not cached.
     */
    public Optional<List<SpexgramAlbum>> spexgramAlbumItems(final UUID spexId) {
        return call(service.get(App.SPEXGRAM), App.SPEXGRAM, SPEX_ALBUM_ITEMS, "spexAlbums", SpexgramAlbum.class,
                Map.of("spexId", spexId.toString()));
    }

    @Cacheable(cacheNames = CacheConfig.LINKS, key = "'spexify:' + #spexIds.toString() + ':' + #numberIds.toString()")
    public Optional<SpexifyLinks> spexifyLinks(final List<UUID> spexIds, final List<UUID> numberIds) {
        return callOne(service.get(App.SPEXIFY), App.SPEXIFY, SPEXIFY_LINKS, "spexipediaLinks", SpexifyLinks.class,
                Map.of("spexIds", strings(spexIds), "numberIds", strings(numberIds)));
    }

    /**
     * The discography of the spex (Spexify's albums, spec 4.4), as Spexify answers Spexipedia's service account.
     */
    @Cacheable(cacheNames = CacheConfig.LINKS, key = "'discography:' + #spexIds.toString()")
    public Optional<List<DiscographyAlbum>> spexifyDiscography(final List<UUID> spexIds) {
        return call(service.get(App.SPEXIFY), App.SPEXIFY, SPEXIFY_DISCOGRAPHY, "spexipediaDiscography", DiscographyAlbum.class,
                Map.of("spexIds", strings(spexIds)));
    }

    /**
     * The chapters of Spexflix's videos where the tracks are seen.
     */
    @Cacheable(cacheNames = CacheConfig.LINKS, key = "'chapters:' + #trackIds.toString()")
    public Optional<List<SpexflixChapter>> spexflixChapters(final List<UUID> trackIds) {
        if (trackIds.isEmpty()) {
            return Optional.of(List.of());
        }
        return call(service.get(App.SPEXFLIX), App.SPEXFLIX, SPEXFLIX_CHAPTERS, "chapterLinks", SpexflixChapter.class,
                Map.of("trackIds", strings(trackIds)));
    }

    @Cacheable(cacheNames = CacheConfig.LINKS, key = "'spexflix:' + #spexIds.toString()")
    public Optional<List<Titled>> spexflixVideos(final List<UUID> spexIds) {
        return call(service.get(App.SPEXFLIX), App.SPEXFLIX, SPEXFLIX_VIDEOS, "spexipediaVideos", Titled.class,
                Map.of("spexIds", strings(spexIds)));
    }

    @Cacheable(cacheNames = CacheConfig.LINKS, key = "'archive:' + #spexIds.toString() + ':' + #numberIds.toString()")
    public Optional<ArchiveLinks> archiveLinks(final List<UUID> spexIds, final List<UUID> numberIds) {
        return callOne(service.get(App.ARCHIVE), App.ARCHIVE, ARCHIVE_LINKS, "spexipediaLinks", ArchiveLinks.class,
                Map.of("spexIds", strings(spexIds), "numberIds", strings(numberIds)));
    }

    @CacheEvict(cacheNames = {CacheConfig.LINKS, CacheConfig.PHOTOS}, allEntries = true)
    public void clearCaches() {
        log.info("Cleared the caches of links and photos");
    }

    private static List<String> strings(final List<UUID> ids) {
        return ids.stream().map(UUID::toString).toList();
    }

    private <T> Optional<List<T>> call(final @Nullable HttpSyncGraphQlClient client, final App app, final String document,
                                       final String field, final Class<T> type, final Map<String, Object> variables) {
        return response(client, app, document, variables).map(response -> {
            final List<T> list = response.field(field).toEntityList(type);

            return list == null ? List.<T>of() : list.stream().filter(Objects::nonNull).toList();
        });
    }

    private <T> Optional<T> callOne(final @Nullable HttpSyncGraphQlClient client, final App app, final String document,
                                    final String field, final Class<T> type, final Map<String, Object> variables) {
        return response(client, app, document, variables).map(response -> response.field(field).toEntity(type));
    }

    private Optional<ClientGraphQlResponse> response(final @Nullable HttpSyncGraphQlClient client, final App app,
                                                     final String document, final Map<String, Object> variables) {
        if (client == null) {
            return Optional.empty();
        }
        try {
            final ClientGraphQlResponse response = client.document(document).variables(variables).executeSync();

            if (!response.isValid() || !response.getErrors().isEmpty()) {
                log.warn("{} answered with errors: {}", app, response.getErrors());
                return Optional.empty();
            }
            return Optional.of(response);
        } catch (final RuntimeException e) {
            log.warn("{} could not be reached: {}", app, e.getMessage());
            return Optional.empty();
        }
    }

    public record Media(String thumbnailUrl, String previewUrl, @Nullable String videoUrl) {
    }

    public record SpexgramItem(UUID id, @Nullable String type, @Nullable String caption, @Nullable Integer width, @Nullable Integer height,
                               @Nullable String effectiveVisibility, @Nullable Media media) {
        public boolean isPublic() {
            return "PUBLIC".equals(effectiveVisibility);
        }
    }

    public record ItemPage(@Nullable List<SpexgramItem> items) {
    }

    public record SpexgramAlbum(UUID id, String title, @Nullable String visibility, @Nullable ItemPage items) {
    }

    public record Titled(UUID id, String title, @Nullable UUID spexId) {
    }

    public record SpexifyTrack(UUID id, String title, @Nullable UUID numberId, @Nullable UUID albumId) {
    }

    public record SpexifyLinks(@Nullable List<Titled> albums, @Nullable List<SpexifyTrack> tracks) {
    }

    public record ArchiveArrangement(UUID id, String title, @Nullable UUID numberId) {
    }

    public record ArchiveBookSong(int position, UUID numberId, boolean textOnly) {
    }

    public record ArchiveBook(UUID id, String title, @Nullable Integer volume, @Nullable String publishedYear,
                              String kindNameSv, String kindNameEn, @Nullable UUID spexId, @Nullable List<ArchiveBookSong> songs) {
    }

    public record SpexflixChapter(UUID trackId, UUID videoId, String videoTitle, String chapterTitle, int startSeconds) {
    }

    public record ArchiveDocument(UUID id, String title, @Nullable UUID spexId, @Nullable String kindNameSv, @Nullable String kindNameEn) {
    }

    public record ArchiveLinks(@Nullable List<ArchiveDocument> manuscripts, @Nullable List<ArchiveArrangement> arrangements,
                               @Nullable List<ArchiveBook> books) {
    }

    public record DiscographyTrack(UUID id, @Nullable String side, int discNumber, int trackNumber, String title,
                                   @Nullable UUID numberId, boolean hasAudio) {
    }

    public record DiscographyAlbum(UUID id, String title, @Nullable Integer year, String kindNameSv, String kindNameEn,
                                   @Nullable String mediumNameSv, @Nullable String mediumNameEn, @Nullable String catalogueNumber,
                                   @Nullable String released, @Nullable String recorded, @Nullable String conductor,
                                   @Nullable UUID spexId, @Nullable String coverUrl, @Nullable String backCoverUrl,
                                   @Nullable List<DiscographyTrack> tracks) {
    }
}
