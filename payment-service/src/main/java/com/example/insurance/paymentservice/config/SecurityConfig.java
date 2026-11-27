package com.example.insurance.paymentservice.config;

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
 * Who can call what (PLAN 4). Payments belong to customers: each endpoint also checks that the
 * payment is the caller's, in the service. Finding and retrying a payout is for an ADMIN, scoped to
 * the token's insurer in the service. {@code /internal/**} needs a SERVICE token (ADR-014) and is
 * never routed by the gateway.
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
                .requestMatchers(
                    "/api/v1/payments/payouts",
                    "/api/v1/payments/*/retry-payout",
                    "/api/v1/payments/refunds",
                    "/api/v1/payments/*/retry-refund")
                .hasRole(Roles.ADMIN)
                .requestMatchers("/api/v1/payments/**")
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
