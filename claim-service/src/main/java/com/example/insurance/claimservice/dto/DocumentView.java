package com.example.insurance.claimservice.dto;

import com.example.insurance.claimservice.domain.ClaimDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** An uploaded document. The storage key is never shown to the customer. */
@Schema(description = "An uploaded claim document.")
public record DocumentView(
    UUID id,
    @Schema(example = "invoice.pdf") String fileName,
    @Schema(example = "application/pdf") String contentType,
    long sizeBytes,
    @Schema(example = "INVOICE") String docType,
    Instant uploadedAt) {

  public static DocumentView of(ClaimDocument document) {
    return new DocumentView(
        document.getId(),
        document.getFileName(),
        document.getContentType(),
        document.getSizeBytes(),
        document.getDocType(),
        document.getUploadedAt());
  }
}
