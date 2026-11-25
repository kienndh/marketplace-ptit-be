package com.ptiters.marketplace.conversation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Toàn bộ DTO request/response của module conversation, ánh xạ 1-1 {@code app/modules/conversations/schemas.py}.
 * JSON dùng snake_case giống Python (qua {@code @JsonProperty}). Không bao giờ trả JPA Entity ra API.
 */
public final class ConversationDtos {

    private ConversationDtos() {
    }

    // =========================================================================== REQUESTS

    /** Python: ConversationCreateRequest (product_id &gt; 0). */
    public record ConversationCreateRequest(
            @JsonProperty("product_id") @NotNull @Positive Long productId) {
    }

    /** Python: ConversationFromOrderRequest (order_id &gt; 0). */
    public record ConversationFromOrderRequest(
            @JsonProperty("order_id") @NotNull @Positive Long orderId) {
    }

    /** Python: DirectConversationCreateRequest (extra="forbid") — chỉ nhận recipient_public_id (UUID). */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record DirectConversationCreateRequest(
            @JsonProperty("recipient_public_id") @NotNull UUID recipientPublicId) {
    }

    /** Python: GroupCreateRequest. */
    public record GroupCreateRequest(
            @NotNull @Size(min = 3, max = 80) String title,
            @Size(max = 300) String description,
            @JsonProperty("initial_invitee_ids") List<Long> initialInviteeIds) {
    }

    /** Python: GroupUpdateRequest — mọi trường tuỳ chọn (null = không đổi). */
    public record GroupUpdateRequest(
            @Size(min = 3, max = 80) String title,
            @Size(max = 300) String description,
            @JsonProperty("requires_approval") Boolean requiresApproval) {
    }

    /** Python: GroupInviteCreateRequest (min_length=1). */
    public record GroupInviteCreateRequest(
            @JsonProperty("invitee_ids") @NotEmpty List<Long> inviteeIds) {
    }

    /** Python: GroupInviteRespondRequest — action ∈ {ACCEPT, DECLINE}. */
    public record GroupInviteRespondRequest(
            @NotNull @Pattern(regexp = "ACCEPT|DECLINE") String action) {
    }

    /** Python: GroupTransferOwnershipRequest. */
    public record GroupTransferOwnershipRequest(
            @JsonProperty("new_owner_id") @NotNull @Positive Long newOwnerId) {
    }

    /** Python: GroupMemberRoleUpdateRequest — role ∈ {ADMIN, MEMBER}. */
    public record GroupMemberRoleUpdateRequest(
            @NotNull @Pattern(regexp = "ADMIN|MEMBER") String role) {
    }

    /** Python: GroupMemberNicknameUpdateRequest (null hoặc "" = xoá biệt danh). */
    public record GroupMemberNicknameUpdateRequest(
            @Size(max = 40) String nickname) {
    }

    /** Python: PendingApprovalActionRequest — action ∈ {APPROVE, REJECT}. */
    public record PendingApprovalActionRequest(
            @NotNull @Pattern(regexp = "APPROVE|REJECT") String action) {
    }

    /** Python: MessageReactionSetRequest (reaction_key là một trong 12 ReactionKey). */
    public record MessageReactionSetRequest(
            @JsonProperty("reaction_key") @NotNull ReactionKey reactionKey) {
    }

    /** Python: MessageMentionCreate. */
    public record MessageMentionCreate(
            @JsonProperty("mentioned_user_id") @NotNull @Positive Long mentionedUserId,
            @JsonProperty("display_text") @NotNull @Size(min = 1, max = 100) String displayText,
            @JsonProperty("start_offset") @NotNull @Min(0) Integer startOffset,
            @JsonProperty("end_offset") @NotNull @Min(0) Integer endOffset) {
    }

    /**
     * Nhánh application/json của POST /{id}/messages (Python đọc body thủ công:
     * content, product_id, voice_duration_ms, reply_to_message_id, mentions).
     */
    public record MessageCreateRequest(
            String content,
            @JsonProperty("product_id") Long productId,
            @JsonProperty("voice_duration_ms") Long voiceDurationMs,
            @JsonProperty("reply_to_message_id") Long replyToMessageId,
            List<MessageMentionCreate> mentions) {
    }

    // =========================================================================== RESPONSES

    /** Python: ParticipantSummary — chỉ lộ thông tin công khai an toàn. */
    public record ParticipantSummary(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("public_id") UUID publicId,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("avatar_url") String avatarUrl) {
    }

    /** Python: ProductContextSummary. */
    public record ProductContextSummary(
            @JsonProperty("product_id") Long productId,
            String title,
            BigDecimal price,
            String status,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("listing_type") String listingType) {
    }

    /** Python: MessageAttachmentResponse (không lộ storage_key). */
    public record MessageAttachmentResponse(
            @JsonProperty("attachment_id") Long attachmentId,
            String kind,
            String url,
            @JsonProperty("mime_type") String mimeType,
            @JsonProperty("original_name") String originalName,
            @JsonProperty("size_bytes") Long sizeBytes,
            @JsonProperty("duration_ms") Long durationMs) {
    }

    /** Python: MessageMentionResponse. */
    public record MessageMentionResponse(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("display_text") String displayText,
            @JsonProperty("start_offset") Integer startOffset,
            @JsonProperty("end_offset") Integer endOffset) {
    }

    /** Python: ReactionAggregateResponse. */
    public record ReactionAggregateResponse(
            @JsonProperty("reaction_key") ReactionKey reactionKey,
            long count) {
    }

    /** Python: MessageReactionSummaryResponse. */
    public record MessageReactionSummaryResponse(
            @JsonProperty("message_id") Long messageId,
            List<ReactionAggregateResponse> reactions,
            @JsonProperty("my_reaction") ReactionKey myReaction) {
    }

    /** Python: ReplyMessageSummary — tóm tắt một cấp, không đệ quy. */
    public record ReplyMessageSummary(
            @JsonProperty("message_id") Long messageId,
            @JsonProperty("sender_id") Long senderId,
            @JsonProperty("sender_name") String senderName,
            String type,
            String preview) {
    }

    /** Python: MessageResponse. {@code sent_at} luôn là UTC có hậu tố "Z". */
    public record MessageResponse(
            @JsonProperty("message_id") Long messageId,
            @JsonProperty("conversation_id") Long conversationId,
            @JsonProperty("sender_id") Long senderId,
            @JsonProperty("sender_public_id") UUID senderPublicId,
            @JsonProperty("sender_name") String senderName,
            @JsonProperty("sender_avatar") String senderAvatar,
            String type,
            String content,
            @JsonProperty("product_id") Long productId,
            ProductContextSummary product,
            @JsonProperty("sent_at") String sentAt,
            @JsonProperty("is_mine") boolean isMine,
            @JsonProperty("is_deleted") boolean isDeleted,
            List<MessageAttachmentResponse> attachments,
            List<MessageMentionResponse> mentions,
            List<ReactionAggregateResponse> reactions,
            @JsonProperty("my_reaction") ReactionKey myReaction,
            @JsonProperty("reply_to") ReplyMessageSummary replyTo) {
    }

    /** Python: MessageListResponse (keyset cursor sent_at DESC, message_id DESC). */
    public record MessageListResponse(
            List<MessageResponse> items,
            @JsonProperty("next_cursor") String nextCursor,
            @JsonProperty("has_more") boolean hasMore) {
    }

    /** Python: ConversationSummaryResponse (một dòng của inbox). */
    public record ConversationSummaryResponse(
            @JsonProperty("conversation_id") Long conversationId,
            String type,
            String title,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("other_participant") ParticipantSummary otherParticipant,
            @JsonProperty("member_count") Long memberCount,
            @JsonProperty("my_role") String myRole,
            @JsonProperty("is_joined") Boolean isJoined,
            @JsonProperty("product_context") ProductContextSummary productContext,
            @JsonProperty("last_message_preview") String lastMessagePreview,
            @JsonProperty("last_message_at") String lastMessageAt,
            @JsonProperty("unread_count") long unreadCount,
            @JsonProperty("created_at") LocalDateTime createdAt) {
    }

    /** Python: ConversationListResponse. */
    public record ConversationListResponse(
            List<ConversationSummaryResponse> items,
            @JsonProperty("next_cursor") String nextCursor,
            @JsonProperty("has_more") boolean hasMore,
            @JsonProperty("owned_group_count") long ownedGroupCount) {
    }

    /** Python: ConversationDetailResponse. */
    public record ConversationDetailResponse(
            @JsonProperty("conversation_id") Long conversationId,
            String type,
            String title,
            String description,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("other_participant") ParticipantSummary otherParticipant,
            @JsonProperty("member_count") Long memberCount,
            @JsonProperty("my_role") String myRole,
            @JsonProperty("is_joined") Boolean isJoined,
            @JsonProperty("product_context") ProductContextSummary productContext,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("requires_approval") boolean requiresApproval) {
    }

    /** Python: OwnedGroupSummaryResponse. */
    public record OwnedGroupSummaryResponse(
            @JsonProperty("owned_group_count") long ownedGroupCount) {
    }

    /** Python: ConversationMemberResponse. is_online=null khi presence không khả dụng. */
    public record ConversationMemberResponse(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("public_id") UUID publicId,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("avatar_url") String avatarUrl,
            String role,
            @JsonProperty("joined_at") LocalDateTime joinedAt,
            @JsonProperty("is_online") Boolean isOnline,
            String nickname) {
    }

    /** Python: ConversationInviteResponse. */
    public record ConversationInviteResponse(
            @JsonProperty("invite_id") Long inviteId,
            @JsonProperty("conversation_id") Long conversationId,
            @JsonProperty("conversation_title") String conversationTitle,
            @JsonProperty("inviter_id") Long inviterId,
            @JsonProperty("inviter_name") String inviterName,
            @JsonProperty("inviter_avatar") String inviterAvatar,
            String status,
            @JsonProperty("created_at") LocalDateTime createdAt) {
    }

    /** Python: PendingApprovalItem. */
    public record PendingApprovalItem(
            @JsonProperty("invite_id") Long inviteId,
            @JsonProperty("conversation_id") Long conversationId,
            @JsonProperty("invitee_id") Long inviteeId,
            @JsonProperty("invitee_name") String inviteeName,
            @JsonProperty("invitee_avatar") String inviteeAvatar,
            @JsonProperty("inviter_id") Long inviterId,
            @JsonProperty("inviter_name") String inviterName,
            @JsonProperty("inviter_avatar") String inviterAvatar,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("responded_at") LocalDateTime respondedAt) {
    }

    /** Python: PendingApprovalListResponse. */
    public record PendingApprovalListResponse(List<PendingApprovalItem> items) {
    }

    /** Python: InviteCandidateItem. */
    public record InviteCandidateItem(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("last_interaction_at") LocalDateTime lastInteractionAt) {
    }

    /** Python: InviteCandidateListResponse. */
    public record InviteCandidateListResponse(List<InviteCandidateItem> items) {
    }

    /** Python: SharedAttachmentItemResponse. */
    public record SharedAttachmentItemResponse(
            @JsonProperty("attachment_id") Long attachmentId,
            @JsonProperty("message_id") Long messageId,
            String kind,
            String url,
            @JsonProperty("mime_type") String mimeType,
            @JsonProperty("original_name") String originalName,
            @JsonProperty("size_bytes") Long sizeBytes,
            @JsonProperty("duration_ms") Long durationMs,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("sender_id") Long senderId,
            @JsonProperty("sender_name") String senderName,
            @JsonProperty("sender_avatar") String senderAvatar) {
    }

    /** Python: SharedAttachmentListResponse. */
    public record SharedAttachmentListResponse(
            List<SharedAttachmentItemResponse> items,
            @JsonProperty("next_cursor") String nextCursor,
            @JsonProperty("has_more") boolean hasMore) {
    }

    /** Python: SharedAttachmentSummaryResponse (media / audio / file). */
    public record SharedAttachmentSummaryResponse(
            @JsonProperty("media_count") long mediaCount,
            @JsonProperty("audio_count") long audioCount,
            @JsonProperty("file_count") long fileCount) {
    }

    /** Python: PresenceBatchResponse — presence_available=false khi presence không sẵn sàng (không lỗi). */
    public record PresenceBatchResponse(
            @JsonProperty("presence_available") boolean presenceAvailable,
            @JsonProperty("online_user_ids") List<Long> onlineUserIds) {
    }

    /** Python: AttachmentDownloadTicketResponse — ticket mờ, sống 60 giây, dùng 1 lần. */
    public record AttachmentDownloadTicketResponse(
            @JsonProperty("ticket") String ticket,
            @JsonProperty("download_url") String downloadUrl,
            @JsonProperty("expires_in_seconds") int expiresInSeconds) {
    }

    /** Python: OfficialSupportGroupResponse. */
    public record OfficialSupportGroupResponse(
            @JsonProperty("conversation_id") Long conversationId,
            String title,
            String description,
            @JsonProperty("is_member") boolean isMember,
            @JsonProperty("last_message_preview") String lastMessagePreview,
            @JsonProperty("last_message_at") String lastMessageAt,
            @JsonProperty("unread_count") long unreadCount) {
    }

    /** Python: OfficialSupportGroupJoinResponse. */
    public record OfficialSupportGroupJoinResponse(
            @JsonProperty("conversation_id") Long conversationId) {
    }
}
