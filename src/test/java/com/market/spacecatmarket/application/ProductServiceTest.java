package com.market.spacecatmarket.application;

import com.market.spacecatmarket.application.dto.PaginationRequest;
import com.market.spacecatmarket.domain.Category;
import com.market.spacecatmarket.domain.Product;
import com.market.spacecatmarket.infrastructure.persistence.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(repository);
    }

    @Test
    void findById_whenProductExists_returnsProduct() {
        Product product = sampleProduct();
        when(repository.findById(product.id())).thenReturn(Optional.of(product));

        Product actual = service.findById(product.id());

        assertEquals(product, actual);
        verify(repository).findById(product.id());
    }

    @Test
    void findById_whenProductDoesNotExist_throwsProductNotFoundException() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(repository.findById(id)).thenReturn(Optional.empty());

        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class, () -> service.findById(id));

        assertEquals("Product '%s' was not found.".formatted(id), exception.getMessage());
        verify(repository).findById(id);
    }

    @Test
    void create_addsProductToRepositoryAndReturnsProduct() {
        Product product = sampleProduct();

        Product actual = service.create(product);

        assertEquals(product, actual);
        verify(repository).add(product);
    }

    @Test
    void update_updatesProductInRepositoryAndReturnsProduct() {
        Product product = sampleProduct();
        when(repository.update(product)).thenReturn(true);

        Product actual = service.update(product);

        assertEquals(product, actual);
        verify(repository).update(product);
    }

    @Test
    void deleteById_whenProductExists_deletesProduct() {
        UUID id = sampleProduct().id();
        when(repository.deleteById(id)).thenReturn(true);

        service.deleteById(id);

        verify(repository).deleteById(id);
    }

    @Test
    void findAll_returnsRequestedPage() {
        Product first = sampleProduct();
        Product second = new Product(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "Cosmic ball",
                "Another cat toy",
                new BigDecimal("15.00"),
                Category.TOYS,
                "seller@example.com");

        when(repository.findAll()).thenReturn(List.of(first, second));

        ProductPage actual = service.findAll(
                new PaginationRequest(1, 1, null));

        assertEquals(List.of(second), actual.content());
        assertEquals(1, actual.page());
        assertEquals(1, actual.size());
        assertEquals(2L, actual.totalElements());
        assertNull(actual.nextCursor());
        verify(repository).findAll();
    }

    @Test
    void update_whenProductDoesNotExist_throwsProductNotFoundException() {
        Product product = sampleProduct();
        when(repository.update(product)).thenReturn(false);

        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class, () -> service.update(product));

        assertEquals("Product '%s' was not found.".formatted(product.id()), exception.getMessage());
        verify(repository).update(product);
    }

    @Test
    void deleteById_whenProductDoesNotExist_throwsProductNotFoundException() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(repository.deleteById(id)).thenReturn(false);

        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class, () -> service.deleteById(id));

        assertEquals("Product '%s' was not found.".formatted(id), exception.getMessage());
        verify(repository).deleteById(id);
    }

    @Test
    void findAll_whenRepositoryIsEmpty_returnsEmptyPage() {
        when(repository.findAll()).thenReturn(List.of());

        ProductPage actual = service.findAll(new PaginationRequest(0, 10, null));

        assertEquals(List.of(), actual.content());
        assertEquals(0, actual.page());
        assertEquals(10, actual.size());
        assertEquals(0L, actual.totalElements());
        assertNull(actual.nextCursor());
        verify(repository).findAll();
    }

    @Test
    void findAll_whenMoreProductsExist_returnsFirstPageWithNextCursor() {
        Product first = sampleProduct(1);
        Product second = sampleProduct(2);
        Product third = sampleProduct(3);
        when(repository.findAll()).thenReturn(List.of(first, second, third));

        ProductPage actual = service.findAll(new PaginationRequest(0, 2, null));

        assertEquals(List.of(first, second), actual.content());
        assertEquals(0, actual.page());
        assertEquals(2, actual.size());
        assertEquals(3L, actual.totalElements());
        assertEquals(second.id(), actual.nextCursor());
        verify(repository).findAll();
    }

    @Test
    void findAll_whenLastPageIsIncomplete_returnsRemainingProductsWithoutNextCursor() {
        Product first = sampleProduct(1);
        Product second = sampleProduct(2);
        Product third = sampleProduct(3);
        when(repository.findAll()).thenReturn(List.of(first, second, third));

        ProductPage actual = service.findAll(new PaginationRequest(1, 2, null));

        assertEquals(List.of(third), actual.content());
        assertEquals(1, actual.page());
        assertEquals(2, actual.size());
        assertEquals(3L, actual.totalElements());
        assertNull(actual.nextCursor());
        verify(repository).findAll();
    }

    @Test
    void findAll_whenPageIsBeyondLastPage_returnsEmptyPageWithTotalElements() {
        when(repository.findAll()).thenReturn(List.of(sampleProduct(1), sampleProduct(2)));

        ProductPage actual = service.findAll(new PaginationRequest(5, 2, null));

        assertEquals(List.of(), actual.content());
        assertEquals(5, actual.page());
        assertEquals(2, actual.size());
        assertEquals(2L, actual.totalElements());
        assertNull(actual.nextCursor());
        verify(repository).findAll();
    }

    @Test
    void findAll_whenCursorExists_returnsProductsAfterCursor() {
        Product first = sampleProduct(1);
        Product second = sampleProduct(2);
        Product third = sampleProduct(3);
        Product fourth = sampleProduct(4);
        when(repository.findAll()).thenReturn(List.of(first, second, third, fourth));

        // Курсор позначає останній уже прочитаний товар: повертаємо наступний.
        ProductPage actual = service.findAll(new PaginationRequest(0, 1, second.id()));

        assertEquals(List.of(third), actual.content());
        assertEquals(0, actual.page());
        assertEquals(1, actual.size());
        assertEquals(4L, actual.totalElements());
        assertEquals(third.id(), actual.nextCursor());
        verify(repository).findAll();
    }

    @Test
    void findAll_whenCursorPointsToLastProduct_returnsEmptyPage() {
        Product first = sampleProduct(1);
        Product last = sampleProduct(2);
        when(repository.findAll()).thenReturn(List.of(first, last));

        ProductPage actual = service.findAll(new PaginationRequest(0, 2, last.id()));

        assertEquals(List.of(), actual.content());
        assertEquals(0, actual.page());
        assertEquals(2, actual.size());
        assertEquals(2L, actual.totalElements());
        assertNull(actual.nextCursor());
        verify(repository).findAll();
    }

    @Test
    void findAll_whenCursorDoesNotExist_throwsInvalidCursorException() {
        UUID unknownCursor = new UUID(0, 3);
        when(repository.findAll()).thenReturn(List.of(sampleProduct(1), sampleProduct(2)));

        InvalidCursorException exception = assertThrows(
                InvalidCursorException.class,
                () -> service.findAll(new PaginationRequest(0, 2, unknownCursor)));

        assertEquals("must refer to an existing product; cursor '%s' is unknown".formatted(unknownCursor),
                exception.getMessage());
        verify(repository).findAll();
    }

    @Test
    void findAll_whenCursorAndNonZeroPageAreProvided_usesCursorAndResetsPageToZero() {
        Product first = sampleProduct(1);
        Product second = sampleProduct(2);
        Product third = sampleProduct(3);
        when(repository.findAll()).thenReturn(List.of(first, second, third));

        ProductPage actual = service.findAll(new PaginationRequest(99, 2, first.id()));

        assertEquals(List.of(second, third), actual.content());
        assertEquals(0, actual.page());
        assertEquals(2, actual.size());
        assertEquals(3L, actual.totalElements());
        assertNull(actual.nextCursor());
        verify(repository).findAll();
    }

    private Product sampleProduct() {
        return sampleProduct(1);
    }

    private Product sampleProduct(int productNumber) {
        return new Product(
                new UUID(0, productNumber),
                "Cosmic cat toy",
                "A toy for an intergalactic cat",
                new BigDecimal("12.50"),
                Category.TOYS,
                "seller@example.com");
    }
}
