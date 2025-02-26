package com.printed_parts.spring_boot.modules.stripe.service;

import com.printed_parts.spring_boot.modules.stripe.dto.ProductRequest;
import com.printed_parts.spring_boot.modules.stripe.dto.StripeResponse;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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


    public StripeResponse checkoutProducts(ProductRequest productRequest){
        Stripe.apiKey=secretKey;

        // Create a PaymentIntent with the order amount and currency
        SessionCreateParams.LineItem.PriceData.ProductData productData =
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(productRequest.getName()).build();

        // Create new line item with the above product data and associated price
        SessionCreateParams.LineItem.PriceData priceData = SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency(productRequest.getCurrency() == null ? "USD" : productRequest.getCurrency())
                .setUnitAmount((productRequest.getAmount()))
                .setProductData(productData)
                .build();


        // Create new line item with the above price data
        SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                .setQuantity(productRequest.getQuantity())
                .setPriceData(priceData)
                .build();

        // Create new session with the line items
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("https://lively-moss-09bc30c10.4.azurestaticapps.net/success")
                .setCancelUrl("https://lively-moss-09bc30c10.4.azurestaticapps.net/cancel")
                .addLineItem(lineItem)
                .build();

        // Create new session
        Session session=null;

        try {
            session=Session.create(params);
        }catch (StripeException e){
            // log
            System.out.println(e.getMessage());
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
