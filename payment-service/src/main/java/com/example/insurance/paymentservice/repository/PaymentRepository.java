package com.example.insurance.paymentservice.repository;

import com.example.insurance.paymentservice.domain.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Optional<Payment> findByIntentKey(String intentKey);

  /** The caller's own payment only: another customer's id finds nothing (IDOR rule, 404). */
  Optional<Payment> findByIdAndHolderUserId(UUID id, UUID holderUserId);

  List<Payment> findByHolderUserIdAndReferenceId(UUID holderUserId, UUID referenceId);

  Optional<Payment> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);
}
