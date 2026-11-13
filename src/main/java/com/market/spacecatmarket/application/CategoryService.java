package com.market.spacecatmarket.application;
import com.market.spacecatmarket.domain.Category;
import com.market.spacecatmarket.infrastructure.persistence.entity.CategoryEntity;
import com.market.spacecatmarket.infrastructure.persistence.jpa.CategoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryJpaRepository repository;
    public CategoryService(CategoryJpaRepository repository) { this.repository = repository; }
    @Transactional
    public CategoryEntity create(Category code, String name) { return repository.save(new CategoryEntity(code, name)); }
    public CategoryEntity findById(long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
    public List<CategoryEntity> findAll() { return repository.findAll(org.springframework.data.domain.Sort.by("id")); }
    @Transactional
    public CategoryEntity update(long id, String name) {
        CategoryEntity category = findById(id);
        category.rename(name);
        return category;
    }
    @Transactional
    public void delete(long id) { repository.delete(findById(id)); }
}
