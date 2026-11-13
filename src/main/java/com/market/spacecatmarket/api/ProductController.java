package com.market.spacecatmarket.api;

import com.market.spacecatmarket.api.dto.ProductPageResponse;
import com.market.spacecatmarket.api.dto.ProductRequest;
import com.market.spacecatmarket.api.dto.ProductResponse;
import com.market.spacecatmarket.application.ProductService;
import com.market.spacecatmarket.application.catalog.CatalogService;
import com.market.spacecatmarket.application.catalog.dto.CatalogProductDto;
import com.market.spacecatmarket.application.dto.PaginationRequest;
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

import com.market.spacecatmarket.application.dto.ProductFilter;
import com.market.spacecatmarket.domain.Category;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService productService;
    private final CatalogService catalogService;
    private final ProductMapper productMapper;

    public ProductController(ProductService productService, CatalogService catalogService, ProductMapper productMapper) {
        this.productService = productService;
        this.catalogService = catalogService;
        this.productMapper = productMapper;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse product = productMapper.toProductDto(productService.create(productMapper.toProduct(request)));
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.id())).body(product);
    }

    @GetMapping
    public ProductPageResponse findAll(@RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                       @RequestParam(required = false) UUID cursor,
                                       @RequestParam(required = false) String name,
                                       @RequestParam(required = false) Category category,
                                       @RequestParam(required = false) BigDecimal minPrice,
                                       @RequestParam(required = false) BigDecimal maxPrice,
                                       @RequestParam(required = false) Boolean inStock,
                                       @RequestParam(defaultValue = "ID") ProductFilter.Sort sort,
                                       @RequestParam(defaultValue = "ASC") ProductFilter.Direction direction) {
        return productMapper.toProductPageDto(productService.findAll(new PaginationRequest(page, size, cursor),
                new ProductFilter(name, category, minPrice, maxPrice, inStock, sort, direction)));
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable UUID id) {
        return productMapper.toProductDto(productService.findById(id));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productMapper.toProductDto(productService.update(productMapper.toProduct(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/external/{externalId}")
    public CatalogProductDto findExternalProduct(@PathVariable @Min(1) int externalId) {
        return catalogService.findById(externalId);
    }
}
