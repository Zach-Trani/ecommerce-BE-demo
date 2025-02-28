package com.printed_parts.spring_boot.modules.stripe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// ProductItem class is a filtered Product class and contains only the relevant information for Stripe
@Data
public class ProductItem {
    private Long amount;  // Price in cents
    private Long quantity;
    private String name;

    // No-args constructor
    public ProductItem() {
    }

    // All-args constructor
    public ProductItem(Long amount, Long quantity, String name) {
        this.amount = amount;
        this.quantity = quantity;
        this.name = name;
    }

    // Getter and Setter for amount
    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    // Getter and Setter for quantity
    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    // Getter and Setter for name
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
} 