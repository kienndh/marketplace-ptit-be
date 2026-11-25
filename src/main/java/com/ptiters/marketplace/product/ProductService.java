package com.ptiters.marketplace.product;

import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.category.CategoryRepository;
import com.ptiters.marketplace.category.CategorySummary;
import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.integration.CloudinaryStorageService;
import com.ptiters.marketplace.user.CapabilityService;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final CapabilityService capabilityService;
    private final ProductViewCounter productViewCounter;
    private final CloudinaryStorageService cloudinaryStorageService;

    public record CursorTuple(LocalDateTime time, Long id) {}
    public record InstantCursorTuple(Instant time, Long id) {}

    public static CursorTuple decodeCursor(String cursorStr) {
        if (cursorStr == null || cursorStr.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursorStr);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            if (parts.length != 2) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid pagination cursor.");
            }
            LocalDateTime time = LocalDateTime.parse(parts[0]);
            Long pid = Long.parseLong(parts[1]);
            return new CursorTuple(time, pid);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid pagination cursor.");
        }
    }

    public static InstantCursorTuple decodeInstantCursor(String cursorStr) {
        if (cursorStr == null || cursorStr.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursorStr);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            if (parts.length != 2) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid pagination cursor.");
            }
            Instant time = Instant.parse(parts[0]);
            Long pid = Long.parseLong(parts[1]);
            return new InstantCursorTuple(time, pid);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid pagination cursor.");
        }
    }

    public static String resolveThumbnailUrl(List<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        for (ProductImage img : images) {
            if (Boolean.TRUE.equals(img.getIsThumbnail())) {
                return img.getImageUrl();
            }
        }
        return images.get(0).getImageUrl();
    }

    public static ProductSummaryResponse formatProductSummary(Product p) {
        Category c = p.getCategory();
        User s = p.getSeller();
        CategorySummary catSummary = c != null ? new CategorySummary(c.getCategoryId(), c.getName(), c.getSlug(), null) : null;
        SellerSummary sellerSummary = s != null ? new SellerSummary(s.getPublicId(), s.getFullName(), s.getAvatarUrl()) : null;

        return new ProductSummaryResponse(
                p.getProductId(),
                p.getTitle(),
                p.getPrice(),
                p.getListingType() != null ? p.getListingType().name() : ListingType.FIXED_PRICE.name(),
                p.getStatus() != null ? p.getStatus().name() : ProductStatus.AVAILABLE.name(),
                p.getAvailableStock(),
                p.getViewCount() != null ? p.getViewCount() : 0,
                resolveThumbnailUrl(p.getImages()),
                catSummary,
                sellerSummary,
                p.getCreatedAt()
        );
    }

    public static ProductDetailResponse formatProductDetail(Product p) {
        Category c = p.getCategory();
        User s = p.getSeller();
        CategorySummary catSummary = c != null ? new CategorySummary(c.getCategoryId(), c.getName(), c.getSlug(), null) : null;
        SellerDetailSummary sellerSummary = s != null ? new SellerDetailSummary(
                s.getPublicId(),
                s.getFullName(),
                s.getAvatarUrl(),
                s.getDeliveryArea(),
                s.getDefaultMeetupArea(),
                s.getCreatedAt(),
                null,
                0
        ) : null;

        List<ProductImageResponse> imgResponses = new ArrayList<>();
        if (p.getImages() != null) {
            for (ProductImage img : p.getImages()) {
                imgResponses.add(new ProductImageResponse(
                        img.getImageId(),
                        p.getProductId(),
                        img.getImageUrl(),
                        img.getImageUrl(),
                        img.getSortOrder(),
                        img.getIsThumbnail()
                ));
            }
        }

        return new ProductDetailResponse(
                p.getProductId(),
                p.getTitle(),
                p.getDescription(),
                p.getListingType() != null ? p.getListingType().name() : ListingType.FIXED_PRICE.name(),
                p.getPrice(),
                p.getAvailableStock(),
                p.getStatus() != null ? p.getStatus().name() : ProductStatus.AVAILABLE.name(),
                p.getModerationStatus() != null ? p.getModerationStatus().name() : ProductModerationStatus.CLEAR.name(),
                p.getApprovalStatus() != null ? p.getApprovalStatus().name() : ProductApprovalStatus.DRAFT.name(),
                p.getViewCount() != null ? p.getViewCount() : 0,
                imgResponses,
                catSummary,
                sellerSummary,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    public static SellerProductResponse formatSellerProduct(Product p) {
        Category c = p.getCategory();
        CategorySummary catSummary = c != null ? new CategorySummary(c.getCategoryId(), c.getName(), c.getSlug(), null) : null;

        return new SellerProductResponse(
                p.getProductId(),
                p.getTitle(),
                p.getDescription(),
                p.getListingType() != null ? p.getListingType().name() : ListingType.FIXED_PRICE.name(),
                p.getPrice(),
                p.getStock() != null ? p.getStock() : 1,
                p.getReservedStock() != null ? p.getReservedStock() : 0,
                p.getAvailableStock(),
                p.getStatus() != null ? p.getStatus().name() : ProductStatus.AVAILABLE.name(),
                p.getModerationStatus() != null ? p.getModerationStatus().name() : ProductModerationStatus.CLEAR.name(),
                p.getApprovalStatus() != null ? p.getApprovalStatus().name() : ProductApprovalStatus.DRAFT.name(),
                p.getSubmittedForReviewAt(),
                p.getReviewedAt(),
                p.getReviewNote(),
                p.getViewCount() != null ? p.getViewCount() : 0,
                resolveThumbnailUrl(p.getImages()),
                catSummary,
                null,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    public static AdminProductApprovalSummary formatAdminApprovalSummary(Product p) {
        Category c = p.getCategory();
        User s = p.getSeller();
        return new AdminProductApprovalSummary(
                p.getProductId(),
                p.getTitle(),
                p.getPrice(),
                c != null ? c.getCategoryId() : null,
                c != null ? c.getName() : null,
                s != null ? s.getPublicId() : null,
                s != null ? s.getFullName() : null,
                p.getApprovalStatus() != null ? p.getApprovalStatus().name() : ProductApprovalStatus.PENDING.name(),
                resolveThumbnailUrl(p.getImages()),
                p.getSubmittedForReviewAt(),
                p.getCreatedAt()
        );
    }

    public static AdminProductApprovalDetailResponse formatAdminApprovalDetail(Product p) {
        Category c = p.getCategory();
        User s = p.getSeller();
        CategorySummary catSummary = c != null ? new CategorySummary(c.getCategoryId(), c.getName(), c.getSlug(), null) : null;
        SellerDetailSummary sellerSummary = s != null ? new SellerDetailSummary(
                s.getPublicId(),
                s.getFullName(),
                s.getAvatarUrl(),
                s.getDeliveryArea(),
                s.getDefaultMeetupArea(),
                s.getCreatedAt(),
                null,
                0
        ) : null;

        List<ProductImageResponse> imgResponses = new ArrayList<>();
        if (p.getImages() != null) {
            for (ProductImage img : p.getImages()) {
                imgResponses.add(new ProductImageResponse(
                        img.getImageId(),
                        p.getProductId(),
                        img.getImageUrl(),
                        img.getImageUrl(),
                        img.getSortOrder(),
                        img.getIsThumbnail()
                ));
            }
        }

        return new AdminProductApprovalDetailResponse(
                p.getProductId(),
                p.getTitle(),
                p.getDescription(),
                p.getListingType() != null ? p.getListingType().name() : ListingType.FIXED_PRICE.name(),
                p.getPrice(),
                p.getAvailableStock(),
                p.getStatus() != null ? p.getStatus().name() : ProductStatus.AVAILABLE.name(),
                p.getModerationStatus() != null ? p.getModerationStatus().name() : ProductModerationStatus.CLEAR.name(),
                p.getApprovalStatus() != null ? p.getApprovalStatus().name() : ProductApprovalStatus.PENDING.name(),
                p.getSubmittedForReviewAt(),
                p.getReviewedAt(),
                p.getReviewNote(),
                p.getViewCount() != null ? p.getViewCount() : 0,
                imgResponses,
                catSummary,
                sellerSummary,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    @Transactional
    public ProductDetailResponse createProduct(User seller, ProductCreateRequest request) {
        // 1. Kiểm tra quyền bán hàng
        if (!capabilityService.getUserAccessStatus(seller).isCanSell()) {
            throw ApiException.forbidden("Tài khoản của bạn đang bị giới hạn quyền bán hàng.");
        }

        // 2. Kiểm tra danh mục
        Category category = categoryRepository.findById(request.categoryId())
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Category not found."));

        // 3. Khởi tạo sản phẩm
        Product product = Product.builder()
                .seller(seller)
                .category(category)
                .title(request.title().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .listingType(request.listingType() != null ? request.listingType() : ListingType.FIXED_PRICE)
                .price(request.price())
                .stock(request.stock() != null ? request.stock() : 1)
                .reservedStock(0)
                .status(ProductStatus.AVAILABLE)
                .approvalStatus(ProductApprovalStatus.DRAFT)
                .moderationStatus(ProductModerationStatus.CLEAR)
                .viewCount(0)
                .isDeleted(false)
                .build();

        Product saved = productRepository.save(product);
        return formatProductDetail(saved);
    }

    @Transactional(readOnly = true)
    public void verifyProductVisibility(Product product, User currentUser) {
        // Approval check: non-APPROVED products are only visible to owner or admin
        if (product.getApprovalStatus() != ProductApprovalStatus.APPROVED) {
            boolean isAuthorized = false;
            if (currentUser != null) {
                if (Objects.equals(currentUser.getUserId(), product.getSeller().getUserId())
                        || currentUser.getRole() == UserRole.ADMIN) {
                    isAuthorized = true;
                }
            }
            if (!isAuthorized) {
                throw ApiException.notFound("Product not found.");
            }
        }

        // Sell-restriction check: products from restricted sellers are hidden from third parties
        if (currentUser == null || (!Objects.equals(currentUser.getUserId(), product.getSeller().getUserId())
                && currentUser.getRole() != UserRole.ADMIN)) {
            if (!capabilityService.getUserAccessStatus(product.getSeller()).isCanSell()) {
                throw ApiException.notFound("Product not found.");
            }
        }

        // Moderation blocked check: only owner may view
        if (product.getModerationStatus() == ProductModerationStatus.BLOCKED) {
            if (currentUser == null || !Objects.equals(currentUser.getUserId(), product.getSeller().getUserId())) {
                throw ApiException.notFound("Product not found.");
            }
        }

        // Hidden check: only owner or admin may view
        if (product.getStatus() == ProductStatus.HIDDEN) {
            if (currentUser == null || (!Objects.equals(currentUser.getUserId(), product.getSeller().getUserId())
                    && currentUser.getRole() != UserRole.ADMIN)) {
                throw ApiException.notFound("Product not found.");
            }
        }
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetail(Long productId, User currentUser) {
        Product product = productRepository.findDetailById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));

        verifyProductVisibility(product, currentUser);
        return formatProductDetail(product);
    }

    @Transactional(readOnly = true)
    public ProductListResponse listPublicProducts(
            Integer categoryId,
            Long sellerId,
            UUID sellerPublicId,
            ListingType listingType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String query,
            int limit,
            String cursor
    ) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "min_price cannot be greater than max_price.");
        }

        CursorTuple tuple = decodeCursor(cursor);
        LocalDateTime cursorTime = tuple != null ? tuple.time() : null;
        Long cursorPid = tuple != null ? tuple.id() : null;

        ProductRepositoryCustom.PaginatedResult<Product> result = productRepository.listPublic(
                categoryId,
                sellerId,
                sellerPublicId,
                listingType,
                minPrice,
                maxPrice,
                query,
                limit,
                cursorTime,
                cursorPid
        );

        List<ProductSummaryResponse> items = result.items().stream()
                .map(ProductService::formatProductSummary)
                .toList();

        return new ProductListResponse(items, result.nextCursor(), result.hasMore());
    }

    @Transactional(readOnly = true)
    public SellerProductListResponse listSellerProducts(
            User seller,
            ProductStatus status,
            ProductApprovalStatus approvalStatus,
            int limit,
            String cursor
    ) {
        CursorTuple tuple = decodeCursor(cursor);
        LocalDateTime cursorTime = tuple != null ? tuple.time() : null;
        Long cursorPid = tuple != null ? tuple.id() : null;

        ProductRepositoryCustom.PaginatedResult<Product> result = productRepository.listBySeller(
                seller.getUserId(),
                status,
                approvalStatus,
                limit,
                cursorTime,
                cursorPid
        );

        List<SellerProductResponse> items = result.items().stream()
                .map(ProductService::formatSellerProduct)
                .toList();

        return new SellerProductListResponse(items, result.nextCursor(), result.hasMore());
    }

    @Transactional
    public ProductDetailResponse updateProduct(Long productId, User seller, ProductUpdateRequest request) {
        Product product = productRepository.findDetailById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));

        if (!Objects.equals(product.getSeller().getUserId(), seller.getUserId())) {
            throw ApiException.forbidden("Chỉ người bán sở hữu tin đăng mới có quyền chỉnh sửa.");
        }

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .filter(c -> !c.isDeleted())
                    .orElseThrow(() -> ApiException.notFound("Category not found."));
            product.setCategory(category);
        }

        if (request.title() != null && !request.title().isBlank()) {
            product.setTitle(request.title().trim());
        }

        if (request.description() != null) {
            product.setDescription(request.description().trim());
        }

        if (request.price() != null) {
            product.setPrice(request.price());
        }

        if (request.status() != null) {
            product.setStatus(request.status());
        }

        if (request.stock() != null) {
            product.setStock(request.stock());
        }

        Product saved = productRepository.save(product);
        return formatProductDetail(saved);
    }

    @Transactional
    public void softDeleteProduct(Long productId, User seller) {
        Product product = productRepository.findByProductIdAndIsDeletedFalse(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));

        if (!Objects.equals(product.getSeller().getUserId(), seller.getUserId())) {
            throw ApiException.forbidden("Chỉ người bán sở hữu tin đăng mới có quyền xóa.");
        }

        product.setIsDeleted(true);
        product.setStatus(ProductStatus.HIDDEN);
        productRepository.save(product);
    }

    @Transactional
    public SellerProductResponse submitForReview(Long productId, User seller) {
        Product product = productRepository.findDetailById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));

        if (!Objects.equals(product.getSeller().getUserId(), seller.getUserId())) {
            throw ApiException.forbidden("Chỉ người bán sở hữu tin đăng mới có thể gửi duyệt.");
        }

        if (product.getImages() == null || product.getImages().isEmpty()) {
            throw ApiException.conflict("Tin đăng cần có ít nhất 1 hình ảnh trước khi gửi duyệt.");
        }

        if (product.getModerationStatus() != ProductModerationStatus.CLEAR) {
            throw ApiException.conflict("Tin đăng đang bị quản trị viên ẩn, không thể gửi duyệt.");
        }

        if (product.getApprovalStatus() != ProductApprovalStatus.DRAFT && product.getApprovalStatus() != ProductApprovalStatus.REJECTED) {
            throw ApiException.conflict("Tin đăng đang ở trạng thái '" + product.getApprovalStatus() + "', không thể gửi duyệt.");
        }

        product.setApprovalStatus(ProductApprovalStatus.PENDING);
        product.setSubmittedForReviewAt(Instant.now());
        product.setReviewNote(null);

        Product saved = productRepository.save(product);
        return formatSellerProduct(saved);
    }

    @Transactional
    public ProductImageResponse uploadProductImage(Long productId, User seller, Object file) {
        Product product = productRepository.findDetailById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));

        if (!Objects.equals(product.getSeller().getUserId(), seller.getUserId())) {
            throw ApiException.forbidden("Chỉ người bán sở hữu tin đăng mới có quyền tải ảnh.");
        }

        if (product.getImages().size() >= 10) {
            throw ApiException.badRequest("Mỗi sản phẩm chỉ được tải lên tối đa 10 ảnh.");
        }

        String imageUrl = "https://res.cloudinary.com/synthetic/image/upload/v1/products/" + productId + "/" + UUID.randomUUID() + ".jpg";
        short nextOrder = (short) product.getImages().size();
        boolean isThumb = product.getImages().isEmpty();

        ProductImage img = ProductImage.builder()
                .product(product)
                .imageUrl(imageUrl)
                .sortOrder(nextOrder)
                .isThumbnail(isThumb)
                .build();

        ProductImage savedImg = productImageRepository.save(img);
        product.getImages().add(savedImg);

        return new ProductImageResponse(
                savedImg.getImageId(),
                productId,
                savedImg.getImageUrl(),
                savedImg.getImageUrl(),
                savedImg.getSortOrder(),
                savedImg.getIsThumbnail()
        );
    }

    @Transactional
    public void deleteProductImage(Long productId, Long imageId, User seller) {
        Product product = productRepository.findDetailById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));

        if (!Objects.equals(product.getSeller().getUserId(), seller.getUserId())) {
            throw ApiException.forbidden("Chỉ người bán sở hữu tin đăng mới có quyền xóa ảnh.");
        }

        ProductImage target = product.getImages().stream()
                .filter(img -> Objects.equals(img.getImageId(), imageId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Product image not found."));

        boolean wasThumbnail = Boolean.TRUE.equals(target.getIsThumbnail());
        product.getImages().remove(target);
        productImageRepository.delete(target);

        if (wasThumbnail && !product.getImages().isEmpty()) {
            product.getImages().get(0).setIsThumbnail(true);
            productImageRepository.save(product.getImages().get(0));
        }
    }

    @Transactional
    public void setProductThumbnail(Long productId, Long imageId, User seller) {
        Product product = productRepository.findDetailById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));

        if (!Objects.equals(product.getSeller().getUserId(), seller.getUserId())) {
            throw ApiException.forbidden("Chỉ người bán sở hữu tin đăng mới có quyền chọn ảnh bìa.");
        }

        boolean found = false;
        for (ProductImage img : product.getImages()) {
            if (Objects.equals(img.getImageId(), imageId)) {
                img.setIsThumbnail(true);
                found = true;
            } else {
                img.setIsThumbnail(false);
            }
            productImageRepository.save(img);
        }

        if (!found) {
            throw ApiException.notFound("Product image not found.");
        }
    }

    @Transactional
    public void reorderProductImages(Long productId, User seller, ImageReorderRequest request) {
        Product product = productRepository.findDetailById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));

        if (!Objects.equals(product.getSeller().getUserId(), seller.getUserId())) {
            throw ApiException.forbidden("Chỉ người bán sở hữu tin đăng mới có quyền sắp xếp ảnh.");
        }

        if (request.images() != null) {
            Map<Long, Short> orderMap = new HashMap<>();
            for (ImageReorderItem item : request.images()) {
                orderMap.put(item.imageId(), item.sortOrder());
            }

            for (ProductImage img : product.getImages()) {
                if (orderMap.containsKey(img.getImageId())) {
                    img.setSortOrder(orderMap.get(img.getImageId()));
                    productImageRepository.save(img);
                }
            }
        }
    }

    @Transactional
    public ProductViewResponse registerProductView(Long productId, User currentUser, UUID anonymousViewerId) {
        Product product = productRepository.findByProductIdAndIsDeletedFalse(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));

        Long userId = currentUser != null ? currentUser.getUserId() : null;
        ProductViewCounter.ViewRegistrationResult res = productViewCounter.registerView(product, userId, anonymousViewerId);

        return new ProductViewResponse(productId, res.viewCount(), res.counted());
    }

    @Transactional(readOnly = true)
    public AdminProductApprovalListResponse listAdminApprovals(String state, int limit, String cursor) {
        ProductApprovalStatus approvalStatus;
        try {
            approvalStatus = state != null ? ProductApprovalStatus.valueOf(state.toUpperCase()) : ProductApprovalStatus.PENDING;
        } catch (IllegalArgumentException e) {
            approvalStatus = ProductApprovalStatus.PENDING;
        }

        InstantCursorTuple tuple = decodeInstantCursor(cursor);
        Instant cursorTime = tuple != null ? tuple.time() : null;
        Long cursorPid = tuple != null ? tuple.id() : null;

        ProductRepositoryCustom.PaginatedResult<Product> result = productRepository.listAdminQueue(
                approvalStatus,
                limit,
                cursorTime,
                cursorPid
        );

        List<AdminProductApprovalSummary> items = result.items().stream()
                .map(ProductService::formatAdminApprovalSummary)
                .toList();

        return new AdminProductApprovalListResponse(items, result.nextCursor(), result.hasMore());
    }

    @Transactional(readOnly = true)
    public AdminProductApprovalDetailResponse getAdminApprovalDetail(Long productId) {
        Product product = productRepository.findDetailById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));

        return formatAdminApprovalDetail(product);
    }

    @Transactional
    public AdminProductApprovalDetailResponse adminDecision(Long productId, User admin, AdminApprovalDecisionRequest request) {
        Product product = productRepository.findDetailById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));

        if (product.getApprovalStatus() != ProductApprovalStatus.PENDING) {
            throw ApiException.conflict("Tin đăng đang ở trạng thái '" + product.getApprovalStatus() + "', không thể xử lý duyệt.");
        }

        Instant now = Instant.now();
        if ("APPROVE".equalsIgnoreCase(request.decision())) {
            product.setApprovalStatus(ProductApprovalStatus.APPROVED);
            product.setReviewedBy(admin);
            product.setReviewedAt(now);
            product.setReviewNote(null);
        } else if ("REJECT".equalsIgnoreCase(request.decision())) {
            if (request.note() == null || request.note().isBlank()) {
                throw ApiException.badRequest("Vui lòng cung cấp lý do từ chối (1..500 ký tự).");
            }
            String note = request.note().trim();
            if (note.length() > 500) {
                throw ApiException.badRequest("Lý do từ chối không được vượt quá 500 ký tự.");
            }
            product.setApprovalStatus(ProductApprovalStatus.REJECTED);
            product.setReviewedBy(admin);
            product.setReviewedAt(now);
            product.setReviewNote(note);
        } else {
            throw ApiException.badRequest("Quyết định không hợp lệ.");
        }

        Product saved = productRepository.save(product);
        return formatAdminApprovalDetail(saved);
    }
}
