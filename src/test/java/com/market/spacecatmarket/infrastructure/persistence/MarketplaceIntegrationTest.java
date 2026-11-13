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
import com.market.spacecatmarket.application.dto.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaProductRepository.class, ProductService.class, CategoryService.class,
         CustomerService.class, OrderService.class, ReportService.class, ProductPageQuery.class})
@Testcontainers
// Service methods must commit/rollback their OWN transactions; a test transaction would mask failures.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MarketplaceIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine");
    @Autowired ProductService products;
    @Autowired ReportService reports;
    @Autowired jakarta.persistence.EntityManagerFactory emf;
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
        assertEquals(7, jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class));
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
        products.deleteById(p.id());
        assertFalse(productRepository.findByPublicId(p.id()).orElseThrow().isActive());
        assertEquals(new BigDecimal("25.00"), orders.findById(o.id()).total());
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

    ProductPage catalog(int page, int size, UUID cursor, ProductFilter filter) {
        return products.findAll(new PaginationRequest(page, size, cursor), filter);
    }
    static java.util.stream.Stream<Arguments> sortOrders() {
        return Arrays.stream(ProductFilter.Sort.values()).flatMap(s ->
            Arrays.stream(ProductFilter.Direction.values()).map(d -> Arguments.of(s,d)));
    }
    @ParameterizedTest @MethodSource("sortOrders")
    void databaseOffsetAndCursorPaginationAreStableWithTies(ProductFilter.Sort sort, ProductFilter.Direction direction) {
        Product a = product("Alpha", Category.TOYS, 1);
        Product b = product("Beta", Category.TOYS, 1);
        Product c = product("Alpha", Category.FOOD, 1);
        Product d = product("Delta", Category.FOOD, 1);
        products.update(new Product(b.id(), b.name(), b.description(), new BigDecimal("5.00"), b.category(), b.sellerEmail()));
        List<UUID> expected = switch(sort) {
            case ID -> List.of(a.id(),b.id(),c.id(),d.id());
            case NAME -> List.of(a.id(),c.id(),b.id(),d.id());
            case PRICE -> List.of(b.id(),a.id(),c.id(),d.id());
        };
        if (direction == ProductFilter.Direction.DESC) expected = expected.reversed();
        var filter = new ProductFilter(null,null,null,null,null,sort,direction);
        var first = catalog(0,2,null,filter);
        assertEquals(expected.subList(0,2), first.content().stream().map(Product::id).toList());
        assertEquals(expected.subList(2,4), catalog(1,2,null,filter).content().stream().map(Product::id).toList());
        var second = catalog(99,2,first.nextCursor(),filter);
        assertEquals(0, second.page());
        assertEquals(expected.subList(2,4), second.content().stream().map(Product::id).toList());
        assertEquals(4, second.totalElements());
        assertNull(second.nextCursor());
        assertTrue(catalog(0,2,expected.getLast(),filter).content().isEmpty());
        assertTrue(catalog(Integer.MAX_VALUE,100,null,filter).content().isEmpty());
    }
    @Test void catalogFiltersSearchLiterallyAndExcludeArchives() {
        Product toy = product("Star 100%_Yarn", Category.TOYS, 4);
        Product food = product("Star Milk", Category.FOOD, 0);
        Product archived = product("Star Old", Category.TOYS, 5);
        products.deleteById(archived.id());
        var f = new ProductFilter("  STAR 100%_  ",Category.TOYS,new BigDecimal("12.50"),new BigDecimal("12.50"),true,null,null);
        assertEquals(List.of(toy), catalog(0,10,null,f).content());
        assertEquals(List.of(food), catalog(0,10,null,new ProductFilter("star",null,null,null,false,null,null)).content());
        assertEquals(2,catalog(0,10,null,ProductFilter.defaults()).totalElements());
        assertTrue(catalog(0,10,null,new ProductFilter(null,null,new BigDecimal("13"),null,null,null,null)).content().isEmpty());
        assertTrue(catalog(0,10,null,new ProductFilter("\\",null,null,null,null,null,null)).content().isEmpty());
        assertThrows(InvalidCursorException.class, () -> catalog(0,2,archived.id(),ProductFilter.defaults()));
        assertThrows(InvalidCursorException.class, () -> catalog(0,2,food.id(),f));
        assertThrows(InvalidCursorException.class, () -> catalog(0,2,UUID.randomUUID(),f));
    }
    @Test void databasePaginationLoadsOnlyOnePageNotEntireCatalog() {
        for (int i=0;i<15;i++) product("Star toy "+i,Category.TOYS,0);
        var statistics = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            var page = catalog(0,2,null,ProductFilter.defaults());
            assertEquals(2,page.content().size());
            assertEquals(15,page.totalElements());
            assertTrue(statistics.getEntityLoadCount() <= 4, "At most size+1 products and one category should load");
        } finally { statistics.setStatisticsEnabled(false); }
    }
    @Test void invalidFiltersAndPaginationAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProductFilter(null,null,new BigDecimal("-1"),null,null,null,null));
        assertThrows(IllegalArgumentException.class, () -> new ProductFilter(null,null,null,new BigDecimal("-1"),null,null,null));
        assertThrows(IllegalArgumentException.class, () -> new ProductFilter(null,null,BigDecimal.TEN,BigDecimal.ONE,null,null,null));
        assertThrows(IllegalArgumentException.class, () -> new PaginationRequest(-1,1,null));
        assertThrows(IllegalArgumentException.class, () -> new PaginationRequest(0,101,null));
        assertThrows(IllegalArgumentException.class, () -> new PaginationRequest(0,0,null));
        assertEquals(0,new PaginationRequest(null,null,null).page());
        assertEquals(20,new PaginationRequest(null,null,null).size());
    }
    @Test void editingNewOrderAddsRemovesAndChangesQuantitiesWithoutChangingHistoricalPrice() {
        Product a = product("Star yarn",Category.TOYS,10);
        Product b = product("Star milk",Category.FOOD,10);
        Product c = product("Star collar",Category.ACCESSORIES,10);
        var o = orders.create(customer(),List.of(new OrderService.Line(a.id(),2),new OrderService.Line(b.id(),3)));
        products.update(new Product(a.id(),a.name(),a.description(),new BigDecimal("99.00"),a.category(),a.sellerEmail()));
        var edited = orders.replaceItems(o.id(),List.of(new OrderService.Line(a.id(),4),new OrderService.Line(c.id(),1)));
        assertEquals(6,stock(a));
        assertEquals(10,stock(b));
        assertEquals(9,stock(c));
        assertEquals(new BigDecimal("62.50"),edited.total());
        assertEquals(2,edited.items().size());
        assertEquals(edited,orders.findById(o.id()));
        orders.updateStatus(o.id(),OrderStatus.CANCELLED);
        assertEquals(10,stock(a));
        assertEquals(10,stock(c));
        assertThrows(IllegalArgumentException.class, () -> orders.replaceItems(o.id(),List.of(new OrderService.Line(a.id(),1))));
    }
    @Test void failedEditRollsBackEarlierStockAndQuantityChanges() {
        Product a = product("Star A",Category.TOYS,10);
        Product b = product("Star B",Category.TOYS,10);
        var sorted = java.util.stream.Stream.of(a,b).sorted(Comparator.comparing(Product::id)).toList();
        var o = orders.create(customer(),List.of(new OrderService.Line(a.id(),1),new OrderService.Line(b.id(),1)));
        assertThrows(IllegalArgumentException.class, () -> orders.replaceItems(o.id(),List.of(
            new OrderService.Line(sorted.getFirst().id(),2),new OrderService.Line(sorted.getLast().id(),99))));
        assertEquals(o,orders.findById(o.id()));
        assertEquals(9,stock(a)); assertEquals(9,stock(b));
        assertThrows(IllegalArgumentException.class, () -> orders.replaceItems(o.id(),List.of()));
        assertThrows(ResourceNotFoundException.class, () -> orders.replaceItems(-1,List.of(new OrderService.Line(a.id(),1))));
        assertThrows(ProductNotFoundException.class, () -> orders.replaceItems(o.id(),List.of(new OrderService.Line(UUID.randomUUID(),1))));
    }
    @Test void archivedProductsStayInHistoryButCannotBePurchasedOrIncreased() {
        Product p = product("Star yarn",Category.TOYS,10);
        long c = customer();
        var o = order(c,p,3);
        products.deleteById(p.id());
        assertThrows(ProductNotFoundException.class, () -> products.findById(p.id()));
        assertThrows(ProductNotFoundException.class, () -> products.update(p));
        assertThrows(ProductNotFoundException.class, () -> products.deleteById(p.id()));
        assertThrows(IllegalArgumentException.class, () -> order(c,p,1));
        assertThrows(IllegalArgumentException.class, () -> orders.replaceItems(o.id(),List.of(new OrderService.Line(p.id(),4))));
        orders.replaceItems(o.id(),List.of(new OrderService.Line(p.id(),1)));
        assertEquals(9,stock(p));
        orders.updateStatus(o.id(),OrderStatus.COMPLETED);
        assertEquals(1L,orders.popularProducts(Instant.EPOCH,Instant.parse("2100-01-01T00:00:00Z")).getFirst().getUnitsSold());
    }
    @Test void concurrentCancellationRestoresStockOnce() throws Exception {
        Product p=product("Star yarn",Category.TOYS,10);
        var o=order(customer(),p,3);
        race(() -> orders.updateStatus(o.id(),OrderStatus.CANCELLED), () -> orders.updateStatus(o.id(),OrderStatus.CANCELLED));
        assertEquals(10,stock(p));
    }
    @Test void concurrentProductUpdateAndPurchaseKeepStockAndPriceConsistent() throws Exception {
        Product p=product("Star yarn",Category.TOYS,10);
        long c=customer();
        Product changed=new Product(p.id(),p.name(),p.description(),new BigDecimal("20.00"),p.category(),p.sellerEmail());
        race(() -> products.update(changed), () -> order(c,p,2));
        assertEquals(8,stock(p));
        assertEquals(new BigDecimal("20.00"),products.findById(p.id()).price());
        BigDecimal price=orders.findAll().getFirst().items().getFirst().unitPrice();
        assertTrue(price.equals(new BigDecimal("12.50")) || price.equals(new BigDecimal("20.00")));
    }
    @Test void concurrentEditingAndPurchaseNeverOverwriteStockWithStaleEntityState() throws Exception {
        Product p=product("Star yarn",Category.TOYS,10);
        long c=customer(); var o=order(c,p,2);
        race(() -> orders.replaceItems(o.id(),List.of(new OrderService.Line(p.id(),4))), () -> order(c,p,3));
        assertEquals(3,stock(p));
        assertEquals(4,orders.findById(o.id()).items().getFirst().quantity());
    }
    void race(Callable<?> first,Callable<?> second) throws Exception {
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var tasks=List.of(first,second).stream().map(task -> executor.submit(() -> {
                ready.countDown();
                if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return task.call();
            })).toList();
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            for(var task:tasks) task.get(20,TimeUnit.SECONDS);
        }
    }
    @Test void extendedReportsUseHistoricalPricesCountDistinctOrdersAndRespectExactPeriodBounds() {
        Instant from=Instant.parse("2026-01-01T00:00:00Z"),to=Instant.parse("2026-02-01T00:00:00Z");
        Product toy=product("Star toy",Category.TOYS,20),food=product("Star food",Category.FOOD,20);
        Product unsold=product("Star unsold",Category.TOYS,10),archived=product("Star archived",Category.TOYS,1);
        products.deleteById(archived.id());
        long c=customer(),other=customers.create("Other","other@example.com").getId();
        var a=orders.create(c,List.of(new OrderService.Line(toy.id(),2),new OrderService.Line(food.id(),1)));
        var b=order(c,toy,1);var boundary=order(other,food,4);var old=order(other,food,2);
        for(var o:List.of(a,b,boundary,old)) orders.updateStatus(o.id(),OrderStatus.COMPLETED);
        jdbc.update("UPDATE orders SET created_at=? WHERE order_id=?",java.sql.Timestamp.from(from),a.id());
        jdbc.update("UPDATE orders SET created_at=? WHERE order_id=?",java.sql.Timestamp.from(from.plusSeconds(1)),b.id());
        jdbc.update("UPDATE orders SET created_at=? WHERE order_id=?",java.sql.Timestamp.from(to),boundary.id());
        jdbc.update("UPDATE orders SET created_at=? WHERE order_id=?",java.sql.Timestamp.from(from.minusNanos(1000)),old.id());
        var pending=order(other,unsold,1);
        jdbc.update("UPDATE orders SET created_at=? WHERE order_id=?",java.sql.Timestamp.from(from),pending.id());
        var cancelled=order(other,unsold,1);orders.updateStatus(cancelled.id(),OrderStatus.CANCELLED);
        jdbc.update("UPDATE orders SET created_at=? WHERE order_id=?",java.sql.Timestamp.from(from),cancelled.id());
        products.update(new Product(toy.id(),toy.name(),toy.description(),new BigDecimal("99.00"),toy.category(),toy.sellerEmail()));
        var revenue=reports.revenue(from,to);
        assertEquals(new BigDecimal("50.00"),revenue.revenue());
        assertEquals(4,revenue.unitsSold());assertEquals(2,revenue.orderCount());
        var cat=reports.categorySales(from,to);
        assertEquals(Category.TOYS,cat.getFirst().getCategory());
        assertEquals("TOYS",cat.getFirst().getCategoryName());
        assertEquals(3L,cat.getFirst().getUnitsSold());assertEquals(new BigDecimal("37.50"),cat.getFirst().getRevenue());
        var top=reports.topCustomers(from,to);
        assertEquals(1,top.size());assertEquals(c,top.getFirst().getCustomerId());
        assertEquals("Cosmo",top.getFirst().getCustomerName());assertEquals(2L,top.getFirst().getOrderCount());
        assertEquals(4L,top.getFirst().getUnitsSold());assertEquals(new BigDecimal("50.00"),top.getFirst().getRevenue());
        var none=reports.unsoldProducts(from,to);
        assertEquals(List.of(unsold.id()),none.stream().map(UnsoldProductProjection::getProductId).toList());
        assertEquals(unsold.name(),none.getFirst().getProductName());assertEquals(Category.TOYS,none.getFirst().getCategory());
        var empty=reports.revenue(Instant.EPOCH,from.minusSeconds(100));
        assertEquals(0,empty.revenue().signum());assertEquals(0,empty.unitsSold());assertEquals(0,empty.orderCount());
        assertThrows(IllegalArgumentException.class,()->reports.revenue(to,from));
        assertThrows(IllegalArgumentException.class,()->reports.categorySales(from,from));
        assertThrows(IllegalArgumentException.class,()->reports.topCustomers(null,to));
        assertThrows(IllegalArgumentException.class,()->reports.unsoldProducts(from,null));
    }

}
