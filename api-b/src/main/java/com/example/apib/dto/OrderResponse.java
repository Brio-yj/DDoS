package com.example.apib.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long orderId, String status, Integer totalPrice, LocalDateTime createdAt,
                            List<OrderSummaryItem> items) {

    public record OrderSummaryItem(Long productId, String name, Integer quantity, Integer unitPrice) {
    }
}
