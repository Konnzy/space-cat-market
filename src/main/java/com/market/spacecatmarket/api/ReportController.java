package com.market.spacecatmarket.api;

import com.market.spacecatmarket.application.ReportService;
import com.market.spacecatmarket.domain.Category;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService reports;
    public ReportController(ReportService reports) { this.reports = reports; }
    public record CategorySales(Category category, String categoryName, Long unitsSold, BigDecimal revenue) { }
    public record CustomerSales(Long customerId, String customerName, Long orderCount, Long unitsSold, BigDecimal revenue) { }
    public record UnsoldProduct(UUID productId, String productName, Category category) { }

    @GetMapping("/category-sales")
    public List<CategorySales> categorySales(@RequestParam Instant from, @RequestParam Instant to) {
        return reports.categorySales(from, to).stream()
            .map(r -> new CategorySales(r.getCategory(), r.getCategoryName(), r.getUnitsSold(), r.getRevenue())).toList();
    }
    @GetMapping("/revenue")
    public ReportService.Revenue revenue(@RequestParam Instant from, @RequestParam Instant to) {
        return reports.revenue(from, to);
    }
    @GetMapping("/top-customers")
    public List<CustomerSales> topCustomers(@RequestParam Instant from, @RequestParam Instant to) {
        return reports.topCustomers(from, to).stream()
            .map(r -> new CustomerSales(r.getCustomerId(), r.getCustomerName(), r.getOrderCount(), r.getUnitsSold(), r.getRevenue())).toList();
    }
    @GetMapping("/unsold-products")
    public List<UnsoldProduct> unsoldProducts(@RequestParam Instant from, @RequestParam Instant to) {
        return reports.unsoldProducts(from, to).stream()
            .map(r -> new UnsoldProduct(r.getProductId(), r.getProductName(), r.getCategory())).toList();
    }
}
