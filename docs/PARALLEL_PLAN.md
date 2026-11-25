# KẾ HOẠCH CHẠY SONG SONG 6 TRACK (SAU WAVE 0)

> Tài liệu quy ước cho 6 phiên Claude Code chạy song song sau khi Wave 0 (nền tảng dùng chung) hoàn tất.
> Mục tiêu: 6 track KHÔNG đụng file của nhau, merge về `main` không xung đột.

---

## 1. Bảng phân công

| Track | Package được sửa | Nhánh | Worktree | DB test |
|---|---|---|---|---|
| A | `order/`, `auction/`, `worker/OrderAutoCancelWorker` | `track/A-order-auction` | `../marketplace-ptit-be-A` | `marketplace_java_test_a` |
| B | `conversation/` | `track/B-conversation` | `../marketplace-ptit-be-B` | `marketplace_java_test_b` |
| C | `review/`, `report/`, admin users + search trong `user/` (**file mới**) | `track/C-review-report-admin` | `../marketplace-ptit-be-C` | `marketplace_java_test_c` |
| D1 | `notification/` (trừ `NotificationWriter`), `feedback/` | `track/D1-notification-feedback` | `../marketplace-ptit-be-D1` | `marketplace_java_test_d1` |
| D2 | `communitymoment/`, `tutorial/`, `dailyneed/` | `track/D2-moment-tutorial-dailyneed` | `../marketplace-ptit-be-D2` | `marketplace_java_test_d2` |
| E | `realtime/`, `integration/`, `worker/MediaCleanup*` | `track/E-realtime-infra` | `../marketplace-ptit-be-E` | `marketplace_java_test_e` |

Mỗi track chạy test với DB riêng bằng biến môi trường (không sửa `application-test.yml`):

```powershell
$env:TEST_DB_URL = "jdbc:postgresql://localhost:5432/marketplace_java_test_a"   # đổi hậu tố theo track
.\mvnw.cmd clean test
```

Các safety check trong test hiện có (và test mới) chỉ chấp nhận DB khớp `marketplace_java_test` hoặc `marketplace_java_test_<hậu tố>`
(regex `marketplace_java_test(_[a-z0-9]+)?`) và cấm tuyệt đối `marketplace_java_dev`; test MỚI của track phải dùng đúng regex này.
Mỗi track chạy được TOÀN BỘ suite trên DB riêng của mình.

## 2. Quy tắc cứng

1. KHÔNG sửa entity, enum, repository cơ bản, interface của Wave 0 (xem mục 4). Cần thêm gì → tạo **class/file mới** trong package của mình (query riêng dùng `@Query`/repository custom đặt tên khác, hoặc `EntityManager`).
2. KHÔNG sửa `pom.xml`, `application*.yml`, `docs/PROJECT_STATE.md`, `docs/PROJECT_HISTORY.md` (người điều phối cập nhật sau khi merge).
3. `SecurityConfig.java`: chỉ thêm `.requestMatchers(...)` BÊN TRONG khối `// >>> TRACK X` ... `// <<< TRACK X` của mình. Các khối nằm TRƯỚC rule `/api/v1/users/**` (matcher khớp đầu tiên thắng).
4. Không sửa logic `user/`, `product/`, `favorite/`, `category/` đang chạy (Track C chỉ thêm file mới trong `user/`).
5. Báo cáo cuối track: `docs/pr-reports/TRACK_<ID>.md` (xem `docs/pr-reports/README.md`). Dừng sau khi báo cáo, chờ phê duyệt.
6. Tuyệt đối không kết nối production; chỉ Docker local `sm_postgres` (5432) và `sm_redis` (6379). Không DROP/TRUNCATE.
7. Tests tích hợp dùng PostgreSQL thật, dọn sạch dữ liệu sau test (`marketplace_java_test_*` phải về 0 dòng).

## 3. Entity đã có (Wave 0) — ánh xạ khớp 100% schema Alembic `c9d0e1f2a3b4`

| Package | Entity → bảng |
|---|---|
| `order` | `Order`→orders (cascade `items`, `statusHistory`), `OrderItem`→order_items, `OrderStatusHistory`→order_status_history |
| `auction` | `Auction`→auctions (cascade `bids`), `AuctionBid`→auction_bids |
| `review` | `Review`→reviews |
| `report` | `Report`→reports (cascade `attachments`), `ReportAttachment`→report_attachments, `ModerationAction`→moderation_actions |
| `conversation` | `Conversation`→conversations (cascade `members`, `invites`, `messages`), `ConversationMember`→conversation_members (PK kép `ConversationMemberId`), `ConversationInvite`, `Message` (cascade `attachments`, `mentions`, `reactions`), `MessageAttachment`, `MessageMention`, `MessageReaction` |
| `notification` | `Notification`→notifications; `UserDeviceToken`→user_device_tokens (**CHƯA @Entity**, xem mục 6) |
| `feedback` | `SiteFeedback`→site_feedback (cascade `attachments`), `SiteFeedbackAttachment`, `SupportContribution`, `SupportClaim` |
| `communitymoment` | `CommunityMoment`→community_moments |
| `tutorial` | `Tutorial`→tutorials |
| `dailyneed` | `DailyNeed`→daily_needs, `DailyJournalCheckin`→daily_journal_checkins |
| `worker` | `MediaCleanupJob`→media_cleanup_jobs |

Quy ước mapping (giống `Product`/`Favorite`):
- Mỗi cột FK có **trường id thuần** (`buyerId`, `sellerId`, ...) ghi được + quan hệ `@ManyToOne(LAZY)` **chỉ đọc** (`insertable=false, updatable=false`) khi Python có `relationship`. Riêng con trong aggregate (`OrderItem.order`, `AuctionBid.auction`, `Message.conversation`, ...) có quan hệ cha **ghi được** và cha có `@OneToMany(mappedBy, cascade=ALL, orphanRemoval=true)` đúng như Python (`cascade="all, delete-orphan"`).
- Cột enum dạng VARCHAR + CHECK: `@Enumerated(STRING) @JdbcTypeCode(VARCHAR)`. Cột enum PostgreSQL native (`payment_status_t`): `@JdbcTypeCode(NAMED_ENUM)` như `UserStatus`.
- `created_at`/`updated_at`: tự gán UTC trong `@PrePersist`/`@PreUpdate`. Cột `timestamptz` (daily_needs, tutorials, support_*, site_feedback_attachments) dùng `OffsetDateTime`; còn lại `LocalDateTime` (UTC).
- Giá tiền `NUMERIC(12,0)` → `BigDecimal`.
- Enum giá trị khớp Python (đã sửa lại các enum skeleton sai): `AuctionStatus{SCHEDULED,ACTIVE,ENDED,CANCELLED}`, `PaymentStatus{UNPAID,VERIFYING,PAID}`, `NotificationType{NEW_ORDER,ORDER_STATUS,NEW_MESSAGE,NEW_REVIEW,AUCTION_BID,AUCTION_OUTBID,AUCTION_WON,AUCTION_ENDED,SYSTEM}`, `ReportStatus{OPEN,REVIEWING,RESOLVED,REJECTED}`, `ReportTargetType{USER,PRODUCT,ORDER}`, `ReportReason{SCAM,FAKE_ITEM,SPAM,INAPPROPRIATE,NO_SHOW,OTHER}`, `ModerationActionType{USER_BAN,USER_UNBAN,PRODUCT_HIDE,PRODUCT_RESTORE,BUY_RESTRICT,BUY_RESTORE,SELL_RESTRICT,SELL_RESTORE}`, `ReactionKey{THICH,HAHA,WOW,CAM_ON,CHOT_DEAL,UY_TIN,DANG_TOI,DOI_CHUT,QUAN_TAM,HET_TIEN,HOC_BAI,KHA_NGHI}`; thêm `ConversationType`, `ConversationMemberRole`, `ConversationInviteStatus`, `MessageType`, `MessageAttachmentKind`, `FeedbackAttachmentKind`.

Lưu ý kỹ thuật (đã gặp khi làm Wave 0):
- Entity `Order` dùng được trong HQL (`select o from Order o`) — đã kiểm chứng.
- **Không inline enum native vào HQL** (`u.status = UserStatus.ACTIVE` sinh lỗi `type "userstatus" does not exist`); luôn `setParameter`.
- `Notification` KHÔNG có cột actor/metadata (schema thật chỉ có `ref_type`, `ref_id`).
- Repository của Wave 0 chỉ là `JpaRepository` rỗng; query nghiệp vụ đặt trong package của track (repository custom đặt tên mới hoặc `EntityManager`).
- Lớp skeleton cũ có tên trùng (`OrderRepository`, `AuctionRepository`, ...) đã được thay bằng `JpaRepository`; các `*Repository` skeleton khác tên (ví dụ `feedback/FeedbackRepository`, `notification/UserDeviceTokenRepository`) vẫn là interface thường, track sở hữu package tự xử lý.

## 4. Interface dùng chung (chữ ký) — KHÔNG sửa, chỉ gọi

Bean mặc định được đăng ký trong `configuration/SharedInfrastructureDefaultsConfiguration` bằng `@Bean @ConditionalOnMissingBean` (`@Order(LOWEST_PRECEDENCE)`). Track E thay bằng bean thật (`@Component`/`@Service`/`@Configuration` ở package của mình) mà không sửa file Wave 0.

### `notification/NotificationWriter` (@Component, Track nào cũng dùng)
```java
Notification create(Long userId, NotificationType type, String title, String body, String refType, Long refId);
List<Notification> notifyAdmins(String title, String body, String refType, Long refId); // SYSTEM → ADMIN ACTIVE
```
Ghi + flush trong transaction của caller (không commit). Sau commit mới gọi `UserEventPublisher.publishUserEvent(NOTIFICATION_CREATED)` rồi `PushNotificationSender.send(...)`; rollback thì không dispatch.

### `notification/PushNotificationSender` (mặc định `NoOpPushNotificationSender`)
```java
void send(Long userId, String title, String body, Map<String,String> data);
```

### `realtime/UserEventPublisher` (mặc định `NoOpUserEventPublisher`)
```java
void publishUserEvent(Long userId, Map<String,Object> eventData);   // abstract
void revokeUserSessions(Long userId);                                // abstract; gọi SAU commit
default void publishOrderCreated(Long sellerId, Long orderId);
default void publishOrderUpdated(Long buyerId, Long sellerId, Long orderId, String reason);
default void publishAuctionWinnerCheckedOut(Long sellerId, Long auctionId, Long orderId);
```

### `realtime/AuctionEventPublisher` (mặc định `NoOpAuctionEventPublisher`)
```java
void publishAuctionEvent(Long auctionId, Map<String,Object> eventData);   // abstract
default void publishAuctionStarted(Long auctionId, BigDecimal startingPrice, Instant serverTime);
default void publishBidPlaced(Long auctionId, BigDecimal amount, BigDecimal bidStep, long bidCount, Instant serverTime);
default void publishAuctionEnded(Long auctionId, BigDecimal finalPrice /*null = không có người thắng*/, Instant serverTime);
default void publishAuctionCancelled(Long auctionId, Instant serverTime);
```

### `realtime/PresenceService` (interface; mặc định `NoOpPresenceService` → available=false, rỗng)
```java
boolean recordUserPresence(Long userId);
PresenceSnapshot getUsersPresence(Collection<Long> userIds);   // record(boolean available, Set<Long> onlineUserIds)
default UserPresence isUserOnline(Long userId);                // record(boolean available, boolean online)
```

### `integration/MediaStorage` (mặc định: profile `test` → `LocalMediaStorage`; còn lại → `UnconfiguredMediaStorage` ném `MediaStorageNotConfiguredException`)
```java
UploadedAsset uploadProductImage(byte[] data, String originalFilename, Long productId);
UploadedAsset uploadOrderReceipt(byte[] data, String originalFilename, Long orderId);
UploadedAsset uploadReportEvidence(byte[] data, String originalFilename, Long reportId, String resourceType);
UploadedAsset uploadMessageAttachment(byte[] data, String originalFilename, Long conversationId, String kind);
UploadedAsset uploadFeedbackAttachment(byte[] data, String originalFilename, String resourceType);
UploadedAsset uploadCommunityMomentImage(byte[] data, String originalFilename, Long userId);
UploadedAsset uploadTutorialVideo(byte[] data, String filename);
TutorialUploadSignature generateTutorialUploadSignature(String uploadSessionId);
VideoResource getVideoResource(String publicId);
boolean destroyAsset(String publicId, String resourceType);
String deliveryUrl(String publicId, String resourceType, String defaultUrl);
String tutorialThumbnailUrl(String videoStorageKey, Long durationMs);
default String thumbnailUrl(String) / detailImageUrl(String) / heroImageUrl(String) / tutorialVideoDeliveryUrl(String);
// record UploadedAsset(secureUrl, publicId, resourceType, bytes, format, durationSeconds)
```
`module product/` hiện dùng skeleton `integration/CloudinaryStorageService` (chưa hiện thực) — **giữ nguyên**, Track E quyết định gom về `MediaStorage`.

### `realtime/MediaValidator` (@Component, đã hiện thực đầy đủ theo `media_validation.py`)
```java
String validateImageBytes(String fileName, byte[] bytes);                 // → "image/jpeg|png|webp"
String validateImageBytes(String fileName, byte[] bytes, long maxSize);
MediaEvidence validateMediaEvidenceBytes(String fileName, byte[] bytes);  // record(mimeType, mediaType IMAGE|VIDEO)
```
Lỗi: `ApiException` HTTP 422, thông điệp tiếng Việt giống Python.

### `worker/WorkerRetryExecutor` (@Component, hiện thực theo `retry.py`) + `worker/SweepResult`
```java
<T> T executeWithRetry(Callable<T> op, String operationName);                                  // 3 retry, 500ms
<T> T executeWithRetry(Callable<T> op, int maxRetries, long backoffMillis, String operationName); // backoff luỹ thừa
static boolean isTransientDbError(Throwable t);
record SweepResult(int changed, int candidates, int noOp, int failed)
```

## 5. Hạ tầng đã có sẵn trong `pom.xml` (Wave 0)
`spring-boot-starter-websocket`, `spring-boot-starter-data-redis`, `com.cloudinary:cloudinary-http5:2.3.2`, `com.google.firebase:firebase-admin:9.5.0`.
App và test khởi động được khi KHÔNG có credentials Cloudinary/Firebase và KHÔNG có Redis (test không phụ thuộc Redis, trừ test của Track E).

## 6. Bảng THIẾU theo DB

| Bảng | `marketplace_java_dev` (Alembic `b8c9d0e1f2a3`) | `marketplace_java_test` (+ bản sao `_a`..`_e`, Alembic `c9d0e1f2a3b4`) |
|---|---|---|
| `user_device_tokens` | **THIẾU** | có |
| Tất cả bảng còn lại (33) | có | có |

→ `notification/UserDeviceToken` là class thường, **KHÔNG gắn `@Entity`** (nếu gắn, app chạy profile mặc định sẽ fail `ddl-auto: validate`) và không có `UserDeviceTokenRepository` Spring Data. Track D1 xử lý token thiết bị bằng `JdbcTemplate`/native SQL kèm điều kiện, hoặc báo người điều phối nâng dev DB (Alembic `c9d0e1f2a3b4`) rồi mới thêm `@Entity`.

## 7. Môi trường build (máy hiện tại)
- Máy chỉ có JDK 17 mặc định và JDK 26 (`C:\Users\Acer\.jdks\openjdk-26.0.2.1`); dự án nhắm Java 21. Build được trên JDK 26 bằng:
```powershell
$env:JAVA_HOME = "C:\Users\Acer\.jdks\openjdk-26.0.2.1"; $env:JDK_JAVAC_OPTIONS = "-proc:full"
.\mvnw.cmd "-Dmaven.compiler.fork=true" "-Dlombok.version=1.18.46" clean test
```
  (JDK 23+ tắt annotation processing ngầm định nên Lombok cần `-proc:full`.) Có JDK 21 thì không cần các cờ này.
- `marketplace_java_dev` hiện được dựng lại với schema + 10 category seed + 3 user tổng hợp (không còn 286 users / 26 categories như bản chụp cũ).

## 8. Thứ tự merge đề xuất
Không có phụ thuộc cứng giữa các track (cùng dùng Wave 0). Gợi ý: E → D1 → A → B → C → D2. Sau mỗi merge chạy toàn bộ suite trên `marketplace_java_test`. Xung đột `SecurityConfig` chỉ có thể xảy ra nếu một track sửa ngoài khối của mình.

## 9. Bổ sung sau Wave 0 (ưu tiên hơn prompt track nếu mâu thuẫn)
- Máy chưa có JDK 21: build/test đúng cách ở mục 7 (JDK 26 + `-proc:full` + `-Dlombok.version=1.18.46`). KHÔNG sửa `pom.xml` vì chuyện JDK.
- Chữ ký interface dùng chung lấy theo mục 4 (ví dụ `NotificationWriter.create(userId, type, title, body, refType, refId)`), không theo mô tả trong prompt.
- Safety check test đã nới thành `marketplace_java_test(_[a-z0-9]+)?`. Trong lúc làm chỉ chạy test của track; trước khi viết báo cáo, chạy TOÀN BỘ suite một lần trên DB riêng của track (vẫn đặt `TEST_DB_URL`) và ghi kết quả vào báo cáo.
- `marketplace_java_dev` chỉ có 10 category + 3 user tổng hợp; không giả định 286 users / 26 categories.
- Repo `../student-marketplace-be/` có thay đổi chưa commit từ trước (`app/main.py`, `reports/*`, `docs/*`): đọc nguyên trạng làm chuẩn, KHÔNG sửa, KHÔNG stash/checkout.
- `user_device_tokens` có trong DB test nhưng thiếu ở dev, nên `UserDeviceToken` không phải `@Entity`; Track D1 xử lý token bằng `JdbcTemplate`.
