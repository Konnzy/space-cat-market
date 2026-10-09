package com.market.spacecatmarket.api;

import com.market.spacecatmarket.api.dto.CatalogProductResponse;
import com.market.spacecatmarket.api.dto.ProductPageResponse;
import com.market.spacecatmarket.api.dto.ProductRequest;
import com.market.spacecatmarket.api.dto.ProductResponse;
import com.market.spacecatmarket.application.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;
    private final ProductMapper mapper;

    public ProductController(ProductService service, ProductMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse product = mapper.toDto(service.create(mapper.toDomain(request)));
        return ResponseEntity.created(URI.create("/api/products/" + product.id())).body(product);
    }

    @GetMapping
    public ProductPageResponse findAll(@RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return mapper.toDto(service.findAll(page, size));
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable UUID id) {
        return mapper.toDto(service.findById(id));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return mapper.toDto(service.update(mapper.toDomain(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/external/{externalId}")
    public CatalogProductResponse findExternalProduct(@PathVariable @Min(1) int externalId) {
        return mapper.toDto(service.findExternalProduct(externalId));
    }
}
