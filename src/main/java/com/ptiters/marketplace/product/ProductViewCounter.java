package com.ptiters.marketplace.product;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProductViewCounter {

    private final ProductRepository productRepository;
    private final Map<String, Long> dedupeCache = new ConcurrentHashMap<>();
    private static final long DEDUPE_TTL_MS = 24L * 60L * 60L * 1000L; // 24 hours

    public ProductViewCounter(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public record ViewRegistrationResult(
            int viewCount,
            boolean counted
    ) {}

    public ViewRegistrationResult registerView(Product product, Long userId, UUID anonymousId) {
        String token = buildViewerToken(userId, anonymousId);
        Long productId = product.getProductId();
        int currentCount = product.getViewCount() != null ? product.getViewCount() : 0;

        if (token == null) {
            // No viewer identifier available, do not increment view count
            return new ViewRegistrationResult(currentCount, false);
        }

        String dedupeKey = productId + ":" + token;
        long now = System.currentTimeMillis();
        Long lastSeen = dedupeCache.get(dedupeKey);

        if (lastSeen != null && (now - lastSeen) < DEDUPE_TTL_MS) {
            // Duplicate view within 24 hours: do not increment
            return new ViewRegistrationResult(currentCount, false);
        }

        // New view: mark seen and increment
        dedupeCache.put(dedupeKey, now);
        int newCount = currentCount + 1;
        productRepository.updateViewCountFloor(productId, newCount);
        product.setViewCount(newCount);

        return new ViewRegistrationResult(newCount, true);
    }

    private String buildViewerToken(Long userId, UUID anonymousId) {
        if (userId != null) {
            return "u:" + userId;
        }
        if (anonymousId != null) {
            return "a:" + anonymousId;
        }
        return null;
    }
}
