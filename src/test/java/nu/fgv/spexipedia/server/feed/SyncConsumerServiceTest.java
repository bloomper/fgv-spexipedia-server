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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Notifications carry Spexipedia's service token, so they go only to the applications' own APIs.
 */
class SyncConsumerServiceTest {

    private static final SpexipediaProperties.Apps APPS = new SpexipediaProperties.Apps(
            new SpexipediaProperties.App("http://spexregister-api:8080/api/graphql", null),
            new SpexipediaProperties.App("https://api.spexgram.example.org/api/graphql", null),
            new SpexipediaProperties.App("HTTPS://API.Spexify.Example.org:8443/api/graphql", null),
            new SpexipediaProperties.App("  ", null),
            new SpexipediaProperties.App(null, null),
            Duration.ofMinutes(5), Duration.ofSeconds(3));

    private final SyncConsumerService service = new SyncConsumerService(mock(SyncConsumerRepository.class), mock(ChangeFeedService.class),
            mock(CallerService.class), mock(AuditService.class),
            new SpexipediaProperties("Europe/Stockholm", "http://web", "http://api", null, null, null, null, null, APPS, null));

    @Test
    void theOriginsAreTheApplicationsWithAnAddress() {
        assertThat(SyncConsumerService.notifyOrigins(APPS)).containsExactlyInAnyOrder(
                "http://spexregister-api:8080", "https://api.spexgram.example.org:443", "https://api.spexify.example.org:8443");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://spexregister-api:8080/api/sync/notify",
            "https://api.spexgram.example.org/api/sync/notify",
            "https://api.spexgram.example.org:443/api/sync/notify",
            "https://API.SPEXIFY.example.org:8443/api/sync/notify"})
    void anApplicationsOwnAddressMayBeNotified(final String url) {
        assertThat(service.mayNotify(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://elsewhere.example.org/api/sync/notify",
            "http://api.spexgram.example.org/api/sync/notify",
            "https://api.spexgram.example.org:8443/api/sync/notify",
            "https://user:pass@api.spexgram.example.org/api/sync/notify",
            "https://api.spexgram.example.org.evil.example/api/sync/notify",
            "https://api.spexgram.example.org@evil.example/api/sync/notify",
            "ftp://spexregister-api:8080/api/sync/notify",
            "//spexregister-api:8080/api/sync/notify",
            "not a url",
            " "})
    void anyOtherAddressIsRefused(final String url) {
        assertThat(service.mayNotify(url)).isFalse();
    }

    @Test
    void noAddressIsRefused() {
        assertThat(service.mayNotify(null)).isFalse();
    }
}
