package com.ptiters.marketplace.auction;

import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entity ánh xạ bảng PostgreSQL `auctions` (Wave 0, khớp schema DB_v7 / Alembic).
 */
@Entity
@Table(name = "auctions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auction_id", nullable = false)
    private Long auctionId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "starting_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal startingPrice;

    @Column(name = "bid_step", nullable = false, precision = 12, scale = 0)
    private BigDecimal bidStep;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private AuctionStatus status = AuctionStatus.SCHEDULED;

    @Column(name = "winner_id")
    private Long winnerId;

    @Column(name = "final_price", precision = 12, scale = 0)
    private BigDecimal finalPrice;

    @Column(name = "winner_checkout_deadline")
    private LocalDateTime winnerCheckoutDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id", insertable = false, updatable = false)
    private User winner;

    @OneToMany(mappedBy = "auction", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("amount DESC, createdAt ASC, bidId ASC")
    @Builder.Default
    private List<AuctionBid> bids = new ArrayList<>();

    @PrePersist
    protected void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        }
        if (this.startsAt == null) {
            this.startsAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        }
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

}
