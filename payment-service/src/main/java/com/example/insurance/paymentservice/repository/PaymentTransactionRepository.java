package com.example.insurance.paymentservice.repository;

import com.example.insurance.paymentservice.domain.PaymentTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

  Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

  long countByPaymentId(UUID paymentId);
}
