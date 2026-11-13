package nu.fgv.spexipedia.server.feed;

import nu.fgv.spexipedia.server.basedata.BaseDataKind;
import nu.fgv.spexipedia.server.user.Caller;
import nu.fgv.spexipedia.server.user.CallerService;
import nu.fgv.spexipedia.server.util.error.BadRequestException;
import nu.fgv.spexipedia.server.util.security.SecurityUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Writes the change feed, in the same transaction as the change it records, and reads it for the applications that
 * keep a copy of base data (section 5.8 of the spec).
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Service
public class ChangeFeedService {

    static final int MAX_PAGE = 500;

    private final ChangeFeedEntryRepository repository;
    private final SyncConsumerRepository consumerRepository;
    private final CallerService callerService;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final Clock clock;

    public ChangeFeedService(final ChangeFeedEntryRepository repository, final SyncConsumerRepository consumerRepository,
                             final CallerService callerService, final ApplicationEventPublisher events,
                             final JsonMapper json, final Clock clock) {
        this.repository = repository;
        this.consumerRepository = consumerRepository;
        this.callerService = callerService;
        this.events = events;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Records a change; consumers are told once the transaction has committed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(final BaseDataKind kind, final UUID recordId, final ChangeType change,
                       final @Nullable UUID mergedIntoId, final Object payload) {
        final ChangeFeedEntry entry = new ChangeFeedEntry();

        entry.setKind(kind);
        entry.setRecordId(recordId);
        entry.setChangeType(change);
        entry.setMergedIntoId(mergedIntoId);
        entry.setPayload(json.writeValueAsString(payload));
        entry.setAt(clock.instant());
        repository.save(entry);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            events.publishEvent(new FeedChangedEvent());
        }
    }

    public void upsert(final BaseDataKind kind, final UUID recordId, final Object payload) {
        record(kind, recordId, ChangeType.UPSERT, null, payload);
    }

    /**
     * SYNC. A page of changes after the cursor; also remembers that the calling consumer has read up to the cursor.
     */
    @Transactional
    public Page page(final @Nullable String after, final int first) {
        final Caller caller = callerService.current();

        if (!caller.isSync()) {
            throw new AccessDeniedException("SYNC required");
        }

        final long cursor = parse(after);
        final List<ChangeFeedEntry> entries = repository.findBySequenceGreaterThanOrderBySequenceAsc(cursor,
                PageRequest.of(0, Math.clamp(first, 1, MAX_PAGE) + 1));
        final boolean hasMore = entries.size() > Math.clamp(first, 1, MAX_PAGE);
        final List<ChangeFeedEntry> page = hasMore ? entries.subList(0, entries.size() - 1) : entries;
        final long next = page.isEmpty() ? cursor : page.getLast().getSequence();

        acknowledge(cursor);
        return new Page(page, after == null && page.isEmpty() ? null : String.valueOf(next), hasMore);
    }

    private void acknowledge(final long cursor) {
        SecurityUtil.currentJwt()
                .map(jwt -> jwt.getClaimAsString("azp"))
                .flatMap(consumerRepository::findByClientId)
                .ifPresent(consumer -> {
                    if (consumer.getAcknowledged() == null || consumer.getAcknowledged() < cursor) {
                        consumer.setAcknowledged(cursor);
                    }
                    consumer.setAcknowledgedAt(clock.instant());
                });
    }

    private static long parse(final @Nullable String after) {
        if (after == null || after.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(after.strip());
        } catch (final NumberFormatException e) {
            throw new BadRequestException("error.cursor.invalid");
        }
    }

    public long latest() {
        return repository.latestSequence();
    }

    public long lag(final @Nullable Long acknowledged) {
        return repository.countBySequenceGreaterThan(acknowledged == null ? 0 : acknowledged);
    }

    /**
     * The payload as its record type.
     */
    public Object payload(final ChangeFeedEntry entry) {
        final Class<?> type = switch (entry.getKind()) {
            case SPEX_CATEGORY -> FeedRecords.FeedSpexCategory.class;
            case SPEX -> FeedRecords.FeedSpex.class;
            case TASK_CATEGORY -> FeedRecords.FeedTaskCategory.class;
            case TASK -> FeedRecords.FeedTask.class;
            case CHARACTER -> FeedRecords.FeedCharacter.class;
            case NUMBER -> FeedRecords.FeedNumber.class;
        };
        return json.readValue(entry.getPayload(), type);
    }

    /**
     * Published after a change has been recorded; the notifier tells the consumers once it is committed.
     */
    public record FeedChangedEvent() {
    }

    public record Page(List<ChangeFeedEntry> entries, @Nullable String cursor, boolean hasMore) {
    }
}
