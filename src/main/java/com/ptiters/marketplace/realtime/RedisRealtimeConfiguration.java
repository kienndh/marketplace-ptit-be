package com.ptiters.marketplace.realtime;

import io.lettuce.core.RedisCredentials;
import io.lettuce.core.RedisCredentialsProvider;
import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;

/**
 * Cấu hình Redis cho Presence &amp; Pub/Sub đa instance.
 * Chỉ kích hoạt khi có cấu hình {@code REDIS_HOST}/{@code REDIS_URL}/{@code spring.data.redis.*};
 * khi không cấu hình, toàn bộ bean fallback về {@code NoOp*} trong {@code SharedInfrastructureDefaultsConfiguration}.
 */
@Configuration
@ConditionalOnExpression(
        "!'${REDIS_HOST:}'.isBlank() || !'${REDIS_URL:}'.isBlank() "
        + "|| !'${spring.data.redis.host:}'.isBlank() || !'${spring.data.redis.url:}'.isBlank()"
)
public class RedisRealtimeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisRealtimeConfiguration.class);
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(2);

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.url:}") String springRedisUrl,
            @Value("${REDIS_URL:}") String envRedisUrl,
            @Value("${spring.data.redis.host:}") String springHost,
            @Value("${REDIS_HOST:localhost}") String envHost,
            @Value("${spring.data.redis.port:0}") int springPort,
            @Value("${REDIS_PORT:6379}") int envPort,
            @Value("${spring.data.redis.database:-1}") int springDb,
            @Value("${REDIS_DB:0}") int envDb,
            @Value("${spring.data.redis.password:}") String springPassword,
            @Value("${REDIS_PASSWORD:}") String envPassword) {

        String resolvedUrl = firstNonBlank(springRedisUrl, envRedisUrl);
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientBuilder =
                LettuceClientConfiguration.builder().commandTimeout(COMMAND_TIMEOUT);

        if (resolvedUrl != null) {
            RedisURI uri = RedisURI.create(resolvedUrl);
            standalone.setHostName(uri.getHost());
            standalone.setPort(uri.getPort());
            standalone.setDatabase(uri.getDatabase());
            if (uri.getCredentialsProvider() instanceof RedisCredentialsProvider.ImmediateRedisCredentialsProvider imm) {
                RedisCredentials creds = imm.resolveCredentialsNow();
                if (creds.hasUsername()) {
                    standalone.setUsername(creds.getUsername());
                }
                if (creds.hasPassword()) {
                    standalone.setPassword(RedisPassword.of(creds.getPassword()));
                }
            }
            if (uri.isSsl()) {
                clientBuilder.useSsl();
            }
            log.info("Configuring RedisConnectionFactory from URL (host={}, port={}, db={})",
                    uri.getHost(), uri.getPort(), uri.getDatabase());
        } else {
            String host = firstNonBlank(springHost, envHost);
            if (host == null) {
                host = "localhost";
            }
            int port = springPort > 0 ? springPort : envPort;
            int db = springDb >= 0 ? springDb : envDb;
            String password = firstNonBlank(springPassword, envPassword);

            standalone.setHostName(host);
            standalone.setPort(port);
            standalone.setDatabase(db);
            if (password != null) {
                standalone.setPassword(RedisPassword.of(password));
            }
            log.info("Configuring RedisConnectionFactory (host={}, port={}, db={})", host, port, db);
        }

        return new LettuceConnectionFactory(standalone, clientBuilder.build());
    }

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(10_000L);
        container.setRecoveryBackoff(backOff);
        container.setErrorHandler(err ->
                log.warn("Redis pub/sub listener error: {}", err != null ? err.getMessage() : "unknown"));
        return container;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
