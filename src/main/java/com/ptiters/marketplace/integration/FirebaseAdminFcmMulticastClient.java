package com.ptiters.marketplace.integration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Triển khai {@link FcmMulticastClient} bằng Firebase Admin SDK, cấu hình từ
 * {@code FIREBASE_CREDENTIALS_JSON} hoặc {@code FIREBASE_CREDENTIALS_PATH}.
 */
@Component
@ConditionalOnExpression(
        "!'${FIREBASE_CREDENTIALS_JSON:}'.isBlank() || !'${FIREBASE_CREDENTIALS_PATH:}'.isBlank()"
)
public class FirebaseAdminFcmMulticastClient implements FcmMulticastClient {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAdminFcmMulticastClient.class);
    private static final int FCM_MAX_BATCH_SIZE = 500;

    private final String credentialsJson;
    private final String credentialsPath;
    private final Object initLock = new Object();

    public FirebaseAdminFcmMulticastClient(
            @Value("${FIREBASE_CREDENTIALS_JSON:}") String credentialsJson,
            @Value("${FIREBASE_CREDENTIALS_PATH:}") String credentialsPath) {
        this.credentialsJson = credentialsJson == null ? "" : credentialsJson.trim();
        this.credentialsPath = credentialsPath == null ? "" : credentialsPath.trim();
    }

    @PostConstruct
    void initOnStartup() {
        ensureInitialized();
    }

    @Override
    public FcmMulticastResult sendMulticast(List<String> tokens, String title, String body, Map<String, String> data) {
        if (tokens == null || tokens.isEmpty()) {
            return FcmMulticastResult.empty();
        }
        FirebaseApp app = ensureInitialized();
        if (app == null) {
            log.warn("Firebase Admin SDK is not initialized. Skipping push notification.");
            return FcmMulticastResult.empty();
        }

        int totalSuccess = 0;
        List<String> staleTokens = new ArrayList<>();
        FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);

        for (int start = 0; start < tokens.size(); start += FCM_MAX_BATCH_SIZE) {
            List<String> batch = tokens.subList(start, Math.min(start + FCM_MAX_BATCH_SIZE, tokens.size()));
            try {
                MulticastMessage message = buildMulticastMessage(batch, title, body, data);
                BatchResponse response = messaging.sendEachForMulticast(message);
                totalSuccess += response.getSuccessCount();
                if (response.getFailureCount() > 0) {
                    List<SendResponse> responses = response.getResponses();
                    for (int i = 0; i < responses.size() && i < batch.size(); i++) {
                        SendResponse resp = responses.get(i);
                        if (!resp.isSuccessful() && isStaleTokenError(resp.getException())) {
                            staleTokens.add(batch.get(i));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error dispatching FCM multicast push batch: {}", e.getMessage());
            }
        }
        return new FcmMulticastResult(totalSuccess, staleTokens);
    }

    static MulticastMessage buildMulticastMessage(
            List<String> tokens,
            String title,
            String body,
            Map<String, String> data) {
        Map<String, String> safeData = new LinkedHashMap<>();
        if (data != null) {
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    safeData.put(entry.getKey(), entry.getValue());
                }
            }
        }

        String safeTitle = title == null ? "" : title;
        String safeBody = body == null ? "" : body;
        String tag = firstNonBlank(safeData.get("notification_id"), safeData.get("conversation_id"), "ptit-notification");

        return MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(safeTitle)
                        .setBody(safeBody)
                        .build())
                .putAllData(safeData)
                .setWebpushConfig(WebpushConfig.builder()
                        .setNotification(WebpushNotification.builder()
                                .setTitle(safeTitle)
                                .setBody(safeBody)
                                .setIcon("/logo.svg")
                                .setBadge("/logo.svg")
                                .setTag(tag)
                                .build())
                        .build())
                .build();
    }

    static boolean isStaleTokenError(FirebaseMessagingException ex) {
        if (ex == null) {
            return false;
        }
        MessagingErrorCode code = ex.getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
            return true;
        }
        String errorCode = ex.getErrorCode() != null ? ex.getErrorCode().name().toLowerCase(Locale.ROOT) : "";
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase(Locale.ROOT) : "";
        return errorCode.contains("not-registered")
                || msg.contains("not-registered")
                || msg.contains("unregistered")
                || msg.contains("invalid registration");
    }

    private FirebaseApp ensureInitialized() {
        List<FirebaseApp> apps = FirebaseApp.getApps();
        if (!apps.isEmpty()) {
            return apps.get(0);
        }
        synchronized (initLock) {
            apps = FirebaseApp.getApps();
            if (!apps.isEmpty()) {
                return apps.get(0);
            }
            if (!credentialsJson.isEmpty()) {
                try (InputStream is = new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(is))
                            .build();
                    FirebaseApp app = FirebaseApp.initializeApp(options);
                    log.info("Firebase Admin SDK successfully initialized from FIREBASE_CREDENTIALS_JSON.");
                    return app;
                } catch (Exception e) {
                    // Không log nội dung JSON hoặc private key
                    log.error("Error initializing Firebase Admin SDK from JSON: {}", e.getClass().getSimpleName());
                    return null;
                }
            }
            if (!credentialsPath.isEmpty()) {
                try (InputStream is = Files.newInputStream(Path.of(credentialsPath))) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(is))
                            .build();
                    FirebaseApp app = FirebaseApp.initializeApp(options);
                    log.info("Firebase Admin SDK successfully initialized from FIREBASE_CREDENTIALS_PATH.");
                    return app;
                } catch (Exception e) {
                    log.error("Error initializing Firebase Admin SDK from path: {}", e.getClass().getSimpleName());
                    return null;
                }
            }
            return null;
        }
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return fallback;
    }
}
