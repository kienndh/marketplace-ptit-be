package com.ptiters.marketplace.auction;

import java.util.Optional;

/**
 * Repository interface for auctions table (PostgreSQL: auctions).
 */
public interface AuctionRepository {
    Optional<Auction> findById(Long auctionId);
    Optional<Auction> findByProductId(Long productId);
    Auction save(Auction auction);
}
