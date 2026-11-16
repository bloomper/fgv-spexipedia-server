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

/**
 * The scheduled maintenance jobs, which ADMIN can also start by hand. Their schedules are set in the
 * {@code spexipedia.jobs} properties and run in {@code spexipedia.time-zone}.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
public enum MaintenanceJob {
    IMAGE_VARIANTS("spexipedia.jobs.image-variants.cron-expression"),
    LINK_CHECK("spexipedia.jobs.link-check.cron-expression"),
    SEARCH_REBUILD("spexipedia.jobs.search-rebuild.cron-expression"),
    SYNC_NOTIFY("spexipedia.jobs.sync-notify.cron-expression"),
    UPLOAD_CLEANUP("spexipedia.jobs.upload-cleanup.cron-expression"),
    RETENTION("spexipedia.jobs.retention.cron-expression");

    private final String cronProperty;

    MaintenanceJob(final String cronProperty) {
        this.cronProperty = cronProperty;
    }

    /**
     * @return the name of the property holding the job's cron expression
     */
    public String cronProperty() {
        return cronProperty;
    }
}
