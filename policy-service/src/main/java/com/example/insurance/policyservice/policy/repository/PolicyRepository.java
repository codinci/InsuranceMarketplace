package com.example.insurance.policyservice.policy.repository;

import com.example.insurance.policyservice.policy.domain.Policy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

  Optional<Policy> findByIdAndHolderUserId(UUID id, UUID holderUserId);

  Page<Policy> findByHolderUserId(UUID holderUserId, Pageable pageable);

  /** The next value for policy and member numbers (PLAN 3). */
  @Query(value = "select nextval('policy_number_seq')", nativeQuery = true)
  long nextNumber();
}
