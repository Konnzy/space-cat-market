package com.market.spacecatmarket.application;
import com.market.spacecatmarket.infrastructure.persistence.entity.CustomerEntity;
import com.market.spacecatmarket.infrastructure.persistence.jpa.CustomerJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly = true)
public class CustomerService {
    private final CustomerJpaRepository repository;
    public CustomerService(CustomerJpaRepository repository) { this.repository = repository; }
    @Transactional
    public CustomerEntity create(String name, String email) { return repository.save(new CustomerEntity(name, email)); }
    public CustomerEntity findById(long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }
}
