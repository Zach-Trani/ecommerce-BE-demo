package com.printed_parts.spring_boot.modules.transactions.controller;

import com.printed_parts.spring_boot.modules.transactions.entity.Transaction;
import com.printed_parts.spring_boot.modules.transactions.entity.TransactionItem;
import com.printed_parts.spring_boot.modules.transactions.repository.TransactionRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.model.LineItem;
import com.stripe.model.LineItemCollection;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionListLineItemsParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/stripe/webhook")
public class StripeWebhookController {

    private final TransactionRepository transactionRepository;
    
    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    public StripeWebhookController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        
        // Debug logging
        System.out.println("Received webhook: " + payload.substring(0, Math.min(payload.length(), 100)) + "...");
        System.out.println("Signature: " + sigHeader);

        // Verify webhook signature
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            System.out.println("Event verified successfully: " + event.getType());
        } catch (SignatureVerificationException e) {
            System.err.println("Signature verification failed: " + e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            System.err.println("Error parsing webhook: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Webhook error: " + e.getMessage());
        }

        // Handle different event types
        switch (event.getType()) {
            case "checkout.session.completed":
                handleCheckoutSessionCompleted(event);
                break;
            case "payment_intent.succeeded":
                handlePaymentIntentSucceeded(event);
                break;
            // Add other event types as needed
        }

        return ResponseEntity.ok("Webhook processed");
    }

    private void handleCheckoutSessionCompleted(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        if (dataObjectDeserializer.getObject().isPresent()) {
            Session session = (Session) dataObjectDeserializer.getObject().get();
            
            try {
                // Retrieve line items from the session
                SessionListLineItemsParams params = SessionListLineItemsParams.builder()
                        .build();
                LineItemCollection lineItems = session.listLineItems(params);
                
                // Create transaction record
                Transaction transaction = new Transaction();
                transaction.setStripeSessionId(session.getId());
                transaction.setStripePaymentIntentId(session.getPaymentIntent());
                transaction.setCustomerEmail(session.getCustomerEmail());
                transaction.setTotalAmount(new BigDecimal(session.getAmountTotal()).divide(new BigDecimal(100)));
                transaction.setCurrency(session.getCurrency());
                transaction.setPaymentStatus("COMPLETED");
                transaction.setTransactionDate(LocalDateTime.now());
                
                // Save transaction first to get an ID
                transaction = transactionRepository.save(transaction);
                
                // Create transaction items from line items
                List<TransactionItem> transactionItems = new ArrayList<>();
                for (LineItem lineItem : lineItems.getData()) {
                    TransactionItem item = new TransactionItem();
                    item.setProductName(lineItem.getDescription());
                    item.setProductId(lineItem.getPrice().getId());
                    item.setQuantity(lineItem.getQuantity());
                    item.setPrice(new BigDecimal(lineItem.getPrice().getUnitAmount()).divide(new BigDecimal(100)));
                    item.setTotalPrice(item.getPrice().multiply(new BigDecimal(item.getQuantity())));
                    item.setTransaction(transaction);
                    transactionItems.add(item);
                }
                
                // Set items and save again
                transaction.setItems(transactionItems);
                transactionRepository.save(transaction);
                
            } catch (Exception e) {
                System.out.println("Error processing session: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        // Similar implementation for payment_intent.succeeded events
    }
}
