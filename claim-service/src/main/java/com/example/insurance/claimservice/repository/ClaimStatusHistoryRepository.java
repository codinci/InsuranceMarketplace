package com.example.insurance.claimservice.repository;

import com.example.insurance.claimservice.domain.ClaimStatusHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimStatusHistoryRepository extends JpaRepository<ClaimStatusHistory, Long> {

  Page<ClaimStatusHistory> findByClaimId(UUID claimId, Pageable pageable);
}
