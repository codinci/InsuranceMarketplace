package com.example.insurance.policyservice.catalogue.service;

import com.example.insurance.policyservice.catalogue.domain.Insurer;
import com.example.insurance.policyservice.catalogue.dto.CreateInsurerRequest;
import com.example.insurance.policyservice.catalogue.dto.InsurerResponse;
import com.example.insurance.policyservice.catalogue.repository.InsurerRepository;
import com.example.insurance.policyservice.exception.ApiException;
import com.example.insurance.policyservice.exception.ErrorCode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating insurers and finding the ones a staff member may act on (PLAN 4). */
@Service
public class InsurerService {

  private final InsurerRepository insurers;
  private final Clock clock;

  public InsurerService(InsurerRepository insurers, Clock clock) {
    this.insurers = insurers;
    this.clock = clock;
  }

  /** Only a platform admin creates insurers (PLAN 4). */
  @Transactional
  public InsurerResponse create(StaffScope scope, CreateInsurerRequest request) {
    if (!scope.isPlatform()) {
      throw new ApiException(
          ErrorCode.PLATFORM_ADMIN_REQUIRED, "Only a platform admin can create insurers.");
    }
    if (insurers.existsByCode(request.code())) {
      throw new ApiException(
          ErrorCode.INSURER_CODE_TAKEN, "An insurer with this code already exists.");
    }
    Insurer insurer =
        insurers.save(Insurer.create(request.code(), request.name(), clock.instant()));
    return toResponse(insurer);
  }

  /** The insurer, if the staff member may act on it; otherwise 404, so it is never revealed. */
  @Transactional(readOnly = true)
  public Insurer visible(StaffScope scope, UUID insurerId) {
    return insurers
        .findById(insurerId)
        .filter(insurer -> scope.canSee(insurer.getId()))
        .orElseThrow(() -> new ApiException(ErrorCode.INSURER_NOT_FOUND, "No such insurer."));
  }

  static InsurerResponse toResponse(Insurer insurer) {
    return new InsurerResponse(insurer.getId(), insurer.getCode(), insurer.getName());
  }
}
