package com.ptiters.marketplace.category;

import java.util.UUID;

public class CategoryTestFixtures {

    public static Category createSyntheticCategory(String namePrefix) {
        Category category = new Category();
        category.setName(namePrefix + " Category");
        category.setSlug(namePrefix.toLowerCase().replace(" ", "-") + "-" + UUID.randomUUID());
        category.setDeleted(false);
        return category;
    }
}
