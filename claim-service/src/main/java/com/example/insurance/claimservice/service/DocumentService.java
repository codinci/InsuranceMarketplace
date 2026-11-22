package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.config.StorageProperties;
import com.example.insurance.claimservice.domain.Claim;
import com.example.insurance.claimservice.domain.ClaimDocument;
import com.example.insurance.claimservice.domain.ClaimStatus;
import com.example.insurance.claimservice.dto.DocumentView;
import com.example.insurance.claimservice.dto.DownloadUrlView;
import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;
import com.example.insurance.claimservice.repository.ClaimDocumentRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claim documents (PLAN 12, M5b). Documents are added while the claim is a draft, at most ten per
 * claim. Downloads are presigned and short-lived; the bucket itself is never public.
 */
@Service
public class DocumentService {

  /** Upper bound per claim, so a claim cannot be used as storage (CLAUDE.md uploads). */
  static final int MAX_DOCUMENTS = 10;

  private static final String DOC_TYPE_PATTERN = "^[A-Z][A-Z_]{1,39}$";

  private final ClaimDocumentRepository documents;
  private final ClaimLookup claims;
  private final DocumentStorage storage;
  private final StorageProperties properties;
  private final Clock clock;

  // DocumentStorage is a port whose implementations are thread-safe and

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "DocumentStorage is a port whose implementations are thread-safe and owned by Spring (ADR-016).")
  public DocumentService(
      ClaimDocumentRepository documents,
      ClaimLookup claims,
      DocumentStorage storage,
      StorageProperties properties,
      Clock clock) {
    this.documents = documents;
    this.claims = claims;
    this.storage = storage;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Checks the file (size, type, magic bytes), stores it under a random key, and records it. The
   * bytes go to storage before the row is saved; a failure after the upload leaves an unreferenced
   * object, which the lifecycle job (M8) will clean up.
   */
  @Transactional
  public DocumentView upload(
      UUID userId,
      UUID claimId,
      String docType,
      String fileName,
      String contentType,
      byte[] content) {
    Claim claim = claims.ownedBy(userId, claimId);
    if (claim.getStatus() != ClaimStatus.DRAFT && claim.getStatus() != ClaimStatus.INFO_REQUESTED) {
      throw new ApiException(
          ErrorCode.CLAIM_NOT_EDITABLE,
          "Documents can be added to a draft, or while information is requested.");
    }
    if (docType == null || !docType.matches(DOC_TYPE_PATTERN)) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "The document type must be upper case, for example INVOICE.");
    }
    DocumentChecks.check(contentType, content);
    if (documents.countByClaimId(claimId) >= MAX_DOCUMENTS) {
      throw new ApiException(
          ErrorCode.DOCUMENT_LIMIT_REACHED,
          "A claim can have at most " + MAX_DOCUMENTS + " documents.");
    }

    String objectKey = "claims/" + UUID.randomUUID();
    storage.put(objectKey, contentType, content);
    Instant now = clock.instant();
    ClaimDocument document =
        new ClaimDocument(
            UUID.randomUUID(),
            claimId,
            objectKey,
            safeName(fileName),
            contentType,
            content.length,
            docType,
            now);
    documents.save(document);
    return DocumentView.of(document);
  }

  @Transactional(readOnly = true)
  public Page<DocumentView> list(Actor actor, UUID claimId, Pageable pageable) {
    claims.visible(actor, claimId);
    return documents.findByClaimId(claimId, pageable).map(DocumentView::of);
  }

  /** A link that works for {@code presign-ttl} from now. Only the claim's owner gets one. */
  @Transactional(readOnly = true)
  public DownloadUrlView downloadUrl(Actor actor, UUID claimId, UUID documentId) {
    claims.visible(actor, claimId);
    ClaimDocument document =
        documents
            .findByIdAndClaimId(documentId, claimId)
            .orElseThrow(() -> new ApiException(ErrorCode.DOCUMENT_NOT_FOUND, "No such document."));
    Instant now = clock.instant();
    return new DownloadUrlView(
        storage.presignedDownload(document.getObjectKey(), properties.presignTtl()).toString(),
        now.plus(properties.presignTtl()));
  }

  /** The file name as the customer gave it, reduced to safe characters for display. */
  static String safeName(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "document";
    }
    String cleaned = fileName.replaceAll("[^A-Za-z0-9._ -]", "_");
    return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
  }
}
