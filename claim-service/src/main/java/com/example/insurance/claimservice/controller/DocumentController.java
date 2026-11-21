package com.example.insurance.claimservice.controller;

import com.example.insurance.claimservice.dto.DocumentView;
import com.example.insurance.claimservice.dto.DownloadUrlView;
import com.example.insurance.claimservice.service.DocumentService;
import com.example.insurance.claimservice.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Documents of a claim (PLAN 12, M5b). Uploads are for drafts; downloads are presigned links. */
@RestController
@RequestMapping("/api/v1/claims/{claimId}/documents")
@Validated
@Tag(name = "Claims", description = "Documents of a claim (M5b).")
public class DocumentController {

  private final DocumentService documents;

  public DocumentController(DocumentService documents) {
    this.documents = documents;
  }

  @GetMapping
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "List the documents of my claim")
  @ApiResponse(responseCode = "200", description = "One page of documents")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "404", description = "No such claim of yours (CLAIM_NOT_FOUND)")
  public PageResponse<DocumentView> list(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID claimId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.of(
        documents.list(
            Customers.of(jwt), claimId, PageRequest.of(page, size, Sort.by("uploadedAt", "id"))),
        document -> document);
  }

  @PostMapping(consumes = "multipart/form-data")
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Upload a document",
      description =
          "Adds a PDF, JPEG or PNG of up to 10 MB to a draft claim. The file's first bytes must match"
              + " its declared type. At most 10 documents per claim.")
  @ApiResponse(responseCode = "201", description = "The stored document")
  @ApiResponse(responseCode = "400", description = "Missing or invalid fields (VALIDATION_FAILED)")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(responseCode = "404", description = "No such claim of yours (CLAIM_NOT_FOUND)")
  @ApiResponse(
      responseCode = "409",
      description =
          "Not a draft, or too many documents (CLAIM_NOT_EDITABLE, DOCUMENT_LIMIT_REACHED)")
  @ApiResponse(responseCode = "413", description = "Larger than 10 MB (DOCUMENT_TOO_LARGE)")
  @ApiResponse(
      responseCode = "415",
      description = "Not a PDF, JPEG or PNG (DOCUMENT_TYPE_NOT_ALLOWED)")
  @ApiResponse(
      responseCode = "422",
      description = "The content does not match the type (DOCUMENT_CONTENT_MISMATCH)")
  public DocumentView upload(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID claimId,
      @RequestParam("docType")
          @Schema(description = "What the document proves, upper case, for example INVOICE.")
          String docType,
      @RequestParam("file") MultipartFile file) {
    try {
      return documents.upload(
          Customers.of(jwt),
          claimId,
          docType,
          file.getOriginalFilename(),
          file.getContentType(),
          file.getBytes());
    } catch (IOException e) {
      throw new IllegalStateException("The uploaded file could not be read.", e);
    }
  }

  @GetMapping("/{documentId}/download-url")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "A link to download a document",
      description =
          "A presigned URL that expires after five minutes. Only the claim's owner gets one.")
  @ApiResponse(responseCode = "200", description = "The link and when it expires")
  @ApiResponse(responseCode = "401", description = "No valid access token (UNAUTHENTICATED)")
  @ApiResponse(
      responseCode = "404",
      description = "No such claim or document of yours (CLAIM_NOT_FOUND, DOCUMENT_NOT_FOUND)")
  public DownloadUrlView downloadUrl(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId, @PathVariable UUID documentId) {
    return documents.downloadUrl(Customers.of(jwt), claimId, documentId);
  }
}
