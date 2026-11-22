package com.example.insurance.claimservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.claimservice.domain.BenefitUtilisation;
import com.example.insurance.claimservice.repository.BenefitUtilisationRepository;
import com.example.insurance.claimservice.support.FakeDocumentStorageConfig;
import com.example.insurance.claimservice.support.TestSecurityConfig;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The version on a running total (PLAN 11): the deterministic proof that a lost update cannot
 * happen. Two transactions read the same total. The first to commit wins; the second is refused
 * instead of silently overwriting it, which is what lets approval retry with the new total. A race
 * between two threads may or may not collide in a given run, so this test interleaves the two on
 * purpose.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, FakeDocumentStorageConfig.class})
class UtilisationLockingIT extends AbstractReviewIT {

  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private BenefitUtilisationRepository totals;

  @Test
  void aSecondWriterOfTheSameTotalIsRefusedInsteadOfOverwritingIt() {
    BenefitUtilisation seed =
        totals.saveAndFlush(BenefitUtilisation.start(UUID.randomUUID(), null, "OUTPATIENT", 1));
    TransactionTemplate outer = new TransactionTemplate(transactionManager);
    TransactionTemplate inner = new TransactionTemplate(transactionManager);
    inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    assertThatThrownBy(() ->
            outer.executeWithoutResult(status -> {
              // The outer transaction reads the total at version 0...
              BenefitUtilisation stale = totals.findById(seed.getId()).orElseThrow();
              // ...then another transaction reads it, adds 2000 and commits first...
              inner.executeWithoutResult(other -> {
                BenefitUtilisation fresh = totals.findById(seed.getId()).orElseThrow();
                fresh.reserve(new BigDecimal("2000"), true);
                totals.saveAndFlush(fresh);
              });
              // ...so this write is based on a total that is no longer current.
              stale.reserve(new BigDecimal("2000"), true);
              totals.saveAndFlush(stale);
            }))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);

    BenefitUtilisation stored = totals.findById(seed.getId()).orElseThrow();
    assertThat(stored.getAmountUsed()).isEqualByComparingTo("2000.00");
    assertThat(stored.getCountUsed()).isEqualTo(1);
    assertThat(stored.getVersion()).isEqualTo(1);
  }
}
