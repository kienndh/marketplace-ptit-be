
package com.ptiters.marketplace.product;

import com.ptiters.marketplace.product.image.ProductImage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "listing_type", nullable = false, length = 12)
    private String listingType = "FIXED_PRICE";

    @Column(name = "price", nullable = false, precision = 12, scale = 0)
    private BigDecimal price;

    @Column(name = "stock", nullable = false)
    private int stock = 1;

    @Column(name = "reserved_stock", nullable = false)
    private int reservedStock = 0;

    @Column(name = "status", nullable = false, length = 10)
    private String status = "AVAILABLE";

    @Column(name = "moderation_status", nullable = false, length = 20)
    private String moderationStatus = "CLEAR";

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false,
            insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false,
            insertable = false, updatable = false)
    private LocalDateTime updatedAt;


    @OneToMany(
            mappedBy = "product",
            fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE}
    )
    @OrderBy("sortOrder ASC, imageId ASC")
    private List<ProductImage> images = new ArrayList<>();

}
