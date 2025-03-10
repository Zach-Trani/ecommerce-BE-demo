package com.printed_parts.spring_boot.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom filter to handle CORS at a lower level before Spring's default filters.
 * This ensures we don't get duplicate CORS headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        
        // Check if this is a webhook request from Stripe
        String requestURI = request.getRequestURI();
        boolean isStripeWebhook = requestURI.contains("/stripe/webhook");
        
        // Skip CORS processing for Stripe webhooks
        if (isStripeWebhook) {
            // For Stripe webhooks, just continue the filter chain without CORS headers
            chain.doFilter(req, res);
            return;
        }
        
        // Get the origin header
        String origin = request.getHeader("Origin");
        
        // If this is a valid origin, add it to the response
        if (origin != null && (origin.contains("localhost") || origin.contains("azurestaticapps.net"))) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization, Stripe-Signature");
            response.setHeader("Access-Control-Max-Age", "3600");
            
            // For OPTIONS requests, just return 200 OK
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }
        }
        
        chain.doFilter(req, res);
    }
} 