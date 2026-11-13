package com.market.spacecatmarket.infrastructure.persistence.entity;

import jakarta.persistence.*;


@Entity
@Table(name = "customers")
public class CustomerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", sequenceName = "customer_seq", allocationSize = 1)
    @Column(name = "customer_id")
    private Long id;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    protected CustomerEntity() { }

    public Long getId() { return id; }

    public String getName() { return name; }

    public String getEmail() { return email; }

    public CustomerEntity(String name, String email) {
        if (name == null || name.isBlank() || email == null || email.isBlank()) throw new IllegalArgumentException("Customer name and email are required");
        this.name = name.trim();
        this.email = email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
