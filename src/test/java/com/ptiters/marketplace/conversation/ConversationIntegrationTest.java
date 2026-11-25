package com.ptiters.marketplace.conversation;

import com.jayway.jsonpath.JsonPath;
import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.category.CategoryRepository;
import com.ptiters.marketplace.integration.LocalMediaStorage;
import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.notification.PushNotificationSender;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.product.ProductApprovalStatus;
import com.ptiters.marketplace.product.ProductRepository;
import com.ptiters.marketplace.product.ProductStatus;
import com.ptiters.marketplace.product.ProductTestFixtures;
import com.ptiters.marketplace.realtime.UserEventPublisher;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserAuthIdentityRepository;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserRole;
import com.ptiters.marketplace.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tích hợp module chat trên PostgreSQL THẬT (marketplace_java_test_<hậu tố>), commit thật, dọn sạch sau test.
 * Không mock repository/DB: unique/FK/check/lock (direct_pair_key, member PK, mention/reaction unique, partial unique
 * index của invite) đều được kiểm chứng bằng đua nhiều thread hoặc ghi thật.
 */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=6",
        "spring.servlet.multipart.max-file-size=60MB",
        "spring.servlet.multipart.max-request-size=100MB"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationIntegrationTest {

    record Event(Long userId, Map<String, Object> data) {
    }

    static final List<Event> EVENTS = new CopyOnWriteArrayList<>();
    static final List<String> DESTROYED = new CopyOnWriteArrayList<>();
    static final List<String> PUSHED = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return ProductTestFixtures.createTestJwtDecoder();
        }

        @Bean
        @Primary
        UserEventPublisher recordingPublisher() {
            return new UserEventPublisher() {
                @Override
                public void publishUserEvent(Long userId, Map<String, Object> eventData) {
                    EVENTS.add(new Event(userId, eventData));
                }

                @Override
                public void revokeUserSessions(Long userId) {
                }
            };
        }

        @Bean
        @Primary
        PushNotificationSender recordingPush() {
            return (userId, title, body, data) -> PUSHED.add(userId + "|" + title);
        }

        @Bean
        @Primary
        MediaStorage recordingMediaStorage() {
            return new LocalMediaStorage(LocalMediaStorage.inTempDirectory().getBaseDir()) {
                @Override
                public boolean destroyAsset(String publicId, String resourceType) {
                    DESTROYED.add(publicId);
                    return super.destroyAsset(publicId, resourceType);
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserAuthIdentityRepository identityRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private ConversationService conversationService;

    private User alice;
    private User bob;
    private User carol;
    private User dave;

    // ------------------------------------------------------------------------------------ setup

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT current_database()")) {
            rs.next();
            assertTrue(rs.getString(1).matches("marketplace_java_test(_[a-z0-9]+)?"),
                    "SAFETY: chỉ được chạy trên marketplace_java_test*");
        }
        cleanDatabase();
        EVENTS.clear();
        DESTROYED.clear();
        PUSHED.clear();
        alice = newUser("alice", "Alice Tester");
        bob = newUser("bob", "Bob Tester");
        carol = newUser("carol", "Carol Tester");
        dave = newUser("dave", "Dave Outsider");
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        for (String t : List.of("messages", "conversations", "conversation_members", "conversation_invites",
                "message_attachments", "message_mentions", "message_reactions", "notifications",
                "media_cleanup_jobs", "orders", "products", "users")) {
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + t, Integer.class), "DB phải sạch: " + t);
        }
    }

    private void cleanDatabase() {
        // conversations CASCADE → members/invites/messages → attachments/mentions/reactions
        jdbc.update("DELETE FROM conversations");
        jdbc.update("DELETE FROM media_cleanup_jobs");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM product_images");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        jdbc.update("DELETE FROM user_capability_restrictions");
        jdbc.update("DELETE FROM user_auth_identities");
        jdbc.update("DELETE FROM users");
    }

    private User newUser(String key, String name) {
        User u = ProductTestFixtures.createUser(userRepository, key + "_chat@test.com", name, UserRole.STUDENT);
        ProductTestFixtures.createAuthIdentity(identityRepository, u, key + "_chat_uid");
        return u;
    }

    private String token(User u) {
        return ProductTestFixtures.createToken(u.getEmail().replace("_chat@test.com", "") + "_chat_uid", u.getEmail());
    }

    private <T extends org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<T>> T auth(T b, User u) {
        return b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(u));
    }

    private ResultActions postJson(User u, String url, String json) throws Exception {
        return mockMvc.perform(auth(post(url), u).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putJson(User u, String url, String json) throws Exception {
        return mockMvc.perform(auth(put(url), u).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions patchJson(User u, String url, String json) throws Exception {
        return mockMvc.perform(auth(patch(url), u).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions getAs(User u, String url) throws Exception {
        return mockMvc.perform(auth(get(url), u));
    }

    private ResultActions deleteAs(User u, String url) throws Exception {
        return mockMvc.perform(auth(delete(url), u));
    }

    private static long idOf(ResultActions r, String path) throws Exception {
        String body = r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private static String strOf(ResultActions r, String path) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    private long openDirect(User from, User to) throws Exception {
        ResultActions r = postJson(from, "/api/v1/conversations/direct",
                "{\"recipient_public_id\":\"" + to.getPublicId() + "\"}").andExpect(status().isOk());
        return idOf(r, "$.conversation_id");
    }

    private long createGroup(User owner, String title) throws Exception {
        ResultActions r = postJson(owner, "/api/v1/conversations/groups", "{\"title\":\"" + title + "\"}")
                .andExpect(status().isCreated());
        return idOf(r, "$.conversation_id");
    }

    private long sendText(User u, long convId, String text) throws Exception {
        ResultActions r = postJson(u, "/api/v1/conversations/" + convId + "/messages",
                "{\"content\":\"" + text + "\"}").andExpect(status().isCreated());
        return idOf(r, "$.message_id");
    }

    /** Mời + chấp nhận để user trở thành thành viên (nhóm không bật phê duyệt, hoặc người mời là OWNER). */
    private void join(User owner, long groupId, User member) throws Exception {
        ResultActions inv = postJson(owner, "/api/v1/conversations/groups/" + groupId + "/invites",
                "{\"invitee_ids\":[" + member.getUserId() + "]}").andExpect(status().isOk());
        long inviteId = idOf(inv, "$[0].invite_id");
        postJson(member, "/api/v1/conversations/invites/" + inviteId + "/respond", "{\"action\":\"ACCEPT\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("ACCEPTED")));
    }

    private static byte[] pngBytes() {
        byte[] b = new byte[64];
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
        System.arraycopy(sig, 0, b, 0, sig.length);
        return b;
    }

    private ResultActions sendMultipart(User u, long convId, String content, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/v1/conversations/" + convId + "/messages");
        for (MockMultipartFile f : files) {
            b.file(f);
        }
        if (content != null) {
            b.param("content", content);
        }
        return mockMvc.perform(auth(b, u));
    }

    // ------------------------------------------------------------------------------------ direct

    @Test
    @DisplayName("Direct: idempotent (cùng conversation_id cả hai chiều), tự chat 400, không tồn tại 404, INACTIVE 400, 422 khi sai body")
    void directIdempotentAndValidation() throws Exception {
        long id1 = openDirect(alice, bob);
        long id2 = openDirect(alice, bob);
        long id3 = openDirect(bob, alice);
        assertEquals(id1, id2);
        assertEquals(id1, id3);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM conversations WHERE type='DIRECT'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM conversation_members WHERE conversation_id=?",
                Integer.class, id1));
        long lo = Math.min(alice.getUserId(), bob.getUserId());
        long hi = Math.max(alice.getUserId(), bob.getUserId());
        assertEquals(lo + ":" + hi, jdbc.queryForObject("SELECT direct_pair_key FROM conversations WHERE conversation_id=?",
                String.class, id1));

        postJson(alice, "/api/v1/conversations/direct", "{\"recipient_public_id\":\"" + alice.getPublicId() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Không thể tự nhắn tin cho chính mình.")));
        postJson(alice, "/api/v1/conversations/direct",
                "{\"recipient_public_id\":\"" + java.util.UUID.randomUUID() + "\"}")
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE users SET status = 'INACTIVE' WHERE user_id = ?", carol.getUserId());
        postJson(alice, "/api/v1/conversations/direct", "{\"recipient_public_id\":\"" + carol.getPublicId() + "\"}")
                .andExpect(status().isBadRequest());
        // extra="forbid": không nhận recipient_id số nguyên
        postJson(alice, "/api/v1/conversations/direct", "{\"recipient_id\":" + bob.getUserId() + "}")
                .andExpect(status().isUnprocessableEntity());
        postJson(alice, "/api/v1/conversations/direct",
                "{\"recipient_public_id\":\"" + bob.getPublicId() + "\",\"recipient_user_id\":1}")
                .andExpect(status().isUnprocessableEntity());

        // chi tiết: DIRECT lấy title là tên người kia, người ngoài 404
        getAs(alice, "/api/v1/conversations/" + id1).andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("DIRECT")))
                .andExpect(jsonPath("$.title", is("Bob Tester")))
                .andExpect(jsonPath("$.other_participant.user_id", is(bob.getUserId().intValue())))
                .andExpect(jsonPath("$.other_participant.public_id", is(bob.getPublicId().toString())));
    }

    @Test
    @DisplayName("Direct: 12 thread đua tạo cùng cặp user → đúng 1 conversation + 2 member (UNIQUE direct_pair_key thật)")
    void directRaceConvergesToSingleConversation() throws Exception {
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final boolean flip = i % 2 == 0;
            futures.add(pool.submit((Callable<Long>) () -> {
                start.await();
                User from = flip ? alice : bob;
                User to = flip ? bob : alice;
                return conversationService.createOrOpenDirect(from, to.getPublicId()).conversationId();
            }));
        }
        start.countDown();
        List<Long> ids = new ArrayList<>();
        for (Future<Long> f : futures) {
            ids.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertEquals(1, ids.stream().distinct().count(), "mọi thread phải nhận cùng conversation_id");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM conversations", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM conversation_members", Integer.class));
    }

    @Test
    @DisplayName("Mở hội thoại từ sản phẩm và từ đơn hàng; chặn tự chat, sản phẩm xoá/ẩn, người ngoài đơn hàng")
    void fromProductAndFromOrder() throws Exception {
        Category cat = ProductTestFixtures.createCategory(categoryRepository, "Chat cat");
        Product product = ProductTestFixtures.createProduct(productRepository, bob, cat, "Giáo trình CTDL",
                new BigDecimal("45000"), ProductStatus.AVAILABLE, ProductApprovalStatus.APPROVED);

        ResultActions r = postJson(alice, "/api/v1/conversations", "{\"product_id\":" + product.getProductId() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product_context.title", is("Giáo trình CTDL")))
                .andExpect(jsonPath("$.product_context.price", is(45000)))
                .andExpect(jsonPath("$.product_context.listing_type", is("FIXED_PRICE")))
                .andExpect(jsonPath("$.other_participant.user_id", is(bob.getUserId().intValue())));
        long convId = idOf(r, "$.conversation_id");
        assertEquals(convId, openDirect(bob, alice), "from-product và direct dùng chung thread canonical");

        postJson(bob, "/api/v1/conversations", "{\"product_id\":" + product.getProductId() + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Cannot start conversation with yourself")));
        postJson(alice, "/api/v1/conversations", "{\"product_id\":999999}").andExpect(status().isNotFound());
        postJson(alice, "/api/v1/conversations", "{\"product_id\":0}").andExpect(status().isUnprocessableEntity());
        jdbc.update("UPDATE products SET status='HIDDEN' WHERE product_id=?", product.getProductId());
        postJson(alice, "/api/v1/conversations", "{\"product_id\":" + product.getProductId() + "}")
                .andExpect(status().isNotFound());

        jdbc.update("INSERT INTO orders (order_code, buyer_id, seller_id, contact_name, contact_phone, total_amount) "
                + "VALUES ('OD-CHAT-1', ?, ?, 'A', '0900000000', 1000)", alice.getUserId(), bob.getUserId());
        long orderId = jdbc.queryForObject("SELECT order_id FROM orders WHERE order_code='OD-CHAT-1'", Long.class);
        postJson(bob, "/api/v1/conversations/from-order", "{\"order_id\":" + orderId + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversation_id", is((int) convId)));
        postJson(carol, "/api/v1/conversations/from-order", "{\"order_id\":" + orderId + "}")
                .andExpect(status().isNotFound());
        postJson(alice, "/api/v1/conversations/from-order", "{\"order_id\":987654}").andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------ groups

    @Test
    @DisplayName("Nhóm: tạo (201) → mời → chấp nhận / từ chối; thành viên ngoài không mời được; đủ system message")
    void groupLifecycleInviteAcceptDecline() throws Exception {
        long gid = createGroup(alice, "Nhóm Test PTIT");
        getAs(alice, "/api/v1/conversations/" + gid).andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("GROUP")))
                .andExpect(jsonPath("$.my_role", is("OWNER")))
                .andExpect(jsonPath("$.member_count", is(1)));

        ResultActions inv = postJson(alice, "/api/v1/conversations/groups/" + gid + "/invites",
                "{\"invitee_ids\":[" + bob.getUserId() + "," + carol.getUserId() + "]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status", is("PENDING")))
                .andExpect(jsonPath("$[0].conversation_title", is("Nhóm Test PTIT")));
        long bobInvite = idOf(inv, "$[0].invite_id");
        long carolInvite = idOf(inv, "$[1].invite_id");
        // mời lại người đang PENDING → bị bỏ qua (không lỗi, không trùng)
        postJson(alice, "/api/v1/conversations/groups/" + gid + "/invites", "{\"invitee_ids\":[" + bob.getUserId() + "]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM conversation_invites WHERE status='PENDING'", Integer.class));
        // notification SYSTEM/GROUP_INVITE cho người được mời
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND type='SYSTEM' "
                + "AND ref_type='GROUP_INVITE' AND ref_id=?", Integer.class, bob.getUserId(), bobInvite));
        assertTrue(EVENTS.stream().anyMatch(e -> e.userId().equals(bob.getUserId())
                && "GROUP_INVITE_CREATED".equals(e.data().get("type"))));

        getAs(bob, "/api/v1/conversations/invites").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].inviter_name", is("Alice Tester")));
        // người ngoài không phải người được mời không thể phản hồi
        postJson(dave, "/api/v1/conversations/invites/" + bobInvite + "/respond", "{\"action\":\"ACCEPT\"}")
                .andExpect(status().isNotFound());
        postJson(bob, "/api/v1/conversations/invites/" + bobInvite + "/respond", "{\"action\":\"ACCEPT\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("ACCEPTED")));
        // lũy đẳng: phản hồi lần 2 trả trạng thái hiện tại
        postJson(bob, "/api/v1/conversations/invites/" + bobInvite + "/respond", "{\"action\":\"ACCEPT\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("ACCEPTED")));
        postJson(carol, "/api/v1/conversations/invites/" + carolInvite + "/respond", "{\"action\":\"DECLINE\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("DECLINED")));
        postJson(carol, "/api/v1/conversations/invites/" + carolInvite + "/respond", "{\"action\":\"MAYBE\"}")
                .andExpect(status().isUnprocessableEntity());

        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM conversation_members WHERE conversation_id=? "
                + "AND left_at IS NULL", Integer.class, gid));
        getAs(bob, "/api/v1/conversations/groups/" + gid + "/members").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].role", is("OWNER")))
                .andExpect(jsonPath("$[1].role", is("MEMBER")))
                .andExpect(jsonPath("$[0].is_online", nullValue())); // presence không khả dụng → null
        // người ngoài nhóm: 403 (Python)
        getAs(carol, "/api/v1/conversations/groups/" + gid + "/members").andExpect(status().isForbidden());
        postJson(dave, "/api/v1/conversations/groups/" + gid + "/invites", "{\"invitee_ids\":[" + carol.getUserId() + "]}")
                .andExpect(status().isForbidden());
        postJson(alice, "/api/v1/conversations/groups/" + gid + "/invites", "{\"invitee_ids\":[]}")
                .andExpect(status().isUnprocessableEntity());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=? AND type='SYSTEM'",
                Integer.class, gid), "system message: tạo nhóm + bob tham gia");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM conversations WHERE last_message_at IS NOT NULL",
                Integer.class), "SYSTEM message KHÔNG cập nhật last_message_at");
    }

    @Test
    @DisplayName("Nhóm requires_approval: người mời không phải OWNER → PENDING_APPROVAL → chỉ OWNER duyệt/từ chối")
    void groupApprovalFlow() throws Exception {
        long gid = createGroup(alice, "Nhóm Phê Duyệt");
        join(alice, gid, bob);
        patchJson(bob, "/api/v1/conversations/groups/" + gid, "{\"requires_approval\":true}")
                .andExpect(status().isForbidden()); // chỉ OWNER đổi
        patchJson(alice, "/api/v1/conversations/groups/" + gid, "{\"requires_approval\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.requires_approval", is(true)));

        // bob (MEMBER) mời carol, dave
        ResultActions inv = postJson(bob, "/api/v1/conversations/groups/" + gid + "/invites",
                "{\"invitee_ids\":[" + carol.getUserId() + "," + dave.getUserId() + "]}").andExpect(status().isOk());
        long carolInvite = idOf(inv, "$[0].invite_id");
        long daveInvite = idOf(inv, "$[1].invite_id");
        postJson(carol, "/api/v1/conversations/invites/" + carolInvite + "/respond", "{\"action\":\"ACCEPT\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("PENDING_APPROVAL")));
        postJson(dave, "/api/v1/conversations/invites/" + daveInvite + "/respond", "{\"action\":\"ACCEPT\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("PENDING_APPROVAL")));
        assertTrue(EVENTS.stream().anyMatch(e -> e.userId().equals(alice.getUserId())
                && "GROUP_APPROVAL_REQUESTED".equals(e.data().get("type"))));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM conversation_members WHERE conversation_id=? AND user_id=?",
                Integer.class, gid, carol.getUserId()), "chưa duyệt → chưa là thành viên");

        getAs(bob, "/api/v1/conversations/groups/" + gid + "/pending-approvals").andExpect(status().isForbidden());
        getAs(alice, "/api/v1/conversations/groups/" + gid + "/pending-approvals").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].invitee_name", is("Carol Tester")))
                .andExpect(jsonPath("$.items[0].inviter_name", is("Bob Tester")));
        postJson(bob, "/api/v1/conversations/groups/" + gid + "/pending-approvals/" + carolInvite, "{\"action\":\"APPROVE\"}")
                .andExpect(status().isForbidden());
        postJson(alice, "/api/v1/conversations/groups/" + gid + "/pending-approvals/" + carolInvite, "{\"action\":\"APPROVE\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("ACCEPTED")));
        postJson(alice, "/api/v1/conversations/groups/" + gid + "/pending-approvals/" + daveInvite, "{\"action\":\"REJECT\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("DECLINED")));
        // xử lý lại → 400 (đã xử lý)
        postJson(alice, "/api/v1/conversations/groups/" + gid + "/pending-approvals/" + carolInvite, "{\"action\":\"APPROVE\"}")
                .andExpect(status().isBadRequest());
        getAs(carol, "/api/v1/conversations/" + gid).andExpect(status().isOk()).andExpect(jsonPath("$.my_role", is("MEMBER")));
        getAs(dave, "/api/v1/conversations/" + gid).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Phân quyền OWNER/ADMIN/MEMBER, biệt danh, chuyển quyền, xoá thành viên, rời nhóm, xoá mềm nhóm")
    void rolePermissionsAndLifecycle() throws Exception {
        long gid = createGroup(alice, "Nhóm Phân Quyền");
        join(alice, gid, bob);
        join(alice, gid, carol);
        String base = "/api/v1/conversations/groups/" + gid;

        // chỉ OWNER đổi vai trò; không đổi chính mình
        postJson(bob, base + "/members/" + carol.getUserId() + "/role", "{\"role\":\"ADMIN\"}").andExpect(status().isForbidden());
        postJson(alice, base + "/members/" + alice.getUserId() + "/role", "{\"role\":\"ADMIN\"}").andExpect(status().isBadRequest());
        postJson(alice, base + "/members/" + bob.getUserId() + "/role", "{\"role\":\"OWNER\"}").andExpect(status().isUnprocessableEntity());
        postJson(alice, base + "/members/" + bob.getUserId() + "/role", "{\"role\":\"ADMIN\"}").andExpect(status().isOk());
        postJson(alice, base + "/members/" + dave.getUserId() + "/role", "{\"role\":\"ADMIN\"}").andExpect(status().isNotFound());

        // MEMBER không đổi tên; ADMIN đổi được; ADMIN không bật phê duyệt
        patchJson(carol, base, "{\"title\":\"Tên mới\"}").andExpect(status().isForbidden());
        patchJson(bob, base, "{\"title\":\"Tên mới của nhóm\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Tên mới của nhóm"))).andExpect(jsonPath("$.my_role", is("ADMIN")));
        patchJson(bob, base, "{\"requires_approval\":true}").andExpect(status().isForbidden());
        patchJson(dave, base, "{\"title\":\"Hack nhóm\"}").andExpect(status().isForbidden());
        patchJson(bob, base, "{\"title\":\"ab\"}").andExpect(status().isUnprocessableEntity());

        // biệt danh
        patchJson(carol, base + "/me/nickname", "{\"nickname\":\"  Cô Ca  \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname", is("Cô Ca")));
        patchJson(carol, base + "/me/nickname", "{\"nickname\":\"\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname", nullValue()));
        patchJson(carol, base + "/me/nickname", "{\"nickname\":\"a\\nb\"}").andExpect(status().isBadRequest());
        patchJson(dave, base + "/me/nickname", "{\"nickname\":\"x\"}").andExpect(status().isForbidden());

        // xoá thành viên: MEMBER không được; ADMIN không xoá OWNER/ADMIN; ADMIN xoá MEMBER được
        deleteAs(carol, base + "/members/" + bob.getUserId()).andExpect(status().isForbidden());
        deleteAs(bob, base + "/members/" + alice.getUserId()).andExpect(status().isForbidden());
        deleteAs(bob, base + "/members/" + bob.getUserId()).andExpect(status().isBadRequest());
        deleteAs(bob, base + "/members/" + carol.getUserId()).andExpect(status().isOk());
        assertTrue(EVENTS.stream().anyMatch(e -> e.userId().equals(carol.getUserId())
                && "REMOVED".equals(e.data().get("action"))));
        getAs(carol, "/api/v1/conversations/" + gid).andExpect(status().isNotFound()); // đã bị xoá khỏi nhóm
        postJson(carol, "/api/v1/conversations/" + gid + "/messages", "{\"content\":\"hi\"}").andExpect(status().isNotFound());

        // OWNER không rời khi còn người; chuyển quyền; owner cũ rời được
        postJson(alice, base + "/leave", "{}").andExpect(status().isBadRequest());
        postJson(bob, base + "/transfer-ownership", "{\"new_owner_id\":" + alice.getUserId() + "}").andExpect(status().isForbidden());
        postJson(alice, base + "/transfer-ownership", "{\"new_owner_id\":" + carol.getUserId() + "}").andExpect(status().isNotFound());
        postJson(alice, base + "/transfer-ownership", "{\"new_owner_id\":" + bob.getUserId() + "}").andExpect(status().isOk());
        getAs(bob, "/api/v1/conversations/" + gid).andExpect(jsonPath("$.my_role", is("OWNER")));
        getAs(alice, "/api/v1/conversations/" + gid).andExpect(jsonPath("$.my_role", is("MEMBER")));
        postJson(alice, base + "/leave", "{}").andExpect(status().isOk());
        postJson(alice, base + "/leave", "{}").andExpect(status().isOk()); // lũy đẳng

        // chủ cuối cùng không rời được → phải xoá nhóm; chỉ OWNER xoá; xoá mềm
        postJson(bob, base + "/leave", "{}").andExpect(status().isBadRequest());
        deleteAs(alice, base).andExpect(status().isForbidden());
        deleteAs(bob, base).andExpect(status().isOk()).andExpect(jsonPath("$.success", is(true)));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM conversations WHERE conversation_id=? AND deleted_at IS NOT NULL",
                Integer.class, gid), "xoá mềm: hàng vẫn còn");
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM messages WHERE conversation_id=?", Integer.class, gid) > 0,
                "lịch sử vẫn được giữ");
        getAs(bob, "/api/v1/conversations/" + gid).andExpect(status().isNotFound());
        deleteAs(bob, base).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Giới hạn 10 nhóm sở hữu; 422 khi tiêu đề < 3 ký tự; owned-group-count")
    void groupCapAndValidation() throws Exception {
        for (int i = 0; i < 10; i++) {
            createGroup(alice, "Nhóm số " + i);
        }
        postJson(alice, "/api/v1/conversations/groups", "{\"title\":\"Nhóm thứ 11\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Bạn đã tạo tối đa 10 nhóm trò chuyện.")));
        postJson(bob, "/api/v1/conversations/groups", "{\"title\":\"ab\"}").andExpect(status().isUnprocessableEntity());
        getAs(alice, "/api/v1/conversations/owned-group-count").andExpect(jsonPath("$.owned_group_count", is(10)));
        getAs(bob, "/api/v1/conversations/owned-group-count").andExpect(jsonPath("$.owned_group_count", is(0)));
    }

    // ------------------------------------------------------------------------------------ messages

    @Test
    @DisplayName("Tin nhắn + phân trang cursor (sent_at,message_id) không trùng/sót; inbox keyset; reply_to; preview")
    void messagesCursorPaginationAndInbox() throws Exception {
        long cid = openDirect(alice, bob);
        getAs(bob, "/api/v1/conversations").andExpect(jsonPath("$.items", hasSize(0))); // chưa có tin → chưa hiện inbox
        List<Long> sent = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            sent.add(sendText(i % 2 == 0 ? bob : alice, cid, "tin " + i));
        }
        long replyId = idOf(postJson(bob, "/api/v1/conversations/" + cid + "/messages",
                "{\"content\":\"trả lời\",\"reply_to_message_id\":" + sent.get(0) + "}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reply_to.message_id", is(sent.get(0).intValue())))
                .andExpect(jsonPath("$.reply_to.preview", is("tin 1"))), "$.message_id");
        sent.add(replyId);

        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        boolean more = true;
        while (more) {
            String url = "/api/v1/conversations/" + cid + "/messages?limit=4" + (cursor == null ? "" : "&cursor=" + cursor);
            String body = getAs(alice, url).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            List<Integer> ids = JsonPath.read(body, "$.items[*].message_id");
            ids.forEach(i -> seen.add(i.longValue()));
            more = JsonPath.read(body, "$.has_more");
            cursor = more ? JsonPath.read(body, "$.next_cursor") : null;
            pages++;
            assertTrue(pages <= 3);
        }
        assertEquals(2, pages);
        List<Long> expected = new ArrayList<>(sent);
        java.util.Collections.reverse(expected);
        assertEquals(expected, seen, "thứ tự sent_at DESC, message_id DESC, đủ 6 tin, không trùng");

        getAs(alice, "/api/v1/conversations/" + cid + "/messages?cursor=!!!").andExpect(status().isUnprocessableEntity());
        getAs(alice, "/api/v1/conversations/" + cid + "/messages?limit=0").andExpect(status().isUnprocessableEntity());
        getAs(alice, "/api/v1/conversations/" + cid + "/messages?limit=101").andExpect(status().isUnprocessableEntity());

        // is_mine + sent_at kết thúc bằng Z
        getAs(alice, "/api/v1/conversations/" + cid + "/messages?limit=1").andExpect(jsonPath("$.items[0].is_mine", is(false)))
                .andExpect(jsonPath("$.items[0].sent_at", org.hamcrest.Matchers.endsWith("Z")));

        // inbox: 1 hội thoại, preview là tin mới nhất, unread của alice = 1 (con trỏ đọc của alice đã ở tin 5 do chính alice gửi; chỉ 'trả lời' của bob mới hơn)
        getAs(alice, "/api/v1/conversations").andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].last_message_preview", is("trả lời")))
                .andExpect(jsonPath("$.items[0].unread_count", is(1)))
                .andExpect(jsonPath("$.items[0].title", is("Bob Tester")))
                .andExpect(jsonPath("$.has_more", is(false)));
        getAs(alice, "/api/v1/conversations?type=GROUP").andExpect(jsonPath("$.items", hasSize(0)));
        getAs(alice, "/api/v1/conversations?type=WHATEVER").andExpect(jsonPath("$.items", hasSize(0)));
        getAs(alice, "/api/v1/conversations?limit=51").andExpect(status().isUnprocessableEntity());

        // validation gửi tin
        postJson(alice, "/api/v1/conversations/" + cid + "/messages", "{\"content\":\"   \"}").andExpect(status().isUnprocessableEntity());
        postJson(alice, "/api/v1/conversations/" + cid + "/messages", "{\"content\":\"" + "x".repeat(2001) + "\"}")
                .andExpect(status().isUnprocessableEntity());
        postJson(alice, "/api/v1/conversations/" + cid + "/messages",
                "{\"content\":\"hi\",\"reply_to_message_id\":999999}").andExpect(status().isNotFound());
        // MESSAGE_CREATED tới cả hai thành viên SAU commit
        assertTrue(EVENTS.stream().anyMatch(e -> e.userId().equals(bob.getUserId())
                && "MESSAGE_CREATED".equals(e.data().get("type"))));
        assertTrue(EVENTS.stream().anyMatch(e -> e.userId().equals(alice.getUserId())
                && "MESSAGE_CREATED".equals(e.data().get("type"))));
        assertTrue(PUSHED.stream().anyMatch(p -> p.startsWith(bob.getUserId() + "|")), "push cho người nhận");
        assertFalse(PUSHED.stream().anyMatch(p -> p.contains("Tin nhắn mới từ Alice") && p.startsWith(alice.getUserId() + "|")));
    }

    @Test
    @DisplayName("Nhóm: thành viên mới chỉ thấy tin từ joined_at; thẻ sản phẩm chỉ trong DIRECT")
    void groupMessagesRespectJoinedAt() throws Exception {
        long gid = createGroup(alice, "Nhóm Lịch Sử");
        sendText(alice, gid, "tin cũ trước khi vào");
        join(alice, gid, bob);
        long newMsg = sendText(alice, gid, "tin mới sau khi vào");
        String body = getAs(bob, "/api/v1/conversations/" + gid + "/messages").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> contents = JsonPath.read(body, "$.items[*].content");
        assertFalse(contents.contains("tin cũ trước khi vào"));
        assertTrue(contents.contains("tin mới sau khi vào"));
        List<Integer> ids = JsonPath.read(body, "$.items[*].message_id");
        assertTrue(ids.contains((int) newMsg));
        postJson(alice, "/api/v1/conversations/" + gid + "/messages", "{\"product_id\":1}").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------------------------ reactions

    @Test
    @DisplayName("Reaction: mỗi user 1 reaction/tin (đổi key = UPDATE), gỡ idempotent, key sai 422, SYSTEM 400, đua nhiều thread")
    void reactions() throws Exception {
        long cid = openDirect(alice, bob);
        long mid = sendText(alice, cid, "thả cảm xúc đi");
        String url = "/api/v1/conversations/" + cid + "/messages/" + mid + "/reaction";

        putJson(bob, url, "{\"reaction_key\":\"THICH\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.my_reaction", is("THICH"))).andExpect(jsonPath("$.reactions[0].count", is(1)));
        putJson(bob, url, "{\"reaction_key\":\"CHOT_DEAL\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.my_reaction", is("CHOT_DEAL"))).andExpect(jsonPath("$.reactions", hasSize(1)))
                .andExpect(jsonPath("$.reactions[0].reaction_key", is("CHOT_DEAL")));
        putJson(alice, url, "{\"reaction_key\":\"CHOT_DEAL\"}").andExpect(jsonPath("$.reactions[0].count", is(2)));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM message_reactions WHERE message_id=?", Integer.class, mid));
        putJson(bob, url, "{\"reaction_key\":\"NOPE\"}").andExpect(status().isUnprocessableEntity());
        putJson(bob, url, "{}").andExpect(status().isUnprocessableEntity());

        // 12 key hợp lệ đều chấp nhận được
        for (ReactionKey k : ReactionKey.values()) {
            putJson(bob, url, "{\"reaction_key\":\"" + k.name() + "\"}").andExpect(status().isOk());
        }
        assertEquals(12, ReactionKey.values().length);

        deleteAs(bob, url).andExpect(status().isOk()).andExpect(jsonPath("$.my_reaction", nullValue()));
        deleteAs(bob, url).andExpect(status().isOk()); // idempotent
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM message_reactions WHERE message_id=?", Integer.class, mid));
        assertTrue(EVENTS.stream().anyMatch(e -> "MESSAGE_REACTIONS_UPDATED".equals(e.data().get("type"))));

        // tin hệ thống không thả cảm xúc được; người ngoài 404
        long gid = createGroup(alice, "Nhóm Reaction");
        long sysId = jdbc.queryForObject("SELECT message_id FROM messages WHERE conversation_id=? AND type='SYSTEM'", Long.class, gid);
        putJson(alice, "/api/v1/conversations/" + gid + "/messages/" + sysId + "/reaction", "{\"reaction_key\":\"THICH\"}")
                .andExpect(status().isBadRequest());
        putJson(dave, url, "{\"reaction_key\":\"THICH\"}").andExpect(status().isNotFound());

    }

    @Test
    @DisplayName("Reaction đua: 10 thread cùng một user → đúng 1 hàng (ON CONFLICT trên UNIQUE thật)")
    void reactionRaceKeepsSingleRow() throws Exception {
        long cid = openDirect(alice, bob);
        long mid = sendText(alice, cid, "đua reaction");
        ReactionKey[] keys = ReactionKey.values();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> fs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final ReactionKey k = keys[i];
            fs.add(pool.submit(() -> {
                start.await();
                return conversationService.setReaction(bob, cid, mid, k);
            }));
        }
        start.countDown();
        for (Future<Object> f : fs) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM message_reactions WHERE message_id=? AND user_id=?",
                Integer.class, mid, bob.getUserId()));
    }

    // ------------------------------------------------------------------------------------ mentions

    @Test
    @DisplayName("Mention: chỉ trong GROUP, phải là thành viên hoạt động, offset khớp nội dung; tạo notification NEW_MESSAGE")
    void mentions() throws Exception {
        long gid = createGroup(alice, "Nhóm Mention");
        join(alice, gid, bob);
        String content = "@Bob Tester xem giúp";
        String mention = "[{\"mentioned_user_id\":" + bob.getUserId() + ",\"display_text\":\"@Bob Tester\","
                + "\"start_offset\":0,\"end_offset\":11}]";

        mockMvc.perform(auth(multipart("/api/v1/conversations/" + gid + "/messages")
                        .param("content", content).param("mentions", mention), alice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions", hasSize(1)))
                .andExpect(jsonPath("$.mentions[0].display_name", is("Bob Tester")))
                .andExpect(jsonPath("$.mentions[0].user_id", is(bob.getUserId().intValue())));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM message_mentions", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND type='NEW_MESSAGE' "
                + "AND ref_type='CONVERSATION' AND ref_id=?", Integer.class, bob.getUserId(), gid));

        // offset sai → 422; người không phải thành viên → 422; trùng user trong cùng tin → chỉ 1 mention
        mockMvc.perform(auth(multipart("/api/v1/conversations/" + gid + "/messages").param("content", content)
                        .param("mentions", mention.replace("\"end_offset\":11", "\"end_offset\":5")), alice))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(auth(multipart("/api/v1/conversations/" + gid + "/messages").param("content", content)
                        .param("mentions", mention.replace(String.valueOf(bob.getUserId()), String.valueOf(carol.getUserId()))), alice))
                .andExpect(status().isUnprocessableEntity());
        String dup = "[" + mention.substring(1, mention.length() - 1) + "," + mention.substring(1, mention.length() - 1) + "]";
        mockMvc.perform(auth(multipart("/api/v1/conversations/" + gid + "/messages").param("content", content)
                        .param("mentions", dup), alice))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mentions", hasSize(1)));

        // DIRECT không cho mention
        long cid = openDirect(alice, bob);
        mockMvc.perform(auth(multipart("/api/v1/conversations/" + cid + "/messages").param("content", content)
                        .param("mentions", mention), alice))
                .andExpect(status().isBadRequest());
        // người bị mention rời nhóm → mention mới bị từ chối
        postJson(bob, "/api/v1/conversations/groups/" + gid + "/leave", "{}").andExpect(status().isOk());
        mockMvc.perform(auth(multipart("/api/v1/conversations/" + gid + "/messages").param("content", content)
                        .param("mentions", mention), alice))
                .andExpect(status().isUnprocessableEntity());
        // nhánh JSON cũng nhận mentions
        postJson(alice, "/api/v1/conversations/" + gid + "/messages", "{\"content\":\"không mention\"}").andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------------------------ read / notifications

    @Test
    @DisplayName("Đã đọc: đưa last_read_message_id, trả read_count, notification NEW_MESSAGE coalesce 1 dòng rồi chuyển is_read=true")
    void readReceiptsAndNotifications() throws Exception {
        long cid = openDirect(alice, bob);
        sendText(alice, cid, "một");
        sendText(alice, cid, "hai");
        long last = sendText(alice, cid, "ba");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND type='NEW_MESSAGE' "
                + "AND ref_id=? AND is_read=false", Integer.class, bob.getUserId(), cid), "coalesce: 1 notification chưa đọc");
        getAs(bob, "/api/v1/conversations").andExpect(jsonPath("$.items[0].unread_count", is(3)));

        postJson(bob, "/api/v1/conversations/" + cid + "/read", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true))).andExpect(jsonPath("$.read_count", is(3)));
        assertEquals(last, jdbc.queryForObject("SELECT last_read_message_id FROM conversation_members WHERE conversation_id=? "
                + "AND user_id=?", Long.class, cid, bob.getUserId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND is_read=false",
                Integer.class, bob.getUserId()));
        getAs(bob, "/api/v1/conversations").andExpect(jsonPath("$.items[0].unread_count", is(0)));
        postJson(bob, "/api/v1/conversations/" + cid + "/read", "{}").andExpect(jsonPath("$.read_count", is(0)));

        // tin nhắn mới sau khi đã đọc → notification mới được tạo lại
        sendText(alice, cid, "bốn");
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND type='NEW_MESSAGE'",
                Integer.class, bob.getUserId()));
        postJson(dave, "/api/v1/conversations/" + cid + "/read", "{}").andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------ attachments

    @Test
    @DisplayName("Đính kèm: upload qua MediaStorage (LocalMediaStorage), danh sách/summary dùng chung, thu hồi → tombstone + dọn media")
    void attachmentsAndRecall() throws Exception {
        long cid = openDirect(alice, bob);
        MockMultipartFile png = new MockMultipartFile("files", "anh_sach.png", "image/png", pngBytes());
        MockMultipartFile pdf = new MockMultipartFile("files", "de_cuong.pdf", "application/pdf",
                "%PDF-1.4 hello".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile txt = new MockMultipartFile("files", "ghi_chu.txt", "text/plain",
                "xin chào".getBytes(StandardCharsets.UTF_8));

        ResultActions sent = sendMultipart(alice, cid, "kèm tệp", png, pdf, txt).andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments", hasSize(3)))
                .andExpect(jsonPath("$.attachments[0].kind", is("IMAGE")))
                .andExpect(jsonPath("$.attachments[0].mime_type", is("image/png")))
                .andExpect(jsonPath("$.attachments[0].url", startsWith("https://media.local.test/")))
                .andExpect(jsonPath("$.attachments[0].original_name", is("anh_sach.png")))
                .andExpect(jsonPath("$.attachments[1].kind", is("PDF")))
                .andExpect(jsonPath("$.attachments[2].kind", is("DOCUMENT")))
                .andExpect(jsonPath("$.attachments[2].mime_type", is("text/plain")));
        long mid = idOf(sent, "$.message_id");
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM message_attachments WHERE message_id=?", Integer.class, mid));

        getAs(bob, "/api/v1/conversations/" + cid + "/attachments/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.media_count", is(1))).andExpect(jsonPath("$.audio_count", is(0)))
                .andExpect(jsonPath("$.file_count", is(2)));
        getAs(bob, "/api/v1/conversations/" + cid + "/attachments").andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].kind", is("IMAGE"))).andExpect(jsonPath("$.items[0].sender_id", is(alice.getUserId().intValue())));
        getAs(bob, "/api/v1/conversations/" + cid + "/attachments?category=file").andExpect(jsonPath("$.items", hasSize(2)));
        getAs(bob, "/api/v1/conversations/" + cid + "/attachments?kind=audio").andExpect(jsonPath("$.items", hasSize(0)));
        getAs(bob, "/api/v1/conversations/" + cid + "/attachments?category=zzz").andExpect(status().isBadRequest());
        getAs(bob, "/api/v1/conversations/" + cid + "/attachments?category=file&limit=1")
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.has_more", is(true)));
        getAs(dave, "/api/v1/conversations/" + cid + "/attachments").andExpect(status().isNotFound());
        getAs(dave, "/api/v1/conversations/" + cid + "/attachments/summary").andExpect(status().isNotFound());

        // inbox preview khi tin nhắn có content ưu tiên content; tin chỉ ảnh → "[Hình ảnh]"
        sendMultipart(alice, cid, null, png).andExpect(status().isCreated());
        getAs(bob, "/api/v1/conversations").andExpect(jsonPath("$.items[0].last_message_preview", is("[Hình ảnh]")));

        // thu hồi: người khác 403; người gửi 200 tombstone; lặp lại idempotent; media bị dọn + hàng cleanup gỡ
        deleteAs(bob, "/api/v1/conversations/" + cid + "/messages/" + mid).andExpect(status().isForbidden());
        deleteAs(dave, "/api/v1/conversations/" + cid + "/messages/" + mid).andExpect(status().isNotFound());
        deleteAs(alice, "/api/v1/conversations/" + cid + "/messages/" + mid).andExpect(status().isOk())
                .andExpect(jsonPath("$.is_deleted", is(true))).andExpect(jsonPath("$.content", nullValue()))
                .andExpect(jsonPath("$.attachments", hasSize(0)));
        deleteAs(alice, "/api/v1/conversations/" + cid + "/messages/" + mid).andExpect(status().isOk())
                .andExpect(jsonPath("$.is_deleted", is(true)));
        assertEquals(3, DESTROYED.stream().distinct().count(), "3 asset của tin bị thu hồi được destroy");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM media_cleanup_jobs", Integer.class),
                "destroy thành công → hàng cleanup được gỡ");
        assertTrue(jdbc.queryForObject("SELECT is_deleted FROM messages WHERE message_id=?", Boolean.class, mid));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM messages WHERE message_id=? AND content IS NULL", Integer.class, mid));
        getAs(bob, "/api/v1/conversations/" + cid + "/attachments/summary").andExpect(jsonPath("$.file_count", is(0)))
                .andExpect(jsonPath("$.media_count", is(1)));
        assertTrue(EVENTS.stream().anyMatch(e -> "MESSAGE_DELETED".equals(e.data().get("type"))
                && !e.data().containsKey("content")));
        // tin bị thu hồi hiện tombstone trong lịch sử
        getAs(bob, "/api/v1/conversations/" + cid + "/messages").andExpect(jsonPath("$.items[1].is_deleted", is(true)))
                .andExpect(jsonPath("$.items[1].content", nullValue()));
    }

    @Test
    @DisplayName("Đính kèm: chặn .exe đổi đuôi, zip giả Office, định dạng lạ, rỗng, quá 4 tệp, quá 1 video")
    void attachmentValidation() throws Exception {
        long cid = openDirect(alice, bob);
        byte[] exe = "MZ\u0090\u0000\u0003\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);
        sendMultipart(alice, cid, "x", new MockMultipartFile("files", "anh.png", "image/png", exe))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", is("Executable files are not permitted.")));
        byte[] fakeZip = new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0, 0, 0, 0, 0};
        sendMultipart(alice, cid, "x", new MockMultipartFile("files", "bao_cao.docx", "application/zip", fakeZip))
                .andExpect(status().isBadRequest());
        sendMultipart(alice, cid, "x", new MockMultipartFile("files", "a.bin", "application/octet-stream", "xxxxxxxx".getBytes()))
                .andExpect(status().isBadRequest());
        sendMultipart(alice, cid, "x", new MockMultipartFile("files", "trong.png", "image/png", new byte[0]))
                .andExpect(status().isBadRequest());
        MockMultipartFile[] five = new MockMultipartFile[5];
        for (int i = 0; i < 5; i++) {
            five[i] = new MockMultipartFile("files", "a" + i + ".png", "image/png", pngBytes());
        }
        sendMultipart(alice, cid, "x", five).andExpect(status().isBadRequest());
        byte[] mp4 = new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0, 'i', 's', 'o', 'm', 0, 0, 0, 0};
        sendMultipart(alice, cid, null, new MockMultipartFile("files", "v1.mp4", "video/mp4", mp4),
                new MockMultipartFile("files", "v2.mp4", "video/mp4", mp4)).andExpect(status().isUnprocessableEntity());
        // voice note > 5 phút → 400
        mockMvc.perform(auth(multipart("/api/v1/conversations/" + cid + "/messages").param("voice_duration_ms", "400000")
                .param("content", "voice"), alice)).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM messages", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM message_attachments", Integer.class));
        assertTrue(DESTROYED.isEmpty(), "chặn trước upload nên không có gì để dọn");
        // một video hợp lệ + ghi âm
        sendMultipart(alice, cid, null, new MockMultipartFile("files", "v1.mp4", "video/mp4", mp4))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.attachments[0].kind", is("VIDEO")));
    }

    @Test
    @DisplayName("DB ghi lỗi SAU khi upload → rollback toàn bộ + destroyAsset dọn asset mồ côi (500)")
    void uploadedAssetsAreCleanedWhenDatabaseWriteFails() throws Exception {
        long cid = openDirect(alice, bob);
        String hugeName = "a".repeat(300) + ".png"; // original_name VARCHAR(255) → DB từ chối
        sendMultipart(alice, cid, "sẽ lỗi", new MockMultipartFile("files", hugeName, "image/png", pngBytes()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.detail", is("Failed to process message")));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM messages", Integer.class), "rollback: không còn tin nhắn");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM message_attachments", Integer.class));
        assertEquals(1, DESTROYED.size(), "asset đã upload bị destroy");
        assertTrue(EVENTS.stream().noneMatch(e -> "MESSAGE_CREATED".equals(e.data().get("type"))),
                "rollback → không phát realtime");
    }

    // ------------------------------------------------------------------------------------ security / BOLA

    @Test
    @DisplayName("BOLA: người ngoài hội thoại bị chặn ở mọi endpoint; thiếu token → 401")
    void bolaAndAuthentication() throws Exception {
        long cid = openDirect(alice, bob);
        long mid = sendText(alice, cid, "bí mật");
        String c = "/api/v1/conversations/" + cid;
        getAs(dave, c).andExpect(status().isNotFound());
        getAs(dave, c + "/messages").andExpect(status().isNotFound());
        postJson(dave, c + "/messages", "{\"content\":\"xâm nhập\"}").andExpect(status().isNotFound());
        postJson(dave, c + "/read", "{}").andExpect(status().isNotFound());
        getAs(dave, c + "/presence").andExpect(status().isNotFound());
        getAs(dave, c + "/attachments").andExpect(status().isNotFound());
        putJson(dave, c + "/messages/" + mid + "/reaction", "{\"reaction_key\":\"THICH\"}").andExpect(status().isNotFound());
        deleteAs(dave, c + "/messages/" + mid + "/reaction").andExpect(status().isNotFound());
        deleteAs(dave, c + "/messages/" + mid).andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM messages", Integer.class));
        // inbox của người ngoài không thấy
        getAs(dave, "/api/v1/conversations").andExpect(jsonPath("$.items", hasSize(0)));
        // mid thuộc hội thoại khác → 404 (không lộ tồn tại)
        long other = openDirect(alice, carol);
        putJson(alice, "/api/v1/conversations/" + other + "/messages/" + mid + "/reaction", "{\"reaction_key\":\"THICH\"}")
                .andExpect(status().isNotFound());
        deleteAs(alice, "/api/v1/conversations/" + other + "/messages/" + mid).andExpect(status().isNotFound());
        postJson(alice, "/api/v1/conversations/" + other + "/messages",
                "{\"content\":\"trả lời chéo\",\"reply_to_message_id\":" + mid + "}").andExpect(status().isNotFound());

        mockMvc.perform(get(c)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/conversations")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(c + "/messages").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(c).header(HttpHeaders.AUTHORIZATION, "Bearer rác")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Thu hồi: tài khoản không ACTIVE bị 403; tin hệ thống không thu hồi được (400)")
    void recallGuards() throws Exception {
        long gid = createGroup(alice, "Nhóm Thu Hồi");
        long sysId = jdbc.queryForObject("SELECT message_id FROM messages WHERE conversation_id=? AND type='SYSTEM'", Long.class, gid);
        deleteAs(alice, "/api/v1/conversations/" + gid + "/messages/" + sysId).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Cannot recall system messages.")));
        long mid = sendText(alice, gid, "sắp bị khoá");
        jdbc.update("UPDATE users SET status='BANNED' WHERE user_id=?", alice.getUserId());
        assertEquals(UserStatus.BANNED, userRepository.findById(alice.getUserId()).orElseThrow().getStatus());
        deleteAs(alice, "/api/v1/conversations/" + gid + "/messages/" + mid).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------ presence / support

    @Test
    @DisplayName("Presence: bản mặc định không khả dụng → presence_available=false, rỗng, không lỗi")
    void presenceUnavailable() throws Exception {
        long cid = openDirect(alice, bob);
        getAs(alice, "/api/v1/conversations/" + cid + "/presence").andExpect(status().isOk())
                .andExpect(jsonPath("$.presence_available", is(false))).andExpect(jsonPath("$.online_user_ids", hasSize(0)));
        getAs(dave, "/api/v1/conversations/" + cid + "/presence").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Nhóm hỗ trợ chính thức: chưa cấu hình → 503 (đọc) và 503 (tham gia)")
    void officialSupportNotConfigured() throws Exception {
        getAs(alice, "/api/v1/conversations/official-support").andExpect(status().isServiceUnavailable());
        postJson(alice, "/api/v1/conversations/official-support/join", "{}").andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Invite: đua 8 thread mời cùng một người → đúng 1 lời mời mở (partial unique index thật)")
    void inviteRaceKeepsSingleOpenInvite() throws Exception {
        long gid = createGroup(alice, "Nhóm Đua Mời");
        join(alice, gid, bob);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final User inviter = i % 2 == 0 ? alice : bob;
            fs.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(auth(post("/api/v1/conversations/groups/" + gid + "/invites"), inviter)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"invitee_ids\":[" + carol.getUserId() + "]}"))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> f : fs) {
            assertEquals(200, f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM conversation_invites WHERE invitee_id=? AND status='PENDING'",
                Integer.class, carol.getUserId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND ref_type='GROUP_INVITE'",
                Integer.class, carol.getUserId()));
    }

    @Test
    @DisplayName("Invite candidates: chỉ bạn từng nhắn trực tiếp, chưa là thành viên / chưa được mời, tìm theo tên")
    void inviteCandidates() throws Exception {
        long gid = createGroup(alice, "Nhóm Gợi Ý");
        long d1 = openDirect(alice, bob);
        sendText(bob, d1, "chào");
        openDirect(alice, carol); // chưa nhắn → không phải ứng viên
        getAs(alice, "/api/v1/conversations/groups/" + gid + "/invite-candidates").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].user_id", is(bob.getUserId().intValue())));
        getAs(alice, "/api/v1/conversations/" + gid + "/invite-candidates?q=zzz").andExpect(jsonPath("$.items", hasSize(0)));
        getAs(alice, "/api/v1/conversations/" + gid + "/invite-candidates?q=bob").andExpect(jsonPath("$.items", hasSize(1)));
        getAs(dave, "/api/v1/conversations/groups/" + gid + "/invite-candidates").andExpect(status().isForbidden());
        getAs(alice, "/api/v1/conversations/groups/" + d1 + "/invite-candidates").andExpect(status().isBadRequest());
        join(alice, gid, bob);
        getAs(alice, "/api/v1/conversations/groups/" + gid + "/invite-candidates").andExpect(jsonPath("$.items", hasSize(0)));
    }
}
