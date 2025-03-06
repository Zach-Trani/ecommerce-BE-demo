package com.printed_parts.spring_boot.modules.transactions.repository;

import com.printed_parts.spring_boot.modules.transactions.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Transaction findByStripeSessionId(String sessionId);
    Transaction findByStripePaymentIntentId(String paymentIntentId);
}
