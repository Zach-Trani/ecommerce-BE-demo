package com.printed_parts.spring_boot.modules.product.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.printed_parts.spring_boot.modules.product.entity.Product;
import com.printed_parts.spring_boot.modules.product.repository.ProductRepository;
import org.springframework.web.bind.annotation.RestController;

// The product controller handles access to our database entity
@RestController
public class ProductController {

	// injects the repo into the controller
	@Autowired
	private ProductRepository repository;

	// not currently being used - no add product form in the front end
	@PostMapping("/product")
	public Product addProduct(@RequestBody Product product){
		return repository.save(product);
	}

	@GetMapping("/products")
	public List<Product> getProducts(){
		return repository.findAll();
	}

}
