package com.market.spacecatmarket.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

public record PaginationRequest(@Min(0) Integer page, @Min(1) @Max(100) Integer size, UUID cursor) {
    public PaginationRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
