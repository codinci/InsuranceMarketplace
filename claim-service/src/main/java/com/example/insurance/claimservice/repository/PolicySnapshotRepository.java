package com.example.insurance.claimservice.repository;

import com.example.insurance.claimservice.domain.PolicySnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Policy read model queries. Every customer query is filtered by the holder (CLAUDE.md). */
public interface PolicySnapshotRepository extends JpaRepository<PolicySnapshot, UUID> {

  Page<PolicySnapshot> findByHolderUserId(UUID holderUserId, Pageable pageable);

  Optional<PolicySnapshot> findByPolicyIdAndHolderUserId(UUID policyId, UUID holderUserId);
}
