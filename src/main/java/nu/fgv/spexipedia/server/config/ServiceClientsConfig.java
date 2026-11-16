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

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;

/**
 * HTTP clients for the other applications: one with {@code fgv-spexipedia-service}'s token (client credentials), which
 * holds Spexregister's {@code SPEXIPEDIA}, Spexgram's, Spexify's and Spexflix's {@code SPEXIPEDIA}, Spexarkivet's
 * {@code LINKS} and every consumer's {@code NOTIFY}; and one without a token, for what is public (Spexgram's public
 * photos for visitors). Both time out quickly: pages are shown without what does not answer.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Configuration
public class ServiceClientsConfig {

    public static final String REGISTRATION_ID = "fgv-spexipedia-service";

    @Bean
    public OAuth2AuthorizedClientManager serviceAuthorizedClientManager(final ClientRegistrationRepository clientRegistrationRepository,
                                                                        final OAuth2AuthorizedClientService authorizedClientService) {
        final AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService);

        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    /**
     * Calls with {@code fgv-spexipedia-service}'s token.
     */
    @Bean
    public RestClient serviceRestClient(final SpexipediaProperties properties,
                                        final OAuth2AuthorizedClientManager serviceAuthorizedClientManager) {
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.defaults()
                        .withConnectTimeout(properties.apps().timeout())
                        .withReadTimeout(properties.apps().timeout())))
                .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(serviceAuthorizedClientManager))
                .defaultRequest(request -> request.attributes(clientRegistrationId(REGISTRATION_ID)))
                .build();
    }

    /**
     * Calls without a token, for what the other applications show visitors.
     */
    @Bean
    public RestClient anonymousRestClient(final SpexipediaProperties properties) {
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.defaults()
                        .withConnectTimeout(properties.apps().timeout())
                        .withReadTimeout(properties.apps().timeout())))
                .build();
    }
}
