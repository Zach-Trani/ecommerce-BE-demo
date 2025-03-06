package com.printed_parts.spring_boot.modules.transactions.repository;

import com.printed_parts.spring_boot.modules.transactions.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByStripeSessionId(String sessionId);
    Optional<Transaction> findByStripePaymentIntentId(String paymentIntentId);
}
