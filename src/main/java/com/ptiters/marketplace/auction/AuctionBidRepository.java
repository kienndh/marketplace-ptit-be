package com.ptiters.marketplace.auction;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho AuctionBid. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long> {
}
