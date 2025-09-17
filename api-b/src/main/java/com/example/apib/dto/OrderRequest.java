package com.example.apib.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrderRequest(@Valid @NotEmpty List<OrderItemRequest> items) {
}
