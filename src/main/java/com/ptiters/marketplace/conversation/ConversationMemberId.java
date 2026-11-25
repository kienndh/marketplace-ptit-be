package com.ptiters.marketplace.conversation;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Khóa chính kép (conversation_id, user_id) của conversation_members. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ConversationMemberId implements Serializable {
    private Long conversationId;
    private Long userId;
}
