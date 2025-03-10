package com.printed_parts.spring_boot.modules.stripe.service;

import com.printed_parts.spring_boot.modules.stripe.dto.CartRequest;
import com.printed_parts.spring_boot.modules.stripe.dto.ProductItem;
import com.printed_parts.spring_boot.modules.stripe.dto.ProductRequest;
import com.printed_parts.spring_boot.modules.stripe.dto.StripeResponse;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// stripe - api
// params: productName, amount, quantity, currency
// return: sessionId, url
@Service
public class StripeService {

    @Value("${stripe.secretKey}")
    private String secretKey;
    // stripe - API
    // -> stripe API requires: productName, price, quantity, currency
    // -> return sessionId and url

    /**
     * For backward compatibility - converts a ProductRequest to CartRequest and calls the new checkout method
     */
    public StripeResponse checkoutProducts(ProductRequest productRequest) {
        // Convert ProductRequest to ProductItem
        ProductItem item = new ProductItem(
                productRequest.getAmount(),
                productRequest.getQuantity(),
                productRequest.getName(),
                null  // No product ID in the legacy flow
        );

        // Create a CartRequest with a single item
        CartRequest cartRequest = new CartRequest();
        cartRequest.setItems(List.of(item));
        cartRequest.setCurrency(productRequest.getCurrency());

        // Use the new checkout method
        return checkout(cartRequest);
    }

    /**
     * Main checkout method that handles a cart with one or more products
     */
    public StripeResponse checkout(CartRequest cartRequest) {
        Stripe.apiKey = secretKey;
        
        // Default currency to USD if not provided
        String currency = cartRequest.getCurrency() == null ? "USD" : cartRequest.getCurrency();
        
        // Create a SessionCreateParams.Builder to build our session parameters
        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("https://lively-moss-09bc30c10.4.azurestaticapps.net/success")
                .setCancelUrl("https://lively-moss-09bc30c10.4.azurestaticapps.net/cancel");
                
        // Add each item from the cart as a line item
        for (ProductItem item : cartRequest.getItems()) {
            // Create price data for this item
            String productName = item.getName();
            
            // Encode product information in the name since metadata isn't available
            if (item.getProductId() != null) {
                // Always include the product ID in the line item description
                // Format: "Product Name [ID:123]" - consistent format for webhook parsing
                productName = productName + " [ID:" + item.getProductId() + "]";
                
                System.out.println("Adding item to cart with ID: " + item.getProductId() + 
                        ", name: " + item.getName() + 
                        ", price: " + item.getAmount() + 
                        ", quantity: " + item.getQuantity());
            } else {
                System.out.println("WARNING: Cart item has no product ID: " + productName + 
                        ". This will cause Stripe's price ID to be used instead in the webhook.");
            }
            
            SessionCreateParams.LineItem.PriceData priceData = 
                    SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(currency)
                            .setUnitAmount(item.getAmount())
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(productName)
                                    .build()
                            )
                            .build();
            
            // Create line item with the price data
            SessionCreateParams.LineItem lineItem = 
                    SessionCreateParams.LineItem.builder()
                            .setQuantity(item.getQuantity())
                            .setPriceData(priceData)
                            .build();
            
            // Add this line item to the session parameters
            paramsBuilder.addLineItem(lineItem);
        }
        
        // Build the complete session parameters
        SessionCreateParams params = paramsBuilder.build();
        
        // Create new session
        Session session = null;
        
        try {
            session = Session.create(params);
        } catch (StripeException e) {
            // log
            System.out.println(e.getMessage());
            return StripeResponse.builder()
                    .status("ERROR")
                    .message("Error creating payment session: " + e.getMessage())
                    .build();
        }
        
        // Return stripe response
        return StripeResponse.builder()
                .status("SUCCESS")
                .message("Payment session created")
                .sessionId(session.getId())
                .sessionUrl(session.getUrl())
                .build();
    }
}
