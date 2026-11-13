package com.market.spacecatmarket.infrastructure.persistence.jpa;
import com.market.spacecatmarket.domain.Category;
import com.market.spacecatmarket.infrastructure.persistence.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {
    Optional<CategoryEntity> findByCode(Category code);
}
