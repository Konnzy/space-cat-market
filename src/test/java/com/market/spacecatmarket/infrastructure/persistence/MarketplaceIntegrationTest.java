package com.market.spacecatmarket.infrastructure.persistence;

import com.market.spacecatmarket.application.*;
import com.market.spacecatmarket.domain.*;
import com.market.spacecatmarket.infrastructure.persistence.entity.*;
import com.market.spacecatmarket.infrastructure.persistence.jpa.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaProductRepository.class, ProductService.class, CategoryService.class,
         CustomerService.class, OrderService.class})
@Testcontainers
// Service methods must commit/rollback their OWN transactions; a test transaction would mask failures.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MarketplaceIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");
    @Autowired ProductService products;
    @Autowired CategoryService categories;
    @Autowired CustomerService customers;
    @Autowired OrderService orders;
    @Autowired ProductJpaRepository productRepository;
    @Autowired CategoryJpaRepository categoryRepository;
    @Autowired OrderJpaRepository orderRepository;
    @Autowired CustomerJpaRepository customerRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM order_details");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM customers");
        jdbc.update("DELETE FROM categories");
        for (Category code : Category.values()) {
            categories.create(code, code.name());
        }
    }
    Product product(String name, Category category, int stock) {
        Product p = products.create(new Product(UUID.randomUUID(), name, "A cosmic product",
            new BigDecimal("12.50"), category, "seller@example.com"));
        if (stock > 0) orders.addStock(p.id(), stock);
        return p;
    }
    long customer() { return customers.create("Cosmo", "cosmo@example.com").getId(); }
    OrderService.OrderView order(long customer, Product product, int quantity) {
        return orders.create(customer, List.of(new OrderService.Line(product.id(), quantity)));
    }
    int stock(Product product) {
        return productRepository.findByPublicId(product.id()).orElseThrow().getStockQuantity();
    }

    @Test void migrationsCreateFiveTablesSequencesConstraintsAndIndexes() {
        List<String> tables = jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public'", String.class);
        assertTrue(tables.containsAll(List.of("customers", "categories", "products", "orders", "order_details", "databasechangelog")));
        assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM information_schema.sequences WHERE sequence_schema='public'", Integer.class));
        List<String> indexes = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname='public'", String.class);
        assertTrue(indexes.containsAll(List.of("idx_product_name", "uq_customer_email", "uq_product_category_name", "uq_order_number")));
    }
    @Test void productCrudPersistsUpdatesAndDeletesThroughJpaAdapter() {
        Product p = product("Cosmic yarn", Category.TOYS, 0);
        assertEquals(p, products.findById(p.id()));
        Product changed = new Product(p.id(), "Cosmic milk", "New description", new BigDecimal("9.99"), Category.FOOD, "new@example.com");
        products.update(changed);
        assertEquals(changed, products.findById(p.id()));
        assertTrue(products.findAll(new com.market.spacecatmarket.application.dto.PaginationRequest(0, 10, null)).content().contains(changed));
        products.deleteById(p.id());
        assertThrows(ProductNotFoundException.class, () -> products.findById(p.id()));
        assertThrows(ProductNotFoundException.class, () -> products.update(changed));
        assertThrows(ProductNotFoundException.class, () -> products.deleteById(p.id()));
    }
    @Test void productsHaveSequenceIdsAndStableUuidPublicIds() {
        Product a = product("Cosmic yarn", Category.TOYS, 0);
        Product b = product("Cosmic ball", Category.TOYS, 0);
        assertTrue(productRepository.findByPublicId(b.id()).orElseThrow().getId()
            > productRepository.findByPublicId(a.id()).orElseThrow().getId());
    }
    @Test void duplicateProductNameIsRejectedOnlyWithinSameCategory() {
        product("Cosmic yarn", Category.TOYS, 0);
        assertThrows(DataIntegrityViolationException.class, () -> product("Cosmic yarn", Category.TOYS, 0));
        product("Cosmic yarn", Category.FOOD, 0);
        assertEquals(2, productRepository.count());
    }
    @Test void duplicatePublicIdIsRejected() {
        Product p = product("Cosmic yarn", Category.TOYS, 0);
        assertThrows(DataIntegrityViolationException.class, () -> products.create(p));
    }
    @Test void categoryCrudAndProductRelationshipAreEnforced() {
        var toys = categoryRepository.findByCode(Category.TOYS).orElseThrow();
        categories.update(toys.getId(), "Space toys");
        assertEquals("Space toys", categories.findById(toys.getId()).getName());
        assertEquals(3, categories.findAll().size());
        product("Cosmic yarn", Category.TOYS, 0);
        assertThrows(DataIntegrityViolationException.class, () -> categories.delete(toys.getId()));
        var food = categoryRepository.findByCode(Category.FOOD).orElseThrow();
        categories.delete(food.getId());
        assertThrows(ResourceNotFoundException.class, () -> categories.findById(food.getId()));
        assertNotNull(categories.create(Category.FOOD, "Food again").getId());
        assertThrows(IllegalArgumentException.class, () -> categories.update(toys.getId(), " "));
        assertThrows(DataIntegrityViolationException.class, () -> categories.create(Category.TOYS, "Duplicate"));
    }
    @Test void productCannotBeCreatedOrMovedToMissingCategory() {
        Product p = product("Cosmic yarn", Category.TOYS, 0);
        categories.delete(categoryRepository.findByCode(Category.FOOD).orElseThrow().getId());
        assertThrows(IllegalArgumentException.class, () -> product("Cosmic milk", Category.FOOD, 0));
        assertThrows(IllegalArgumentException.class, () -> products.update(new Product(p.id(), p.name(), p.description(), p.price(), Category.FOOD, p.sellerEmail())));
        assertEquals(Category.TOYS, products.findById(p.id()).category());
    }
    @Test void emailsAreNormalizedAndUnique() {
        var c = customers.create("Cosmo", " COSMO@example.com ");
        assertEquals("cosmo@example.com", customers.findById(c.getId()).getEmail());
        assertThrows(DataIntegrityViolationException.class, () -> customers.create("Other", "cosmo@EXAMPLE.com"));
        assertThrows(ResourceNotFoundException.class, () -> customers.findById(-1));
    }
    @Test void orderCrudAndNaturalIdLookupWorkAcrossTransactions() {
        Product p = product("Cosmic yarn", Category.TOYS, 10);
        var o = order(customer(), p, 2);
        assertNotNull(o.id());
        assertEquals(new BigDecimal("25.00"), o.total());
        assertEquals(8, stock(p));
        assertEquals(o, orders.findById(o.id()));
        assertEquals(o, orders.findByOrderNumber(o.orderNumber()));
        assertEquals(List.of(o), orders.findAll());
        assertEquals(OrderStatus.COMPLETED, orders.updateStatus(o.id(), OrderStatus.COMPLETED).status());
        assertEquals(OrderStatus.COMPLETED, orders.updateStatus(o.id(), OrderStatus.COMPLETED).status());
        assertThrows(IllegalArgumentException.class, () -> orders.updateStatus(o.id(), OrderStatus.CANCELLED));
        assertThrows(IllegalArgumentException.class, () -> orders.delete(o.id()));
    }
    @Test void deletingNewOrderRestoresStockAndRemovesItems() {
        Product p = product("Cosmic yarn", Category.TOYS, 10);
        var o = order(customer(), p, 2);
        orders.delete(o.id());
        assertEquals(10, stock(p));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM order_details", Integer.class));
        assertThrows(ResourceNotFoundException.class, () -> orders.findById(o.id()));
    }
    @Test void cancellingRestoresStockExactlyOnce() {
        Product p = product("Cosmic yarn", Category.TOYS, 10);
        var o = order(customer(), p, 2);
        orders.updateStatus(o.id(), OrderStatus.CANCELLED);
        orders.updateStatus(o.id(), OrderStatus.CANCELLED);
        assertEquals(10, stock(p));
        orders.delete(o.id());
        assertEquals(10, stock(p));
    }
    @Test void historicalPriceSurvivesProductPriceChange() {
        Product p = product("Cosmic yarn", Category.TOYS, 10);
        var o = order(customer(), p, 2);
        products.update(new Product(p.id(), p.name(), p.description(), new BigDecimal("99.00"), p.category(), p.sellerEmail()));
        assertEquals(new BigDecimal("25.00"), orders.findById(o.id()).total());
        assertThrows(DataIntegrityViolationException.class, () -> products.deleteById(p.id()));
    }
    @Test void failureOnSecondItemRollsBackFirstReservationAndWholeOrder() {
        Product a = product("Cosmic yarn", Category.TOYS, 5);
        Product b = product("Cosmic milk", Category.FOOD, 5);
        List<Product> sorted = java.util.stream.Stream.of(a,b).sorted(Comparator.comparing(Product::id)).toList();
        // Force a real UPDATE before the subsequent failure; rollback must undo SQL, not just Java state.
        jdbc.update("UPDATE products SET stock_quantity=0 WHERE public_id=?", sorted.get(1).id());
        assertThrows(IllegalArgumentException.class, () -> orders.create(customer(), List.of(
            new OrderService.Line(sorted.get(0).id(), 1), new OrderService.Line(sorted.get(1).id(), 1))));
        assertEquals(5, stock(sorted.get(0)));
        assertEquals(0, orderRepository.count());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM order_details", Integer.class));
    }
    @Test void parallelOrdersCannotOversellLastUnit() throws Exception {
        Product p = product("Cosmic yarn", Category.TOYS, 1);
        long c = customer();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                try { order(c, p, 1); return true; }
                catch (IllegalArgumentException expected) { return false; }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(0, stock(p));
        assertEquals(1, orderRepository.count());
    }
    @Test void projectionAggregatesUnitsFiltersStatusAndPeriodAndSorts() {
        Product a = product("Cosmic yarn", Category.TOYS, 20);
        Product b = product("Cosmic milk", Category.FOOD, 20);
        long c = customer();
        var first = order(c, a, 2);
        var second = order(c, a, 3);
        var third = order(c, b, 4);
        orders.updateStatus(first.id(), OrderStatus.COMPLETED);
        orders.updateStatus(second.id(), OrderStatus.COMPLETED);
        orders.updateStatus(third.id(), OrderStatus.COMPLETED);
        order(c, b, 10); // NEW must be excluded.
        var cancelled = order(c, a, 1);
        orders.updateStatus(cancelled.id(), OrderStatus.CANCELLED);
        var old = order(c, b, 1);
        orders.updateStatus(old.id(), OrderStatus.COMPLETED);
        jdbc.update("UPDATE orders SET created_at='2000-01-01T00:00:00Z' WHERE order_id=?", old.id());
        var report = orders.popularProducts(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2100-01-01T00:00:00Z"));
        assertEquals(2, report.size());
        assertEquals(a.id(), report.get(0).getProductId());
        assertEquals(a.name(), report.get(0).getProductName());
        assertEquals(5L, report.get(0).getUnitsSold());
        assertEquals(4L, report.get(1).getUnitsSold());
        assertTrue(orders.popularProducts(Instant.EPOCH, Instant.parse("1999-01-01T00:00:00Z")).isEmpty());
    }
    @Test void invalidCommandsAndMissingRecordsAreRejected() {
        long c = customer();
        Product p = product("Cosmic yarn", Category.TOYS, 10);
        assertThrows(IllegalArgumentException.class, () -> orders.create(c, null));
        assertThrows(IllegalArgumentException.class, () -> orders.create(c, List.of()));
        assertThrows(IllegalArgumentException.class, () -> order(c, p, 0));
        assertThrows(IllegalArgumentException.class, () -> orders.create(c, List.of(new OrderService.Line(null, 1))));
        assertThrows(IllegalArgumentException.class, () -> orders.create(c, List.of(new OrderService.Line(p.id(), 1), new OrderService.Line(p.id(), 2))));
        assertThrows(ResourceNotFoundException.class, () -> order(-1, p, 1));
        assertThrows(ProductNotFoundException.class, () -> orders.create(c, List.of(new OrderService.Line(UUID.randomUUID(), 1))));
        assertThrows(ProductNotFoundException.class, () -> orders.addStock(UUID.randomUUID(), 1));
        assertThrows(IllegalArgumentException.class, () -> orders.addStock(p.id(), 0));
        assertThrows(ResourceNotFoundException.class, () -> orders.findByOrderNumber("missing"));
        assertThrows(ResourceNotFoundException.class, () -> orders.updateStatus(-1, OrderStatus.COMPLETED));
        assertThrows(ResourceNotFoundException.class, () -> orders.delete(-1));
        var o = order(c, p, 1);
        assertThrows(IllegalArgumentException.class, () -> orders.updateStatus(o.id(), null));
        assertThrows(IllegalArgumentException.class, () -> orders.popularProducts(Instant.now(), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> orders.popularProducts(null, Instant.now()));
    }
    @Test void databaseEnforcesChecksForeignKeysAndNonNulls() {
        Product p = product("Cosmic yarn", Category.TOYS, 1);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE products SET stock_quantity=-1 WHERE public_id=?", p.id()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE products SET price=0 WHERE public_id=?", p.id()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE products SET category_id=NULL WHERE public_id=?", p.id()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE products SET category_id=-1 WHERE public_id=?", p.id()));
        var o = order(customer(), p, 1);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE order_details SET quantity=0 WHERE order_id=?", o.id()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE orders SET order_number=NULL WHERE order_id=?", o.id()));
    }
}
