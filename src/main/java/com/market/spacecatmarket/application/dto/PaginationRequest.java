package com.market.spacecatmarket.application.dto;

import java.util.UUID;

public record PaginationRequest(Integer page, Integer size, UUID cursor) {
    public PaginationRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
