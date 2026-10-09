package com.market.spacecatmarket.api;

import com.market.spacecatmarket.api.dto.CatalogProductResponse;
import com.market.spacecatmarket.api.dto.ProductPageResponse;
import com.market.spacecatmarket.api.dto.ProductRequest;
import com.market.spacecatmarket.api.dto.ProductResponse;
import com.market.spacecatmarket.application.ProductPage;
import com.market.spacecatmarket.application.catalog.CatalogProduct;
import com.market.spacecatmarket.domain.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR, imports = UUID.class)
public interface ProductMapper {

    @Mapping(target = "id", expression = "java(UUID.randomUUID())")
    Product toDomain(ProductRequest request);

    @Mapping(target = "id", source = "id")
    Product toDomain(UUID id, ProductRequest request);

    ProductResponse toDto(Product product);

    @Mapping(target = "content", expression = "java(page.content().stream().map(this::toDto).toList())")
    ProductPageResponse toDto(ProductPage page);

    CatalogProductResponse toDto(CatalogProduct product);
}
