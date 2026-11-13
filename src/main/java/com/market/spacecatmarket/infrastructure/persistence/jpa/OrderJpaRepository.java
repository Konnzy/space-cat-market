package com.market.spacecatmarket.infrastructure.persistence.jpa;
import com.market.spacecatmarket.infrastructure.persistence.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> lockById(@Param("id") Long id);

    @Query("""
        select p.publicId as productId, p.name as productName, sum(i.quantity) as unitsSold
        from OrderItemEntity i join i.product p join i.order o
        where o.status = :status and o.createdAt >= :from and o.createdAt < :to
        group by p.id, p.publicId, p.name
        order by sum(i.quantity) desc, p.name asc, p.publicId asc
        """)
    List<ProductSalesProjection> findPopularProducts(@Param("from") Instant from,
        @Param("to") Instant to, @Param("status") OrderStatus status);
}
