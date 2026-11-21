package com.example.insurance.claimservice.support;

import com.example.insurance.claimservice.service.DocumentStorage;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * An in-memory {@link DocumentStorage} for the integration tests, so they need no object store. The
 * real S3 path is exercised by the smoke script against SeaweedFS (PLAN 16.2).
 */
@TestConfiguration
public class FakeDocumentStorageConfig {

  @Bean
  @Primary
  DocumentStorage fakeDocumentStorage() {
    return new DocumentStorage() {
      private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

      @Override
      public void put(String objectKey, String contentType, byte[] content) {
        objects.put(objectKey, content.clone());
      }

      @Override
      public URI presignedDownload(String objectKey, Duration ttl) {
        return URI.create(
            "https://storage.example.test/" + objectKey + "?expires=" + ttl.toSeconds());
      }
    };
  }
}
