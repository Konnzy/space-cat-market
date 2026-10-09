package com.market.spacecatmarket.api.dto;

import java.math.BigDecimal;

public record CatalogProductResponse(int id, String title, String description, BigDecimal price) {
}
