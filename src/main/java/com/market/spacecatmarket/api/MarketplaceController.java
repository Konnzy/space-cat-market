package com.market.spacecatmarket.api;

import com.market.spacecatmarket.application.*;
import com.market.spacecatmarket.domain.Category;
import com.market.spacecatmarket.infrastructure.persistence.entity.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class MarketplaceController {
    private final CategoryService categories;
    private final CustomerService customers;
    private final OrderService orders;
    public MarketplaceController(CategoryService categories, CustomerService customers, OrderService orders) {
        this.categories = categories;
        this.customers = customers;
        this.orders = orders;
    }
    public record CategoryRequest(@NotNull Category code, @NotBlank @Size(max = 100) String name) { }
    public record CategoryName(@NotBlank @Size(max = 100) String name) { }
    public record CategoryView(Long id, Category code, String name) { }
    public record CustomerRequest(@NotBlank @Size(max = 100) String name,
                                  @NotBlank @Email @Size(max = 254) String email) { }
    public record CustomerView(Long id, String name, String email) { }
    public record OrderLine(@NotNull UUID productId, @Positive int quantity) { }
    public record OrderRequest(@NotNull @Positive Long customerId, @NotEmpty List<@NotNull @Valid OrderLine> items) { }
    public record StatusRequest(@NotNull OrderStatus status) { }
    public record SalesView(UUID productId, String productName, Long unitsSold) { }
    public record StockRequest(@Positive int quantity) { }

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return categories.findAll().stream().map(c -> new CategoryView(c.getId(), c.getCode(), c.getName())).toList();
    }
    @GetMapping("/categories/{id}")
    public CategoryView category(@PathVariable long id) {
        var c = categories.findById(id); return new CategoryView(c.getId(), c.getCode(), c.getName());
    }
    @PostMapping("/categories")
    public ResponseEntity<CategoryView> createCategory(@Valid @RequestBody CategoryRequest request) {
        var c = categories.create(request.code(), request.name());
        return ResponseEntity.created(URI.create("/api/v1/categories/" + c.getId()))
            .body(new CategoryView(c.getId(), c.getCode(), c.getName()));
    }
    @PutMapping("/categories/{id}")
    public CategoryView updateCategory(@PathVariable long id, @Valid @RequestBody CategoryName request) {
        var c = categories.update(id, request.name()); return new CategoryView(c.getId(), c.getCode(), c.getName());
    }
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable long id) {
        categories.delete(id); return ResponseEntity.noContent().build();
    }
    @PostMapping("/customers")
    public ResponseEntity<CustomerView> createCustomer(@Valid @RequestBody CustomerRequest request) {
        var c = customers.create(request.name(), request.email());
        return ResponseEntity.created(URI.create("/api/v1/customers/" + c.getId()))
            .body(new CustomerView(c.getId(), c.getName(), c.getEmail()));
    }
    @GetMapping("/customers/{id}")
    public CustomerView customer(@PathVariable long id) {
        var c = customers.findById(id); return new CustomerView(c.getId(), c.getName(), c.getEmail());
    }
    @PostMapping("/orders")
    public ResponseEntity<OrderService.OrderView> createOrder(@Valid @RequestBody OrderRequest request) {
        var order = orders.create(request.customerId(), request.items().stream()
            .map(i -> new OrderService.Line(i.productId(), i.quantity())).toList());
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(order);
    }
    @GetMapping("/orders")
    public List<OrderService.OrderView> orders() { return orders.findAll(); }
    @GetMapping("/orders/{id}")
    public OrderService.OrderView order(@PathVariable long id) { return orders.findById(id); }
    @GetMapping("/orders/by-number/{number}")
    public OrderService.OrderView orderByNumber(@PathVariable String number) { return orders.findByOrderNumber(number); }
    @PutMapping("/orders/{id}/status")
    public OrderService.OrderView updateStatus(@PathVariable long id, @Valid @RequestBody StatusRequest request) {
        return orders.updateStatus(id, request.status());
    }
    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable long id) {
        orders.delete(id); return ResponseEntity.noContent().build();
    }
    @PostMapping("/products/{id}/stock")
    public int addStock(@PathVariable UUID id, @Valid @RequestBody StockRequest request) {
        return orders.addStock(id, request.quantity());
    }
    @GetMapping("/reports/popular-products")
    public List<SalesView> popularProducts(@RequestParam Instant from, @RequestParam Instant to) {
        return orders.popularProducts(from, to).stream()
            .map(p -> new SalesView(p.getProductId(), p.getProductName(), p.getUnitsSold())).toList();
    }
}
