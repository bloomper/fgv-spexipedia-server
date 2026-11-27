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

package nu.fgv.spexipedia.server.spexpackage;

import lombok.extern.slf4j.Slf4j;
import nu.fgv.spexipedia.server.config.ServiceClientsConfig;
import nu.fgv.spexipedia.server.config.SpexipediaProperties;
import nu.fgv.spexipedia.server.user.App;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.io.FileSystemResource;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;

/**
 * The import APIs of Spexarkivet, Spexify, Spexflix and Spexgram (their spec 5.x, "the import API"), called with
 * {@code fgv-spexipedia-service}'s {@code IMPORT} role: {@code HEAD}/{@code PUT /api/import/files/<sha256>} for
 * files and {@code importBatch} for records. Unlike the links, calls may take long (large files), so this client
 * has its own timeouts.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Slf4j
@Component
public class PackageApps {

    /**
     * The applications a package writes to besides Spexipedia, in the order they are applied.
     */
    public static final List<App> APPS = List.of(App.ARCHIVE, App.SPEXIFY, App.SPEXFLIX, App.SPEXGRAM);

    static final String BATCH = """
            mutation($input: ImportBatchInput!, $dryRun: Boolean!) { importBatch(input: $input, dryRun: $dryRun) {
                created updated unchanged kept failed items {
                    kind sourceKey id outcome
                    messageSv: message(locale: "sv") messageEn: message(locale: "en")
                    warningsSv: warnings(locale: "sv") warningsEn: warnings(locale: "en")
                }
            } }""";

    /**
     * What an import call gave.
     */
    public record BatchResult(List<PackageReport.Item> items) {
    }

    private final Map<App, String> graphqlUrls = new EnumMap<>(App.class);
    private final Map<App, String> webUrls = new EnumMap<>(App.class);
    private final RestClient rest;

    public PackageApps(final SpexipediaProperties properties, final OAuth2AuthorizedClientManager serviceAuthorizedClientManager) {
        final Map<App, SpexipediaProperties.App> apps = Map.of(App.ARCHIVE, properties.apps().archive(), App.SPEXIFY,
                properties.apps().spexify(), App.SPEXFLIX, properties.apps().spexflix(), App.SPEXGRAM, properties.apps().spexgram());

        apps.forEach((app, config) -> {
            if (StringUtils.hasText(config.graphqlUrl())) {
                graphqlUrls.put(app, config.graphqlUrl());
            }
            if (StringUtils.hasText(config.webUrl())) {
                webUrls.put(app, config.webUrl().replaceAll("/+$", ""));
            }
        });
        this.rest = RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.defaults()
                        .withConnectTimeout(Duration.ofSeconds(10))
                        .withReadTimeout(Duration.ofMinutes(30))))
                .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(serviceAuthorizedClientManager))
                .defaultRequest(request -> request.attributes(clientRegistrationId(ServiceClientsConfig.REGISTRATION_ID)))
                .build();
    }

    public boolean configured(final App app) {
        return graphqlUrls.containsKey(app);
    }

    public @Nullable String webUrl(final App app) {
        return webUrls.get(app);
    }

    /**
     * Sends the file unless the application has it already.
     */
    public void upload(final App app, final String sha256, final Path file) {
        final String url = filesUrl(app) + sha256;

        try {
            rest.head().uri(url).retrieve().toBodilessEntity();
            return;
        } catch (final HttpClientErrorException.NotFound e) {
            // Not there yet
        }
        rest.put().uri(url).contentType(MediaType.APPLICATION_OCTET_STREAM).body(new FileSystemResource(file)).retrieve().toBodilessEntity();
    }

    /**
     * One call of the application's {@code importBatch}.
     */
    public BatchResult importBatch(final App app, final Map<String, Object> input, final boolean dryRun) {
        final HttpSyncGraphQlClient client = HttpSyncGraphQlClient.builder(rest).url(graphqlUrls.get(app)).build();
        final ClientGraphQlResponse response = client.document(BATCH).variable("input", input).variable("dryRun", dryRun).executeSync();

        if (!response.isValid() || !response.getErrors().isEmpty()) {
            throw new IllegalStateException(app + " answered " + response.getErrors());
        }

        final List<PackageReport.Item> items = new ArrayList<>();

        final @Nullable List<Map<String, Object>> answered = response.field("importBatch.items").getValue();

        // Each text in both languages, kept so the report can be read in either
        for (final Map<String, Object> item : answered == null ? List.<Map<String, Object>>of() : answered) {
            final List<String> swedish = strings(item.get("warningsSv"));
            final List<String> english = strings(item.get("warningsEn"));
            final List<PackageText> warnings = new ArrayList<>();

            for (int i = 0; i < swedish.size(); i++) {
                warnings.add(PackageText.translated(swedish.get(i), i < english.size() ? english.get(i) : swedish.get(i)));
            }
            items.add(new PackageReport.Item(String.valueOf(item.get("kind")), String.valueOf(item.get("sourceKey")),
                    item.get("id") == null ? null : String.valueOf(item.get("id")), String.valueOf(item.get("outcome")),
                    item.get("messageSv") == null ? null : PackageText.translated(String.valueOf(item.get("messageSv")),
                            String.valueOf(Objects.requireNonNullElse(item.get("messageEn"), item.get("messageSv")))), warnings));
        }
        return new BatchResult(items);
    }

    private static List<String> strings(final @Nullable Object value) {
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private String filesUrl(final App app) {
        return graphqlUrls.get(app).replaceFirst("/graphql/?$", "") + "/import/files/";
    }
}
