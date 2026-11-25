package com.ptiters.marketplace.configuration;

import com.ptiters.marketplace.integration.LocalMediaStorage;
import com.ptiters.marketplace.integration.MediaStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CHỈ profile {@code e2e}: lưu tệp upload vào thư mục tạm và phục vụ lại qua {@code /e2e-media/**}
 * để React hiển thị được ảnh. Không dùng Cloudinary. Khi có {@code CLOUDINARY_*} thì bean thật được ưu tiên.
 */
@Configuration
@Profile("e2e")
public class E2eLocalMediaConfiguration {

    private static final Path MEDIA_DIR = createDir();

    private static Path createDir() {
        try {
            return Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "marketplace-e2e-media"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Bean
    @ConditionalOnMissingBean(MediaStorage.class)
    public MediaStorage e2eLocalMediaStorage(
            @Value("${marketplace.e2e.media-base-url:http://localhost:8080/e2e-media/}") String baseUrl) {
        com.ptiters.marketplace.report.ReportEvidenceStreamer.trustLocalPrefixForE2e(baseUrl);
        return new LocalMediaStorage(MEDIA_DIR, baseUrl);
    }

    /** Phục vụ tệp đã lưu; đoán Content-Type theo chữ ký (tệp lưu không có đuôi, trình duyệt chặn khi có nosniff). */
    @org.springframework.web.bind.annotation.RestController
    @Profile("e2e")
    static class E2eMediaController {
        @org.springframework.web.bind.annotation.GetMapping("/e2e-media/**")
        public org.springframework.http.ResponseEntity<byte[]> get(jakarta.servlet.http.HttpServletRequest req) throws IOException {
            String rel = req.getRequestURI().substring("/e2e-media/".length());
            Path f = MEDIA_DIR.resolve(rel).normalize();
            if (!f.startsWith(MEDIA_DIR) || !Files.isRegularFile(f)) {
                return org.springframework.http.ResponseEntity.notFound().build();
            }
            byte[] b = Files.readAllBytes(f);
            String ct = "application/octet-stream";
            if (b.length > 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P') ct = "image/png";
            else if (b.length > 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8) ct = "image/jpeg";
            else if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[8] == 'W') ct = "image/webp";
            else if (b.length > 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') ct = "video/mp4";
            else if (b.length > 4 && (b[0] & 0xff) == 0x1A && (b[1] & 0xff) == 0x45) ct = "video/webm";
            return org.springframework.http.ResponseEntity.ok().header("Content-Type", ct).body(b);
        }
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain e2eMediaFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/e2e-media/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
