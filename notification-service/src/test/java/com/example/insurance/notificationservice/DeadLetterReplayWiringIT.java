package com.example.insurance.notificationservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.messaging.DeadLetterReplayRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * The operator's switch works: setting the property (or its environment variable) adds the runner.
 */
@TestPropertySource(properties = "messaging.dlt.replay-topics=claim.events.DLT, payment.events.DLT")
class DeadLetterReplayWiringIT extends AbstractNotificationIT {

  @Autowired private ApplicationContext context;

  @Test
  void thePropertyAddsTheRunnerAndTheServiceStillStarts() {
    assertThat(context.getBeansOfType(DeadLetterReplayRunner.class)).hasSize(1);
  }
}
