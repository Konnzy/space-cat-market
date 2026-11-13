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
