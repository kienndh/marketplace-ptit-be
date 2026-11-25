package com.ptiters.marketplace.realtime;

import com.ptiters.marketplace.configuration.SharedInfrastructureDefaultsConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRealtimeIntegrationTest {

    private final List<String> keysToCleanup = new ArrayList<>();

    @AfterEach
    void cleanupRedisKeys() {
        if (keysToCleanup.isEmpty() || !isLocalRedisReachable()) {
            keysToCleanup.clear();
            return;
        }
        new ApplicationContextRunner()
                .withPropertyValues("REDIS_HOST=localhost", "REDIS_PORT=6379")
                .withUserConfiguration(RedisRealtimeConfiguration.class)
                .run(ctx -> {
                    StringRedisTemplate tpl = ctx.getBean(StringRedisTemplate.class);
                    tpl.delete(keysToCleanup);
                });
        keysToCleanup.clear();
    }

    private static boolean isLocalRedisReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 6379), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Thiếu cấu hình Redis -> fallback NoOp*; có REDIS_HOST -> kích hoạt bean Redis thật")
    void conditionalBeansFallbackWhenUnconfiguredAndActivateWhenConfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        UserConnectionManager.class,
                        AuctionConnectionManager.class,
                        RedisRealtimeConfiguration.class,
                        RedisPubSubBridge.class,
                        RedisPresenceService.class,
                        RedisUserEventPublisher.class,
                        RedisAuctionEventPublisher.class,
                        SharedInfrastructureDefaultsConfiguration.class
                )
                .run(ctx -> {
                    assertInstanceOf(NoOpPresenceService.class, ctx.getBean(PresenceService.class));
                    assertInstanceOf(NoOpUserEventPublisher.class, ctx.getBean(UserEventPublisher.class));
                    assertInstanceOf(NoOpAuctionEventPublisher.class, ctx.getBean(AuctionEventPublisher.class));
                    assertFalse(ctx.containsBean("redisPubSubBridge"));
                });

        new ApplicationContextRunner()
                .withPropertyValues("REDIS_HOST=localhost", "REDIS_PORT=6379")
                .withUserConfiguration(
                        UserConnectionManager.class,
                        AuctionConnectionManager.class,
                        RedisRealtimeConfiguration.class,
                        RedisPubSubBridge.class,
                        RedisPresenceService.class,
                        RedisUserEventPublisher.class,
                        RedisAuctionEventPublisher.class,
                        SharedInfrastructureDefaultsConfiguration.class
                )
                .run(ctx -> {
                    assertInstanceOf(RedisPresenceService.class, ctx.getBean(PresenceService.class));
                    assertInstanceOf(RedisUserEventPublisher.class, ctx.getBean(UserEventPublisher.class));
                    assertInstanceOf(RedisAuctionEventPublisher.class, ctx.getBean(AuctionEventPublisher.class));
                    assertTrue(ctx.containsBean("redisPubSubBridge"));
                });
    }

    @Test
    @DisplayName("PresenceService ghi key presence:user:{id} TTL 90s và batch MGET trên Redis thật")
    void presenceWorksOnRealRedis() {
        Assumptions.assumeTrue(isLocalRedisReachable(), "Redis sm_redis (localhost:6379) is not reachable");

        long userA = 900_001L;
        long userB = 900_002L;
        keysToCleanup.add(RedisPresenceService.presenceKey(userA));
        keysToCleanup.add(RedisPresenceService.presenceKey(userB));

        new ApplicationContextRunner()
                .withPropertyValues("REDIS_HOST=localhost", "REDIS_PORT=6379")
                .withUserConfiguration(RedisRealtimeConfiguration.class, RedisPresenceService.class)
                .run(ctx -> {
                    PresenceService presence = ctx.getBean(PresenceService.class);
                    StringRedisTemplate tpl = ctx.getBean(StringRedisTemplate.class);
                    tpl.delete(List.of(RedisPresenceService.presenceKey(userA), RedisPresenceService.presenceKey(userB)));

                    assertTrue(presence.recordUserPresence(userA));
                    assertEquals("1", tpl.opsForValue().get(RedisPresenceService.presenceKey(userA)));
                    Long ttl = tpl.getExpire(RedisPresenceService.presenceKey(userA));
                    assertNotNull(ttl);
                    assertTrue(ttl > 0 && ttl <= 90, "TTL must be bounded to 90s, was " + ttl);

                    PresenceService.PresenceSnapshot snap = presence.getUsersPresence(List.of(userA, userB));
                    assertTrue(snap.available());
                    assertEquals(Set.of(userA), snap.onlineUserIds());

                    PresenceService.PresenceSnapshot emptySnap = presence.getUsersPresence(List.of());
                    assertTrue(emptySnap.available());
                    assertTrue(emptySnap.onlineUserIds().isEmpty());
                });
    }

    @Test
    @DisplayName("PresenceService trả available=false và không ném lỗi khi Redis lỗi")
    void presenceFailsOpenWhenRedisErrors() {
        StringRedisTemplate brokenTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> brokenOps = mock(ValueOperations.class);
        when(brokenTemplate.opsForValue()).thenReturn(brokenOps);
        Mockito.doThrow(new RuntimeException("Redis connection refused"))
                .when(brokenOps).set(any(), any(), any(java.time.Duration.class));
        when(brokenOps.multiGet(any())).thenThrow(new RuntimeException("Redis timeout"));

        RedisPresenceService service = new RedisPresenceService(brokenTemplate);
        assertFalse(service.recordUserPresence(1L));

        PresenceService.PresenceSnapshot snap = service.getUsersPresence(List.of(1L, 2L));
        assertFalse(snap.available());
        assertTrue(snap.onlineUserIds().isEmpty());
    }

    @Test
    @DisplayName("UserEventPublisher & AuctionEventPublisher pub/sub kênh user:{id}:events / auction:{id}:events với _meta.origin trên Redis thật")
    void pubSubEnvelopeAndLoopbackDedupWorkOnRealRedis() throws Exception {
        Assumptions.assumeTrue(isLocalRedisReachable(), "Redis sm_redis (localhost:6379) is not reachable");

        long userId = 900_010L;
        long auctionId = 900_020L;
        keysToCleanup.add(RedisPresenceService.presenceKey(userId));

        new ApplicationContextRunner()
                .withPropertyValues("REDIS_HOST=localhost", "REDIS_PORT=6379")
                .withUserConfiguration(
                        UserConnectionManager.class,
                        AuctionConnectionManager.class,
                        RedisRealtimeConfiguration.class,
                        RedisPubSubBridge.class,
                        RedisPresenceService.class,
                        RedisUserEventPublisher.class,
                        RedisAuctionEventPublisher.class
                )
                .run(ctx -> {
                    UserEventPublisher userPublisher = ctx.getBean(UserEventPublisher.class);
                    AuctionEventPublisher auctionPublisher = ctx.getBean(AuctionEventPublisher.class);
                    UserConnectionManager userConnMgr = ctx.getBean(UserConnectionManager.class);
                    AuctionConnectionManager auctionConnMgr = ctx.getBean(AuctionConnectionManager.class);
                    RedisPubSubBridge bridge = ctx.getBean(RedisPubSubBridge.class);
                    StringRedisTemplate tpl = ctx.getBean(StringRedisTemplate.class);
                    RedisMessageListenerContainer container = ctx.getBean(RedisMessageListenerContainer.class);

                    // 1. Raw external Redis subscriber inspects envelope published by Java
                    BlockingQueue<String> rawUserChannelMsgs = new LinkedBlockingQueue<>();
                    BlockingQueue<String> rawAuctionChannelMsgs = new LinkedBlockingQueue<>();
                    MessageListener userSpy = (Message msg, byte[] p) ->
                            rawUserChannelMsgs.offer(new String(msg.getBody(), StandardCharsets.UTF_8));
                    MessageListener auctionSpy = (Message msg, byte[] p) ->
                            rawAuctionChannelMsgs.offer(new String(msg.getBody(), StandardCharsets.UTF_8));
                    container.addMessageListener(userSpy, ChannelTopic.of(RedisPubSubBridge.userChannel(userId)));
                    container.addMessageListener(auctionSpy, ChannelTopic.of(RedisPubSubBridge.auctionChannel(auctionId)));
                    Thread.sleep(200);

                    // Connect mock local sockets
                    WebSocketSession userWs = mockOpenSession("ws-user-1");
                    WebSocketSession auctionWs = mockOpenSession("ws-auction-1");
                    userConnMgr.connect(userId, userWs);
                    auctionConnMgr.connect(auctionId, auctionWs);
                    Thread.sleep(200);

                    userPublisher.publishOrderCreated(userId, 777L);
                    String userEnvelopeJson = rawUserChannelMsgs.poll(3, TimeUnit.SECONDS);
                    assertNotNull(userEnvelopeJson, "Expected Redis pub/sub message on user:{id}:events");
                    assertTrue(userEnvelopeJson.contains("\"_meta\""));
                    assertTrue(userEnvelopeJson.contains(bridge.getInstanceId()));
                    assertTrue(userEnvelopeJson.contains("\"ORDER_CREATED\""));
                    assertTrue(userEnvelopeJson.contains("777"));

                    auctionPublisher.publishBidPlaced(
                            auctionId, new BigDecimal("50000"), new BigDecimal("5000"), 3L, Instant.parse("2026-11-25T00:00:00Z"));
                    String auctionEnvelopeJson = rawAuctionChannelMsgs.poll(3, TimeUnit.SECONDS);
                    assertNotNull(auctionEnvelopeJson, "Expected Redis pub/sub message on auction:{id}:events");
                    assertTrue(auctionEnvelopeJson.contains("\"BID_PLACED\""));
                    assertTrue(auctionEnvelopeJson.contains("\"55000\""));

                    // Give pub/sub listener time to process self-origin loopback and verify local ws got 1 message each (not 2)
                    Thread.sleep(200);
                    verify(userWs, Mockito.times(1)).sendMessage(any(TextMessage.class));
                    verify(auctionWs, Mockito.times(1)).sendMessage(any(TextMessage.class));

                    // 2. Simulate message from a remote instance -> must be delivered to local socket!
                    String remoteEvent = "{\"_meta\":{\"origin\":\"remote-node-2\"},\"payload\":{\"type\":\"ORDER_UPDATED\",\"order_id\":888}}";
                    tpl.convertAndSend(RedisPubSubBridge.userChannel(userId), remoteEvent);
                    Thread.sleep(300);
                    verify(userWs, Mockito.times(2)).sendMessage(any(TextMessage.class));

                    // 3. Simulate remote _DISCONNECT -> closes local socket with 1008!
                    verify(userWs, never()).close(any());
                    String remoteDisconnect = "{\"_meta\":{\"origin\":\"remote-node-2\"},\"payload\":{\"type\":\"_DISCONNECT\"}}";
                    tpl.convertAndSend(RedisPubSubBridge.userChannel(userId), remoteDisconnect);
                    Thread.sleep(300);
                    verify(userWs, atLeastOnce()).close(CloseStatus.POLICY_VIOLATION);

                    auctionConnMgr.disconnect(auctionId, auctionWs);
                    container.removeMessageListener(userSpy);
                    container.removeMessageListener(auctionSpy);
                });
    }

    private static WebSocketSession mockOpenSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
