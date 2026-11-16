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


package nu.fgv.spexipedia.server.maintenance;

import lombok.extern.slf4j.Slf4j;
import nu.fgv.spexipedia.server.article.ArticleService;
import nu.fgv.spexipedia.server.feed.SyncNotifier;
import nu.fgv.spexipedia.server.image.ImageService;
import nu.fgv.spexipedia.server.imports.ImportFileStore;
import nu.fgv.spexipedia.server.links.LinkService;
import nu.fgv.spexipedia.server.search.SearchService;
import nu.fgv.spexipedia.server.user.CallerService;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Runs the maintenance jobs on their schedules, one run of each at a time, and remembers how the latest run of each
 * went, whether the schedule or ADMIN started it. The runs are kept in memory until the application restarts.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Component
public class MaintenanceJobs {

    private final Map<MaintenanceJob, Runnable> work;
    private final Set<MaintenanceJob> running = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<MaintenanceJob, Run> lastRuns = new ConcurrentHashMap<>();

    @Autowired
    public MaintenanceJobs(final ImageService imageService, final LinkService linkService, final SearchService searchService,
                           final SyncNotifier syncNotifier, final CallerService callerService,
                           final ArticleService articleService, final ImportFileStore importFiles) {
        this(Map.of(
                MaintenanceJob.IMAGE_VARIANTS, imageService::makeMissingVariants,
                MaintenanceJob.LINK_CHECK, linkService::check,
                MaintenanceJob.SEARCH_REBUILD, searchService::rebuild,
                MaintenanceJob.SYNC_NOTIFY, syncNotifier::notifyConsumers,
                MaintenanceJob.UPLOAD_CLEANUP, () -> {
                    imageService.cleanUpUploads();
                    importFiles.cleanUp();
                },
                MaintenanceJob.RETENTION, () -> {
                    callerService.cleanUp();
                    articleService.cleanUpEditing();
                }
        ));
    }

    MaintenanceJobs(final Map<MaintenanceJob, Runnable> work) {
        this.work = work;
    }

    @Scheduled(cron = "${spexipedia.jobs.image-variants.cron-expression}", zone = "${spexipedia.time-zone}")
    void imageVariants() {
        run(MaintenanceJob.IMAGE_VARIANTS);
    }

    @Scheduled(cron = "${spexipedia.jobs.link-check.cron-expression}", zone = "${spexipedia.time-zone}")
    void linkCheck() {
        run(MaintenanceJob.LINK_CHECK);
    }

    @Scheduled(cron = "${spexipedia.jobs.search-rebuild.cron-expression}", zone = "${spexipedia.time-zone}")
    void searchRebuild() {
        run(MaintenanceJob.SEARCH_REBUILD);
    }

    @Scheduled(cron = "${spexipedia.jobs.sync-notify.cron-expression}", zone = "${spexipedia.time-zone}")
    void syncNotify() {
        run(MaintenanceJob.SYNC_NOTIFY);
    }

    @Scheduled(cron = "${spexipedia.jobs.upload-cleanup.cron-expression}", zone = "${spexipedia.time-zone}")
    void uploadCleanup() {
        run(MaintenanceJob.UPLOAD_CLEANUP);
    }

    @Scheduled(cron = "${spexipedia.jobs.retention.cron-expression}", zone = "${spexipedia.time-zone}")
    void retention() {
        run(MaintenanceJob.RETENTION);
    }

    /**
     * Runs the job in the calling thread unless it is already running.
     *
     * @param job the job
     * @return whether it ran
     */
    public boolean run(final MaintenanceJob job) {
        if (!running.add(job)) {
            log.info("Not starting {}, it is already running", job);
            return false;
        }

        final Instant startedAt = Instant.now();
        boolean succeeded = false;

        lastRuns.put(job, new Run(startedAt, null, null));
        try {
            work.get(job).run();
            succeeded = true;
        } catch (final RuntimeException e) {
            log.error("{} failed", job, e);
        } finally {
            lastRuns.put(job, new Run(startedAt, Instant.now(), succeeded));
            running.remove(job);
        }
        return true;
    }

    /**
     * @param job the job
     * @return whether it is running
     */
    public boolean isRunning(final MaintenanceJob job) {
        return running.contains(job);
    }

    /**
     * @param job the job
     * @return its latest run since the application started
     */
    public Optional<Run> lastRun(final MaintenanceJob job) {
        return Optional.ofNullable(lastRuns.get(job));
    }

    /**
     * A run of a job.
     *
     * @param startedAt  when it started
     * @param finishedAt when it finished, or null while it runs
     * @param succeeded  whether it finished without an error, or null while it runs
     */
    public record Run(Instant startedAt, @Nullable Instant finishedAt, @Nullable Boolean succeeded) {
    }
}
