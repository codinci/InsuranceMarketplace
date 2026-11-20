package com.example.insurance.policyservice.policy.repository;

import com.example.insurance.policyservice.policy.domain.Quote;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

  Optional<Quote> findByIdAndHolderUserId(UUID id, UUID holderUserId);
}
