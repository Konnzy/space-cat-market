package com.market.spacecatmarket.infrastructure.persistence.jpa;

import com.market.spacecatmarket.domain.Category;
import java.math.BigDecimal;
import java.util.UUID;

public interface RevenueProjection {
    BigDecimal getRevenue();
    Long getUnitsSold();
    Long getOrderCount();
}
