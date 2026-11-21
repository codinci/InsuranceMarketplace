package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Upload rules (CLAUDE.md uploads): an allowlisted type, the type confirmed by the file's first
 * bytes (magic bytes), and the size limit. The declared content type is what the client says; the
 * bytes decide.
 */
public final class DocumentChecks {

  /** 10 MB (CLAUDE.md, PLAN 12). */
  public static final long MAX_BYTES = 10L * 1024 * 1024;

  private static final Map<String, List<byte[]>> SIGNATURES =
      Map.of(
          "application/pdf", List.of(bytes("%PDF-")),
          "image/jpeg", List.of(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
          "image/png", List.of(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));

  private DocumentChecks() {
    // Static rules only; not instantiable.
  }

  /**
   * Checks size, the declared type and the magic bytes. Throws the matching {@link ApiException}.
   */
  public static void check(String declaredContentType, byte[] content) {
    if (content.length == 0 || content.length > MAX_BYTES) {
      throw new ApiException(
          ErrorCode.DOCUMENT_TOO_LARGE, "Documents must be between 1 byte and 10 MB.");
    }
    if (declaredContentType == null || !SIGNATURES.containsKey(declaredContentType)) {
      throw new ApiException(
          ErrorCode.DOCUMENT_TYPE_NOT_ALLOWED, "Upload a PDF, a JPEG or a PNG file.");
    }
    if (!matches(declaredContentType, content)) {
      throw new ApiException(
          ErrorCode.DOCUMENT_CONTENT_MISMATCH,
          "The file content does not match its declared type.");
    }
  }

  static boolean matches(String type, byte[] content) {
    return SIGNATURES.get(type).stream().anyMatch(signature -> startsWith(content, signature));
  }

  private static boolean startsWith(byte[] content, byte[] signature) {
    return content.length >= signature.length
        && Arrays.equals(Arrays.copyOf(content, signature.length), signature);
  }

  private static byte[] bytes(String text) {
    return text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  }
}
