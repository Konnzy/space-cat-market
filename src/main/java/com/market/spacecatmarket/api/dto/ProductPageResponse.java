package com.market.spacecatmarket.api.dto;

import java.util.List;
import java.util.UUID;

public record ProductPageResponse(List<ProductResponse> content, int page, int size, long totalElements, UUID nextCursor) {
    public ProductPageResponse {
        content = List.copyOf(content);
    }
}
