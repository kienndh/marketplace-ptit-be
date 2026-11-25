package com.ptiters.marketplace.report;

import com.ptiters.marketplace.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Mở luồng tải bằng chứng từ storage cho admin, có bảo vệ SSRF (chỉ https + res.cloudinary.com, KHÔNG theo redirect).
 * Tương ứng {@code validate_storage_url_safety} + {@code stream_admin_evidence} của Python.
 */
@Component
public class ReportEvidenceStreamer {

    private static final Logger log = LoggerFactory.getLogger(ReportEvidenceStreamer.class);
    private static final Pattern RANGE = Pattern.compile("^bytes=\\d*-\\d*$");
    private static final String TRUSTED_HOST = "res.cloudinary.com";

    /** Kết quả mở luồng: status upstream (200/206), header cần chuyển tiếp, thân dữ liệu. */
    public record Opened(int status, String contentRange, String contentLength, InputStream body) {}

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    static void validateStorageUrlSafety(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Đường dẫn tệp không tồn tại.");
        }
        URI uri;
        try {
            uri = URI.create(targetUrl);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Đường dẫn tệp không tồn tại.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Giao thức lưu trữ không an toàn.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!(host.equals(TRUSTED_HOST) || host.endsWith("." + TRUSTED_HOST))) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Nguồn lưu trữ tệp không được tin cậy.");
        }
    }

    public Opened open(String url, boolean video, String rangeHeader) {
        validateStorageUrlSafety(url);
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET();
        if (video && rangeHeader != null && RANGE.matcher(rangeHeader.strip()).matches()) {
            req.header("Range", rangeHeader.strip());
        }
        try {
            HttpResponse<InputStream> resp = client.send(req.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 400) {
                try {
                    resp.body().close();
                } catch (IOException ignored) {
                    // đóng best-effort
                }
                log.error("Storage upstream returned {} for evidence", resp.statusCode());
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Không thể tải tệp minh chứng từ kho lưu trữ.");
            }
            String contentLength = resp.headers().firstValue("content-length")
                    .filter(v -> v.chars().allMatch(Character::isDigit) && !v.equals("0")).orElse(null);
            return new Opened(resp.statusCode(), resp.headers().firstValue("content-range").orElse(null),
                    contentLength, resp.body());
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to connect to upstream storage for evidence: {}", e.toString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Lỗi kết nối đến kho lưu trữ minh chứng.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Lỗi kết nối đến kho lưu trữ minh chứng.");
        }
    }
}
