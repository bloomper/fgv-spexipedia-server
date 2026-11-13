package nu.fgv.spexipedia.server.feed;

import nu.fgv.spexipedia.server.audit.AuditService;
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
import java.util.UUID;

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

    public SyncConsumerService(final SyncConsumerRepository repository, final ChangeFeedService feed,
                               final CallerService callerService, final AuditService auditService) {
        this.repository = repository;
        this.feed = feed;
        this.callerService = callerService;
        this.auditService = auditService;
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

        if (url != null) {
            try {
                final URI uri = URI.create(url);

                if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
                    throw new BadRequestException("error.field.invalid", "notifyUrl");
                }
            } catch (final IllegalArgumentException e) {
                throw new BadRequestException("error.field.invalid", "notifyUrl");
            }
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
