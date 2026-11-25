package com.ptiters.marketplace.integration;

import java.util.List;
import java.util.Map;

/**
 * Cổng gửi FCM multicast tách khỏi Firebase Admin SDK để dễ kiểm thử với client giả
 * mà không bao giờ gọi mạng Firebase thật trong test.
 */
public interface FcmMulticastClient {

    record FcmMulticastResult(int successCount, List<String> staleTokens) {
        public static FcmMulticastResult empty() {
            return new FcmMulticastResult(0, List.of());
        }
    }

    FcmMulticastResult sendMulticast(List<String> tokens, String title, String body, Map<String, String> data);
}
