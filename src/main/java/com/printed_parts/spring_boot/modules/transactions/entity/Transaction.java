package com.printed_parts.spring_boot.modules.transactions.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "SALES_TRANSACTION")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String stripeSessionId;
    private String stripePaymentIntentId;
    private String customerEmail;
    private BigDecimal totalAmount;
    private String currency;
    private String paymentStatus;
    private LocalDateTime transactionDate;

    // Relationship to TransactionItem entity to store individual line items
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    private List<TransactionItem> items;

    // No-args constructor
    public Transaction() {
    }

    // All-args constructor
    public Transaction(Long id, String stripeSessionId, String stripePaymentIntentId, 
                      String customerEmail, BigDecimal totalAmount, String currency, 
                      String paymentStatus, LocalDateTime transactionDate, 
                      List<TransactionItem> items) {
        this.id = id;
        this.stripeSessionId = stripeSessionId;
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.customerEmail = customerEmail;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.paymentStatus = paymentStatus;
        this.transactionDate = transactionDate;
        this.items = items;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    public void setStripePaymentIntentId(String stripePaymentIntentId) {
        this.stripePaymentIntentId = stripePaymentIntentId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    public List<TransactionItem> getItems() {
        return items;
    }

    public void setItems(List<TransactionItem> items) {
        this.items = items;
    }

    // equals method
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(stripeSessionId, that.stripeSessionId) &&
               Objects.equals(stripePaymentIntentId, that.stripePaymentIntentId) &&
               Objects.equals(customerEmail, that.customerEmail) &&
               Objects.equals(totalAmount, that.totalAmount) &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(paymentStatus, that.paymentStatus) &&
               Objects.equals(transactionDate, that.transactionDate);
    }

    // hashCode method
    @Override
    public int hashCode() {
        return Objects.hash(id, stripeSessionId, stripePaymentIntentId, customerEmail, 
                           totalAmount, currency, paymentStatus, transactionDate);
    }

    // toString method
    @Override
    public String toString() {
        return "Transaction{" +
               "id=" + id +
               ", stripeSessionId='" + stripeSessionId + '\'' +
               ", stripePaymentIntentId='" + stripePaymentIntentId + '\'' +
               ", customerEmail='" + customerEmail + '\'' +
               ", totalAmount=" + totalAmount +
               ", currency='" + currency + '\'' +
               ", paymentStatus='" + paymentStatus + '\'' +
               ", transactionDate=" + transactionDate +
               ", items.size=" + (items != null ? items.size() : 0) +
               '}';
    }
}
