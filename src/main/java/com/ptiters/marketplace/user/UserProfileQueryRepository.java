package com.ptiters.marketplace.user;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class UserProfileQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public int countActiveListings(Long userId) {
        String sql = """
            SELECT COUNT(p.product_id)
            FROM products p
            WHERE p.seller_id = :userId
              AND p.is_deleted = false
              AND p.status = 'AVAILABLE'
              AND p.approval_status = 'APPROVED'
              AND p.moderation_status = 'CLEAR'
              AND NOT EXISTS (
                  SELECT 1 FROM user_capability_restrictions ucr
                  WHERE ucr.user_id = :userId
                    AND ucr.capability = 'SELL'
                    AND ucr.revoked_at IS NULL
                    AND ucr.starts_at <= CURRENT_TIMESTAMP
                    AND (ucr.expires_at IS NULL OR ucr.expires_at > CURRENT_TIMESTAMP)
              )
              AND (
                  p.listing_type = 'FIXED_PRICE'
                  OR EXISTS (
                      SELECT 1 FROM auctions a
                      WHERE a.product_id = p.product_id
                        AND a.status = 'ACTIVE'
                  )
              )
            """;
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
        return count != null ? count : 0;
    }

    public int countCompletedSold(Long userId) {
        String sql = """
            SELECT COALESCE(SUM(oi.quantity), 0)
            FROM order_items oi
            JOIN orders o ON o.order_id = oi.order_id
            WHERE o.seller_id = :userId
              AND o.status = 'COMPLETED'
            """;
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
        return count != null ? count : 0;
    }

    public record ReviewStats(Double averageRating, int reviewCount) {}

    public ReviewStats getReviewStats(Long userId) {
        String sql = """
            SELECT AVG(r.rating), COUNT(r.review_id)
            FROM reviews r
            WHERE r.reviewee_id = :userId
            """;
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) -> {
            BigDecimal avg = rs.getBigDecimal(1);
            int count = rs.getInt(2);
            Double formattedAvg = null;
            if (avg != null) {
                formattedAvg = avg.setScale(1, RoundingMode.HALF_UP).doubleValue();
            }
            return new ReviewStats(formattedAvg, count);
        });
    }

    public SoldProductListResponse getSoldProducts(Long userId) {
        String sql = """
            SELECT 
              oi.product_id,
              oi.product_title,
              oi.product_image_url,
              COALESCE(SUM(oi.quantity), 0) AS total_sold_quantity,
              MAX(o.completed_at) AS last_sold_at
            FROM order_items oi
            JOIN orders o ON o.order_id = oi.order_id
            WHERE o.seller_id = :userId
              AND o.status = 'COMPLETED'
            GROUP BY oi.product_id, oi.product_title, oi.product_image_url
            ORDER BY MAX(o.completed_at) DESC NULLS LAST
            """;
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);

        List<SoldProductSummary> summaries = jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            SoldProductSummary summary = new SoldProductSummary();
            summary.setProductId(rs.getLong("product_id"));
            summary.setProductTitle(rs.getString("product_title"));
            summary.setProductImageUrl(rs.getString("product_image_url"));
            summary.setTotalSoldQuantity(rs.getInt("total_sold_quantity"));
            Timestamp lastSoldTs = rs.getTimestamp("last_sold_at");
            if (lastSoldTs != null) {
                summary.setLastSoldAt(lastSoldTs.toLocalDateTime());
            }
            return summary;
        });

        if (summaries.isEmpty()) {
            return new SoldProductListResponse(Collections.emptyList(), 0);
        }

        List<Long> productIds = summaries.stream().map(SoldProductSummary::getProductId).toList();

        String reviewSql = """
            SELECT 
              oi.product_id,
              r.rating,
              r.comment,
              r.created_at
            FROM reviews r
            JOIN orders o ON o.order_id = r.order_id
            JOIN order_items oi ON oi.order_id = o.order_id
            WHERE o.seller_id = :userId
              AND o.status = 'COMPLETED'
              AND oi.product_id IN (:productIds)
            ORDER BY r.created_at DESC, r.review_id DESC
            """;
        MapSqlParameterSource reviewParams = new MapSqlParameterSource();
        reviewParams.addValue("userId", userId);
        reviewParams.addValue("productIds", productIds);

        Map<Long, List<SoldProductTransactionReview>> reviewsByProduct = new HashMap<>();
        jdbcTemplate.query(reviewSql, reviewParams, rs -> {
            long pId = rs.getLong("product_id");
            SoldProductTransactionReview review = new SoldProductTransactionReview();
            review.setRating(rs.getInt("rating"));
            review.setComment(rs.getString("comment"));
            Timestamp ts = rs.getTimestamp("created_at");
            if (ts != null) {
                review.setCreatedAt(ts.toLocalDateTime());
            }
            review.setVerifiedTransaction(true);

            reviewsByProduct.computeIfAbsent(pId, k -> new ArrayList<>()).add(review);
        });

        for (SoldProductSummary summary : summaries) {
            List<SoldProductTransactionReview> prodReviews = reviewsByProduct.getOrDefault(summary.getProductId(), Collections.emptyList());
            int revCount = prodReviews.size();
            summary.setReviewCount(revCount);
            if (revCount > 0) {
                double avg = prodReviews.stream().mapToInt(SoldProductTransactionReview::getRating).average().orElse(0.0);
                summary.setRatingAverage(BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue());
                summary.setRecentReviews(prodReviews.stream().limit(2).toList());
            } else {
                summary.setRatingAverage(null);
                summary.setRecentReviews(Collections.emptyList());
            }
        }

        return new SoldProductListResponse(summaries, summaries.size());
    }
}
