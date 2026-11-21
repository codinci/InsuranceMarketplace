package com.example.insurance.claimservice.dto;

import com.example.insurance.claimservice.domain.ClaimStatusHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** One entry of a claim's status history (PLAN 7). */
@Schema(description = "One status change of a claim.")
public record StatusChangeView(
    @Schema(example = "DRAFT") String from,
    @Schema(example = "SUBMITTED") String to,
    String actorRole,
    String reason,
    Instant changedAt) {

  public static StatusChangeView of(ClaimStatusHistory row) {
    return new StatusChangeView(
        row.getFromStatus() == null ? null : row.getFromStatus().name(),
        row.getToStatus().name(),
        row.getActorRole(),
        row.getReason(),
        row.getChangedAt());
  }
}
