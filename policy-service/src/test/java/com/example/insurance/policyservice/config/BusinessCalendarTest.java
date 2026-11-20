package com.example.insurance.policyservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BusinessCalendarTest {

  @Test
  void justAfterMidnightInNairobiItIsAlreadyTomorrow() {
    // 22:30 UTC on 20 November is 01:30 on 21 November in Nairobi (UTC+3).
    Clock clock = Clock.fixed(Instant.parse("2026-11-20T22:30:00Z"), ZoneOffset.UTC);
    BusinessCalendar calendar = new BusinessCalendar(clock, "Africa/Nairobi");
    assertThat(calendar.today()).isEqualTo(LocalDate.of(2026, 11, 21));
    assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(calendar.zone().getId()).isEqualTo("Africa/Nairobi");
  }
}
