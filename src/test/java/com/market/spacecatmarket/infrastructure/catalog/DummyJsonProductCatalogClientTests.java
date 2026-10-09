package com.market.spacecatmarket.infrastructure.catalog;

import com.market.spacecatmarket.application.catalog.CatalogProduct;
import com.market.spacecatmarket.application.catalog.CatalogProductNotFoundException;
import com.market.spacecatmarket.application.catalog.CatalogUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DummyJsonProductCatalogClientTests {
    private MockRestServiceServer server;
    private DummyJsonProductCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://catalog.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DummyJsonProductCatalogClient(builder.build());
    }

    @Test
    void callsTheProviderAndMapsOnlyTheSelectedFields() {
        server.expect(requestTo("https://catalog.test/products/1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":1,"title":"Provider product","description":"Description","price":9.99,
                         "category":"beauty","stock":20,"images":["image.png"]}
                        """, MediaType.APPLICATION_JSON));
        CatalogProduct product = client.findById(1);
        assertThat(product).isEqualTo(new CatalogProduct(1, "Provider product", "Description", new BigDecimal("9.99")));
        server.verify();
    }

    @Test
    void mapsProvider404ToProductNotFound() {
        server.expect(requestTo("https://catalog.test/products/1")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.findById(1)).isInstanceOf(CatalogProductNotFoundException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 429, 500, 503})
    void mapsOtherProviderHttpFailuresToUnavailable(int status) {
        server.expect(requestTo("https://catalog.test/products/1")).andRespond(withStatus(HttpStatus.valueOf(status)));
        assertThatThrownBy(() -> client.findById(1)).isInstanceOf(CatalogUnavailableException.class)
                .hasMessage("External catalog is temporarily unavailable.");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{}", "{\"id\":1,\"title\":\"Product\"}",
            "{\"id\":2,\"title\":\"Product\",\"description\":\"Description\",\"price\":1}",
            "{\"id\":1,\"title\":\" \u0020\",\"description\":\"Description\",\"price\":1}",
            "{\"id\":1,\"title\":\"Product\",\"description\":\"Description\",\"price\":-1}"})
    void rejectsMalformedEmptyOrIncompleteResponses(String body) {
        server.expect(requestTo("https://catalog.test/products/1")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.findById(1)).isInstanceOf(CatalogUnavailableException.class);
        server.verify();
    }

    @Test
    void mapsTransportFailuresToUnavailable() {
        server.expect(requestTo("https://catalog.test/products/1")).andRespond(withException(new IOException("Connection failed")));
        assertThatThrownBy(() -> client.findById(1)).isInstanceOf(CatalogUnavailableException.class)
                .hasMessage("External catalog is temporarily unavailable.");
        server.verify();
    }
}
