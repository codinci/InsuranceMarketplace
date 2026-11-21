package com.example.insurance.policyservice.policy.repository;

import com.example.insurance.policyservice.policy.domain.Policy;
import com.example.insurance.policyservice.policy.domain.PolicyStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

  Optional<Policy> findByIdAndHolderUserId(UUID id, UUID holderUserId);

  Page<Policy> findByHolderUserId(UUID holderUserId, Pageable pageable);

  /** The next value for policy and member numbers (PLAN 3). */
  @Query(value = "select nextval('policy_number_seq')", nativeQuery = true)
  long nextNumber();

  /** Ids of policies in {@code status} created before {@code cutoff}, oldest first. */
  @Query(
      "select p.id from Policy p where p.status = :status and p.createdAt < :cutoff"
          + " order by p.createdAt")
  List<UUID> findIdsByStatusCreatedBefore(
      @Param("status") PolicyStatus status, @Param("cutoff") Instant cutoff, Pageable pageable);
}
