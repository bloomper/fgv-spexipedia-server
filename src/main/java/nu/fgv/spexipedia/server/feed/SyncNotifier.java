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

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

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
    private final SyncConsumerService consumers;
    private final ChangeFeedEntryRepository feed;
    private final RestClient serviceRestClient;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ReentrantLock running = new ReentrantLock();
    private final AtomicBoolean again = new AtomicBoolean();

    public SyncNotifier(final SyncConsumerRepository repository, final SyncConsumerService consumers,
                        final ChangeFeedEntryRepository feed, final RestClient serviceRestClient, final JdbcClient jdbc,
                        final Clock clock) {
        this.repository = repository;
        this.consumers = consumers;
        this.feed = feed;
        this.serviceRestClient = serviceRestClient;
        this.jdbc = jdbc;
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
        // One run at a time; a change during a run makes it go round once more, so nothing is missed
        again.set(true);
        while (again.get() && running.tryLock()) {
            try {
                again.set(false);
                notifyOnce();
            } finally {
                running.unlock();
            }
        }
    }

    private void notifyOnce() {
        final long latest = feed.latestSequence();

        for (final SyncConsumer consumer : repository.findByEnabledTrue()) {
            if (consumer.getNotifyUrl() == null || consumer.getNotifyUrl().isBlank()
                    || (consumer.getNotified() != null && consumer.getNotified() >= latest)) {
                continue;
            }
            String error = null;

            try {
                // The token goes to the other applications' APIs only (see SyncConsumerService.mayNotify)
                if (!consumers.mayNotify(consumer.getNotifyUrl())) {
                    throw new IllegalStateException("Not one of the applications' addresses: " + consumer.getNotifyUrl());
                }
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

            jdbc.sql("""
                            UPDATE sync_consumer SET last_notified_at = :at, last_error = :error,
                                notified = CASE WHEN :error IS NULL THEN GREATEST(COALESCE(notified, 0), :latest) ELSE notified END
                            WHERE id = :id""")
                    .param("at", java.sql.Timestamp.from(clock.instant()))
                    .param("error", failure)
                    .param("latest", latest)
                    .param("id", consumer.getId())
                    .update();
        }
    }
}
