package com.ptiters.marketplace.auction;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho Auction. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface AuctionRepository extends JpaRepository<Auction, Long> {
}
