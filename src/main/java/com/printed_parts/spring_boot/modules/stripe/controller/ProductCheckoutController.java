package com.printed_parts.spring_boot.modules.stripe.controller;

import com.printed_parts.spring_boot.modules.stripe.dto.ProductRequest;
import com.printed_parts.spring_boot.modules.stripe.dto.StripeResponse;
import com.printed_parts.spring_boot.modules.stripe.service.StripeService;
// import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/product/v1")
// @CrossOrigin(
// origins = "http://localhost:5173",
// allowedHeaders = "*",
// methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS},
// allowCredentials = "true"
// )
public class ProductCheckoutController {

    private StripeService stripeService;

    public ProductCheckoutController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<StripeResponse> checkoutProducts(@RequestBody ProductRequest productRequest) {
        StripeResponse response = stripeService.checkoutProducts(productRequest);
        return ResponseEntity.ok()
                .header("Access-Control-Allow-Origin", "https://lively-moss-09bc30c10.4.azurestaticapps.net")
                .header("Access-Control-Allow-Credentials", "true")
                .header("Vary", "Origin")
                .body(response);
    }

    @RequestMapping(value = "/checkout", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok()
                .header("Access-Control-Allow-Origin", "https://lively-moss-09bc30c10.4.azurestaticapps.net")
                .header("Access-Control-Allow-Methods", "POST, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .header("Access-Control-Allow-Credentials", "true")
                .header("Vary", "Origin")
                .build();
    }
}