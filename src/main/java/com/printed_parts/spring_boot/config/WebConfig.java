package com.printed_parts.spring_boot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;
import java.io.IOException;

// In addition to the below CORS settings - Azure has App Service level CORS settings for the backend deployment
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    /* 
    // We're handling CORS at the controller level instead of globally
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                    "http://localhost:5173", 
                    "https://lively-moss-09bc30c10.4.azurestaticapps.net"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Access-Control-Allow-Credentials")
                .allowCredentials(true)
                .maxAge(3600);
    }
    */

    // We can handle backend routes that exist in Spring Boot (@GetMapping's or @PostMapping's) through controllers.
    // Client → Spring Boot (processes request) → Returns JSON/data

    // But, we have additional client side routing (UI pages) that don't have actual endpoints in Spring Boot ("/success" or "/cancel").
    // addResourceHandlers tells Spring Boot to let your client side router (currently React Router) handle routes that are not found.
    // Client → Spring Boot (no endpoint found) → Serves index.html → React Router takes over
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**") // handle all requests
            .addResourceLocations("classpath:/static/") // look for static files first
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String resourcePath, Resource location) throws IOException {
                    Resource requestedResource = location.createRelative(resourcePath);
                    return requestedResource.exists() && requestedResource.isReadable() ? requestedResource
                        : location.createRelative("index.html");
                }
            });
    }
}