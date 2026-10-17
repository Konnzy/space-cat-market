package com.market.spacecatmarket.application;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(UUID id) {
        super("Product '%s' was not found.".formatted(id));
    }
}
