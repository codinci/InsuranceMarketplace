package com.example.insurance.policyservice.catalogue.repository;

import com.example.insurance.policyservice.catalogue.domain.Insurer;
import com.example.insurance.policyservice.catalogue.domain.InsurerStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsurerRepository extends JpaRepository<Insurer, UUID> {

  Optional<Insurer> findByCode(String code);

  boolean existsByCode(String code);

  Page<Insurer> findByStatus(InsurerStatus status, Pageable pageable);
}
