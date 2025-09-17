package com.example.apia.config;

import com.example.common.domain.catalog.Product;
import com.example.common.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;

    public DataInitializer(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() == 0) {
            List<Product> products = List.of(
                    createProduct("Laptop", 1500000, 20),
                    createProduct("Monitor", 350000, 50),
                    createProduct("Mechanical Keyboard", 120000, 100)
            );
            productRepository.saveAll(products);
        }
    }

    private Product createProduct(String name, int price, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(price);
        product.setStock(stock);
        return product;
    }
}
