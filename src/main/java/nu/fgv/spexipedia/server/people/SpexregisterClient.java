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

package nu.fgv.spexipedia.server.people;

import lombok.extern.slf4j.Slf4j;
import nu.fgv.spexipedia.server.config.CacheConfig;
import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Spexregister's GraphQL API, as its {@code SPEXIPEDIA} role allows: the participants of spex, and spexare by id or by
 * name, only those who are published there. Answers are kept in memory for at most ten minutes (the caches in
 * {@link CacheConfig}) and are never written anywhere, nor logged. Pages for visitors ask without names, so that a fault
 * in the web cannot leak names to them (section 5.3 of the spec).
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Component
public class SpexregisterClient {

    private static final String PARTICIPANTS = """
            query SpexipediaParticipants($spexIds: [ID!]!, $names: Boolean!) {
                spexipediaParticipants(spexIds: $spexIds, names: $names) {
                    spexareId nickname name spexId taskIds characterIds
                }
            }""";
    private static final String SPEXARE = """
            query SpexipediaSpexare($ids: [ID!]!, $names: Boolean!) {
                spexipediaSpexare(ids: $ids, names: $names) { id nickname name }
            }""";
    private static final String SEARCH = """
            query SpexipediaSpexareSearch($q: String!) {
                spexipediaSpexareSearch(q: $q, first: 25) { id nickname name }
            }""";

    private final @Nullable HttpSyncGraphQlClient client;

    public SpexregisterClient(final SpexipediaProperties properties, final RestClient serviceRestClient) {
        final String url = properties.apps().spexregister().graphqlUrl();

        this.client = StringUtils.hasText(url) ? HttpSyncGraphQlClient.builder(serviceRestClient).url(url).build() : null;
    }

    /**
     * Who took part in the spex (the spex and its revivals, by id), with their tasks and characters.
     *
     * @throws PeopleUnavailableException when Spexregister cannot be reached or answers with an error
     */
    @Cacheable(cacheNames = CacheConfig.PARTICIPANTS, key = "#spexIds.toString() + ':' + #names")
    public List<Participant> participants(final List<UUID> spexIds, final boolean names) {
        return call(PARTICIPANTS, "spexipediaParticipants", Participant.class, request -> request
                .variable("spexIds", spexIds.stream().map(UUID::toString).toList())
                .variable("names", names));
    }

    /**
     * Published spexare among the ids, with names when asked.
     */
    @Cacheable(cacheNames = CacheConfig.SPEXARE, key = "#ids.toString() + ':' + #names")
    public List<Spexare> spexare(final Collection<UUID> ids, final boolean names) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return call(SPEXARE, "spexipediaSpexare", Spexare.class, request -> request
                .variable("ids", ids.stream().map(UUID::toString).sorted().toList())
                .variable("names", names));
    }

    /**
     * Published spexare matching a name, for picking credits.
     */
    public List<Spexare> search(final String q) {
        return call(SEARCH, "spexipediaSpexareSearch", Spexare.class, request -> request.variable("q", q));
    }

    private <T> List<T> call(final String document, final String field, final Class<T> type,
                             final java.util.function.UnaryOperator<HttpSyncGraphQlClient.RequestSpec> variables) {
        if (client == null) {
            throw new PeopleUnavailableException("Spexregister is not configured");
        }
        try {
            final ClientGraphQlResponse response = variables.apply(client.document(document)).executeSync();

            if (!response.isValid() || !response.getErrors().isEmpty()) {
                throw new PeopleUnavailableException("Spexregister answered with errors");
            }

            final List<T> result = response.field(field).toEntityList(type);

            return result == null ? List.of() : result.stream().filter(Objects::nonNull).toList();
        } catch (final PeopleUnavailableException e) {
            throw e;
        } catch (final RuntimeException e) {
            // The message may hold the response, which may hold names: never logged
            log.warn("Spexregister could not be reached ({})", e.getClass().getSimpleName());
            throw new PeopleUnavailableException("Spexregister could not be reached");
        }
    }

    /**
     * A spexare's part in one production.
     *
     * @param spexId       the spex or revival
     * @param taskIds      the tasks they held
     * @param characterIds the characters they played
     */
    public record Participant(UUID spexareId, @Nullable String nickname, @Nullable String name, UUID spexId,
                              @Nullable List<UUID> taskIds, @Nullable List<UUID> characterIds) {
    }

    public record Spexare(UUID id, @Nullable String nickname, @Nullable String name) {
    }

    /**
     * Spexregister could not be reached; the page says so and shows the rest.
     */
    public static class PeopleUnavailableException extends RuntimeException {
        public PeopleUnavailableException(final String message) {
            super(message);
        }
    }
}
