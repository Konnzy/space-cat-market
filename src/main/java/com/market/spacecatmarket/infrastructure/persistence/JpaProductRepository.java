package com.market.spacecatmarket.infrastructure.persistence;
import com.market.spacecatmarket.domain.Product;
import com.market.spacecatmarket.infrastructure.persistence.entity.ProductEntity;
import com.market.spacecatmarket.infrastructure.persistence.jpa.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Repository
@Transactional(readOnly = true)
public class JpaProductRepository implements ProductRepository {
    private final ProductJpaRepository products;
    private final CategoryJpaRepository categories;
    public JpaProductRepository(ProductJpaRepository products, CategoryJpaRepository categories) {
        this.products = products;
        this.categories = categories;
    }
    @Override @Transactional
    public void add(Product product) {
        products.save(new ProductEntity(product, categories.findByCode(product.category())
            .orElseThrow(() -> new IllegalArgumentException("Category does not exist"))));
    }
    @Override
    public List<Product> findAll() { return products.findAllByOrderByIdAsc().stream().map(ProductEntity::toDomain).toList(); }
    @Override
    public Optional<Product> findById(UUID id) { return products.findByPublicId(id).map(ProductEntity::toDomain); }
    @Override @Transactional
    public boolean update(Product product) {
        return products.lockByPublicId(product.id()).map(entity -> {
            entity.update(product, categories.findByCode(product.category())
                .orElseThrow(() -> new IllegalArgumentException("Category does not exist")));
            return true;
        }).orElse(false);
    }
    @Override @Transactional
    public boolean deleteById(UUID id) {
        return products.lockByPublicId(id).map(entity -> { products.delete(entity); return true; }).orElse(false);
    }
}
