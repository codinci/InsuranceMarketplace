package com.example.insurance.policyservice.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.policyservice.config.FieldEncryptionProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FieldEncryptorTest {

  private static final String KEY =
      Base64.getEncoder().encodeToString("test-only-key-not-a-secret-32byt".getBytes());
  private static final String OTHER_KEY =
      Base64.getEncoder().encodeToString("another-test-key-not-a-secret-32".getBytes());

  private final FieldEncryptor encryptor =
      new FieldEncryptor(new FieldEncryptionProperties(KEY, "k1"));

  @Test
  void aValueRoundTrips() {
    String stored = encryptor.encrypt("t.c", "A1234567");
    assertThat(stored).startsWith("v1:k1:").doesNotContain("A1234567");
    assertThat(encryptor.decrypt("t.c", stored)).isEqualTo("A1234567");
    assertThat(FieldEncryptor.keyIdOf(stored)).isEqualTo("k1");
  }

  @Test
  void theSameValueEncryptsDifferentlyEachTime() {
    assertThat(encryptor.encrypt("t.c", "A1234567"))
        .isNotEqualTo(encryptor.encrypt("t.c", "A1234567"));
  }

  @Test
  void aChangedCiphertextIsRefused() {
    String stored = encryptor.encrypt("t.c", "A1234567");
    char last = stored.charAt(stored.length() - 2);
    String tampered =
        stored.substring(0, stored.length() - 2)
            + (last == 'A' ? 'B' : 'A')
            + stored.charAt(stored.length() - 1);
    assertThatThrownBy(() -> encryptor.decrypt("t.c", tampered))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aValueMovedToAnotherColumnIsRefused() {
    String stored = encryptor.encrypt("t.national_id", "A1234567");
    assertThatThrownBy(() -> encryptor.decrypt("t.phone", stored))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anotherKeyCannotRead() {
    String stored = encryptor.encrypt("t.c", "A1234567");
    FieldEncryptor other = new FieldEncryptor(new FieldEncryptionProperties(OTHER_KEY, "k1"));
    assertThatThrownBy(() -> other.decrypt("t.c", stored))
        .isInstanceOf(IllegalStateException.class);
    FieldEncryptor otherId = new FieldEncryptor(new FieldEncryptionProperties(KEY, "k2"));
    assertThatThrownBy(() -> otherId.decrypt("t.c", stored))
        .hasMessageContaining("No key with id k1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"plain", "v2:k1:AAAA", "v1:k1:AAAA"})
  void malformedValuesAreRefused(String stored) {
    assertThatThrownBy(() -> encryptor.decrypt("t.c", stored))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aMissingOrShortKeyStopsStartup() {
    assertThatThrownBy(() -> new FieldEncryptor(new FieldEncryptionProperties("", "k1")))
        .hasMessageContaining("32 bytes");
    assertThatThrownBy(() -> new FieldEncryptor(new FieldEncryptionProperties("not base64!", "k1")))
        .hasMessageContaining("not base64");
    assertThatThrownBy(() -> new FieldEncryptor(new FieldEncryptionProperties(KEY, null)))
        .hasMessageContaining("key-id");
    assertThatThrownBy(() -> new FieldEncryptor(new FieldEncryptionProperties(KEY, "bad id")))
        .hasMessageContaining("key-id");
  }

  @Test
  void theKeyIsNeverPrinted() {
    assertThat(new FieldEncryptionProperties(KEY, "k1").toString()).doesNotContain(KEY);
  }
}
