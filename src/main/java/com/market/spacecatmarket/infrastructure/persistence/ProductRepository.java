package com.market.spacecatmarket.infrastructure.persistence;

import com.market.spacecatmarket.domain.Product;

import com.market.spacecatmarket.application.ProductPage;
import com.market.spacecatmarket.application.dto.PaginationRequest;
import com.market.spacecatmarket.application.dto.ProductFilter;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    void add(Product product);

    ProductPage findPage(PaginationRequest pagination, ProductFilter filter);

    Optional<Product> findById(UUID id);

    boolean update(Product product);

    boolean deleteById(UUID id);
}
