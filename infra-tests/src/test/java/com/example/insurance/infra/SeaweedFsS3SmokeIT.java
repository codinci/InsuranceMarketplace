package com.example.insurance.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Puts and gets an object through the AWS SDK v2 against SeaweedFS with an identity, the same way
 * the application will talk to it (path-style, custom endpoint, PLAN 12).
 */
@Testcontainers
class SeaweedFsS3SmokeIT {

  private static final String ACCESS_KEY = "test-access";
  private static final String SECRET_KEY = "test-secret";
  private static final int S3_PORT = 8333;

  /** Same identity file shape that docker-compose writes from the environment. */
  private static final String IDENTITY_JSON =
      "{\"identities\":[{\"name\":\"app\",\"credentials\":[{\"accessKey\":\""
          + ACCESS_KEY
          + "\",\"secretKey\":\""
          + SECRET_KEY
          + "\"}],\"actions\":[\"Admin\",\"Read\",\"Write\",\"List\",\"Tagging\"]}]}";

  @Container
  static final GenericContainer<?> SEAWEEDFS =
      new GenericContainer<>("chrislusf/seaweedfs:4.54")
          .withCopyToContainer(Transferable.of(IDENTITY_JSON), "/etc/seaweedfs/s3.json")
          .withCommand(
              "server",
              "-dir=/data",
              "-s3",
              "-s3.port=" + S3_PORT,
              "-s3.config=/etc/seaweedfs/s3.json")
          .withExposedPorts(S3_PORT)
          .waitingFor(Wait.forListeningPort());

  @Test
  void putThenGetReturnsTheSameBytes() {
    try (S3Client s3 = client()) {
      s3.createBucket(CreateBucketRequest.builder().bucket("insurance-documents").build());
      byte[] content = "claim-document-bytes".getBytes(StandardCharsets.UTF_8);

      s3.putObject(
          PutObjectRequest.builder()
              .bucket("insurance-documents")
              .key("claims/0001/random-key.pdf")
              .build(),
          software.amazon.awssdk.core.sync.RequestBody.fromBytes(content));

      byte[] readBack =
          s3.getObjectAsBytes(
                  GetObjectRequest.builder()
                      .bucket("insurance-documents")
                      .key("claims/0001/random-key.pdf")
                      .build())
              .asByteArray();

      assertThat(readBack).isEqualTo(content);
    }
  }

  private static S3Client client() {
    URI endpoint =
        URI.create("http://" + SEAWEEDFS.getHost() + ":" + SEAWEEDFS.getMappedPort(S3_PORT));
    return S3Client.builder()
        .endpointOverride(endpoint)
        .region(Region.US_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .build();
  }
}
