package com.ptiters.marketplace.conversation;

import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.trackc.TrackCFixtures;
import com.ptiters.marketplace.trackc.TrackCTestConfig;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** download-ticket (Redis thật, 60s, dùng 1 lần) + download: kiểm quyền, SSRF, định dạng Content-Disposition. */
@SpringBootTest(properties = {"REDIS_HOST=localhost", "REDIS_PORT=6379"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TrackCTestConfig.class)
class AttachmentDownloadIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TrackCFixtures fx;
    @Autowired JdbcTemplate jdbc;
    @Autowired AttachmentDownloadService downloadService;
    @Autowired StringRedisTemplate redis;

    User alice;
    User bob;
    User eve;
    Long convId;
    Long goodAtt;
    Long badHostAtt;

    @BeforeEach
    void setUp() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 6379), 500);
        } catch (Exception e) {
            Assumptions.abort("Redis local không chạy");
        }
        cleanup();
        alice = fx.user("alice", UserRole.STUDENT);
        bob = fx.user("bob", UserRole.STUDENT);
        eve = fx.user("eve", UserRole.STUDENT);
        convId = jdbc.queryForObject("INSERT INTO conversations (type, user_one_id, user_two_id, direct_pair_key, "
                + "requires_approval, created_at) VALUES ('DIRECT', ?, ?, ?, false, now()) RETURNING conversation_id",
                Long.class, alice.getUserId(), bob.getUserId(), alice.getUserId() + ":" + bob.getUserId());
        jdbc.update("INSERT INTO conversation_members (conversation_id, user_id, role, joined_at) VALUES (?,?,'MEMBER',now()),(?,?,'MEMBER',now())",
                convId, alice.getUserId(), convId, bob.getUserId());
        Long msg = jdbc.queryForObject("INSERT INTO messages (conversation_id, sender_id, type, is_deleted, sent_at) "
                + "VALUES (?, ?, 'TEXT', false, now()) RETURNING message_id", Long.class, convId, alice.getUserId());
        goodAtt = attachment(msg, "https://res.cloudinary.com/demo/raw/upload/v1/chat/tai-lieu.pdf", "báo cáo tháng 5.pdf");
        badHostAtt = attachment(msg, "https://evil.example.com/x.pdf", "x.pdf");
    }

    private Long attachment(Long msg, String url, String name) {
        return jdbc.queryForObject("INSERT INTO message_attachments (message_id, kind, url, storage_key, mime_type, "
                + "original_name, size_bytes, created_at) VALUES (?, 'PDF', ?, 'k', 'application/pdf', ?, 10, now()) "
                + "RETURNING attachment_id", Long.class, msg, url, name);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM conversations");
        fx.cleanAll();
    }

    private static String key(String ticket) throws Exception {
        return "download_ticket:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(ticket.getBytes(StandardCharsets.UTF_8)));
    }

    private String bearer(User u) {
        return "Bearer " + fx.token(u);
    }

    @Test
    @DisplayName("Ticket: lưu Redis TTL<=60s, dùng đúng 1 lần, sai tệp → 403, ticket lạ → 401")
    void ticketLifecycle() throws Exception {
        String body = mockMvc.perform(post("/api/v1/conversations/" + convId + "/attachments/" + goodAtt + "/download-ticket")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expires_in_seconds", is(60)))
                .andExpect(jsonPath("$.download_url", startsWith("/api/v1/conversations/" + convId + "/attachments/" + goodAtt
                        + "/download?ticket=")))
                .andReturn().getResponse().getContentAsString();
        String ticket = body.replaceAll(".*\"ticket\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        Long ttl = redis.getExpire(key(ticket), TimeUnit.SECONDS);
        assertTrue(ttl != null && ttl > 0 && ttl <= 60, "TTL phải trong (0,60] nhưng là " + ttl);

        // sai attachment → 403 và ticket đã bị tiêu thụ
        ApiException ex = assertThrows(ApiException.class, () -> downloadService.consumeTicket(convId, badHostAtt, ticket));
        assertEquals(403, ex.getStatus().value());
        assertThrows(ApiException.class, () -> downloadService.consumeTicket(convId, goodAtt, ticket));

        // lấy ticket mới: tiêu thụ đúng → user_id; lần 2 → 401
        String t2 = downloadService.createTicket(alice, convId, goodAtt).ticket();
        assertEquals(alice.getUserId(), downloadService.consumeTicket(convId, goodAtt, t2));
        ApiException again = assertThrows(ApiException.class, () -> downloadService.consumeTicket(convId, goodAtt, t2));
        assertEquals(401, again.getStatus().value());
        assertEquals(401, assertThrows(ApiException.class,
                () -> downloadService.consumeTicket(convId, goodAtt, "khong-ton-tai")).getStatus().value());
    }

    @Test
    @DisplayName("Quyền: người ngoài hội thoại → 404; tệp host lạ → 502 (SSRF); tệp không thuộc hội thoại → 404")
    void authorization() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/" + convId + "/attachments/" + goodAtt + "/download-ticket")
                        .header(HttpHeaders.AUTHORIZATION, bearer(eve)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/conversations/" + convId + "/attachments/" + badHostAtt + "/download-ticket")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isBadGateway());
        mockMvc.perform(post("/api/v1/conversations/" + convId + "/attachments/999999999/download-ticket")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/conversations/" + convId + "/attachments/" + goodAtt + "/download-ticket"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET download: thiếu cả ticket lẫn Bearer → 401; ticket lạ → 401; ticket của thành viên đã rời → 404")
    void downloadAuth() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/" + convId + "/attachments/" + goodAtt + "/download"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/conversations/" + convId + "/attachments/" + goodAtt + "/download").param("ticket", "xxx"))
                .andExpect(status().isUnauthorized());
        // quyền được kiểm lại LÚC TẢI: bob rời hội thoại sau khi có ticket → 404
        String t = downloadService.createTicket(bob, convId, goodAtt).ticket();
        jdbc.update("UPDATE conversation_members SET left_at = now() WHERE conversation_id = ? AND user_id = ?", convId, bob.getUserId());
        mockMvc.perform(get("/api/v1/conversations/" + convId + "/attachments/" + goodAtt + "/download").param("ticket", t))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Content-Disposition: bỏ ký tự điều khiển/đường dẫn, fallback ASCII + filename* UTF-8")
    void contentDisposition() {
        assertEquals("attachment; filename=\"b_o c_o th_ng 5.pdf\"; filename*=UTF-8''b%C3%A1o%20c%C3%A1o%20th%C3%A1ng%205.pdf",
                AttachmentDownloadService.formatContentDisposition("báo cáo tháng 5.pdf", "application/pdf"));
        String traversal = AttachmentDownloadService.formatContentDisposition("../../etc/passwd\r\n", null);
        assertTrue(traversal.startsWith("attachment; filename=\"passwd\""), traversal);
        assertTrue(AttachmentDownloadService.formatContentDisposition("", "image/png").contains("attachment.png"));
    }
}
