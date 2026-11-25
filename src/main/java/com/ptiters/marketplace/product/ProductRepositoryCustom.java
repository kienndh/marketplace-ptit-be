package com.ptiters.marketplace.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ProductRepositoryCustom {

    record PaginatedResult<T>(
            List<T> items,
            boolean hasMore,
            String nextCursor
    ) {}

    PaginatedResult<Product> listPublic(
            Integer categoryId,
            Long sellerId,
            UUID sellerPublicId,
            ListingType listingType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String searchQuery,
            int limit,
            LocalDateTime cursorTime,
            Long cursorPid
    );

    PaginatedResult<Product> listBySeller(
            Long sellerId,
            ProductStatus status,
            ProductApprovalStatus approvalStatus,
            int limit,
            LocalDateTime cursorTime,
            Long cursorPid
    );

    PaginatedResult<Product> listAdminQueue(
            ProductApprovalStatus approvalStatus,
            int limit,
            Instant cursorTime,
            Long cursorPid
    );
}
