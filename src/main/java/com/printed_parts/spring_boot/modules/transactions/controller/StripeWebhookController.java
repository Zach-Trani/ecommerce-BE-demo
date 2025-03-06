package com.printed_parts.spring_boot.modules.transactions.controller;

import com.printed_parts.spring_boot.modules.transactions.entity.Transaction;
import com.printed_parts.spring_boot.modules.transactions.entity.TransactionItem;
import com.printed_parts.spring_boot.modules.transactions.repository.TransactionRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.model.LineItem;
import com.stripe.model.LineItemCollection;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionListLineItemsParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
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
        
        // Enhanced debug logging
        log.info("Received webhook: {}", payload.substring(0, Math.min(payload.length(), 100)) + "...");
        log.info("Signature: {}", sigHeader);
        log.debug("Using webhook secret: {}...", webhookSecret.substring(0, 6));

        // Verify webhook signature
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            log.info("Event verified successfully: {} with ID: {}", event.getType(), event.getId());
        } catch (SignatureVerificationException e) {
            log.error("Signature verification failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Invalid signature: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error parsing webhook: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Webhook error: " + e.getMessage());
        }
        
        // Check if this event has already been processed
        String sessionId = getSessionIdFromEvent(event);
        Optional<Transaction> existingTransaction = transactionRepository.findByStripeSessionId(sessionId);
        if (existingTransaction.isPresent()) {
            log.info("Event already processed: {}", event.getId());
            return ResponseEntity.ok("Event already processed");
        }

        // Handle different event types
        try {
            switch (event.getType()) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(event);
                    break;
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                case "charge.succeeded":
                    log.info("Charge succeeded event received: {}", event.getId());
                    break;
                default:
                    log.info("Unhandled event type: {}", event.getType());
            }
            return ResponseEntity.ok("Webhook processed successfully for event: " + event.getType());
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.ok("Webhook received, but error during processing: " + e.getMessage());
        }
    }

    /**
     * Extract a session ID from the event
     * If it's a checkout.session event, try to get the actual session ID
     * Otherwise, generate a synthetic session ID from the event ID
     */
    private String getSessionIdFromEvent(Event event) {
        if ("checkout.session.completed".equals(event.getType())) {
            try {
                EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
                if (deserializer.getObject().isPresent()) {
                    StripeObject stripeObject = deserializer.getObject().get();
                    if (stripeObject instanceof Session) {
                        Session session = (Session) stripeObject;
                        return session.getId();
                    }
                }
            } catch (Exception e) {
                log.warn("Could not extract session ID from checkout.session event: {}", e.getMessage());
            }
        }
        
        // Fallback: create a synthetic session ID from the event ID
        return "sess_" + event.getId().substring(event.getId().length() - 10);
    }

    private void handleCheckoutSessionCompleted(Event event) {
        try {
            // Try to process as a normal session first
            if (processCheckoutSession(event)) {
                return;
            }
            
            // If that fails, fall back to creating a generic transaction
            log.info("Unable to process checkout session normally, creating generic transaction");
            createGenericTransaction(event);
        } catch (Exception e) {
            log.error("Error processing checkout session: {}", e.getMessage(), e);
            // Still try to create a generic transaction as a last resort
            try {
                createGenericTransaction(event);
            } catch (Exception ex) {
                log.error("Even generic transaction creation failed: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Tries to process a checkout session event by deserializing to a Session object
     * @return true if successful, false if needs fallback
     */
    private boolean processCheckoutSession(Event event) {
        try {
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            
            if (deserializer.getObject().isPresent()) {
                StripeObject stripeObject = deserializer.getObject().get();
                
                if (stripeObject instanceof Session) {
                    Session session = (Session) stripeObject;
                    String sessionId = session.getId();
                    
                    log.info("Processing checkout.session.completed for session ID: {}", sessionId);
                    
                    // Check if this session has already been processed
                    Optional<Transaction> existingTransaction = transactionRepository.findByStripeSessionId(sessionId);
                    if (existingTransaction.isPresent()) {
                        log.info("Transaction already exists for session ID: {}", sessionId);
                        return true;
                    }
                    
                    // Create a new transaction
                    Transaction transaction = new Transaction();
                    transaction.setStripeSessionId(sessionId);
                    transaction.setStripePaymentIntentId(session.getPaymentIntent());
                    transaction.setCustomerEmail(session.getCustomerEmail());
                    transaction.setTotalAmount(new BigDecimal(session.getAmountTotal()).divide(new BigDecimal(100)));
                    transaction.setCurrency(session.getCurrency());
                    transaction.setPaymentStatus("COMPLETED");
                    transaction.setTransactionDate(LocalDateTime.now());
                    
                    // Save transaction first to get an ID
                    transaction = transactionRepository.save(transaction);
                    log.info("Saved transaction with ID: {}", transaction.getId());
                    
                    // Create transaction items
                    List<TransactionItem> transactionItems = new ArrayList<>();
                    
                    try {
                        // Attempt to retrieve line items
                        SessionListLineItemsParams params = SessionListLineItemsParams.builder().build();
                        LineItemCollection lineItems = session.listLineItems(params);
                        
                        for (LineItem lineItem : lineItems.getData()) {
                            TransactionItem item = new TransactionItem();
                            item.setProductName(lineItem.getDescription());
                            item.setProductId(lineItem.getPrice().getId());
                            item.setQuantity(lineItem.getQuantity());
                            item.setPrice(new BigDecimal(lineItem.getPrice().getUnitAmount()).divide(new BigDecimal(100)));
                            item.setTotalPrice(item.getPrice().multiply(new BigDecimal(item.getQuantity())));
                            item.setTransaction(transaction);
                            transactionItems.add(item);
                            log.debug("Added item: {} x{}", item.getProductName(), item.getQuantity());
                        }
                    } catch (Exception e) {
                        // If retrieving line items fails, create a single transaction item
                        log.warn("Failed to retrieve line items, creating a single transaction item: {}", e.getMessage());
                        TransactionItem item = new TransactionItem();
                        item.setProductName("Order #" + sessionId.substring(sessionId.length() - 6));
                        item.setProductId(sessionId);
                        item.setQuantity(1L);
                        item.setPrice(transaction.getTotalAmount());
                        item.setTotalPrice(transaction.getTotalAmount());
                        item.setTransaction(transaction);
                        transactionItems.add(item);
                    }
                    
                    // Set items and save again
                    transaction.setItems(transactionItems);
                    transactionRepository.save(transaction);
                    log.info("Transaction completed successfully with {} items", transactionItems.size());
                    return true;
                } else {
                    log.warn("Object is not a Session: {}", stripeObject.getClass().getName());
                    return false;
                }
            } else {
                log.warn("Unable to deserialize event object for checkout.session.completed");
                return false;
            }
        } catch (Exception e) {
            log.error("Error processing checkout session: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private void createGenericTransaction(Event event) {
        try {
            // Generate a session ID from the event ID
            String sessionId = getSessionIdFromEvent(event);
            String paymentIntentId = null;
            
            log.info("Creating generic transaction for event: {}", event.getId());
            
            // Check if this session has already been processed
            Optional<Transaction> existingTransaction = transactionRepository.findByStripeSessionId(sessionId);
            if (existingTransaction.isPresent()) {
                log.info("Transaction already exists for session ID: {}", sessionId);
                return;
            }
            
            // Create a new transaction with minimal information
            Transaction transaction = new Transaction();
            transaction.setStripeSessionId(sessionId);
            transaction.setStripePaymentIntentId(paymentIntentId);
            transaction.setCustomerEmail("webhook@example.com");
            transaction.setTotalAmount(BigDecimal.ZERO); // We don't know the amount
            transaction.setCurrency("USD"); // Default currency
            transaction.setPaymentStatus("COMPLETED");
            transaction.setTransactionDate(LocalDateTime.now());
            
            // Save transaction
            transaction = transactionRepository.save(transaction);
            log.info("Saved generic transaction with ID: {}", transaction.getId());
            
            // Create a simple transaction item
            List<TransactionItem> transactionItems = new ArrayList<>();
            TransactionItem item = new TransactionItem();
            item.setProductName("Event #" + event.getId().substring(event.getId().length() - 6));
            item.setProductId(event.getId());
            item.setQuantity(1L);
            item.setPrice(BigDecimal.ZERO);
            item.setTotalPrice(BigDecimal.ZERO);
            item.setTransaction(transaction);
            transactionItems.add(item);
            
            // Set items and save again
            transaction.setItems(transactionItems);
            transactionRepository.save(transaction);
            log.info("Generic transaction completed successfully");
        } catch (Exception e) {
            log.error("Error creating generic transaction: {}", e.getMessage(), e);
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        try {
            String paymentIntentId = extractPaymentIntentId(event);
            if (paymentIntentId != null) {
                updatePaymentStatusForIntent(paymentIntentId);
            } else {
                // If we couldn't extract the payment intent ID, create a generic transaction
                log.info("Unable to extract payment intent ID, creating generic transaction");
                createGenericTransaction(event);
            }
        } catch (Exception e) {
            log.error("Error processing payment intent: {}", e.getMessage(), e);
            // Try to create a generic transaction as a fallback
            try {
                createGenericTransaction(event);
            } catch (Exception ex) {
                log.error("Even generic transaction creation failed: {}", ex.getMessage(), ex);
            }
        }
    }
    
    private String extractPaymentIntentId(Event event) {
        try {
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            
            if (deserializer.getObject().isPresent()) {
                StripeObject stripeObject = deserializer.getObject().get();
                
                if (stripeObject instanceof PaymentIntent) {
                    PaymentIntent paymentIntent = (PaymentIntent) stripeObject;
                    return paymentIntent.getId();
                }
            }
            
            // Try to get the payment intent ID from the event ID if it starts with 'pi_'
            String eventId = event.getId();
            if (eventId.startsWith("pi_")) {
                return eventId;
            }
            
            return null;
        } catch (Exception e) {
            log.error("Error extracting payment intent ID: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private void updatePaymentStatusForIntent(String paymentIntentId) {
        try {
            // Check if we already have a transaction for this payment intent
            Optional<Transaction> existingTransaction = transactionRepository.findByStripePaymentIntentId(paymentIntentId);
            if (existingTransaction.isPresent()) {
                Transaction transaction = existingTransaction.get();
                // Update payment status if needed
                if (!"COMPLETED".equals(transaction.getPaymentStatus())) {
                    transaction.setPaymentStatus("COMPLETED");
                    transactionRepository.save(transaction);
                    log.info("Updated payment status to COMPLETED for transaction ID: {}", transaction.getId());
                } else {
                    log.info("Transaction already marked as COMPLETED: {}", transaction.getId());
                }
            } else {
                log.info("No transaction found for payment intent ID: {}", paymentIntentId);
            }
        } catch (Exception e) {
            log.error("Error updating payment status: {}", e.getMessage(), e);
        }
    }
}
