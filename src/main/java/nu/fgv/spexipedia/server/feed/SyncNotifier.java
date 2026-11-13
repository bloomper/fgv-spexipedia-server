package nu.fgv.spexipedia.server.feed;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tells the consumers of the change feed that there is something new ({@code POST <notifyUrl>} with
 * {@code fgv-spexipedia-service}'s token, which holds {@code NOTIFY} on their clients), so that they pull at once
 * instead of at their next five-minute pull. Calls carry no data. A failed call is made again by the maintenance job
 * SYNC_NOTIFY.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Component
public class SyncNotifier {

    private static final int MAX_ERROR = 1000;

    private final SyncConsumerRepository repository;
    private final ChangeFeedEntryRepository feed;
    private final RestClient serviceRestClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SyncNotifier(final SyncConsumerRepository repository, final ChangeFeedEntryRepository feed,
                        final RestClient serviceRestClient, final TransactionTemplate transactionTemplate,
                        final Clock clock) {
        this.repository = repository;
        this.feed = feed;
        this.serviceRestClient = serviceRestClient;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void changed(final ChangeFeedService.FeedChangedEvent event) {
        executor.execute(this::notifyConsumers);
    }

    /**
     * Tells every enabled consumer with an address that has not been told about the latest change.
     */
    @SchedulerLock(name = "sync-notify", lockAtMostFor = "PT5M")
    public void notifyConsumers() {
        final long latest = feed.latestSequence();

        for (final SyncConsumer consumer : repository.findByEnabledTrue()) {
            if (consumer.getNotifyUrl() == null || consumer.getNotifyUrl().isBlank()
                    || (consumer.getNotified() != null && consumer.getNotified() >= latest)) {
                continue;
            }
            String error = null;

            try {
                serviceRestClient.post()
                        .uri(consumer.getNotifyUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("source", "spexipedia", "sequence", String.valueOf(latest)))
                        .retrieve()
                        .toBodilessEntity();
            } catch (final RuntimeException e) {
                // Unreachable consumers, refused calls and a token Keycloak would not give alike
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                // Told once per new failure: the job tries every minute
                if (!error.equals(consumer.getLastError())) {
                    log.warn("Could not notify {}: {}", consumer.getName(), error);
                }
            }

            final String failure = error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR));

            transactionTemplate.executeWithoutResult(_ -> repository.findById(consumer.getId()).ifPresent(fresh -> {
                fresh.setLastNotifiedAt(clock.instant());
                fresh.setLastError(failure);
                if (failure == null) {
                    fresh.setNotified(latest);
                }
            }));
        }
    }
}
