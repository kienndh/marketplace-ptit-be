package com.ptiters.marketplace.favorite;

import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.category.CategorySummary;
import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.product.*;
import com.ptiters.marketplace.user.CapabilityService;
import com.ptiters.marketplace.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;
    private final CapabilityService capabilityService;

    public record CursorTuple(LocalDateTime time, Long id) {}

    public static CursorTuple decodeCursor(String cursorStr) {
        if (cursorStr == null || cursorStr.isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursorStr);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            if (parts.length != 2) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid cursor.");
            }
            LocalDateTime time = LocalDateTime.parse(parts[0]);
            Long pid = Long.parseLong(parts[1]);
            return new CursorTuple(time, pid);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid cursor.");
        }
    }

    @Transactional
    public void addFavorite(User user, Long productId) {
        Product product = productRepository.findByProductIdAndIsDeletedFalse(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));

        if (product.getStatus() != ProductStatus.AVAILABLE
                || product.getApprovalStatus() != ProductApprovalStatus.APPROVED
                || product.getModerationStatus() != ProductModerationStatus.CLEAR) {
            throw ApiException.notFound("Product not found.");
        }

        if (!favoriteRepository.existsByUserIdAndProductId(user.getUserId(), productId)) {
            Favorite favorite = Favorite.builder()
                    .userId(user.getUserId())
                    .productId(productId)
                    .user(user)
                    .product(product)
                    .createdAt(LocalDateTime.now())
                    .build();
            favoriteRepository.save(favorite);
        }
    }

    @Transactional
    public void removeFavorite(User user, Long productId) {
        favoriteRepository.deleteByUserIdAndProductId(user.getUserId(), productId);
    }

    @Transactional(readOnly = true)
    public FavoriteListResponse listFavorites(User user, int limit, String cursor) {
        CursorTuple tuple = decodeCursor(cursor);
        LocalDateTime cursorTime = tuple != null ? tuple.time() : null;
        Long cursorPid = tuple != null ? tuple.id() : null;

        FavoriteRepositoryCustom.PaginatedResult<Favorite> result = favoriteRepository.listFavorites(
                user.getUserId(),
                limit,
                cursorTime,
                cursorPid
        );

        List<FavoriteProductResponse> items = result.items().stream()
                .filter(fav -> {
                    // Loại bỏ nếu seller bị SELL_RESTRICTED
                    return capabilityService.getUserAccessStatus(fav.getProduct().getSeller()).isCanSell();
                })
                .map(fav -> {
                    Product p = fav.getProduct();
                    Category c = p.getCategory();
                    User s = p.getSeller();
                    CategorySummary catSummary = c != null ? new CategorySummary(c.getCategoryId(), c.getName(), c.getSlug(), null) : null;
                    SellerSummary sellerSummary = s != null ? new SellerSummary(s.getPublicId(), s.getFullName(), s.getAvatarUrl()) : null;

                    return new FavoriteProductResponse(
                            p.getProductId(),
                            p.getTitle(),
                            p.getPrice(),
                            p.getListingType() != null ? p.getListingType().name() : ListingType.FIXED_PRICE.name(),
                            p.getStatus() != null ? p.getStatus().name() : ProductStatus.AVAILABLE.name(),
                            p.getAvailableStock(),
                            p.getViewCount() != null ? p.getViewCount() : 0,
                            ProductService.resolveThumbnailUrl(p.getImages()),
                            catSummary,
                            sellerSummary,
                            p.getCreatedAt(),
                            fav.getCreatedAt()
                    );
                })
                .toList();

        return new FavoriteListResponse(items, result.nextCursor(), result.hasMore());
    }

    @Transactional(readOnly = true)
    public FavoriteIdsResponse getFavoriteIds(User user) {
        List<Long> visibleIds = favoriteRepository.getVisibleFavoriteIds(user.getUserId());
        return new FavoriteIdsResponse(visibleIds);
    }
}
