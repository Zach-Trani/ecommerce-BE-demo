package com.printed_parts.spring_boot.modules.customer.repository;

import com.printed_parts.spring_boot.modules.customer.entity.CustomerInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerInformation, Long> {
    
    // Find a customer by email
    Optional<CustomerInformation> findByEmail(String email);
    
    // Check if a customer with this email exists
    boolean existsByEmail(String email);
}
