package com.ptiters.marketplace.conversation;

import com.ptiters.marketplace.conversation.ConversationDtos.MessageAttachmentResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.MessageMentionResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.MessageResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.ParticipantSummary;
import com.ptiters.marketplace.conversation.ConversationDtos.ProductContextSummary;
import com.ptiters.marketplace.conversation.ConversationDtos.ReactionAggregateResponse;
import com.ptiters.marketplace.conversation.ConversationDtos.ReplyMessageSummary;
import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.product.ProductImage;
import com.ptiters.marketplace.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lắp ráp DTO phản hồi từ entity (batch-loading để tránh N+1: attachments, mentions, reactions, người gửi,
 * sản phẩm, tin được trả lời đều lấy theo lô). Đối ứng các hàm dựng response trong {@code repository.py}.
 */
@Component
public class ConversationViewBuilder {

    private final ConversationQueryRepository queries;
    private final MediaStorage mediaStorage;

    @PersistenceContext
    private EntityManager em;

    public ConversationViewBuilder(ConversationQueryRepository queries, MediaStorage mediaStorage) {
        this.queries = queries;
        this.mediaStorage = mediaStorage;
    }

    // ------------------------------------------------------------------------------------ resource type

    /** Python: _map_kind_to_resource_type. */
    static String resourceType(MessageAttachmentKind kind) {
        return switch (kind) {
            case IMAGE -> "image";
            case VIDEO, AUDIO -> "video";
            default -> "raw";
        };
    }

    // ------------------------------------------------------------------------------------ small views

    public ParticipantSummary participant(User u, String fallbackName) {
        return new ParticipantSummary(u == null ? null : u.getUserId(), u == null ? null : u.getPublicId(),
                u == null ? fallbackName : u.getFullName(), u == null ? null : u.getAvatarUrl());
    }

    public ProductContextSummary productContext(Product p) {
        if (p == null) {
            return null;
        }
        String thumb = null;
        List<ProductImage> images = p.getImages();
        if (images != null) {
            for (ProductImage img : images) {
                if (Boolean.TRUE.equals(img.getIsThumbnail())) {
                    thumb = img.getImageUrl();
                    break;
                }
            }
            if (thumb == null && !images.isEmpty()) {
                thumb = images.get(0).getImageUrl();
            }
        }
        return new ProductContextSummary(p.getProductId(), p.getTitle(), p.getPrice(), p.getStatus().name(), thumb,
                p.getListingType().name());
    }

    /** Python: _format_message_preview. */
    public String preview(Message msg, List<MessageAttachment> attachments) {
        if (msg.getContent() != null && !msg.getContent().isEmpty()) {
            return msg.getContent();
        }
        if (attachments != null && !attachments.isEmpty()) {
            return switch (attachments.get(0).getKind()) {
                case IMAGE -> "[Hình ảnh]";
                case VIDEO -> "[Video]";
                case AUDIO -> "[Tin nhắn thoại]";
                case PDF -> "[Tệp PDF]";
                case DOCUMENT -> "[Tài liệu]";
            };
        }
        if (msg.getProductId() != null) {
            return "[Sản phẩm]";
        }
        return null;
    }

    /** Python: build_reply_summary — một cấp, preview an toàn. */
    public ReplyMessageSummary replySummary(Message target, User sender) {
        if (target == null) {
            return null;
        }
        String preview;
        if (Boolean.TRUE.equals(target.getIsDeleted())) {
            preview = "Tin nhắn không còn khả dụng";
        } else {
            MessageType type = target.getType() == null ? MessageType.TEXT : target.getType();
            preview = switch (type) {
                case IMAGE -> "Hình ảnh";
                case FILE -> "Tệp đính kèm";
                case PRODUCT_CARD -> "Sản phẩm";
                case ORDER_CARD -> "Đơn hàng";
                default -> {
                    String raw = target.getContent() == null ? "" : target.getContent().strip();
                    String normalized = raw.replaceAll("\\s+", " ");
                    if (normalized.length() > 120) {
                        normalized = normalized.substring(0, 120).stripTrailing() + "...";
                    }
                    yield normalized.isEmpty() ? "Tin nhắn" : normalized;
                }
            };
        }
        return new ReplyMessageSummary(target.getMessageId(), target.getSenderId(),
                sender == null ? null : sender.getFullName(),
                (target.getType() == null ? MessageType.TEXT : target.getType()).name(), preview);
    }

    public MessageAttachmentResponse attachmentView(MessageAttachment a) {
        return new MessageAttachmentResponse(a.getAttachmentId(), a.getKind().name(),
                mediaStorage.deliveryUrl(a.getStorageKey(), resourceType(a.getKind()), a.getUrl()),
                a.getMimeType(), a.getOriginalName(), a.getSizeBytes(), a.getDurationMs());
    }

    // ------------------------------------------------------------------------------------ message page

    /**
     * Python: phần dựng MessageResponse của get_messages — tin đã thu hồi trả "tombstone"
     * (content/attachments/mentions/reactions rỗng, không có sender_public_id).
     */
    public List<MessageResponse> messages(List<Message> msgs, Long viewerId) {
        if (msgs.isEmpty()) {
            return List.of();
        }
        List<Long> ids = msgs.stream().map(Message::getMessageId).toList();

        Set<Long> userIds = new HashSet<>();
        Set<Long> replyIds = new HashSet<>();
        Set<Long> productIds = new HashSet<>();
        for (Message m : msgs) {
            userIds.add(m.getSenderId());
            if (m.getReplyToMessageId() != null) {
                replyIds.add(m.getReplyToMessageId());
            }
            if (m.getProductId() != null) {
                productIds.add(m.getProductId());
            }
        }
        Map<Long, Message> replies = new HashMap<>();
        if (!replyIds.isEmpty()) {
            for (Message r : em.createQuery("select m from Message m where m.messageId in :ids", Message.class)
                    .setParameter("ids", replyIds).getResultList()) {
                replies.put(r.getMessageId(), r);
                userIds.add(r.getSenderId());
            }
        }
        Map<Long, User> users = queries.usersById(userIds);
        Map<Long, List<MessageAttachment>> attachments = queries.attachmentsByMessage(ids);
        Map<Long, List<MessageMention>> mentions = queries.mentionsByMessage(ids);
        for (List<MessageMention> l : mentions.values()) {
            for (MessageMention mm : l) {
                if (!users.containsKey(mm.getMentionedUserId())) {
                    users.putAll(queries.usersById(List.of(mm.getMentionedUserId())));
                }
            }
        }
        Map<Long, List<ReactionAggregateResponse>> reactions = queries.reactionAggregates(ids);
        Map<Long, ReactionKey> mine = queries.myReactions(ids, viewerId);
        Map<Long, Product> products = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Product p : em.createQuery("select p from Product p where p.productId in :ids", Product.class)
                    .setParameter("ids", productIds).getResultList()) {
                products.put(p.getProductId(), p);
            }
        }

        List<MessageResponse> out = new ArrayList<>(msgs.size());
        for (Message m : msgs) {
            User sender = users.get(m.getSenderId());
            boolean isMine = viewerId != null && viewerId.equals(m.getSenderId());
            ReplyMessageSummary reply = null;
            if (m.getReplyToMessageId() != null) {
                Message target = replies.get(m.getReplyToMessageId());
                reply = replySummary(target, target == null ? null : users.get(target.getSenderId()));
            }
            if (Boolean.TRUE.equals(m.getIsDeleted())) {
                out.add(new MessageResponse(m.getMessageId(), m.getConversation().getConversationId(),
                        m.getSenderId(), null, sender == null ? null : sender.getFullName(),
                        sender == null ? null : sender.getAvatarUrl(), m.getType().name(), null, null, null,
                        ConversationSupport.isoZ(m.getSentAt()), isMine, true, List.of(), List.of(), List.of(), null,
                        reply));
                continue;
            }
            List<MessageAttachmentResponse> atts = attachments.getOrDefault(m.getMessageId(), List.of()).stream()
                    .map(this::attachmentView).toList();
            List<MessageMentionResponse> mens = mentions.getOrDefault(m.getMessageId(), List.of()).stream()
                    .map(mm -> mentionView(mm, users.get(mm.getMentionedUserId()))).toList();
            out.add(new MessageResponse(m.getMessageId(), m.getConversation().getConversationId(), m.getSenderId(),
                    sender == null ? null : sender.getPublicId(), sender == null ? null : sender.getFullName(),
                    sender == null ? null : sender.getAvatarUrl(), m.getType().name(), m.getContent(),
                    m.getProductId(), productContext(products.get(m.getProductId())),
                    ConversationSupport.isoZ(m.getSentAt()), isMine, false, atts, mens,
                    reactions.getOrDefault(m.getMessageId(), List.of()), mine.get(m.getMessageId()), reply));
        }
        return out;
    }

    public MessageMentionResponse mentionView(MessageMention mm, User mentioned) {
        String name = mentioned != null ? mentioned.getFullName()
                : (mm.getDisplayText().startsWith("@") ? mm.getDisplayText().replaceFirst("^@+", "") : mm.getDisplayText());
        return new MessageMentionResponse(mm.getMentionedUserId(), name, mm.getDisplayText(), mm.getStartOffset(),
                mm.getEndOffset());
    }

    /** Tập user id đã chuẩn hoá (loại null) — tiện cho caller. */
    static Set<Long> idSet(Collection<Long> ids) {
        Set<Long> s = new HashSet<>();
        for (Long id : ids) {
            if (id != null) {
                s.add(id);
            }
        }
        return s;
    }
}
