package com.printed_parts.spring_boot.modules.stripe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
public class CartRequest {
    private List<ProductItem> items;
    private String currency;  // Currency applies to all items in the cart

    // No-args constructor
    public CartRequest() {
    }

    // All-args constructor
    public CartRequest(List<ProductItem> items, String currency) {
        this.items = items;
        this.currency = currency;
    }

    // Getter and Setter for items
    public List<ProductItem> getItems() {
        return items;
    }

    public void setItems(List<ProductItem> items) {
        this.items = items;
    }

    // Getter and Setter for currency
    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
} 