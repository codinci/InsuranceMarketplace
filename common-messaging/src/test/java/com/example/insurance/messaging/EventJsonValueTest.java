package com.example.insurance.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Stored payloads round-trip through the shared mapper, and amounts stay BigDecimal (M5a). */
class EventJsonValueTest {

  record Stored(UUID id, BigDecimal amount, String note) {}

  @Test
  void aValueWrittenAsJsonReadsBackEqual() {
    Stored original = new Stored(UUID.randomUUID(), new BigDecimal("1234.50"), "kept whole");

    String json = EventJson.writeValue(original);

    assertThat(EventJson.read(json, Stored.class)).isEqualTo(original);
  }

  @Test
  void amountsReadBackAsBigDecimalWithTheirScale() {
    Stored read = EventJson.read("{\"amount\":0.10}", Stored.class);

    assertThat(read.amount()).isEqualByComparingTo("0.10");
    assertThat(read.amount().scale()).isEqualTo(2);
  }
}
