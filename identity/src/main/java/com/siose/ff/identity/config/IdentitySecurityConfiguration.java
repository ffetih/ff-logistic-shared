package com.siose.ff.identity.config;

import com.siose.ff.identity.security.IdentityJwtAuthenticationConverter;
import com.siose.ff.identity.security.RefreshableJwtDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Login, refresh, logout, JWKS and the service-token broker are open on this service.
 * User administration requires a realm token with TENANT_ADMIN. Tokens are checked with the
 * cached Keycloak JWKS, so this process does not call Keycloak to validate them.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class IdentitySecurityConfiguration {

    @Bean
    SecurityFilterChain identitySecurityFilterChain(HttpSecurity http, RefreshableJwtDecoder jwtDecoder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/sessions", "/sessions/refresh", "/sessions/client", "/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/jwks").permitAll()
                        .requestMatchers(HttpMethod.POST, "/service-tokens").permitAll()
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole("TENANT_ADMIN")
                        .requestMatchers("/api/v1/integration-clients", "/api/v1/integration-clients/**").hasRole("TENANT_ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(new IdentityJwtAuthenticationConverter())));
        return http.build();
    }
}
