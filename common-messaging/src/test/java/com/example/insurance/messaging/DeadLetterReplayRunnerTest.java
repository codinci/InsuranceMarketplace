package com.example.insurance.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class DeadLetterReplayRunnerTest {

  @Test
  void replaysEveryConfiguredTopicOnceWithTheConfiguredLimit() {
    DeadLetterReplayer replayer = mock(DeadLetterReplayer.class);
    DeadLetterReplayRunner runner =
        new DeadLetterReplayRunner(
            replayer, new String[] {"claim.events.DLT", " payment.events.DLT "}, 25);

    runner.run(new DefaultApplicationArguments());

    verify(replayer).replay("claim.events.DLT", 25);
    verify(replayer).replay("payment.events.DLT", 25);
    verifyNoMoreInteractions(replayer);
  }
}
