package com.market.spacecatmarket.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Cart(UUID id, List<Product> products) {

    public Cart {
        Objects.requireNonNull(id, "Cart ID is required");
        products = List.copyOf(products);
    }
}
