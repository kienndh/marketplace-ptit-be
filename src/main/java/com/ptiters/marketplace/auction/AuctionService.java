package com.ptiters.marketplace.auction;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AuctionService {

    public AuctionDetailResponse enableAuction(Object seller, AuctionCreateRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AuctionDetailResponse cancelAuction(Object seller, Long auctionId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void convertProductToFixedPrice(Object seller, Long productId) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AuctionDetailResponse getAuctionByProduct(Long productId, Object currentUser) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public List<WonAuctionItemResponse> getMyWonAuctions(Object currentUser) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AuctionDetailResponse getAuctionDetail(Long auctionId, Object currentUser) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AuctionBidCursorPage listBids(Long auctionId, String cursor, int limit, Object currentUser) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public AuctionBidResponse placeBid(Object currentUser, Long auctionId, PlaceBidRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public Object checkoutWonAuction(Object currentUser, Long auctionId, WinnerCheckoutRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }
}
