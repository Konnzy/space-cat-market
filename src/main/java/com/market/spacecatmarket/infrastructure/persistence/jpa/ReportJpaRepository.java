package com.market.spacecatmarket.infrastructure.persistence.jpa;

import com.market.spacecatmarket.infrastructure.persistence.entity.OrderEntity;
import com.market.spacecatmarket.infrastructure.persistence.entity.OrderStatus;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface ReportJpaRepository extends Repository<OrderEntity, Long> {
    @Query("""
        select c.code as category, c.name as categoryName, sum(i.quantity) as unitsSold,
               sum(i.unitPrice * i.quantity) as revenue
        from OrderItemEntity i join i.product p join p.category c join i.order o
        where o.status = :status and o.createdAt >= :from and o.createdAt < :to
        group by c.id, c.code, c.name
        order by sum(i.quantity) desc, c.code asc
        """)
    List<CategorySalesProjection> categorySales(@Param("from") Instant from, @Param("to") Instant to,
                                                @Param("status") OrderStatus status);

    @Query("""
        select sum(i.unitPrice * i.quantity) as revenue, sum(i.quantity) as unitsSold,
               count(distinct o.id) as orderCount
        from OrderItemEntity i join i.order o
        where o.status = :status and o.createdAt >= :from and o.createdAt < :to
        """)
    RevenueProjection revenue(@Param("from") Instant from, @Param("to") Instant to,
                               @Param("status") OrderStatus status);

    @Query("""
        select c.id as customerId, c.name as customerName, count(distinct o.id) as orderCount,
               sum(i.quantity) as unitsSold, sum(i.unitPrice * i.quantity) as revenue
        from OrderItemEntity i join i.order o join o.customer c
        where o.status = :status and o.createdAt >= :from and o.createdAt < :to
        group by c.id, c.name
        order by count(distinct o.id) desc, sum(i.unitPrice * i.quantity) desc, c.id asc
        """)
    List<CustomerSalesProjection> topCustomers(@Param("from") Instant from, @Param("to") Instant to,
                                               @Param("status") OrderStatus status);

    @Query("""
        select p.publicId as productId, p.name as productName, c.code as category
        from ProductEntity p join p.category c
        where p.active = true and not exists (
            select i.id from OrderItemEntity i join i.order o
            where i.product = p and o.status = :status and o.createdAt >= :from and o.createdAt < :to)
        order by p.name asc, p.id asc
        """)
    List<UnsoldProductProjection> unsoldProducts(@Param("from") Instant from, @Param("to") Instant to,
                                                 @Param("status") OrderStatus status);
}
