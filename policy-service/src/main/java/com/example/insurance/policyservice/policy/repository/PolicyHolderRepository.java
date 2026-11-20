package com.example.insurance.policyservice.policy.repository;

import com.example.insurance.policyservice.policy.domain.PolicyHolder;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyHolderRepository extends JpaRepository<PolicyHolder, UUID> {}
