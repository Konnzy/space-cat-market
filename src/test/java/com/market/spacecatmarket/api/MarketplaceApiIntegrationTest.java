package com.market.spacecatmarket.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MarketplaceApiIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void completePurchaseUsesRealJpaServicesAndReturnsProjectedSales() throws Exception {
        String productJson = mvc.perform(post("/api/v1/products").contentType(APPLICATION_JSON).content("""
            {"name":"Star Yarn","description":"A cosmic toy","price":19.99,
             "category":"TOYS","sellerEmail":"seller@example.com"}
            """))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String productId = mapper.readTree(productJson).get("id").asText();
        mvc.perform(get("/api/v1/products/" + productId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Star Yarn"));
        mvc.perform(post("/api/v1/products/" + productId + "/stock")
            .contentType(APPLICATION_JSON).content("""
                {"quantity":10}
                """))
            .andExpect(status().isOk());
        String customerJson = mvc.perform(post("/api/v1/customers").contentType(APPLICATION_JSON).content("""
            {"name":"Cosmo","email":"cosmo@example.com"}
            """))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long customerId = mapper.readTree(customerJson).get("id").asLong();
        String orderJson = mvc.perform(post("/api/v1/orders").contentType(APPLICATION_JSON).content("""
            {"customerId":%d,"items":[{"productId":"%s","quantity":2}]}
            """.formatted(customerId, productId)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(39.98))
            .andReturn().getResponse().getContentAsString();
        var order = mapper.readTree(orderJson);
        mvc.perform(get("/api/v1/orders/by-number/" + order.get("orderNumber").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(put("/api/v1/orders/" + order.get("id").asLong() + "/status")
            .contentType(APPLICATION_JSON).content("""
                {"status":"COMPLETED"}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/reports/popular-products")
            .param("from", "2000-01-01T00:00:00Z").param("to", "2100-01-01T00:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productId").value(productId))
            .andExpect(jsonPath("$[0].productName").value("Star Yarn"))
            .andExpect(jsonPath("$[0].unitsSold").value(2))
            .andExpect(jsonPath("$[0].price").doesNotExist());
        mvc.perform(delete("/api/v1/products/" + productId)).andExpect(status().isConflict());
    }
}
