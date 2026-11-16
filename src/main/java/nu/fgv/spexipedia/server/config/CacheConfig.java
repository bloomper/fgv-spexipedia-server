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

package nu.fgv.spexipedia.server.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caches for what other applications answer. People from Spexregister are kept in memory for at most ten minutes and
 * never written anywhere (section 5.3 of the spec); links and photos for the configured few minutes.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PARTICIPANTS = "participants";
    public static final String SPEXARE = "spexare";
    public static final String LINKS = "links";
    public static final String PHOTOS = "photos";

    @Bean
    public CacheManager cacheManager(final SpexipediaProperties properties) {
        final CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        final Duration people = Duration.ofMinutes(10);
        final Duration apps = properties.apps().cacheTtl();

        cacheManager.registerCustomCache(PARTICIPANTS, Caffeine.newBuilder().expireAfterWrite(people).maximumSize(2_000).build());
        cacheManager.registerCustomCache(SPEXARE, Caffeine.newBuilder().expireAfterWrite(people).maximumSize(20_000).build());
        cacheManager.registerCustomCache(LINKS, Caffeine.newBuilder().expireAfterWrite(apps).maximumSize(5_000).build());
        // Signed addresses from Spexgram stay valid for hours; a few minutes is well within that
        cacheManager.registerCustomCache(PHOTOS, Caffeine.newBuilder().expireAfterWrite(apps).maximumSize(5_000).build());
        return cacheManager;
    }
}
