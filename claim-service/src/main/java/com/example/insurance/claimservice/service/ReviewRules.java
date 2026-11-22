package com.example.insurance.claimservice.service;

import com.example.insurance.claimservice.domain.Claim;
import com.example.insurance.claimservice.exception.ApiException;
import com.example.insurance.claimservice.exception.ErrorCode;

/** Rules that apply to every staff action on a claim (PLAN 4). */
final class ReviewRules {

  private ReviewRules() {
    // Static rules only; not instantiable.
  }

  /**
   * A staff member can never act on a claim they filed themselves (PLAN 4). This matters when a
   * person holds a policy and also works for the insurer.
   */
  static void requireNotOwnClaim(Actor staff, Claim claim) {
    if (claim.getHolderUserId().equals(staff.userId())) {
      throw new ApiException(
          ErrorCode.SELF_REVIEW_NOT_ALLOWED, "You cannot review a claim that you filed.");
    }
  }
}
