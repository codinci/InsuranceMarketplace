package com.example.insurance.policyservice.crypto;

import com.example.insurance.policyservice.config.FieldEncryptionProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Encrypts personal data at rest with AES-256-GCM (D37, ADR-012). Each value gets a fresh random
 * 12-byte nonce, so equal values never look equal in the database. GCM also authenticates: a
 * changed ciphertext fails to decrypt instead of returning garbage. The field name is bound as
 * associated data, so a value cannot be moved to another column.
 *
 * <p>Stored form: {@code v1:<keyId>:<base64(nonce || ciphertext with tag)>}. The key id allows a
 * later rotation: old values name the key that can read them.
 */
@Component
@EnableConfigurationProperties(FieldEncryptionProperties.class)
public final class FieldEncryptor {

  private static final String FORMAT = "v1";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int KEY_BYTES = 32;
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKey key;
  private final String keyId;
  private final SecureRandom random = new SecureRandom();

  public FieldEncryptor(FieldEncryptionProperties properties) {
    if (properties.keyId() == null || !properties.keyId().matches("[A-Za-z0-9_-]{1,40}")) {
      throw new IllegalStateException(
          "policy.field-encryption.key-id is missing or invalid (see docs/runbook.md).");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(nullToEmpty(properties.keyBase64()));
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("policy.field-encryption.key-base64 is not base64.", e);
    }
    if (bytes.length != KEY_BYTES) {
      throw new IllegalStateException(
          "policy.field-encryption.key-base64 must hold 32 bytes (see docs/runbook.md).");
    }
    this.key = new SecretKeySpec(bytes, "AES");
    this.keyId = properties.keyId();
  }

  /** Encrypts {@code value} for the column named {@code field}. */
  public String encrypt(String field, String value) {
    byte[] nonce = new byte[NONCE_BYTES];
    random.nextBytes(nonce);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(field.getBytes(StandardCharsets.UTF_8));
      byte[] sealed = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      byte[] stored =
          ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array();
      return FORMAT + ":" + keyId + ":" + Base64.getEncoder().encodeToString(stored);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed.", e);
    }
  }

  /** Decrypts a value written by {@link #encrypt}; fails if it was changed or moved. */
  public String decrypt(String field, String stored) {
    String[] parts = stored.split(":", 3);
    if (parts.length != 3 || !FORMAT.equals(parts[0])) {
      throw new IllegalStateException("Unknown encrypted value format.");
    }
    if (!keyId.equals(parts[1])) {
      throw new IllegalStateException("No key with id " + parts[1] + " is configured.");
    }
    byte[] bytes = Base64.getDecoder().decode(parts[2]);
    if (bytes.length <= NONCE_BYTES) {
      throw new IllegalStateException("Encrypted value is too short.");
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES));
      cipher.updateAAD(field.getBytes(StandardCharsets.UTF_8));
      byte[] plain = cipher.doFinal(bytes, NONCE_BYTES, bytes.length - NONCE_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Decryption failed: the value was changed or moved.", e);
    }
  }

  /** The key id named in a stored value, for rotation reports. */
  public static String keyIdOf(String stored) {
    String[] parts = stored.split(":", 3);
    return parts.length == 3 ? parts[1] : null;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
