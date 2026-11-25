package com.ptiters.marketplace.auction;

/** Python: {@code app/core/privacy.anonymize_name(full_name, default="Người đặt giá")}. */
public final class AuctionNames {

    public static final String DEFAULT_BIDDER = "Người đặt giá";

    private AuctionNames() {
    }

    /** "Nguyễn Khắc Thịnh" → "Nguyễn K. T."; một từ giữ nguyên; rỗng → mặc định. */
    public static String anonymize(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return DEFAULT_BIDDER;
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(' ').append(parts[i].substring(0, parts[i].offsetByCodePoints(0, 1)).toUpperCase()).append('.');
            }
        }
        return sb.toString().trim();
    }
}
