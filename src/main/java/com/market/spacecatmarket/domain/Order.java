package com.market.spacecatmarket.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Order(UUID id, List<Product> products) {

    public Order {
        Objects.requireNonNull(id, "Order ID is required");
        products = List.copyOf(products);
        if (products.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one product");
        }
    }

    public BigDecimal totalPrice() {
        return products.stream().map(Product::price).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
