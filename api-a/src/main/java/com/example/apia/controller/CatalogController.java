package com.example.apia.controller;

import com.example.apia.dto.ProductResponse;
import com.example.apia.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api-a")
public class CatalogController {

    private final ProductService productService;

    public CatalogController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/items")
    public ResponseEntity<List<ProductResponse>> items() {
        return ResponseEntity.ok(productService.getCatalog());
    }
}
