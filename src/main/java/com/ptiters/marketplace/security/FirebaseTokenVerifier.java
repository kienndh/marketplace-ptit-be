package com.ptiters.marketplace.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.ptiters.marketplace.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
public class FirebaseTokenVerifier {

    // null nếu chưa cấu hình được credentials
    private FirebaseAuth firebaseAuth;

    public FirebaseTokenVerifier(@Value("${app.firebase.credentials-path:}") String credentialsPath) {
        this.firebaseAuth = createFirebaseAuth(credentialsPath);
    }

    private FirebaseAuth createFirebaseAuth(String credentialsPath) {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("FIREBASE_CREDENTIALS_PATH chưa được cấu hình: các request có token sẽ trả 503.");
            return null;
        }
        try (InputStream in = new FileInputStream(credentialsPath)) {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(in))
                        .build();
                FirebaseApp.initializeApp(options);
            }
            return FirebaseAuth.getInstance();
        } catch (IOException e) {
            log.error("Không đọc được file Firebase credentials: {}", credentialsPath, e);
            return null;
        }
    }

    public FirebaseToken verify(String idToken) {
        if (firebaseAuth == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Firebase Admin SDK is not configured.");
        }
        try {
            return firebaseAuth.verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            log.warn("Firebase token verification failed: {}", e.getAuthErrorCode());
            if (e.getAuthErrorCode() == AuthErrorCode.EXPIRED_ID_TOKEN) {
                throw ApiException.unauthorized("Firebase token has expired.");
            }
            throw ApiException.unauthorized("Invalid Firebase token.");
        } catch (IllegalArgumentException e) {
            throw ApiException.unauthorized("Invalid Firebase token.");
        }
    }
}