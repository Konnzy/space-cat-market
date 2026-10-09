package com.market.spacecatmarket.application.catalog;

import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final ProductCatalogClient catalogClient;

    public CatalogService(ProductCatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    public CatalogProduct findById(int id) {
        return catalogClient.findById(id);
    }
}
