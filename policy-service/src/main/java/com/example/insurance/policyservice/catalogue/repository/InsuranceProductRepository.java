package com.example.insurance.policyservice.catalogue.repository;

import com.example.insurance.policyservice.catalogue.domain.InsuranceProduct;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsuranceProductRepository extends JpaRepository<InsuranceProduct, UUID> {

  Optional<InsuranceProduct> findByInsurerIdAndCode(UUID insurerId, String code);
}
