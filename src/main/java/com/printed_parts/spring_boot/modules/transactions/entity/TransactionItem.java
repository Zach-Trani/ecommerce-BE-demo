package com.printed_parts.spring_boot.modules.transactions.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "TRANSACTION_ITEM")
public class TransactionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String productName;
    private String productId;
    private Long quantity;
    private BigDecimal price; // Price per unit
    private BigDecimal totalPrice; // quantity * price
    
    @ManyToOne
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;
    
    // No-args constructor
    public TransactionItem() {
    }
    
    // All-args constructor
    public TransactionItem(Long id, String productName, String productId, 
                          Long quantity, BigDecimal price, BigDecimal totalPrice,
                          Transaction transaction) {
        this.id = id;
        this.productName = productName;
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
        this.totalPrice = totalPrice;
        this.transaction = transaction;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getProductName() {
        return productName;
    }
    
    public void setProductName(String productName) {
        this.productName = productName;
    }
    
    public String getProductId() {
        return productId;
    }
    
    public void setProductId(String productId) {
        this.productId = productId;
    }
    
    public Long getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public BigDecimal getTotalPrice() {
        return totalPrice;
    }
    
    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }
    
    public Transaction getTransaction() {
        return transaction;
    }
    
    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }
    
    // equals method
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionItem that = (TransactionItem) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(productName, that.productName) &&
               Objects.equals(productId, that.productId) &&
               Objects.equals(quantity, that.quantity) &&
               Objects.equals(price, that.price) &&
               Objects.equals(totalPrice, that.totalPrice);
    }
    
    // hashCode method
    @Override
    public int hashCode() {
        return Objects.hash(id, productName, productId, quantity, price, totalPrice);
    }
    
    // toString method
    @Override
    public String toString() {
        return "TransactionItem{" +
               "id=" + id +
               ", productName='" + productName + '\'' +
               ", productId='" + productId + '\'' +
               ", quantity=" + quantity +
               ", price=" + price +
               ", totalPrice=" + totalPrice +
               '}';
    }
}
