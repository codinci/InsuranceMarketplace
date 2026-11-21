package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.config.StorageProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import java.time.Duration;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * The AWS SDK v2 client against SeaweedFS (PLAN 12, M0b). Uploads go to the internal endpoint.
 * Presigned URLs are built with the public endpoint, because the customer's browser downloads them.
 */
@Component
public class S3DocumentStorage implements DocumentStorage {

  private final S3Client s3;
  private final S3Presigner presigner;
  private final String bucket;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The AWS clients are thread-safe and closed by Spring (ADR-016).")
  public S3DocumentStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
    this.s3 = s3;
    this.presigner = presigner;
    this.bucket = properties.bucket();
  }

  @Override
  public void put(String objectKey, String contentType, byte[] content) {
    s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(objectKey).contentType(contentType).build(),
        RequestBody.fromBytes(content));
  }

  @Override
  public URI presignedDownload(String objectKey, Duration ttl) {
    GetObjectPresignRequest request =
        GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(objectKey).build())
            .build();
    try {
      return presigner.presignGetObject(request).url().toURI();
    } catch (java.net.URISyntaxException e) {
      throw new IllegalStateException("The presigned URL is not a valid URI.", e);
    }
  }
}
