package com.market.spacecatmarket.application;

import java.util.UUID;

public class InvalidCursorException extends RuntimeException {
    public InvalidCursorException(UUID cursor) {
        super("must refer to an existing product; cursor '%s' is unknown".formatted(cursor));
    }
}
