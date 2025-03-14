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

/**
 * Controller responsible for handling Stripe webhook events after payment processing.
 *
 * This controller receives events from Stripe when payment-related actions occur, such as:
 * - Checkout session completion
 * - Payment intent success
 * 
 * It processes these events to create transaction records in the database, facilitating
 * order history tracking and payment confirmation in the ecommerce application.
 * 
 * Data Flow:
 * 1. Stripe sends webhook event to /stripe/webhook endpoint
 * 2. Controller verifies webhook signature using the secret key
 * 3. Event is parsed and handled based on its type
 * 4. Transaction and TransactionItem objects are created and stored in database
 */
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

    /**
     * Simple diagnostic endpoint to check if the webhook controller is accessible.
     * <p>
     * This endpoint helps verify both controller accessibility and database connectivity.
     * 
     * @return ResponseEntity<String> - HTTP 200 with status message if healthy, 
     *                                  HTTP 500 with error details if database connection fails
     */
    @GetMapping("/healthcheck")
    public ResponseEntity<String> healthCheck() {
        log.info("Webhook healthcheck endpoint accessed");
        
        // Check if we can access the database
        long count = 0;
        try {
            count = transactionRepository.count();
            log.info("Database connection successful. Transaction count: {}", count);
            return ResponseEntity.ok("Webhook controller is healthy. DB connection ok. Transaction count: " + count);
        } catch (Exception e) {
            log.error("Database connection failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Webhook controller reached but database error: " + e.getMessage());
        }
    }

    /**
     * Main webhook handler that receives and processes events from Stripe.
     * <p>
     * This endpoint:
     * 1. Verifies the Stripe signature to ensure the event is authentic
     * 2. Checks for duplicate event processing
     * 3. Dispatches to appropriate handler based on event type
     * 
     * @param payload String - Raw JSON payload from Stripe
     * @param sigHeader String - 'Stripe-Signature' header containing signature verification data
     * @return ResponseEntity<String> - HTTP 200 if processed or already handled,
     *                                  HTTP 400 if signature verification fails or payload is invalid
     */
    @PostMapping
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        
        log.info("====== WEBHOOK RECEIVED ======");
        log.info("Headers: Stripe-Signature length: {}", sigHeader != null ? sigHeader.length() : 0);
        log.info("Payload length: {}", payload != null ? payload.length() : 0);
        log.info("Webhook secret configured: {}", webhookSecret != null ? "YES (length: " + webhookSecret.length() + ")" : "NO");
        
        // Basic input validation
        if (payload == null || payload.isEmpty() || sigHeader == null || sigHeader.isEmpty()) {
            log.error("Invalid webhook request: missing payload or signature");
            return ResponseEntity.badRequest().body("Missing payload or signature");
        }

        // STRIPE BOILERPLATE: Verify webhook signature
        Event event;
        try {
            log.info("Attempting to construct event with Webhook.constructEvent...");
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            log.info("Webhook received: {} [{}]", event.getType(), event.getId());
        } catch (SignatureVerificationException e) {
            log.error("Invalid signature: {}", e.getMessage());
            log.error("This usually means the webhook secret is incorrect. Check your Stripe Dashboard and environment variables.");
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            log.error("Error parsing webhook: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Webhook error: " + e.getMessage());
        }
        
        // CUSTOM APP LOGIC: Check for duplicate processing
        String sessionId = getSessionIdFromEvent(event);
        Optional<Transaction> existingTransaction = transactionRepository.findByStripeSessionId(sessionId);
        if (existingTransaction.isPresent()) {
            log.info("Event already processed: {}", sessionId);
            return ResponseEntity.ok("Event already processed");
        }

        // CUSTOM APP LOGIC: Handle different event types
        try {
            switch (event.getType()) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(event);
                    break;
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                default:
                    log.info("Unhandled event type: {}", event.getType());
            }
            return ResponseEntity.ok("Webhook processed");
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.ok("Webhook received, but error during processing");
        }
    }

    /**
     * Extracts the Stripe session ID from an event.
     * <p>
     * This method attempts multiple strategies to extract the session ID:
     * 1. Directly from the deserialized Event object (preferred)
     * 2. By parsing the raw JSON data
     * 3. Falling back to a synthetic ID based on the event ID if all else fails
     * 
     * @param event Event - The Stripe event object
     * @return String - The session ID (format: "cs_..." if from Stripe, "sess_..." if synthetic)
     */
    private String getSessionIdFromEvent(Event event) {
        if ("checkout.session.completed".equals(event.getType())) {
            try {
                // First try to get it from the event data object 
                EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
                if (deserializer.getObject().isPresent()) {
                    StripeObject stripeObject = deserializer.getObject().get();
                    if (stripeObject instanceof Session) {
                        return ((Session) stripeObject).getId();
                    }
                }
                
                // If that fails, try to parse it directly from the raw JSON
                try {
                    String rawJson = event.toJson();
                    // Look for the session ID in the raw JSON data
                    if (rawJson.contains("\"id\":")) {
                        // Find the session ID in the object section
                        int objectIdStart = rawJson.indexOf("\"object\":");
                        if (objectIdStart > 0) {
                            int idStart = rawJson.indexOf("\"id\":", objectIdStart);
                            if (idStart > 0) {
                                idStart = rawJson.indexOf("\"", idStart + 5) + 1; // Skip past "id": "
                                int idEnd = rawJson.indexOf("\"", idStart);
                                if (idEnd > idStart) {
                                    String sessionId = rawJson.substring(idStart, idEnd);
                                    if (sessionId.startsWith("cs_")) {
                                        log.info("Successfully extracted session ID from JSON: {}", sessionId);
                                        return sessionId;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error extracting session ID from JSON: {}", e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Could not extract session ID from event: {}", e.getMessage());
            }
        }
        
        // If still no session ID, create a synthetic one 
        String fallbackId = "sess_" + event.getId().substring(event.getId().length() - 10);
        log.warn("Using fallback session ID: {} (this will not work for retrieving data from Stripe)", fallbackId);
        return fallbackId;
    }

    /**
     * Handles 'checkout.session.completed' events from Stripe.
     * <p>
     * This method attempts to process the checkout session using two approaches:
     * 1. Normal session processing - tries to get detailed line item data
     * 2. Generic transaction - fallback when session data can't be properly extracted
     * 
     * @param event Event - The Stripe event object containing checkout session data
     */
    private void handleCheckoutSessionCompleted(Event event) {
        log.info("Handling checkout.session.completed event: {}", event.getId());
        try {
            // Try to process as a normal session first
            boolean processed = processCheckoutSession(event);
            if (processed) {
                log.info("Successfully processed checkout session: {}", event.getId());
                return;
            }
            
            // If session processing failed, explain why we're falling back
            log.warn("Failed to process checkout session normally. This may occur if:");
            log.warn("1. The Stripe session could not be deserialized from the event");
            log.warn("2. The event data does not contain a valid Session object");
            log.warn("3. There was an error retrieving line items from the session");
            log.warn("Falling back to generic transaction for event: {}", event.getId());
            
            // Create a generic transaction as fallback
            createGenericTransaction(event);
        } catch (Exception e) {
            log.error("Error processing checkout session: {}", e.getMessage(), e);
            try {
                log.warn("Attempting fallback to generic transaction due to error: {}", e.getMessage());
                createGenericTransaction(event);
            } catch (Exception ex) {
                log.error("Generic transaction creation failed: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Processes a checkout session event by extracting transaction details and storing in database.
     * <p>
     * This method:
     * 1. Extracts the session ID and checks for duplicate processing
     * 2. Extracts transaction data (email, amount, currency) from the event
     * 3. Creates and saves a Transaction entity
     * 4. Retrieves line items from the Stripe API or creates fallback items
     * 5. Creates and saves TransactionItem entities linked to the Transaction
     * 
     * @param event Event - The Stripe event object containing checkout session data
     * @return boolean - true if processing succeeded, false otherwise
     */
    private boolean processCheckoutSession(Event event) {
        log.info("Attempting to process checkout session for event: {}", event.getId());
        try {
            // Get the session ID using our improved method
            String sessionId = getSessionIdFromEvent(event);
            log.info("Using session ID: {}", sessionId);
                    
            // Check if this session has already been processed
            Optional<Transaction> existingTransaction = transactionRepository.findByStripeSessionId(sessionId);
            if (existingTransaction.isPresent()) {
                log.info("Session already processed: {}", sessionId);
                return true;
            }
            
            // Extract customer email and amount from the raw JSON if possible
            String customerEmail = "webhook@example.com";
            BigDecimal amount = BigDecimal.ZERO;
            String currency = "usd";
            String paymentIntentId = null;
            boolean isDataExtracted = false;
            
            try {
                String rawJson = event.toJson();
                log.info("Attempting to extract transaction data from raw event JSON");
                
                // Extract customer email
                int customerDetailsIndex = rawJson.indexOf("\"customer_details\":");
                if (customerDetailsIndex > 0) {
                    int emailIndex = rawJson.indexOf("\"email\":", customerDetailsIndex);
                    if (emailIndex > 0) {
                        emailIndex = rawJson.indexOf("\"", emailIndex + 8) + 1; // Skip past "email": "
                        int emailEndIndex = rawJson.indexOf("\"", emailIndex);
                        if (emailEndIndex > emailIndex) {
                            customerEmail = rawJson.substring(emailIndex, emailEndIndex);
                            log.info("Extracted customer email: {}", customerEmail);
                        }
                    }
                }
                
                // Extract amount
                int amountIndex = rawJson.indexOf("\"amount_total\":");
                if (amountIndex > 0) {
                    amountIndex += 15; // Skip past "amount_total":
                    int amountEndIndex = rawJson.indexOf(",", amountIndex);
                    if (amountEndIndex > amountIndex) {
                        String amountStr = rawJson.substring(amountIndex, amountEndIndex).trim();
                        try {
                            long amountCents = Long.parseLong(amountStr);
                            amount = new BigDecimal(amountCents).divide(new BigDecimal(100));
                            log.info("Extracted amount: {}", amount);
                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse amount: {}", amountStr);
                        }
                    }
                }
                
                // Extract currency
                int currencyIndex = rawJson.indexOf("\"currency\":");
                if (currencyIndex > 0) {
                    currencyIndex = rawJson.indexOf("\"", currencyIndex + 11) + 1; // Skip past "currency": "
                    int currencyEndIndex = rawJson.indexOf("\"", currencyIndex);
                    if (currencyEndIndex > currencyIndex) {
                        currency = rawJson.substring(currencyIndex, currencyEndIndex);
                        log.info("Extracted currency: {}", currency);
                    }
                }
                
                // Extract payment intent ID
                int paymentIntentIndex = rawJson.indexOf("\"payment_intent\":");
                if (paymentIntentIndex > 0) {
                    paymentIntentIndex = rawJson.indexOf("\"", paymentIntentIndex + 17) + 1; // Skip past "payment_intent": "
                    int paymentIntentEndIndex = rawJson.indexOf("\"", paymentIntentIndex);
                    if (paymentIntentEndIndex > paymentIntentIndex) {
                        paymentIntentId = rawJson.substring(paymentIntentIndex, paymentIntentEndIndex);
                        log.info("Extracted payment intent ID: {}", paymentIntentId);
                    }
                }
                
                isDataExtracted = true;
            } catch (Exception e) {
                log.warn("Failed to extract data from event JSON: {}", e.getMessage());
            }
                    
            // Create a new transaction record
            Transaction transaction = new Transaction();
            transaction.setStripeSessionId(sessionId);
            transaction.setStripePaymentIntentId(paymentIntentId);
            transaction.setCustomerEmail(customerEmail);
            transaction.setTotalAmount(amount);
            transaction.setCurrency(currency);
            transaction.setPaymentStatus("COMPLETED");
            transaction.setTransactionDate(LocalDateTime.now());
            
            log.info("Created transaction with session ID: {}, amount: {}", sessionId, amount);
                    
            // Save transaction first to get an ID
            transaction = transactionRepository.save(transaction);
                    
            // Now try to retrieve line items
            // Two approaches:
            // 1. Try to parse from raw JSON if available
            // 2. If that fails, try to retrieve from Stripe API
            List<TransactionItem> transactionItems = new ArrayList<>();
            boolean itemsCreated = false;
            
            try {
                // First try the Stripe API approach if we have a valid session ID
                if (sessionId.startsWith("cs_")) {
                    log.info("Attempting to retrieve session from Stripe with ID: {}", sessionId);
                    try {
                        Session session = Session.retrieve(sessionId);
                        log.info("Successfully retrieved session from Stripe API");
                        
                        // Now get line items
                        log.info("Retrieving line items for session: {}", sessionId);
                        SessionListLineItemsParams params = SessionListLineItemsParams.builder().build();
                        LineItemCollection lineItems = session.listLineItems(params);
                        
                        log.info("Found {} line items", lineItems.getData().size());
                        
                        for (LineItem lineItem : lineItems.getData()) {
                            // Process each line item as before
                            TransactionItem item = createTransactionItemFromLineItem(lineItem, transaction);
                            transactionItems.add(item);
                        }
                        
                        itemsCreated = true;
                    } catch (Exception e) {
                        log.error("Failed to retrieve session or line items from Stripe: {}", e.getMessage(), e);
                    }
                }
                
                // If we couldn't get items from Stripe API, create a fallback item
                if (!itemsCreated) {
                    log.warn("Could not retrieve line items - creating fallback transaction item");
                    TransactionItem item = new TransactionItem();
                    item.setProductName("Order from " + customerEmail);
                    item.setProductId(sessionId);
                    item.setQuantity(1L);
                    item.setPrice(amount);
                    item.setTotalPrice(amount);
                    item.setTransaction(transaction);
                    transactionItems.add(item);
                }
            } catch (Exception e) {
                log.error("Error creating transaction items: {}", e.getMessage(), e);
                // Create generic item
                TransactionItem item = new TransactionItem();
                item.setProductName("Order #" + sessionId.substring(sessionId.length() - 6));
                item.setProductId(sessionId);
                item.setQuantity(1L);
                item.setPrice(amount);
                item.setTotalPrice(amount);
                item.setTransaction(transaction);
                transactionItems.add(item);
            }
                    
            // Set items and save again
            transaction.setItems(transactionItems);
            transactionRepository.save(transaction);
            log.info("Transaction saved with {} items", transactionItems.size());
            return true;
        } catch (Exception e) {
            log.error("Error processing checkout session: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Creates a TransactionItem entity from a Stripe LineItem.
     * <p>
     * This method:
     * 1. Extracts product ID from the line item description (format: "Product Name [ID:123]")
     * 2. Cleans up the product name by removing the ID part
     * 3. Sets quantity, price, and total price based on the Stripe line item data
     * 
     * @param lineItem LineItem - The Stripe line item object containing product data
     * @param transaction Transaction - The parent Transaction entity to link this item to
     * @return TransactionItem - The created TransactionItem entity
     */
    private TransactionItem createTransactionItemFromLineItem(LineItem lineItem, Transaction transaction) {
        TransactionItem item = new TransactionItem();
        
        // Get the raw description before any parsing
        String description = lineItem.getDescription();
        log.info("Processing line item with description: '{}'", description);
        
        String productId = null;
        Integer productIdInt = null;
        
        // Extract product ID from item description if it contains "[ID:123]" format
        if (description != null && description.contains("[ID:")) {
            try {
                int startIndex = description.indexOf("[ID:") + 4;
                int endIndex = description.indexOf("]", startIndex);
                if (endIndex > startIndex) {
                    productId = description.substring(startIndex, endIndex).trim();
                    log.info("Extracted product ID: {} from description", productId);
                    
                    try {
                        productIdInt = Integer.parseInt(productId);
                        log.info("Converted to integer product ID: {}", productIdInt);
                    } catch (NumberFormatException e) {
                        log.warn("Product ID is not a valid integer: {}", productId);
                    }
                    
                    // Clean the product name by removing the ID part
                    description = description.substring(0, description.indexOf("[ID:")).trim();
                    log.info("Clean product name: '{}'", description);
                }
            } catch (Exception e) {
                log.error("Failed to parse product ID from description: {} - {}", description, e.getMessage(), e);
                // Continue processing even if ID extraction fails
            }
        } else {
            log.warn("Description does not contain product ID format: '{}'", description);
        }
        
        // Set product name (cleaned if ID was extracted, or original otherwise)
        item.setProductName(description);
        
        // FIXED: Use extracted internal product ID if available and log what's happening
        if (productId != null) {
            item.setProductId(productId);
            log.info("Using extracted product ID from description: {}", productId);
        } else {
            // This is a fallback that should rarely happen if your products are correctly configured
            String fallbackId = lineItem.getPrice().getId();
            item.setProductId(fallbackId);
            log.warn("⚠️ USING STRIPE PRICE ID AS FALLBACK: {}. THIS INDICATES PRODUCT ID WAS NOT FOUND IN DESCRIPTION.", 
                    fallbackId);
            log.warn("Please verify that product IDs are correctly included in product names during checkout.");
        }
        
        // Set other item properties from the Stripe line item
        log.info("Line item raw data - quantity: {}, unit_amount: {}", 
                lineItem.getQuantity(), lineItem.getPrice().getUnitAmount());
        
        item.setQuantity(lineItem.getQuantity());
        item.setPrice(new BigDecimal(lineItem.getPrice().getUnitAmount()).divide(new BigDecimal(100)));
        item.setTotalPrice(item.getPrice().multiply(new BigDecimal(item.getQuantity())));
        item.setTransaction(transaction);
        
        log.info("Added transaction item: productId={}, name='{}', price={}, quantity={}, total={}",
                item.getProductId(), item.getProductName(), item.getPrice(), item.getQuantity(), item.getTotalPrice());
        
        return item;
    }
    
    /**
     * Creates a generic transaction when proper session data can't be retrieved.
     * <p>
     * This is a fallback method that creates a minimal transaction record when
     * normal processing fails. It's used to ensure we at least have some record
     * of the payment event, even if details are limited.
     * 
     * @param event Event - The Stripe event object containing limited payment data
     */
    private void createGenericTransaction(Event event) {
        log.info("Creating generic transaction for event type: {}, event ID: {}", event.getType(), event.getId());
        try {
            // Generate a session ID from the event ID
            String sessionId = getSessionIdFromEvent(event);
            
            // Try to get some meaningful data from the event
            String customerEmail = "webhook@example.com";
            BigDecimal amount = BigDecimal.ZERO;
            String currency = "USD";
            
            // Try to extract some data from the raw event JSON if possible
            try {
                String rawJson = event.toJson();
                log.info("Raw event JSON: {}", rawJson);
                
                // Log the data to help with debugging
                log.info("Stripe event raw data - ID: {}, API Version: {}, Type: {}", 
                        event.getId(), event.getApiVersion(), event.getType());
            } catch (Exception e) {
                log.warn("Could not extract raw event data: {}", e.getMessage());
            }
            
            // Check for duplicate transactions
            Optional<Transaction> existingTransaction = transactionRepository.findByStripeSessionId(sessionId);
            if (existingTransaction.isPresent()) {
                log.info("Generic transaction already exists for session: {}", sessionId);
                return;
            }
            
            // Create a new transaction with minimal information
            Transaction transaction = new Transaction();
            transaction.setStripeSessionId(sessionId);
            transaction.setStripePaymentIntentId(null);
            transaction.setCustomerEmail(customerEmail);
            transaction.setTotalAmount(amount);
            transaction.setCurrency(currency);
            transaction.setPaymentStatus("COMPLETED");
            transaction.setTransactionDate(LocalDateTime.now());
            
            log.info("Created generic transaction with session ID: {} (fallback mechanism)", sessionId);
            
            // Save transaction first to get an ID
            transaction = transactionRepository.save(transaction);
            
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
            transactionRepository.save(transaction);
            log.warn("Saved generic transaction with placeholder values - please check Stripe Dashboard for actual order details");
            log.warn("To fix this issue, you may need to update the Stripe API version or check the webhook payload format");
        } catch (Exception e) {
            log.error("Error creating generic transaction: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles 'payment_intent.succeeded' events from Stripe.
     * <p>
     * This method updates the payment status of an existing transaction
     * if one is found with the matching payment intent ID.
     * 
     * @param event Event - The Stripe event object containing payment intent data
     */
    private void handlePaymentIntentSucceeded(Event event) {
        try {
            String paymentIntentId = extractPaymentIntentId(event);
            if (paymentIntentId != null) {
                // Update existing transaction if found
                Optional<Transaction> existingTransaction = transactionRepository.findByStripePaymentIntentId(paymentIntentId);
                if (existingTransaction.isPresent()) {
                    Transaction transaction = existingTransaction.get();
                    transaction.setPaymentStatus("COMPLETED");
                    transactionRepository.save(transaction);
                    log.info("Updated payment status for transaction ID: {}", transaction.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error handling payment intent: {}", e.getMessage());
        }
    }
    
    /**
     * Extracts a payment intent ID from a Stripe event.
     * <p>
     * This method attempts to retrieve the payment intent ID from the event data
     * by deserializing the Stripe object.
     * 
     * @param event Event - The Stripe event object that might contain a PaymentIntent
     * @return String - The payment intent ID if found, null otherwise
     */
    private String extractPaymentIntentId(Event event) {
        try {
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            if (deserializer.getObject().isPresent()) {
                StripeObject stripeObject = deserializer.getObject().get();
                if (stripeObject instanceof PaymentIntent) {
                    return ((PaymentIntent) stripeObject).getId();
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting payment intent ID: {}", e.getMessage());
        }
        return null;
    }
}
