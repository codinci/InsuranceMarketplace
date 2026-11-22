package com.example.insurance.claimservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.insurance.claimservice.domain.BenefitUtilisation;
import com.example.insurance.claimservice.repository.BenefitUtilisationRepository;
import com.example.insurance.claimservice.support.FakeDocumentStorageConfig;
import com.example.insurance.claimservice.support.TestSecurityConfig;
import com.example.insurance.claimservice.support.TestTokens;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Two claims on one shared limit, approved at the same instant (PLAN 11, M6): the end-to-end check
 * that the limit holds. A first claim is approved beforehand, so the running total already exists
 * and both contenders read the same row. The race is repeated, and every round the amounts paid add
 * up to the limit and never exceed it. A given run may or may not collide, so the deterministic
 * proof that a lost update is refused is {@link UtilisationLockingIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, FakeDocumentStorageConfig.class})
class ConcurrentApprovalIT extends AbstractReviewIT {

  private static final int ROUNDS = 8;

  @Autowired private BenefitUtilisationRepository totals;

  @Test
  void twoApprovalsOnOneSharedLimitNeverPayMoreThanTheLimit() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < ROUNDS; round++) {
        UUID holder = UUID.randomUUID();
        String customer = TestTokens.customer(holder);
        UUID policyId = policy(holder, terms(annualLimit("3000", 10), null, null));
        String staffA = adjuster(UUID.randomUUID());
        String staffB = adjuster(UUID.randomUUID());

        // 500 of the 3000 is used first, so the total row exists before the race.
        String first = underReview(customer, policyId, "500.00", staffA);
        assertThat(approve(staffA, first, Map.of()).getResponse().getStatus()).isEqualTo(200);
        String claimA = underReview(customer, policyId, "2000.00", staffA);
        String claimB = underReview(customer, policyId, "2000.00", staffB);

        List<MvcResult> results = approveTogether(pool, staffA, claimA, staffB, claimB);

        // Both succeed: the one that lost the race starts again from the new total.
        assertThat(results).allSatisfy(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal paid = BigDecimal.ZERO;
        for (MvcResult result : results) {
          BigDecimal amount = money(result, "$.amountApproved");
          amounts.add(amount);
          paid = paid.add(amount);
        }
        // 2500 is left: one claim gets its 2000, the other what remains, 500.
        assertThat(paid).as("round %d: paid in the race", round).isEqualByComparingTo("2500.00");
        assertThat(amounts).anySatisfy(a -> assertThat(a).isEqualByComparingTo("2000.00"));
        assertThat(amounts).anySatisfy(a -> assertThat(a).isEqualByComparingTo("500.00"));

        List<BenefitUtilisation> rows = totals.findByPolicyId(policyId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAmountUsed()).isEqualByComparingTo("3000.00");
        assertThat(rows.get(0).getCountUsed()).isEqualTo(3);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private List<MvcResult> approveTogether(
      ExecutorService pool, String staffA, String claimA, String staffB, String claimB)
      throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    Callable<MvcResult> a = () -> awaitThenApprove(ready, go, staffA, claimA);
    Callable<MvcResult> b = () -> awaitThenApprove(ready, go, staffB, claimB);
    Future<MvcResult> first = pool.submit(a);
    Future<MvcResult> second = pool.submit(b);
    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
    go.countDown();
    return List.of(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS));
  }

  private MvcResult awaitThenApprove(
      CountDownLatch ready, CountDownLatch go, String staff, String claimId) throws Exception {
    ready.countDown();
    go.await();
    return approve(staff, claimId, Map.of());
  }
}
