package com.example.insurance.auth.config;

import com.example.insurance.common.Roles;
import com.example.insurance.common.problem.ServletSecurityProblems;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who can call what (PLAN 4). Staff rules that depend on the insurer are checked in the service
 * layer, because a path rule cannot see the insurer. Role names come from the token's {@code roles}
 * claim.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final int BCRYPT_STRENGTH = 12;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth ->
            auth.requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout")
                .permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/.well-known/jwks.json",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/health",
                    "/actuator/health/**")
                .permitAll()
                .requestMatchers("/api/v1/admin/**")
                .hasRole(Roles.ADMIN)
                .anyRequest()
                .authenticated())
        .oauth2ResourceServer(oauth2 ->
            oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .exceptionHandling(handling ->
            handling
                .authenticationEntryPoint(ServletSecurityProblems.entryPoint())
                .accessDeniedHandler(ServletSecurityProblems.accessDenied()));
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
  }

  /** Maps the {@code roles} claim to Spring authorities as {@code ROLE_<name>}. */
  private static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}
