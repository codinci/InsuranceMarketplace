package com.example.insurance.policyservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The key that encrypts personal data at rest (D37, ADR-012). Never logged. */
@ConfigurationProperties(prefix = "policy.field-encryption")
public record FieldEncryptionProperties(String keyBase64, String keyId) {

  @Override
  public String toString() {
    return "FieldEncryptionProperties[keyId=" + keyId + "]";
  }
}
