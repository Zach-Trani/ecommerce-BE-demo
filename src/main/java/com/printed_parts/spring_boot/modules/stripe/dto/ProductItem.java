package com.printed_parts.spring_boot.modules.stripe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for product items in a cart.
 * This class is used to represent items sent from the frontend to the backend for processing.
 * 
 * amount: Price in cents (e.g., $9.99 = 999 cents)
 * quantity: Number of units
 * name: Product name/title (used for display on Stripe checkout page)
 * productId: Internal product ID from our database (from Product.java entity)
 */
@Data
public class ProductItem {
    private Long amount;  // Price in cents
    private Long quantity;
    private String name;
    private Integer productId; // Internal product ID from our database

    // No-args constructor
    public ProductItem() {
    }

    // All-args constructor
    public ProductItem(Long amount, Long quantity, String name, Integer productId) {
        this.amount = amount;
        this.quantity = quantity;
        this.name = name;
        this.productId = productId;
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

    // Getter and Setter for productId
    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }
} 
