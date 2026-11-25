package com.ptiters.marketplace.auction;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho Auction. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface AuctionRepository extends JpaRepository<Auction, Long> {

    /** Python: {@code get_active_auction_by_product_id} — phiên SCHEDULED/ACTIVE của sản phẩm. */
    boolean existsByProductIdAndStatusIn(Long productId, java.util.Collection<AuctionStatus> statuses);
}
