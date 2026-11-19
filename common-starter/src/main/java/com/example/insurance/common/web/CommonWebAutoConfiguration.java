package com.example.insurance.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registers the cross-cutting web pieces for every service that depends on common-starter.
 *
 * <p>Listed in {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, so Spring Boot
 * loads it automatically. Services do not need to scan this package.
 */
@AutoConfiguration
public class CommonWebAutoConfiguration {

  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
    FilterRegistrationBean<CorrelationIdFilter> registration =
        new FilterRegistrationBean<>(new CorrelationIdFilter());
    // Run first, so the id is set before any other filter or the security chain logs.
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
