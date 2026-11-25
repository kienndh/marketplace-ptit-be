package com.ptiters.marketplace.auction;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    @PostMapping
    public ResponseEntity<AuctionDetailResponse> createAuction(@RequestBody AuctionCreateRequest payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auctionService.enableAuction(null, payload));
    }

    @PostMapping("/{auctionId}/cancel")
    public ResponseEntity<AuctionDetailResponse> cancelAuction(@PathVariable("auctionId") Long auctionId) {
        return ResponseEntity.ok(auctionService.cancelAuction(null, auctionId));
    }

    @PostMapping("/products/{productId}/convert-fixed-price")
    public ResponseEntity<Map<String, String>> convertToFixedPrice(@PathVariable("productId") Long productId) {
        auctionService.convertProductToFixedPrice(null, productId);
        return ResponseEntity.ok(Map.of("message", "Sản phẩm đã được chuyển sang bán giá cố định thành công."));
    }

    @GetMapping("/by-product/{productId}")
    public ResponseEntity<AuctionDetailResponse> getAuctionByProduct(@PathVariable("productId") Long productId) {
        return ResponseEntity.ok(auctionService.getAuctionByProduct(productId, null));
    }

    @GetMapping("/me/won")
    public ResponseEntity<List<WonAuctionItemResponse>> getMyWonAuctions() {
        return ResponseEntity.ok(auctionService.getMyWonAuctions(null));
    }

    @GetMapping("/{auctionId}")
    public ResponseEntity<AuctionDetailResponse> getAuctionDetail(@PathVariable("auctionId") Long auctionId) {
        return ResponseEntity.ok(auctionService.getAuctionDetail(auctionId, null));
    }

    @GetMapping("/{auctionId}/bids")
    public ResponseEntity<AuctionBidCursorPage> listAuctionBids(
            @PathVariable("auctionId") Long auctionId,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(auctionService.listBids(auctionId, cursor, limit, null));
    }

    @PostMapping("/{auctionId}/bids")
    public ResponseEntity<AuctionBidResponse> placeBid(
            @PathVariable("auctionId") Long auctionId,
            @RequestBody PlaceBidRequest payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auctionService.placeBid(null, auctionId, payload));
    }

    @PostMapping("/{auctionId}/checkout")
    public ResponseEntity<Object> checkoutWonAuction(
            @PathVariable("auctionId") Long auctionId,
            @RequestBody WinnerCheckoutRequest payload) {
        return ResponseEntity.ok(auctionService.checkoutWonAuction(null, auctionId, payload));
    }
}
