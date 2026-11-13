package com.market.spacecatmarket.application.dto;

import com.market.spacecatmarket.domain.Category;
import java.math.BigDecimal;

public record ProductFilter(String name, Category category, BigDecimal minPrice, BigDecimal maxPrice,
                            Boolean inStock, Sort sort, Direction direction) {
    public enum Sort { ID, NAME, PRICE }
    public enum Direction { ASC, DESC }

    public ProductFilter {
        name = name == null || name.isBlank() ? null : name.trim();
        sort = sort == null ? Sort.ID : sort;
        direction = direction == null ? Direction.ASC : direction;
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) {
            throw new IllegalArgumentException("Prices must be non-negative and minPrice <= maxPrice");
        }
    }

    public static ProductFilter defaults() { return new ProductFilter(null, null, null, null, null, null, null); }
}
