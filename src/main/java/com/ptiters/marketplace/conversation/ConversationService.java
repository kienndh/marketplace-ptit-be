package com.ptiters.marketplace.conversation;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ConversationService {

    public ConversationDetailResponse createFromProduct(Object currentUser, ConversationCreateRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public ConversationDetailResponse createDirect(Object currentUser, DirectConversationCreateRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public ConversationDetailResponse createFromOrder(Object currentUser, ConversationFromOrderRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public ConversationListResponse listMyConversations(Object currentUser) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public ConversationDetailResponse getConversationDetail(Long conversationId, Object currentUser) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public MessageListResponse listMessages(Long conversationId, String cursor, int limit, Object currentUser) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public MessageResponse sendMessage(Long conversationId, Object currentUser, MessageCreateRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void reactToMessage(Long conversationId, Long messageId, Object currentUser, MessageReactionSetRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public ConversationDetailResponse createGroup(Object currentUser, GroupCreateRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public ConversationDetailResponse updateGroup(Long conversationId, Object currentUser, GroupUpdateRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void inviteToGroup(Long conversationId, Object currentUser, GroupInviteCreateRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public void respondToInvite(Long inviteId, Object currentUser, GroupInviteRespondRequest payload) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }

    public List<SharedAttachmentItemResponse> listAttachments(Long conversationId, Object currentUser) {
        throw new UnsupportedOperationException("TODO: Implement business logic");
    }
}
