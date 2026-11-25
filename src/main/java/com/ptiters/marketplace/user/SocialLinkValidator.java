package com.ptiters.marketplace.user;

import com.ptiters.marketplace.exception.ApiException;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SocialLinkValidator {

    private static final Set<String> FB_DOMAINS = Set.of(
            "facebook.com", "www.facebook.com", "m.facebook.com", "fb.com", "www.fb.com"
    );
    private static final Set<String> IG_DOMAINS = Set.of(
            "instagram.com", "www.instagram.com", "instagr.am"
    );
    private static final Set<String> TIKTOK_DOMAINS = Set.of(
            "tiktok.com", "www.tiktok.com", "vt.tiktok.com"
    );
    private static final Set<String> ZALO_DOMAINS = Set.of(
            "zalo.me", "chat.zalo.me"
    );
    private static final Set<String> LINKEDIN_DOMAINS = Set.of(
            "linkedin.com", "www.linkedin.com", "vn.linkedin.com"
    );

    private SocialLinkValidator() {
    }

    public static void validateLinks(List<SocialLink> links) {
        if (links == null || links.isEmpty()) {
            return;
        }

        for (SocialLink link : links) {
            if (link == null) {
                continue;
            }
            String platform = link.getPlatform();
            String url = link.getUrl();

            if (platform == null || platform.isBlank()) {
                throw ApiException.badRequest("Nền tảng mạng xã hội không được để trống.");
            }
            if (url == null || url.isBlank()) {
                throw ApiException.badRequest("URL không được để trống.");
            }

            String trimmedUrl = url.trim();
            if (!trimmedUrl.startsWith("https://") && !trimmedUrl.startsWith("http://")) {
                throw ApiException.badRequest("URL phải bắt đầu bằng https:// hoặc http://.");
            }

            String host;
            try {
                URI uri = URI.create(trimmedUrl);
                host = uri.getHost();
                if (host == null || host.isBlank()) {
                    throw ApiException.badRequest("Định dạng URL không hợp lệ.");
                }
                host = host.toLowerCase(Locale.ROOT);
            } catch (Exception e) {
                throw ApiException.badRequest("Định dạng URL không hợp lệ.");
            }

            String upperPlatform = platform.trim().toUpperCase(Locale.ROOT);
            switch (upperPlatform) {
                case "FACEBOOK" -> {
                    if (!FB_DOMAINS.contains(host)) {
                        throw ApiException.badRequest("Liên kết không thuộc Facebook.");
                    }
                }
                case "INSTAGRAM" -> {
                    if (!IG_DOMAINS.contains(host)) {
                        throw ApiException.badRequest("Liên kết không thuộc Instagram.");
                    }
                }
                case "TIKTOK" -> {
                    if (!TIKTOK_DOMAINS.contains(host)) {
                        throw ApiException.badRequest("Liên kết không thuộc TikTok.");
                    }
                }
                case "ZALO" -> {
                    if (!ZALO_DOMAINS.contains(host)) {
                        throw ApiException.badRequest("Liên kết không thuộc Zalo.");
                    }
                }
                case "LINKEDIN" -> {
                    if (!LINKEDIN_DOMAINS.contains(host)) {
                        throw ApiException.badRequest("Liên kết không thuộc LinkedIn.");
                    }
                }
                default -> throw ApiException.badRequest("Nền tảng mạng xã hội không được hỗ trợ: " + platform);
            }
        }
    }
}
