package com.ptiters.marketplace.product;

import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false, length = 12)
    @Builder.Default
    private ListingType listingType = ListingType.FIXED_PRICE;

    @Column(name = "price", nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    @Column(name = "stock", nullable = false)
    @Builder.Default
    private Integer stock = 1;

    @Column(name = "reserved_stock", nullable = false)
    @Builder.Default
    private Integer reservedStock = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private ProductStatus status = ProductStatus.AVAILABLE;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 10)
    @Builder.Default
    private ProductModerationStatus moderationStatus = ProductModerationStatus.CLEAR;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 12)
    @Builder.Default
    private ProductApprovalStatus approvalStatus = ProductApprovalStatus.DRAFT;

    @Column(name = "submitted_for_review_at")
    private Instant submittedForReviewAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, imageId ASC")
    @org.hibernate.annotations.BatchSize(size = 20)
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.listingType == null) {
            this.listingType = ListingType.FIXED_PRICE;
        }
        if (this.stock == null) {
            this.stock = 1;
        }
        if (this.reservedStock == null) {
            this.reservedStock = 0;
        }
        if (this.status == null) {
            this.status = ProductStatus.AVAILABLE;
        }
        if (this.viewCount == null) {
            this.viewCount = 0;
        }
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
        if (this.moderationStatus == null) {
            this.moderationStatus = ProductModerationStatus.CLEAR;
        }
        if (this.approvalStatus == null) {
            this.approvalStatus = ProductApprovalStatus.DRAFT;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public int getAvailableStock() {
        int s = (this.stock != null) ? this.stock : 0;
        int r = (this.reservedStock != null) ? this.reservedStock : 0;
        return Math.max(0, s - r);
    }
}
