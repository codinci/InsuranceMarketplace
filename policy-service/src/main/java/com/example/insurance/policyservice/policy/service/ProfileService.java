package com.example.insurance.policyservice.policy.service;

import com.example.insurance.policyservice.exception.ApiException;
import com.example.insurance.policyservice.exception.ErrorCode;
import com.example.insurance.policyservice.policy.domain.PolicyHolder;
import com.example.insurance.policyservice.policy.dto.ProfileRequest;
import com.example.insurance.policyservice.policy.dto.ProfileResponse;
import com.example.insurance.policyservice.policy.repository.PolicyHolderRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The caller's own profile. The user id always comes from the token, never the request. */
@Service
public class ProfileService {

  private final PolicyHolderRepository holders;
  private final Clock clock;

  public ProfileService(PolicyHolderRepository holders, Clock clock) {
    this.holders = holders;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ProfileResponse get(UUID userId) {
    return toResponse(
        holders
            .findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.PROFILE_NOT_FOUND, "No profile yet.")));
  }

  @Transactional
  public ProfileResponse save(UUID userId, ProfileRequest request) {
    PolicyHolder holder =
        holders.findById(userId).orElseGet(() -> PolicyHolder.create(userId, clock.instant()));
    holder.update(
        request.firstName(),
        request.lastName(),
        request.dateOfBirth(),
        request.sex(),
        request.nationalId(),
        request.phone(),
        clock.instant());
    return toResponse(holders.save(holder));
  }

  static ProfileResponse toResponse(PolicyHolder holder) {
    return new ProfileResponse(
        holder.getFirstName(),
        holder.getLastName(),
        holder.getDateOfBirth(),
        holder.getSex(),
        holder.maskedNationalId(),
        holder.getPhone());
  }
}
