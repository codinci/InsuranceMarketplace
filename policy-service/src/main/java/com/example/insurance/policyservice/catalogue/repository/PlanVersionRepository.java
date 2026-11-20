package com.example.insurance.policyservice.catalogue.repository;

import com.example.insurance.policyservice.catalogue.domain.PlanStatus;
import com.example.insurance.policyservice.catalogue.domain.PlanVersion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanVersionRepository
    extends JpaRepository<PlanVersion, UUID>, JpaSpecificationExecutor<PlanVersion> {

  Optional<PlanVersion> findByProductIdAndCodeAndPlanVersion(
      UUID productId, String code, int planVersion);

  List<PlanVersion> findByProductIdAndCodeAndStatus(UUID productId, String code, PlanStatus status);

  boolean existsByProductIdAndCodeAndStatusAndPlanVersionGreaterThan(
      UUID productId, String code, PlanStatus status, int planVersion);

  /** Published plans of one insurer that are on sale on {@code day} (PLAN 13, browse). */
  @Query(
      """
      select p from PlanVersion p
      where p.insurerId = :insurerId
        and p.status = com.example.insurance.policyservice.catalogue.domain.PlanStatus.PUBLISHED
        and p.saleFrom <= :day
        and (p.saleTo is null or p.saleTo >= :day)
      """)
  Page<PlanVersion> findOnSale(
      @Param("insurerId") UUID insurerId, @Param("day") LocalDate day, Pageable pageable);
}
