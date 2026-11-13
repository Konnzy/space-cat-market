package com.market.spacecatmarket.infrastructure.persistence;

import com.market.spacecatmarket.application.InvalidCursorException;
import com.market.spacecatmarket.application.ProductPage;
import com.market.spacecatmarket.application.dto.*;
import com.market.spacecatmarket.infrastructure.persistence.entity.ProductEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Limits, filtering, counting and lexicographic cursor predicates execute in the database. */
@Repository
public class ProductPageQuery {
    private final EntityManager em;
    public ProductPageQuery(EntityManager em) { this.em = em; }

    public ProductPage findPage(PaginationRequest page, ProductFilter filter) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        long total = count(filter, null);
        ProductEntity anchor = null;
        if (page.cursor() != null) {
            // A cursor must belong to the current visible/filtered catalog.
            if (count(filter, page.cursor()) == 0) throw new InvalidCursorException(page.cursor());
            anchor = em.createQuery("select p from ProductEntity p where p.publicId=:id", ProductEntity.class)
                .setParameter("id", page.cursor()).getSingleResult();
        }
        int number = anchor == null ? page.page() : 0;
        long offset = (long) number * page.size();
        if (offset > Integer.MAX_VALUE) return new ProductPage(List.of(), number, page.size(), total, null);

        CriteriaQuery<ProductEntity> query = cb.createQuery(ProductEntity.class);
        Root<ProductEntity> p = query.from(ProductEntity.class);
        p.fetch("category", JoinType.INNER);
        List<Predicate> predicates = predicates(p, cb, filter);
        if (anchor != null) predicates.add(after(p, cb, filter, anchor));
        query.where(predicates.toArray(Predicate[]::new));
        String field = field(filter);
        boolean asc = filter.direction() == ProductFilter.Direction.ASC;
        query.orderBy(asc ? cb.asc(p.get(field)) : cb.desc(p.get(field)),
            asc ? cb.asc(p.get("id")) : cb.desc(p.get("id")));
        List<ProductEntity> rows = em.createQuery(query).setFirstResult((int) offset)
            .setMaxResults(page.size() + 1).getResultList();
        boolean hasMore = rows.size() > page.size();
        List<ProductEntity> content = hasMore ? rows.subList(0, page.size()) : rows;
        UUID next = hasMore ? content.getLast().getPublicId() : null;
        return new ProductPage(content.stream().map(ProductEntity::toDomain).toList(), number, page.size(), total, next);
    }

    private long count(ProductFilter filter, UUID id) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<ProductEntity> p = query.from(ProductEntity.class);
        List<Predicate> predicates = predicates(p, cb, filter);
        if (id != null) predicates.add(cb.equal(p.get("publicId"), id));
        return em.createQuery(query.select(cb.count(p)).where(predicates.toArray(Predicate[]::new))).getSingleResult();
    }

    private List<Predicate> predicates(Root<ProductEntity> p, CriteriaBuilder cb, ProductFilter filter) {
        List<Predicate> result = new ArrayList<>();
        result.add(cb.isTrue(p.get("active")));
        if (filter.name() != null) {
            String escaped = filter.name().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            result.add(cb.like(cb.lower(p.get("name")), "%" + escaped + "%", '\\'));
        }
        if (filter.category() != null) result.add(cb.equal(p.get("category").get("code"), filter.category()));
        if (filter.minPrice() != null) result.add(cb.ge(p.get("price"), filter.minPrice()));
        if (filter.maxPrice() != null) result.add(cb.le(p.get("price"), filter.maxPrice()));
        if (filter.inStock() != null) result.add(filter.inStock()
            ? cb.gt(p.get("stockQuantity"), 0) : cb.equal(p.get("stockQuantity"), 0));
        return result;
    }

    private String field(ProductFilter filter) {
        return switch (filter.sort()) { case ID -> "id"; case NAME -> "name"; case PRICE -> "price"; };
    }

    private Predicate after(Root<ProductEntity> p, CriteriaBuilder cb, ProductFilter filter, ProductEntity anchor) {
        boolean asc = filter.direction() == ProductFilter.Direction.ASC;
        Predicate id = asc ? cb.gt(p.get("id"), anchor.getId()) : cb.lt(p.get("id"), anchor.getId());
        return switch (filter.sort()) {
            case ID -> id;
            case PRICE -> cb.or(asc ? cb.greaterThan(p.get("price"), anchor.getPrice())
                : cb.lessThan(p.get("price"), anchor.getPrice()), cb.and(cb.equal(p.get("price"), anchor.getPrice()), id));
            case NAME -> cb.or(asc ? cb.greaterThan(p.get("name"), anchor.getName())
                : cb.lessThan(p.get("name"), anchor.getName()), cb.and(cb.equal(p.get("name"), anchor.getName()), id));
        };
    }
}
