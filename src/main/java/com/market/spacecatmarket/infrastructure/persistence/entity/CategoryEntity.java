package com.market.spacecatmarket.infrastructure.persistence.entity;

import jakarta.persistence.*;
import com.market.spacecatmarket.domain.Category;

@Entity
@Table(name = "categories")
public class CategoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "category_seq")
    @SequenceGenerator(name = "category_seq", sequenceName = "category_seq", allocationSize = 1)
    @Column(name = "category_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 32)
    private Category code;

    @Column(name = "category_name", nullable = false, length = 100)
    private String name;

    protected CategoryEntity() { }

    public Long getId() { return id; }

    public Category getCode() { return code; }

    public String getName() { return name; }

    public CategoryEntity(Category code, String name) {
        this.code = java.util.Objects.requireNonNull(code);
        rename(name);
    }
    public void rename(String name) {
        if (name == null || name.isBlank() || name.length() > 100) throw new IllegalArgumentException("Invalid category name");
        this.name = name.trim();
    }
}
