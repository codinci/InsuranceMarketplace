package com.example.insurance.claimservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** A presigned link to one document. It works until {@code expiresAt}, then it stops. */
@Schema(description = "A time-limited link to download one document.")
public record DownloadUrlView(String url, Instant expiresAt) {}
