package com.market.spacecatmarket.application;

import com.market.spacecatmarket.domain.Product;
import com.market.spacecatmarket.domain.ProductRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Service
@Validated
public class ProductService {
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public Product create(Product product) {
        repository.add(product);
        return product;
    }

    public ProductPage findAll(@Min(0) int page, @Min(1) @Max(100) int size) {
        List<Product> products = repository.findAll();
        long offset = (long) page * size;
        int fromIndex = (int) Math.min(offset, products.size());
        int toIndex = (int) Math.min(offset + size, products.size());
        return new ProductPage(products.subList(fromIndex, toIndex), page, size, products.size());
    }

    public Product findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Product update(Product product) {
        if (!repository.update(product)) {
            throw new ProductNotFoundException(product.id());
        }
        return product;
    }

    public void deleteById(UUID id) {
        if (!repository.deleteById(id)) {
            throw new ProductNotFoundException(id);
        }
    }
}
