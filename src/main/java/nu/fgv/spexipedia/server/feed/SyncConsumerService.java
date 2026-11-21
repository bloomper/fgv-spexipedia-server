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

package nu.fgv.spexipedia.server.feed;

import nu.fgv.spexipedia.server.audit.AuditService;
import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.Texts;
import nu.fgv.spexipedia.server.util.Uuids;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.error.ResourceNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The consumers of the change feed, kept by admins (section 5.12 of the spec).
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class SyncConsumerService {

    private final SyncConsumerRepository repository;
    private final ChangeFeedService feed;
    private final CallerService callerService;
    private final AuditService auditService;
    private final Set<String> notifyOrigins;

    public SyncConsumerService(final SyncConsumerRepository repository, final ChangeFeedService feed,
                               final CallerService callerService, final AuditService auditService,
                               final SpexipediaProperties properties) {
        this.repository = repository;
        this.feed = feed;
        this.callerService = callerService;
        this.auditService = auditService;
        this.notifyOrigins = notifyOrigins(properties.apps());
    }

    /**
     * The origins (scheme, host and port) of the other applications' APIs. Notifications carry Spexipedia's service
     * token, so they go to these only, never to an address an admin typed for somewhere else.
     */
    static Set<String> notifyOrigins(final SpexipediaProperties.Apps apps) {
        return Stream.of(apps.spexregister(), apps.spexgram(), apps.spexify(), apps.spexflix(), apps.archive())
                .map(SpexipediaProperties.App::graphqlUrl)
                .filter(Objects::nonNull)
                .filter(url -> !url.isBlank())
                .map(SyncConsumerService::origin)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static @Nullable String origin(final String url) {
        try {
            final URI uri = URI.create(url.strip());
            // Schemes and hosts are written in any case
            final String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(java.util.Locale.ROOT);

            if (uri.getHost() == null || (!"http".equals(scheme) && !"https".equals(scheme))) {
                return null;
            }
            final int port = uri.getPort() >= 0 ? uri.getPort() : "https".equals(scheme) ? 443 : 80;

            return scheme + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + ":" + port;
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Whether a notification may be sent to the address: one of the other applications' APIs, without credentials in
     * the address.
     */
    public boolean mayNotify(final @Nullable String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            final URI uri = URI.create(url.strip());
            final String origin = origin(url);

            // An address that is no http(s) address has no origin, and the set of origins takes no null
            return uri.getRawUserInfo() == null && origin != null && notifyOrigins.contains(origin);
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<SyncConsumerDto> list() {
        callerService.current().requireAdmin();
        return repository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public SyncConsumerDto save(final @Nullable UUID id, final String name, final String clientId,
                                final @Nullable String notifyUrl, final boolean enabled) {
        callerService.current().requireAdmin();

        final SyncConsumer consumer = id == null ? new SyncConsumer()
                : repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(SyncConsumer.class, id));
        final String url = Texts.optional(notifyUrl, 1000, "notifyUrl");

        if (url != null && !mayNotify(url)) {
            throw new BadRequestException("error.field.invalid", "notifyUrl");
        }
        if (consumer.getUuid() == null) {
            consumer.setUuid(Uuids.v7());
        }
        consumer.setName(Texts.required(name, 255, "name"));
        consumer.setClientId(Texts.required(clientId, 255, "clientId"));
        consumer.setNotifyUrl(url);
        consumer.setEnabled(enabled);
        repository.save(consumer);
        auditService.record(id == null ? "CREATE" : "UPDATE", "SYNC_CONSUMER", consumer.getUuid(),
                "Sparade mottagaren " + consumer.getName() + " av ändringsflödet");
        return toDto(consumer);
    }

    /**
     * Without checking the caller, for the sample data.
     */
    @Transactional
    public void register(final String name, final String clientId, final @Nullable String notifyUrl) {
        final SyncConsumer consumer = repository.findByClientId(clientId).orElseGet(SyncConsumer::new);

        if (consumer.getUuid() == null) {
            consumer.setUuid(Uuids.v7());
        }
        consumer.setName(name);
        consumer.setClientId(clientId);
        consumer.setNotifyUrl(notifyUrl);
        consumer.setEnabled(true);
        repository.save(consumer);
    }

    @Transactional
    public boolean delete(final UUID id) {
        callerService.current().requireAdmin();

        final SyncConsumer consumer = repository.findByUuid(id).orElseThrow(() -> new ResourceNotFoundException(SyncConsumer.class, id));

        repository.delete(consumer);
        auditService.record("DELETE", "SYNC_CONSUMER", id, "Tog bort mottagaren " + consumer.getName() + " av ändringsflödet");
        return true;
    }

    private SyncConsumerDto toDto(final SyncConsumer consumer) {
        return new SyncConsumerDto(consumer.getUuid(), consumer.getName(), consumer.getClientId(), consumer.getNotifyUrl(),
                consumer.isEnabled(), consumer.getAcknowledged() == null ? null : String.valueOf(consumer.getAcknowledged()),
                consumer.getAcknowledgedAt(), (int) Math.min(Integer.MAX_VALUE, feed.lag(consumer.getAcknowledged())),
                consumer.getLastNotifiedAt(), consumer.getLastError());
    }

    public record SyncConsumerDto(UUID id, String name, String clientId, @Nullable String notifyUrl, boolean enabled,
                                  @Nullable String acknowledged, @Nullable Instant acknowledgedAt, int lag,
                                  @Nullable Instant lastNotifiedAt, @Nullable String lastError) {
    }
}
