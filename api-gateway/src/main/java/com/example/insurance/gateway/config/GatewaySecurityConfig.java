package com.example.insurance.gateway.config;

import com.example.insurance.gateway.exception.GatewayProblems;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

/**
 * Coarse access at the edge (PLAN 3): anonymous routes are listed here, everything else needs a
 * valid signed-in token. Roles and insurer scoping are checked by each service, not here.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

  @Bean
  SecurityWebFilterChain gatewaySecurity(
      ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder, CorsConfigurationSource corsSource) {
    return http.cors(cors -> cors.configurationSource(corsSource))
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .authorizeExchange(exchanges ->
            exchanges
                .pathMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout")
                .permitAll()
                .pathMatchers(
                    HttpMethod.GET,
                    "/.well-known/jwks.json",
                    "/api/v1/insurers/**",
                    "/api/v1/plans/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/actuator/health",
                    "/actuator/health/**")
                .permitAll()
                .anyExchange()
                .authenticated())
        .oauth2ResourceServer(oauth2 ->
            oauth2
                .jwt(jwt -> jwt.jwtDecoder(jwtDecoder))
                .authenticationEntryPoint(GatewayProblems.unauthenticated()))
        .exceptionHandling(handling ->
            handling
                .authenticationEntryPoint(GatewayProblems.unauthenticated())
                .accessDeniedHandler(GatewayProblems.forbidden()))
        .build();
  }

  @Bean
  ReactiveJwtDecoder jwtDecoder(GatewayProperties properties) {
    NimbusReactiveJwtDecoder decoder =
        NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwt().jwkSetUri()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
    return decoder;
  }
}
