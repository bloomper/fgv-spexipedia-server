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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * An application that keeps a copy of base data: who it is (its service account, {@code azp}), where to tell it about
 * changes, and how far it has read the feed.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Entity
@Table(name = "sync_consumer")
// Admins change the name, address and switch; the feed and the notifier write the rest with SQL of their own
@DynamicUpdate
@Getter
@Setter
public class SyncConsumer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "notify_url")
    private @Nullable String notifyUrl;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "acknowledged")
    private @Nullable Long acknowledged;

    @Column(name = "acknowledged_at")
    private @Nullable Instant acknowledgedAt;

    @Column(name = "notified")
    private @Nullable Long notified;

    @Column(name = "last_notified_at")
    private @Nullable Instant lastNotifiedAt;

    @Column(name = "last_error")
    private @Nullable String lastError;
}
