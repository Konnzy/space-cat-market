package com.market.spacecatmarket.api.dto;

import java.util.List;

public record ProductPageResponse(List<ProductResponse> content, int page, int size, long totalElements) {
}
