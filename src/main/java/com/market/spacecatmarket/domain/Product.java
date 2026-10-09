package com.market.spacecatmarket.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record Product(UUID id, String name, String description, BigDecimal price,
                      Category category, String sellerEmail) {

    public Product {
        Objects.requireNonNull(id, "Product ID is required");
        Objects.requireNonNull(name, "Product name is required");
        Objects.requireNonNull(description, "Product description is required");
        Objects.requireNonNull(price, "Product price is required");
        Objects.requireNonNull(category, "Product category is required");
        Objects.requireNonNull(sellerEmail, "Seller email is required");
        if (name.isBlank() || price.signum() <= 0) {
            throw new IllegalArgumentException("Product name must not be blank and price must be greater than 0");
        }
    }
}
