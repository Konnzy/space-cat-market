package com.market.spacecatmarket.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import com.market.spacecatmarket.domain.Product;

@Entity
@Table(name = "products")
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 1)
    @Column(name = "product_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;

    @Column(name = "seller_email", nullable = false, length = 254)
    private String sellerEmail;

    protected ProductEntity() { }

    public Long getId() { return id; }

    public UUID getPublicId() { return publicId; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public BigDecimal getPrice() { return price; }

    public int getStockQuantity() { return stockQuantity; }

    public CategoryEntity getCategory() { return category; }

    public String getSellerEmail() { return sellerEmail; }

    public ProductEntity(Product product, CategoryEntity category) {
        this.publicId = product.id();
        update(product, category);
    }
    public void update(Product product, CategoryEntity category) {
        this.name = product.name();
        this.description = product.description();
        this.price = product.price();
        this.category = category;
        this.sellerEmail = product.sellerEmail();
    }
    public Product toDomain() {
        return new Product(publicId, name, description, price, category.getCode(), sellerEmail);
    }
    public void addStock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Stock addition must be positive");
        stockQuantity = Math.addExact(stockQuantity, quantity);
    }
    public void reserve(int quantity) {
        if (quantity <= 0 || quantity > stockQuantity) throw new IllegalArgumentException("Insufficient stock or invalid quantity");
        stockQuantity -= quantity;
    }
}
