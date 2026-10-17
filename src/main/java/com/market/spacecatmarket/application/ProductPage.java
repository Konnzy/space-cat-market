package com.market.spacecatmarket.application;

import com.market.spacecatmarket.domain.Product;

import java.util.List;
import java.util.UUID;

public record ProductPage(List<Product> content, int page, int size, long totalElements, UUID nextCursor) {
    public ProductPage {
        content = List.copyOf(content);
    }
}
