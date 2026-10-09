package com.market.spacecatmarket.application.catalog;

import java.math.BigDecimal;

public record CatalogProduct(int id, String title, String description, BigDecimal price) {
}
