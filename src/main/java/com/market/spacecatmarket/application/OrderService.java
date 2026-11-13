package com.market.spacecatmarket.application;

import com.market.spacecatmarket.infrastructure.persistence.entity.*;
import com.market.spacecatmarket.infrastructure.persistence.jpa.*;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private final OrderJpaRepository orders;
    private final CustomerJpaRepository customers;
    private final ProductJpaRepository products;
    private final EntityManager entityManager;
    public OrderService(OrderJpaRepository orders, CustomerJpaRepository customers,
                        ProductJpaRepository products, EntityManager entityManager) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.entityManager = entityManager;
    }
    public record Line(UUID productId, int quantity) { }
    public record ItemView(UUID productId, int quantity, BigDecimal unitPrice) { }
    public record OrderView(Long id, String orderNumber, Long customerId, Instant createdAt,
                            OrderStatus status, List<ItemView> items, BigDecimal total) { }

    @Transactional
    public OrderView create(long customerId, List<Line> lines) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("Order must contain at least one item");
        if (lines.stream().anyMatch(l -> l == null || l.productId() == null || l.quantity() <= 0)
                || lines.stream().map(Line::productId).distinct().count() != lines.size()) {
            throw new IllegalArgumentException("Order items must have unique products and positive quantities");
        }
        CustomerEntity customer = customers.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        OrderEntity order = new OrderEntity("CC-" + UUID.randomUUID(), customer, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        // Acquire product locks in a stable order to avoid deadlocks between multi-item orders.
        for (Line line : lines.stream().sorted(Comparator.comparing(Line::productId)).toList()) {
            ProductEntity product = lockedProduct(line.productId());
            product.reserve(line.quantity());
            order.addItem(product, line.quantity());
        }
        return view(orders.saveAndFlush(order));
    }
    public OrderView findById(long id) { return view(required(id)); }
    public List<OrderView> findAll() {
        return orders.findAll(org.springframework.data.domain.Sort.by("id")).stream().map(this::view).toList();
    }
    public OrderView findByOrderNumber(String number) {
        OrderEntity order = entityManager.unwrap(Session.class).bySimpleNaturalId(OrderEntity.class).load(number);
        if (order == null) throw new ResourceNotFoundException("Order", number);
        return view(order);
    }
    @Transactional
    public OrderView updateStatus(long id, OrderStatus status) {
        OrderEntity order = orders.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
        OrderStatus previous = order.getStatus();
        order.changeStatus(status);
        if (previous == OrderStatus.NEW && status == OrderStatus.CANCELLED) restoreStock(order);
        return view(order);
    }
    @Transactional
    public void delete(long id) {
        OrderEntity order = orders.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
        if (order.getStatus() == OrderStatus.COMPLETED) throw new IllegalArgumentException("Completed orders cannot be deleted");
        if (order.getStatus() == OrderStatus.NEW) restoreStock(order);
        orders.delete(order);
    }
    @Transactional
    public int addStock(UUID productId, int quantity) {
        ProductEntity product = lockedProduct(productId);
        product.addStock(quantity);
        return product.getStockQuantity();
    }
    public List<ProductSalesProjection> popularProducts(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) throw new IllegalArgumentException("Report requires from < to");
        return orders.findPopularProducts(from, to, OrderStatus.COMPLETED);
    }
    private OrderEntity required(long id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }
    private ProductEntity lockedProduct(UUID id) {
        return products.lockByPublicId(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
    private void restoreStock(OrderEntity order) {
        order.getItems().stream().sorted(Comparator.comparing(i -> i.getProduct().getPublicId()))
            .forEach(i -> lockedProduct(i.getProduct().getPublicId()).addStock(i.getQuantity()));
    }
    private OrderView view(OrderEntity order) {
        List<ItemView> items = order.getItems().stream()
            .map(i -> new ItemView(i.getProduct().getPublicId(), i.getQuantity(), i.getUnitPrice())).toList();
        BigDecimal total = items.stream().map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderView(order.getId(), order.getOrderNumber(), order.getCustomer().getId(),
            order.getCreatedAt(), order.getStatus(), items, total);
    }
}
