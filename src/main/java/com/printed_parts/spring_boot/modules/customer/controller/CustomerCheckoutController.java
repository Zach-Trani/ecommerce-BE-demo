package com.printed_parts.spring_boot.modules.customer.controller;

import com.printed_parts.spring_boot.modules.customer.entity.CustomerInformation;
import com.printed_parts.spring_boot.modules.customer.repository.CustomerRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@RestController
public class CustomerCheckoutController {

    private static final Logger logger = Logger.getLogger(CustomerCheckoutController.class.getName());
    
    @Autowired
    private CustomerRepository customerRepository;

    @PostMapping("/checkout")
    public ResponseEntity<?> processCustomerCheckout(@RequestBody CustomerInformation customerInfo) {
        try {
            logger.info("Processing customer checkout for: " + customerInfo.getEmail());
            
            // Check if customer with this email already exists
            Optional<CustomerInformation> existingCustomer = customerRepository.findByEmail(customerInfo.getEmail());
            
            CustomerInformation savedCustomer;
            if (existingCustomer.isPresent()) {
                // Update existing customer information
                CustomerInformation customer = existingCustomer.get();
                customer.setFullName(customerInfo.getFullName());
                customer.setCountry(customerInfo.getCountry());
                customer.setAddress(customerInfo.getAddress());
                customer.setApartment(customerInfo.getApartment());
                customer.setCity(customerInfo.getCity());
                customer.setState(customerInfo.getState());
                customer.setZipCode(customerInfo.getZipCode());
                
                savedCustomer = customerRepository.save(customer);
                logger.info("Updated existing customer with ID: " + savedCustomer.getCustomerId());
            } else {
                // Save new customer information
                savedCustomer = customerRepository.save(customerInfo);
                logger.info("Created new customer with ID: " + savedCustomer.getCustomerId());
            }
            
            // Create response with customer ID
            Map<String, Object> response = new HashMap<>();
            response.put("customerId", savedCustomer.getCustomerId());
            response.put("email", savedCustomer.getEmail());
            response.put("message", "Customer information processed successfully");
            
            return new ResponseEntity<>(response, HttpStatus.OK);
            
        } catch (Exception e) {
            logger.severe("Error processing customer checkout: " + e.getMessage());
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to process customer information");
            errorResponse.put("message", e.getMessage());
            return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
