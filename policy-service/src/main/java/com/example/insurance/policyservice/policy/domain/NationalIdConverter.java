package com.example.insurance.policyservice.policy.domain;

import com.example.insurance.policyservice.crypto.FieldEncryptor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Encrypts the national ID on its way into the database and decrypts it on the way out (D37).
 * Hibernate gets this converter from Spring, so the encryptor is injected.
 */
@Converter
public class NationalIdConverter implements AttributeConverter<String, String> {

  static final String FIELD = "policy_holders.national_id";

  private final FieldEncryptor encryptor;

  public NationalIdConverter(FieldEncryptor encryptor) {
    this.encryptor = encryptor;
  }

  @Override
  public String convertToDatabaseColumn(String nationalId) {
    return nationalId == null ? null : encryptor.encrypt(FIELD, nationalId);
  }

  @Override
  public String convertToEntityAttribute(String stored) {
    return stored == null ? null : encryptor.decrypt(FIELD, stored);
  }
}
