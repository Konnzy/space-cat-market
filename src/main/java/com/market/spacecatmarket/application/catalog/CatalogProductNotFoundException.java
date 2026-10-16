package com.market.spacecatmarket.application.catalog;

public class CatalogProductNotFoundException extends RuntimeException {
    public CatalogProductNotFoundException(int id) {
        super("External catalog product '%s' was not found.".formatted(id));
    }
}
