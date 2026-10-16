package com.market.spacecatmarket.infrastructure.persistence;

import com.market.spacecatmarket.domain.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    void add(Product product);

    List<Product> findAll();

    Optional<Product> findById(UUID id);

    boolean update(Product product);

    boolean deleteById(UUID id);
}
