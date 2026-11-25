package com.ptiters.marketplace.tutorial;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TutorialQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public List<Tutorial> findPublishedTutorials(String featureKey) {
        StringBuilder jpql = new StringBuilder("SELECT t FROM Tutorial t WHERE t.isPublished = true ");
        if (featureKey != null && !featureKey.isBlank()) {
            jpql.append("AND t.featureKey = :featureKey ");
        }
        jpql.append("ORDER BY t.featureKey ASC, t.sortOrder ASC, t.tutorialId ASC");

        TypedQuery<Tutorial> query = em.createQuery(jpql.toString(), Tutorial.class);
        if (featureKey != null && !featureKey.isBlank()) {
            query.setParameter("featureKey", featureKey.trim());
        }
        return query.getResultList();
    }

    public List<Tutorial> findAllTutorials(String featureKey) {
        StringBuilder jpql = new StringBuilder("SELECT t FROM Tutorial t ");
        if (featureKey != null && !featureKey.isBlank()) {
            jpql.append("WHERE t.featureKey = :featureKey ");
        }
        jpql.append("ORDER BY t.featureKey ASC, t.sortOrder ASC, t.tutorialId ASC");

        TypedQuery<Tutorial> query = em.createQuery(jpql.toString(), Tutorial.class);
        if (featureKey != null && !featureKey.isBlank()) {
            query.setParameter("featureKey", featureKey.trim());
        }
        return query.getResultList();
    }

    public Optional<Tutorial> findByIdForUpdate(Long tutorialId) {
        List<Tutorial> results = em.createQuery(
                        "SELECT t FROM Tutorial t WHERE t.tutorialId = :tutorialId",
                        Tutorial.class)
                .setParameter("tutorialId", tutorialId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return results.stream().findFirst();
    }

    public List<Tutorial> findByIdsForUpdate(List<Long> tutorialIds) {
        if (tutorialIds == null || tutorialIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        "SELECT t FROM Tutorial t WHERE t.tutorialId IN :ids",
                        Tutorial.class)
                .setParameter("ids", tutorialIds)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    public Optional<Tutorial> findByStorageKey(String videoStorageKey) {
        List<Tutorial> results = em.createQuery(
                        "SELECT t FROM Tutorial t WHERE t.videoStorageKey = :key",
                        Tutorial.class)
                .setParameter("key", videoStorageKey)
                .getResultList();
        return results.stream().findFirst();
    }

    public int getMaxSortOrder(String featureKey) {
        Integer max = em.createQuery(
                        "SELECT COALESCE(MAX(t.sortOrder), 0) FROM Tutorial t WHERE t.featureKey = :featureKey",
                        Integer.class)
                .setParameter("featureKey", featureKey)
                .getSingleResult();
        return max != null ? Math.max(0, max) : 0;
    }
}
