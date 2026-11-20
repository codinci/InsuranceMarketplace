package com.example.insurance.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * The servlet-only condition: the MVC correlation filter exists in servlet apps and nowhere else
 * (PLAN 15, M2).
 */
class CommonWebAutoConfigurationTest {

  private final ApplicationContextRunner nonWeb =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class));

  private final WebApplicationContextRunner servlet =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class));

  @Test
  void theServletFilterIsRegisteredInAServletApplication() {
    servlet.run(context -> assertThat(context).hasSingleBean(FilterRegistrationBean.class));
  }

  @Test
  void theServletFilterIsNotRegisteredOutsideAServletApplication() {
    nonWeb.run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
  }
}
