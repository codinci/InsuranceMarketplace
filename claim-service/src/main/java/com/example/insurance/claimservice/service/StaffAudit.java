package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.exception.ApiException;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The audit trail of staff actions (PLAN 12, M6): one structured log line for every approval,
 * rejection, request for information and review start, and for every one that is refused or fails.
 * The logger is named {@code audit}, so it can be routed on its own. A line holds identifiers and
 * the stable error code only: never a name, a message the reviewer typed, an amount reason, or a
 * request body (CLAUDE.md, no PII in logs).
 */
@Component
public class StaffAudit {

  private static final Logger AUDIT = LoggerFactory.getLogger("audit");

  /**
   * Runs {@code body}, then writes one audit line with its outcome. The result or error passes on.
   */
  public <T> T run(Actor staff, String action, UUID claimId, Supplier<T> body) {
    try {
      T result = body.get();
      AUDIT
          .atInfo()
          .addKeyValue("event", "staff_action")
          .addKeyValue("action", action)
          .addKeyValue("outcome", "SUCCESS")
          .addKeyValue("claimId", claimId)
          .addKeyValue("actorId", staff.userId())
          .addKeyValue("role", staff.role())
          .addKeyValue("insurerId", staff.insurerId())
          .log("Staff action succeeded.");
      return result;
    } catch (ApiException e) {
      AUDIT
          .atWarn()
          .addKeyValue("event", "staff_action")
          .addKeyValue("action", action)
          .addKeyValue("outcome", "REFUSED")
          .addKeyValue("code", e.code().name())
          .addKeyValue("claimId", claimId)
          .addKeyValue("actorId", staff.userId())
          .addKeyValue("role", staff.role())
          .addKeyValue("insurerId", staff.insurerId())
          .log("Staff action refused.");
      throw e;
    } catch (RuntimeException e) {
      AUDIT
          .atError()
          .addKeyValue("event", "staff_action")
          .addKeyValue("action", action)
          .addKeyValue("outcome", "ERROR")
          .addKeyValue("code", e.getClass().getSimpleName())
          .addKeyValue("claimId", claimId)
          .addKeyValue("actorId", staff.userId())
          .addKeyValue("role", staff.role())
          .addKeyValue("insurerId", staff.insurerId())
          .log("Staff action failed.");
      throw e;
    }
  }
}
