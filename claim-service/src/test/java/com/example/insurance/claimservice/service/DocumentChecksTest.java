package com.example.insurance.claimservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Upload rules (CLAUDE.md uploads): the size limit, the allowlist, and the magic bytes. The
 * declared type is never enough on its own.
 */
class DocumentChecksTest {

  private static final byte[] PDF = bytes("%PDF-1.7 minimal body");
  private static final byte[] PNG =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
  private static final byte[] JPEG =
      new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};

  @Test
  void theThreeAllowedTypesWithTheirOwnBytesPass() {
    assertThatCode(() -> DocumentChecks.check("application/pdf", PDF)).doesNotThrowAnyException();
    assertThatCode(() -> DocumentChecks.check("image/png", PNG)).doesNotThrowAnyException();
    assertThatCode(() -> DocumentChecks.check("image/jpeg", JPEG)).doesNotThrowAnyException();
  }

  @Test
  void aFileOfExactlyTheLimitPasses() {
    byte[] atLimit = new byte[(int) DocumentChecks.MAX_BYTES];
    System.arraycopy(PDF, 0, atLimit, 0, PDF.length);

    assertThatCode(() -> DocumentChecks.check("application/pdf", atLimit))
        .doesNotThrowAnyException();
  }

  @Test
  void aFileOverTheLimitIsTooLarge() {
    byte[] over = new byte[(int) DocumentChecks.MAX_BYTES + 1];
    System.arraycopy(PDF, 0, over, 0, PDF.length);

    assertCode(() -> DocumentChecks.check("application/pdf", over), ErrorCode.DOCUMENT_TOO_LARGE);
  }

  @Test
  void anEmptyFileIsRejectedAsTooLarge() {
    assertCode(
        () -> DocumentChecks.check("application/pdf", new byte[0]), ErrorCode.DOCUMENT_TOO_LARGE);
  }

  @Test
  void aTypeOutsideTheAllowlistIsRefused() {
    assertCode(
        () -> DocumentChecks.check("text/plain", bytes("hello")),
        ErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
    assertCode(() -> DocumentChecks.check(null, PDF), ErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
  }

  @Test
  void aDeclaredTypeWhoseBytesAreSomethingElseIsAMismatch() {
    assertCode(
        () -> DocumentChecks.check("application/pdf", PNG), ErrorCode.DOCUMENT_CONTENT_MISMATCH);
    assertCode(() -> DocumentChecks.check("image/png", PDF), ErrorCode.DOCUMENT_CONTENT_MISMATCH);
  }

  @Test
  void aFileShorterThanItsSignatureIsAMismatch() {
    assertCode(
        () -> DocumentChecks.check("image/png", new byte[] {(byte) 0x89, 0x50}),
        ErrorCode.DOCUMENT_CONTENT_MISMATCH);
  }

  @Test
  void theFileNameKeepsSafeCharactersOnly() {
    assertThat(DocumentService.safeName("Invoice 2026 (final).pdf"))
        .isEqualTo("Invoice 2026 _final_.pdf");
    assertThat(DocumentService.safeName("../../etc/passwd")).isEqualTo(".._.._etc_passwd");
    assertThat(DocumentService.safeName(null)).isEqualTo("document");
    assertThat(DocumentService.safeName("  ")).isEqualTo("document");
    assertThat(DocumentService.safeName("a".repeat(300))).hasSize(200);
  }

  private static void assertCode(Runnable call, ErrorCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(expected));
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.US_ASCII);
  }
}
