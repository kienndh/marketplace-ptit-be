# BÁO CÁO PR: TRACK D1 — NOTIFICATION + FEEDBACK (+ SUPPORT)

- **Nhánh Git**: `track/D1-notification-feedback`
- **Worktree**: `D:\choptiters_java\marketplace-ptit-be-D1`
- **CSDL kiểm thử**: `marketplace_java_test_d1` (PostgreSQL 15.20, Docker `sm_postgres:5432`)
- **Ngày hoàn tất**: `2026-11-25`

---

## BƯỚC 1: KIỂM TOÁN RANH GIỚI WORKSPACE VÀ NHÁNH GIT (WORKSPACE & GIT SANITY)

- **Nhánh làm việc**: `track/D1-notification-feedback` (xuất phát từ commit mới nhất của `main`).
- **Ranh giới repository tham chiếu**: Không sửa bất kỳ file nào trong `..\student-marketplace-be` hoặc `..\student-marketplace-fe`.
- **Ranh giới file Wave 0 & cấu hình dùng chung**:
  - **TUYỆT ĐỐI KHÔNG sửa**: `pom.xml`, `application*.yml`, `docs/PROJECT_STATE.md`, `docs/PROJECT_HISTORY.md`.
  - **TUYỆT ĐỐI KHÔNG sửa entity, enum, repository hoặc interface Wave 0**:
    - `notification/`: giữ nguyên `Notification.java`, `NotificationType.java`, `NotificationRepository.java`, `NotificationWriter.java`, `PushNotificationSender.java`, `NoOpPushNotificationSender.java`, `UserDeviceToken.java` (không gắn `@Entity`), `UserDeviceTokenRepository.java`.
    - `feedback/`: giữ nguyên `SiteFeedback.java`, `SiteFeedbackAttachment.java`, `SupportContribution.java`, `SupportClaim.java`, `FeedbackAttachmentKind.java`, `SupportClaimStatus.java`, `SiteFeedbackRepository.java`, `SiteFeedbackAttachmentRepository.java`, `SupportContributionRepository.java`, `SupportClaimRepository.java`.
  - **`configuration/SecurityConfig.java`**: Chỉ bổ sung 3 matcher bên trong khối `// >>> TRACK D1 (notification, feedback)` ... `// <<< TRACK D1`, nằm trước rule catch-all `/api/v1/users/**` và `/api/v1/admin/**`.

### Danh sách file đã thêm / sửa / xóa

#### 1. File sửa (trong phạm vi track)
- `src/main/java/com/ptiters/marketplace/configuration/SecurityConfig.java` (chỉ trong khối `TRACK D1`)
- `src/main/java/com/ptiters/marketplace/notification/`: `NotificationController.java`, `NotificationService.java`, `NotificationResponse.java`, `NotificationListResponse.java`, `UnreadCountResponse.java`, `PushSubscribeRequest.java`, `PushUnsubscribeRequest.java`, `PushSubscriptionResponse.java`
- `src/main/java/com/ptiters/marketplace/feedback/`: `FeedbackController.java`, `AdminFeedbackController.java`, `AdminSupportController.java`, `AdminSupportClaimsController.java`, `FeedbackService.java`, `FeedbackCreateResponse.java`, `PublicFeedbackItem.java`, `PublicFeedbackCursorPage.java`, `MyFeedbackItem.java`, `MyFeedbackCursorPage.java`, `AdminFeedbackDetail.java`, `AdminFeedbackListPage.java`, `SupportResponse.java`, `SupporterLeaderboardItem.java`, `SupporterLeaderboardResponse.java`, `AdminSupportContributionCreate.java`, `AdminSupportContributionVisibilityUpdate.java`, `AdminSupportContributionItem.java`, `AdminSupportContributionCursorPage.java`, `SupportClaimCreate.java`, `SupportClaimUserItem.java`, `SupportClaimUserListPage.java`, `AdminSupportClaimVerify.java`, `AdminSupportClaimReject.java`, `AdminSupportClaimItem.java`, `AdminSupportClaimCursorPage.java`

#### 2. File tạo mới
- `src/main/java/com/ptiters/marketplace/notification/DeviceTokenStore.java` (xử lý `user_device_tokens` qua `JdbcTemplate`)
- `src/main/java/com/ptiters/marketplace/notification/NotificationQueryRepository.java` (keyset cursor & bulk update/delete qua `EntityManager`)
- `src/main/java/com/ptiters/marketplace/feedback/FeedbackQueryRepository.java` (keyset cursor, batch attachment query, leaderboard, `PESSIMISTIC_WRITE`)
- `src/main/java/com/ptiters/marketplace/feedback/`: `FeedbackAttachmentItem.java`, `AdminFeedbackSender.java`, `AdminFeedbackAttachmentItem.java`, `AdminFeedbackListItem.java`, `CreatorPublicInfo.java`, `SupporterPublicIdentity.java`, `AdminUserSafeIdentity.java`, `SupporterResolveResponse.java`
- `src/test/java/com/ptiters/marketplace/notification/NotificationControllerIntegrationTest.java`
- `src/test/java/com/ptiters/marketplace/feedback/D1TestSecurityConfig.java`
- `src/test/java/com/ptiters/marketplace/feedback/FeedbackAndNotificationUnitTest.java`
- `src/test/java/com/ptiters/marketplace/feedback/FeedbackControllerIntegrationTest.java`
- `src/test/java/com/ptiters/marketplace/feedback/SupportIntegrationTest.java`
- `docs/pr-reports/TRACK_D1.md`

#### 3. File skeleton cũ không sử dụng đã xóa (trong `feedback/`)
- `src/main/java/com/ptiters/marketplace/feedback/FeedbackCreateRequest.java` (endpoint `POST /api/v1/feedback` dùng `multipart/form-data`, không dùng JSON body)
- `src/main/java/com/ptiters/marketplace/feedback/FeedbackRepository.java` (interface placeholder không có implement, thay bằng `FeedbackQueryRepository` + Wave 0 `*Repository`)

#### Kết quả `git diff --stat main..HEAD` (tính đến toàn bộ thay đổi code + test)
```text
 .../marketplace/configuration/SecurityConfig.java  |    3 +
 .../feedback/AdminFeedbackAttachmentItem.java      |   12 +
 .../feedback/AdminFeedbackController.java          |   38 +-
 .../marketplace/feedback/AdminFeedbackDetail.java  |   14 +-
 .../feedback/AdminFeedbackListItem.java            |   13 +
 .../marketplace/feedback/AdminFeedbackListPage.java  |    8 +-
 .../marketplace/feedback/AdminFeedbackSender.java  |    9 +
 .../feedback/AdminSupportClaimCursorPage.java      |    6 +-
 .../marketplace/feedback/AdminSupportClaimItem.java|   22 +-
 .../feedback/AdminSupportClaimReject.java          |    6 +-
 .../feedback/AdminSupportClaimVerify.java          |   22 +-
 .../feedback/AdminSupportClaimsController.java     |   36 +-
 .../feedback/AdminSupportContributionCreate.java   |   30 +-
 .../AdminSupportContributionCursorPage.java        |    6 +-
 .../feedback/AdminSupportContributionItem.java     |   19 +-
 .../AdminSupportContributionVisibilityUpdate.java  |    3 +-
 .../feedback/AdminSupportController.java           |   46 +-
 .../feedback/AdminUserSafeIdentity.java            |    9 +
 .../marketplace/feedback/CreatorPublicInfo.java    |   10 +
 .../feedback/FeedbackAttachmentItem.java           |   21 +
 .../marketplace/feedback/FeedbackController.java   |   59 +-
 .../feedback/FeedbackCreateRequest.java            |   10 -
 .../feedback/FeedbackCreateResponse.java           |    9 +-
 .../feedback/FeedbackQueryRepository.java          |  301 ++++++
 .../marketplace/feedback/FeedbackRepository.java   |   13 -
 .../marketplace/feedback/FeedbackService.java      | 1048 +++++++++++++++++++-
 .../marketplace/feedback/MyFeedbackCursorPage.java |    6 +-
 .../marketplace/feedback/MyFeedbackItem.java       |   11 +-
 .../feedback/PublicFeedbackCursorPage.java         |    6 +-
 .../marketplace/feedback/PublicFeedbackItem.java   |    7 +-
 .../marketplace/feedback/SupportClaimCreate.java   |   21 +-
 .../marketplace/feedback/SupportClaimUserItem.java |   28 +-
 .../feedback/SupportClaimUserListPage.java         |    2 +-
 .../marketplace/feedback/SupportResponse.java      |   10 +-
 .../feedback/SupporterLeaderboardItem.java         |   11 +-
 .../feedback/SupporterLeaderboardResponse.java     |    2 +-
 .../feedback/SupporterPublicIdentity.java          |   11 +
 .../feedback/SupporterResolveResponse.java         |   10 +
 .../marketplace/notification/DeviceTokenStore.java |   90 ++
 .../notification/NotificationController.java       |   64 +-
 .../notification/NotificationListResponse.java     |    6 +-
 .../notification/NotificationQueryRepository.java  |   87 ++
 .../notification/NotificationResponse.java         |   34 +-
 .../notification/NotificationService.java          |  127 ++-
 .../notification/PushSubscribeRequest.java         |    8 +-
 .../notification/PushSubscriptionResponse.java     |    5 +-
 .../notification/PushUnsubscribeRequest.java       |    4 +-
 .../notification/UnreadCountResponse.java          |    6 +-
 .../marketplace/feedback/D1TestSecurityConfig.java |   17 +
 .../feedback/FeedbackAndNotificationUnitTest.java  |  164 +++
 .../FeedbackControllerIntegrationTest.java         |  325 ++++++
 .../feedback/SupportIntegrationTest.java           |  382 +++++++
 .../NotificationControllerIntegrationTest.java     |  360 +++++++
 53 files changed, 3292 insertions(+), 285 deletions(-)
```
*(Bổ sung thêm file báo cáo này: `docs/pr-reports/TRACK_D1.md`.)*

---

## BƯỚC 2: BIÊN DỊCH VÀ XÂY DỰNG MAVEN (COMPILATION & MAVEN BUILD VERIFICATION)

Lệnh chạy tại `D:\choptiters_java\marketplace-ptit-be-D1`:
```powershell
$env:JAVA_HOME = "C:\Users\Acer\.jdks\openjdk-26.0.2.1"
$env:JDK_JAVAC_OPTIONS = "-proc:full"
$env:TEST_DB_URL = "jdbc:postgresql://localhost:5432/marketplace_java_test_d1"
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = "2"
.\mvnw.cmd "-Dmaven.compiler.fork=true" "-Dlombok.version=1.18.46" clean test
```
- Biên dịch `325` source files (`src/main`) và `24` test source files (`src/test`) với release 21.
- Kết quả: **`BUILD SUCCESS`**, 0 lỗi biên dịch, 0 cảnh báo unchecked cast.

---

## BƯỚC 3: ĐỐI SOÁT SCHEMA VÀ MIGRATION FLYWAY (SCHEMA & MIGRATION PARITY)

- Không tạo hay chỉnh sửa bất kỳ migration Flyway nào (`spring.flyway.enabled: false`).
- Xử lý an toàn bảng `user_device_tokens` (chỉ có ở `marketplace_java_test*` Alembic `c9d0e1f2a3b4`, **thiếu** ở `marketplace_java_dev` Alembic `b8c9d0e1f2a3`):
  - `UserDeviceToken` giữ nguyên không gắn `@Entity`, đảm bảo `DevSchemaReadOnlyValidationTest` chạy trên `marketplace_java_dev` vẫn PASS với `ddl-auto: validate`.
  - `DeviceTokenStore` dùng `JdbcTemplate` + `SELECT to_regclass('user_device_tokens') IS NOT NULL`:
    - Khi bảng tồn tại (`marketplace_java_test_d1`): upsert atomic bằng `INSERT ... ON CONFLICT (fcm_token) DO UPDATE ...` (chuyển quyền sở hữu token nếu user khác đăng nhập cùng thiết bị), xóa theo `(user_id, fcm_token)`, đọc danh sách token và xóa stale token.
    - Khi bảng **không tồn tại** (dev DB): `getTokensForUser()` trả danh sách rỗng, `deleteToken()` trả `false`, `deleteStaleTokens()` là no-op kèm log cảnh báo; `upsertToken()` trả `503 Service Unavailable` (`ApiException`) thay vì crash `500 BadSqlGrammarException`.

---

## BƯỚC 4: TÍNH CHUẨN XÁC CỦA ÁNH XẠ JPA / HIBERNATE (ENTITY & DOMAIN MAPPING FIDELITY)

- Toàn bộ entity Wave 0 giữ nguyên vẹn (`ddl-auto: validate` PASS trên cả `marketplace_java_dev` và `marketplace_java_test_d1`).
- Không inline enum PostgreSQL native (`UserStatus`) vào JPQL/HQL — luôn dùng `.setParameter("activeStatus", UserStatus.ACTIVE)`.
- Kiểu thời gian chuẩn xác: `LocalDateTime` (UTC) cho `notifications` và `site_feedback`; `OffsetDateTime` cho `site_feedback_attachments`, `support_contributions`, `support_claims`.
- Tránh Hibernate HHH90003004 (in-memory pagination khi `JOIN FETCH` trên `@OneToMany`): `listMyFeedbacks` và `listAdminFeedbacks` phân trang trên `SiteFeedback` trước (`setMaxResults(limit + 1)`), sau đó truy vấn batch `SiteFeedbackAttachment` theo `feedback_id IN :ids`.

---

## BƯỚC 5: KIẾN TRÚC PHÂN TẦNG VÀ XỬ LÝ LỖI (LAYERED ARCHITECTURE & EXCEPTION CONTRACT)

- Tuân thủ nghiêm ngặt `Controller -> Service -> Repository -> Entity`.
- **Transaction & Side-effect isolation**:
  - Ghi notification trong luồng feedback và support claim chỉ gọi `NotificationWriter.notifyAdmins(...)` / `NotificationWriter.create(...)` trong cùng transaction DB; realtime WebSocket và push dispatch sau commit do `NotificationWriter` đảm nhiệm best-effort.
  - `FeedbackService.submitFeedback` upload media qua `MediaValidator` + `MediaStorage.uploadFeedbackAttachment(...)` trước khi mở transaction ghi (`@Transactional(propagation = Propagation.NOT_SUPPORTED)` + `TransactionTemplate`), thực hiện compensation `destroyAsset` best-effort nếu upload lỗi, vượt thời lượng 60s hoặc lưu DB thất bại.
  - `deleteMyFeedback` và `deleteAdminFeedback` khóa dòng `PESSIMISTIC_WRITE` (`FOR UPDATE`), xóa bản ghi DB (cascade attachments) trong `TransactionTemplate`, sau đó mới gọi `MediaStorage.destroyAsset(...)` best-effort sau commit.
- **Bảo mật & phân quyền**:
  - `CurrentUserResolver.getCurrentActiveUser()` chặn `401` khi thiếu/sai JWT và `403` khi `BANNED`/`INACTIVE`.
  - `CurrentUserResolver.requireAdmin()` chặn `403` cho `STUDENT` trên toàn bộ `/api/v1/admin/feedback/**`, `/api/v1/admin/support-contributions/**`, `/api/v1/admin/support-claims/**`.
  - BOLA privacy-safe `404 Not Found`: `markRead`, `deleteNotification`, `deleteMyFeedback` trả `404` khi không tìm thấy hoặc thuộc về user khác.
  - Toàn bộ lỗi trả qua `ApiException` + `ErrorHandler` format `{ "detail": "..." }`.

---

## BƯỚC 6: TƯƠNG THÍCH HỢP ĐỒNG API VÀ DTO (API CONTRACT & DTO PARITY)

### Bảng đối chiếu Endpoint đạt parity với `API_CONTRACT.md` & Python

| Nhóm | Method & Path | Auth | Parity Status & Ghi chú |
|---|---|---|---|
| **Notifications** | `POST /api/v1/notifications/push/subscribe` | `ACTIVE` user | **Đạt 100%** (`fcm_token`, `device_info` → `{"status":"subscribed"}`; `422` khi rỗng; `503` nếu thiếu bảng ở dev DB) |
| **Notifications** | `POST /api/v1/notifications/push/unsubscribe` | `ACTIVE` user | **Đạt 100%** (`{"status":"unsubscribed"}`, idempotent) |
| **Notifications** | `GET /api/v1/notifications` | `ACTIVE` user | **Đạt 100%** (cursor Base64-urlsafe JSON `{"created_at": iso, "notification_id": id}`, sort `created_at DESC, notification_id DESC`, `limit` kẹp `1..50`, `unread_only`) |
| **Notifications** | `GET /api/v1/notifications/unread-count` | `ACTIVE` user | **Đạt 100%** (`{"count": N}`) |
| **Notifications** | `PATCH /api/v1/notifications/read-all` | `ACTIVE` user | **Đạt 100%** (`204 No Content`, đăng ký trước `/{notificationId}/read`) |
| **Notifications** | `PATCH /api/v1/notifications/{notificationId}/read` | `ACTIVE` user | **Đạt 100%** (`204` idempotent, `404` privacy-safe) |
| **Notifications** | `DELETE /api/v1/notifications/{notificationId}` | `ACTIVE` user | **Đạt 100%** (`204`, hard delete inbox only, `404` privacy-safe) |
| **Feedback** | `POST /api/v1/feedback` | `ACTIVE` user | **Đạt 100%** (`multipart/form-data`, NFC + control char check, max 3 ảnh/1 video/4 file, `MediaValidator` + `MediaStorage` + compensation, `NotificationWriter.notifyAdmins` `ADMIN_FEEDBACK`, `201`) |
| **Feedback** | `GET /api/v1/feedback` | Public (`permitAll`) | **Đạt 100%** (chỉ trả `feedback_id`, `content`, `created_at`; loại user không `ACTIVE`; cursor `created_at\|id` Base64-urlsafe) |
| **Feedback** | `GET /api/v1/feedback/me` | `ACTIVE` user | **Đạt 100%** (kèm danh sách `attachments`, cursor) |
| **Feedback** | `DELETE /api/v1/feedback/{feedbackId}` | `ACTIVE` user | **Đạt 100%** (`FOR UPDATE`, `404` privacy-safe, `204`, dọn media sau commit) |
| **Admin Feedback** | `GET /api/v1/admin/feedback` | `ADMIN` | **Đạt 100%** (`sender`, `image_count`, `video_count`, cursor) |
| **Admin Feedback** | `GET /api/v1/admin/feedback/{feedbackId}` | `ADMIN` | **Đạt 100%** (kèm `stream_url` tương đối) |
| **Admin Feedback** | `DELETE /api/v1/admin/feedback/{feedbackId}` | `ADMIN` | **Đạt 100%** (`FOR UPDATE`, `204`, dọn media sau commit) |
| **Admin Feedback** | `GET /api/v1/admin/feedback/{feedbackId}/evidence/{attachmentId}` | `ADMIN` | **Đạt ~95%** (hỗ trợ `Range` HTTP `206` cho cả `LocalMediaStorage` và Cloudinary `res.cloudinary.com`; xem mục khác biệt bên dưới) |
| **Support** | `GET /api/v1/feedback/support` | Public (`permitAll`) | **Đạt 100%** (phân giải `COMMUNITY_CREATOR_PROVIDER_UID` / `COMMUNITY_CREATOR_EMAIL`, ẩn nếu không `ACTIVE`) |
| **Support** | `GET /api/v1/feedback/support/leaderboard` | Public (`permitAll`) | **Đạt 100% theo Python `schemas.py`** (chỉ đọc `support_contributions` với `is_public=true` và user `ACTIVE`, Top 10, sort `SUM DESC, MAX(received_at) ASC, user_id ASC`, dùng `public_id` + `profile_path="/profile/{public_id}"` theo Security E-Lite) |
| **Support Claims** | `POST /api/v1/feedback/support/claims` | `ACTIVE` user | **Đạt 100%** (`amount > 0`, trim `sender_account_name`, `wants_public` mặc định `false`, `PENDING`, `notifyAdmins` `ADMIN_SUPPORT_CLAIM`, `201`) |
| **Support Claims** | `GET /api/v1/feedback/support/claims/me` | `ACTIVE` user | **Đạt 100%** (`limit` 1..50, mặc định 10, ẩn thông tin đối soát nội bộ) |
| **Admin Support** | `GET /api/v1/admin/support-contributions/search-supporters` | `ADMIN` | **Đạt 100%** (chỉ user `ACTIVE`, tìm theo tên hoặc numeric ID, `limit` 1..20) |
| **Admin Support** | `POST /api/v1/admin/support-contributions` | `ADMIN` | **Đạt 100%** (`amount_vnd` `1..1_000_000_000`, `recorded_by` lấy từ admin, `201`) |
| **Admin Support** | `GET /api/v1/admin/support-contributions` | `ADMIN` | **Đạt 100%** (cursor `received_at\|contribution_id`) |
| **Admin Support** | `PATCH /api/v1/admin/support-contributions/{contributionId}/visibility` | `ADMIN` | **Đạt 100%** (`200 OK` trả `AdminSupportContributionItem`) |
| **Admin Support** | `DELETE /api/v1/admin/support-contributions/{contributionId}` | `ADMIN` | **Đạt 100%** (`204 No Content`, `404` nếu không tồn tại) |
| **Admin Claims** | `GET /api/v1/admin/support-claims` | `ADMIN` | **Đạt 100%** (lọc `status` `PENDING`/`VERIFIED`/`REJECTED`, cursor `created_at\|claim_id`) |
| **Admin Claims** | `POST /api/v1/admin/support-claims/{claimId}/verify` | `ADMIN` | **Đạt 100%** (`FOR UPDATE`, `409` nếu không `PENDING`, tạo `support_contributions` với `is_public = claim.wants_public`, gắn `contribution_id`, `NotificationWriter.create` `SUPPORT_CLAIM`, `200 OK`) |
| **Admin Claims** | `POST /api/v1/admin/support-claims/{claimId}/reject` | `ADMIN` | **Đạt 100%** (`FOR UPDATE`, `409` nếu không `PENDING`, không tạo contribution, `NotificationWriter.create` `SUPPORT_CLAIM`, `200 OK`) |

### Khác biệt còn tồn đọng / lưu ý đối chiếu tài liệu
1. **`SupporterPublicIdentity` (`GET /api/v1/feedback/support/leaderboard`)**: Tài liệu `API_CONTRACT.md` mục 14.5.1 ghi ví dụ cũ `user_id` + `/users/15`, nhưng code Python thực tế (`student-marketplace-be/app/modules/feedback/schemas.py`, `repository.py` và `tests/test_support_contributions.py`) sau migration Security E-Lite đã chuyển sang `public_id` (UUID) + `profile_path = "/profile/{public_id}"` và **cấm trả `user_id`**. Bản Java tuân theo code Python thực tế (đồng nhất với `UserController` và `ProductService`).
2. **`SupportClaim.sender_account_name` max length**: Entity Wave 0 (`SupportClaim.java`) và Python `models.py` khai báo `VARCHAR(100)` (dù `API_CONTRACT.md` ghi 120), nên DTO `SupportClaimCreate` giới hạn `@Size(max = 100)` để khớp schema DB thật.
3. **`GET /api/v1/admin/feedback/{feedbackId}/evidence/{attachmentId}`**: Ở Python có thêm fallback `?token=` trên query parameter (`get_admin_for_evidence`); trên Java dùng cơ chế chuẩn `Authorization: Bearer ...` của `SecurityConfig` (trùng khớp với `student-marketplace-fe/src/features/feedback/api.ts` gọi qua `apiClient` với header Bearer).
4. **Rate limiting (`rate_limit_write_mutation`)**: Python gắn decorator rate limit lên `POST /api/v1/feedback`; Java hiện chưa có tầng rate limiter dùng chung ở Wave 0.

---

## BƯỚC 7: KIỂM THỬ THỰC CHẤT VỚI DATABASE THẬT (TEST VERIFICATION & INTEGRATION)

### Kết quả chạy TOÀN BỘ suite trên `marketplace_java_test_d1`
- **Tổng số test**: **185/185 PASS** (`Failures: 0`, `Errors: 0`, `Skipped: 0`), `BUILD SUCCESS`.
- Trong đó **14 tests mới của Track D1** (cộng với `NotificationWriterIntegrationTest` 3 tests Wave 0 = **17 tests** thuộc 2 package của D1):
  - `FeedbackAndNotificationUnitTest`: 4/4 PASS (kiểm tra `DeviceTokenStore` khi thiếu bảng ở dev DB, validation NFC/emoji/control char, upload compensation khi vượt 60s / lỗi storage, phân giải creator `getSupportInfo`).
  - `NotificationControllerIntegrationTest`: 4/4 PASS (`401`/`403`, cô lập user, ordering `created_at DESC, notification_id DESC`, keyset cursor JSON Base64-urlsafe nhiều trang, `limit` kẹp `1..50`, `unread_only`, `unread-count`, `mark-read` idempotent, `mark-all-read`, xóa BOLA `404`, push `subscribe`/`reassign`/`unsubscribe` trên bảng `user_device_tokens` thật).
  - `FeedbackControllerIntegrationTest`: 3/3 PASS (`401`/`403`, multipart upload qua `MediaValidator` + `LocalMediaStorage`, thông báo admin qua `NotificationWriter`, public feed ẩn danh, `me`, stream evidence `200` + `Range` `206`, hard delete + dọn file sau commit, validation magic bytes/số lượng, cursor nhiều trang).
  - `SupportIntegrationTest`: 3/3 PASS (`support` & `leaderboard` công khai, `401`/`403` admin, `search-supporters`, CRUD `support_contributions`, aggregation & privacy của `leaderboard`, tạo `support_claims` + `notifyAdmins`, `verify` `FOR UPDATE` tạo contribution + gửi notification + `409` khi lặp, `reject` không tạo contribution + gửi notification + `409` khi lặp).
  - `NotificationWriterIntegrationTest`: 3/3 PASS.
  - Toàn bộ 168 tests nền (Category, User, Security, Product, Favorite, MediaValidator, WorkerRetryExecutor, Wave0EntityMapping, DevSchemaReadOnlyValidation): **168/168 PASS**.

### Trạng thái CSDL `marketplace_java_test_d1` sau test
Đã kiểm tra trực tiếp bằng `SELECT count(*)` trên toàn bộ 33 bảng nghiệp vụ + `user_device_tokens` + `alembic_version`: **tất cả các bảng đều về 0 dòng**.

---

## BƯỚC 8: RỦI RO & VIỆC CẦN NGƯỜI ĐIỀU PHỐI (RISKS & COORDINATOR ACTION ITEMS)

1. **Nâng cấp `marketplace_java_dev` lên Alembic `c9d0e1f2a3b4` (ưu tiên P1)**:
   - Hiện bảng `user_device_tokens` chưa có trên `marketplace_java_dev` (`b8c9d0e1f2a3`). Do đó `UserDeviceToken` vẫn giữ ở trạng thái chưa gắn `@Entity` và dùng `DeviceTokenStore` (`JdbcTemplate`). Sau khi Dev DB được nâng lên `c9d0e1f2a3b4`, có thể cân nhắc bổ sung `@Entity` cho `UserDeviceToken` nếu cần.
2. **Tích hợp `DeviceTokenStore` với Track E (FCM Push thật)**:
   - `NoOpPushNotificationSender` ở Wave 0 chưa gửi FCM thật. Khi Track E hiện thực `FirebasePushNotificationSender` thay thế `NoOpPushNotificationSender`, có thể tiêm `DeviceTokenStore` để đọc `getTokensForUser(userId)` và gọi `deleteStaleTokens(staleTokens)`.
3. **Cấu hình biến môi trường Creator khi chạy staging/production**:
   - Đảm bảo thiết lập `COMMUNITY_CREATOR_PROVIDER_UID` (hoặc `COMMUNITY_CREATOR_EMAIL`) để `GET /api/v1/feedback/support` phân giải được `creator`.
4. **Số lượng connection pool khi chạy toàn bộ test suite song song**:
   - Do nhiều `@SpringBootTest` có `@TestConfiguration` khác nhau mở nhiều HikariCP pool đồng thời (mỗi pool mặc định 10 connections), khi nhiều track chạy cùng lúc trên cùng `sm_postgres` (mặc định 100 max connections) có thể gặp `FATAL: sorry, too many clients already`. Trong quá trình kiểm thử D1 đã đặt `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2` khi chạy toàn suite và gom `D1TestSecurityConfig` dùng chung cho 3 lớp test tích hợp mới.
