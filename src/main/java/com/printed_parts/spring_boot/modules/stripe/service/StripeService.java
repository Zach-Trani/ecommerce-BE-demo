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

import java.util.List;

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
                productRequest.getName()
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
            // Create product data for this item
            SessionCreateParams.LineItem.PriceData.ProductData productData = 
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(item.getName())
                            .build();
            
            // Create price data for this item
            SessionCreateParams.LineItem.PriceData priceData = 
                    SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(currency)
                            .setUnitAmount(item.getAmount())
                            .setProductData(productData)
                            .build();
            
            // Create line item with the above price data
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
