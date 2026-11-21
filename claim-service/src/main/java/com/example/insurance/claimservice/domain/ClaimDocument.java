package com.example.insurance.claimservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An uploaded document. The bytes live in object storage under a random key (CLAUDE.md uploads).
 */
@Entity
@Table(name = "claim_documents")
public class ClaimDocument {

  @Id private UUID id;

  @Column(name = "claim_id", nullable = false)
  private UUID claimId;

  @Column(name = "object_key", nullable = false, unique = true, length = 80)
  private String objectKey;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 60)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "doc_type", nullable = false, length = 40)
  private String docType;

  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt;

  protected ClaimDocument() {
    // For JPA.
  }

  public ClaimDocument(
      UUID id,
      UUID claimId,
      String objectKey,
      String fileName,
      String contentType,
      long sizeBytes,
      String docType,
      Instant uploadedAt) {
    this.id = id;
    this.claimId = claimId;
    this.objectKey = objectKey;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.docType = docType;
    this.uploadedAt = uploadedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getClaimId() {
    return claimId;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getDocType() {
    return docType;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }
}
