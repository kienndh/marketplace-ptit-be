package com.ptiters.marketplace.integration;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FirebasePushService {

    public int sendPushNotification(List<String> deviceTokens, String title, String body, Map<String, String> data) {
        throw new UnsupportedOperationException("TODO: Implement Firebase Cloud Messaging push");
    }
}
