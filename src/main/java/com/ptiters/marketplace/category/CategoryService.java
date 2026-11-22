package com.ptiters.marketplace.category;

import com.ptiters.marketplace.exception.ApiException;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {
    private CategoryRepository categoryRepository;
    private ModelMapper modelMapper;

    public List<CategoryResponse> listActive(){
        return categoryRepository.findByDeletedFalseOrderByCategoryIdAsc()
                .stream().map(category -> modelMapper.map(category, CategoryResponse.class))
                .toList();
    }

    public CategoryResponse getActive(Integer categoryId){
        return categoryRepository.findByCategoryIdAndDeletedFalse(categoryId)
                .map(category -> modelMapper.map(category, CategoryResponse.class))
                .orElseThrow(() -> ApiException.notFound("Category not found."));
    }
}
