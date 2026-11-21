package com.example.insurance.claimservice.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * The S3 clients for SeaweedFS: path-style addressing and the checksum settings the M0b smoke test
 * proved against SeaweedFS (infra-tests).
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

  @Bean(destroyMethod = "close")
  S3Client s3Client(StorageProperties properties) {
    return S3Client.builder()
        .endpointOverride(properties.endpoint())
        .region(Region.of(properties.region()))
        .credentialsProvider(credentials(properties))
        .serviceConfiguration(pathStyle())
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .build();
  }

  @Bean(destroyMethod = "close")
  S3Presigner s3Presigner(StorageProperties properties) {
    URI endpoint =
        properties.publicEndpoint() == null ? properties.endpoint() : properties.publicEndpoint();
    return S3Presigner.builder()
        .endpointOverride(endpoint)
        .region(Region.of(properties.region()))
        .credentialsProvider(credentials(properties))
        .serviceConfiguration(pathStyle())
        .build();
  }

  private static StaticCredentialsProvider credentials(StorageProperties properties) {
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
  }

  private static S3Configuration pathStyle() {
    return S3Configuration.builder().pathStyleAccessEnabled(true).build();
  }
}
