# BÁO CÁO NGHIỆM THU TRACK D2 — COMMUNITY MOMENT + TUTORIAL + DAILY NEED

- **Nhánh Git**: `track/D2-moment-tutorial-dailyneed`
- **Thư mục làm việc**: `D:\choptiters_java\marketplace-ptit-be-D2`
- **Commit mã nguồn**: `2990953` (`feat(track-d2): implement communitymoment, tutorial, and dailyneed parity with integration tests`)
- **Commit nền (Wave 0)**: `a4624cc` (`docs: add post-Wave0 addendum to PARALLEL_PLAN and pr-reports README`)
- **Quy trình kiểm duyệt**: Tuân thủ 8 bước tại `.agents/skills/marketplace-java-review/SKILL.md` và `docs/PARALLEL_PLAN.md`.

---

## BƯỚC 1: KIỂM TOÁN RANH GIỚI WORKSPACE VÀ NHÁNH GIT

### 1.1. Xác nhận ranh giới repository & Wave 0
- `git branch --show-current` xác nhận đúng nhánh `track/D2-moment-tutorial-dailyneed`.
- Track D2 **TUYỆT ĐỐI KHÔNG** chỉnh sửa `../student-marketplace-be/` hay `../student-marketplace-fe/` (chỉ đọc phục vụ đối soát nghiệp vụ và DTO; các thay đổi uncommitted ở `app/modules/reports`, `app/modules/users/admin_*`, `src/features/users/admin*` trong hai repo đó đã tồn tại sẵn từ trước khi Track D2 bắt đầu).
- Xác nhận **KHÔNG** sửa các thành phần thuộc Wave 0 / Track khác / cấu hình dùng chung:
  - Không sửa `pom.xml`, `src/main/resources/application*.yml`, `src/test/resources/application-test.yml`.
  - Không sửa `docs/PROJECT_STATE.md`, `docs/PROJECT_HISTORY.md` (theo mục 3 `PARALLEL_PLAN.md`, việc cập nhật thuộc trách nhiệm người điều phối sau merge).
  - Không sửa bất kỳ Entity / Enum / Repository cơ bản nào của Wave 0 (`CommunityMoment`, `CommunityMomentStatus`, `CommunityMomentRepository`, `Tutorial`, `TutorialRepository`, `DailyNeed`, `DailyJournalCheckin`, `DailyNeedRepository`, `DailyJournalCheckinRepository`, `Notification`, `NotificationWriter`, `MediaStorage`, `MediaValidator`, `CurrentUserResolver`). Các query mở rộng đều tách sang `*QueryRepository` mới trong package của Track D2.
  - Không sửa `category/`, `user/`, `product/`, `favorite/`.
  - Trong `configuration/SecurityConfig.java`: chỉ thêm **9 dòng** `.requestMatchers(...)` nằm hoàn toàn bên trong cặp đánh dấu `// >>> TRACK D2 (community moment, tutorial, daily need)` … `// <<< TRACK D2`, phía trước rule catch-all `/api/v1/users/**` và `/api/v1/admin/**`.
- Không có file rác (`.class`, `.jar`, `.env`, secrets, `target/`, file IDE) được commit.

### 1.2. Danh sách file thêm/sửa & kết quả `git diff --stat main..HEAD`

Tổng hợp thay đổi mã nguồn và test trước commit báo cáo (`git diff --stat main..HEAD` tại commit `2990953`, ngoài ra commit tiếp theo bổ sung duy nhất file báo cáo `docs/pr-reports/TRACK_D2.md`):

- **Cấu hình bảo mật (1 file sửa trong khối `TRACK D2`)**:
  - `src/main/java/com/ptiters/marketplace/configuration/SecurityConfig.java`
- **Module `communitymoment/` (3 file sửa, 2 file mới + 1 file test mới)**:
  - `src/main/java/com/ptiters/marketplace/communitymoment/CommunityMomentController.java`
  - `src/main/java/com/ptiters/marketplace/communitymoment/AdminCommunityMomentController.java`
  - `src/main/java/com/ptiters/marketplace/communitymoment/CommunityMomentService.java`
  - `src/main/java/com/ptiters/marketplace/communitymoment/CommunityMomentQueryRepository.java` *(mới)*
  - `src/main/java/com/ptiters/marketplace/communitymoment/CommunityMomentPendingException.java` *(mới)*
  - `src/test/java/com/ptiters/marketplace/communitymoment/CommunityMomentControllerIntegrationTest.java` *(mới)*
- **Module `tutorial/` (8 file sửa, 1 file mới + 1 file test mới)**:
  - `src/main/java/com/ptiters/marketplace/tutorial/TutorialController.java`
  - `src/main/java/com/ptiters/marketplace/tutorial/AdminTutorialController.java`
  - `src/main/java/com/ptiters/marketplace/tutorial/TutorialService.java`
  - `src/main/java/com/ptiters/marketplace/tutorial/PublicTutorialResponse.java`
  - `src/main/java/com/ptiters/marketplace/tutorial/AdminTutorialResponse.java`
  - `src/main/java/com/ptiters/marketplace/tutorial/AdminTutorialCreateDirectRequest.java`
  - `src/main/java/com/ptiters/marketplace/tutorial/AdminTutorialUpdateRequest.java`
  - `src/main/java/com/ptiters/marketplace/tutorial/AdminTutorialReorderRequest.java`
  - `src/main/java/com/ptiters/marketplace/tutorial/TutorialQueryRepository.java` *(mới)*
  - `src/test/java/com/ptiters/marketplace/tutorial/TutorialControllerIntegrationTest.java` *(mới)*
- **Module `dailyneed/` (6 file sửa, 1 file mới + 1 file test mới)**:
  - `src/main/java/com/ptiters/marketplace/dailyneed/DailyNeedController.java`
  - `src/main/java/com/ptiters/marketplace/dailyneed/DailyNeedService.java`
  - `src/main/java/com/ptiters/marketplace/dailyneed/DailyNeedResponse.java`
  - `src/main/java/com/ptiters/marketplace/dailyneed/DailyNeedPublicResponse.java`
  - `src/main/java/com/ptiters/marketplace/dailyneed/DailyNeedListResponse.java`
  - `src/main/java/com/ptiters/marketplace/dailyneed/DailyNeedCreateOrUpdateRequest.java`
  - `src/main/java/com/ptiters/marketplace/dailyneed/DailyNeedQueryRepository.java` *(mới)*
  - `src/test/java/com/ptiters/marketplace/dailyneed/DailyNeedControllerIntegrationTest.java` *(mới)*

```text
 .../AdminCommunityMomentController.java            |  43 +-
 .../communitymoment/CommunityMomentController.java |  33 +-
 .../CommunityMomentPendingException.java           |  15 +
 .../CommunityMomentQueryRepository.java            | 146 +++++
 .../communitymoment/CommunityMomentService.java    | 414 ++++++++++++-
 .../marketplace/configuration/SecurityConfig.java  |   9 +
 .../marketplace/dailyneed/DailyNeedController.java |  59 +-
 .../dailyneed/DailyNeedCreateOrUpdateRequest.java  |   4 +-
 .../dailyneed/DailyNeedListResponse.java           |   3 +-
 .../dailyneed/DailyNeedPublicResponse.java         |   4 +-
 .../dailyneed/DailyNeedQueryRepository.java        | 139 +++++
 .../marketplace/dailyneed/DailyNeedResponse.java   |   6 +-
 .../marketplace/dailyneed/DailyNeedService.java    | 172 +++++-
 .../tutorial/AdminTutorialController.java          |  85 +--
 .../tutorial/AdminTutorialCreateDirectRequest.java |   2 +-
 .../tutorial/AdminTutorialReorderRequest.java      |   4 +-
 .../tutorial/AdminTutorialResponse.java            |  12 +-
 .../tutorial/AdminTutorialUpdateRequest.java       |  60 +-
 .../tutorial/PublicTutorialResponse.java           |   1 +
 .../marketplace/tutorial/TutorialController.java   |   8 +-
 .../marketplace/tutorial/TutorialQueryRepository.java|  85 +++
 .../marketplace/tutorial/TutorialService.java      | 658 ++++++++++++++++++++-
 .../CommunityMomentControllerIntegrationTest.java  | 561 ++++++++++++++++++
 .../DailyNeedControllerIntegrationTest.java        | 494 ++++++++++++++++
 .../TutorialControllerIntegrationTest.java         | 566 ++++++++++++++++++
 25 files changed, 3415 insertions(+), 168 deletions(-)
```

---

## BƯỚC 2: BIÊN DỊCH VÀ XÂY DỰNG MAVEN

- Lệnh thực thi trong `D:\choptiters_java\marketplace-ptit-be-D2`:
  ```powershell
  $env:JAVA_HOME = "C:\Users\Acer\.jdks\openjdk-26.0.2.1"
  $env:JDK_JAVAC_OPTIONS = "-proc:full"
  $env:TEST_DB_URL = "jdbc:postgresql://localhost:5432/marketplace_java_test_d2"
  .\mvnw.cmd "-Dmaven.compiler.fork=true" "-Dlombok.version=1.18.46" clean test
  ```
- Kết quả biên dịch:
  - `Compiling 320 source files with javac [forked debug parameters release 21] to target\classes`
  - `Compiling 22 source files with javac [forked debug parameters release 21] to target\test-classes`
  - **0 lỗi biên dịch**, **0 cảnh báo biên dịch (`javac`)**.
  - Lưu ý: Maven chỉ log `[WARNING]` HTTP 401 khi dò metadata prefix từ 2 repo Flyway/Redgate và JVM Java 26 có cảnh báo runtime chuẩn từ `modelmapper` (`sun.misc.Unsafe`), `byte-buddy-agent` và `spring.jpa.open-in-view` (đều thuộc cấu hình gốc Wave 0).

---

## BƯỚC 3: ĐỐI SOÁT SCHEMA VÀ MIGRATION FLYWAY

- Không thêm hoặc sửa file migration nào trong `src/main/resources/db/migration/` (toàn bộ 4 bảng `community_moments`, `tutorials`, `daily_needs`, `daily_journal_checkins` đã tồn tại sẵn trên `marketplace_java_test_d2` khớp `DB_v7.md`).
- Toàn bộ truy vấn native trong Track D2 dùng cú pháp **PostgreSQL 15+ native**:
  - `CommunityMomentQueryRepository`: sử dụng `SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:lockName))) AS _lock` để tuần tự hóa thao tác duyệt/cắt tỉa còn tối đa 5 ảnh trên Hero carousel (chỉ 1 transaction tại 1 thời điểm giữ khóa).
  - `DailyNeedQueryRepository`: sử dụng `INSERT INTO daily_needs (...) VALUES (...) ON CONFLICT (user_id, note_date) DO UPDATE ...`, `INSERT INTO daily_journal_checkins (...) VALUES (...) ON CONFLICT (user_id, checkin_date) DO NOTHING RETURNING checkin_id`, và **PostgreSQL Recursive CTE** (`WITH RECURSIVE streak_days AS (...)`) tính chuỗi check-in liên tiếp mà không giới hạn 365 ngày.
- Toàn bộ các ràng buộc DB (`uq_community_moments_user_pending`, `chk_tutorials_video_size_pos`, `chk_tutorials_duration_pos`, `chk_tutorials_sort_order_nonneg`, `uq_daily_needs_user_date`, `uq_daily_journal_checkins_user_date`) đều được kiểm chứng thực thi trên PostgreSQL thật trong integration tests.

---

## BƯỚC 4: TÍNH CHUẨN XÁC ÁNH XẠ JPA / HIBERNATE (`ddl-auto: validate`)

- Toàn bộ các integration test đều khởi chạy Hibernate với cấu hình `spring.jpa.hibernate.ddl-auto = validate` và `spring.flyway.enabled = false` trên PostgreSQL 15 (`marketplace_java_test_d2`) và pass 100%.
- Không inline enum PostgreSQL native (`UserStatus`) vào HQL; các truy vấn đều truyền tham số kiểu chuẩn qua `setParameter`.
- Không dùng `LEFT JOIN FETCH` kèm `LockModeType.PESSIMISTIC_WRITE` (PostgreSQL cấm `FOR UPDATE` trên phía nullable của outer join), nên `CommunityMomentQueryRepository.findByIdForUpdate` và `TutorialQueryRepository.findByIdForUpdate` khóa trực tiếp thực thể mục tiêu.
- Xử lý kiểu thời gian khớp schema:
  - `DailyNeed` và `DailyJournalCheckin`: các cột `timestamptz` dùng `OffsetDateTime`; ngày nhật ký `note_date` tính theo múi giờ Việt Nam `Asia/Ho_Chi_Minh`.
  - `CommunityMoment`: `created_at` / `reviewed_at` dùng `LocalDateTime` khớp bảng DB hiện tại.
  - `Tutorial`: `created_at` / `updated_at` trên `AdminTutorialResponse` dùng `OffsetDateTime`.

---

## BƯỚC 5: KIẾN TRÚC PHÂN TẦNG VÀ XỬ LÝ LỖI

- Tuân thủ kiến trúc `Controller -> Service -> Repository / QueryRepository -> Entity`:
  - Controller chỉ nhận HTTP request, kiểm tra `@Valid` / `@Validated`, giải quyết user qua `CurrentUserResolver` (`getCurrentActiveUser()`, `requireAdmin()`), gọi Service và trả về Response DTO (`record` hoặc class DTO). Tuyệt đối không trả `@Entity` trực tiếp ra API response.
  - Các method đọc dữ liệu gắn `@Transactional(readOnly = true)`; thao tác ghi gắn `@Transactional` hoặc `TransactionTemplate`.
  - Upload/xóa media (`MediaStorage.uploadCommunityMomentImage`, `MediaStorage.uploadTutorialVideo`, `MediaStorage.getVideoResource`, `MediaStorage.destroyAsset`) được đặt **bên ngoài transaction DB** để tránh giữ connection PostgreSQL khi gọi external storage; nếu ghi DB lỗi thì thực hiện dọn tài nguyên best-effort (`safeDestroyImage` / `safeDestroyVideo`).
  - Ghi notification chỉ qua `NotificationWriter.notifyAdmins(...)` (khi có moment `PENDING` mới) và `NotificationWriter.create(...)` (khi duyệt/từ chối moment).
  - Xử lý lỗi thống nhất qua `ApiException` + `ErrorHandler` trả JSON `{"detail": ...}`. Ngoại lệ duy nhất theo đúng hợp đồng Python & React FE (`CommunityUploadModal.tsx`) là `CommunityMomentPendingException` trả HTTP `409` dạng `{"detail": {"code": "COMMUNITY_MOMENT_PENDING", "message": "..."}}`.

---

## BƯỚC 6: TƯƠNG THÍCH HỢP ĐỒNG API VÀ DTO

### 6.1. Bảng đối soát Endpoint (25 endpoints)

#### A. Community Moments (7 endpoints)

| Method & Path | Phân quyền / Auth | Chức năng & Tương thích Python / `API_CONTRACT.md` | Trạng thái |
|---|---|---|---|
| `POST /api/v1/community-moments` | User ACTIVE (`authenticated`) | Upload multipart `file` (JPEG/PNG/WebP qua `MediaValidator` + `MediaStorage.uploadCommunityMomentImage`) + `note` (NFC, 1 dòng, ≤ 80 codepoints); partial unique index `PENDING`; 409 trả `detail.code = COMMUNITY_MOMENT_PENDING`; `NotificationWriter.notifyAdmins`. | Đạt parity |
| `GET /api/v1/community-moments/hero` | Public (`permitAll`) | Tối đa 5 ảnh `APPROVED` mới nhất, trả về theo thứ tự cũ → mới (`reviewed_at ASC`), dùng `MediaStorage.heroImageUrl`, chỉ gồm `moment_id`, `image_url`, `note` (không lộ PII). | Đạt parity |
| `GET /api/v1/admin/community-moments` | Admin (`requireAdmin` → 403) | Lọc `status` (mặc định `PENDING`), `limit` [1..50], keyset cursor Base64 (`createdAt\|momentId`), cursor lỗi → 400 `"Invalid pagination cursor."`. | Đạt parity |
| `GET /api/v1/admin/community-moments/{momentId}` | Admin (`requireAdmin` → 403) | Chi tiết moment gồm `thumbnail_url`, `detail_image_url`, `uploader`, `reviewer`; không thấy → 404. | Đạt parity |
| `POST /api/v1/admin/community-moments/{momentId}/approve` | Admin (`requireAdmin` → 403) | Advisory lock + `PESSIMISTIC_WRITE`; chuyển `APPROVED`; tự cắt tỉa ảnh `APPROVED` cũ nhất nếu vượt 5 (xóa DB + `destroyAsset`); gửi notification `COMMUNITY_MOMENT`; trả `AdminCommunityMomentItem` (khớp FE/Python). | Đạt parity |
| `POST /api/v1/admin/community-moments/{momentId}/reject` | Admin (`requireAdmin` → 403) | Chỉ từ chối trạng thái `PENDING` (khác → 409); xóa bản ghi khỏi DB + `destroyAsset`; gửi notification `COMMUNITY_REJECTED`; trả `{moment_id, status: "REJECTED", message}`. | Đạt parity |
| `DELETE /api/v1/admin/community-moments/{momentId}` | Admin (`requireAdmin` → 403) | Chỉ xóa bản ghi `APPROVED` (khác → 409); xóa khỏi DB + `destroyAsset`; trả `{moment_id, status: "DELETED", message}`. | Đạt parity |

#### B. Tutorials (11 endpoints: 1 public + 10 admin)

| Method & Path | Phân quyền / Auth | Chức năng & Tương thích Python / `API_CONTRACT.md` | Trạng thái |
|---|---|---|---|
| `GET /api/v1/tutorials` | Public (`permitAll`) | Lọc optional `feature_key`, chỉ trả bài `is_published = true`, sắp theo `feature_key, sort_order ASC, tutorial_id ASC`; DTO `PublicTutorialResponse` có đủ `duration_ms`, `thumbnail_url` (`tutorialThumbnailUrl`), `video_url` (`tutorialVideoDeliveryUrl`). | Đạt parity |
| `POST /api/v1/admin/tutorials/upload-signature` | Admin | Sinh chữ ký upload trực tiếp qua `MediaStorage.generateTutorialUploadSignature("tut_up_" + uuid32)`. | Đạt parity |
| `POST /api/v1/admin/tutorials/create-direct` | Admin | Xác thực `public_id` khớp `upload_session_id`, kiểm tra trùng `video_storage_key` (400), xác thực video phía server qua `MediaStorage.getVideoResource`, gán `sort_order = max + 10`, tự cleanup asset nếu DB lỗi. | Đạt parity* |
| `GET /api/v1/admin/tutorials` | Admin | Liệt kê tất cả bài hướng dẫn (cả chưa publish), lọc optional `feature_key`; trả `AdminTutorialResponse` đầy đủ `video_storage_key`, `video_mime_type`, `OffsetDateTime`. | Đạt parity |
| `GET /api/v1/admin/tutorials/{tutorialId}` | Admin | Chi tiết 1 tutorial admin (không thấy → 404). | Đạt parity |
| `POST /api/v1/admin/tutorials` | Admin | Tạo tutorial bằng multipart field `video` + form params; kiểm tra `feature_key`, `title`, `description`, `action_key`; upload qua `MediaStorage.uploadTutorialVideo`. | Đạt parity |
| `PATCH /api/v1/admin/tutorials/reorder` | Admin | Sắp xếp lại thứ tự trong cùng `feature_key`; chặn rỗng/trùng ID (422), sai ID hoặc khác `feature_key` (400); cập nhật bước 10 (`10, 20,...`) và trả `List<AdminTutorialResponse>` (200). | Đạt parity |
| `PATCH /api/v1/admin/tutorials/{tutorialId}` | Admin | Cập nhật metadata/publish/chuyển `feature_key` (tự gán `sort_order = max + 10` ở feature mới); dùng `@JsonSetter` phân biệt field bị bỏ qua vs truyền tường minh `null` để xóa `description`/`action_key`. | Đạt parity |
| `POST /api/v1/admin/tutorials/{tutorialId}/video` | Admin | Thay thế video qua multipart `video`; cập nhật URL/key/size/duration và dọn video cũ best-effort. | Đạt parity |
| `POST /api/v1/admin/tutorials/{tutorialId}/confirm-direct-video` | Admin | Xác nhận video direct upload thay thế; idempotent nếu gửi lại cùng `public_id` (400 nếu thuộc tutorial khác); xóa video cũ khi thay thành công. | Đạt parity* |
| `DELETE /api/v1/admin/tutorials/{tutorialId}` | Admin | Xóa cứng DB + dọn asset best-effort; trả `200 {"message": "Đã xóa Tutorial thành công"}` khớp Python/FE. | Đạt parity |

#### C. Daily Needs (7 endpoints)

| Method & Path | Phân quyền / Auth | Chức năng & Tương thích Python / `API_CONTRACT.md` | Trạng thái |
|---|---|---|---|
| `GET /api/v1/daily-needs/recent` | Public (`permitAll`) | Feed ghi chú gần đây của user `ACTIVE`, giới hạn `limit` clamp `[1..10]`, sắp `note_date DESC, updated_at DESC`; trả `DailyNeedListResponse` (`{"items": [...]}`). | Đạt parity |
| `GET /api/v1/daily-needs/today` | Public (`permitAll`) | Feed ghi chú của ngày hôm nay (theo `Asia/Ho_Chi_Minh`) của user `ACTIVE`, tối đa 10 bản ghi; trả `DailyNeedListResponse`. | Đạt parity |
| `GET /api/v1/daily-needs/me/today` | User ACTIVE | Ghi chú hôm nay của chính mình; trả HTTP `200` với JSON `null` nếu chưa tạo. | Đạt parity |
| `GET /api/v1/daily-needs/me/summary` | User ACTIVE | Trả `today_journal`, `has_journal_today`, `has_checked_in_today`, `is_checked_in_today`, `streak_count` (tính theo ngày VN bằng Recursive CTE: tính ngược từ `today` nếu đã check-in hôm nay hoặc từ `yesterday` nếu chưa). | Đạt parity |
| `GET /api/v1/daily-needs/users/{publicId}/today` | Public (`permitAll`) | Xem ghi chú hôm nay theo `public_id` (user không tồn tại / không `ACTIVE` → 404); trả HTTP `200` với JSON `null` nếu hôm nay user không có ghi chú. | Đạt parity |
| `PUT /api/v1/daily-needs/me/today` | User ACTIVE | Chuẩn hóa whitespace & độ dài ≤ 120 codepoints (422 nếu sai); atomic upsert (`ON CONFLICT DO UPDATE`) + check-in (`ON CONFLICT DO NOTHING RETURNING checkin_id`) với retry tối đa 2 lần nếu va chạm unique constraint, trả `checkin_created`. | Đạt parity |
| `DELETE /api/v1/daily-needs/me/today` | User ACTIVE | Xóa ghi chú hôm nay của bản thân (HTTP `204`), giữ nguyên bản ghi `daily_journal_checkins` để bảo toàn streak ngày đó. | Đạt parity |

### 6.2. Khác biệt còn tồn đọng / lưu ý kỹ thuật giữa Java và Python

1. **Chưa kiểm tra kích thước khung hình dọc 9:16 của video Tutorial phía server (`create-direct`, `confirm-direct-video`)**:
   - Giao diện `MediaStorage.VideoResource(publicId, secureUrl, format, bytes, durationSeconds)` của Wave 0 hiện không mang trường `width` và `height`. Do đó `TutorialService` chưa thể kiểm tra `height > width` ở phía server như `tutorials/service.py` của Python.
   - Tương tự, `LocalMediaStorage` (profile `test`) trả `durationSeconds = null` khi upload/đọc video; vì vậy `TutorialService` chỉ từ chối khi `durationMs` non-null `<= 0`, còn trường hợp `null` (khi storage test không cung cấp duration và client không gửi `duration_ms`) vẫn được DB cho phép do cột `tutorials.duration_ms` là nullable (`chk_tutorials_duration_pos`: `duration_ms IS NULL OR duration_ms > 0`).
2. **Bean `MediaStorage` thực tế (Cloudinary) thuộc phạm vi Track E**:
   - Trong ngữ cảnh hiện tại, profile `test` dùng `LocalMediaStorage`; môi trường default chưa có bean Cloudinary thật (`UnconfiguredMediaStorage` trả `503`).
3. **Kiểm tra byte ảnh cộng đồng (`MediaValidator.validateImageBytes`)**:
   - Java dùng `MediaValidator` của Wave 0 (kiểm tra dung lượng và magic header MIME nằm trong `ALLOWED_COMMUNITY_IMAGE_MIMES`), không dùng Pillow giải mã toàn bộ pixel ảnh như Python; các trường hợp ảnh rỗng hoặc file giả mạo JPEG/PNG/WebP đã được test từ chối với HTTP `422`.
4. **Hành vi `API_CONTRACT.md` so với code Python & React FE thực tế**:
   - `community_moments`: Python/FE thực tế chỉ có 7 endpoint ở bảng trên (không có public list ngoài `/hero` hay endpoint `/me`).
   - `POST /api/v1/admin/community-moments/{id}/approve` trả `AdminCommunityMomentItem` khớp Python `admin_router.py` & FE `AdminMomentDetailModal` (thay cho skeleton ban đầu).
   - `PATCH /api/v1/admin/tutorials/reorder` trả `200 List<AdminTutorialResponse>` và `DELETE /api/v1/admin/tutorials/{id}` trả `200 {"message": "Đã xóa Tutorial thành công"}` khớp Python/FE (thay cho 204).

---

## BƯỚC 7: KIỂM THỬ THỰC CHẤT VỚI DATABASE THẬT

### 7.1. Chạy riêng bộ test của Track D2 (lệnh chuẩn Section 0)
```powershell
.\mvnw.cmd "-Dmaven.compiler.fork=true" "-Dlombok.version=1.18.46" "-Dtest=CommunityMomentControllerIntegrationTest,TutorialControllerIntegrationTest,DailyNeedControllerIntegrationTest" test
```
- **Kết quả**: **Tests run: 17, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** (`CommunityMomentControllerIntegrationTest`: 7, `DailyNeedControllerIntegrationTest`: 5, `TutorialControllerIntegrationTest`: 5).

### 7.2. Chạy toàn bộ suite (188 tests) trên `marketplace_java_test_d2`
1. **Lần chạy với lệnh Section 0 mặc định (không đặt giới hạn Hikari pool)**:
   - Kết quả: `Tests run: 188, Failures: 0, Errors: 66, Skipped: 0 — BUILD FAILURE`.
   - Toàn bộ 66 errors đều rơi vào lỗi khởi tạo `ApplicationContext` do `org.postgresql.util.PSQLException: FATAL: sorry, too many clients already` tại `SecurityIntegrationTest` (23), `TutorialControllerIntegrationTest` (5), `UserControllerIntegrationTest` (38).
   - **Phân tích nguyên nhân gốc rễ**: PostgreSQL Docker `sm_postgres` có `max_connections = 100` (dùng chung cho 6 track chạy song song). Trong repo hiện tại (Wave 0 + PR03/PR04), bộ test gốc có ~10 `ApplicationContext` cache key khác nhau được giữ sống trong cùng JVM; mỗi pool Hikari mặc định lấy `10` connection (= `100` connection chỉ riêng 1 JVM chưa tính các track song song khác). Track D2 đã gắn `@TestPropertySource(properties = {"spring.datasource.hikari.maximum-pool-size=3", "spring.datasource.hikari.minimum-idle=1"})` và `@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)` cho cả 3 class test của D2 (log xác nhận `HikariPool-3` và `HikariPool-4` shutdown ngay sau khi chạy xong class).
2. **Lần chạy toàn bộ suite với giới hạn Hikari pool qua biến môi trường** (`$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE = "2"`; `$env:SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE = "1"`):
   - **Kết quả**: **Tests run: 188, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** (`Total time: 02:53 min`).
   - Chi tiết từng class test:
     - `CategoryControllerIntegrationTest`: 9 PASS
     - `CategoryWriteIntegrationTest`: 4 PASS
     - `CommunityMomentControllerIntegrationTest`: 7 PASS *(Track D2)*
     - `DailyNeedControllerIntegrationTest`: 5 PASS *(Track D2)*
     - `DevSchemaReadOnlyValidationTest`: 1 PASS
     - `FavoriteControllerIntegrationTest`: 8 PASS
     - `MarketplaceApplicationTests`: 1 PASS
     - `NotificationWriterIntegrationTest`: 3 PASS
     - `AdminProductApprovalIntegrationTest`: 6 PASS
     - `ProductControllerIntegrationTest`: 20 PASS
     - `MediaValidatorTest`: 7 PASS
     - `FirebaseJwtValidatorTest`: 19 PASS
     - `SecurityIntegrationTest`: 23 PASS
     - `TutorialControllerIntegrationTest`: 5 PASS *(Track D2)*
     - `UserControllerIntegrationTest`: 38 PASS
     - `UserIdentityResolutionIntegrationTest`: 18 PASS
     - `UserReadOnlyRepositoryTest`: 3 PASS
     - `UserWriteIntegrationTest`: 4 PASS
     - `Wave0EntityMappingIntegrationTest`: 2 PASS
     - `WorkerRetryExecutorTest`: 5 PASS

### 7.3. Trạng thái `marketplace_java_test_d2` sau test
Kiểm tra trực tiếp `COUNT(*)` trên toàn bộ 35 bảng thuộc schema `public` của `marketplace_java_test_d2` sau khi suite kết thúc:
- **34/35 bảng dữ liệu/nghiệp vụ** (`users`, `user_auth_identities`, `categories`, `products`, `community_moments`, `tutorials`, `daily_needs`, `daily_journal_checkins`, `notifications`, `favorites`, `orders`, `auctions`, v.v.): **`exact_rows = 0`**.
- Chỉ có bảng metadata `alembic_version` có `1` dòng (version stamp schema có sẵn khi clone DB mẫu).

---

## BƯỚC 8: GIẢI THÍCH KỸ THUẬT & VIỆC CẦN NGƯỜI ĐIỀU PHỐI

- **Đồng bộ bộ nhớ**: Không sửa `docs/PROJECT_STATE.md` hay `docs/PROJECT_HISTORY.md` theo đúng ranh giới quy định cho Track D2.
- **Rủi ro / Việc cần người điều phối xử lý**:
  1. **Giới hạn kết nối PostgreSQL khi chạy full test suite (`too many clients already`)**: Đề xuất người điều phối cân nhắc cấu hình `spring.datasource.hikari.maximum-pool-size` / `minimum-idle` nhỏ hơn (ví dụ `3` / `1`) trong `src/test/resources/application-test.yml` hoặc tăng `max_connections` trên container `sm_postgres` (hiện là `100` chia sẻ giữa các track song song), để lệnh `.\mvnw.cmd clean test` không cần biến môi trường bổ sung vẫn vượt qua toàn bộ 188 test khi các track chạy đồng thời.
  2. **Kiểm tra tỷ lệ 9:16 và duration video khi tích hợp Cloudinary thật (Track E)**: Cần mở rộng record `MediaStorage.VideoResource` thêm `width`, `height` (và cập nhật `CloudinaryMediaStorage`) nếu muốn bật ràng buộc server-side cho video dọc 9:16 ở `TutorialService`.
  3. **Kiểm tra DB dev/staging khi tích hợp**: Trước khi bật trên `marketplace_java_dev` hoặc staging, đảm bảo `DevSchemaReadOnlyValidationTest` tiếp tục pass sau khi gộp các track còn lại (A/B/C/D1/E) và kiểm chứng Cloudinary credentials thật cho `tutorialVideoDeliveryUrl` / `tutorialThumbnailUrl` / `heroImageUrl`.
