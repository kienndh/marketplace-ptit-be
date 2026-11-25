package com.ptiters.marketplace.conversation;

import com.ptiters.marketplace.notification.NotificationType;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Truy vấn dữ liệu chat (tương ứng {@code conversations/repository.py}). Repository cơ bản của Wave 0 giữ nguyên;
 * mọi query nghiệp vụ nằm ở đây (HQL tham số hoá — không inline enum native — và native SQL cho upsert race-safe).
 * Lớp này KHÔNG mở transaction: caller ({@code @Transactional} service) sở hữu transaction.
 */
@Repository
public class ConversationQueryRepository {

    /** Conversation đang hoạt động + membership đang hoạt động của một user. */
    public record Access(Conversation conversation, ConversationMember member) {
    }

    @PersistenceContext
    private EntityManager em;

    // =============================================================================== conversation / member

    /** Python: get_conversation_by_id (loại hội thoại đã xoá mềm). */
    public Conversation findActiveConversation(Long conversationId) {
        Conversation c = em.find(Conversation.class, conversationId);
        return c != null && c.getDeletedAt() == null ? c : null;
    }

    /** Python: get_conversation_with_member — null nếu không phải thành viên hoạt động. */
    public Access findAccess(Long conversationId, Long userId) {
        Conversation c = findActiveConversation(conversationId);
        if (c == null) {
            return null;
        }
        ConversationMember m = findActiveMember(conversationId, userId);
        return m == null ? null : new Access(c, m);
    }

    public ConversationMember findActiveMember(Long conversationId, Long userId) {
        ConversationMember m = em.find(ConversationMember.class, new ConversationMemberId(conversationId, userId));
        return m != null && m.getLeftAt() == null ? m : null;
    }

    /** Python: get_member_for_update — SELECT ... FOR UPDATE (không lọc left_at). */
    public ConversationMember lockMember(Long conversationId, Long userId) {
        return em.find(ConversationMember.class, new ConversationMemberId(conversationId, userId),
                LockModeType.PESSIMISTIC_WRITE);
    }

    /** Python: get_group_for_update — khoá hàng conversation (null nếu đã xoá mềm). */
    public Conversation lockActiveConversation(Long conversationId) {
        Conversation c = em.find(Conversation.class, conversationId, LockModeType.PESSIMISTIC_WRITE);
        return c != null && c.getDeletedAt() == null ? c : null;
    }

    /** Khoá hàng conversation kể cả đã xoá mềm (dùng cho xoá nhóm). */
    public Conversation lockConversation(Long conversationId) {
        return em.find(Conversation.class, conversationId, LockModeType.PESSIMISTIC_WRITE);
    }

    public List<Long> activeMemberIds(Long conversationId) {
        return em.createQuery("select m.userId from ConversationMember m "
                        + "where m.conversationId = :cid and m.leftAt is null order by m.userId", Long.class)
                .setParameter("cid", conversationId).getResultList();
    }

    public long countActiveMembers(Long conversationId) {
        return em.createQuery("select count(m) from ConversationMember m "
                        + "where m.conversationId = :cid and m.leftAt is null", Long.class)
                .setParameter("cid", conversationId).getSingleResult();
    }

    public ConversationMember findActiveOwner(Long conversationId) {
        List<ConversationMember> l = em.createQuery("select m from ConversationMember m "
                        + "where m.conversationId = :cid and m.role = :role and m.leftAt is null", ConversationMember.class)
                .setParameter("cid", conversationId).setParameter("role", ConversationMemberRole.OWNER)
                .setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    public long ownedGroupCount(Long userId) {
        return em.createQuery("select count(m) from ConversationMember m, Conversation c "
                        + "where c.conversationId = m.conversationId and m.userId = :uid and m.role = :role "
                        + "and m.leftAt is null and c.type = :type and c.deletedAt is null", Long.class)
                .setParameter("uid", userId).setParameter("role", ConversationMemberRole.OWNER)
                .setParameter("type", ConversationType.GROUP).getSingleResult();
    }

    // =============================================================================== direct (race-safe)

    /**
     * Python: get_or_create_direct_conversation — {@code INSERT ... ON CONFLICT (direct_pair_key) DO NOTHING}.
     * Nhiều request đồng thời cùng cặp user luôn hội tụ về một conversation_id (ràng buộc UNIQUE của PostgreSQL).
     */
    public Conversation getOrCreateDirect(Long userA, Long userB) {
        long u1 = Math.min(userA, userB);
        long u2 = Math.max(userA, userB);
        String pairKey = u1 + ":" + u2;
        LocalDateTime now = ConversationSupport.nowUtc();

        List<?> inserted = em.createNativeQuery("INSERT INTO conversations "
                        + "(type, direct_pair_key, user_one_id, user_two_id, created_at, requires_approval) "
                        + "VALUES ('DIRECT', :k, :u1, :u2, :now, false) "
                        + "ON CONFLICT (direct_pair_key) DO NOTHING RETURNING conversation_id")
                .setParameter("k", pairKey).setParameter("u1", u1).setParameter("u2", u2)
                .setParameter("now", now).getResultList();
        if (!inserted.isEmpty()) {
            long newId = ((Number) inserted.get(0)).longValue();
            insertMemberIfAbsent(newId, u1, now);
            insertMemberIfAbsent(newId, u2, now);
            return em.find(Conversation.class, newId);
        }
        Conversation existing = em.createQuery("select c from Conversation c where c.directPairKey = :k",
                Conversation.class).setParameter("k", pairKey).getSingleResult();
        insertMemberIfAbsent(existing.getConversationId(), u1, now);
        insertMemberIfAbsent(existing.getConversationId(), u2, now);
        return existing;
    }

    private void insertMemberIfAbsent(long conversationId, long userId, LocalDateTime now) {
        em.createNativeQuery("INSERT INTO conversation_members (conversation_id, user_id, role, joined_at) "
                        + "VALUES (:c, :u, 'MEMBER', :now) ON CONFLICT (conversation_id, user_id) DO NOTHING")
                .setParameter("c", conversationId).setParameter("u", userId).setParameter("now", now)
                .executeUpdate();
    }

    /**
     * Python: upsert_active_member + join_support_group — tạo hoặc kích hoạt lại thành viên, atomically.
     * {@code last_read_message_id} chỉ ghi đè khi {@code latestMsgId != null}.
     */
    public void upsertActiveMember(Long conversationId, Long userId, ConversationMemberRole role,
                                   Long latestMsgId, LocalDateTime now) {
        em.createNativeQuery("INSERT INTO conversation_members "
                        + "(conversation_id, user_id, role, joined_at, last_read_message_id) "
                        + "VALUES (:c, :u, :role, :now, CAST(:lr AS bigint)) "
                        + "ON CONFLICT (conversation_id, user_id) DO UPDATE SET left_at = NULL, "
                        + "joined_at = :now, role = :role, "
                        + "last_read_message_id = COALESCE(CAST(:lr AS bigint), conversation_members.last_read_message_id)")
                .setParameter("c", conversationId).setParameter("u", userId).setParameter("role", role.name())
                .setParameter("now", now).setParameter("lr", latestMsgId)
                .executeUpdate();
        em.flush();
    }

    // =============================================================================== inbox

    /** Một dòng inbox thô: conversation + membership của user. */
    public record InboxRow(Conversation conversation, ConversationMember member) {
    }

    /**
     * Python: list_user_conversations (phần truy vấn trang). Keyset
     * {@code (COALESCE(last_message_at, created_at) DESC, conversation_id DESC)}; trả tối đa limit+1 dòng.
     */
    public List<InboxRow> listInbox(Long userId, ConversationType type, int limit,
                                    LocalDateTime cursorTime, Long cursorId) {
        StringBuilder jpql = new StringBuilder("select c, m from Conversation c join ConversationMember m "
                + "on m.conversationId = c.conversationId and m.userId = :uid and m.leftAt is null "
                + "where c.deletedAt is null and (c.lastMessageAt is not null or c.type = :group)");
        if (type != null) {
            jpql.append(" and c.type = :type");
        }
        if (cursorTime != null) {
            jpql.append(" and (coalesce(c.lastMessageAt, c.createdAt) < :ct or "
                    + "(coalesce(c.lastMessageAt, c.createdAt) = :ct and c.conversationId < :cid))");
        }
        jpql.append(" order by coalesce(c.lastMessageAt, c.createdAt) desc, c.conversationId desc");
        TypedQuery<Object[]> q = em.createQuery(jpql.toString(), Object[].class)
                .setParameter("uid", userId).setParameter("group", ConversationType.GROUP);
        if (type != null) {
            q.setParameter("type", type);
        }
        if (cursorTime != null) {
            q.setParameter("ct", cursorTime).setParameter("cid", cursorId);
        }
        q.setMaxResults(limit + 1);
        List<InboxRow> rows = new ArrayList<>();
        for (Object[] r : q.getResultList()) {
            rows.add(new InboxRow((Conversation) r[0], (ConversationMember) r[1]));
        }
        return rows;
    }

    /** Thành viên còn lại (không phải viewer) của các hội thoại DIRECT. */
    public Map<Long, Long> otherMemberByDirectConversation(Collection<Long> conversationIds, Long viewerId) {
        Map<Long, Long> out = new HashMap<>();
        if (conversationIds.isEmpty()) {
            return out;
        }
        List<Object[]> rows = em.createQuery("select m.conversationId, m.userId from ConversationMember m "
                        + "where m.conversationId in :ids and m.userId <> :uid", Object[].class)
                .setParameter("ids", conversationIds).setParameter("uid", viewerId).getResultList();
        for (Object[] r : rows) {
            out.put((Long) r[0], (Long) r[1]);
        }
        return out;
    }

    public Map<Long, Long> activeMemberCounts(Collection<Long> conversationIds) {
        Map<Long, Long> out = new HashMap<>();
        if (conversationIds.isEmpty()) {
            return out;
        }
        List<Object[]> rows = em.createQuery("select m.conversationId, count(m) from ConversationMember m "
                        + "where m.conversationId in :ids and m.leftAt is null group by m.conversationId", Object[].class)
                .setParameter("ids", conversationIds).getResultList();
        for (Object[] r : rows) {
            out.put((Long) r[0], (Long) r[1]);
        }
        return out;
    }

    /** Tin nhắn mới nhất (không xoá, không SYSTEM) cho mỗi hội thoại — một câu truy vấn DISTINCT ON. */
    public Map<Long, Message> latestVisibleMessages(Collection<Long> conversationIds) {
        Map<Long, Message> out = new HashMap<>();
        if (conversationIds.isEmpty()) {
            return out;
        }
        List<?> ids = em.createNativeQuery("select distinct on (conversation_id) message_id from messages "
                        + "where conversation_id in (:ids) and is_deleted = false and type <> 'SYSTEM' "
                        + "order by conversation_id, sent_at desc, message_id desc")
                .setParameter("ids", conversationIds).getResultList();
        if (ids.isEmpty()) {
            return out;
        }
        List<Long> messageIds = ids.stream().map(o -> ((Number) o).longValue()).collect(Collectors.toList());
        List<Message> messages = em.createQuery("select m from Message m where m.messageId in :ids", Message.class)
                .setParameter("ids", messageIds).getResultList();
        for (Message m : messages) {
            out.put(m.getConversation().getConversationId(), m);
        }
        return out;
    }

    /** Số tin chưa đọc theo từng hội thoại (loại tin của chính mình, đã xoá, SYSTEM; nhóm chỉ tính sau joined_at). */
    public Map<Long, Long> unreadCounts(Collection<Long> conversationIds, Long userId) {
        Map<Long, Long> out = new HashMap<>();
        if (conversationIds.isEmpty()) {
            return out;
        }
        List<Object[]> rows = em.createNativeQuery("select m.conversation_id, count(*) from messages m "
                        + "join conversation_members cm on cm.conversation_id = m.conversation_id and cm.user_id = :uid "
                        + "join conversations c on c.conversation_id = m.conversation_id "
                        + "where m.conversation_id in (:ids) and m.sender_id <> :uid and m.is_deleted = false "
                        + "and m.type <> 'SYSTEM' and m.message_id > coalesce(cm.last_read_message_id, 0) "
                        + "and (c.type <> 'GROUP' or m.sent_at >= cm.joined_at) group by m.conversation_id", Object[].class)
                .setParameter("ids", conversationIds).setParameter("uid", userId).getResultList();
        for (Object[] r : rows) {
            out.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }
        return out;
    }

    // =============================================================================== messages

    public Message findMessage(Long messageId) {
        return em.find(Message.class, messageId);
    }

    /** Python: get_message_for_recall — khoá hàng tin nhắn, không lọc is_deleted (để idempotent). */
    public Message lockMessage(Long conversationId, Long messageId) {
        List<Message> l = em.createQuery("select m from Message m where m.conversation.conversationId = :cid "
                        + "and m.messageId = :mid", Message.class)
                .setParameter("cid", conversationId).setParameter("mid", messageId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    /** Python: get_message_by_id_and_conversation — chưa xoá mềm. */
    public Message findLiveMessage(Long conversationId, Long messageId) {
        List<Message> l = em.createQuery("select m from Message m where m.conversation.conversationId = :cid "
                        + "and m.messageId = :mid and m.isDeleted = false", Message.class)
                .setParameter("cid", conversationId).setParameter("mid", messageId).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    public List<Message> messagePage(Long conversationId, LocalDateTime joinedAtFilter,
                                     LocalDateTime cursorTime, Long cursorId, int limit) {
        StringBuilder jpql = new StringBuilder("select m from Message m where m.conversation.conversationId = :cid");
        if (joinedAtFilter != null) {
            jpql.append(" and m.sentAt >= :joined");
        }
        if (cursorTime != null) {
            jpql.append(" and (m.sentAt < :ct or (m.sentAt = :ct and m.messageId < :mid))");
        }
        jpql.append(" order by m.sentAt desc, m.messageId desc");
        TypedQuery<Message> q = em.createQuery(jpql.toString(), Message.class).setParameter("cid", conversationId);
        if (joinedAtFilter != null) {
            q.setParameter("joined", joinedAtFilter);
        }
        if (cursorTime != null) {
            q.setParameter("ct", cursorTime).setParameter("mid", cursorId);
        }
        return q.setMaxResults(limit + 1).getResultList();
    }

    public Map<Long, List<MessageAttachment>> attachmentsByMessage(Collection<Long> messageIds) {
        Map<Long, List<MessageAttachment>> out = new HashMap<>();
        if (messageIds.isEmpty()) {
            return out;
        }
        for (MessageAttachment a : em.createQuery("select a from MessageAttachment a where a.message.messageId in :ids "
                + "order by a.attachmentId", MessageAttachment.class).setParameter("ids", messageIds).getResultList()) {
            out.computeIfAbsent(a.getMessage().getMessageId(), k -> new ArrayList<>()).add(a);
        }
        return out;
    }

    public Map<Long, List<MessageMention>> mentionsByMessage(Collection<Long> messageIds) {
        Map<Long, List<MessageMention>> out = new HashMap<>();
        if (messageIds.isEmpty()) {
            return out;
        }
        for (MessageMention m : em.createQuery("select m from MessageMention m where m.message.messageId in :ids "
                + "order by m.startOffset, m.mentionId", MessageMention.class).setParameter("ids", messageIds).getResultList()) {
            out.computeIfAbsent(m.getMessage().getMessageId(), k -> new ArrayList<>()).add(m);
        }
        return out;
    }

    public Map<Long, User> usersById(Collection<Long> userIds) {
        Map<Long, User> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        for (User u : em.createQuery("select u from User u where u.userId in :ids", User.class)
                .setParameter("ids", userIds).getResultList()) {
            out.put(u.getUserId(), u);
        }
        return out;
    }

    // =============================================================================== reactions

    /** Python: batch_get_reaction_summaries — tổng hợp (đếm giảm dần, rồi theo key) + reaction của viewer. */
    public Map<Long, List<ConversationDtos.ReactionAggregateResponse>> reactionAggregates(Collection<Long> messageIds) {
        Map<Long, List<ConversationDtos.ReactionAggregateResponse>> out = new LinkedHashMap<>();
        if (messageIds.isEmpty()) {
            return out;
        }
        List<Object[]> rows = em.createQuery("select r.message.messageId, r.reactionKey, count(r) "
                        + "from MessageReaction r where r.message.messageId in :ids "
                        + "group by r.message.messageId, r.reactionKey", Object[].class)
                .setParameter("ids", messageIds).getResultList();
        for (Object[] r : rows) {
            out.computeIfAbsent((Long) r[0], k -> new ArrayList<>())
                    .add(new ConversationDtos.ReactionAggregateResponse((ReactionKey) r[1], (Long) r[2]));
        }
        out.values().forEach(l -> l.sort((a, b) -> {
            int c = Long.compare(b.count(), a.count());
            return c != 0 ? c : a.reactionKey().name().compareTo(b.reactionKey().name());
        }));
        return out;
    }

    public Map<Long, ReactionKey> myReactions(Collection<Long> messageIds, Long userId) {
        Map<Long, ReactionKey> out = new HashMap<>();
        if (messageIds.isEmpty() || userId == null) {
            return out;
        }
        List<Object[]> rows = em.createQuery("select r.message.messageId, r.reactionKey from MessageReaction r "
                        + "where r.message.messageId in :ids and r.userId = :uid", Object[].class)
                .setParameter("ids", messageIds).setParameter("uid", userId).getResultList();
        for (Object[] r : rows) {
            out.put((Long) r[0], (ReactionKey) r[1]);
        }
        return out;
    }

    /** Python: set_reaction — {@code ON CONFLICT (message_id, user_id) DO UPDATE} (mỗi user đúng 1 reaction/tin). */
    public void upsertReaction(Long messageId, Long userId, ReactionKey key) {
        em.createNativeQuery("INSERT INTO message_reactions (message_id, user_id, reaction_key, created_at) "
                        + "VALUES (:m, :u, :k, :now) "
                        + "ON CONFLICT ON CONSTRAINT uq_message_reactions_message_user "
                        + "DO UPDATE SET reaction_key = :k")
                .setParameter("m", messageId).setParameter("u", userId).setParameter("k", key.name())
                .setParameter("now", ConversationSupport.nowUtc()).executeUpdate();
    }

    public int deleteReaction(Long messageId, Long userId) {
        return em.createQuery("delete from MessageReaction r where r.message.messageId = :m and r.userId = :u")
                .setParameter("m", messageId).setParameter("u", userId).executeUpdate();
    }

    // =============================================================================== message writes

    public void touchConversationLastMessage(Long conversationId, LocalDateTime at) {
        em.createQuery("update Conversation c set c.lastMessageAt = :at where c.conversationId = :cid")
                .setParameter("at", at).setParameter("cid", conversationId).executeUpdate();
    }

    public void setLastRead(Long conversationId, Long userId, Long messageId) {
        em.createQuery("update ConversationMember m set m.lastReadMessageId = :mid "
                        + "where m.conversationId = :cid and m.userId = :uid")
                .setParameter("mid", messageId).setParameter("cid", conversationId).setParameter("uid", userId)
                .executeUpdate();
    }

    public Long maxLiveMessageId(Long conversationId) {
        return em.createQuery("select max(m.messageId) from Message m where m.conversation.conversationId = :cid "
                + "and m.isDeleted = false", Long.class).setParameter("cid", conversationId).getSingleResult();
    }

    public long countUnreadFor(Long conversationId, Long readerId, long afterId, long upToId) {
        return em.createQuery("select count(m) from Message m where m.conversation.conversationId = :cid "
                        + "and m.senderId <> :uid and m.messageId > :after and m.messageId <= :upto "
                        + "and m.isDeleted = false", Long.class)
                .setParameter("cid", conversationId).setParameter("uid", readerId)
                .setParameter("after", afterId).setParameter("upto", upToId).getSingleResult();
    }

    // =============================================================================== notifications (đọc/đánh dấu)

    /** Có notification chat chưa đọc cho (user, conversation) chưa? (coalesce NEW_MESSAGE như Python). */
    public boolean hasUnreadChatNotification(Long userId, Long conversationId) {
        return em.createQuery("select count(n) from Notification n where n.userId = :uid and n.type = :type "
                        + "and n.refType = 'CONVERSATION' and n.refId = :cid and n.isRead = false", Long.class)
                .setParameter("uid", userId).setParameter("type", NotificationType.NEW_MESSAGE)
                .setParameter("cid", conversationId).getSingleResult() > 0;
    }

    /** Khoá advisory theo (recipient, conversation): tuần tự hoá việc tạo notification chat (như Python). */
    public void lockChatNotification(Long userId, Long conversationId) {
        em.createNativeQuery("select pg_advisory_xact_lock(hashtext(:k))")
                .setParameter("k", "notif_chat:" + userId + ":" + conversationId).getSingleResult();
    }

    public int markChatNotificationsRead(Long userId, Long conversationId) {
        return em.createQuery("update Notification n set n.isRead = true where n.userId = :uid and n.type = :type "
                        + "and n.refType = 'CONVERSATION' and n.refId = :cid and n.isRead = false")
                .setParameter("uid", userId).setParameter("type", NotificationType.NEW_MESSAGE)
                .setParameter("cid", conversationId).executeUpdate();
    }

    // =============================================================================== attachments

    /** Python: get_shared_attachments — keyset (created_at DESC, attachment_id DESC); trả tối đa limit+1. */
    public List<MessageAttachment> sharedAttachments(Long conversationId, List<MessageAttachmentKind> kinds,
                                                     LocalDateTime joinedAtFilter, LocalDateTime cursorTime,
                                                     Long cursorId, int limit) {
        StringBuilder jpql = new StringBuilder("select a from MessageAttachment a join a.message m "
                + "where m.conversation.conversationId = :cid and m.isDeleted = false and a.kind in :kinds");
        if (joinedAtFilter != null) {
            jpql.append(" and m.sentAt >= :joined");
        }
        if (cursorTime != null) {
            jpql.append(" and (a.createdAt < :ct or (a.createdAt = :ct and a.attachmentId < :aid))");
        }
        jpql.append(" order by a.createdAt desc, a.attachmentId desc");
        TypedQuery<MessageAttachment> q = em.createQuery(jpql.toString(), MessageAttachment.class)
                .setParameter("cid", conversationId).setParameter("kinds", kinds);
        if (joinedAtFilter != null) {
            q.setParameter("joined", joinedAtFilter);
        }
        if (cursorTime != null) {
            q.setParameter("ct", cursorTime).setParameter("aid", cursorId);
        }
        return q.setMaxResults(limit + 1).getResultList();
    }

    public Map<MessageAttachmentKind, Long> attachmentKindCounts(Long conversationId, LocalDateTime joinedAtFilter) {
        StringBuilder jpql = new StringBuilder("select a.kind, count(a) from MessageAttachment a join a.message m "
                + "where m.conversation.conversationId = :cid and m.isDeleted = false");
        if (joinedAtFilter != null) {
            jpql.append(" and m.sentAt >= :joined");
        }
        jpql.append(" group by a.kind");
        TypedQuery<Object[]> q = em.createQuery(jpql.toString(), Object[].class).setParameter("cid", conversationId);
        if (joinedAtFilter != null) {
            q.setParameter("joined", joinedAtFilter);
        }
        Map<MessageAttachmentKind, Long> out = new HashMap<>();
        for (Object[] r : q.getResultList()) {
            out.put((MessageAttachmentKind) r[0], (Long) r[1]);
        }
        return out;
    }

    // =============================================================================== invites

    public ConversationInvite findInvite(Long inviteId) {
        return em.find(ConversationInvite.class, inviteId);
    }

    public ConversationInvite lockInvite(Long inviteId) {
        return em.find(ConversationInvite.class, inviteId, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Invitee còn PENDING/PENDING_APPROVAL (trùng chỉ mục unique một phần ux_conversation_invites_pending). */
    public java.util.Set<Long> openInviteeIds(Long conversationId) {
        return new java.util.HashSet<>(em.createQuery("select i.inviteeId from ConversationInvite i "
                        + "where i.conversation.conversationId = :cid and i.status in :st", Long.class)
                .setParameter("cid", conversationId)
                .setParameter("st", List.of(ConversationInviteStatus.PENDING, ConversationInviteStatus.PENDING_APPROVAL))
                .getResultList());
    }

    public java.util.Set<Long> existingUserIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(em.createQuery("select u.userId from User u where u.userId in :ids", Long.class)
                .setParameter("ids", ids).getResultList());
    }

    /**
     * Tạo lời mời với {@code ON CONFLICT ... DO NOTHING} trên chỉ mục unique một phần: nếu hai request đua nhau
     * mời cùng một người, chỉ một bản ghi được tạo (trả null cho request thua).
     */
    public ConversationInvite insertInviteIfAbsent(Long conversationId, Long inviterId, Long inviteeId,
                                                   LocalDateTime now) {
        List<?> r = em.createNativeQuery("INSERT INTO conversation_invites "
                        + "(conversation_id, inviter_id, invitee_id, status, created_at) "
                        + "VALUES (:c, :inviter, :invitee, 'PENDING', :now) "
                        + "ON CONFLICT (conversation_id, invitee_id) "
                        + "WHERE status IN ('PENDING', 'PENDING_APPROVAL') DO NOTHING RETURNING invite_id")
                .setParameter("c", conversationId).setParameter("inviter", inviterId)
                .setParameter("invitee", inviteeId).setParameter("now", now).getResultList();
        if (r.isEmpty()) {
            return null;
        }
        return em.find(ConversationInvite.class, ((Number) r.get(0)).longValue());
    }

    public List<ConversationInvite> pendingInvitesFor(Long userId) {
        return em.createQuery("select i from ConversationInvite i where i.inviteeId = :uid and i.status = :st "
                        + "order by i.createdAt desc, i.inviteId desc", ConversationInvite.class)
                .setParameter("uid", userId).setParameter("st", ConversationInviteStatus.PENDING).getResultList();
    }

    public List<ConversationInvite> pendingApprovals(Long conversationId) {
        return em.createQuery("select i from ConversationInvite i where i.conversation.conversationId = :cid "
                        + "and i.status = :st order by i.createdAt asc, i.inviteId asc", ConversationInvite.class)
                .setParameter("cid", conversationId).setParameter("st", ConversationInviteStatus.PENDING_APPROVAL)
                .getResultList();
    }

    public List<ConversationMember> activeMembersOrdered(Long conversationId) {
        List<ConversationMember> members = em.createQuery("select m from ConversationMember m "
                        + "where m.conversationId = :cid and m.leftAt is null order by m.joinedAt asc, m.userId asc",
                        ConversationMember.class)
                .setParameter("cid", conversationId).getResultList();
        // Python: OWNER (1) → ADMIN (2) → MEMBER (3), rồi joined_at tăng dần (sort ổn định giữ thứ tự joined_at).
        members.sort(java.util.Comparator.comparingInt(m -> switch (m.getRole()) {
            case OWNER -> 1;
            case ADMIN -> 2;
            default -> 3;
        }));
        return members;
    }

    /** Python: get_invite_candidates — bạn bè từng nhắn trực tiếp, mới tương tác nhất trước. */
    public List<Object[]> inviteCandidates(Long groupId, Long inviterId, String query, int limit) {
        StringBuilder jpql = new StringBuilder("select u.userId, u.fullName, u.avatarUrl, c.lastMessageAt "
                + "from Conversation c, User u where c.type = :direct and c.deletedAt is null "
                + "and c.lastMessageAt is not null and (c.userOneId = :inv or c.userTwoId = :inv) "
                + "and u.userId = (case when c.userOneId = :inv then c.userTwoId else c.userOneId end) "
                + "and u.userId <> :inv and u.status = :active "
                + "and u.userId not in (select m.userId from ConversationMember m where m.conversationId = :gid and m.leftAt is null) "
                + "and u.userId not in (select i.inviteeId from ConversationInvite i where i.conversation.conversationId = :gid "
                + "and i.status in :open)");
        if (query != null && !query.isBlank()) {
            jpql.append(" and lower(u.fullName) like :q");
        }
        jpql.append(" order by c.lastMessageAt desc");
        TypedQuery<Object[]> q = em.createQuery(jpql.toString(), Object[].class)
                .setParameter("direct", ConversationType.DIRECT).setParameter("inv", inviterId)
                .setParameter("active", UserStatus.ACTIVE).setParameter("gid", groupId)
                .setParameter("open", List.of(ConversationInviteStatus.PENDING, ConversationInviteStatus.PENDING_APPROVAL));
        if (query != null && !query.isBlank()) {
            q.setParameter("q", "%" + query.trim().toLowerCase() + "%");
        }
        return q.setMaxResults(limit).getResultList();
    }

    public long deactivateAllMembers(Long conversationId, LocalDateTime now) {
        return em.createQuery("update ConversationMember m set m.leftAt = :now "
                        + "where m.conversationId = :cid and m.leftAt is null")
                .setParameter("now", now).setParameter("cid", conversationId).executeUpdate();
    }

    public Long latestLiveMessageId(Long conversationId) {
        return maxLiveMessageId(conversationId);
    }

    // =============================================================================== media cleanup rows (như Python recall)

    /** Python: recall_message_in_db — hàng đợi dọn media bền vững; ON CONFLICT DO NOTHING. */
    public void enqueueMediaCleanup(String storageKey, String resourceType, LocalDateTime nextRetryAt, LocalDateTime now) {
        em.createNativeQuery("INSERT INTO media_cleanup_jobs "
                        + "(storage_key, resource_type, attempt_count, next_retry_at, created_at, updated_at) "
                        + "VALUES (:k, :t, 0, :retry, :now, :now) "
                        + "ON CONFLICT ON CONSTRAINT uq_media_cleanup_jobs_key_type DO NOTHING")
                .setParameter("k", storageKey).setParameter("t", resourceType)
                .setParameter("retry", nextRetryAt).setParameter("now", now).executeUpdate();
    }

    public void deleteMediaCleanup(String storageKey, String resourceType) {
        em.createNativeQuery("DELETE FROM media_cleanup_jobs WHERE storage_key = :k AND resource_type = :t")
                .setParameter("k", storageKey).setParameter("t", resourceType).executeUpdate();
    }

    // =============================================================================== helpers

    Query nativeQuery(String sql) {
        return em.createNativeQuery(sql);
    }
}
