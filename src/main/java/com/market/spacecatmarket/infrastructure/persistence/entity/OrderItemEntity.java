package com.market.spacecatmarket.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_details")
public class OrderItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_detail_seq")
    @SequenceGenerator(name = "order_detail_seq", sequenceName = "order_detail_seq", allocationSize = 1)
    @Column(name = "order_detail_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItemEntity() { }

    public Long getId() { return id; }

    public OrderEntity getOrder() { return order; }

    public ProductEntity getProduct() { return product; }

    public int getQuantity() { return quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }

    public OrderItemEntity(OrderEntity order, ProductEntity product, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        this.order = order;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = product.getPrice();
    }
}
