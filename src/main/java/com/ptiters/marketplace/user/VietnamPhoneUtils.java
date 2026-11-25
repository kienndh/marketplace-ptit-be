package com.ptiters.marketplace.user;

import com.ptiters.marketplace.exception.ApiException;

import java.util.regex.Pattern;

public final class VietnamPhoneUtils {

    private static final Pattern VN_MOBILE_REGEX = Pattern.compile("^0[35789]\\d{8}$");
    private static final Pattern ALLOWED_PHONE_CHARS = Pattern.compile("^\\+?[0-9\\s.-]+$");

    private VietnamPhoneUtils() {
    }

    /**
     * Normalizes and validates a Vietnamese mobile phone number.
     * Returns normalized 10-digit format '0XXXXXXXXX' if valid.
     * Throws ApiException.badRequest if invalid format.
     * If phone is null or empty/whitespace, returns null.
     */
    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        String raw = phone.trim();
        if (raw.isEmpty()) {
            return null;
        }

        if (!ALLOWED_PHONE_CHARS.matcher(raw).matches()) {
            throw ApiException.badRequest("Số điện thoại không hợp lệ. Vui lòng nhập số di động Việt Nam 10 chữ số.");
        }

        String cleaned = raw.replaceAll("[\\s.-]", "");
        if (cleaned.startsWith("+84")) {
            cleaned = "0" + cleaned.substring(3);
        } else if (cleaned.startsWith("84") && cleaned.length() == 11) {
            cleaned = "0" + cleaned.substring(2);
        }

        if (!VN_MOBILE_REGEX.matcher(cleaned).matches()) {
            throw ApiException.badRequest("Số điện thoại không hợp lệ. Vui lòng nhập số di động Việt Nam 10 chữ số.");
        }

        return cleaned;
    }
}
