package com.market.spacecatmarket.infrastructure.persistence;
import com.market.spacecatmarket.domain.Product;
import com.market.spacecatmarket.infrastructure.persistence.entity.ProductEntity;
import com.market.spacecatmarket.infrastructure.persistence.jpa.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.market.spacecatmarket.application.ProductPage;
import com.market.spacecatmarket.application.dto.*;

@Repository
@Transactional(readOnly = true)
public class JpaProductRepository implements ProductRepository {
    private final ProductJpaRepository products;
    private final CategoryJpaRepository categories;
    private final ProductPageQuery pageQuery;
    public JpaProductRepository(ProductJpaRepository products, CategoryJpaRepository categories, ProductPageQuery pageQuery) {
        this.pageQuery = pageQuery;
        this.products = products;
        this.categories = categories;
    }
    @Override @Transactional
    public void add(Product product) {
        products.save(new ProductEntity(product, categories.findByCode(product.category())
            .orElseThrow(() -> new IllegalArgumentException("Category does not exist"))));
    }
    @Override
    public ProductPage findPage(PaginationRequest pagination, ProductFilter filter) {
        return pageQuery.findPage(pagination, filter);
    }
    @Override
    public Optional<Product> findById(UUID id) { return products.findByPublicId(id).filter(ProductEntity::isActive).map(ProductEntity::toDomain); }
    @Override @Transactional
    public boolean update(Product product) {
        return products.lockByPublicId(product.id()).filter(ProductEntity::isActive).map(entity -> {
            entity.update(product, categories.findByCode(product.category())
                .orElseThrow(() -> new IllegalArgumentException("Category does not exist")));
            return true;
        }).orElse(false);
    }
    @Override @Transactional
    public boolean deleteById(UUID id) {
        return products.lockByPublicId(id).filter(ProductEntity::isActive).map(entity -> { entity.archive(); return true; }).orElse(false);
    }
}
