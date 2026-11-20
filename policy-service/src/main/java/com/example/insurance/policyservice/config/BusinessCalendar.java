package com.example.insurance.policyservice.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "Today" for business dates (PLAN 3: instants are UTC, cover dates are {@code LocalDate}). Cover
 * starts, sale periods and policy-number years follow the market's calendar, not UTC: just after
 * midnight in Nairobi it is still the previous day in UTC (D38).
 */
@Component
public class BusinessCalendar {

  private final Clock clock;
  private final ZoneId zone;

  public BusinessCalendar(
      Clock clock, @Value("${policy.business-zone:Africa/Nairobi}") String zone) {
    this.clock = clock;
    this.zone = ZoneId.of(zone);
  }

  public LocalDate today() {
    return LocalDate.now(clock.withZone(zone));
  }

  public ZoneId zone() {
    return zone;
  }
}
