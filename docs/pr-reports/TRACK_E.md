# BÁO CÁO KIỂM DUYỆT & HOÀN TẤT TRACK E — REALTIME, INTEGRATION & MEDIA CLEANUP

- **Nhánh Git**: `track/E-realtime-infra`
- **Worktree**: `D:\choptiters_java\marketplace-ptit-be-E`
- **Quy chuẩn áp dụng**: `.agents/skills/marketplace-java-review/SKILL.md` (8 bước), `AGENTS.md`, `docs/PARALLEL_PLAN.md`, `docs/pr-reports/README.md`

---

## BƯỚC 1: KIỂM TOÁN RANH GIỚI WORKSPACE, NHÁNH GIT & PHẠM VI FILE

1. **Ranh giới repository & nhánh**:
   - Đã xác nhận `git branch --show-current` = `track/E-realtime-infra`.
   - Không có bất kỳ chỉnh sửa nào lên nhánh `main`, không sửa `student-marketplace-be` hoặc `student-marketplace-fe` (chỉ đọc tham chiếu).
   - Không có secrets, `.env`, dump CSDL hay thư mục `target/` được đưa vào commit.
2. **Xác nhận KHÔNG sửa file Wave 0, cấu hình chung và module track khác**:
   - Không sửa: `pom.xml`, `application*.yml`, `docs/PROJECT_STATE.md`, `docs/PROJECT_HISTORY.md`, `SharedInfrastructureDefaultsConfiguration`, `MediaValidator`, `PresenceService`, `UserEventPublisher`, `AuctionEventPublisher`, `NoOp*`, `MediaStorage`, `LocalMediaStorage`, `UnconfiguredMediaStorage`, `MediaStorageException`, `MediaStorageNotConfiguredException`, `MediaCleanupJob`, `MediaCleanupJobRepository`, `OrderAutoCancelWorker`, `SweepResult`, `WorkerRetryExecutor`, `OrderWebSocketHandler` (`order/`), `AuctionWebSocketHandler` (`auction/`).
3. **Danh sách file đã sửa (6 file)**:
   - `src/main/java/com/ptiters/marketplace/configuration/SecurityConfig.java`: chỉ thêm duy nhất `.requestMatchers("/ws/**").permitAll()` bên trong khối đánh dấu `// >>> TRACK E (realtime, websocket)` ... `// <<< TRACK E` (trước các rule catch-all `/api/v1/users/**` và `/api/v1/admin/**`).
   - `src/main/java/com/ptiters/marketplace/realtime/ClientIpResolver.java`: nâng cấp đầy đủ trích xuất IP thực qua Cloudflare edge & reverse proxy (`X-Real-IP` thuộc 15 IPv4 + 7 IPv6 official Cloudflare CIDRs mới tin `CF-Connecting-IP` non-internal; hỗ trợ `HttpServletRequest`, `WebSocketSession` và `maskIp(String)`).
   - `src/main/java/com/ptiters/marketplace/realtime/WebSocketConfig.java`: bật `@EnableWebSocket` và triển khai `WebSocketConfigurer` đăng ký `/ws/me` (`UserWebSocketHandler`) và `/ws/auctions/**` (`AuctionRoomWebSocketHandler`) với `setAllowedOriginPatterns("*")`.
   - `src/main/java/com/ptiters/marketplace/integration/CloudinaryStorageService.java`: giữ nguyên chữ ký 5 public methods (`uploadImage`, `uploadVideo`, `destroyAsset`, `generateDeliveryUrl`, `generateUploadSignature`) cho các module hiện có (như `ProductService`), uỷ quyền hoàn toàn sang bean `MediaStorage`.
   - `src/main/java/com/ptiters/marketplace/integration/FirebasePushService.java`: triển khai `PushNotificationSender` dùng `JdbcTemplate` + `FcmMulticastClient` (`@ConditionalOnExpression` theo `FIREBASE_CREDENTIALS_JSON`/`FIREBASE_CREDENTIALS_PATH`), giữ lại helper `sendPushNotification(List<String>, String, String, Map<String, String>)`, xử lý an toàn SQLSTATE `42P01` khi bảng `user_device_tokens` chưa có trên DB dev cũ.
   - `src/main/java/com/ptiters/marketplace/worker/MediaCleanupWorker.java`: triển khai `runCleanupSweep()` và `runSweep()` với `SELECT ... FOR UPDATE SKIP LOCKED`, gia hạn lease `CLAIM_LEASE_SECONDS = 300`, tách DB claim/update bằng `WorkerRetryExecutor` ngoài transaction gọi `MediaStorage.destroyAsset(...)`, exponential backoff `{60, 300, 900, 3600, 21600}` (tối đa `86_400s`), và làm sạch secret/URL trong `last_error` (`MAX_ERROR_LENGTH = 500`).
4. **Danh sách file tạo mới trong `realtime/`, `integration/`, `worker/` và `src/test/`**:
   - Production code (15 file):
     - `realtime/RedisRealtimeConfiguration.java` (`@ConditionalOnExpression` khi có `REDIS_HOST`/`REDIS_URL`/`spring.data.redis.*`, `LettuceConnectionFactory` timeout 2s, `StringRedisTemplate`, `RedisMessageListenerContainer` với `ExponentialBackOff(1s..10s)`).
     - `realtime/RedisPubSubBridge.java` (envelope `{"_meta":{"origin":...},"payload":...}` chống loopback trên kênh `user:{id}:events` và `auction:{id}:events`).
     - `realtime/RedisPresenceService.java` (triển khai `PresenceService`: key `presence:user:{userId}`, TTL `90s`, `MGET`, lỗi Redis trả `false` / `PresenceSnapshot.unavailable()`).
     - `realtime/RedisUserEventPublisher.java` (triển khai `UserEventPublisher`: giao WebSocket cục bộ + Redis Pub/Sub `user:{id}:events`, hỗ trợ `revokeUserSessions` đóng local với mã `1008` và phát `_DISCONNECT`).
     - `realtime/RedisAuctionEventPublisher.java` (triển khai `AuctionEventPublisher`: giao WebSocket phòng đấu giá cục bộ + Redis Pub/Sub `auction:{id}:events`).
     - `realtime/UserConnectionManager.java` (quản lý nhiều kết nối `ConcurrentWebSocketSessionDecorator` mỗi user, ref-counted Redis subscribe/unsubscribe, đóng `1008` khi bị revoke).
     - `realtime/AuctionConnectionManager.java` (quản lý kết nối phòng đấu giá và ref-counted Redis subscribe/unsubscribe).
     - `realtime/UserWebSocketHandler.java` (xác thực `/ws/me` qua `?token=` hoặc `{"type":"AUTH","token":...}` trong `marketplace.ws.auth-timeout-ms:5000`, `JwtDecoder` + `CurrentUserResolver.resolveActiveFromJwt`, đóng `1008` khi token lỗi/timeout/user `BANNED`/`INACTIVE`, heartbeat `ping`/`heartbeat`/`PING`/`HEARTBEAT`).
     - `realtime/AuctionRoomWebSocketHandler.java` (xác thực `/ws/auctions/{auctionId}` số nguyên dương + tồn tại + `SCHEDULED`/`ACTIVE`, sai đóng `1008` không đăng ký vào manager, heartbeat `ping` → `pong`).
     - `integration/CloudinaryUrlBuilder.java` (builder thuần: thư mục `{root}/{dev|prod}/...`, biến đổi ảnh `200`/`800`/`1600`, video `vc_auto`, thumbnail `1s`/`0.5s`, ký SHA-1 tutorial upload qua `cloudinary.apiSignRequest(..., 1)`, URL có chữ ký).
     - `integration/CloudinaryMediaStorage.java` (triển khai `MediaStorage` thật, `@ConditionalOnExpression` theo `CLOUDINARY_URL` hoặc `CLOUDINARY_CLOUD_NAME` + `CLOUDINARY_API_KEY` + `CLOUDINARY_API_SECRET`, dùng `uploadLarge` > 20MB, tự huỷ asset nếu upload tutorial trả `resource_type != video`).
     - `integration/FcmMulticastClient.java` (interface tách client gửi multicast FCM).
     - `integration/FirebaseAdminFcmMulticastClient.java` (triển khai Firebase Admin SDK chia lô 500 token, Webpush config `/logo.svg`, nhận diện stale tokens `UNREGISTERED`/`SENDER_ID_MISMATCH`/chuỗi tương ứng).
     - `worker/MediaCleanupScheduler.java` (`enqueue`, `enqueueAll`, `deleteJob` dùng `ON CONFLICT (storage_key, resource_type) DO NOTHING`).
     - `worker/MediaCleanupSchedulingConfiguration.java` (`@EnableScheduling` cho `MediaCleanupWorker`, mặc định TẮT và chỉ kích hoạt khi `MEDIA_CLEANUP_ENABLED=true` hoặc `marketplace.workers.media-cleanup.enabled=true`).
   - Test mới (7 file, tổng 31 test cases):
     - `realtime/ClientIpResolverTest.java` (7 tests)
     - `worker/MediaCleanupWorkerIntegrationTest.java` (6 tests)
     - `integration/CloudinaryUrlBuilderTest.java` (7 tests)
     - `integration/FirebasePushServiceTest.java` (3 tests)
     - `realtime/RedisRealtimeIntegrationTest.java` (4 tests)
     - `realtime/WebSocketEndpointIntegrationTest.java` (3 tests)
     - `realtime/RedisWebSocketEndToEndIntegrationTest.java` (1 test)

---

## BƯỚC 2: KẾT QUẢ BIÊN DỊCH & TOÀN BỘ TEST SUITE TRÊN `marketplace_java_test_e`

- **Lệnh thực thi trên PowerShell** (JDK 26.0.2.1 nhắm target Java 21, không sửa `pom.xml` hay `application*.yml`):
  ```powershell
  Set-Location "D:\choptiters_java\marketplace-ptit-be-E"
  $env:JAVA_HOME = "C:\Users\Acer\.jdks\openjdk-26.0.2.1"
  $env:JDK_JAVAC_OPTIONS = "-proc:full"
  $env:TEST_DB_URL = "jdbc:postgresql://localhost:5432/marketplace_java_test_e"
  $env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = "2"
  $env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE = "1"
  .\mvnw.cmd "-Dmaven.compiler.fork=true" "-Dlombok.version=1.18.46" clean test
  ```
- **Kết quả Build & Test**:
  - Biên dịch chính: `331 source files`, biên dịch test: `26 source files` — 0 lỗi.
  - Tổng kết quả toàn bộ suite: **`Tests run: 202, Failures: 0, Errors: 0, Skipped: 0`**, **`BUILD SUCCESS`** (thời gian `01:38 min`).
  - Bao gồm toàn bộ `171` test nền Wave 0 (khởi động mặc định bằng các bean `NoOp*`/`LocalMediaStorage` không cần cấu hình Redis/Cloudinary/Firebase) + `31` test mới của Track E:
    - `CloudinaryUrlBuilderTest`: `7/7 PASS`
    - `FirebasePushServiceTest`: `3/3 PASS`
    - `ClientIpResolverTest`: `7/7 PASS`
    - `RedisRealtimeIntegrationTest`: `4/4 PASS` (trên Redis thật `sm_redis:6379`)
    - `RedisWebSocketEndToEndIntegrationTest`: `1/1 PASS` (2 instance `UserConnectionManager` mô phỏng fan-out qua `sm_redis:6379` + Tomcat WebSocket thật + `marketplace_java_test_e`)
    - `WebSocketEndpointIntegrationTest`: `3/3 PASS` (Tomcat WebSocket thật + `StandardWebSocketClient` + PostgreSQL thật `marketplace_java_test_e`)
    - `MediaCleanupWorkerIntegrationTest`: `6/6 PASS` (PostgreSQL thật `marketplace_java_test_e`)
- **Trạng thái CSDL & Redis sau khi chạy xong toàn bộ suite**:
  - Truy vấn toàn bộ bảng trong `marketplace_java_test_e`: bảng dữ liệu nghiệp vụ duy nhất có dòng là `alembic_version = 1`; tất cả bảng dữ liệu (`users`, `categories`, `products`, `auctions`, `notifications`, `media_cleanup_jobs`, `user_device_tokens`, v.v.) đều đạt **0 dòng**.
  - Kiểm tra `docker exec sm_redis redis-cli KEYS "*"` trả về rỗng (0 key tồn dư sau test).

---

## BƯỚC 3 & BƯỚC 4: SCHEMA PARITY, `ddl-auto: validate` & ENTITY MAPPING

- Track E không thêm bảng hay sửa entity/migration nào. Toàn bộ `171` test nền và các integration test Track E (bao gồm `DevSchemaReadOnlyValidationTest` và `Wave0EntityMappingIntegrationTest` kiểm tra 33 `@Entity`) chạy qua với cấu hình bất biến `spring.jpa.hibernate.ddl-auto: validate`.
- `MediaCleanupScheduler.enqueue(...)` sử dụng native SQL khớp đúng ràng buộc PostgreSQL thật trên `marketplace_java_test_e`:
  - Unique constraint `uq_media_cleanup_jobs_key_type (storage_key, resource_type)` với `ON CONFLICT (storage_key, resource_type) DO NOTHING`.
  - Cột `next_retry_at`, `created_at`, `updated_at` kiểu `timestamp without time zone` (`LocalDateTime` UTC).
- `MediaCleanupWorker.runSweep()` sử dụng native query PostgreSQL `FOR UPDATE SKIP LOCKED` trên chỉ mục `(next_retry_at, cleanup_id)` (`ix_media_cleanup_jobs_next_retry`).
- `FirebasePushService` truy vấn và xóa batch trên bảng `user_device_tokens` (`user_id`, `fcm_token`) bằng `JdbcTemplate`, bắt riêng SQLSTATE `42P01` (`undefined_table`) để tương thích an toàn khi chạy trên DB dev chưa có bảng này.

---

## BƯỚC 5 & BƯỚC 6: KIẾN TRÚC PHÂN TẦNG, BẢO MẬT & ĐỐI SOÁT PARITY VỚI `API_CONTRACT.md`

Track E là track hạ tầng Realtime, Integration & Worker nên **không sở hữu REST controller `/api/v1/...`**. Phần endpoint WebSocket và hạ tầng đối soát với `API_CONTRACT.md` & mã nguồn Python như sau:

| Thành phần / Endpoint | Contract chuẩn (`API_CONTRACT.md` / Python) | Trạng thái Java (Track E) | Ghi chú / Khác biệt còn tồn đọng |
|---|---|---|---|
| WebSocket `/ws/me` | Xác thực qua `?token=` hoặc frame đầu `{"type":"AUTH","token":...}` trong 5s; từ chối `1008` nếu token lỗi/hết hạn/timeout/`BANNED`/`INACTIVE`; hỗ trợ `ping`/`heartbeat` → `pong`, `{"type":"PING"\|"HEARTBEAT"}` → `{"type":"PONG"}`; đa thiết bị; đóng `1008` khi revoke session (`_DISCONNECT`). | **Đạt parity** (`UserWebSocketHandler`, `UserConnectionManager`, `RedisUserEventPublisher`) | `SecurityConfig.java` mở `.requestMatchers("/ws/**").permitAll()` trước rule catch-all để handler tự xác thực token WebSocket giống Python. |
| WebSocket `/ws/auctions/{auction_id}` | `auction_id` phải là số nguyên dương; auction tồn tại với status `SCHEDULED` hoặc `ACTIVE`; sai đóng `1008`, không đăng ký manager/Redis; `ping` → `pong`. | **Đạt parity** (`AuctionRoomWebSocketHandler`, `AuctionConnectionManager`, `RedisAuctionEventPublisher`) | Hai skeleton `order/OrderWebSocketHandler` và `auction/AuctionWebSocketHandler` của Track A được giữ nguyên không sửa; routing thật trỏ về handler trong `realtime/`. |
| Redis Presence & Pub/Sub | Key `presence:user:{id}` TTL `90s`, `MGET`, Redis lỗi trả `available=false`; kênh `user:{id}:events` & `auction:{id}:events` kèm `_meta.origin` chống loopback. | **Đạt parity** (`RedisPresenceService`, `RedisPubSubBridge`) | Chỉ kích hoạt khi có `REDIS_HOST`/`REDIS_URL`/`spring.data.redis.*`. |
| Cloudinary Integration (`MediaStorage`) | Thư mục `{root}/{dev\|prod}/...`, upload ảnh/biên lai/evidence/attachment/moment/video (`>20MB` dùng `uploadLarge`), huỷ asset (`destroyAsset`), chữ ký SHA-1 tutorial upload, `getVideoResource`, biến đổi URL `200/800/1600`/`vc_auto`/thumbnail `1s`/`0.5s`, ký URL. | **Đạt parity** (`CloudinaryMediaStorage`, `CloudinaryUrlBuilder`, `CloudinaryStorageService`) | `CloudinaryStorageService` uỷ quyền về `MediaStorage` (khi thiếu env `CLOUDINARY_*` sẽ fallback an toàn về `LocalMediaStorage`/`UnconfiguredMediaStorage` của Wave 0). |
| Firebase FCM Push (`PushNotificationSender`) | Truy vấn token từ `user_device_tokens`, chia batch 500, Webpush `/logo.svg`, tag `notification_id` > `conversation_id` > `ptit-notification`, tự dọn stale token, best-effort không ném lỗi. | **Đạt parity** (`FirebasePushService`, `FirebaseAdminFcmMulticastClient`) | Chưa có API đăng ký/xóa device token ở Track E (nằm ở track module User/Notification nếu triển khai tương ứng). |
| Client IP Resolver | Chỉ nhận `CF-Connecting-IP` (và không thuộc dải nội bộ) khi `X-Real-IP` nằm trong 22 dải CIDR Cloudflare; fallback `X-Real-IP` / `remoteAddr`; `maskIp`. | **Đạt parity logic** (`ClientIpResolver`) | **Khác biệt tồn đọng**: Middleware HTTP Rate Limiting theo `API_CONTRACT.md §1.1` (Auth/Catalog/Admin/Chat video upload quota trên Redis ZSET) chưa được lắp thành Servlet Filter chung; `ClientIpResolver` đã sẵn sàng để tầng Web/Security sử dụng khi ghép track. |
| Media Cleanup Worker & Scheduler | Quét batch `FOR UPDATE SKIP LOCKED`, claim lease `300s`, gọi `destroy` ngoài DB transaction, retry backoff `{60,300,900,3600,21600}` tối đa `24h`, làm sạch URL/secret trong `last_error`. | **Đạt parity** (`MediaCleanupWorker`, `MediaCleanupScheduler`, `MediaCleanupSchedulingConfiguration`) | Scheduler nền mặc định TẮT (`MEDIA_CLEANUP_ENABLED=false`) để không quét DB khi chạy API/test. |

---

## BƯỚC 7: KIỂM THỬ THỰC CHẤT KHÔNG MOCK GIAN LẬN

- WebSocket (`/ws/me`, `/ws/auctions/{auctionId}`), `MediaCleanupWorker` + `MediaCleanupScheduler`, `RedisPresenceService`, `RedisUserEventPublisher`, `RedisAuctionEventPublisher` và fan-out 2 instance đều đã được kiểm thử tích hợp trên PostgreSQL thật (`marketplace_java_test_e`), Redis thật (`sm_redis:6379`) và servlet container Tomcat + `StandardWebSocketClient` thật.
- Lưu ý test chạy có mock có chủ đích cho ngoại vi bên ngoài không được phép kết nối production:
  - `FirebasePushServiceTest` dùng `FcmMulticastClient` giả để kiểm thử hành vi dọn stale token & xử lý lỗi SQLSTATE `42P01`.
  - `MediaCleanupWorkerIntegrationTest` dùng `LocalMediaStorage` ghi nhận cuộc gọi `destroyAsset` và giả lập lỗi trả về chứa secret/URL để kiểm thử transaction claim/backoff thật trên PostgreSQL.
  - Không có test nào làm giảm assertion hay tắt kiểm tra để ép PASS.

---

## BƯỚC 8: RỦI RO, LƯU Ý & VIỆC CẦN NGƯỜI ĐIỀU PHỐI XỬ LÝ

1. **HikariCP `max_connections` khi chạy full suite trên Postgres Docker local (`sm_postgres`)**:
   - Do tập hợp ~24 lớp test hiện tại sử dụng nhiều cấu hình `@TestConfiguration`/`@SpringBootTest` khác nhau, Spring TestContext cache mở khoảng 12–14 `ApplicationContext` đồng thời; với pool mặc định `maximum-pool-size=10` sẽ vượt ngưỡng `max_connections=100` của `sm_postgres` (`FATAL: sorry, too many clients already`).
   - Khi chạy full suite với `$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="2"` và `$env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="1"`, toàn bộ `202/202` test PASS ổn định. Người điều phối nên cân nhắc đặt `hikari.maximum-pool-size: 2` trong `src/test/resources/application-test.yml` sau khi hợp nhất các track (Track E không tự sửa file cấu hình này theo quy tắc cứng).
2. **Nâng cấp schema `marketplace_java_dev` trước khi bật Firebase Push trên môi trường dev**:
   - `marketplace_java_dev` hiện đang thiếu bảng `user_device_tokens` (mới có ở `marketplace_java_test_*` tại revision `c9d0e1f2a3b4`). Hiện tại `FirebasePushService` đã an toàn (bỏ qua khi SQLSTATE `42P01`), nhưng để gửi FCM thật trên dev cần đồng bộ migration tạo bảng `user_device_tokens`.
3. **Biến môi trường kích hoạt bean thật khi triển khai thực tế**:
   - Cần cấp `REDIS_HOST` hoặc `REDIS_URL` để bật `RedisPresenceService`, `RedisUserEventPublisher`, `RedisAuctionEventPublisher`.
   - Cần cấp `CLOUDINARY_URL` (hoặc đủ `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`) để bật `CloudinaryMediaStorage`.
   - Cần cấp `FIREBASE_CREDENTIALS_JSON` hoặc `FIREBASE_CREDENTIALS_PATH` để bật `FirebasePushService` và `FirebaseAdminFcmMulticastClient`.
   - Cần đặt `MEDIA_CLEANUP_ENABLED=true` **chỉ trên process worker nền**, giữ `false` trên các API server.
4. **Điểm nối cho các track khác (A, B, C, D1, D2)**:
   - Giao tiếp realtime/push/media/cleanup chỉ tiêm các bean chuẩn đã có ở Wave 0 và mới bổ sung (`UserEventPublisher`, `AuctionEventPublisher`, `PresenceService`, `PushNotificationSender`, `MediaStorage`, `MediaCleanupScheduler`, `ClientIpResolver`).
   - Lưu ý khi người điều phối dọn dẹp sau merge: hai class skeleton `order/OrderWebSocketHandler` và `auction/AuctionWebSocketHandler` ở Wave 0 không được đăng ký trong `WebSocketConfig` (do handler thật nằm tại `realtime/UserWebSocketHandler` và `realtime/AuctionRoomWebSocketHandler`).
