# TRẠNG THÁI DỰ ÁN JAVA (PROJECT STATE) — Chợ PTITers Backend Migration
# Repository: marketplace-ptit-be

> **Vai trò tài liệu**: Bộ nhớ ngắn hạn (Short-Term Memory) cho kỹ sư phát triển và AI coding agent, ghi nhận trạng thái hiện tại, các quyết định kiến trúc đang hiệu lực, rủi ro kỹ thuật và kế hoạch hành động tiếp theo cho dự án di trú backend Java Spring Boot.

---

## 1. Current Phase (Giai đoạn hiện tại)
**Phase 4: Product & Favorite Module Migration (Di trú Module Sản phẩm & Yêu thích)** — *Hoàn thành 100%*.
- **Cột mốc hoàn thành**:
  - Task PR03-A (Firebase Auth & User Identity Architecture Audit)
  - Task PR03-B (Spring Security Foundation & Firebase ID Token Verification)
  - Task PR03-B.1 (Security Hardening & Authorization Boundary Review)
  - Task PR03-C (User Identity Resolution & Safe JIT Provisioning)
  - Task PR03-D1 (Current User & Access Status API Parity: `/users/me`, `/users/me/access-status`)
  - Task PR03-E (Secure Local React Auth Integration & Isolated E2E Setup)
  - Task ARCH-SKELETON (Toàn bộ Skeleton Architecture & Package Generation cho 15 domain modules hoàn thành 100%, 119/119 tests PASS)
  - Task PR03-D2 (Remaining User Profile & Bank APIs Parity: `/users/me`, `/users/banks`, `/users/{public_id}`, `/users/{public_id}/sold-products`, `/users/me/bank-account`)
  - Task PR04 (Product & Favorite Full Business Logic, PostgreSQL Integration, 24h View Deduplication Counter, Keyset Cursor Pagination & 34 New Integration Tests - 153/153 tests PASS)
- **Cột mốc tiếp theo**: **Phase 5: Order & Auction Migration** (Hoặc Review Checkpoint bàn giao Phase 4).

---

## 2. Current Task (Công việc hiện tại)
**Task PR04 — Product & Favorite Business Logic & PostgreSQL Integration**:
- [x] Ánh xạ JPA Entity & Bảng vật lý PostgreSQL 15+:
  - `Product.java`: `@Entity @Table(name = "products")` khớp 100% PostgreSQL schema (`DB_v7.md`), Native Enum (`ListingType`, `ProductStatus`, `ProductModerationStatus`, `ProductApprovalStatus`), quan hệ `@ManyToOne` Lazy với `User` (seller) và `Category`, `@OneToMany` với `ProductImage` kèm cấu hình `@BatchSize(size = 20)` chống triệt để N+1 queries.
  - `ProductImage.java`: `@Entity @Table(name = "product_images")` (`imageId`, `product`, `imageUrl`, `sortOrder`, `isThumbnail`).
  - `Favorite.java` & `FavoriteId.java`: `@Entity @Table(name = "favorites") @IdClass(FavoriteId.class)` composite key (`userId`, `productId`).
- [x] Phân trang Keyset Cursor & Hiệu năng Truy vấn PostgreSQL:
  - Custom Repositories (`ProductRepositoryCustomImpl`, `FavoriteRepositoryCustomImpl`): Keyset cursor phân trang `(created_at, product_id)` mã hóa Base64 an toàn.
  - Xử lý tương thích kiểu dữ liệu PostgreSQL: Loại bỏ phép `DISTINCT` trên Entity chứa cột `JSON` (`social_links`) để tránh lỗi `could not identify an equality operator for type json`, dùng parameter binding cho native enum `UserStatus`.
- [x] Bộ đếm Lượt xem Chống gian lận (24h View Deduplication Counter):
  - `ProductViewCounter.java`: Cơ chế TTL 24 giờ cho viewer token (`u:<userId>` hoặc `a:<anonymousId>`), cập nhật atomic `UPDATE products SET view_count = GREATEST(view_count, :cachedFloor)`.
- [x] Logic Nghiệp vụ & Ranh giới Bảo mật (ProductService, FavoriteService):
  - Chặn đăng tin khi tài khoản bị chế tài `SELL` (`UserCapabilityRestriction`).
  - Kiểm soát quyền BOLA: Chỉ chủ sở hữu hoặc ADMIN mới được sửa tin, xóa ảnh, đổi thumbnail, reorder ảnh hoặc xem tin ở trạng thái DRAFT.
  - Idempotent favorite: Thêm/xóa yêu thích không báo lỗi khi trùng lặp.
- [x] Kiểm thử Tích hợp Toàn diện (153/153 tests PASS):
  - `ProductControllerIntegrationTest` (15 tests): CRUD, BOLA, Sell restriction 403, Cursor pagination, View counter 24h.
  - `AdminProductApprovalIntegrationTest` (5 tests): Duyệt tin, từ chối kèm lý do, bảo vệ quyền ADMIN 403, Conflict 409 khi tin không ở PENDING.
  - `FavoriteControllerIntegrationTest` (7 tests): Yêu thích sản phẩm, idempotent, ẩn sản phẩm đã xóa/chưa duyệt.
  - Bảo toàn 119 tests cũ của Category, User, Security và ApplicationContext.
- [x] Bảo toàn CSDL & Ranh giới:
  - CSDL `marketplace_java_dev`: 286 users, 26 categories (nguyên vẹn 100%).
  - CSDL `marketplace_java_test`: 0 users, 0 categories, 0 products, 0 favorites (cô lập, dọn dẹp sạch sẽ 100%).
  - `student-marketplace-be/`: Hoàn toàn giữ nguyên (READ-ONLY).

---

## 3. Status (Trạng thái)
- **Tài liệu Kiến trúc Phase 4**: Hoàn thành và đồng bộ.
- **Mã nguồn Module Product & Favorite**: Đạt chuẩn tương thích API Contract 100% với Python FastAPI và React Frontend.
- **Biên dịch (`.\mvnw.cmd clean compile`)**: `BUILD SUCCESS`.
- **Kiểm thử (`.\mvnw.cmd test`)**: `BUILD SUCCESS` (**153/153 tests PASS**, 0 failures, 0 errors, 0 skipped).
  - 1 ApplicationContext test (`MarketplaceApplicationTests`)
  - 3 User read-only dev DB tests (`UserReadOnlyRepositoryTest`)
  - 4 User write test DB tests (`UserWriteIntegrationTest`)
  - 4 Category write test DB tests (`CategoryWriteIntegrationTest`)
  - 9 Category HTTP API tests (`CategoryControllerIntegrationTest`)
  - 19 Firebase JWT Validator unit tests (`FirebaseJwtValidatorTest`)
  - 23 Security Integration tests (`SecurityIntegrationTest`)
  - 18 User Identity Resolution Integration tests (`UserIdentityResolutionIntegrationTest`)
  - 38 User Controller HTTP Integration tests (`UserControllerIntegrationTest`)
  - 15 Product Controller HTTP Integration tests (`ProductControllerIntegrationTest`)
  - 5 Admin Product Approval HTTP Integration tests (`AdminProductApprovalIntegrationTest`)
  - 7 Favorite Controller HTTP Integration tests (`FavoriteControllerIntegrationTest`)
- **Flyway**: Đang TẮT an toàn (`spring.flyway.enabled: false`). Hibernate ORM: `ddl-auto: validate` hoạt động 100% chuẩn xác với PostgreSQL thật.


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
- Hoàn thành toàn diện Phase 4 (Product & Favorite Module Migration) với kết nối PostgreSQL thật và bảo đảm `spring.jpa.hibernate.ddl-auto: validate`.
- Xây dựng Entity JPA `Product`, `ProductImage`, `Favorite` tương thích 100% với PostgreSQL 15+ native enum và constraints.
- Cài đặt Keyset Cursor Pagination Base64 mã hóa an toàn, loại bỏ triệt để Cartesian product và lỗi `equality operator for type json`.
- Cài đặt 24h View Deduplication Counter (`ProductViewCounter`) với TTL và atomic update.
- Bổ sung 34 tests tích hợp mới bao phủ toàn diện: `ProductControllerIntegrationTest` (15 tests), `AdminProductApprovalIntegrationTest` (5 tests), `FavoriteControllerIntegrationTest` (7 tests) cùng các unit tests.
- Bộ kiểm thử tự động toàn dự án đạt **153/153 tests PASS (0 failures, 0 errors, 0 skipped)**.

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
- **Kiểm thử**: `./mvnw.cmd test` -> `BUILD SUCCESS` (**119 tests PASS**, 0 failures, 0 errors, 0 skipped).
  - 1 Context Load test (`MarketplaceApplicationTests`)
  - 3 Read-only tests trên `marketplace_java_dev` (`UserReadOnlyRepositoryTest`)
  - 4 Write tests trên `marketplace_java_test` (`UserWriteIntegrationTest`)
  - 4 Write/Read tests trên `marketplace_java_test` (`CategoryWriteIntegrationTest`)
  - 9 HTTP integration tests trên `marketplace_java_test` (`CategoryControllerIntegrationTest`)
  - 19 Unit tests kiểm thực JWT Firebase offline (`FirebaseJwtValidatorTest`)
  - 23 Security HTTP integration tests trên `marketplace_java_test` (`SecurityIntegrationTest`)
  - 18 User Identity Resolution Integration tests (`UserIdentityResolutionIntegrationTest`)
  - 38 User Controller HTTP Integration tests (`UserControllerIntegrationTest`)
- **Tình trạng CSDL sau kiểm thử**:
  - `marketplace_java_test`: 0 users, 0 categories (rollback sạch 100%).
  - `marketplace_java_dev`: 286 users, 26 categories (alembic: `b8c9d0e1f2a3`, nguyên vẹn).
  - `student_marketplace`: 286 users, 26 categories (alembic: `b8c9d0e1f2a3`, nguyên vẹn).

---

## 9. Known Risks & Technical Debt (Rủi ro & nợ kỹ thuật)
1. **Lệch revision Alembic & Cơ chế Deploy VPS Production (P1)**:
   - `marketplace_java_dev` ở `b8c9d0e1f2a3` (thiếu `user_device_tokens`).
   - CSDL và Backend Production hiện tại đang chạy trên VPS của giảng viên (trước đây là Railway), trạng thái revision Alembic thực tế và cơ chế tự động deploy đều là **UNVERIFIED**. Cần làm việc với quản trị viên/DevOps để xác minh revision và quy trình deploy thủ công/tự động trước khi cutover.
2. **Quan hệ 1:1 giữa User và UserAuthIdentity (P2)**:
   - `UserAuthIdentity` đang dùng `@ManyToOne`. Sẽ chuẩn hóa trong module User & Auth khi triển khai JIT provisioning ở PR03-C.
3. **Giới hạn kiểm tra thu hồi Token (Revocation Checking) (P2)**:
   - Xác thực JWT cục bộ bằng JWKS không gọi Google API kiểm tra `check_revoked` trên mỗi request (phù hợp với hiệu năng và cách làm của Python production backend). Chưa có phê duyệt kiến trúc cho Redis blacklist.
4. **Phân quyền vai trò và trạng thái tài khoản (P1)**:
   - Token JWT chỉ chứng minh tính hợp lệ mật mã học của Firebase UID, không đại diện cho vai trò `ADMIN` hay trạng thái `ACTIVE` trong PostgreSQL. Logic phân quyền nghiệp vụ (403 Forbidden) sẽ được hiện thực toàn diện tại PR03-C.
5. **Cấu trúc phản hồi lỗi 422 của Category (P2)**:
   - Đang trả về `{"detail": "Invalid value for request parameter."}` hoặc `{"detail": "Dữ liệu không hợp lệ", "errors": {...}}`. Schema này khác biệt cấu trúc chi tiết với mảng Pydantic của FastAPI nhưng hoàn toàn tương thích với Axios interceptor của React frontend.

---

## 10. Next Steps (Các bước tiếp theo)
1. Báo cáo tổng kết Task PR03-B và dừng lại chờ người dùng phê duyệt ("Proceed").
2. Chuyển sang **Task PR03-C — User Identity Resolution & JIT Auto-Provisioning**:
   - Xây dựng logic phân giải danh tính từ Firebase UID / email trong Bearer token.
   - Hiện thực JIT user provisioning an toàn đa luồng (xử lý race condition trên unique constraints `ux_users_email` và `ux_user_auth_identities_provider_uid`).
   - Rà soát trạng thái tài khoản `BANNED` / `INACTIVE` trả về HTTP 403 Forbidden.
