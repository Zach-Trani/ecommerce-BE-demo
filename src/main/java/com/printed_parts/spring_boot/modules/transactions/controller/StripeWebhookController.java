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
        log.info("StripeWebhookController initialized with webhook secret: {}...", 
                webhookSecret != null ? webhookSecret.substring(0, Math.min(6, webhookSecret.length())) : "null");
    }

    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        
        // DETAILED DEBUG LOGGING
        log.info("============ WEBHOOK REQUEST RECEIVED ============");
        log.info("Received webhook payload length: {} bytes", payload != null ? payload.length() : 0);
        log.info("First 100 chars of payload: {}", payload != null ? 
                payload.substring(0, Math.min(payload.length(), 100)) + "..." : "null");
        log.info("Signature header: {}", sigHeader);
        log.info("Using webhook secret starting with: {}", 
                webhookSecret != null ? webhookSecret.substring(0, Math.min(6, webhookSecret.length())) : "null");

        if (payload == null || payload.isEmpty()) {
            log.error("Webhook payload is null or empty");
            return ResponseEntity.badRequest().body("Webhook payload is empty");
        }

        if (sigHeader == null || sigHeader.isEmpty()) {
            log.error("Stripe-Signature header is null or empty");
            return ResponseEntity.badRequest().body("Stripe-Signature header is missing");
        }

        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.error("Webhook secret is null or empty");
            return ResponseEntity.badRequest().body("Webhook secret is not configured");
        }

        // Verify webhook signature
        Event event;
        try {
            log.debug("Attempting to construct event from payload and signature");
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            log.info("Event verified successfully: {} with ID: {}", event.getType(), event.getId());
        } catch (SignatureVerificationException e) {
            log.error("Signature verification failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Invalid signature: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error parsing webhook: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Webhook error: " + e.getMessage());
        }
        
        // Get session ID and check for duplicate processing
        String sessionId = getSessionIdFromEvent(event);
        log.info("Extracted session ID: {}", sessionId);
        
        Optional<Transaction> existingTransaction = transactionRepository.findByStripeSessionId(sessionId);
        if (existingTransaction.isPresent()) {
            log.info("Event already processed for session ID: {}", sessionId);
            return ResponseEntity.ok("Event already processed");
        }

        // Handle different event types
        try {
            log.info("Processing event type: {}", event.getType());
            switch (event.getType()) {
                case "checkout.session.completed":
                    log.info("Handling checkout.session.completed event");
                    handleCheckoutSessionCompleted(event);
                    break;
                case "payment_intent.succeeded":
                    log.info("Handling payment_intent.succeeded event");
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
        log.debug("Extracting session ID from event type: {}", event.getType());
        
        if ("checkout.session.completed".equals(event.getType())) {
            try {
                EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
                log.debug("Deserializer has object: {}", deserializer.getObject().isPresent());
                
                if (deserializer.getObject().isPresent()) {
                    StripeObject stripeObject = deserializer.getObject().get();
                    log.debug("StripeObject class: {}", stripeObject.getClass().getName());
                    
                    if (stripeObject instanceof Session) {
                        Session session = (Session) stripeObject;
                        String id = session.getId();
                        log.debug("Extracted session ID: {}", id);
                        return id;
                    } else {
                        log.warn("Expected Session object but got: {}", stripeObject.getClass().getName());
                    }
                } else {
                    log.warn("Failed to get object from deserializer");
                }
            } catch (Exception e) {
                log.warn("Could not extract session ID from checkout.session event: {}", e.getMessage(), e);
            }
        }
        
        // Fallback: create a synthetic session ID from the event ID
        String fallbackId = "sess_" + event.getId().substring(event.getId().length() - 10);
        log.debug("Using fallback session ID: {}", fallbackId);
        return fallbackId;
    }

    private void handleCheckoutSessionCompleted(Event event) {
        log.info("=== HANDLING CHECKOUT SESSION COMPLETED ===");
        try {
            // Try to process as a normal session first
            log.debug("Attempting to process checkout session normally");
            if (processCheckoutSession(event)) {
                log.info("Successfully processed checkout session");
                return;
            }
            
            // If that fails, fall back to creating a generic transaction
            log.info("Unable to process checkout session normally, creating generic transaction");
            createGenericTransaction(event);
        } catch (Exception e) {
            log.error("Error processing checkout session: {}", e.getMessage(), e);
            // Still try to create a generic transaction as a last resort
            try {
                log.info("Attempting fallback to generic transaction after error");
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
        log.debug("Processing checkout session for event ID: {}", event.getId());
        try {
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            log.debug("Deserializer has object present: {}", deserializer.getObject().isPresent());
            
            if (deserializer.getObject().isPresent()) {
                StripeObject stripeObject = deserializer.getObject().get();
                log.debug("Stripe object class: {}", stripeObject.getClass().getName());
                
                if (stripeObject instanceof Session) {
                    Session session = (Session) stripeObject;
                    String sessionId = session.getId();
                    
                    log.info("Processing checkout.session.completed for session ID: {}", sessionId);
                    log.debug("Session details - payment intent: {}, customer email: {}, amount: {}, currency: {}", 
                            session.getPaymentIntent(), session.getCustomerEmail(), 
                            session.getAmountTotal(), session.getCurrency());
                    
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
                    log.debug("Saving initial transaction record");
                    transaction = transactionRepository.save(transaction);
                    log.info("Saved transaction with ID: {}", transaction.getId());
                    
                    // Create transaction items
                    List<TransactionItem> transactionItems = new ArrayList<>();
                    
                    try {
                        // Attempt to retrieve line items
                        log.debug("Attempting to retrieve line items for session: {}", sessionId);
                        SessionListLineItemsParams params = SessionListLineItemsParams.builder().build();
                        LineItemCollection lineItems = session.listLineItems(params);
                        
                        log.debug("Retrieved {} line items", lineItems.getData().size());
                        for (LineItem lineItem : lineItems.getData()) {
                            TransactionItem item = new TransactionItem();
                            item.setProductName(lineItem.getDescription());
                            item.setProductId(lineItem.getPrice().getId());
                            item.setQuantity(lineItem.getQuantity());
                            item.setPrice(new BigDecimal(lineItem.getPrice().getUnitAmount()).divide(new BigDecimal(100)));
                            item.setTotalPrice(item.getPrice().multiply(new BigDecimal(item.getQuantity())));
                            item.setTransaction(transaction);
                            transactionItems.add(item);
                            log.debug("Added item: {} x{} at price {} for total {}", 
                                    item.getProductName(), item.getQuantity(), item.getPrice(), item.getTotalPrice());
                        }
                    } catch (Exception e) {
                        // If retrieving line items fails, create a single transaction item
                        log.warn("Failed to retrieve line items, creating a single transaction item: {}", e.getMessage(), e);
                        TransactionItem item = new TransactionItem();
                        item.setProductName("Order #" + sessionId.substring(sessionId.length() - 6));
                        item.setProductId(sessionId);
                        item.setQuantity(1L);
                        item.setPrice(transaction.getTotalAmount());
                        item.setTotalPrice(transaction.getTotalAmount());
                        item.setTransaction(transaction);
                        transactionItems.add(item);
                        log.debug("Added fallback item: {} for total {}", item.getProductName(), item.getTotalPrice());
                    }
                    
                    // Set items and save again
                    transaction.setItems(transactionItems);
                    log.debug("Saving transaction with {} items", transactionItems.size());
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
        log.info("Creating generic transaction for event: {}", event.getId());
        try {
            // Generate a session ID from the event ID
            String sessionId = getSessionIdFromEvent(event);
            String paymentIntentId = null;
            
            log.debug("Using session ID: {} for generic transaction", sessionId);
            
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
            
            // Save transaction first to get an ID
            log.debug("Saving generic transaction record");
            transaction = transactionRepository.save(transaction);
            log.info("Saved generic transaction with ID: {}", transaction.getId());
            
            // Create a single transaction item
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
            log.debug("Saving generic transaction with 1 item");
            transactionRepository.save(transaction);
            log.info("Generic transaction completed successfully");
        } catch (Exception e) {
            log.error("Error creating generic transaction: {}", e.getMessage(), e);
        }
    }
    
    private void handlePaymentIntentSucceeded(Event event) {
        log.info("=== HANDLING PAYMENT INTENT SUCCEEDED ===");
        try {
            String paymentIntentId = extractPaymentIntentId(event);
            if (paymentIntentId != null) {
                log.info("Extracted payment intent ID: {}", paymentIntentId);
                
                // If we have a transaction with this payment intent ID already
                Optional<Transaction> existingTransaction = transactionRepository.findByStripePaymentIntentId(paymentIntentId);
                if (existingTransaction.isPresent()) {
                    log.info("Transaction exists for payment intent ID: {}, updating status", paymentIntentId);
                    updatePaymentStatusForIntent(paymentIntentId);
                } else {
                    log.info("No transaction found for payment intent ID: {}, this may be normal if checkout.session was processed first", paymentIntentId);
                }
            } else {
                log.warn("Could not extract payment intent ID from event");
            }
        } catch (Exception e) {
            log.error("Error handling payment_intent.succeeded event: {}", e.getMessage(), e);
        }
    }
    
    private String extractPaymentIntentId(Event event) {
        log.debug("Extracting payment intent ID from event: {}", event.getId());
        try {
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            if (deserializer.getObject().isPresent()) {
                StripeObject stripeObject = deserializer.getObject().get();
                log.debug("Payment intent object class: {}", stripeObject.getClass().getName());
                
                if (stripeObject instanceof PaymentIntent) {
                    PaymentIntent paymentIntent = (PaymentIntent) stripeObject;
                    String id = paymentIntent.getId();
                    log.debug("Extracted payment intent ID: {}", id);
                    return id;
                } else {
                    log.warn("Expected PaymentIntent object but got: {}", stripeObject.getClass().getName());
                }
            } else {
                log.warn("Payment intent event deserializer couldn't get object");
            }
        } catch (Exception e) {
            log.warn("Error extracting payment intent ID: {}", e.getMessage(), e);
        }
        return null;
    }
    
    private void updatePaymentStatusForIntent(String paymentIntentId) {
        log.info("Updating payment status for intent: {}", paymentIntentId);
        try {
            Optional<Transaction> transactionOpt = transactionRepository.findByStripePaymentIntentId(paymentIntentId);
            if (transactionOpt.isPresent()) {
                Transaction transaction = transactionOpt.get();
                transaction.setPaymentStatus("COMPLETED");
                transactionRepository.save(transaction);
                log.info("Updated transaction {} payment status to COMPLETED", transaction.getId());
            } else {
                log.warn("No transaction found for payment intent ID: {}", paymentIntentId);
            }
        } catch (Exception e) {
            log.error("Error updating payment status: {}", e.getMessage(), e);
        }
    }
}
