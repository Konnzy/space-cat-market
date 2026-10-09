package com.market.spacecatmarket.api.error;

public record ValidationError(String field, String message) {
}
