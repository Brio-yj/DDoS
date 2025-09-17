package com.example.apib.service;

import com.example.apib.dto.OrderRequest;
import com.example.apib.dto.OrderResponse;
import com.example.common.domain.catalog.Order;
import com.example.common.domain.catalog.OrderItem;
import com.example.common.domain.catalog.OrderStatus;
import com.example.common.domain.catalog.Product;
import com.example.common.domain.user.User;
import com.example.common.repository.OrderRepository;
import com.example.common.repository.ProductRepository;
import com.example.common.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderService(UserRepository userRepository, ProductRepository productRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse createOrder(Long userId, OrderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.CREATED);

        int totalPrice = 0;
        List<OrderResponse.OrderSummaryItem> summaryItems = new ArrayList<>();
        for (var itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemRequest.productId()));
            if (product.getStock() < itemRequest.quantity()) {
                throw new IllegalArgumentException("Insufficient stock for product " + product.getName());
            }
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.quantity());
            orderItem.setUnitPrice(product.getPrice());
            order.addItem(orderItem);
            totalPrice += product.getPrice() * itemRequest.quantity();
            summaryItems.add(new OrderResponse.OrderSummaryItem(product.getId(), product.getName(), itemRequest.quantity(), product.getPrice()));
        }
        order.setTotalPrice(totalPrice);
        Order saved = orderRepository.save(order);

        return new OrderResponse(saved.getId(), saved.getStatus().name(), saved.getTotalPrice(), saved.getCreatedAt(), summaryItems);
    }
}
