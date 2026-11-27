package com.example.insurance.policyservice.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.policyservice.policy.service.PolicyExpiryJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The expiry job runs only while it holds its ShedLock row (PLAN 11, ADR-022). Another instance's
 * lock is simulated by writing the row directly, as that instance would.
 */
class ShedLockIT extends AbstractPolicyFlowIT {

  private static final String LOCK = "policy-expiry-job";
  private static final String OTHER = "another-instance";

  @Autowired PolicyExpiryJob job;
  @Autowired JdbcTemplate jdbc;

  private Bought policy;

  @BeforeEach
  void aPolicyWhoseCoverHasEnded() throws Exception {
    PAYMENTS.up();
    saveProfile(customer, PRINCIPAL_DOB, "MALE");
    policy = buyPolicy();
    pay(policy);
    jdbc.update(
        "update policies set start_date = ?, end_date = ? where id = ?",
        TODAY.minusYears(1),
        TODAY.minusDays(1),
        policy.policyId());
  }

  @Test
  void whileAnotherInstanceHoldsTheLockTheJobDoesNothing() throws Exception {
    lockHeldByAnotherInstanceFor("1 hour");

    job.run();

    assertThat(statusOf(policy.policyId())).isEqualTo("ACTIVE");
    assertThat(lockedBy()).isEqualTo(OTHER);
  }

  @Test
  void onceTheOtherLockHasRunOutThisInstanceTakesItAndRuns() throws Exception {
    lockHeldByAnotherInstanceFor("-1 second");

    job.run();

    assertThat(statusOf(policy.policyId())).isEqualTo("EXPIRED");
    assertThat(lockedBy()).isNotEqualTo(OTHER);
  }

  private void lockHeldByAnotherInstanceFor(String interval) {
    jdbc.update(
        "insert into shedlock (name, lock_until, locked_at, locked_by)"
            + " values (?, timezone('utc', now()) + cast(? as interval), timezone('utc', now()), ?)"
            + " on conflict (name) do update set lock_until = excluded.lock_until,"
            + " locked_at = excluded.locked_at, locked_by = excluded.locked_by",
        LOCK,
        interval,
        OTHER);
  }

  private String lockedBy() {
    return jdbc.queryForObject("select locked_by from shedlock where name = ?", String.class, LOCK);
  }
}
