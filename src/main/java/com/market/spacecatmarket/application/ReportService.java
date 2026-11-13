package com.market.spacecatmarket.application;

import com.market.spacecatmarket.infrastructure.persistence.jpa.*;
import com.market.spacecatmarket.infrastructure.persistence.entity.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReportService {
    private final ReportJpaRepository repository;
    public ReportService(ReportJpaRepository repository) { this.repository = repository; }
    public record Revenue(BigDecimal revenue, long unitsSold, long orderCount) { }

    public List<CategorySalesProjection> categorySales(Instant from, Instant to) {
        validate(from, to);
        return repository.categorySales(from, to, OrderStatus.COMPLETED);
    }
    public Revenue revenue(Instant from, Instant to) {
        validate(from, to);
        RevenueProjection row = repository.revenue(from, to, OrderStatus.COMPLETED);
        return new Revenue(row.getRevenue() == null ? BigDecimal.ZERO : row.getRevenue(),
            row.getUnitsSold() == null ? 0 : row.getUnitsSold(), row.getOrderCount());
    }
    public List<CustomerSalesProjection> topCustomers(Instant from, Instant to) {
        validate(from, to);
        return repository.topCustomers(from, to, OrderStatus.COMPLETED);
    }
    public List<UnsoldProductProjection> unsoldProducts(Instant from, Instant to) {
        validate(from, to);
        return repository.unsoldProducts(from, to, OrderStatus.COMPLETED);
    }
    private void validate(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) throw new IllegalArgumentException("Report requires from < to");
    }
}
