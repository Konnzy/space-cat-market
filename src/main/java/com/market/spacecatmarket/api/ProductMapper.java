package com.market.spacecatmarket.api;

import com.market.spacecatmarket.api.dto.CatalogProductResponse;
import com.market.spacecatmarket.api.dto.ProductPageResponse;
import com.market.spacecatmarket.api.dto.ProductRequest;
import com.market.spacecatmarket.api.dto.ProductResponse;
import com.market.spacecatmarket.application.ProductPage;
import com.market.spacecatmarket.application.catalog.CatalogProduct;
import com.market.spacecatmarket.domain.Product;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProductMapper {

    public Product toDomain(ProductRequest request) {
        return toDomain(UUID.randomUUID(), request);
    }

    public Product toDomain(UUID id, ProductRequest request) {
        return new Product(id, request.name(), request.description(), request.price(),
                request.category(), request.sellerEmail());
    }

    public ProductResponse toDto(Product product) {
        return new ProductResponse(product.id(), product.name(), product.description(), product.price(),
                product.category(), product.sellerEmail());
    }

    public ProductPageResponse toDto(ProductPage page) {
        return new ProductPageResponse(page.content().stream().map(this::toDto).toList(),
                page.page(), page.size(), page.totalElements());
    }

    public CatalogProductResponse toDto(CatalogProduct product) {
        return new CatalogProductResponse(product.id(), product.title(), product.description(), product.price());
    }
}
