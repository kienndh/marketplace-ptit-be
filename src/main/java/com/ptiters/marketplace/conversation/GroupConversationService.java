package com.ptiters.marketplace.conversation;

import com.ptiters.marketplace.conversation.ConversationDtos.ConversationDetailResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.ConversationInviteResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.ConversationMemberResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.GroupCreateRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.GroupMemberNicknameUpdateRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.GroupUpdateRequest;
import com.ptiters.marketplace.conversation.ConversationDtos.InviteCandidateItem;
import com.ptiters.marketplace.conversation.ConversationDtos.InviteCandidateListResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.PendingApprovalItem;
import com.ptiters.marketplace.conversation.ConversationDtos.PendingApprovalListResponse;
import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.notification.NotificationType;
import com.ptiters.marketplace.notification.NotificationWriter;
import com.ptiters.marketplace.realtime.PresenceService;
import com.ptiters.marketplace.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quản lý nhóm trò chuyện: tạo, mời, duyệt thành viên (requires_approval), vai trò OWNER/ADMIN/MEMBER, biệt danh,
 * chuyển quyền, xoá thành viên, rời nhóm, xoá mềm nhóm. Python: các hàm "Group Management" của {@code service.py}.
 * Ràng buộc đua dùng khoá hàng (SELECT ... FOR UPDATE) + {@code ON CONFLICT} của PostgreSQL.
 */
@Service
@Transactional(readOnly = true)
public class GroupConversationService {

    static final int MAX_ACTIVE_MEMBERS_PER_GROUP = 50;
    static final int MAX_GROUPS_OWNED_PER_USER = 10;

    private final ConversationQueryRepository queries;
    private final ConversationRealtime realtime;
    private final NotificationWriter notificationWriter;
    private final PresenceService presenceService;

    @PersistenceContext
    private EntityManager em;

    @Value("${marketplace.chat.official-support-group-id:0}")
    private long officialSupportGroupId;

    public GroupConversationService(ConversationQueryRepository queries, ConversationRealtime realtime,
                                    NotificationWriter notificationWriter, PresenceService presenceService) {
        this.queries = queries;
        this.realtime = realtime;
        this.notificationWriter = notificationWriter;
        this.presenceService = presenceService;
    }

    // =========================================================================================== create

    /** Python: create_group (tối đa 10 nhóm sở hữu; lời mời ban đầu ở trạng thái PENDING, không sinh notification). */
    @Transactional
    public ConversationDetailResponse createGroup(User cu, GroupCreateRequest payload) {
        if (queries.ownedGroupCount(cu.getUserId()) >= MAX_GROUPS_OWNED_PER_USER) {
            throw ApiException.badRequest("Bạn đã tạo tối đa 10 nhóm trò chuyện.");
        }
        LocalDateTime now = ConversationSupport.nowUtc();
        Conversation conv = Conversation.builder().type(ConversationType.GROUP).title(payload.title().strip())
                .description(payload.description() == null || payload.description().isBlank()
                        ? null : payload.description().strip())
                .createdAt(now).build();
        em.persist(conv);
        em.flush();
        em.persist(ConversationMember.builder().conversationId(conv.getConversationId()).userId(cu.getUserId())
                .role(ConversationMemberRole.OWNER).joinedAt(now).build());
        em.flush();

        if (payload.initialInviteeIds() != null && !payload.initialInviteeIds().isEmpty()) {
            Set<Long> wanted = new LinkedHashSet<>(payload.initialInviteeIds());
            wanted.remove(cu.getUserId());
            wanted.removeIf(java.util.Objects::isNull);
            Set<Long> existing = queries.existingUserIds(wanted);
            int created = 0;
            for (Long inviteeId : wanted) {
                if (created >= 49) {
                    break;
                }
                if (!existing.contains(inviteeId)) {
                    continue; // bỏ qua id không tồn tại (tránh vi phạm FK → 500)
                }
                if (queries.insertInviteIfAbsent(conv.getConversationId(), cu.getUserId(), inviteeId, now) != null) {
                    created++;
                }
            }
        }

        Message sys = createSystemMessage(conv.getConversationId(), cu.getUserId(), cu.getFullName() + " đã tạo nhóm.");
        realtime.publish(List.of(cu.getUserId()),
                ConversationRealtime.messageCreated(conv.getConversationId(), sys.getMessageId(), cu.getUserId()));
        return new ConversationDetailResponse(conv.getConversationId(), "GROUP", conv.getTitle(), conv.getDescription(),
                null, null, 1L, "OWNER", true, null, conv.getCreatedAt(), false);
    }

    // =========================================================================================== invites

    /** Python: invite_members_to_group — khoá nhóm, kiểm sức chứa 50, tạo lời mời + notification (SYSTEM/GROUP_INVITE). */
    @Transactional
    public List<ConversationInviteResponse> inviteMembers(User cu, Long conversationId, List<Long> inviteeIds) {
        Conversation conv = queries.lockConversation(conversationId);
        if (conv == null || conv.getDeletedAt() != null || conv.getType() != ConversationType.GROUP) {
            throw ApiException.badRequest("INVALID_CONVERSATION");
        }
        ConversationMember inviter = queries.findActiveMember(conversationId, cu.getUserId());
        if (inviter == null) {
            throw ApiException.forbidden("Chỉ chủ nhóm hoặc quản trị viên mới có quyền mời thành viên.");
        }
        long activeCount = queries.countActiveMembers(conversationId);
        Set<Long> activeUids = new HashSet<>(queries.activeMemberIds(conversationId));
        Set<Long> openUids = queries.openInviteeIds(conversationId);
        Set<Long> candidates = new LinkedHashSet<>(inviteeIds);
        candidates.removeIf(java.util.Objects::isNull);
        Set<Long> existing = queries.existingUserIds(candidates);
        List<Long> valid = new ArrayList<>();
        for (Long uid : candidates) {
            if (!activeUids.contains(uid) && !openUids.contains(uid) && !uid.equals(cu.getUserId())
                    && existing.contains(uid)) {
                valid.add(uid);
            }
        }
        if (activeCount + valid.size() > MAX_ACTIVE_MEMBERS_PER_GROUP) {
            throw ApiException.badRequest("Nhóm đã đạt giới hạn tối đa 50 thành viên.");
        }
        String groupTitle = conv.getTitle() == null ? "Nhóm trò chuyện" : conv.getTitle().strip();
        String inviterName = cu.getFullName() == null ? "Người dùng" : cu.getFullName().strip();
        LocalDateTime now = ConversationSupport.nowUtc();

        List<ConversationInviteResponse> out = new ArrayList<>();
        for (Long uid : valid) {
            ConversationInvite inv = queries.insertInviteIfAbsent(conversationId, cu.getUserId(), uid, now);
            if (inv == null) {
                continue; // thua cuộc đua: đã có lời mời mở cho người này
            }
            notificationWriter.create(uid, NotificationType.SYSTEM, "Bạn có lời mời tham gia nhóm",
                    inviterName + " đã mời bạn tham gia " + groupTitle + ".", "GROUP_INVITE", inv.getInviteId());
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "GROUP_INVITE_CREATED");
            event.put("invite_id", inv.getInviteId());
            event.put("conversation_id", conversationId);
            realtime.publish(List.of(uid), event);
            out.add(new ConversationInviteResponse(inv.getInviteId(), conversationId, groupTitle, cu.getUserId(),
                    cu.getFullName(), cu.getAvatarUrl(), inv.getStatus().name(), inv.getCreatedAt()));
        }
        return out;
    }

    /** Python: list_user_invites (chỉ PENDING, mới nhất trước). */
    public List<ConversationInviteResponse> listMyInvites(User cu) {
        List<ConversationInvite> invites = queries.pendingInvitesFor(cu.getUserId());
        Map<Long, User> inviters = queries.usersById(invites.stream().map(ConversationInvite::getInviterId).toList());
        List<ConversationInviteResponse> out = new ArrayList<>();
        for (ConversationInvite i : invites) {
            User inviter = inviters.get(i.getInviterId());
            String title = i.getConversation().getTitle();
            out.add(new ConversationInviteResponse(i.getInviteId(), i.getConversation().getConversationId(),
                    title == null || title.isEmpty() ? "Nhóm trò chuyện" : title, i.getInviterId(),
                    inviter == null ? "Người dùng" : inviter.getFullName(), inviter == null ? null : inviter.getAvatarUrl(),
                    i.getStatus().name(), i.getCreatedAt()));
        }
        return out;
    }

    /** Python: respond_to_invite — ACCEPT vào thẳng, hoặc PENDING_APPROVAL nếu nhóm bật phê duyệt và người mời không phải OWNER. */
    @Transactional
    public Map<String, Object> respondToInvite(User cu, Long inviteId, String action) {
        ConversationInvite invite = queries.lockInvite(inviteId);
        if (invite == null || !invite.getInviteeId().equals(cu.getUserId())) {
            throw ApiException.notFound("Lời mời không tồn tại hoặc đã được xử lý.");
        }
        if (invite.getStatus() != ConversationInviteStatus.PENDING) {
            return statusMap(invite.getStatus().name());
        }
        LocalDateTime now = ConversationSupport.nowUtc();
        if ("DECLINE".equals(action)) {
            invite.setStatus(ConversationInviteStatus.DECLINED);
            invite.setRespondedAt(now);
            return statusMap("DECLINED");
        }

        Long conversationId = invite.getConversation().getConversationId();
        Conversation conv = queries.lockActiveConversation(conversationId);
        if (conv == null) {
            throw ApiException.notFound("Nhóm trò chuyện không tồn tại hoặc đã bị giải tán.");
        }
        if (queries.countActiveMembers(conversationId) >= MAX_ACTIVE_MEMBERS_PER_GROUP) {
            throw ApiException.badRequest("Nhóm đã đạt giới hạn tối đa 50 thành viên.");
        }
        ConversationMember inviterMember = queries.findActiveMember(conversationId, invite.getInviterId());
        boolean trusted = inviterMember != null && inviterMember.getRole() == ConversationMemberRole.OWNER;

        if (!Boolean.TRUE.equals(conv.getRequiresApproval()) || trusted) {
            invite.setStatus(ConversationInviteStatus.ACCEPTED);
            invite.setRespondedAt(now);
            queries.upsertActiveMember(conversationId, cu.getUserId(), ConversationMemberRole.MEMBER,
                    queries.maxLiveMessageId(conversationId), now);
            Message sys = createSystemMessage(conversationId, cu.getUserId(), cu.getFullName() + " đã tham gia nhóm.");
            fanOut(conversationId, sys, cu.getUserId(), "MEMBER_JOINED");
            return statusMap("ACCEPTED");
        }
        invite.setStatus(ConversationInviteStatus.PENDING_APPROVAL);
        invite.setRespondedAt(now);
        ConversationMember owner = queries.findActiveOwner(conversationId);
        if (owner != null) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "GROUP_APPROVAL_REQUESTED");
            event.put("conversation_id", conversationId);
            event.put("invite_id", invite.getInviteId());
            event.put("invitee_id", cu.getUserId());
            event.put("invitee_name", cu.getFullName());
            realtime.publish(List.of(owner.getUserId()), event);
        }
        return statusMap("PENDING_APPROVAL");
    }

    // =========================================================================================== members

    public List<ConversationMemberResponse> listMembers(User cu, Long conversationId) {
        if (queries.findActiveConversation(conversationId) == null) {
            throw ApiException.notFound("Nhóm không tồn tại hoặc đã bị giải tán.");
        }
        if (queries.findActiveMember(conversationId, cu.getUserId()) == null) {
            throw ApiException.forbidden("Bạn phải là thành viên nhóm để xem danh sách thành viên.");
        }
        List<ConversationMember> members = queries.activeMembersOrdered(conversationId);
        Map<Long, User> users = queries.usersById(members.stream().map(ConversationMember::getUserId).toList());
        PresenceService.PresenceSnapshot snap = presenceService.getUsersPresence(
                members.stream().map(ConversationMember::getUserId).toList());
        List<ConversationMemberResponse> out = new ArrayList<>();
        for (ConversationMember m : members) {
            User u = users.get(m.getUserId());
            out.add(new ConversationMemberResponse(m.getUserId(), u == null ? null : u.getPublicId(),
                    u == null ? "Người dùng" : u.getFullName(), u == null ? null : u.getAvatarUrl(),
                    m.getRole().name(), m.getJoinedAt(),
                    snap.available() ? snap.onlineUserIds().contains(m.getUserId()) : null, m.getNickname()));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> updateMemberRole(User cu, Long conversationId, Long targetUserId, String newRole) {
        ConversationMember op = queries.findActiveMember(conversationId, cu.getUserId());
        if (op == null || op.getRole() != ConversationMemberRole.OWNER) {
            throw ApiException.forbidden("Chỉ chủ nhóm mới có quyền thay đổi vai trò thành viên.");
        }
        if (cu.getUserId().equals(targetUserId)) {
            throw ApiException.badRequest("Không thể thay đổi vai trò của chính mình.");
        }
        ConversationMember target = queries.lockMember(conversationId, targetUserId);
        if (target == null || target.getLeftAt() != null) {
            throw ApiException.notFound("Thành viên không tồn tại trong nhóm.");
        }
        ConversationMemberRole role = ConversationMemberRole.valueOf(newRole);
        if (target.getRole() == role) {
            return success();
        }
        User targetUser = em.find(User.class, targetUserId);
        String targetName = targetUser == null ? "Thành viên" : targetUser.getFullName();
        target.setRole(role);
        String text = role == ConversationMemberRole.ADMIN
                ? cu.getFullName() + " đã bổ nhiệm " + targetName + " làm phó nhóm."
                : cu.getFullName() + " đã gỡ quyền phó nhóm của " + targetName + ".";
        Message sys = createSystemMessage(conversationId, cu.getUserId(), text);
        fanOut(conversationId, sys, cu.getUserId(), "ROLE_UPDATED");
        return success();
    }

    @Transactional
    public ConversationMemberResponse updateOwnNickname(User cu, Long conversationId,
                                                        GroupMemberNicknameUpdateRequest payload) {
        Conversation conv = queries.findActiveConversation(conversationId);
        if (conv == null) {
            throw ApiException.notFound("Nhóm không tồn tại hoặc đã bị giải tán.");
        }
        if (conv.getType() != ConversationType.GROUP) {
            throw ApiException.badRequest("Tính năng biệt danh chỉ áp dụng cho nhóm trò chuyện.");
        }
        ConversationMember member = queries.lockMember(conversationId, cu.getUserId());
        if (member == null || member.getLeftAt() != null) {
            throw ApiException.forbidden("Bạn không phải là thành viên hoạt động của nhóm này.");
        }
        String nickname = null;
        if (payload.nickname() != null) {
            String trimmed = payload.nickname().strip();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() > 40) {
                    throw ApiException.badRequest("Biệt danh không được vượt quá 40 ký tự.");
                }
                for (int i = 0; i < trimmed.length(); i++) {
                    char c = trimmed.charAt(i);
                    if (c == '\n' || c == '\r' || c == '\t' || c < 32 || (c >= 127 && c <= 159)) {
                        throw ApiException.badRequest("Biệt danh không được chứa ký tự điều khiển hoặc xuống dòng.");
                    }
                }
                nickname = trimmed;
            }
        }
        member.setNickname(nickname);
        em.flush();
        Map<String, Object> event = ConversationRealtime.membershipChanged(conversationId, "NICKNAME_UPDATED");
        event.put("member_user_id", cu.getUserId());
        realtime.publish(queries.activeMemberIds(conversationId), event);
        return new ConversationMemberResponse(cu.getUserId(), cu.getPublicId(), cu.getFullName(), cu.getAvatarUrl(),
                member.getRole().name(), member.getJoinedAt(), true, member.getNickname());
    }

    @Transactional
    public Map<String, Object> transferOwnership(User cu, Long conversationId, Long newOwnerId) {
        ConversationMember current = queries.lockMember(conversationId, cu.getUserId());
        if (current == null || current.getLeftAt() != null || current.getRole() != ConversationMemberRole.OWNER) {
            throw ApiException.forbidden("Chỉ chủ nhóm mới có quyền chuyển nhượng quyền sở hữu.");
        }
        if (cu.getUserId().equals(newOwnerId)) {
            return success();
        }
        ConversationMember target = queries.lockMember(conversationId, newOwnerId);
        if (target == null || target.getLeftAt() != null) {
            throw ApiException.notFound("Thành viên được chuyển giao quyền không tồn tại trong nhóm.");
        }
        User targetUser = em.find(User.class, newOwnerId);
        String targetName = targetUser == null ? "Thành viên" : targetUser.getFullName();
        current.setRole(ConversationMemberRole.MEMBER);
        target.setRole(ConversationMemberRole.OWNER);
        Message sys = createSystemMessage(conversationId, cu.getUserId(),
                cu.getFullName() + " đã chuyển quyền trưởng nhóm cho " + targetName + ".");
        fanOut(conversationId, sys, cu.getUserId(), "OWNER_TRANSFERRED");
        return success();
    }

    @Transactional
    public Map<String, Object> removeMember(User cu, Long conversationId, Long targetUserId) {
        ConversationMember op = queries.findActiveMember(conversationId, cu.getUserId());
        if (op == null || op.getRole() == ConversationMemberRole.MEMBER) {
            throw ApiException.forbidden("Bạn không có quyền xóa thành viên này.");
        }
        if (cu.getUserId().equals(targetUserId)) {
            throw ApiException.badRequest("Không thể tự xóa chính mình, hãy sử dụng tính năng rời nhóm.");
        }
        ConversationMember target = queries.lockMember(conversationId, targetUserId);
        if (target == null || target.getLeftAt() != null) {
            throw ApiException.notFound("Thành viên không tồn tại trong nhóm.");
        }
        if (op.getRole() == ConversationMemberRole.ADMIN && target.getRole() != ConversationMemberRole.MEMBER) {
            throw ApiException.forbidden("Phó nhóm không thể xóa trưởng nhóm hoặc phó nhóm khác.");
        }
        User targetUser = em.find(User.class, targetUserId);
        String targetName = targetUser == null ? "Thành viên" : targetUser.getFullName();
        target.setLeftAt(ConversationSupport.nowUtc());
        Message sys = createSystemMessage(conversationId, cu.getUserId(),
                cu.getFullName() + " đã xóa " + targetName + " khỏi nhóm.");
        fanOut(conversationId, sys, cu.getUserId(), "MEMBER_REMOVED");
        realtime.publish(List.of(targetUserId), ConversationRealtime.membershipChanged(conversationId, "REMOVED"));
        return success();
    }

    @Transactional
    public Map<String, Object> leaveGroup(User cu, Long conversationId) {
        ConversationMember mem = queries.lockMember(conversationId, cu.getUserId());
        if (mem == null || mem.getLeftAt() != null) {
            return success();
        }
        if (mem.getRole() == ConversationMemberRole.OWNER) {
            long others = queries.countActiveMembers(conversationId) - 1;
            if (others > 0) {
                throw ApiException.badRequest("Chủ nhóm phải chuyển quyền sở hữu cho thành viên khác trước khi rời nhóm.");
            }
            throw ApiException.badRequest("Bạn là thành viên duy nhất, vui lòng sử dụng tính năng giải tán nhóm.");
        }
        mem.setLeftAt(ConversationSupport.nowUtc());
        Message sys = createSystemMessage(conversationId, cu.getUserId(), cu.getFullName() + " đã rời khỏi nhóm.");
        fanOut(conversationId, sys, cu.getUserId(), "MEMBER_LEFT");
        realtime.publish(List.of(cu.getUserId()), ConversationRealtime.membershipChanged(conversationId, "LEFT"));
        return success();
    }

    // =========================================================================================== metadata

    @Transactional
    public ConversationDetailResponse updateMetadata(User cu, Long conversationId, GroupUpdateRequest payload) {
        ConversationMember op = queries.findActiveMember(conversationId, cu.getUserId());
        if (op == null) {
            throw ApiException.forbidden("Bạn không phải thành viên của nhóm.");
        }
        ConversationMemberRole role = op.getRole();
        if (payload.requiresApproval() != null && role != ConversationMemberRole.OWNER) {
            throw ApiException.forbidden("Chỉ trưởng nhóm mới có quyền thay đổi cài đặt phê duyệt thành viên.");
        }
        if ((payload.title() != null || payload.description() != null) && role == ConversationMemberRole.MEMBER) {
            throw ApiException.forbidden("Chỉ chủ nhóm hoặc quản trị viên mới có quyền cập nhật thông tin nhóm.");
        }
        Conversation conv = queries.lockActiveConversation(conversationId);
        if (conv == null) {
            throw ApiException.notFound("Nhóm trò chuyện không tồn tại.");
        }
        if (conv.getType() != ConversationType.GROUP) {
            throw ApiException.badRequest("Chỉ có thể cập nhật nhóm trò chuyện.");
        }
        List<Message> sysMsgs = new ArrayList<>();
        if (payload.requiresApproval() != null && !payload.requiresApproval().equals(conv.getRequiresApproval())) {
            conv.setRequiresApproval(payload.requiresApproval());
            sysMsgs.add(createSystemMessage(conversationId, cu.getUserId(), payload.requiresApproval()
                    ? cu.getFullName() + " đã bật tính năng phê duyệt thành viên mới."
                    : cu.getFullName() + " đã tắt tính năng phê duyệt thành viên mới."));
        }
        if (payload.title() != null) {
            String newTitle = payload.title().strip();
            if (!newTitle.equals(conv.getTitle())) {
                conv.setTitle(newTitle);
                sysMsgs.add(createSystemMessage(conversationId, cu.getUserId(),
                        cu.getFullName() + " đã đổi tên nhóm thành \"" + conv.getTitle() + "\"."));
            }
        }
        if (payload.description() != null) {
            String newDesc = payload.description().isEmpty() ? null : payload.description().strip();
            if (newDesc != null && newDesc.isEmpty()) {
                newDesc = null;
            }
            if (!java.util.Objects.equals(newDesc, conv.getDescription())) {
                conv.setDescription(newDesc);
                sysMsgs.add(createSystemMessage(conversationId, cu.getUserId(),
                        cu.getFullName() + " đã cập nhật mô tả nhóm."));
            }
        }
        em.flush();
        if (!sysMsgs.isEmpty()) {
            List<Long> active = queries.activeMemberIds(conversationId);
            for (Message m : sysMsgs) {
                realtime.publish(active, ConversationRealtime.messageCreated(conversationId, m.getMessageId(),
                        cu.getUserId()));
            }
        }
        return new ConversationDetailResponse(conv.getConversationId(), conv.getType().name(), conv.getTitle(),
                conv.getDescription(), conv.getAvatarUrl(), null, queries.countActiveMembers(conversationId),
                role.name(), true, null, conv.getCreatedAt(), Boolean.TRUE.equals(conv.getRequiresApproval()));
    }

    // =========================================================================================== approvals

    public PendingApprovalListResponse listPendingApprovals(User cu, Long conversationId) {
        ConversationMember op = queries.findActiveMember(conversationId, cu.getUserId());
        if (op == null || op.getRole() != ConversationMemberRole.OWNER) {
            throw ApiException.forbidden("Chỉ trưởng nhóm mới có quyền xem danh sách chờ phê duyệt.");
        }
        List<ConversationInvite> invites = queries.pendingApprovals(conversationId);
        Set<Long> ids = new HashSet<>();
        invites.forEach(i -> {
            ids.add(i.getInviteeId());
            ids.add(i.getInviterId());
        });
        Map<Long, User> users = queries.usersById(ids);
        List<PendingApprovalItem> items = new ArrayList<>();
        for (ConversationInvite i : invites) {
            User invitee = users.get(i.getInviteeId());
            User inviter = users.get(i.getInviterId());
            items.add(new PendingApprovalItem(i.getInviteId(), conversationId, i.getInviteeId(),
                    invitee == null ? "Người dùng" : invitee.getFullName(), invitee == null ? null : invitee.getAvatarUrl(),
                    i.getInviterId(), inviter == null ? "Người dùng" : inviter.getFullName(),
                    inviter == null ? null : inviter.getAvatarUrl(), i.getCreatedAt(), i.getRespondedAt()));
        }
        return new PendingApprovalListResponse(items);
    }

    @Transactional
    public Map<String, Object> processPendingApproval(User cu, Long conversationId, Long inviteId, String action) {
        ConversationMember op = queries.findActiveMember(conversationId, cu.getUserId());
        if (op == null || op.getRole() != ConversationMemberRole.OWNER) {
            throw ApiException.forbidden("Chỉ trưởng nhóm mới có quyền phê duyệt thành viên.");
        }
        ConversationInvite invite = queries.lockInvite(inviteId);
        if (invite == null || !invite.getConversation().getConversationId().equals(conversationId)
                || invite.getStatus() != ConversationInviteStatus.PENDING_APPROVAL) {
            throw ApiException.badRequest("Yêu cầu phê duyệt không tồn tại hoặc đã được xử lý.");
        }
        if ("REJECT".equals(action)) {
            invite.setStatus(ConversationInviteStatus.DECLINED);
            publishInviteUpdated(invite, "DECLINED");
            return statusMap("DECLINED");
        }
        Conversation conv = queries.lockActiveConversation(conversationId);
        if (conv == null) {
            throw ApiException.notFound("Nhóm trò chuyện không tồn tại hoặc đã bị giải tán.");
        }
        if (queries.countActiveMembers(conversationId) >= MAX_ACTIVE_MEMBERS_PER_GROUP) {
            throw ApiException.badRequest("Nhóm đã đạt giới hạn tối đa 50 thành viên.");
        }
        if (queries.findActiveMember(conversationId, invite.getInviteeId()) != null) {
            invite.setStatus(ConversationInviteStatus.ACCEPTED);
            publishInviteUpdated(invite, "ACCEPTED");
            Map<String, Object> out = statusMap("ACCEPTED");
            out.put("already_active", true);
            return out;
        }
        invite.setStatus(ConversationInviteStatus.ACCEPTED);
        queries.upsertActiveMember(conversationId, invite.getInviteeId(), ConversationMemberRole.MEMBER,
                queries.maxLiveMessageId(conversationId), ConversationSupport.nowUtc());
        User invitee = em.find(User.class, invite.getInviteeId());
        Message sys = createSystemMessage(conversationId, cu.getUserId(), cu.getFullName() + " đã phê duyệt "
                + (invitee == null ? "Thành viên" : invitee.getFullName()) + " vào nhóm.");
        fanOut(conversationId, sys, cu.getUserId(), "MEMBER_JOINED");
        publishInviteUpdated(invite, "ACCEPTED");
        return statusMap("ACCEPTED");
    }

    // =========================================================================================== delete

    /** Python: delete_group_conversation — xoá mềm (deleted_at) + vô hiệu hoá mọi thành viên; chỉ OWNER; không xoá nhóm hỗ trợ. */
    @Transactional
    public Map<String, Object> deleteGroup(User cu, Long conversationId) {
        Conversation conv = queries.lockConversation(conversationId);
        if (conv == null || conv.getDeletedAt() != null) {
            throw ApiException.notFound("Nhóm không tồn tại hoặc đã bị xóa.");
        }
        if (officialSupportGroupId > 0 && conv.getConversationId() == officialSupportGroupId) {
            throw ApiException.forbidden("Không thể xóa nhóm hỗ trợ chính thức.");
        }
        if (conv.getType() != ConversationType.GROUP) {
            throw ApiException.notFound("Nhóm không tồn tại hoặc đã bị xóa.");
        }
        ConversationMember op = queries.findActiveMember(conversationId, cu.getUserId());
        if (op == null || op.getRole() != ConversationMemberRole.OWNER) {
            throw ApiException.forbidden("Chỉ trưởng nhóm mới có quyền xóa nhóm này.");
        }
        LocalDateTime now = ConversationSupport.nowUtc();
        conv.setDeletedAt(now);
        em.flush();
        queries.deactivateAllMembers(conversationId, now);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Nhóm đã được xóa thành công.");
        return out;
    }

    // =========================================================================================== candidates

    public InviteCandidateListResponse inviteCandidates(User cu, Long groupId, String query, int limit) {
        ConversationQueryRepository.Access access = queries.findAccess(groupId, cu.getUserId());
        if (access == null) {
            throw ApiException.forbidden("Bạn phải là thành viên nhóm để xem danh sách gợi ý mời.");
        }
        if (access.conversation().getType() != ConversationType.GROUP) {
            throw ApiException.badRequest("INVITES_ONLY_FOR_GROUPS");
        }
        List<InviteCandidateItem> items = new ArrayList<>();
        for (Object[] r : queries.inviteCandidates(groupId, cu.getUserId(), query, limit)) {
            items.add(new InviteCandidateItem((Long) r[0], (String) r[1], (String) r[2], (LocalDateTime) r[3]));
        }
        return new InviteCandidateListResponse(items);
    }

    // =========================================================================================== helpers

    /** Python: create_system_message — KHÔNG đụng conversation.last_message_at hay con trỏ đã đọc. */
    private Message createSystemMessage(Long conversationId, Long senderId, String content) {
        Message msg = Message.builder().conversation(em.getReference(Conversation.class, conversationId))
                .senderId(senderId).type(MessageType.SYSTEM).content(content).sentAt(ConversationSupport.nowUtc())
                .build();
        em.persist(msg);
        em.flush();
        return msg;
    }

    /** Phát MESSAGE_CREATED + GROUP_MEMBERSHIP_CHANGED(action) tới mọi thành viên đang hoạt động (sau commit). */
    private void fanOut(Long conversationId, Message sysMsg, Long actorId, String action) {
        List<Long> active = queries.activeMemberIds(conversationId);
        realtime.publish(active, ConversationRealtime.messageCreated(conversationId, sysMsg.getMessageId(), actorId));
        realtime.publish(active, ConversationRealtime.membershipChanged(conversationId, action));
    }

    private void publishInviteUpdated(ConversationInvite invite, String status) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "GROUP_INVITE_UPDATED");
        event.put("invite_id", invite.getInviteId());
        event.put("conversation_id", invite.getConversation().getConversationId());
        event.put("status", status);
        realtime.publish(List.of(invite.getInviteeId()), event);
    }

    private static Map<String, Object> success() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    private static Map<String, Object> statusMap(String status) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("status", status);
        return out;
    }
}
