package com.market.spacecatmarket.infrastructure.persistence;

import com.market.spacecatmarket.domain.Category;
import com.market.spacecatmarket.domain.Product;
import com.market.spacecatmarket.domain.ProductRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InMemoryProductRepository implements ProductRepository {
    private final Map<UUID, Product> products = new LinkedHashMap<>();

    public InMemoryProductRepository() {
        add(new Product(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Star Antigravity Yarn", "A floating yarn ball for intergalactic cats.",
                new BigDecimal("19.99"), Category.TOYS, "toys@cosmo-cats.market"));
        add(new Product(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "Galaxy Cosmic Milk", "Cosmic milk for long journeys through the galaxy.",
                new BigDecimal("8.50"), Category.FOOD, "food@cosmo-cats.market"));
        add(new Product(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "Comet Collar", "A collar for a cat exploring distant stars.",
                new BigDecimal("12.00"), Category.ACCESSORIES, "gear@cosmo-cats.market"));
    }

    @Override
    public synchronized void add(Product product) {
        if (products.putIfAbsent(product.id(), product) != null) {
            throw new IllegalArgumentException("Product ID already exists");
        }
    }

    @Override
    public synchronized List<Product> findAll() {
        return List.copyOf(products.values());
    }

    @Override
    public synchronized Optional<Product> findById(UUID id) {
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public synchronized boolean update(Product product) {
        return products.replace(product.id(), product) != null;
    }

    @Override
    public synchronized boolean deleteById(UUID id) {
        return products.remove(id) != null;
    }
}
