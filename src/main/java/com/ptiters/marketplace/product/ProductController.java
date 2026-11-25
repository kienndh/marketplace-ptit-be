package com.ptiters.marketplace.product;

import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<ProductListResponse> listProducts(
            @RequestParam(value = "category_id", required = false) Integer categoryId,
            @RequestParam(value = "seller_id", required = false) Long sellerId,
            @RequestParam(value = "seller_public_id", required = false) UUID sellerPublicId,
            @RequestParam(value = "listing_type", required = false) ListingType listingType,
            @RequestParam(value = "min_price", required = false) BigDecimal minPrice,
            @RequestParam(value = "max_price", required = false) BigDecimal maxPrice,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        ProductListResponse response = productService.listPublicProducts(
                categoryId,
                sellerId,
                sellerPublicId,
                listingType,
                minPrice,
                maxPrice,
                q,
                limit,
                cursor
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<SellerProductListResponse> listMyProducts(
            @RequestParam(value = "status", required = false) ProductStatus status,
            @RequestParam(value = "approval_status", required = false) ProductApprovalStatus approvalStatus,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        SellerProductListResponse response = productService.listSellerProducts(
                currentUser,
                status,
                approvalStatus,
                limit,
                cursor
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}/submit-review")
    public ResponseEntity<SellerProductResponse> submitProductReview(@PathVariable Long productId) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        SellerProductResponse response = productService.submitForReview(productId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductDetailResponse> getProductDetail(@PathVariable Long productId) {
        Optional<User> currentUserOpt = currentUserResolver.getOptionalCurrentActiveUser();
        ProductDetailResponse response = productService.getProductDetail(productId, currentUserOpt.orElse(null));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}/view")
    public ResponseEntity<ProductViewResponse> registerProductView(
            @PathVariable Long productId,
            @RequestBody(required = false) ProductViewRequest request
    ) {
        Optional<User> currentUserOpt = currentUserResolver.getOptionalCurrentActiveUser();
        UUID anonymousId = request != null ? request.anonymousViewerId() : null;
        ProductViewResponse response = productService.registerProductView(productId, currentUserOpt.orElse(null), anonymousId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ProductDetailResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        ProductDetailResponse response = productService.createProduct(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{productId}")
    public ResponseEntity<ProductDetailResponse> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        ProductDetailResponse response = productService.updateProduct(productId, currentUser, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        productService.softDeleteProduct(productId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/images/upload")
    public ResponseEntity<ProductImageResponse> uploadProductImage(
            @PathVariable Long productId,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        ProductImageResponse response = productService.uploadProductImage(productId, currentUser, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    public ResponseEntity<Void> deleteProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        productService.deleteProductImage(productId, imageId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{productId}/images/{imageId}/thumbnail")
    public ResponseEntity<Map<String, String>> setProductThumbnail(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        productService.setProductThumbnail(productId, imageId, currentUser);
        return ResponseEntity.ok(Map.of("message", "Thumbnail updated successfully."));
    }

    @PutMapping("/{productId}/images/reorder")
    public ResponseEntity<Map<String, String>> reorderProductImages(
            @PathVariable Long productId,
            @RequestBody ImageReorderRequest request
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        productService.reorderProductImages(productId, currentUser, request);
        return ResponseEntity.ok(Map.of("message", "Images reordered successfully."));
    }
}
