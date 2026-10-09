package com.market.spacecatmarket.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderAndCartTests {

    @Test
    void orderTotalUsesExactPricesAndRetainsItsSnapshot() {
        List<Product> products = new ArrayList<>(List.of(product("0.10"), product("0.20")));
        Order order = new Order(UUID.randomUUID(), products);
        products.clear();
        assertThat(order.products()).hasSize(2);
        assertThat(order.totalPrice()).isEqualByComparingTo("0.30");
        assertThatThrownBy(() -> order.products().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void cartCanBeEmptyAndRetainsItsSnapshot() {
        List<Product> products = new ArrayList<>(List.of(product("19.99")));
        Cart cart = new Cart(UUID.randomUUID(), products);
        products.clear();
        assertThat(cart.products()).hasSize(1);
        assertThat(new Cart(UUID.randomUUID(), List.of()).products()).isEmpty();
    }

    @Test
    void orderCannotBeEmpty() {
        assertThatThrownBy(() -> new Order(UUID.randomUUID(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private Product product(String price) {
        return new Product(UUID.randomUUID(), "Star Yarn", "Yarn", new BigDecimal(price),
                Category.TOYS, "seller@cosmo-cats.market");
    }
}
