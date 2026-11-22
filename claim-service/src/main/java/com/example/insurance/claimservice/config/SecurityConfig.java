package com.example.insurance.claimservice.config;

import com.example.insurance.common.Roles;
import com.example.insurance.common.problem.ServletSecurityProblems;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who can call what (PLAN 4). Claims belong to customers: each endpoint also checks ownership in
 * the service and answers 404 to anyone else. {@code /internal/**} needs a SERVICE token (ADR-014)
 * and is never routed by the gateway.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(
                    HttpMethod.GET,
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/health",
                    "/actuator/health/**")
                .permitAll()
                .requestMatchers("/internal/**")
                .hasRole(Roles.SERVICE)
                // Staff: the review queue and the decisions (M6). The insurer scope is checked in
                // the
                // service from the token, never here and never from the request.
                .requestMatchers(HttpMethod.GET, "/api/v1/claims/queue")
                .hasAnyRole(Roles.ADJUSTER, Roles.ADMIN)
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/claims/*/start-review",
                    "/api/v1/claims/*/request-info",
                    "/api/v1/claims/*/approve",
                    "/api/v1/claims/*/reject")
                .hasAnyRole(Roles.ADJUSTER, Roles.ADMIN)
                // The policy read model is the customer's own view of their policies (M5a).
                .requestMatchers(
                    HttpMethod.GET, "/api/v1/claims/policies", "/api/v1/claims/policies/**")
                .hasRole(Roles.CUSTOMER)
                // A claim, its history and its documents are readable by the customer who owns them
                // and by
                // staff of the claim's insurer; the service decides which claim each can see.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/claims/*",
                    "/api/v1/claims/*/history",
                    "/api/v1/claims/*/documents",
                    "/api/v1/claims/*/documents/*/download-url")
                .hasAnyRole(Roles.CUSTOMER, Roles.ADJUSTER, Roles.ADMIN)
                // Everything else on claims is the customer's: draft, edit, submit, withdraw,
                // respond, upload.
                .requestMatchers("/api/v1/claims/**")
                .hasRole(Roles.CUSTOMER)
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(oauth2 ->
            oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(ServletSecurityProblems.entryPoint()))
        .exceptionHandling(handling ->
            handling
                .authenticationEntryPoint(ServletSecurityProblems.entryPoint())
                .accessDeniedHandler(ServletSecurityProblems.accessDenied()));
    return http.build();
  }

  /** Maps the {@code roles} claim to Spring authorities as {@code ROLE_<name>} (ADR-006). */
  private static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}
