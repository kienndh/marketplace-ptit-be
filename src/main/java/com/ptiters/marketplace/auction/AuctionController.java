package com.ptiters.marketplace.auction;

import com.ptiters.marketplace.order.OrderDetailResponse;
import com.ptiters.marketplace.user.CurrentUserResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST đấu giá — `/api/v1/auctions` (API_CONTRACT mục 7). GET chi tiết/theo sản phẩm/lịch sử bid là public (optional auth);
 * còn lại yêu cầu đăng nhập. Endpoint WebSocket đấu giá thuộc Track E.
 */
@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
@Validated
public class AuctionController {

    private final AuctionService auctionService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    public ResponseEntity<AuctionDetailResponse> createAuction(@Valid @RequestBody AuctionCreateRequest payload) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionService.enableAuction(currentUserResolver.getCurrentActiveUser(), payload));
    }

    @PostMapping("/{auctionId}/cancel")
    public AuctionDetailResponse cancelAuction(@PathVariable("auctionId") Long auctionId) {
        return auctionService.cancelAuction(currentUserResolver.getCurrentActiveUser(), auctionId);
    }

    @PostMapping("/products/{productId}/convert-fixed-price")
    public Map<String, String> convertToFixedPrice(@PathVariable("productId") Long productId) {
        auctionService.convertProductToFixedPrice(currentUserResolver.getCurrentActiveUser(), productId);
        return Map.of("message", "Sản phẩm đã được chuyển sang bán giá cố định thành công.");
    }

    @GetMapping("/by-product/{productId}")
    public AuctionDetailResponse getAuctionByProduct(@PathVariable("productId") Long productId) {
        return auctionService.getAuctionByProduct(productId, currentUserResolver.getOptionalCurrentActiveUser().orElse(null));
    }

    @GetMapping("/me/won")
    public List<WonAuctionItemResponse> getMyWonAuctions() {
        return auctionService.getMyWonAuctions(currentUserResolver.getCurrentActiveUser());
    }

    @GetMapping("/{auctionId}")
    public AuctionDetailResponse getAuctionDetail(@PathVariable("auctionId") Long auctionId) {
        return auctionService.getAuctionDetail(auctionId, currentUserResolver.getOptionalCurrentActiveUser().orElse(null));
    }

    @GetMapping("/{auctionId}/bids")
    public AuctionBidCursorPage listAuctionBids(
            @PathVariable("auctionId") Long auctionId,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(50) int limit) {
        return auctionService.listBids(auctionId, cursor, limit,
                currentUserResolver.getOptionalCurrentActiveUser().orElse(null));
    }

    @PostMapping("/{auctionId}/bids")
    public ResponseEntity<AuctionBidResponse> placeBid(@PathVariable("auctionId") Long auctionId,
                                                       @Valid @RequestBody PlaceBidRequest payload) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionService.placeBid(currentUserResolver.getCurrentActiveUser(), auctionId, payload));
    }

    @PostMapping("/{auctionId}/checkout")
    public ResponseEntity<OrderDetailResponse> checkoutWonAuction(@PathVariable("auctionId") Long auctionId,
                                                                  @Valid @RequestBody WinnerCheckoutRequest payload) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionService.winnerCheckout(currentUserResolver.getCurrentActiveUser(), auctionId, payload));
    }
}
