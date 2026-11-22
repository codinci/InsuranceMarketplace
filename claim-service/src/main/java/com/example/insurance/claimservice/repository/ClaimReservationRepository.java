package com.example.insurance.claimservice.repository;

import com.example.insurance.claimservice.domain.ClaimReservation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimReservationRepository extends JpaRepository<ClaimReservation, Long> {

  /** The reservations of one claim that have not been released (at most one per limited node). */
  List<ClaimReservation> findByClaimIdAndReleasedAtIsNull(UUID claimId);

  List<ClaimReservation> findByClaimId(UUID claimId);
}
