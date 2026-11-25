package com.ptiters.marketplace.conversation;

import com.ptiters.marketplace.conversation.ConversationDtos.AttachmentDownloadTicketResponse;
import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.report.ReportEvidenceStreamer;
import com.ptiters.marketplace.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tải tệp đính kèm chat: ticket dùng 1 lần (Redis, 60 giây) + stream qua storage an toàn SSRF.
 * Tương ứng {@code create_download_ticket} / {@code consume_download_ticket} /
 * {@code authorize_and_get_attachment_metadata} và {@code download_attachment} của Python.
 * Khoá và định dạng giá trị Redis giữ nguyên Python ({@code download_ticket:<sha256>}, JSON user_id/conversation_id/attachment_id).
 */
@Service
@Transactional(readOnly = true)
public class AttachmentDownloadService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentDownloadService.class);
    static final long TICKET_TTL_SECONDS = 60;
    private static final String KEY_PREFIX = "download_ticket:";
    private static final String NOT_FOUND_CONV = "Cuộc trò chuyện không tồn tại hoặc bạn không có quyền truy cập.";
    private static final String UNAVAILABLE = "Dịch vụ tải xuống tạm thời không khả dụng. Vui lòng thử lại sau.";
    private static final Pattern JSON_NUM = Pattern.compile("\"(user_id|conversation_id|attachment_id)\"\\s*:\\s*(\\d+)");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Metadata đã xác thực quyền, đủ để mở luồng tải. */
    public record Target(String url, String originalName, String mimeType) {}

    private final ConversationQueryRepository queries;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ReportEvidenceStreamer streamer;

    @PersistenceContext
    private EntityManager em;

    public AttachmentDownloadService(ConversationQueryRepository queries,
                                     ObjectProvider<StringRedisTemplate> redisProvider,
                                     ReportEvidenceStreamer streamer) {
        this.queries = queries;
        this.redisProvider = redisProvider;
        this.streamer = streamer;
    }

    public AttachmentDownloadTicketResponse createTicket(User cu, Long conversationId, Long attachmentId) {
        authorize(cu.getUserId(), conversationId, attachmentId);
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String payload = "{\"user_id\": " + cu.getUserId() + ", \"conversation_id\": " + conversationId
                + ", \"attachment_id\": " + attachmentId + "}";
        try {
            redis.opsForValue().set(KEY_PREFIX + sha256(ticket), payload, Duration.ofSeconds(TICKET_TTL_SECONDS));
        } catch (RuntimeException e) {
            log.error("Failed to store download ticket in Redis: {}", e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
        return new AttachmentDownloadTicketResponse(ticket,
                "/api/v1/conversations/" + conversationId + "/attachments/" + attachmentId + "/download?ticket=" + ticket,
                (int) TICKET_TTL_SECONDS);
    }

    /** Tiêu thụ nguyên tử (GETDEL) ticket; trả về user_id gắn với ticket. */
    public Long consumeTicket(Long conversationId, Long attachmentId, String ticket) {
        if (ticket == null || ticket.isEmpty()) {
            throw ApiException.unauthorized("Mã tải xuống không hợp lệ.");
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
        String raw;
        try {
            raw = redis.opsForValue().getAndDelete(KEY_PREFIX + sha256(ticket));
        } catch (RuntimeException e) {
            log.error("Failed to read/delete ticket from Redis: {}", e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
        if (raw == null || raw.isEmpty()) {
            throw ApiException.unauthorized("Mã tải xuống không hợp lệ, đã hết hạn hoặc đã được sử dụng.");
        }
        Long userId = null;
        Long convId = null;
        Long attId = null;
        Matcher m = JSON_NUM.matcher(raw);
        while (m.find()) {
            long v = Long.parseLong(m.group(2));
            switch (m.group(1)) {
                case "user_id" -> userId = v;
                case "conversation_id" -> convId = v;
                default -> attId = v;
            }
        }
        if (convId == null && attId == null && userId == null) {
            throw ApiException.unauthorized("Mã tải xuống không hợp lệ.");
        }
        if (!conversationId.equals(convId) || !attachmentId.equals(attId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Mã tải xuống không khớp với tệp đính kèm này.");
        }
        if (userId == null || userId == 0) {
            throw ApiException.unauthorized("Mã tải xuống không hợp lệ.");
        }
        return userId;
    }

    /** Kiểm tra lại quyền thành viên HIỆN TẠI + thuộc hội thoại + riêng tư joined_at của nhóm + an toàn SSRF. */
    public Target authorize(Long userId, Long conversationId, Long attachmentId) {
        ConversationQueryRepository.Access access = queries.findAccess(conversationId, userId);
        if (access == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND_CONV);
        }
        List<MessageAttachment> found = em.createQuery("select a from MessageAttachment a join a.message m "
                        + "where a.attachmentId = :aid and m.conversation.conversationId = :cid", MessageAttachment.class)
                .setParameter("aid", attachmentId).setParameter("cid", conversationId).getResultList();
        if (found.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Tệp đính kèm không tồn tại.");
        }
        MessageAttachment att = found.get(0);
        if (access.conversation().getType() == ConversationType.GROUP && access.member() != null
                && att.getCreatedAt().isBefore(access.member().getJoinedAt())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Tệp đính kèm không tồn tại hoặc bạn không có quyền truy cập.");
        }
        ReportEvidenceStreamer.validateStorageUrlSafety(att.getUrl());
        return new Target(att.getUrl(), att.getOriginalName(), att.getMimeType());
    }

    /** Mở luồng tải upstream (không theo redirect, chỉ host Cloudinary tin cậy). */
    public ReportEvidenceStreamer.Opened open(Target target) {
        return streamer.open(target.url(), false, null);
    }

    // ----------------------------------------------------------------------------- Content-Disposition (RFC 5987/6266)

    /** Python: {@code format_content_disposition}. */
    public static String formatContentDisposition(String originalName, String mimeType) {
        StringBuilder sb = new StringBuilder();
        for (char ch : (originalName == null ? "" : originalName).toCharArray()) {
            if (ch >= 32 && ch != 127) {
                sb.append(ch);
            }
        }
        String sanitized = sb.toString().replace('\\', '/');
        String basename = strip(sanitized.substring(sanitized.lastIndexOf('/') + 1), ". ");
        if (basename.isEmpty()) {
            basename = "attachment";
        }
        int dot = basename.lastIndexOf('.');
        String namePart = dot > 0 ? basename.substring(0, dot) : basename;
        String ext = dot > 0 ? basename.substring(dot) : "";
        if (ext.isEmpty() && mimeType != null) {
            String guessed = guessExtension(mimeType);
            if (guessed != null) {
                basename = namePart + guessed;
                ext = guessed;
            }
        }
        StringBuilder ascii = new StringBuilder();
        for (char c : basename.toCharArray()) {
            boolean ok = (c < 128 && Character.isLetterOrDigit(c)) || c == ' ' || c == '.' || c == '_' || c == '-';
            ascii.append(ok ? c : '_');
        }
        String asciiClean = strip(ascii.toString().replaceAll("_+", "_"), " _");
        String fallback;
        if (asciiClean.isEmpty() || asciiClean.equals(ext.startsWith(".") ? ext.substring(1) : ext)) {
            fallback = "download" + ext;
        } else {
            fallback = asciiClean;
            if (!ext.isEmpty() && !fallback.toLowerCase().endsWith(ext.toLowerCase())) {
                fallback = fallback + ext;
            }
        }
        String encoded = java.net.URLEncoder.encode(basename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

    private static String strip(String s, String chars) {
        int a = 0;
        int b = s.length();
        while (a < b && chars.indexOf(s.charAt(a)) >= 0) {
            a++;
        }
        while (b > a && chars.indexOf(s.charAt(b - 1)) >= 0) {
            b--;
        }
        return s.substring(a, b);
    }

    private static String guessExtension(String mime) {
        return switch (mime.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "video/mp4" -> ".mp4";
            case "application/pdf" -> ".pdf";
            case "audio/mpeg" -> ".mp3";
            default -> null;
        };
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
