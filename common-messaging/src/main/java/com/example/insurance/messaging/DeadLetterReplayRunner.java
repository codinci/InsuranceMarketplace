package com.example.insurance.messaging;

import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Replays the configured dead-letter topics once, when the service starts. It exists only when
 * {@code messaging.dlt.replay-topics} is set, so a normal start never replays anything (runbook,
 * ADR-019). The operator sets the property for one run, after fixing the cause, and removes it.
 */
public class DeadLetterReplayRunner implements ApplicationRunner {

  private final DeadLetterReplayer replayer;
  private final String[] topics;
  private final int max;

  public DeadLetterReplayRunner(DeadLetterReplayer replayer, String[] topics, int max) {
    this.replayer = replayer;
    this.topics = Arrays.copyOf(topics, topics.length);
    this.max = max;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (String topic : topics) {
      replayer.replay(topic.trim(), max);
    }
  }
}
