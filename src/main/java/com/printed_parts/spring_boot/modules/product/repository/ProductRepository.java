package com.printed_parts.spring_boot.modules.product.repository;

import com.printed_parts.spring_boot.modules.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product,Integer> {
}
