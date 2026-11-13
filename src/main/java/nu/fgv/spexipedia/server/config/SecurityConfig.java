package nu.fgv.spexipedia.server.config;

import nu.fgv.spexipedia.server.util.security.KeycloakJwtRolesConverter;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A stateless JWT resource server bound to {@code fgv-spexipedia} tokens and the service accounts of the applications
 * that read base data. GraphQL is open at HTTP level, without a token too, because the services decide access; image
 * uploads need a token.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(final HttpSecurity http,
                                                   final JwtAuthenticationConverter jwtAuthenticationConverter) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize ->
                        authorize
                                .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                                .requestMatchers(HttpMethod.GET, "/docs/**").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/graphql").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/graphql").permitAll()
                                .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    @Bean
    public OAuth2TokenValidator<Jwt> authorizedPartyValidator(final SpexipediaProperties properties) {
        return new JwtClaimValidator<String>("azp", properties.security().authorizedParties()::contains);
    }

    @Bean
    public OAuth2TokenValidator<Jwt> accessTokenTypeValidator() {
        return new JwtClaimValidator<String>("typ", "Bearer"::equals);
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        final DelegatingJwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new DelegatingJwtGrantedAuthoritiesConverter(
                new JwtGrantedAuthoritiesConverter(),
                new KeycloakJwtRolesConverter()
        );
        final JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();

        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }
}
