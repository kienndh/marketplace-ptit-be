package com.ptiters.marketplace.user;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTO admin users, khớp {@code users/admin_schemas.py}. */
public final class AdminUserDtos {

    private AdminUserDtos() {}

    public record SummaryStats(
            @JsonProperty("total_users") long totalUsers,
            @JsonProperty("active_users") long activeUsers,
            @JsonProperty("restricted_users") long restrictedUsers,
            @JsonProperty("banned_users") long bannedUsers) {}

    public record ListItem(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("public_id") UUID publicId,
            String email,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("avatar_url") String avatarUrl,
            String role,
            String status,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("last_active_at") LocalDateTime lastActiveAt,
            @JsonProperty("can_buy") boolean canBuy,
            @JsonProperty("can_sell") boolean canSell,
            @JsonProperty("active_restrictions") List<String> activeRestrictions,
            @JsonProperty("report_count") long reportCount) {}

    public record ListResponse(
            List<ListItem> items,
            long total,
            int page,
            @JsonProperty("page_size") int pageSize,
            @JsonProperty("total_pages") int totalPages,
            SummaryStats stats) {}

    public record RestrictionDetail(
            @JsonProperty("restriction_id") Long restrictionId,
            String capability,
            @JsonProperty("starts_at") LocalDateTime startsAt,
            @JsonProperty("expires_at") LocalDateTime expiresAt,
            @JsonProperty("revoked_at") LocalDateTime revokedAt,
            @JsonProperty("is_active") boolean isActive,
            @JsonProperty("source_report_id") Long sourceReportId,
            @JsonProperty("imposed_by") Long imposedBy,
            @JsonProperty("imposed_by_name") String imposedByName,
            @JsonProperty("revoked_by") Long revokedBy,
            @JsonProperty("revoked_by_name") String revokedByName,
            @JsonProperty("created_at") LocalDateTime createdAt) {}

    public record EligibleReport(
            @JsonProperty("report_id") Long reportId,
            @JsonProperty("target_type") String targetType,
            @JsonProperty("target_id") Long targetId,
            String reason,
            String description,
            String status,
            @JsonProperty("created_at") LocalDateTime createdAt) {}

    public record ModerationHistoryItem(
            @JsonProperty("moderation_action_id") Long moderationActionId,
            @JsonProperty("action_type") String actionType,
            @JsonProperty("previous_status") String previousStatus,
            @JsonProperty("new_status") String newStatus,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("admin_id") Long adminId,
            @JsonProperty("admin_name") String adminName,
            @JsonProperty("admin_email") String adminEmail,
            @JsonProperty("report_id") Long reportId) {}

    public record DetailResponse(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("public_id") UUID publicId,
            String email,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("avatar_url") String avatarUrl,
            String phone,
            String bio,
            @JsonProperty("delivery_area") String deliveryArea,
            String role,
            String status,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("last_active_at") LocalDateTime lastActiveAt,
            @JsonProperty("can_buy") boolean canBuy,
            @JsonProperty("can_sell") boolean canSell,
            @JsonProperty("active_listing_count") long activeListingCount,
            @JsonProperty("total_received_reports") long totalReceivedReports,
            @JsonProperty("supported_received_reports") long supportedReceivedReports,
            @JsonProperty("total_sent_reports") long totalSentReports,
            @JsonProperty("active_restrictions") List<RestrictionDetail> activeRestrictions,
            @JsonProperty("all_restrictions") List<RestrictionDetail> allRestrictions,
            @JsonProperty("eligible_reports") List<EligibleReport> eligibleReports) {}
}
