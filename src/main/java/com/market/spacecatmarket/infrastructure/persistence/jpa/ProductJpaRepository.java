package com.market.spacecatmarket.infrastructure.persistence.jpa;
import com.market.spacecatmarket.infrastructure.persistence.entity.ProductEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {
    @EntityGraph(attributePaths = "category")
    Optional<ProductEntity> findByPublicId(UUID publicId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductEntity p where p.publicId = :id")
    Optional<ProductEntity> lockByPublicId(@Param("id") UUID id);
}
