package com.market.spacecatmarket.application.catalog.dto;

import java.math.BigDecimal;

public record CatalogProductDto(int id, String title, String description, BigDecimal price) {
}
