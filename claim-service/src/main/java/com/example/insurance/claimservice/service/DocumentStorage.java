package com.example.insurance.claimservice.service;

import java.net.URI;
import java.time.Duration;

/**
 * Where claim documents are kept (CLAUDE.md uploads). The bucket is private: downloads go through a
 * presigned URL that expires. The S3 implementation is {@code S3DocumentStorage}; tests use an
 * in-memory one.
 */
public interface DocumentStorage {

  void put(String objectKey, String contentType, byte[] content);

  /** A URL that lets the holder download the object until it expires. */
  URI presignedDownload(String objectKey, Duration ttl);
}
