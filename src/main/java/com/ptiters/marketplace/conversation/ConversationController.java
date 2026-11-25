package com.ptiters.marketplace.conversation;

import com.ptiters.marketplace.conversation.ConversationDtos.AttachmentDownloadTicketResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.ConversationCreateRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.ConversationDetailResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.ConversationFromOrderRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.ConversationInviteResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.ConversationListResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.ConversationMemberResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.GroupCreateRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.GroupInviteCreateRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.GroupInviteRespondRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.GroupMemberNicknameUpdateRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.GroupMemberRoleUpdateRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.GroupTransferOwnershipRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.GroupUpdateRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.InviteCandidateListResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.MessageCreateRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.MessageListResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.MessageMentionCreate;
import com.ptiters.marketplace.conversation.ConversationDtos.MessageReactionSetRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.MessageReactionSummaryResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.MessageResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.OfficialSupportGroupJoinResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.OfficialSupportGroupResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.OwnedGroupSummaryResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.PendingApprovalActionRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.PendingApprovalListResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.PresenceBatchResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.SharedAttachmentListResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.SharedAttachmentSummaryResponse;
import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

/**
 * API chat — Python: {@code conversations/router.py} (prefix {@code /api/v1/conversations}).
 * Controller chỉ nhận request, validate tham số, gọi Service và trả DTO (không trả Entity).
 * Tải tệp đính kèm: {@code download-ticket} (Redis, 60s, dùng 1 lần) và {@code download} (stream, an toàn SSRF).
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Validated
public class ConversationController {

    private final ConversationService conversationService;
    private final GroupConversationService groupService;
    private final CurrentUserResolver currentUserResolver;
    private final AttachmentDownloadService downloadService;

    // ------------------------------------------------------------------------------------ open / create

    @PostMapping
    public ResponseEntity<ConversationDetailResponse> openFromProduct(@Valid @RequestBody ConversationCreateRequest payload) {
        return ResponseEntity.ok(conversationService.createOrOpenFromProduct(user(), payload.productId()));
    }

    @PostMapping("/direct")
    public ResponseEntity<ConversationDetailResponse> openDirect(@RequestBody Map<String, Object> payload) {
        // Python: extra="forbid" — chỉ nhận đúng recipient_public_id (UUID); recipient_id số nguyên bị 422.
        if (payload == null || payload.size() != 1 || !(payload.get("recipient_public_id") instanceof String raw)) {
            throw ConversationSupport.unprocessable("Dữ liệu không hợp lệ");
        }
        java.util.UUID recipient;
        try {
            recipient = java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ConversationSupport.unprocessable("Dữ liệu không hợp lệ");
        }
        return ResponseEntity.ok(conversationService.createOrOpenDirect(user(), recipient));
    }

    @PostMapping("/from-order")
    public ResponseEntity<ConversationDetailResponse> openFromOrder(@Valid @RequestBody ConversationFromOrderRequest payload) {
        return ResponseEntity.ok(conversationService.createOrOpenFromOrder(user(), payload.orderId()));
    }

    @PostMapping("/groups")
    public ResponseEntity<ConversationDetailResponse> createGroup(@Valid @RequestBody GroupCreateRequest payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(user(), payload));
    }

    // ------------------------------------------------------------------------------------ invites

    @GetMapping("/invites")
    public ResponseEntity<List<ConversationInviteResponse>> listInvites() {
        return ResponseEntity.ok(groupService.listMyInvites(user()));
    }

    @PostMapping("/invites/{inviteId}/respond")
    public ResponseEntity<Map<String, Object>> respondToInvite(@PathVariable Long inviteId,
                                                               @Valid @RequestBody GroupInviteRespondRequest payload) {
        return ResponseEntity.ok(groupService.respondToInvite(user(), inviteId, payload.action()));
    }

    // ------------------------------------------------------------------------------------ official support

    @GetMapping("/official-support")
    public ResponseEntity<OfficialSupportGroupResponse> officialSupport() {
        return ResponseEntity.ok(conversationService.officialSupport(activeUser()));
    }

    @PostMapping("/official-support/join")
    public ResponseEntity<OfficialSupportGroupJoinResponse> joinOfficialSupport() {
        return ResponseEntity.ok(conversationService.joinOfficialSupport(activeUser()));
    }

    // ------------------------------------------------------------------------------------ inbox / detail

    @GetMapping
    public ResponseEntity<ConversationListResponse> list(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(value = "cursor", required = false) String cursor) {
        return ResponseEntity.ok(conversationService.listConversations(user(), type, limit, cursor));
    }

    @GetMapping("/owned-group-count")
    public ResponseEntity<OwnedGroupSummaryResponse> ownedGroupCount() {
        return ResponseEntity.ok(conversationService.ownedGroupCount(user()));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDetailResponse> get(@PathVariable Long conversationId) {
        return ResponseEntity.ok(conversationService.getConversation(user(), conversationId));
    }

    // ------------------------------------------------------------------------------------ group management

    @PatchMapping("/groups/{conversationId}")
    public ResponseEntity<ConversationDetailResponse> updateGroup(@PathVariable Long conversationId,
                                                                  @Valid @RequestBody GroupUpdateRequest payload) {
        return ResponseEntity.ok(groupService.updateMetadata(user(), conversationId, payload));
    }

    @PostMapping("/groups/{conversationId}/invites")
    public ResponseEntity<List<ConversationInviteResponse>> inviteMembers(
            @PathVariable Long conversationId, @Valid @RequestBody GroupInviteCreateRequest payload) {
        return ResponseEntity.ok(groupService.inviteMembers(user(), conversationId, payload.inviteeIds()));
    }

    @GetMapping("/groups/{conversationId}/pending-approvals")
    public ResponseEntity<PendingApprovalListResponse> pendingApprovals(@PathVariable Long conversationId) {
        return ResponseEntity.ok(groupService.listPendingApprovals(user(), conversationId));
    }

    @PostMapping("/groups/{conversationId}/pending-approvals/{inviteId}")
    public ResponseEntity<Map<String, Object>> processPendingApproval(
            @PathVariable Long conversationId, @PathVariable Long inviteId,
            @Valid @RequestBody PendingApprovalActionRequest payload) {
        return ResponseEntity.ok(groupService.processPendingApproval(user(), conversationId, inviteId, payload.action()));
    }

    @GetMapping({"/{conversationId}/invite-candidates", "/groups/{conversationId}/invite-candidates"})
    public ResponseEntity<InviteCandidateListResponse> inviteCandidates(
            @PathVariable Long conversationId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(groupService.inviteCandidates(user(), conversationId, q, limit));
    }

    @GetMapping("/groups/{conversationId}/members")
    public ResponseEntity<List<ConversationMemberResponse>> listMembers(@PathVariable Long conversationId) {
        return ResponseEntity.ok(groupService.listMembers(user(), conversationId));
    }

    @PostMapping("/groups/{conversationId}/members/{userId}/role")
    public ResponseEntity<Map<String, Object>> updateMemberRole(
            @PathVariable Long conversationId, @PathVariable Long userId,
            @Valid @RequestBody GroupMemberRoleUpdateRequest payload) {
        return ResponseEntity.ok(groupService.updateMemberRole(user(), conversationId, userId, payload.role()));
    }

    @PatchMapping("/groups/{conversationId}/me/nickname")
    public ResponseEntity<ConversationMemberResponse> updateNickname(
            @PathVariable Long conversationId, @Valid @RequestBody GroupMemberNicknameUpdateRequest payload) {
        return ResponseEntity.ok(groupService.updateOwnNickname(user(), conversationId, payload));
    }

    @PostMapping("/groups/{conversationId}/transfer-ownership")
    public ResponseEntity<Map<String, Object>> transferOwnership(
            @PathVariable Long conversationId, @Valid @RequestBody GroupTransferOwnershipRequest payload) {
        return ResponseEntity.ok(groupService.transferOwnership(user(), conversationId, payload.newOwnerId()));
    }

    @DeleteMapping("/groups/{conversationId}/members/{userId}")
    public ResponseEntity<Map<String, Object>> removeMember(@PathVariable Long conversationId, @PathVariable Long userId) {
        return ResponseEntity.ok(groupService.removeMember(user(), conversationId, userId));
    }

    @PostMapping("/groups/{conversationId}/leave")
    public ResponseEntity<Map<String, Object>> leaveGroup(@PathVariable Long conversationId) {
        return ResponseEntity.ok(groupService.leaveGroup(user(), conversationId));
    }

    @DeleteMapping("/groups/{conversationId}")
    public ResponseEntity<Map<String, Object>> deleteGroup(@PathVariable Long conversationId) {
        return ResponseEntity.ok(groupService.deleteGroup(user(), conversationId));
    }

    // ------------------------------------------------------------------------------------ shared attachments / presence

    @GetMapping("/{conversationId}/attachments")
    public ResponseEntity<SharedAttachmentListResponse> sharedAttachments(
            @PathVariable Long conversationId,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "limit", defaultValue = "30") @Min(1) @Max(100) int limit,
            @RequestParam(value = "cursor", required = false) String cursor) {
        String selected = category != null ? category : (kind != null ? kind : "media");
        return ResponseEntity.ok(conversationService.sharedAttachments(user(), conversationId, selected, limit, cursor));
    }

    @GetMapping("/{conversationId}/attachments/summary")
    public ResponseEntity<SharedAttachmentSummaryResponse> sharedAttachmentsSummary(@PathVariable Long conversationId) {
        return ResponseEntity.ok(conversationService.sharedAttachmentsSummary(user(), conversationId));
    }

    @PostMapping("/{conversationId}/attachments/{attachmentId}/download-ticket")
    public ResponseEntity<AttachmentDownloadTicketResponse> createDownloadTicket(
            @PathVariable Long conversationId, @PathVariable Long attachmentId) {
        return ResponseEntity.ok(downloadService.createTicket(user(), conversationId, attachmentId));
    }

    /** Xác thực bằng ticket một lần HOẶC Bearer header (Python: get_current_user_optional); stream qua storage. */
    @GetMapping("/{conversationId}/attachments/{attachmentId}/download")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> downloadAttachment(
            @PathVariable Long conversationId, @PathVariable Long attachmentId,
            @RequestParam(value = "ticket", required = false) String ticket) {
        Long userId;
        if (ticket != null && !ticket.isEmpty()) {
            userId = downloadService.consumeTicket(conversationId, attachmentId, ticket);
        } else {
            userId = currentUserResolver.getOptionalCurrentActiveUser().map(com.ptiters.marketplace.user.User::getUserId)
                    .orElseThrow(() -> com.ptiters.marketplace.exception.ApiException.unauthorized(
                            "Yêu cầu xác thực qua ticket hoặc Authorization Bearer header."));
        }
        var target = downloadService.authorize(userId, conversationId, attachmentId);
        var opened = downloadService.open(target);
        var response = ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        AttachmentDownloadService.formatContentDisposition(target.originalName(), target.mimeType()))
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE,
                        target.mimeType() != null && !target.mimeType().isEmpty() ? target.mimeType() : "application/octet-stream")
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, no-store");
        if (opened.contentLength() != null) {
            response.header(org.springframework.http.HttpHeaders.CONTENT_LENGTH, opened.contentLength());
        }
        return response.body(out -> {
            try (java.io.InputStream in = opened.body()) {
                in.transferTo(out);
            }
        });
    }

    @GetMapping("/{conversationId}/presence")
    public ResponseEntity<PresenceBatchResponse> presence(@PathVariable Long conversationId) {
        return ResponseEntity.ok(conversationService.presence(user(), conversationId));
    }

    // ------------------------------------------------------------------------------------ messages

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<MessageListResponse> messages(
            @PathVariable Long conversationId,
            @RequestParam(value = "limit", defaultValue = "30") @Min(1) @Max(100) int limit,
            @RequestParam(value = "cursor", required = false) String cursor) {
        return ResponseEntity.ok(conversationService.getMessages(user(), conversationId, limit, cursor));
    }

    /** Nhánh multipart/form-data (có thể kèm tối đa 4 tệp). */
    @PostMapping(value = "/{conversationId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> sendMessageMultipart(
            @PathVariable Long conversationId,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "product_id", required = false) Long productId,
            @RequestParam(value = "voice_duration_ms", required = false) Long voiceDurationMs,
            @RequestParam(value = "mentions", required = false) String mentions,
            @RequestParam(value = "reply_to_message_id", required = false) Long replyToMessageId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        List<MessageMentionCreate> parsed = List.of();
        if (mentions != null && !mentions.isBlank()) {
            try {
                parsed = ConversationSupport.json().readValue(mentions, new TypeReference<List<MessageMentionCreate>>() {
                });
            } catch (RuntimeException e) {
                parsed = List.of(); // Python: JSON lỗi → bỏ qua mentions
            }
        }
        var input = new ConversationService.SendMessageInput(content, productId, voiceDurationMs, parsed,
                replyToMessageId, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.sendMessage(user(), conversationId, input));
    }

    /** Nhánh application/json (không tệp) — Python giữ để tương thích client cũ. */
    @PostMapping(value = "/{conversationId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> sendMessageJson(@PathVariable Long conversationId,
                                                           @RequestBody MessageCreateRequest body) {
        var input = new ConversationService.SendMessageInput(body.content(), body.productId(), body.voiceDurationMs(),
                body.mentions(), body.replyToMessageId(), List.of());
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.sendMessage(user(), conversationId, input));
    }

    @PostMapping("/{conversationId}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long conversationId) {
        return ResponseEntity.ok(conversationService.markAsRead(user(), conversationId));
    }

    @PutMapping("/{conversationId}/messages/{messageId}/reaction")
    public ResponseEntity<MessageReactionSummaryResponse> setReaction(
            @PathVariable Long conversationId, @PathVariable Long messageId,
            @Valid @RequestBody MessageReactionSetRequest payload) {
        return ResponseEntity.ok(conversationService.setReaction(user(), conversationId, messageId, payload.reactionKey()));
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}/reaction")
    public ResponseEntity<MessageReactionSummaryResponse> removeReaction(@PathVariable Long conversationId,
                                                                         @PathVariable Long messageId) {
        return ResponseEntity.ok(conversationService.removeReaction(user(), conversationId, messageId));
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}")
    public ResponseEntity<MessageResponse> recallMessage(@PathVariable Long conversationId, @PathVariable Long messageId) {
        return ResponseEntity.ok(conversationService.recallMessage(activeUser(), conversationId, messageId));
    }

    // ------------------------------------------------------------------------------------ helpers

    /** Python {@code get_current_user}: xác thực Firebase, không chặn BANNED/INACTIVE. */
    private User user() {
        return currentUserResolver.getCurrentUser();
    }

    /** Python {@code get_current_active_user}: từ chối BANNED/INACTIVE bằng 403. */
    private User activeUser() {
        return currentUserResolver.getCurrentActiveUser();
    }
}
