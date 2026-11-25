package com.ptiters.marketplace.auction;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for auction_bids table (PostgreSQL: auction_bids).
 */
public interface AuctionBidRepository {
    Optional<AuctionBid> findById(Long bidId);
    List<AuctionBid> findByAuctionId(Long auctionId);
    AuctionBid save(AuctionBid bid);
}
