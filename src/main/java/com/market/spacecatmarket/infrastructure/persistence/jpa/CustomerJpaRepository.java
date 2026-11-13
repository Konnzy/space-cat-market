package com.market.spacecatmarket.infrastructure.persistence.jpa;
import com.market.spacecatmarket.infrastructure.persistence.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long> { }
