package com.market.spacecatmarket.api.dto;

import com.market.spacecatmarket.domain.Category;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(UUID id, String name, String description, BigDecimal price,
                              Category category, String sellerEmail) {
}
