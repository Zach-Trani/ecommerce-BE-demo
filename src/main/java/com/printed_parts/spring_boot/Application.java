package com.printed_parts.spring_boot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@SpringBootApplication
@RestController
public class Application {

	// injects the repo into the controller
	@Autowired
	private ProductRepository repository;

	@PostMapping("/product")
	public Product addProduct(@RequestBody Product product){
		return repository.save(product);
	}

	@GetMapping("/products")
	public List<Product> getProducts(){
		return repository.findAll();
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
