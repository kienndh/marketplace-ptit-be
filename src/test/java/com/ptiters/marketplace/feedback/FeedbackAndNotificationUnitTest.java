package com.ptiters.marketplace.feedback;

import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.integration.MediaStorage;
import com.ptiters.marketplace.integration.MediaStorageNotConfiguredException;
import com.ptiters.marketplace.notification.DeviceTokenStore;
import com.ptiters.marketplace.notification.NotificationWriter;
import com.ptiters.marketplace.realtime.MediaValidator;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserRole;
import com.ptiters.marketplace.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeedbackAndNotificationUnitTest {

    private static final byte[] VALID_JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01
    };
    private static final byte[] VALID_MP4 = new byte[]{
            0x00, 0x00, 0x00, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0x00
    };

    @Test
    @DisplayName("DeviceTokenStore xử lý an toàn khi thiếu bảng user_device_tokens (dev DB)")
    void deviceTokenStoreHandlesMissingTableSafely() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT to_regclass('user_device_tokens') IS NOT NULL", Boolean.class))
                .thenReturn(false);

        DeviceTokenStore store = new DeviceTokenStore(jdbc);
        assertFalse(store.isTableAvailable());
        assertEquals(List.of(), store.getTokensForUser(1L));
        assertFalse(store.deleteToken(1L, "tok"));
        assertDoesNotThrow(() -> store.deleteStaleTokens(List.of("tok")));

        ApiException ex = assertThrows(ApiException.class, () -> store.upsertToken(1L, "tok", "Chrome"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    @DisplayName("sanitizeFeedbackContent chuẩn hóa NFC, giữ emoji và từ chối control chars")
    void sanitizeFeedbackContentValidatesProperly() {
        String valid = FeedbackService.sanitizeFeedbackContent("  Góp ý rất hay 💛  ");
        assertEquals("Góp ý rất hay 💛", valid);

        ApiException shortEx = assertThrows(ApiException.class,
                () -> FeedbackService.sanitizeFeedbackContent("  ab  "));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, shortEx.getStatus());

        ApiException longEx = assertThrows(ApiException.class,
                () -> FeedbackService.sanitizeFeedbackContent("A".repeat(501)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, longEx.getStatus());

        ApiException ctrlEx = assertThrows(ApiException.class,
                () -> FeedbackService.sanitizeFeedbackContent("Hello\u0007World"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ctrlEx.getStatus());
    }

    @Test
    @DisplayName("submitFeedback dọn dẹp asset đã upload (compensation) khi video > 60s hoặc storage lỗi")
    void submitFeedbackCompensatesUploadedAssetsOnFailure() {
        MediaStorage storage = mock(MediaStorage.class);
        FeedbackQueryRepository queryRepo = mock(FeedbackQueryRepository.class);
        FeedbackService service = new FeedbackService(
                mock(SiteFeedbackRepository.class),
                mock(SiteFeedbackAttachmentRepository.class),
                mock(SupportContributionRepository.class),
                mock(SupportClaimRepository.class),
                queryRepo,
                mock(UserRepository.class),
                mock(NotificationWriter.class),
                new MediaValidator(),
                storage,
                mock(TransactionTemplate.class),
                new MockEnvironment()
        );

        User activeUser = new User();
        activeUser.setUserId(10L);
        activeUser.setStatus(UserStatus.ACTIVE);
        activeUser.setRole(UserRole.STUDENT);

        // 1. Ảnh upload xong, video vượt 60s -> destroyAsset phải được gọi cho cả ảnh và video
        when(storage.uploadFeedbackAttachment(any(), eq("a.jpg"), eq("image")))
                .thenReturn(new MediaStorage.UploadedAsset("https://res.cloudinary.com/demo/a.jpg", "pid_img", "image", 12L, "jpg", null));
        when(storage.uploadFeedbackAttachment(any(), eq("v.mp4"), eq("video")))
                .thenReturn(new MediaStorage.UploadedAsset("https://res.cloudinary.com/demo/v.mp4", "pid_vid", "video", 13L, "mp4", 65.0));

        ApiException durEx = assertThrows(ApiException.class, () -> service.submitFeedback(
                activeUser,
                "Video quá dài cần bồi hoàn.",
                List.of(new MockMultipartFile("images", "a.jpg", "image/jpeg", VALID_JPEG)),
                new MockMultipartFile("video", "v.mp4", "video/mp4", VALID_MP4)
        ));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, durEx.getStatus());
        verify(storage).destroyAsset("pid_img", "image");
        verify(storage).destroyAsset("pid_vid", "video");

        // 2. Storage chưa cấu hình -> trả 503 Service Unavailable
        reset(storage);
        when(storage.uploadFeedbackAttachment(any(), any(), any()))
                .thenThrow(new MediaStorageNotConfiguredException());
        ApiException cfgEx = assertThrows(ApiException.class, () -> service.submitFeedback(
                activeUser,
                "Kiểm tra storage chưa cấu hình.",
                List.of(new MockMultipartFile("images", "a.jpg", "image/jpeg", VALID_JPEG)),
                null
        ));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, cfgEx.getStatus());
    }

    @Test
    @DisplayName("getSupportInfo phân giải creator theo PROVIDER_UID / EMAIL và ẩn khi không ACTIVE")
    void getSupportInfoResolvesCreatorSafely() {
        FeedbackQueryRepository queryRepo = mock(FeedbackQueryRepository.class);
        MockEnvironment env = new MockEnvironment()
                .withProperty("COMMUNITY_CREATOR_PROVIDER_UID", "creator_fb_uid");

        User creator = new User();
        creator.setUserId(99L);
        creator.setFullName("Nguyễn Khắc Thịnh");
        creator.setStatus(UserStatus.ACTIVE);
        when(queryRepo.findUserByFirebaseProviderUid("creator_fb_uid")).thenReturn(Optional.of(creator));

        FeedbackService service = new FeedbackService(
                mock(SiteFeedbackRepository.class),
                mock(SiteFeedbackAttachmentRepository.class),
                mock(SupportContributionRepository.class),
                mock(SupportClaimRepository.class),
                queryRepo,
                mock(UserRepository.class),
                mock(NotificationWriter.class),
                new MediaValidator(),
                mock(MediaStorage.class),
                mock(TransactionTemplate.class),
                env
        );

        SupportResponse res = service.getSupportInfo();
        assertNotNull(res.creator());
        assertEquals(99L, res.creator().userId());
        assertEquals("/users/99", res.profilePath());
        assertEquals("/support/nguyen-khac-thinh-vietqr.png", res.fixedQrUrl());

        creator.setStatus(UserStatus.BANNED);
        SupportResponse bannedRes = service.getSupportInfo();
        assertNull(bannedRes.creator());
        assertNull(bannedRes.profilePath());
    }
}
