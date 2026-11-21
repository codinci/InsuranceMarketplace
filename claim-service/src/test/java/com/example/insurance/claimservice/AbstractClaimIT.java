package com.example.insurance.claimservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.insurance.claimservice.support.PolicyServiceStub;
import com.example.insurance.events.EventEnvelope;
import com.example.insurance.events.policy.PolicyActivated;
import com.example.insurance.events.policy.PolicyCancelled;
import com.example.insurance.events.policy.TermsSnapshot;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/**
 * Base for integration tests: one Postgres 17, one Kafka broker (the same images as compose) and a
 * stand-in for policy-service, shared by every test class. Tokens come from {@link
 * com.example.insurance.claimservice.support.TestTokens}.
 */
public abstract class AbstractClaimIT {

  static final UUID INSURER = UUID.fromString("a3c1e7f0-1111-4222-8333-444455556666");

  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.12-alpine");

  @ServiceConnection
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.2"));

  static final PolicyServiceStub POLICY_SERVICE = PolicyServiceStub.start();

  static {
    POSTGRES.start();
    KAFKA.start();
  }

  @DynamicPropertySource
  static void storageProperties(DynamicPropertyRegistry registry) {
    // Test-only credentials. The S3 client refuses to start without some (fail fast in production).
    registry.add("claim.storage.access-key", () -> "test-only-access");
    registry.add("claim.storage.secret-key", () -> "test-only-secret");
  }

  @DynamicPropertySource
  static void policyServiceProperties(DynamicPropertyRegistry registry) {
    registry.add("claim.policy-service.base-url", POLICY_SERVICE::baseUrl);
    registry.add(
        "claim.service-auth.token-url", () -> POLICY_SERVICE.baseUrl() + "/internal/service-token");
    registry.add("claim.service-auth.secret", () -> "test-only-secret");
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper json;
  @Autowired protected KafkaTemplate<String, String> kafka;

  /** Publishes an envelope the way policy-service does: the aggregate id is the Kafka key. */
  protected void publish(UUID eventId, String type, UUID policyId, Object payload)
      throws Exception {
    String value =
        json.writeValueAsString(
            new EventEnvelope<>(
                eventId,
                type,
                1,
                Instant.now(),
                policyId.toString(),
                INSURER.toString(),
                "it-test",
                payload));
    kafka.send("policy.events", policyId.toString(), value).get(10, TimeUnit.SECONDS);
  }

  protected static PolicyActivated activated(UUID policyId, UUID holder, long version) {
    TermsSnapshot terms =
        new TermsSnapshot(
            1,
            "ACACIA",
            UUID.fromString("00000000-0000-4000-8000-00000000cafe"),
            "STANDARD",
            1,
            "KES",
            "NEXT_BIRTHDAY",
            List.of(),
            List.of(),
            List.of(),
            90,
            null);
    return new PolicyActivated(
        policyId,
        "POL-ACACIA-2026-" + policyId.toString().substring(0, 6),
        version,
        holder,
        LocalDate.of(2026, 12, 1),
        LocalDate.of(2027, 11, 30),
        List.of(
            new PolicyActivated.MemberTerms(
                "M-1", "PRINCIPAL", "MALE", "Amina", "Test", LocalDate.of(1989, 1, 1), false)),
        terms);
  }

  /**
   * Terms with one outpatient tree (OUTPATIENT, with OP-CONSULT needing a RECEIPT) and a 30-day
   * illness waiting rule. Claims in the claim tests run against this.
   */
  protected static TermsSnapshot claimTerms() {
    TermsSnapshot.BenefitTerms consult =
        new TermsSnapshot.BenefitTerms(
            "OP-CONSULT", "SERVICE", null, null, List.of("RECEIPT"), false, List.of());
    TermsSnapshot.BenefitTerms outpatient =
        new TermsSnapshot.BenefitTerms(
            "OUTPATIENT", "CATEGORY", null, null, List.of(), false, List.of(consult));
    return new TermsSnapshot(
        1,
        "ACACIA",
        UUID.fromString("00000000-0000-4000-8000-00000000cafe"),
        "STANDARD",
        1,
        "KES",
        "NEXT_BIRTHDAY",
        List.of(new TermsSnapshot.ModuleTerms("OUTPATIENT", "OP-40K", outpatient)),
        List.of(
            new TermsSnapshot.WaitingRuleTerms(
                "GENERAL_ILLNESS", 30, 0, "ILLNESS", null, List.of(), List.of())),
        List.of(),
        90,
        null);
  }

  /** An activation with the given terms and cover dates, for a member numbered M-1. */
  protected static PolicyActivated activatedWithTerms(
      UUID policyId, UUID holder, long version, LocalDate coverStart, LocalDate coverEnd) {
    return new PolicyActivated(
        policyId,
        "POL-ACACIA-2026-" + policyId.toString().substring(0, 6),
        version,
        holder,
        coverStart,
        coverEnd,
        List.of(
            new PolicyActivated.MemberTerms(
                "M-1", "PRINCIPAL", "MALE", "Amina", "Test", LocalDate.of(1989, 1, 1), false)),
        claimTerms());
  }

  protected static PolicyCancelled cancelled(UUID policyId, long version) {
    return new PolicyCancelled(
        policyId,
        "POL-ACACIA-2026-" + policyId.toString().substring(0, 6),
        version,
        "UNPAID",
        LocalDate.of(2026, 12, 1));
  }

  /** The policy as the stub answers it for the fallback. */
  protected String fallbackPolicyJson(UUID policyId, UUID holder) throws Exception {
    return json.writeValueAsString(
        java.util.Map.of(
            "policyId",
            policyId.toString(),
            "policyNumber",
            "POL-FALLBACK-" + policyId.toString().substring(0, 6),
            "insurerId",
            INSURER.toString(),
            "holderUserId",
            holder.toString(),
            "status",
            "PENDING_PAYMENT",
            "coverStart",
            "2026-12-01",
            "coverEnd",
            "2027-11-30",
            "policyVersion",
            1,
            "members",
            List.of(),
            "termsSnapshot",
            java.util.Map.of(
                "snapshotVersion",
                1,
                "insurerCode",
                "ACACIA",
                "planVersion",
                1,
                "currency",
                "KES",
                "ageBasis",
                "NEXT_BIRTHDAY",
                "filingDeadlineDays",
                90)));
  }

  protected MvcResult getWith(String token, String path) throws Exception {
    return mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
  }

  protected static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString();
  }

  protected static <T> T read(MvcResult result, String path) throws Exception {
    return JsonPath.read(body(result), path);
  }

  /**
   * Polls until the read model shows the policy with {@code status}; listeners work asynchronously.
   */
  protected void awaitStatus(String token, UUID policyId, String status) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    String current = "none";
    while (System.nanoTime() < deadline) {
      MvcResult result = getWith(token, "/api/v1/claims/policies/" + policyId);
      if (result.getResponse().getStatus() == 200) {
        current = read(result, "$.status");
        if (status.equals(current)) {
          return;
        }
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Policy " + policyId + " stayed " + current + ", expected " + status);
  }
}
