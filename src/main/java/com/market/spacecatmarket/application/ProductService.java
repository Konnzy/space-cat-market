package com.market.spacecatmarket.application;

import com.market.spacecatmarket.application.dto.PaginationRequest;
import com.market.spacecatmarket.domain.Product;
import com.market.spacecatmarket.infrastructure.persistence.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.market.spacecatmarket.application.dto.ProductFilter;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Product create(Product product) {
        repository.add(product);
        log.info("Created product id={}", product.id());
        return product;
    }

    public ProductPage findAll(PaginationRequest pagination) {
        return findAll(pagination, ProductFilter.defaults());
    }

    public ProductPage findAll(PaginationRequest pagination, ProductFilter filter) {
        return repository.findPage(pagination, filter);
    }

    public Product findById(UUID id) {
        log.debug("Retrieving product id={}", id);
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product update(Product product) {
        if (!repository.update(product)) {
            throw new ProductNotFoundException(product.id());
        }
        log.info("Updated product id={}", product.id());
        return product;
    }

    @Transactional
    public void deleteById(UUID id) {
        if (!repository.deleteById(id)) {
            throw new ProductNotFoundException(id);
        }
        log.info("Archived product id={}", id);
    }
}
