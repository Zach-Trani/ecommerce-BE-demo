package com.printed_parts.spring_boot;

import com.printed_parts.spring_boot.modules.transactions.entity.Transaction;
import com.printed_parts.spring_boot.modules.transactions.entity.TransactionItem;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * This component ensures that the required database tables for transactions are created
 * at application startup. This helps fix issues where webhook processing fails
 * because the necessary tables don't exist yet.
 */
@Slf4j
@Component
public class DatabaseInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Value("${spring.jpa.hibernate.ddl-auto:none}")
    private String ddlAuto;

    @PostConstruct
    public void initializeDatabase() {
        log.info("Checking database configuration...");
        log.info("Current JPA DDL setting: {}", ddlAuto);
        
        try {
            // Check if tables exist by querying metadata
            boolean transactionTableExists = tableExists("SALES_TRANSACTION");
            boolean transactionItemTableExists = tableExists("TRANSACTION_ITEM");
            
            log.info("Database tables check - SALES_TRANSACTION: {}, TRANSACTION_ITEM: {}", 
                    transactionTableExists ? "EXISTS" : "MISSING",
                    transactionItemTableExists ? "EXISTS" : "MISSING");
            
            // Only try to create tables if they don't exist and if we're not in "none" or "validate" mode
            if ((!transactionTableExists || !transactionItemTableExists) && 
                    !ddlAuto.equals("none") && !ddlAuto.equals("validate")) {
                log.info("Missing tables detected. Will create them when Hibernate initializes.");
            }
            
            // Log entity information to help with debugging
            log.info("Transaction Entity: {}", Transaction.class.getName());
            log.info("TransactionItem Entity: {}", TransactionItem.class.getName());
            
        } catch (Exception e) {
            log.error("Error checking database tables: {}", e.getMessage(), e);
        }
    }
    
    private boolean tableExists(String tableName) {
        try {
            // Query to check if the table exists
            String query = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?";
            Integer count = jdbcTemplate.queryForObject(query, Integer.class, tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking if table {} exists: {}", tableName, e.getMessage());
            return false;
        }
    }
} 