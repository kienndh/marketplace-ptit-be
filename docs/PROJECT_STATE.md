# TRẠNG THÁI DỰ ÁN JAVA (PROJECT STATE) — Chợ PTITers Backend Migration
# Repository: marketplace-ptit-be

> **Vai trò tài liệu**: Bộ nhớ ngắn hạn (Short-Term Memory) cho kỹ sư phát triển và AI coding agent, ghi nhận trạng thái hiện tại, các quyết định kiến trúc đang hiệu lực, rủi ro kỹ thuật và kế hoạch hành động tiếp theo cho dự án di trú backend Java Spring Boot.

---

## 1. Current Phase (Giai đoạn hiện tại)
**Wave 2 — Gộp 6 track + tích hợp toàn hệ thống** — *Hoàn thành* (2026-11-25). Chuyển đổi Python → Java đạt **153/153 endpoint HTTP + 2/2 WebSocket** về mặt route (xem mục 3b).
- **Cột mốc đã xong**: PR01 → PR04 (Category, User/Auth, Product, Favorite) → Wave 0 (nền tảng dùng chung) → Wave 1 (6 track song song A, B, C, D1, D2, E) → **Wave 2** (gộp bằng `git merge --squash` theo thứ tự E → A → C → D1 → D2 → B, rồi tích hợp).
- **Cột mốc tiếp theo**: kiểm thử parity hành vi với Python trên dữ liệu thật (ngoài phạm vi route), rate limiting HTTP, nâng dev DB, cài JDK 21 (xem mục 9–10).

---

## 2. Current Task (Công việc hiện tại)
**Wave 2 — Gộp & tích hợp** (đã hoàn tất, chờ người dùng duyệt):
- [x] Gộp 6 track bằng `git merge --squash` (1 commit mỗi track, tác giả theo `git config`, KHÔNG có trailer AI), `.\mvnw.cmd clean compile` sau mỗi track; xung đột chỉ ở `SecurityConfig` (giữ đủ rule mọi track, các khối TRACK đứng sau rule OPTIONS và trước catch-all, không wildcard nào nuốt route module khác).
- [x] Tích hợp (commit `feat(integration)`):
  1. `application-test.yml`: Hikari `maximum-pool-size: 2`, `minimum-idle: 1`; gỡ mọi override pool riêng trong 11 class test → `clean test` mặc định xanh, không cần biến môi trường.
  2. UTC: `TimeZone.setDefault(UTC)` trong `MarketplaceApplication.main` + `-Duser.timezone=UTC` trong `argLine` surefire; test `UtcTimestampIntegrationTest` đọc `created_at::text` bằng SQL thô và so với UTC (không còn fixture nào bù giờ thủ công). Đã thử boot với `-Duser.timezone=Asia/Saigon` → code vẫn ép về UTC.
  3. `spring.servlet.multipart.max-file-size: 50MB`, `max-request-size: 60MB` (giới hạn từng loại file vẫn do `MediaValidator`/`ChatAttachmentValidator`).
  4. `GET /users/search`: `UserService.searchUsers` → `UserSearchService.search`; tham số `q` (min 2), `limit` (mặc định 20, 1..50), `conversation_id`; bỏ `UnsupportedOperationException`; bỏ trần 20 kết quả ở repository (Python không cắt).
  5. Chat: `download-ticket` (Redis, key `download_ticket:<sha256>`, 60s, dùng 1 lần qua GETDEL) và `download` (ticket hoặc Bearer; kiểm tra lại quyền thành viên + `joined_at` nhóm tại thời điểm tải; chặn SSRF chỉ https + `res.cloudinary.com`, không theo redirect; `Content-Disposition` RFC 5987); `NotificationWriter.upsertUnread` giữ 1 notification `NEW_MESSAGE` chưa đọc cho mỗi (user, hội thoại) và làm mới `title/body/created_at` như Python; `PresenceService` bản Redis (Track E) tự thay bản NoOp khi có cấu hình Redis.
  6. `marketplace.chat.official-support-group-id: ${OFFICIAL_SUPPORT_GROUP_ID:0}` (mặc định 0 → 503 như Python).
  7. Admin xem bằng chứng report nhận `?token=` như Python: `BearerTokenResolver` trong `SecurityConfig` **chỉ** cho `GET /api/v1/admin/reports/{id}/evidence/{id}` (quyền ADMIN vẫn kiểm ở controller). *Đây là chỗ sửa `SecurityConfig` ngoài khối track.*
  8. Tiêu đề notification đấu giá giữ theo code Python (xem mục 9, rủi ro #9).
  9. Dọn: xoá 2 skeleton Wave 0 không dùng (`order/OrderWebSocketHandler`, `auction/AuctionWebSocketHandler`); tắt Redis repositories (`spring.data.redis.repositories.enabled: false`).
- [x] `scripts/route_parity.py` + `docs/ROUTE_PARITY.md` (sinh tự động): 153/153 HTTP, 2/2 WS, 0 route chỉ có ở Java.
- [x] Boot thử profile mặc định (`marketplace_java_dev`, `ddl-auto: validate`, Redis local `sm_redis`): `Started MarketplaceApplication`; chỉ gọi `GET /categories` (200) và 2 route 401; đã tắt app.

---

## 3. Status (Trạng thái)
- **Biên dịch (`.\mvnw.cmd clean compile`)**: `BUILD SUCCESS`.
- **Kiểm thử (`.\mvnw.cmd clean test`, không đặt biến môi trường nào)**: `BUILD SUCCESS` — **321/321 PASS**, 0 failures, 0 errors, 0 skipped (40 test class), chạy trên `marketplace_java_test` (pool Hikari 2/1).
- **Flyway**: TẮT an toàn (`spring.flyway.enabled: false`). **Hibernate**: `ddl-auto: validate` (đã xác nhận bằng boot trên `marketplace_java_dev`).
- **Worker/scheduler**: cờ mặc định TẮT (xem báo cáo từng track).

### 3b. Bảng parity endpoint (route Python ↔ Java; chi tiết từng dòng: `docs/ROUTE_PARITY.md`)
| Module (Python) | HTTP | WS | Java |
|---|---|---|---|
| categories | 2 | | Done |
| users (+admin users) | 10 + 3 | | Done (`/users/search` đã nối) |
| products (+admin approvals) | 12 + 3 | | Done |
| favorites | 4 | | Done |
| orders | 11 | `/ws/me` | Done (Track A + E) |
| auctions | 9 | `/ws/auctions/{id}` | Done (Track A + E) |
| reviews | 4 | | Done (Track C) |
| reports (+admin reports/users) | 2 + 6 | | Done (Track C) |
| conversations | 35 | | Done (Track B; `download-ticket`/`download` ở Wave 2) |
| notifications | 7 | | Done (Track D1) |
| feedback (+admin feedback/support/claims) | 8 + 4 + 5 + 3 | | Done (Track D1) |
| community moments (+admin) | 2 + 5 | | Done (Track D2) |
| tutorials (+admin) | 1 + 10 | | Done (Track D2) |
| daily needs | 7 | | Done (Track D2) |
| **Tổng** | **153/153** | **2/2** | |

> Lưu ý: parity ở đây là **route (method + path)**; hành vi từng endpoint được kiểm bằng integration test của từng track (báo cáo tại `docs/pr-reports/TRACK_*.md`), chưa có đối chiếu response-by-response với Python trên cùng dữ liệu.

---

## 4. Production Environment & Reference Stack (Môi trường tham chiếu & ranh giới)
- **Production Reference Backend (Python FastAPI)**:
  - Repository: `student-marketplace-be/` (READ-ONLY).
  - CSDL chuẩn: PostgreSQL 15+ (tài liệu đặc tả: `student-marketplace-be/docs/DB_v7.md`).
  - Hợp đồng API: `student-marketplace-be/docs/API_CONTRACT.md`.
  - Môi trường Production Live Hiện tại: **VPS do giảng viên cung cấp (Teacher-provided VPS)**, phục vụ domain `choptiters.id.vn`. *(Lưu ý lịch sử: Nền tảng Railway trước đây là thông tin triển khai trong quá khứ / historical deployment information)*.
  - Cơ chế Triển khai Production: Quy trình deploy lên VPS và việc tự động redeploy khi push GitHub là **UNVERIFIED** (cấm tự suy đoán rằng push GitHub sẽ tự động redeploy VPS).
  - **TUYỆT ĐỐI KHÔNG KẾT NỐI HOẶC TÁC ĐỘNG TỚI PRODUCTION**.
- **Reference Frontend (React TypeScript)**:
  - Repository: `student-marketplace-fe/` (READ-ONLY).
- **Target Java Environment (Môi trường mục tiêu)**:
  - Runtime: Java 21 LTS (OpenJDK 21.0.12.1).
  - Framework: Spring Boot 4.1.1 baseline, Spring Data JPA, Hibernate ORM 7.4.5.Final, Flyway Core 12.4.0 (Community).
  - CSDL Cục bộ (Local DBs):
    - `marketplace_java_dev`: READ-ONLY snapshot dữ liệu phát triển (`alembic_version: b8c9d0e1f2a3`).
    - `marketplace_java_test`: ISOLATED TEST-WRITE ONLY (schema-only, tự động rollback).
    - `student_marketplace`: READ-ONLY nguồn Python (`alembic_version: b8c9d0e1f2a3`).
  - **Dựng môi trường local trên máy MỚI** (`compose.yaml`: `postgres:15-alpine` → `sm_postgres:5432`, `redis:7-alpine` → `sm_redis:6379`; user `postgres`, mật khẩu mặc định `dev_password`, DB mặc định `marketplace_java_dev`; đổi qua biến `POSTGRES_USER`/`POSTGRES_PASSWORD`/`POSTGRES_DB`):
    ```powershell
    docker compose up -d
    ```
    Sau đó tạo thêm DB `marketplace_java_test` / `marketplace_java_e2e` nếu cần (Flyway/Alembic sẽ áp schema khi chạy app/test).
  - ⚠️ **CẢNH BÁO**: KHÔNG chạy `docker compose up`/`down`/`rm` trên máy đã có sẵn container `sm_postgres`/`sm_redis` (trùng `container_name`, và `down -v`/tạo lại sẽ mất `marketplace_java_dev`, `marketplace_java_test`, `marketplace_java_e2e`).

---

## 5. Release Readiness Checklist (Bảng kiểm soát sẵn sàng)
> Quy ước trạng thái: `CONFIRMED` (Đã xác nhận), `TODO` (Cần làm).

- [x] **PR01-A: PostgreSQL JDBC Foundation & Config**: `CONFIRMED`.
- [x] **PR01-B: User Entity Read Parity (UUID, Native Enum, JSON, Boolean)**: `CONFIRMED`.
- [x] **PR01-C: Isolated Database Write Verification (User & Category)**: `CONFIRMED`.
- [x] **PR01-D1: Flyway Migration Foundation & Legacy MySQL Isolation**: `CONFIRMED`.
- [x] **PR01-D2: Finalize PostgreSQL Foundation & Strategy Correction**: `CONFIRMED`.
- [x] **PR01-E: Foundation Final Review & Commit Readiness**: `CONFIRMED`.
- [x] **PR01-F: Final Documentation Correction (VPS / Railway / category_id Parity)**: `CONFIRMED`.
- [x] **PR02-A: Category Module API Contract Parity (Controller, DTO, Service)**: `CONFIRMED`.
- [x] **PR02-B: Local React Frontend Integration**: `CONFIRMED`.
- [x] **PR02-C: Final Category Acceptance & Commit Readiness**: `CONFIRMED`.
- [x] **PR03-A: Firebase Auth & User Identity Architecture Audit**: `CONFIRMED`.
- [x] **PR03-B: Spring Security & Token Verification Foundation**: `CONFIRMED`.
- [x] **PR03-B.1: Security Hardening & Authorization Boundary Review**: `CONFIRMED`.
- [x] **PR03-C: User Identity Resolution & Safe JIT Provisioning**: `CONFIRMED`.
- [x] **PR03-D1: Current User & Access Status API Parity**: `CONFIRMED`.
- [x] **PR03-E: React Frontend Auth Integration & Local Dev Proxy**: `CONFIRMED`.
- [x] **PR03-D2: Remaining User Profile & Bank APIs Parity**: `CONFIRMED`.
- [x] **PR04-A: Product & Favorite JPA Entities & Database Parity**: `CONFIRMED`.
- [x] **PR04-B: Product & Favorite DTOs & Business Services**: `CONFIRMED`.
- [x] **PR04-C: Product & Favorite Controllers & Security Boundary**: `CONFIRMED`.
- [x] **PR04-D: Full Integration Testing & Zero Regressions (153/153 PASS)**: `CONFIRMED`.

---

## 6. Recently Completed (Các công việc vừa hoàn thành)
- Wave 2: gộp 6 track (E → A → C → D1 → D2 → B) + tích hợp (mục 2) + sinh bảng parity tự động.
- Wave 1: 6 track song song hoàn tất (báo cáo ở `docs/pr-reports/TRACK_A..E.md`, D1, D2).
- Wave 0: 26 entity + hạ tầng dùng chung; Phase 1–4: Category, User/Auth, Product, Favorite.
- Bộ kiểm thử toàn dự án: **321/321 PASS**.

---

## 7. Active Decisions (Quyết định kiến trúc đang hiệu lực)
1. **Ranh giới CSDL bất biến**:
   - `marketplace_java_dev` và `student_marketplace` là READ ONLY.
   - Thao tác ghi kiểm thử CHỈ được phép trên `marketplace_java_test`.
2. **Chiến lược Baseline Flyway chuẩn**:
   - Flyway B-version baseline migration scripts (`B...`) được hỗ trợ trên tất cả phiên bản Flyway (kể cả Community Edition). Tính năng tự động sinh script baseline mới là tính năng thương mại.
   - Khi thực hiện cutover, có thể dùng lệnh CLI `flyway baseline` hoặc script `B...` tùy thuộc vào thỏa thuận bàn giao với DevOps.
   - Không tạo script seed riêng biệt có version lớn hơn version baseline.
3. **Hibernate ddl-auto: validate là bất biến**: Toàn bộ cấu hình giữ nguyên `validate`.
4. **Flyway tạm tắt**: `spring.flyway.enabled: false` cho đến khi thực hiện quy trình bàn giao CSDL chính thức.
5. **Bảo toàn `Category.java` AS-IS**: Giữ nguyên `GenerationType.IDENTITY` do đã kiểm chứng hoạt động tốt với sequence PostgreSQL.
6. **Review Skill độc lập**: Quản trị quy trình review 8 bước nội bộ trong repository Java (`.agents/skills/marketplace-java-review/SKILL.md`).
7. **Môi trường Production**: Production backend hiện tại chạy trên VPS do giảng viên cung cấp (Railway là lịch sử). Cơ chế redeploy là UNVERIFIED.
8. **Hợp đồng API Category**: Tên trường JSON cho Category ID bắt buộc phải là `"category_id"`, không dùng `"categoryId"`.
9. **Cấu trúc phản hồi lỗi**: Trả về trực tiếp `{ "detail": "..." }` cho các lỗi nghiệp vụ, bảo đảm tương thích cả với FastAPI lẫn interceptor của frontend React.
10. **Cơ chế Dev Reverse Proxy cục bộ cho Frontend**: Sử dụng biến cờ opt-in `JAVA_CATEGORY_PROXY=1` trong `student-marketplace-fe/vite.config.ts` để định tuyến có chọn lọc các request Category sang Java cổng 8080 mà không ảnh hưởng tới các API khác trên Python cổng 8000. Tắt cờ sẽ hoàn nguyên 100% cấu hình định tuyến gốc.
11. **Kiến trúc Xác thực Token Firebase trên Java**: Lựa chọn Spring Security OAuth2 Resource Server (`spring-boot-starter-oauth2-resource-server` với NimbusJwtDecoder) kết nối Google JWKs endpoint công khai, bảo đảm kiểm thử tự động độc lập 100% không cần service account private key hay kết nối Firebase Admin server.

---

## 8. Verification Baseline (Đường cơ sở kiểm thử)
- **Biên dịch**: `./mvnw.cmd clean compile` -> `BUILD SUCCESS`.
- **Kiểm thử**: `./mvnw.cmd clean test` -> `BUILD SUCCESS` (**321 tests PASS**, 0 failures, 0 errors, 0 skipped; Wave 2). Context load với `ddl-auto: validate` kiểm mapping toàn bộ entity (trên `_test` lẫn `_dev`).
- **Cách build trên máy hiện tại** (chỉ có JDK 17 mặc định và JDK 26; **không còn cần** biến môi trường pool Hikari): xem `docs/PARALLEL_PLAN.md` mục 7 (`JDK_JAVAC_OPTIONS=-proc:full`, `-Dmaven.compiler.fork=true`, `-Dlombok.version=1.18.46`). Có JDK 21 thì không cần.
- **Tình trạng CSDL sau kiểm thử** (môi trường Docker `sm_postgres` PostgreSQL 15, `sm_redis` Redis 7 được **dựng lại** ngày 2026-11-25 vì container/volume cũ đã mất):
  - `marketplace_java_test`: 0 dòng ở mọi bảng (Alembic `c9d0e1f2a3b4`); 6 bản sao `_a`, `_b`, `_c`, `_d1`, `_d2`, `_e` tạo bằng `TEMPLATE`.
  - `marketplace_java_dev`: schema Alembic `b8c9d0e1f2a3` (thiếu `user_device_tokens`) + **10 category seed + 3 user tổng hợp** — KHÔNG còn 286 users / 26 categories của bản chụp cũ (không khôi phục được; theo quyết định của người dùng chỉ dựng schema + seed).
  - `student_marketplace`: DB rỗng chưa migrate (nguồn dữ liệu Python tham chiếu cũ đã mất).

- **Sau Wave 2**: `marketplace_java_test` chỉ còn 1 dòng `alembic_version`, mọi bảng nghiệp vụ 0 dòng; `marketplace_java_dev` giữ 10 category + 3 user; 6 DB `marketplace_java_test_*` còn nguyên (người dùng tự xoá nếu muốn).

---

## 9. Known Risks & Technical Debt (Rủi ro & nợ kỹ thuật)
1. **Lệch revision Alembic & Cơ chế Deploy VPS Production (P1)**:
   - `marketplace_java_dev` ở `b8c9d0e1f2a3` (thiếu `user_device_tokens`) → `UserDeviceToken` chưa gắn `@Entity` (xem `PARALLEL_PLAN.md` mục 6).
   - CSDL và Backend Production hiện tại đang chạy trên VPS của giảng viên (trước đây là Railway), trạng thái revision Alembic thực tế và cơ chế tự động deploy đều là **UNVERIFIED**. Cần làm việc với quản trị viên/DevOps để xác minh revision và quy trình deploy thủ công/tự động trước khi cutover.
2. **Quan hệ 1:1 giữa User và UserAuthIdentity (P2)** — *Đã xử lý ở PR03-C* (JIT provisioning an toàn đa luồng).
3. **Giới hạn kiểm tra thu hồi Token (Revocation Checking) (P2)**:
   - Xác thực JWT cục bộ bằng JWKS không gọi Google API kiểm tra `check_revoked` trên mỗi request (phù hợp với hiệu năng và cách làm của Python production backend). Chưa có phê duyệt kiến trúc cho Redis blacklist.
4. **Phân quyền vai trò và trạng thái tài khoản (P1)** — *Đã xử lý ở PR03-C/PR04*:
   - Logic phân quyền nghiệp vụ (403 Forbidden, BOLA) đã hiện thực trong `UserService`, `ProductService`, `FavoriteService`; các module mới (6 track) phải tuân thủ cùng quy ước.
5. **Cấu trúc phản hồi lỗi 422 của Category (P2)**:
   - Đang trả về `{"detail": "Invalid value for request parameter."}` hoặc `{"detail": "Dữ liệu không hợp lệ", "errors": {...}}`. Schema này khác biệt cấu trúc chi tiết với mảng Pydantic của FastAPI nhưng hoàn toàn tương thích với Axios interceptor của React frontend.
6. **Rate limiting HTTP (API_CONTRACT §1.1) chưa có (P1)**: Python giới hạn Auth/Catalog/Admin/Chat video quota qua Redis; Java chưa có Servlet Filter chung (`ClientIpResolver` đã sẵn). Ngoài phạm vi Wave 2.
7. **Chưa kiểm tỉ lệ video dọc 9:16 của Tutorial phía server (P2)**: `MediaStorage.VideoResource` chưa có `width/height`.
8. **Dev DB thiếu `user_device_tokens` (P2)**: nâng `marketplace_java_dev` lên Alembic `c9d0e1f2a3b4`; hiện token FCM xử lý bằng `JdbcTemplate` (`DeviceTokenStore`) và bắt `42P01`.
9. **Tiêu đề notification đấu giá lệch hợp đồng (P3)**: Java theo code Python (bản đang chạy production): "Có lượt đấu giá mới" (`AUCTION_BID`), "Bạn đã bị vượt giá" (`AUCTION_OUTBID`), "Bạn đã thắng đấu giá" (`AUCTION_WON`), "Đấu giá đã kết thúc" (`AUCTION_ENDED`). `API_CONTRACT.md` mục 12.6 ghi "Lượt đặt giá mới", "Bạn đã bị trả giá cao hơn", "Chúc mừng! Bạn đã thắng đấu giá". Cần chọn nguồn chuẩn nếu muốn đồng bộ tài liệu.
10. **JDK 21 chưa cài (P2)**: máy dev build bằng JDK 26 + `-proc:full` + `-Dlombok.version=1.18.46` (xem `PARALLEL_PLAN.md` §7). Cần xác nhận lại trên JDK 21 trước khi bàn giao.
11. **Chưa chạy với Redis/Cloudinary/Firebase thật (P2)**: ticket download mới được test với Redis local; stream tệp thật từ Cloudinary, FCM thật và worker (cờ mặc định TẮT) chưa được kiểm trên môi trường thật. Rate limit chat video (Redis) cũng chưa có.
12. **`ReportEvidenceStreamer` dùng chung cho chat download**: `MediaStorage` chưa có phương thức stream; cân nhắc gom về một `SafeMediaStreamer` trung lập sau này.

---

## 10. Next Steps (Các bước tiếp theo)
1. Người dùng duyệt Wave 2 (review 8 bước ở báo cáo cuối) rồi mới push (chưa push gì lên `origin`).
2. Ưu tiên cao: rate limiting HTTP (§1.1); chạy so sánh response Java ↔ Python cho các luồng chính (order, auction, chat) trên cùng dữ liệu; kiểm WebSocket/Redis/Cloudinary/FCM trên môi trường thử thật.
3. Ưu tiên trung bình: nâng dev DB (`user_device_tokens`), cài JDK 21 và chạy lại suite; kiểm tỉ lệ video 9:16; quyết định tiêu đề notification đấu giá; khôi phục dữ liệu dev (286 users / 26 categories) nếu cần.
4. Dọn dẹp: xoá DB `marketplace_java_test_a/_b/_c/_d1/_d2/_e` và các nhánh `track/*` khi không còn cần (worktree đã gỡ, nhánh còn nguyên).
