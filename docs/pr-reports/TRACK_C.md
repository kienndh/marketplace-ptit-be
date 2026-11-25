# BÁO CÁO TRACK C — REVIEW + REPORT + ADMIN USERS

Nhánh: `track/C-review-report-admin` · Worktree: `marketplace-ptit-be-C` · DB test: `marketplace_java_test_c`
Chuẩn đối chiếu: Python `reviews/*`, `reports/*`, `users/admin_*`, `API_CONTRACT.md` (mục 8, 19–26, 35). Python lấy nguyên trạng (có thay đổi chưa commit), không sửa.

## Bước 1 — Ranh giới workspace & Git
- Chỉ làm trong worktree C, nhánh `track/C-review-report-admin`; không đụng `student-marketplace-be/`, `student-marketplace-fe/`, `marketplace-ptit-be` (main) hay worktree track khác.
- **File Wave 0 bị sửa duy nhất:** `configuration/SecurityConfig.java` (+7 dòng, chỉ BÊN TRONG khối `// >>> TRACK C … // <<< TRACK C`). Không sửa `pom.xml`, `application*.yml`, `PROJECT_STATE/HISTORY`, entity, enum, `*Repository` cơ bản, interface dùng chung, và không sửa `UserController/UserService/User/UserRepository`.
- Skeleton của track (cho phép viết lại): `ReviewController/Service`, `ReportController/Service`, `AdminReportController` và các DTO trong `review/`, `report/`. Đã xoá `ReviewCreateRequest`, `ReviewUpdateRequest` (thay bằng parser `JsonNode`).
- `git diff --stat main..HEAD` (sau commit): xem cuối tài liệu.

### File thêm / sửa
| Gói | File |
|---|---|
| `review/` | `ReviewService`, `ReviewController`, `ReviewQueryRepository` (mới), `ReviewPayloadParser` (mới), DTO `OrderReviewResponse`, `PublicSellerReviewItem`, `PublicSellerReviewsCursorPage` (viết lại) |
| `report/` | `ReportService`, `AdminReportService` (mới), `ReportQueryRepository` (mới), `ReportEvidenceStreamer` (mới), `AfterCommit` (mới), `ReportController`, `AdminReportController`, `AdminUserReportHistoryController` (mới), `AdminReportQueueState` (mới), `ReportSummaries` (mới) + DTO `ReportCreateRequest`, `AdminEnforcementRequest`, `AdminReportStatusUpdateRequest`, `ReportResponse`, `ReportEvidenceUploadResponse`, `AdminReportResponse`, `AdminReportDetailResponse`, `AdminReportListResponse`, `SubjectReportHistoryResponse` |
| `user/` (chỉ file MỚI) | `AdminUserController`, `AdminUserService`, `AdminUserQueryRepository`, `AdminUserDtos`, `UserSearchService` |
| `configuration/` | `SecurityConfig` (khối TRACK C) |
| test | `review/ReviewIntegrationTest`, `report/ReportIntegrationTest`, `report/ReportModerationIntegrationTest`, `user/AdminUserIntegrationTest`, `trackc/TrackCFixtures`, `trackc/TrackCTestConfig` |

## Bước 2 — Build
`.\mvnw.cmd "-Dmaven.compiler.fork=true" "-Dlombok.version=1.18.46" clean test` (JDK 26, `-proc:full`): **BUILD SUCCESS**, 0 lỗi biên dịch. Lưu ý: Spring Boot 4 dùng **Jackson 3** (`tools.jackson.databind.JsonNode`; annotation vẫn `com.fasterxml.jackson.annotation`).

## Bước 3 — Schema / entity
Không migration, không đổi entity; `ddl-auto: validate` giữ nguyên. Toàn bộ truy vấn enum dùng `setParameter` (tránh lỗi `type "userstatus" does not exist`); cột `user_status` chỉ so sánh bằng tham số hoặc đọc qua `::text` trong test.

## Bước 4 — Kiến trúc & lỗi
- Controller → Service → Repository(Query) → Entity; không trả Entity; lỗi qua `ApiException`/`ErrorHandler` (`{"detail": …}`), JSON snake_case như Python.
- ADMIN kiểm bằng `CurrentUserResolver.requireAdmin()` (403 "Yêu cầu quyền quản trị viên." — cùng cách với `AdminProductApprovalController`).
- Thứ tự khoá cố định **Report → User → Product → Auction** (`PESSIMISTIC_WRITE`).
- Tác dụng phụ **sau commit, best-effort** (`AfterCommit`): `UserEventPublisher.revokeUserSessions` khi ban; `AuctionEventPublisher.publishAuctionCancelled` khi ẩn tin/hạn chế SELL. Notification chỉ qua `NotificationWriter` (`create`, `notifyAdmins`). Huỷ đấu giá thao tác trực tiếp entity, **không** gọi `OrderService/AuctionService`.
- Media chỉ qua `MediaValidator.validateMediaEvidenceBytes` + `MediaStorage.uploadReportEvidence`; upload 2 pha (validate hết → upload ngoài khoá DB → transaction khoá Report FOR UPDATE kiểm tra lại → bù trừ `destroyAsset` khi lỗi).

## Bước 5 — Parity API (đối chiếu Python / `API_CONTRACT.md`)
| Endpoint | Trạng thái |
|---|---|
| `POST /api/v1/orders/{id}/review` (201; 400 chưa COMPLETED; 403 seller; 404 ngoài đơn; 409 trùng; 422 payload; notification `NEW_REVIEW`) | ✅ |
| `GET /api/v1/orders/{id}/review` (200 `null` khi chưa có) | ✅ |
| `PATCH /api/v1/orders/{id}/review` (`{}`/`rating:null` → 422, `comment:null` xoá, không notification mới) | ✅ |
| `GET /api/v1/users/{public_id}/reviews` (công khai, ẩn danh, keyset cursor, chỉ seller ACTIVE) | ✅ |
| `POST /api/v1/reports` (ma trận lý do, USER qua `target_public_id`, 400/404/409/422, notify admins) | ✅ |
| `POST /api/v1/reports/{id}/evidence` (multipart `files`; 5 ảnh + 1 video; 404 không lộ; 409 terminal) | ✅ (xem rủi ro multipart) |
| `GET /api/v1/admin/reports` (queue_state/status/workflow/conclusion/target_type, cursor) | ✅ |
| `GET /api/v1/admin/reports/{id}` (reported_subject, enforcement_actions, available_*) | ✅ |
| `PATCH /api/v1/admin/reports/{id}/status` | ✅ |
| `POST /api/v1/admin/reports/{id}/enforcement` (USER_BAN/UNBAN, PRODUCT_HIDE/RESTORE, CAPABILITY_RESTRICT/RESTORE → BUY/SELL_RESTRICT/RESTORE) | ✅ |
| `GET /api/v1/admin/reports/{id}/evidence/{attachment_id}` (SSRF: https + `res.cloudinary.com`, không redirect, Range cho video) | ✅ (không hỗ trợ `?token=`) |
| `GET /api/v1/admin/users/{id}/report-history` (RECEIVED/SENT) | ✅ |
| `GET /api/v1/admin/users`, `/{id}`, `/{id}/moderation-history` | ✅ (theo Python: `can_buy`, `report_count`… phẳng) |
| `GET /api/v1/users/search` | ⚠️ Đã có `UserSearchService`, **chưa nối** (xem dưới) |

### Khác biệt / tồn đọng
1. **`/users/search`**: route + `UserService.searchUsers` (TODO) nằm trong file `user/` hiện có, Track C không được sửa. Cần người điều phối đổi `UserService.searchUsers` → gọi `UserSearchService.search(currentUserId, q, limit, conversationId)` (và controller truyền `conversation_id`). Logic đã test ở mức service.
2. **"Đổi vai trò"** (prompt) không tồn tại ở Python `admin_*` → không hiện thực (đúng parity). Ban/unban và chế tài BUY/SELL thực hiện qua `/admin/reports/{id}/enforcement` đúng như Python.
3. **`?token=`** cho evidence admin (thẻ `<video>`) cần `BearerTokenResolver` ở `SecurityConfig` ngoài khối Track C → chưa làm.
4. Contract 23.2 nhắc `/enforce`; Python thật chỉ có `/enforcement` → theo Python.
5. `extra="forbid"` hiện thực bằng `JsonNode` (422 thông điệp tiếng Việt, khác định dạng lỗi Pydantic); validate enum sai ở query param trả 422 chung "Invalid value for request parameter.".
6. Chưa kiểm thử ca video > 60 giây (cần `MediaStorage` trả `durationSeconds`; `LocalMediaStorage` luôn null). Nhánh code đã có (huỷ asset + 422).
7. Rate limit (`rate_limit_user_profile`, `rate_limit_write_mutation`) của Python chưa có trong Java (ngoài phạm vi track).

## Bước 6 — Kiểm thử với PostgreSQL thật
**Toàn bộ suite** trên `marketplace_java_test_c`: **201 test, 0 fail, 0 error, 0 skipped — BUILD SUCCESS.**
Test mới của Track C: Review 11, Report 7, Moderation 8, AdminUser 4 (= 30). Sau suite, đếm lại các bảng users/reports/reviews/orders/notifications/moderation_actions/user_capability_restrictions/products/auctions/categories/report_attachments = **0 dòng**. Safety check DB theo regex `marketplace_java_test(_[a-z0-9]+)?`.
Ràng buộc kiểm chứng thật trên DB: UNIQUE `ux_reviews_order_reviewer`, CHECK `chk_orders_buyer_not_seller` & `chk_reviews_reviewer_not_reviewee`, FK/CHECK moderation + capability, khoá `FOR UPDATE`.
Ghi chú: ca "tự đánh giá" không dựng được qua API vì chính DB chặn đơn buyer = seller → test chuyển thành kiểm chứng CHECK (không đổi nghiệp vụ).

## Bước 7 — Rủi ro / việc cho người điều phối
1. **Multipart limit**: `spring.servlet.multipart.max-file-size`/`max-request-size` mặc định 1MB/10MB sẽ chặn ảnh 10MB/video 30MB. Cần đặt ≥ 30MB / ≥ 60MB trong `application.yml` (track không được sửa).
2. **Múi giờ JVM**: máy dev ở UTC+7 trong khi `hibernate.jdbc.time_zone=UTC`; Hibernate ghi `LocalDateTime` lệch −7h so với UTC thật (SQL thô không lệch). Mọi cột `timestamp` (không tz) bị ảnh hưởng, trong đó có so sánh `starts_at/expires_at` của restriction. Triển khai nên chạy JVM với `-Duser.timezone=UTC`; test Track C tạo restriction qua entity để nhất quán.
3. **Cạn kết nối Postgres** khi nhiều context Spring cùng sống (max 100, các track dùng chung server): test Track C đã giới hạn Hikari = 3; đề nghị đặt pool nhỏ trong `application-test.yml`.
4. `@Import(TrackCTestConfig)` thay publisher bằng bean ghi nhận trong test; Track E thay bean thật thì không ảnh hưởng.
5. Dùng chung entity `Order/Auction/Product` chỉ qua entity/HQL; nếu Track A đổi tên trường các entity này sẽ phải cập nhật `ReportQueryRepository`.

## Bước 8 — Quyết định kỹ thuật
Parser `JsonNode` thay `@Valid` để phân biệt "không gửi" vs `null` (PATCH) và cấm trường thừa; DTO response viết lại theo Python (`report_id`, `workflow_state`, …) thay cho skeleton sai (`id`, `action_type`); danh sách ẩn danh theo contract 8.4 (bỏ `reviewer_name/avatar`); upload bằng chứng 2 pha để không giữ khoá DB khi gọi mạng.

**Điểm dừng phê duyệt:** chờ xác nhận ("Proceed"/"Đồng ý") trước khi merge/chuyển task.
