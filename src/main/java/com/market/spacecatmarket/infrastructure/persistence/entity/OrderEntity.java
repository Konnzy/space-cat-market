package com.market.spacecatmarket.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import org.hibernate.annotations.NaturalId;

@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(name = "order_seq", sequenceName = "order_seq", allocationSize = 1)
    @Column(name = "order_id")
    private Long id;

    @NaturalId
    @Column(name = "order_number", nullable = false, unique = true, updatable = false, length = 64)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItemEntity> items;

    protected OrderEntity() { }

    public Long getId() { return id; }

    public String getOrderNumber() { return orderNumber; }

    public CustomerEntity getCustomer() { return customer; }

    public Instant getCreatedAt() { return createdAt; }

    public OrderStatus getStatus() { return status; }

    public List<OrderItemEntity> getItems() { return items; }

    public OrderEntity(String orderNumber, CustomerEntity customer, Instant createdAt) {
        this.orderNumber = orderNumber;
        this.customer = customer;
        this.createdAt = createdAt;
        this.status = OrderStatus.NEW;
        this.items = new ArrayList<>();
    }
    public void addItem(ProductEntity product, int quantity) {
        items.add(new OrderItemEntity(this, product, quantity));
    }
    public void changeStatus(OrderStatus status) {
        if (status == null || (this.status != status && (this.status != OrderStatus.NEW || status == OrderStatus.NEW))) {
            throw new IllegalArgumentException("Only NEW orders may be completed or cancelled");
        }
        this.status = status;
    }
}
