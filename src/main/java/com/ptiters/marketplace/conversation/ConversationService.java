package com.ptiters.marketplace.conversation;

import com.ptiters.marketplace.conversation.ConversationDtos.ConversationDetailResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.ConversationListResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.ConversationSummaryResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.MessageListResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.MessageMentionCreate;
import com.ptiters.marketplace.conversation.ConversationDtos.MessageReactionSummaryResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.MessageResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.OfficialSupportGroupJoinResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.OfficialSupportGroupResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.OwnedGroupSummaryResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.ParticipantSummary;
import com.ptiters.marketplace.conversation.ConversationDtos.PresenceBatchResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.ProductContextSummary;
import com.ptiters.marketplace.conversation.ConversationDtos.ReactionAggregateResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.SharedAttachmentItemResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.SharedAttachmentListResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.SharedAttachmentSummaryResponse;
import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.notification.NotificationType;
import com.ptiters.marketplace.notification.NotificationWriter;
import com.ptiters.marketplace.order.Order;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.product.ProductStatus;
import com.ptiters.marketplace.realtime.PresenceService;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Nghiệp vụ hội thoại 1-1, tin nhắn, reaction, đã đọc, tệp đính kèm dùng chung, presence và nhóm hỗ trợ chính thức.
 * Quản lý nhóm (tạo/mời/duyệt/vai trò/rời/xoá) nằm ở {@link GroupConversationService}.
 *
 * <p>Python tham chiếu: {@code conversations/service.py}. Realtime/push luôn chạy SAU commit và best-effort.
 */
@Service
@Transactional(readOnly = true)
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    static final int MAX_ATTACHMENTS_PER_MESSAGE = 4;
    static final long MAX_TOTAL_ATTACHMENTS_SIZE = 60L * 1024 * 1024;
    static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    static final long MAX_DOCUMENT_SIZE_BYTES = 20L * 1024 * 1024;
    static final long MAX_AUDIO_SIZE_BYTES = 15L * 1024 * 1024;
    static final long MAX_VIDEO_SIZE_BYTES = 50L * 1024 * 1024;
    static final long MAX_VOICE_DURATION_MS = 300_000L;
    static final int MAX_VIDEOS_PER_MESSAGE = 1;

    private final ConversationQueryRepository queries;
    private final ConversationViewBuilder views;
    private final ConversationRealtime realtime;
    private final UserRepository userRepository;
    private final NotificationWriter notificationWriter;
    private final MediaStorage mediaStorage;
    private final ChatAttachmentValidator attachmentValidator;
    private final PresenceService presenceService;
    private final TransactionTemplate requiresNewTx;

    @PersistenceContext
    private EntityManager em;

    @Value("${marketplace.chat.official-support-group-id:0}")
    private long officialSupportGroupId;

    public ConversationService(ConversationQueryRepository queries, ConversationViewBuilder views,
                               ConversationRealtime realtime, UserRepository userRepository,
                               NotificationWriter notificationWriter, MediaStorage mediaStorage,
                               ChatAttachmentValidator attachmentValidator, PresenceService presenceService,
                               PlatformTransactionManager transactionManager) {
        this.queries = queries;
        this.views = views;
        this.realtime = realtime;
        this.userRepository = userRepository;
        this.notificationWriter = notificationWriter;
        this.mediaStorage = mediaStorage;
        this.attachmentValidator = attachmentValidator;
        this.presenceService = presenceService;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // =========================================================================================== open / create

    /** Python: create_or_open_conversation (từ sản phẩm). */
    @Transactional
    public ConversationDetailResponse createOrOpenFromProduct(User cu, Long productId) {
        Product product = em.find(Product.class, productId);
        if (product == null || Boolean.TRUE.equals(product.getIsDeleted())) {
            throw ApiException.notFound("Sản phẩm không tồn tại.");
        }
        Long sellerId = product.getSeller().getUserId();
        if (sellerId.equals(cu.getUserId())) {
            throw ApiException.badRequest("Cannot start conversation with yourself");
        }
        if (product.getStatus() == ProductStatus.HIDDEN) {
            throw ApiException.notFound("Product not found");
        }
        Conversation conv = queries.getOrCreateDirect(cu.getUserId(), sellerId);
        User other = em.find(User.class, sellerId);
        return new ConversationDetailResponse(conv.getConversationId(), "DIRECT", null, null, null,
                views.participant(other, "Người bán"), null, null, null, views.productContext(product),
                conv.getCreatedAt(), false);
    }

    /** Python: create_or_open_conversation_from_order. */
    @Transactional
    public ConversationDetailResponse createOrOpenFromOrder(User cu, Long orderId) {
        Order order = em.find(Order.class, orderId);
        if (order == null || !(cu.getUserId().equals(order.getBuyerId()) || cu.getUserId().equals(order.getSellerId()))) {
            throw ApiException.notFound("Không tìm thấy đơn hàng hoặc bạn không có quyền truy cập.");
        }
        Long otherId = cu.getUserId().equals(order.getBuyerId()) ? order.getSellerId() : order.getBuyerId();
        Conversation conv = queries.getOrCreateDirect(cu.getUserId(), otherId);
        User other = em.find(User.class, otherId);
        return new ConversationDetailResponse(conv.getConversationId(), "DIRECT", null, null, null,
                views.participant(other, "Người dùng"), null, null, null, null, conv.getCreatedAt(), false);
    }

    /** Python: create_or_open_direct_conversation (theo recipient_public_id). */
    @Transactional
    public ConversationDetailResponse createOrOpenDirect(User cu, UUID recipientPublicId) {
        User recipient = userRepository.findByPublicId(recipientPublicId)
                .orElseThrow(() -> ApiException.notFound("Người dùng không tồn tại."));
        if (recipient.getUserId().equals(cu.getUserId())) {
            throw ApiException.badRequest("Không thể tự nhắn tin cho chính mình.");
        }
        if (recipient.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.badRequest("Tài khoản người dùng hiện không hoạt động.");
        }
        Conversation conv = queries.getOrCreateDirect(cu.getUserId(), recipient.getUserId());
        return new ConversationDetailResponse(conv.getConversationId(), "DIRECT", null, null, null,
                views.participant(recipient, "Người dùng"), null, null, null, null, conv.getCreatedAt(), false);
    }

    // =========================================================================================== read

    /** Python: get_conversation — người ngoài nhận 404 (không lộ sự tồn tại). */
    public ConversationDetailResponse getConversation(User cu, Long conversationId) {
        ConversationQueryRepository.Access access = requireAccess(cu.getUserId(), conversationId, "Conversation not found");
        Conversation conv = access.conversation();
        ConversationMember member = access.member();

        ParticipantSummary other = null;
        if (conv.getType() == ConversationType.DIRECT) {
            Long otherId = cu.getUserId().equals(conv.getUserOneId()) ? conv.getUserTwoId() : conv.getUserOneId();
            other = views.participant(otherId == null ? null : em.find(User.class, otherId), "Người dùng");
        }
        Long memberCount = conv.getType() == ConversationType.GROUP ? queries.countActiveMembers(conversationId) : null;
        String title = conv.getType() != ConversationType.DIRECT ? conv.getTitle() : (other == null ? null : other.fullName());
        return new ConversationDetailResponse(conv.getConversationId(), conv.getType().name(), title,
                conv.getDescription(), conv.getAvatarUrl(), other, memberCount, member.getRole().name(), true, null,
                conv.getCreatedAt(),
                conv.getType() == ConversationType.GROUP && Boolean.TRUE.equals(conv.getRequiresApproval()));
    }

    /** Python: list_conversations — inbox keyset (COALESCE(last_message_at, created_at) DESC, id DESC). */
    public ConversationListResponse listConversations(User cu, String type, int limit, String cursor) {
        long owned = queries.ownedGroupCount(cu.getUserId());
        ConversationType typeFilter = null;
        if (type != null && !type.isEmpty()) {
            try {
                typeFilter = ConversationType.valueOf(type);
            } catch (IllegalArgumentException e) {
                return new ConversationListResponse(List.of(), null, false, owned); // Python: so sánh chuỗi → rỗng
            }
        }
        LocalDateTime cursorTime = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isEmpty()) {
            try {
                Map<String, Object> data = ConversationSupport.decodeCursor(cursor);
                cursorTime = ConversationSupport.cursorTime(data, "last_message_at");
                cursorId = ConversationSupport.cursorLong(data, "conversation_id");
            } catch (ConversationSupport.InvalidCursorException e) {
                throw ConversationSupport.unprocessable(e.getMessage());
            }
        }
        List<ConversationQueryRepository.InboxRow> rows = queries.listInbox(cu.getUserId(), typeFilter, limit,
                cursorTime, cursorId);
        boolean hasMore = rows.size() > limit;
        List<ConversationQueryRepository.InboxRow> page = hasMore ? rows.subList(0, limit) : rows;
        if (page.isEmpty()) {
            return new ConversationListResponse(List.of(), null, false, owned);
        }
        Conversation last = page.get(page.size() - 1).conversation();
        LocalDateTime lastDt = last.getLastMessageAt() != null ? last.getLastMessageAt() : last.getCreatedAt();
        String nextCursor = hasMore ? ConversationSupport.encodeCursor("last_message_at", lastDt.toString(),
                "conversation_id", last.getConversationId()) : null;

        List<Long> allIds = page.stream().map(r -> r.conversation().getConversationId()).toList();
        List<Long> directIds = page.stream().filter(r -> r.conversation().getType() == ConversationType.DIRECT)
                .map(r -> r.conversation().getConversationId()).toList();
        List<Long> groupIds = page.stream().filter(r -> r.conversation().getType() == ConversationType.GROUP)
                .map(r -> r.conversation().getConversationId()).toList();

        Map<Long, Long> otherByConv = queries.otherMemberByDirectConversation(directIds, cu.getUserId());
        Map<Long, User> users = queries.usersById(new HashSet<>(otherByConv.values()));
        Map<Long, Long> memberCounts = queries.activeMemberCounts(groupIds);
        Map<Long, Message> latest = queries.latestVisibleMessages(allIds);
        Map<Long, Long> unread = queries.unreadCounts(allIds, cu.getUserId());
        Map<Long, List<MessageAttachment>> latestAttachments = queries.attachmentsByMessage(
                latest.values().stream().map(Message::getMessageId).toList());
        Set<Long> productIds = new HashSet<>();
        latest.values().forEach(m -> {
            if (m.getProductId() != null) {
                productIds.add(m.getProductId());
            }
        });
        Map<Long, Product> products = new HashMap<>();
        if (!productIds.isEmpty()) {
            em.createQuery("select p from Product p where p.productId in :ids and p.isDeleted = false", Product.class)
                    .setParameter("ids", productIds).getResultList()
                    .forEach(p -> products.put(p.getProductId(), p));
        }

        List<ConversationSummaryResponse> items = new ArrayList<>();
        for (ConversationQueryRepository.InboxRow row : page) {
            Conversation c = row.conversation();
            boolean direct = c.getType() == ConversationType.DIRECT;
            User otherUser = direct ? users.get(otherByConv.get(c.getConversationId())) : null;
            ParticipantSummary part = null;
            if (direct) {
                part = new ParticipantSummary(otherUser == null ? 0L : otherUser.getUserId(),
                        otherUser == null ? null : otherUser.getPublicId(),
                        otherUser == null ? "Người dùng" : otherUser.getFullName(),
                        otherUser == null ? null : otherUser.getAvatarUrl());
            }
            Message lm = latest.get(c.getConversationId());
            ProductContextSummary prodCtx = null;
            if (lm != null && lm.getProductId() != null && products.containsKey(lm.getProductId())) {
                prodCtx = views.productContext(products.get(lm.getProductId()));
            }
            items.add(new ConversationSummaryResponse(c.getConversationId(), c.getType().name(),
                    direct ? (otherUser == null ? "Người dùng" : otherUser.getFullName()) : c.getTitle(),
                    direct ? (otherUser == null ? null : otherUser.getAvatarUrl()) : c.getAvatarUrl(), part,
                    memberCounts.getOrDefault(c.getConversationId(), direct ? 2L : 1L), row.member().getRole().name(),
                    true, prodCtx,
                    lm == null ? null : views.preview(lm, latestAttachments.get(lm.getMessageId())),
                    ConversationSupport.isoZ(c.getLastMessageAt()), unread.getOrDefault(c.getConversationId(), 0L),
                    c.getCreatedAt()));
        }
        return new ConversationListResponse(items, nextCursor, hasMore, owned);
    }

    public OwnedGroupSummaryResponse ownedGroupCount(User cu) {
        return new OwnedGroupSummaryResponse(queries.ownedGroupCount(cu.getUserId()));
    }

    /** Python: get_messages — nhóm chỉ thấy tin từ lúc joined_at; cursor (sent_at, message_id). */
    public MessageListResponse getMessages(User cu, Long conversationId, int limit, String cursor) {
        ConversationQueryRepository.Access access = requireAccess(cu.getUserId(), conversationId, "Conversation not found");
        LocalDateTime joinedFilter = access.conversation().getType() == ConversationType.GROUP
                ? access.member().getJoinedAt() : null;
        LocalDateTime cursorTime = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isEmpty()) {
            try {
                Map<String, Object> data = ConversationSupport.decodeCursor(cursor);
                cursorTime = ConversationSupport.cursorTime(data, "sent_at");
                cursorId = ConversationSupport.cursorLong(data, "message_id");
            } catch (ConversationSupport.InvalidCursorException e) {
                throw ConversationSupport.unprocessable(e.getMessage());
            }
        }
        List<Message> rows = queries.messagePage(conversationId, joinedFilter, cursorTime, cursorId, limit);
        boolean hasMore = rows.size() > limit;
        List<Message> items = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            Message lastItem = items.get(items.size() - 1);
            nextCursor = ConversationSupport.encodeCursor("sent_at", lastItem.getSentAt().toString(),
                    "message_id", lastItem.getMessageId());
        }
        return new MessageListResponse(views.messages(items, cu.getUserId()), nextCursor, hasMore);
    }

    // =========================================================================================== send message

    /** Đầu vào đã chuẩn hoá cho gửi tin (nhánh multipart hoặc JSON). */
    public record SendMessageInput(String content, Long productId, Long voiceDurationMs,
                                   List<MessageMentionCreate> mentions, Long replyToMessageId,
                                   List<MultipartFile> files) {
    }

    private record ValidatedFile(String filename, MessageAttachmentKind kind, String mime, byte[] bytes) {
    }

    /** Python: send_message. Upload trước, ghi DB sau; ghi DB lỗi → dọn asset đã upload (best-effort). */
    @Transactional
    public MessageResponse sendMessage(User cu, Long conversationId, SendMessageInput in) {
        ConversationQueryRepository.Access access = requireAccess(cu.getUserId(), conversationId, "Conversation not found");
        Conversation conv = access.conversation();

        String content = in.content() == null ? null : in.content().strip();
        if (content != null && content.isEmpty()) {
            content = null;
        }
        if (content != null && content.length() > 2000) {
            throw ConversationSupport.unprocessable("Message content cannot exceed 2000 characters");
        }
        List<MultipartFile> activeFiles = new ArrayList<>();
        if (in.files() != null) {
            for (MultipartFile f : in.files()) {
                if (f != null && f.getOriginalFilename() != null && !f.getOriginalFilename().isEmpty()) {
                    activeFiles.add(f);
                }
            }
        }
        if (activeFiles.size() > MAX_ATTACHMENTS_PER_MESSAGE) {
            throw ApiException.badRequest("Cannot attach more than " + MAX_ATTACHMENTS_PER_MESSAGE
                    + " files per message.");
        }
        if (content == null && activeFiles.isEmpty() && in.productId() == null) {
            throw ConversationSupport.unprocessable("Message must contain text content or at least one attachment.");
        }
        if (in.voiceDurationMs() != null && in.voiceDurationMs() > MAX_VOICE_DURATION_MS) {
            throw ApiException.badRequest("Tin nhắn thoại vượt quá thời lượng tối đa 5 phút.");
        }

        Long productId = in.productId();
        if (productId != null) {
            if (conv.getType() != ConversationType.DIRECT) {
                throw ApiException.badRequest("Không thể gửi thẻ sản phẩm vào nhóm trò chuyện.");
            }
            Product product = em.find(Product.class, productId);
            if (product == null || Boolean.TRUE.equals(product.getIsDeleted())) {
                throw ApiException.notFound("Referenced product not found");
            }
            Long sellerId = product.getSeller().getUserId();
            if (!sellerId.equals(conv.getUserOneId()) && !sellerId.equals(conv.getUserTwoId())) {
                throw ApiException.badRequest("Product does not belong to conversation participants");
            }
        }

        Message replied = null;
        if (in.replyToMessageId() != null) {
            Message target = queries.findMessage(in.replyToMessageId());
            if (target == null || !target.getConversation().getConversationId().equals(conversationId)) {
                throw ApiException.notFound("Tin nhắn được trả lời không tồn tại hoặc không thể truy cập.");
            }
            if (target.getType() == MessageType.SYSTEM) {
                throw ApiException.badRequest("Không thể trả lời tin nhắn hệ thống.");
            }
            if (Boolean.TRUE.equals(target.getIsDeleted())) {
                throw ApiException.badRequest("Không thể trả lời tin nhắn đã bị xóa.");
            }
            replied = target;
        }

        List<ValidatedFile> validated = validateFiles(activeFiles);
        long videos = validated.stream().filter(f -> f.kind() == MessageAttachmentKind.VIDEO).count();
        if (videos > MAX_VIDEOS_PER_MESSAGE) {
            throw ConversationSupport.unprocessable("Mỗi tin nhắn chỉ được đính kèm tối đa " + MAX_VIDEOS_PER_MESSAGE
                    + " video.");
        }

        List<MessageMentionCreate> mentions = in.mentions() == null ? List.of() : in.mentions();
        List<MessageMentionCreate> acceptedMentions = validateMentions(conv, conversationId, content, mentions);

        // ---- Upload tệp (trước khi ghi DB); nếu transaction không commit thì dọn asset mồ côi.
        List<ConversationRealtime.UploadedRef> uploaded = new ArrayList<>();
        realtime.destroyAssetsUnlessCommitted(uploaded);
        List<MessageAttachment> attachmentRows = new ArrayList<>();
        LocalDateTime now = ConversationSupport.nowUtc();
        for (ValidatedFile f : validated) {
            MediaStorage.UploadedAsset asset = mediaStorage.uploadMessageAttachment(f.bytes(), f.filename(),
                    conversationId, f.kind().name());
            uploaded.add(new ConversationRealtime.UploadedRef(asset.publicId(), ConversationViewBuilder.resourceType(f.kind())));

            boolean singleVoiceNote = validated.size() == 1 && f.kind() == MessageAttachmentKind.AUDIO;
            Long verifiedMs = null;
            if (asset.durationSeconds() != null) {
                if (asset.durationSeconds() > MAX_VOICE_DURATION_MS / 1000.0) {
                    throw ApiException.badRequest("Tin nhắn thoại vượt quá thời lượng tối đa 5 phút.");
                }
                if (asset.durationSeconds() > 0) {
                    verifiedMs = (long) (asset.durationSeconds() * 1000);
                }
            }
            if (singleVoiceNote && in.voiceDurationMs() != null) {
                if (in.voiceDurationMs() > MAX_VOICE_DURATION_MS) {
                    throw ApiException.badRequest("Tin nhắn thoại vượt quá thời lượng tối đa 5 phút.");
                }
                if (verifiedMs == null && in.voiceDurationMs() > 0) {
                    verifiedMs = in.voiceDurationMs();
                }
            }
            String url = asset.secureUrl() != null ? asset.secureUrl() : "";
            attachmentRows.add(MessageAttachment.builder().kind(f.kind()).url(url).storageKey(asset.publicId())
                    .mimeType(f.mime()).originalName(f.filename()).sizeBytes((long) f.bytes().length)
                    .durationMs(f.kind() == MessageAttachmentKind.AUDIO ? verifiedMs : null).createdAt(now).build());
        }

        // ---- Ghi DB (cùng một transaction: tin nhắn + đính kèm + mention + notification).
        // Lỗi DB bất kỳ → rollback, asset đã upload được dọn ở afterCompletion, client nhận 500 giống Python.
        Message msg;
        String senderName;
        try {
        msg = Message.builder().conversation(em.getReference(Conversation.class, conversationId))
                .senderId(cu.getUserId()).type(MessageType.TEXT).content(content).productId(productId)
                .sentAt(now).replyToMessageId(replied == null ? null : replied.getMessageId()).build();
        em.persist(msg);
        em.flush();
        for (MessageAttachment att : attachmentRows) {
            att.setMessage(msg);
            em.persist(att);
        }
        for (MessageMentionCreate m : acceptedMentions) {
            em.persist(MessageMention.builder().message(msg).mentionedUserId(m.mentionedUserId())
                    .displayText(m.displayText()).startOffset(m.startOffset()).endOffset(m.endOffset())
                    .createdAt(now).build());
        }
        em.flush();
        queries.touchConversationLastMessage(conversationId, now);
        queries.setLastRead(conversationId, cu.getUserId(), msg.getMessageId());

        senderName = cu.getFullName() != null && !cu.getFullName().isBlank() ? cu.getFullName().strip() : null;
        if (conv.getType() == ConversationType.DIRECT) {
            Long recipientId = cu.getUserId().equals(conv.getUserOneId()) ? conv.getUserTwoId() : conv.getUserOneId();
            if (recipientId != null && !recipientId.equals(cu.getUserId())) {
                createChatNotification(recipientId, conversationId, "Tin nhắn mới",
                        (senderName == null ? "Một người dùng" : senderName) + " đã gửi tin nhắn cho bạn.");
            }
        } else if (!acceptedMentions.isEmpty()) {
            Set<Long> targets = new LinkedHashSet<>();
            for (MessageMentionCreate m : acceptedMentions) {
                if (!m.mentionedUserId().equals(cu.getUserId())) {
                    targets.add(m.mentionedUserId());
                }
            }
            String groupTitle = conv.getTitle() != null && !conv.getTitle().isBlank() ? conv.getTitle().strip() : "một nhóm";
            for (Long target : targets) {
                createChatNotification(target, conversationId, "Bạn được nhắc đến trong nhóm",
                        (senderName == null ? "Một thành viên" : senderName) + " đã nhắc đến bạn trong " + groupTitle + ".");
            }
        }

        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to persist message in conversation {}: {}", conversationId, e.toString());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to process message");
        }

        // ---- Realtime + push SAU commit (best-effort).
        List<Long> activeMembers = queries.activeMemberIds(conversationId);
        realtime.publish(activeMembers, ConversationRealtime.messageCreated(conversationId, msg.getMessageId(),
                cu.getUserId()));
        List<Long> pushTargets = activeMembers.stream().filter(u -> !u.equals(cu.getUserId())).toList();
        if (!pushTargets.isEmpty()) {
            String body = content != null ? (content.length() > 80 ? content.substring(0, 80) + "..." : content)
                    : "Đã gửi một tệp đính kèm";
            Map<String, String> data = new LinkedHashMap<>();
            data.put("type", "MESSAGE_CREATED");
            data.put("conversation_id", String.valueOf(conversationId));
            data.put("ref_type", "CONVERSATION");
            data.put("ref_id", String.valueOf(conversationId));
            realtime.push(pushTargets, "Tin nhắn mới từ " + (cu.getFullName() == null ? "Người dùng" : cu.getFullName()),
                    body, data);
        }

        return views.messages(List.of(msg), cu.getUserId()).get(0);
    }

    private void createChatNotification(Long recipientId, Long conversationId, String title, String body) {
        queries.lockChatNotification(recipientId, conversationId);
        if (queries.hasUnreadChatNotification(recipientId, conversationId)) {
            // Python làm mới title/body/created_at của notification chưa đọc (coalesce). NotificationWriter chỉ
            // hỗ trợ tạo mới, nên giữ nguyên một notification chưa đọc duy nhất cho mỗi (user, hội thoại).
            return;
        }
        notificationWriter.create(recipientId, NotificationType.NEW_MESSAGE, title, body, "CONVERSATION", conversationId);
    }

    private List<ValidatedFile> validateFiles(List<MultipartFile> activeFiles) {
        List<ValidatedFile> out = new ArrayList<>();
        long total = 0;
        for (MultipartFile file : activeFiles) {
            String name = file.getOriginalFilename();
            if (file.getSize() > MAX_VIDEO_SIZE_BYTES) {
                throw ApiException.badRequest("Tệp " + name + " vượt quá dung lượng tối đa cho phép.");
            }
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException e) {
                throw ApiException.badRequest("Tệp " + name + " rỗng.");
            }
            if (bytes.length == 0) {
                throw ApiException.badRequest("Tệp " + name + " rỗng.");
            }
            total += bytes.length;
            if (total > MAX_TOTAL_ATTACHMENTS_SIZE) {
                throw ApiException.badRequest("Tổng dung lượng các tệp đính kèm vượt quá giới hạn 60MB.");
            }
            ChatAttachmentValidator.Detected d = attachmentValidator.validate(bytes, name);
            long size = bytes.length;
            switch (d.kind()) {
                case IMAGE -> {
                    if (size > MAX_IMAGE_SIZE_BYTES) {
                        throw ApiException.badRequest("Ảnh " + name + " vượt quá giới hạn 10MB.");
                    }
                }
                case PDF, DOCUMENT -> {
                    if (size > MAX_DOCUMENT_SIZE_BYTES) {
                        throw ApiException.badRequest("Tài liệu " + name + " vượt quá giới hạn 20MB.");
                    }
                }
                case AUDIO -> {
                    if (size > MAX_AUDIO_SIZE_BYTES) {
                        throw ApiException.badRequest("Tệp âm thanh " + name + " vượt quá giới hạn 15MB.");
                    }
                }
                case VIDEO -> {
                    if (size > MAX_VIDEO_SIZE_BYTES) {
                        throw ApiException.badRequest("Video " + name + " vượt quá giới hạn 50MB.");
                    }
                }
            }
            out.add(new ValidatedFile(name, d.kind(), d.mime(), bytes));
        }
        return out;
    }

    /** Python: create_message (phần mentions) — chỉ GROUP; mentioned user phải là thành viên hoạt động. */
    private List<MessageMentionCreate> validateMentions(Conversation conv, Long conversationId, String content,
                                                        List<MessageMentionCreate> mentions) {
        if (mentions.isEmpty()) {
            return List.of();
        }
        if (conv.getType() != ConversationType.GROUP) {
            throw ApiException.badRequest("Tính năng gắn thẻ chỉ khả dụng trong nhóm và cộng đồng.");
        }
        Set<Long> active = new HashSet<>(queries.activeMemberIds(conversationId));
        Set<Long> seen = new HashSet<>();
        List<MessageMentionCreate> accepted = new ArrayList<>();
        for (MessageMentionCreate m : mentions) {
            if (m == null || m.mentionedUserId() == null || m.displayText() == null || m.startOffset() == null
                    || m.endOffset() == null || m.displayText().isEmpty() || m.displayText().length() > 100
                    || m.startOffset() < 0 || m.endOffset() < 0 || m.mentionedUserId() <= 0) {
                throw ConversationSupport.unprocessable("Vị trí gắn thẻ không khớp với nội dung tin nhắn.");
            }
            if (!active.contains(m.mentionedUserId())) {
                throw ConversationSupport.unprocessable(
                        "Một số người dùng được gắn thẻ không còn là thành viên hoạt động trong nhóm.");
            }
            if (!seen.add(m.mentionedUserId())) {
                continue; // một mention mỗi (tin nhắn, user) — khớp unique uq_message_mentions_msg_user
            }
            int start = m.startOffset();
            int end = m.endOffset();
            if (content == null || start < 0 || end > content.length() || start >= end
                    || !content.substring(start, end).equals(m.displayText())) {
                throw ConversationSupport.unprocessable("Vị trí gắn thẻ không khớp với nội dung tin nhắn.");
            }
            accepted.add(m);
        }
        return accepted;
    }

    // =========================================================================================== recall

    /** Python: recall_message — chỉ người gửi, soft-delete (tombstone), idempotent, xếp hàng dọn media bền vững. */
    @Transactional
    public MessageResponse recallMessage(User cu, Long conversationId, Long messageId) {
        if (cu.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("Tài khoản của bạn không ở trạng thái hoạt động.");
        }
        ConversationQueryRepository.Access access = queries.findAccess(conversationId, cu.getUserId());
        if (access == null) {
            throw ApiException.notFound("Tin nhắn không tồn tại hoặc bạn không có quyền truy cập.");
        }
        Message msg = queries.lockMessage(conversationId, messageId);
        if (msg == null) {
            throw ApiException.notFound("Tin nhắn không tồn tại hoặc bạn không có quyền truy cập.");
        }
        if (!msg.getSenderId().equals(cu.getUserId())) {
            throw ApiException.forbidden("Chỉ người gửi mới có thể thu hồi tin nhắn.");
        }
        if (msg.getType() == MessageType.SYSTEM) {
            throw ApiException.badRequest("Cannot recall system messages.");
        }
        ReplySource reply = replySource(msg);
        if (Boolean.TRUE.equals(msg.getIsDeleted())) {
            return tombstone(cu, msg, reply);
        }

        List<MessageAttachment> atts = queries.attachmentsByMessage(List.of(messageId)).getOrDefault(messageId, List.of());
        List<String[]> cleanup = new ArrayList<>();
        LocalDateTime now = ConversationSupport.nowUtc();
        for (MessageAttachment a : atts) {
            if (a.getStorageKey() != null && !a.getStorageKey().isEmpty()) {
                String rt = ConversationViewBuilder.resourceType(a.getKind());
                cleanup.add(new String[]{a.getStorageKey(), rt});
                queries.enqueueMediaCleanup(a.getStorageKey(), rt, now.plusSeconds(60), now);
            }
        }
        msg.setIsDeleted(true);
        msg.setContent(null);
        em.flush();

        List<Long> members = queries.activeMemberIds(conversationId);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "MESSAGE_DELETED");
        event.put("conversation_id", conversationId);
        event.put("message_id", messageId);
        realtime.publish(members, event);
        if (!cleanup.isEmpty()) {
            realtime.afterCommit(() -> {
                for (String[] job : cleanup) {
                    try {
                        if (mediaStorage.destroyAsset(job[0], job[1])) {
                            requiresNewTx.executeWithoutResult(s -> queries.deleteMediaCleanup(job[0], job[1]));
                        }
                    } catch (RuntimeException e) {
                        log.warn("Immediate media destroy failed for {} ({}): {}. Worker sẽ thử lại.", job[0], job[1],
                                e.toString());
                    }
                }
            });
        }
        return tombstone(cu, msg, reply);
    }

    private record ReplySource(Message target, User sender) {
    }

    private ReplySource replySource(Message msg) {
        if (msg.getReplyToMessageId() == null) {
            return null;
        }
        Message target = queries.findMessage(msg.getReplyToMessageId());
        return new ReplySource(target, target == null ? null : em.find(User.class, target.getSenderId()));
    }

    private MessageResponse tombstone(User cu, Message msg, ReplySource reply) {
        return new MessageResponse(msg.getMessageId(), msg.getConversation().getConversationId(), msg.getSenderId(),
                cu.getPublicId(), cu.getFullName(), cu.getAvatarUrl(), msg.getType().name(), null, null, null,
                ConversationSupport.isoZ(msg.getSentAt()), true, true, List.of(), List.of(), List.of(), null,
                reply == null ? null : views.replySummary(reply.target(), reply.sender()));
    }

    // =========================================================================================== read receipts

    /** Python: mark_as_read — đẩy last_read_message_id tới tin mới nhất + đánh dấu notification chat đã đọc. */
    @Transactional
    public Map<String, Object> markAsRead(User cu, Long conversationId) {
        ConversationQueryRepository.Access access = requireAccess(cu.getUserId(), conversationId,
                "Cuộc trò chuyện không tồn tại.");
        Long maxId = queries.maxLiveMessageId(conversationId);
        long count = 0;
        if (maxId != null) {
            long prev = access.member().getLastReadMessageId() == null ? 0 : access.member().getLastReadMessageId();
            count = queries.countUnreadFor(conversationId, cu.getUserId(), prev, maxId);
            queries.setLastRead(conversationId, cu.getUserId(), maxId);
            queries.markChatNotificationsRead(cu.getUserId(), conversationId);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("read_count", count);
        return out;
    }

    // =========================================================================================== reactions

    @Transactional
    public MessageReactionSummaryResponse setReaction(User cu, Long conversationId, Long messageId, ReactionKey key) {
        Conversation conv = authorizeReaction(cu, conversationId, messageId);
        queries.upsertReaction(messageId, cu.getUserId(), key);
        MessageReactionSummaryResponse summary = reactionSummary(messageId, cu.getUserId());
        publishReaction(conv, conversationId, messageId, cu.getUserId(), summary, key.name());
        return summary;
    }

    @Transactional
    public MessageReactionSummaryResponse removeReaction(User cu, Long conversationId, Long messageId) {
        Conversation conv = authorizeReaction(cu, conversationId, messageId);
        queries.deleteReaction(messageId, cu.getUserId());
        MessageReactionSummaryResponse summary = reactionSummary(messageId, cu.getUserId());
        publishReaction(conv, conversationId, messageId, cu.getUserId(), summary, null);
        return summary;
    }

    private MessageReactionSummaryResponse reactionSummary(Long messageId, Long userId) {
        List<ReactionAggregateResponse> reactions = queries.reactionAggregates(List.of(messageId))
                .getOrDefault(messageId, List.of());
        return new MessageReactionSummaryResponse(messageId, reactions, queries.myReactions(List.of(messageId), userId)
                .get(messageId));
    }

    private void publishReaction(Conversation conv, Long conversationId, Long messageId, Long actorId,
                                 MessageReactionSummaryResponse summary, String actorReaction) {
        List<Long> recipients = conv.getType() == ConversationType.DIRECT
                ? java.util.stream.Stream.of(conv.getUserOneId(), conv.getUserTwoId())
                .filter(java.util.Objects::nonNull).toList()
                : queries.activeMemberIds(conversationId);
        List<Map<String, Object>> reactions = new ArrayList<>();
        for (ReactionAggregateResponse r : summary.reactions()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reaction_key", r.reactionKey().name());
            m.put("count", r.count());
            reactions.add(m);
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "MESSAGE_REACTIONS_UPDATED");
        event.put("conversation_id", conversationId);
        event.put("message_id", messageId);
        event.put("reactions", reactions);
        event.put("actor_user_id", actorId);
        event.put("actor_reaction", actorReaction);
        realtime.publish(recipients, event);
    }

    /** Python: _authorize_reaction_access — mọi từ chối đều 404 để không lộ sự tồn tại. */
    private Conversation authorizeReaction(User cu, Long conversationId, Long messageId) {
        String denied = "Cuộc trò chuyện không tồn tại hoặc bạn không có quyền truy cập.";
        Conversation conv = queries.findActiveConversation(conversationId);
        if (conv == null) {
            throw ApiException.notFound(denied);
        }
        LocalDateTime joinedAt = null;
        if (conv.getType() == ConversationType.DIRECT) {
            if (!cu.getUserId().equals(conv.getUserOneId()) && !cu.getUserId().equals(conv.getUserTwoId())) {
                throw ApiException.notFound(denied);
            }
        } else {
            ConversationMember member = queries.findActiveMember(conversationId, cu.getUserId());
            if (member == null) {
                throw ApiException.notFound(denied);
            }
            joinedAt = member.getJoinedAt();
        }
        Message msg = queries.findLiveMessage(conversationId, messageId);
        if (msg == null) {
            throw ApiException.notFound("Tin nhắn không tồn tại.");
        }
        if (conv.getType() == ConversationType.GROUP && joinedAt != null && msg.getSentAt().isBefore(joinedAt)) {
            throw ApiException.notFound("Tin nhắn không tồn tại hoặc bạn không có quyền truy cập.");
        }
        if (msg.getType() == MessageType.SYSTEM) {
            throw ApiException.badRequest("Không thể bày tỏ cảm xúc cho tin nhắn hệ thống.");
        }
        return conv;
    }

    // =========================================================================================== shared attachments

    public SharedAttachmentListResponse sharedAttachments(User cu, Long conversationId, String category, int limit,
                                                          String cursor) {
        ConversationQueryRepository.Access access = requireAccess(cu.getUserId(), conversationId,
                "Cuộc trò chuyện không tồn tại hoặc bạn không có quyền truy cập.");
        LocalDateTime joinedFilter = access.conversation().getType() == ConversationType.GROUP
                ? access.member().getJoinedAt() : null;
        List<MessageAttachmentKind> kinds = switch (category == null ? "" : category.toLowerCase()) {
            case "media" -> List.of(MessageAttachmentKind.IMAGE, MessageAttachmentKind.VIDEO);
            case "audio" -> List.of(MessageAttachmentKind.AUDIO);
            case "file" -> List.of(MessageAttachmentKind.PDF, MessageAttachmentKind.DOCUMENT);
            default -> throw ApiException.badRequest("INVALID_ATTACHMENT_CATEGORY");
        };
        LocalDateTime cursorTime = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isEmpty()) {
            try {
                Map<String, Object> data = ConversationSupport.decodeCursor(cursor);
                cursorTime = ConversationSupport.cursorTime(data, "created_at");
                cursorId = ConversationSupport.cursorLong(data, "attachment_id");
            } catch (ConversationSupport.InvalidCursorException e) {
                throw ApiException.badRequest(e.getMessage());
            }
        }
        List<MessageAttachment> rows = queries.sharedAttachments(conversationId, kinds, joinedFilter, cursorTime,
                cursorId, limit);
        boolean hasMore = rows.size() > limit;
        List<MessageAttachment> items = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            MessageAttachment last = items.get(items.size() - 1);
            nextCursor = ConversationSupport.encodeCursor("created_at", last.getCreatedAt().toString(),
                    "attachment_id", last.getAttachmentId());
        }
        Set<Long> senderIds = new HashSet<>();
        items.forEach(a -> senderIds.add(a.getMessage().getSenderId()));
        Map<Long, User> users = queries.usersById(senderIds);
        List<SharedAttachmentItemResponse> out = new ArrayList<>();
        for (MessageAttachment a : items) {
            User s = users.get(a.getMessage().getSenderId());
            out.add(new SharedAttachmentItemResponse(a.getAttachmentId(), a.getMessage().getMessageId(),
                    a.getKind().name(),
                    mediaStorage.deliveryUrl(a.getStorageKey(), ConversationViewBuilder.resourceType(a.getKind()), a.getUrl()),
                    a.getMimeType(), a.getOriginalName(), a.getSizeBytes(), a.getDurationMs(), a.getCreatedAt(),
                    a.getMessage().getSenderId(), s == null ? null : s.getFullName(), s == null ? null : s.getAvatarUrl()));
        }
        return new SharedAttachmentListResponse(out, nextCursor, hasMore);
    }

    public SharedAttachmentSummaryResponse sharedAttachmentsSummary(User cu, Long conversationId) {
        ConversationQueryRepository.Access access = requireAccess(cu.getUserId(), conversationId,
                "Cuộc trò chuyện không tồn tại hoặc bạn không có quyền truy cập.");
        LocalDateTime joinedFilter = access.conversation().getType() == ConversationType.GROUP
                ? access.member().getJoinedAt() : null;
        Map<MessageAttachmentKind, Long> counts = queries.attachmentKindCounts(conversationId, joinedFilter);
        long media = counts.getOrDefault(MessageAttachmentKind.IMAGE, 0L) + counts.getOrDefault(MessageAttachmentKind.VIDEO, 0L);
        long audio = counts.getOrDefault(MessageAttachmentKind.AUDIO, 0L);
        long file = counts.getOrDefault(MessageAttachmentKind.PDF, 0L) + counts.getOrDefault(MessageAttachmentKind.DOCUMENT, 0L);
        return new SharedAttachmentSummaryResponse(media, audio, file);
    }

    // =========================================================================================== presence

    /** Python: get_conversation_presence — presence không khả dụng → available=false, danh sách rỗng (không lỗi). */
    public PresenceBatchResponse presence(User cu, Long conversationId) {
        requireAccess(cu.getUserId(), conversationId, "Cuộc trò chuyện không tồn tại hoặc bạn không có quyền truy cập.");
        PresenceService.PresenceSnapshot snap = presenceService.getUsersPresence(queries.activeMemberIds(conversationId));
        List<Long> online = new ArrayList<>(snap.onlineUserIds());
        java.util.Collections.sort(online);
        return new PresenceBatchResponse(snap.available(), online);
    }

    // =========================================================================================== official support

    public OfficialSupportGroupResponse officialSupport(User cu) {
        if (officialSupportGroupId <= 0) {
            throw ConversationSupport.unavailable("Nhóm hỗ trợ chính thức chưa được cấu hình.");
        }
        Conversation conv = queries.findActiveConversation(officialSupportGroupId);
        if (conv == null) {
            throw ConversationSupport.unavailable("Nhóm hỗ trợ chính thức không tồn tại.");
        }
        if (conv.getType() != ConversationType.GROUP) {
            throw ConversationSupport.unavailable("Nhóm hỗ trợ chính thức có loại không hợp lệ.");
        }
        ConversationMember member = queries.findActiveMember(conv.getConversationId(), cu.getUserId());
        boolean isMember = member != null;

        String lastPreview = conv.getDescription();
        LocalDateTime lastDt = conv.getLastMessageAt();
        List<Message> last = em.createQuery("select m from Message m where m.conversation.conversationId = :cid "
                        + "and m.isDeleted = false order by m.sentAt desc, m.messageId desc", Message.class)
                .setParameter("cid", conv.getConversationId()).setMaxResults(1).getResultList();
        if (!last.isEmpty()) {
            lastPreview = last.get(0).getContent();
            lastDt = last.get(0).getSentAt();
        }
        long unread = 0;
        if (isMember) {
            if (member.getLastReadMessageId() != null) {
                unread = em.createQuery("select count(m) from Message m where m.conversation.conversationId = :cid "
                                + "and m.messageId > :lr and m.senderId <> :uid and m.isDeleted = false", Long.class)
                        .setParameter("cid", conv.getConversationId()).setParameter("lr", member.getLastReadMessageId())
                        .setParameter("uid", cu.getUserId()).getSingleResult();
            } else if (conv.getLastMessageAt() != null) {
                unread = em.createQuery("select count(m) from Message m where m.conversation.conversationId = :cid "
                                + "and m.senderId <> :uid and m.isDeleted = false", Long.class)
                        .setParameter("cid", conv.getConversationId()).setParameter("uid", cu.getUserId())
                        .getSingleResult();
            }
        }
        return new OfficialSupportGroupResponse(conv.getConversationId(),
                conv.getTitle() != null ? conv.getTitle() : "Cộng đồng Chợ PTITers", conv.getDescription(), isMember,
                lastPreview, ConversationSupport.isoZ(lastDt), unread);
    }

    @Transactional
    public OfficialSupportGroupJoinResponse joinOfficialSupport(User cu) {
        if (officialSupportGroupId <= 0) {
            throw ConversationSupport.unavailable("Nhóm hỗ trợ chính thức chưa được cấu hình.");
        }
        Conversation conv = queries.findActiveConversation(officialSupportGroupId);
        if (conv == null) {
            throw ApiException.notFound("Nhóm hỗ trợ chính thức không tồn tại.");
        }
        if (conv.getType() != ConversationType.GROUP) {
            throw ApiException.badRequest("Nhóm hỗ trợ chính thức có loại không hợp lệ.");
        }
        Long cid = conv.getConversationId();
        ConversationMember existing = em.find(ConversationMember.class, new ConversationMemberId(cid, cu.getUserId()));
        // Python join_support_group: đang hoạt động → giữ nguyên; đã rời → kích hoạt lại; chưa có → tạo mới.
        if (existing == null || existing.getLeftAt() != null) {
            queries.upsertActiveMember(cid, cu.getUserId(), ConversationMemberRole.MEMBER,
                    queries.maxLiveMessageId(cid), ConversationSupport.nowUtc());
        }
        return new OfficialSupportGroupJoinResponse(cid);
    }

    // =========================================================================================== helpers

    private ConversationQueryRepository.Access requireAccess(Long userId, Long conversationId, String notFoundMessage) {
        ConversationQueryRepository.Access access = queries.findAccess(conversationId, userId);
        if (access == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
        return access;
    }
}
