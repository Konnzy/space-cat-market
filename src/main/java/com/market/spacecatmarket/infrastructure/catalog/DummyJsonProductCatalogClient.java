package com.market.spacecatmarket.infrastructure.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.market.spacecatmarket.application.catalog.CatalogProduct;
import com.market.spacecatmarket.application.catalog.CatalogProductNotFoundException;
import com.market.spacecatmarket.application.catalog.CatalogUnavailableException;
import com.market.spacecatmarket.application.catalog.ProductCatalogClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Component
public class DummyJsonProductCatalogClient implements ProductCatalogClient {
    private final RestClient restClient;

    public DummyJsonProductCatalogClient(@Qualifier("catalogRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public CatalogProduct findById(int id) {
        try {
            DummyJsonProduct response = restClient.get().uri("/products/{id}", id)
                    .retrieve().body(DummyJsonProduct.class);
            if (response == null || response.id() == null || response.id() != id
                    || response.title() == null || response.title().isBlank()
                    || response.description() == null || response.price() == null
                    || response.price().signum() < 0) {
                throw new CatalogUnavailableException("External catalog returned an invalid product.");
            }
            return new CatalogProduct(response.id(), response.title(), response.description(), response.price());
        } catch (HttpClientErrorException.NotFound exception) {
            throw new CatalogProductNotFoundException(id);
        } catch (RestClientException exception) {
            throw new CatalogUnavailableException("External catalog is temporarily unavailable.", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DummyJsonProduct(Integer id, String title, String description, BigDecimal price) {
    }
}
