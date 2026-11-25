package com.ptiters.marketplace.tutorial;

import java.util.List;
import java.util.Optional;

public interface TutorialRepository {
    Tutorial save(Tutorial tutorial);
    Optional<Tutorial> findById(Long id);
    List<Tutorial> findPublishedByFeatureKey(String featureKey);
    List<Tutorial> findAll();
    void deleteById(Long id);
}
