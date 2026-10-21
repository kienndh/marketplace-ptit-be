package com.ptiters.marketplace.category;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@AllArgsConstructor
@Validated
public class CategoryController {
    private CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> list(){
        return categoryService.listActive();
    }

    @GetMapping("/{categoryid}")
    public CategoryResponse get(@PathVariable(name = "categoryid") Integer categoryId){
        return categoryService.getActive(categoryId);
    }

}
