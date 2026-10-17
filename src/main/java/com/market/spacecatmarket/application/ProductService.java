package com.market.spacecatmarket.application;

import com.market.spacecatmarket.application.dto.PaginationRequest;
import com.market.spacecatmarket.domain.Product;
import com.market.spacecatmarket.infrastructure.persistence.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public Product create(Product product) {
        repository.add(product);
        log.info("Created product id={}", product.id());
        return product;
    }

    public ProductPage findAll(PaginationRequest pagination) {
        int page = pagination.cursor() == null ? pagination.page() : 0;
        int size = pagination.size();
        log.debug("Listing products: page={}, size={}, cursor={}", page, size, pagination.cursor());
        List<Product> products = repository.findAll();
        int startIndex = 0;
        if (pagination.cursor() != null) {
            int cursorIndex = -1;
            for (int index = 0; index < products.size(); index++) {
                if (products.get(index).id().equals(pagination.cursor())) {
                    cursorIndex = index;
                    break;
                }
            }
            if (cursorIndex < 0) {
                throw new InvalidCursorException(pagination.cursor());
            }
            startIndex = cursorIndex + 1;
        }
        long offset = startIndex + (long) page * size;
        int fromIndex = (int) Math.min(offset, products.size());
        int toIndex = (int) Math.min(offset + size, products.size());
        UUID nextCursor = toIndex > fromIndex && toIndex < products.size() ? products.get(toIndex - 1).id() : null;
        log.debug("Listed products: returned={}, totalElements={}, nextCursor={}",
                toIndex - fromIndex, products.size(), nextCursor);
        return new ProductPage(products.subList(fromIndex, toIndex), page, size, products.size(), nextCursor);
    }

    public Product findById(UUID id) {
        log.debug("Retrieving product id={}", id);
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Product update(Product product) {
        if (!repository.update(product)) {
            throw new ProductNotFoundException(product.id());
        }
        log.info("Updated product id={}", product.id());
        return product;
    }

    public void deleteById(UUID id) {
        if (!repository.deleteById(id)) {
            throw new ProductNotFoundException(id);
        }
        log.info("Deleted product id={}", id);
    }
}
