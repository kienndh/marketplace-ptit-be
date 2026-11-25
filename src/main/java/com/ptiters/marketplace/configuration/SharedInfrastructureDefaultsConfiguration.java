package com.ptiters.marketplace.configuration;

import com.ptiters.marketplace.integration.LocalMediaStorage;
import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.integration.UnconfiguredMediaStorage;
import com.ptiters.marketplace.notification.NoOpPushNotificationSender;
import com.ptiters.marketplace.notification.PushNotificationSender;
import com.ptiters.marketplace.realtime.AuctionEventPublisher;
import com.ptiters.marketplace.realtime.NoOpAuctionEventPublisher;
import com.ptiters.marketplace.realtime.NoOpPresenceService;
import com.ptiters.marketplace.realtime.NoOpUserEventPublisher;
import com.ptiters.marketplace.realtime.PresenceService;
import com.ptiters.marketplace.realtime.UserEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Bean MẶC ĐỊNH AN TOÀN cho hạ tầng dùng chung (Wave 0). Mỗi bean chỉ được tạo khi chưa có bean thật cùng kiểu,
 * nên Track E chỉ cần khai báo bean thật (@Component/@Service/@Configuration) ở package của mình — KHÔNG sửa file này.
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)} bảo đảm class này được xử lý sau mọi @Configuration khác để
 * {@code @ConditionalOnMissingBean} nhìn thấy bean thật.
 */
@Configuration
@Order(Ordered.LOWEST_PRECEDENCE)
public class SharedInfrastructureDefaultsConfiguration {

    @Bean
    @ConditionalOnMissingBean(PushNotificationSender.class)
    public PushNotificationSender noOpPushNotificationSender() {
        return new NoOpPushNotificationSender();
    }

    @Bean
    @ConditionalOnMissingBean(UserEventPublisher.class)
    public UserEventPublisher noOpUserEventPublisher() {
        return new NoOpUserEventPublisher();
    }

    @Bean
    @ConditionalOnMissingBean(AuctionEventPublisher.class)
    public AuctionEventPublisher noOpAuctionEventPublisher() {
        return new NoOpAuctionEventPublisher();
    }

    @Bean
    @ConditionalOnMissingBean(PresenceService.class)
    public PresenceService noOpPresenceService() {
        return new NoOpPresenceService();
    }

    /** Profile test: lưu tệp vào thư mục tạm, URL giả lập, không gọi mạng. */
    @Bean
    @Profile("test")
    @ConditionalOnMissingBean(MediaStorage.class)
    public MediaStorage localMediaStorage() {
        return LocalMediaStorage.inTempDirectory();
    }

    /** Profile khác: chưa cấu hình storage → upload ném MediaStorageNotConfiguredException. */
    @Bean
    @Profile("!test")
    @ConditionalOnMissingBean(MediaStorage.class)
    public MediaStorage unconfiguredMediaStorage() {
        return new UnconfiguredMediaStorage();
    }
}
