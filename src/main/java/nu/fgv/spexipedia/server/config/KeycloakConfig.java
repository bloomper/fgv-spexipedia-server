package nu.fgv.spexipedia.server.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Keycloak admin client ({@code fgv-admin}), used only by the sample data import, which grants roles to the
 * sample users.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Configuration(proxyBeanMethods = false)
public class KeycloakConfig {

    @Bean(destroyMethod = "close")
    public Keycloak keycloakAdmin(final SpexipediaProperties properties) {
        final SpexipediaProperties.Keycloak keycloak = properties.keycloak();

        return KeycloakBuilder.builder()
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .serverUrl(keycloak.url())
                .realm(keycloak.realm())
                .clientId(keycloak.adminClientId())
                .clientSecret(keycloak.adminClientSecret())
                .build();
    }
}
