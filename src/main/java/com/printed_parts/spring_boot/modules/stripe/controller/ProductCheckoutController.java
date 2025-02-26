package com.printed_parts.spring_boot.modules.stripe.controller;

import com.printed_parts.spring_boot.modules.stripe.dto.CartRequest;
import com.printed_parts.spring_boot.modules.stripe.dto.ProductRequest;
import com.printed_parts.spring_boot.modules.stripe.dto.StripeResponse;
import com.printed_parts.spring_boot.modules.stripe.service.StripeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/product/v1")
public class ProductCheckoutController {

    private StripeService stripeService;

    public ProductCheckoutController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    /**
     * Legacy endpoint for single product checkout
     * Maintains backward compatibility
     */
    @PostMapping("/checkout")
    public ResponseEntity<StripeResponse> checkoutProducts(@RequestBody ProductRequest productRequest) {
        StripeResponse response = stripeService.checkoutProducts(productRequest);
        return ResponseEntity.ok(response);
    }
    
    /**
     * New endpoint for cart checkout
     * Handles multiple products
     */
    @PostMapping("/cart/checkout")
    public ResponseEntity<StripeResponse> checkoutCart(@RequestBody CartRequest cartRequest) {
        StripeResponse response = stripeService.checkout(cartRequest);
        return ResponseEntity.ok(response);
    }
}