package com.market.spacecatmarket.api;

import com.market.spacecatmarket.application.*;
import com.market.spacecatmarket.domain.Category;
import com.market.spacecatmarket.infrastructure.persistence.entity.*;
import com.market.spacecatmarket.infrastructure.persistence.jpa.ProductSalesProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MarketplaceController.class)
class MarketplaceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CategoryService categories;
    @MockitoBean CustomerService customers;
    @MockitoBean OrderService orders;
    private CategoryEntity category() {
        var c = mock(CategoryEntity.class);
        when(c.getId()).thenReturn(1L);
        when(c.getCode()).thenReturn(Category.TOYS);
        when(c.getName()).thenReturn("Toys");
        return c;
    }
    private OrderService.OrderView order() {
        return new OrderService.OrderView(1L, "CC-test", 2L, Instant.EPOCH, OrderStatus.NEW,
            List.of(new OrderService.ItemView(new UUID(0,1), 2, new BigDecimal("5.00"))), new BigDecimal("10.00"));
    }
    @Test void categoryEndpointsSupportCrud() throws Exception {
        var c = category();
        when(categories.findAll()).thenReturn(List.of(c));
        when(categories.findById(1)).thenReturn(c);
        when(categories.create(Category.TOYS, "Toys")).thenReturn(c);
        when(categories.update(1, "Toys")).thenReturn(c);
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("TOYS"));
        mvc.perform(get("/api/v1/categories/1")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Toys"));
        mvc.perform(post("/api/v1/categories").contentType(APPLICATION_JSON).content("{\"code\":\"TOYS\",\"name\":\"Toys\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/categories/1"));
        mvc.perform(put("/api/v1/categories/1").contentType(APPLICATION_JSON).content("{\"name\":\"Toys\"}"))
            .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/categories/1")).andExpect(status().isNoContent());
        verify(categories).delete(1);
    }
    @Test void customerEndpointsValidateAndReturnCreatedCustomer() throws Exception {
        var c = mock(CustomerEntity.class);
        when(c.getId()).thenReturn(2L);
        when(c.getName()).thenReturn("Cosmo");
        when(c.getEmail()).thenReturn("cat@example.com");
        when(customers.create("Cosmo", "cat@example.com")).thenReturn(c);
        when(customers.findById(2)).thenReturn(c);
        mvc.perform(post("/api/v1/customers").contentType(APPLICATION_JSON).content("{\"name\":\"Cosmo\",\"email\":\"cat@example.com\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("cat@example.com"));
        mvc.perform(get("/api/v1/customers/2")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2));
        mvc.perform(post("/api/v1/customers").contentType(APPLICATION_JSON).content("{\"name\":\"\",\"email\":\"invalid\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void orderEndpointsMapCommandsAndViews() throws Exception {
        when(orders.create(eq(2L), anyList())).thenReturn(order());
        when(orders.findAll()).thenReturn(List.of(order()));
        when(orders.findById(1)).thenReturn(order());
        when(orders.findByOrderNumber("CC-test")).thenReturn(order());
        when(orders.updateStatus(1, OrderStatus.COMPLETED)).thenReturn(order());
        mvc.perform(post("/api/v1/orders").contentType(APPLICATION_JSON)
            .content("{\"customerId\":2,\"items\":[{\"productId\":\"00000000-0000-0000-0000-000000000001\",\"quantity\":2}]}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(10));
        verify(orders).create(2L, List.of(new OrderService.Line(new UUID(0,1), 2)));
        mvc.perform(get("/api/v1/orders")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1));
        mvc.perform(get("/api/v1/orders/1")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders/by-number/CC-test")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/orders/1/status").contentType(APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
            .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/orders/1")).andExpect(status().isNoContent());
        verify(orders).delete(1);
        mvc.perform(post("/api/v1/orders").contentType(APPLICATION_JSON).content("{\"customerId\":2,\"items\":[]}"))
            .andExpect(status().isBadRequest());
    }
    @Test void stockAndSalesReportEndpointsWork() throws Exception {
        var id = new UUID(0,1);
        when(orders.addStock(id, 10)).thenReturn(10);
        mvc.perform(post("/api/v1/products/" + id + "/stock").contentType(APPLICATION_JSON).content("{\"quantity\":10}"))
            .andExpect(status().isOk()).andExpect(content().string("10"));
        ProductSalesProjection row = mock(ProductSalesProjection.class);
        when(row.getProductId()).thenReturn(id);
        when(row.getProductName()).thenReturn("Cosmic yarn");
        when(row.getUnitsSold()).thenReturn(5L);
        Instant from = Instant.EPOCH, to = Instant.parse("2100-01-01T00:00:00Z");
        when(orders.popularProducts(from, to)).thenReturn(List.of(row));
        mvc.perform(get("/api/v1/reports/popular-products").param("from", from.toString()).param("to", to.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].unitsSold").value(5));
    }
    @Test void databaseAndBusinessErrorsHaveUsefulHttpStatuses() throws Exception {
        when(categories.findById(1)).thenThrow(new ResourceNotFoundException("Category", 1));
        mvc.perform(get("/api/v1/categories/1")).andExpect(status().isNotFound());
        doThrow(new DataIntegrityViolationException("foreign key")).when(categories).delete(2);
        mvc.perform(delete("/api/v1/categories/2")).andExpect(status().isConflict());
        doThrow(new IllegalArgumentException("Completed orders cannot be deleted")).when(orders).delete(1);
        mvc.perform(delete("/api/v1/orders/1")).andExpect(status().isBadRequest());
    }
}
