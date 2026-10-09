package com.market.spacecatmarket.application;

import com.market.spacecatmarket.application.catalog.CatalogProduct;
import com.market.spacecatmarket.application.catalog.ProductCatalogClient;
import com.market.spacecatmarket.domain.Product;
import com.market.spacecatmarket.domain.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {
    private final ProductRepository repository;
    private final ProductCatalogClient catalogClient;

    public ProductService(ProductRepository repository, ProductCatalogClient catalogClient) {
        this.repository = repository;
        this.catalogClient = catalogClient;
    }

    public Product create(Product product) {
        repository.add(product);
        return product;
    }

    public ProductPage findAll(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
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

    public CatalogProduct findExternalProduct(int id) {
        return catalogClient.findById(id);
    }
}
