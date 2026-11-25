package com.ptiters.marketplace.conversation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ConversationDetailResponse> createFromProduct(@RequestBody ConversationCreateRequest payload) {
        return ResponseEntity.ok(conversationService.createFromProduct(null, payload));
    }

    @PostMapping("/direct")
    public ResponseEntity<ConversationDetailResponse> createDirect(@RequestBody DirectConversationCreateRequest payload) {
        return ResponseEntity.ok(conversationService.createDirect(null, payload));
    }

    @PostMapping("/from-order")
    public ResponseEntity<ConversationDetailResponse> createFromOrder(@RequestBody ConversationFromOrderRequest payload) {
        return ResponseEntity.ok(conversationService.createFromOrder(null, payload));
    }

    @GetMapping
    public ResponseEntity<ConversationListResponse> listMyConversations() {
        return ResponseEntity.ok(conversationService.listMyConversations(null));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDetailResponse> getConversationDetail(@PathVariable("conversationId") Long conversationId) {
        return ResponseEntity.ok(conversationService.getConversationDetail(conversationId, null));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<MessageListResponse> listMessages(
            @PathVariable("conversationId") Long conversationId,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(conversationService.listMessages(conversationId, cursor, limit, null));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable("conversationId") Long conversationId,
            @RequestBody MessageCreateRequest payload) {
        return ResponseEntity.ok(conversationService.sendMessage(conversationId, null, payload));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/reactions")
    public ResponseEntity<Void> reactToMessage(
            @PathVariable("conversationId") Long conversationId,
            @PathVariable("messageId") Long messageId,
            @RequestBody MessageReactionSetRequest payload) {
        conversationService.reactToMessage(conversationId, messageId, null, payload);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups")
    public ResponseEntity<ConversationDetailResponse> createGroup(@RequestBody GroupCreateRequest payload) {
        return ResponseEntity.ok(conversationService.createGroup(null, payload));
    }

    @PatchMapping("/groups/{conversationId}")
    public ResponseEntity<ConversationDetailResponse> updateGroup(
            @PathVariable("conversationId") Long conversationId,
            @RequestBody GroupUpdateRequest payload) {
        return ResponseEntity.ok(conversationService.updateGroup(conversationId, null, payload));
    }

    @PostMapping("/groups/{conversationId}/invites")
    public ResponseEntity<Void> inviteToGroup(
            @PathVariable("conversationId") Long conversationId,
            @RequestBody GroupInviteCreateRequest payload) {
        conversationService.inviteToGroup(conversationId, null, payload);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups/invites/{inviteId}/respond")
    public ResponseEntity<Void> respondToInvite(
            @PathVariable("inviteId") Long inviteId,
            @RequestBody GroupInviteRespondRequest payload) {
        conversationService.respondToInvite(inviteId, null, payload);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{conversationId}/attachments")
    public ResponseEntity<List<SharedAttachmentItemResponse>> listAttachments(@PathVariable("conversationId") Long conversationId) {
        return ResponseEntity.ok(conversationService.listAttachments(conversationId, null));
    }
}
