package com.example.insurance.hello;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class HelloServiceContextTest {

  @Autowired private ApplicationContext context;

  @Test
  void contextStartsWithTheHelloController() {
    assertThat(context.getBean(HelloController.class)).isNotNull();
  }
}
