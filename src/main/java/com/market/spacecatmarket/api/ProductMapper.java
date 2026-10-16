package com.market.spacecatmarket.api;

import com.market.spacecatmarket.api.dto.ProductPageResponse;
import com.market.spacecatmarket.api.dto.ProductRequest;
import com.market.spacecatmarket.api.dto.ProductResponse;
import com.market.spacecatmarket.application.ProductPage;
import com.market.spacecatmarket.domain.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR, imports = UUID.class)
public interface ProductMapper {

    @Mapping(target = "id", expression = "java(UUID.randomUUID())")
    Product toProduct(ProductRequest request);

    @Mapping(target = "id", source = "id")
    Product toProduct(UUID id, ProductRequest request);

    ProductResponse toProductDto(Product product);

    List<ProductResponse> toProductDtos(List<Product> products);

    ProductPageResponse toProductPageDto(ProductPage page);
}
