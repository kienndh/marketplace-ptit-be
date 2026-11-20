# NHẬT KÝ LỊCH SỬ DỰ ÁN JAVA (PROJECT HISTORY) — Chợ PTITers Backend Migration
# Repository: marketplace-ptit-be

> **Vai trò tài liệu**: Bộ nhớ dài hạn (Long-Term Memory), chỉ ghi thêm (Append-Only). Ghi lại toàn bộ các quyết định kiến trúc, các bản nâng cấp, các PR và cột mốc hoàn thành trong quá trình di trú backend Chợ PTITers sang Java Spring Boot.

---

## [2026-11-19] TASK 00: PROJECT BOOTSTRAP, ARCHITECTURAL AUDIT & GOVERNANCE BASELINE

### 1. Bối cảnh & Mục tiêu
- **Dự án**: Chuyển đổi và xây dựng lại hệ thống backend Chợ PTITers (C2C Marketplace cho sinh viên) từ nền tảng Python/FastAPI sang Java 21 / Spring Boot.
- **Mục tiêu Task 00**:
  - Thiết lập ranh giới quản trị đa repository (Multi-repo workspace boundary).
  - Rà soát toàn bộ hiện trạng mã nguồn Java ban đầu so với hệ thống Python chuẩn và tài liệu thiết kế CSDL `DB_v7.md`.
  - Xây dựng quy chuẩn làm việc có kiểm soát (Controlled AI Development Workflow), quy chuẩn review mã nguồn 8 bước (Review Skill).
  - Thiết lập bộ nhớ dự án: `PROJECT_STATE.md` (ngắn hạn) và `PROJECT_HISTORY.md` (dài hạn).
  - Lập kế hoạch chi tiết cho PR-01 (Di trú CSDL từ MySQL sang PostgreSQL 15+).

### 2. Các phát hiện cốt lõi từ kiểm toán (Architectural Audit Findings)
1. **Ranh giới Repository**:
   - `student-marketplace-be/` và `student-marketplace-fe/` là tài nguyên tham chiếu chuẩn (Read-Only), tuyệt đối không sửa đổi.
   - `marketplace-ptit-be/` là repository phát triển duy nhất, hiện đang ở nhánh `main`.
2. **Lệch cấu hình CSDL trong Java ban đầu**:
   - Mã nguồn Java ban đầu sử dụng cấu hình MySQL (`compose.yaml` chạy `mysql:8.4` cổng 3307; `pom.xml` chứa `mysql-connector-j` và `flyway-mysql`).
   - Các file di trú Flyway ban đầu (`V1__init.sql`, `V2__seed_categories.sql`) sử dụng cú pháp MySQL không tương thích với PostgreSQL (`AUTO_INCREMENT`, `DATETIME`, `ON UPDATE CURRENT_TIMESTAMP`).
   - CSDL chuẩn của hệ thống là PostgreSQL 15+ đang chạy trong container `sm_postgres` cổng 5432 với database `marketplace_java_dev`.
3. **Hiện trạng Database `marketplace_java_dev`**:
   - Đang chứa 34 bảng vật lý (gồm `alembic_version` ở bản `b8c9d0e1f2a3` và 33 bảng thực thể).
   - Đã có các kiểu ENUM tùy biến: `payment_status_t`, `user_status`.
   - Trạng thái kiểm chứng: **PARTIALLY VERIFIED** do cần giải quyết xung đột Flyway baseline và tính tương thích của sequence / identity.
4. **Lệch cấu trúc Entity User & Category**:
   - `User.java` hiện thiếu cột `public_id` (UUID v4 ngẫu nhiên, UNIQUE, NOT NULL - yêu cầu bắt buộc của `DB_v7.md`), thiếu `show_phone_publicly`, `social_links`, và ánh xạ `status` dạng `VARCHAR` thay vì PostgreSQL enum `user_status`.
   - `Category.java` sử dụng `GenerationType.IDENTITY`, trong khi bảng `categories` trong PostgreSQL dùng sequence `categories_category_id_seq`.
   - Với cài đặt `spring.jpa.hibernate.ddl-auto: validate`, ứng dụng sẽ lỗi ngay lúc khởi động nếu không đồng bộ các thực thể này.

### 3. Các tài liệu & công cụ đã thiết lập
1. `student-marketplace/AGENTS.md`: Quản trị toàn workspace, ranh giới an toàn, cấm can thiệp production và nguồn tham chiếu.
2. `marketplace-ptit-be/AGENTS.md`: Quy chuẩn kỹ thuật Java Spring Boot, phân tầng, cấu hình Hibernate validate, Flyway và kiểm thử.
3. `marketplace-ptit-be/docs/PROJECT_STATE.md`: Bộ nhớ ngắn hạn 13 phần phản ánh chính xác trạng thái hiện tại.
4. `marketplace-ptit-be/docs/PROJECT_HISTORY.md`: Bộ nhớ dài hạn lưu giữ tiến trình.
5. `student-marketplace/.agents/skills/marketplace-java-review/SKILL.md`: Kỹ năng kiểm duyệt mã nguồn 8 bước nghiêm ngặt.

### 4. Kết quả & Bước chuyển tiếp
- Hoàn tất Task 00 với trạng thái sạch trên working tree của `marketplace-ptit-be`.
- Lập Đề xuất di trú CSDL PostgreSQL 15+ (PR-01 Proposal) chi tiết 10 điểm để trình người dùng phê duyệt trước khi lập trình.


---

## [2026-11-19] TASK PR01-A: POSTGRESQL FOUNDATION MIGRATION

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Chuyển đổi cấu hình CSDL của backend Java từ MySQL 8.4 sang PostgreSQL 15, kết nối an toàn với database `marketplace_java_dev` trên Docker `sm_postgres`.
- **Phạm vi kiểm soát**: Không thêm tính năng nghiệp vụ, không sửa đổi Entity, không can thiệp schema CSDL PostgreSQL, không kích hoạt các migration MySQL cũ.

### 2. Thay đổi thực tế đã thực hiện
1. **Dependencies (`pom.xml`)**:
   - Gỡ bỏ `com.mysql:mysql-connector-j` và `org.flywaydb:flyway-mysql`.
   - Bổ sung `org.postgresql:postgresql` (PostgreSQL JDBC driver) và `org.flywaydb:flyway-database-postgresql` sử dụng Spring Boot dependency management.
   - Giữ nguyên phiên bản Java 21 LTS và Spring Boot 4.1.1 baseline.
2. **Cấu hình (`application.yml`)**:
   - Thay thế JDBC URL MySQL bằng cấu hình PostgreSQL thông qua các biến môi trường: `${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}`.
   - Giữ nguyên `spring.jpa.hibernate.ddl-auto: validate`.
   - Tạm thời tắt Flyway: `spring.flyway.enabled: false` để bảo vệ an toàn CSDL, không bật `baseline-on-migrate`.
3. **Đính chính kiểm toán Task 00**:
   - Không áp dụng `baseline-on-migrate: true` với `baseline-version: 0` khi script MySQL V1/V2 còn tồn tại.
   - Không hardcode mật khẩu CSDL mặc định.
   - Ghi nhận `marketplace_java_dev` ở trạng thái `PARTIALLY VERIFIED`.
   - Xác định việc Hibernate validate thành công chỉ kiểm tra 3 Entity hiện có, không đại diện cho toàn bộ 34 bảng.

### 3. Kết quả kiểm chứng (Verification)
- **Compile**: `./mvnw.cmd clean compile` hoàn thành với `BUILD SUCCESS` (16 source files biên dịch sạch).
- **Test**: `./mvnw.cmd test` hoàn thành với `BUILD SUCCESS` (1 test run, 0 failures, 0 errors).
  - Ứng dụng khởi động thành công HikariCP, kết nối tới `jdbc:postgresql://localhost:5432/marketplace_java_dev` (PostgreSQL 15.19).
  - Hibernate 7.4.5.Final khởi tạo `EntityManagerFactory` thành công với dialect `PostgreSQLDialect`.
  - Không có bất kỳ thao tác ghi (write) hoặc thay đổi DDL nào lên CSDL.

### 4. Kết quả kiểm toán Entity Mismatches
- `User.java`: Phát hiện thiếu trường `public_id` (UUID v4 ngẫu nhiên, UNIQUE NOT NULL theo DB_v7.md), thiếu `show_phone_publicly`, `social_links`; cột `status` ánh xạ `VARCHAR` lệch với kiểu native enum `user_status` của PostgreSQL (Blocker cho runtime mutation).
- `Category.java`: Khóa chính `categoryId` dùng `GenerationType.IDENTITY`, trong khi CSDL dùng sequence `categories_category_id_seq` (cần verify runtime write).
- `UserAuthIdentity.java`: Hoàn toàn khớp với schema bảng `user_auth_identities`.


---

## [2026-11-19] TASK PR01-B: USER ENTITY POSTGRESQL PARITY

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Đồng bộ Entity `User.java` của backend Java với bảng `users` thực tế trong CSDL PostgreSQL 15 (`marketplace_java_dev`).
- **Phạm vi kiểm soát**: Chỉ cập nhật ánh xạ các thuộc tính còn thiếu hoặc sai lệch của `User.java`, tạo DTO phụ trợ `SocialLink.java`, mở rộng `UserRepository.java`, và viết bài kiểm thử đọc an toàn (Read-Only). Không chỉnh sửa `Category.java`, không can thiệp schema CSDL, không ghi dữ liệu lên `marketplace_java_dev`.

### 2. Thay đổi thực tế đã thực hiện
1. **Thực thể `User.java`**:
   - Bổ sung `publicId` kiểu `java.util.UUID` gắn `@Column(name = "public_id", nullable = false, unique = true)` và `@PrePersist` tự sinh `UUID.randomUUID()` nếu null.
   - Bổ sung `showPhonePublicly` kiểu `boolean` (`@Column(name = "show_phone_publicly", nullable = false)`).
   - Bổ sung `socialLinks` ánh xạ kiểu PostgreSQL native `json` qua `@JdbcTypeCode(SqlTypes.JSON)` và `@Column(name = "social_links", columnDefinition = "json")`.
   - Cập nhật `status`: Chuyển từ `VARCHAR` sang kiểu native PostgreSQL ENUM `user_status` thông qua `@Enumerated(EnumType.STRING)`, `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`, `@Column(name = "status", nullable = false, columnDefinition = "user_status")`.
   - Giữ nguyên `role` kiểu `VARCHAR(10)` với `@JdbcTypeCode(SqlTypes.VARCHAR)` do CSDL sử dụng `character varying(10)` kèm CHECK constraint.
2. **DTO `SocialLink.java`**:
   - Tạo class DTO `SocialLink` (`platform: String`, `url: String`) với Lombok `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor` để Hibernate Jackson deserialize tự động.
3. **Repository `UserRepository.java`**:
   - Bổ sung method `findByPublicId(UUID publicId)`.
   - Bổ sung method `findFirstByStatus(UserStatus status)`.
   - Bổ sung method `@Query("SELECT u FROM User u WHERE u.socialLinks IS NOT NULL") List<User> findUsersWithSocialLinks(Pageable pageable)`.
4. **Kiểm thử Read-Only (`UserReadOnlyRepositoryTest.java`)**:
   - Viết 3 ca kiểm thử đọc an toàn với `@Transactional(readOnly = true)`.
   - Kiểm tra đọc User records, kiểm tra native Enum reading, kiểm tra UUID lookup, kiểm tra JSON deserialization.
   - Tuân thủ quy tắc bảo vệ dữ liệu cá nhân (không print bất kỳ thông tin nhạy cảm nào).

### 3. Kết quả kiểm chứng (Verification)
- **Compile**: `./mvnw.cmd clean compile` -> `BUILD SUCCESS` (17 source files biên dịch sạch).
- **Test**: `./mvnw.cmd test` -> `BUILD SUCCESS` (4 tests run: 1 contextLoad + 3 read-only repository tests, 0 failures, 0 errors, 0 skipped).
- **Trạng thái tương thích CSDL**:
  - Tương thích Đọc (Read): **VERIFIED** trên PostgreSQL 15.19 (`marketplace_java_dev`).
  - Tương thích Ghi (Write): **NOT VERIFIED** (do chưa có database kiểm thử cô lập chuyên biệt; không ghi dữ liệu lên DB dev theo quy định an toàn).


---

## [2026-11-19] TASK PR01-C: ISOLATED DATABASE WRITE VERIFICATION

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Kiểm chứng toàn diện khả năng đọc và ghi (Read/Write) dữ liệu PostgreSQL 15 của hai thực thể `User` và `Category` trên cơ sở dữ liệu cô lập `marketplace_java_test`.
- **Ranh giới an toàn**: Không ghi vào `marketplace_java_dev` và `student_marketplace`. Kiểm tra khẳng định tên CSDL kết nối bằng `@BeforeEach`. Sử dụng transaction rollback tự động để không lưu rác.

### 2. Thay đổi thực tế đã thực hiện
1. **Cấu hình Test Cô lập (`application-test.yml`)**:
   - Tạo file cấu hình `src/test/resources/application-test.yml` nhận `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD`.
   - Giữ `spring.jpa.hibernate.ddl-auto: validate` và `spring.flyway.enabled: false`.
2. **Kiểm thử Ghi Thực thể User (`UserWriteIntegrationTest.java`)**:
   - Xây dựng 4 ca kiểm thử tổng hợp sử dụng người dùng nhân tạo (synthetic users):
     - Kiểm chứng sinh và bảo toàn UUID (`@PrePersist`).
     - Kiểm chứng ghi và cập nhật kiểu native PostgreSQL ENUM `user_status` (`ACTIVE -> BANNED -> INACTIVE`).
     - Kiểm chứng ghi và đọc lại trường JSON `socialLinks` (`List<SocialLink>`) và boolean `showPhonePublicly`.
     - Kiểm chứng ràng buộc duy nhất email `ux_users_email` ném `DataIntegrityViolationException`.
3. **Kiểm thử Thực thể Category AS-IS (`CategoryWriteIntegrationTest.java`)**:
   - Xây dựng 4 ca kiểm thử kiểm chứng `Category.java` không sửa đổi:
     - Kiểm chứng `GenerationType.IDENTITY` lấy chính xác giá trị sinh từ sequence `categories_category_id_seq` qua PostgreSQL JDBC `RETURNING`. Quyết định giữ nguyên `Category.java` AS-IS, không thay thế bằng `@SequenceGenerator`.
     - Kiểm chứng các query loại trừ danh mục xóa mềm.
     - Kiểm chứng hành vi partial unique index `ux_categories_slug (slug) WHERE is_deleted = false`: chặn trùng slug giữa các category active, nhưng cho phép tái sử dụng slug nếu category cũ đã bị xóa mềm.

### 3. Kết quả kiểm chứng (Verification)
- **Compile**: `./mvnw.cmd clean compile` -> `BUILD SUCCESS`.
- **Test**: `./mvnw.cmd test` -> `BUILD SUCCESS` (**12/12 tests PASS**, 0 failures, 0 errors, 0 skipped).
- **Trạng thái CSDL sau test**:
  - `marketplace_java_test`: 0 users, 0 categories (rollback hoàn hảo 100%).
  - `marketplace_java_dev`: 286 users, 26 categories (bảo vệ nguyên vẹn 100%).
  - `student_marketplace`: 286 users, 26 categories (bảo vệ nguyên vẹn 100%).


---

## [2026-11-19] TASK PR01-D1: FLYWAY MIGRATION FOUNDATION & LEGACY MYSQL ISOLATION

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Chuẩn bị nền tảng an toàn cho công cụ di trú CSDL Flyway trong repository Java, cách ly hoàn toàn các script MySQL cũ, đối soát phiên bản schema Alembic và xây dựng tài liệu chiến lược migration PostgreSQL.
- **Ranh giới kiểm soát**: Không chỉnh sửa schema hay dữ liệu CSDL, không bật Flyway, không thực thi bất kỳ câu lệnh DDL nào.

### 2. Thay đổi thực tế đã thực hiện
1. **Cách ly Migration MySQL Cũ**:
   - Dùng lệnh `git mv` di chuyển 2 file:
     - `src/main/resources/db/migration/V1__init.sql` -> `docs/legacy/mysql/V1__init.sql`
     - `src/main/resources/db/migration/V2__seed_categories.sql` -> `docs/legacy/mysql/V2__seed_categories.sql`
   - Bảo toàn 100% nội dung gốc để lưu trữ lịch sử, loại bỏ hoàn toàn các file SQL không tương thích ra khỏi thư mục thực thi mặc định của Flyway.
2. **Kiểm toán Phiên bản Alembic**:
   - Đối soát chuỗi migration trong `student-marketplace-be/alembic/versions/`:
     - Alembic HEAD trong code Python: `c9d0e1f2a3b4` (tạo bảng `user_device_tokens`).
     - Snapshot CSDL dev thực tế (`marketplace_java_dev` và `student_marketplace`): `b8c9d0e1f2a3` (chưa có bảng `user_device_tokens`).
     - CSDL yêu cầu trước khi Cutover: Khớp `c9d0e1f2a3b4`.
3. **Ban hành Tài liệu Chiến lược (`docs/DATABASE_MIGRATION_STRATEGY.md`)**:
   - Quy hoạch 2 kịch bản vận hành độc lập:
     - Kịch bản A (Existing Database): Baseline thủ công tại phiên bản `1.0.0` (tương đương `b8c9d0e1f2a3`), không bật `baseline-on-migrate` tự động, chỉ chạy các migration mới `> 1.0.0`.
     - Kịch bản B (Fresh Empty Database): Thực thi tuần tự `V1.0.0` (33 bảng thực thể chuẩn), `V1.0.1` (10 danh mục chuẩn tắc), `V1.1.0` (bổ sung `user_device_tokens`).
4. **Kiểm chứng An toàn**:
   - Biên dịch sạch (`clean compile`): xác nhận thư mục `target/classes/db/` hoàn toàn trống.
   - Kiểm thử toàn diện (`mvn test`): 12/12 bài kiểm thử tiếp tục PASS 100%.


---

## [2026-11-19] TASK PR01-D2: FINALIZE POSTGRESQL FOUNDATION & STRATEGY CORRECTION

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Chuẩn hóa và chốt phương án chiến lược di trú CSDL Flyway trước khi bước vào lập trình tính năng nghiệp vụ và hợp đồng API.
- **Phạm vi kiểm soát**:
  - Không sinh mã di trú 33 bảng schema vội vàng khi chưa đến giai đoạn cutover để tránh trùng lặp DDL.
  - Không bật Flyway runtime (`spring.flyway.enabled: false`).
  - Không can thiệp schema hay dữ liệu trên `marketplace_java_dev` và `student_marketplace`.
  - Mọi thao tác kiểm thử ghi phải cô lập trên `marketplace_java_test` với transaction rollback tự động.
  - Tối ưu hóa tính tái sử dụng schema PostgreSQL hiện có, hạn chế tối đa rủi ro vận hành production.

### 2. Các điểm đính chính & hoàn thiện cốt lõi
1. **Loại bỏ nguy cơ từ Migration Seed tách rời (`V1.0.1`)**:
   - Nhận diện rủi ro: Trong kịch bản baseline CSDL đã có sẵn dữ liệu (Kịch bản A - Existing Database) ở phiên bản `1.0.0`, nếu tồn tại migration dạng version `V1.0.1` nạp danh mục thì Flyway sẽ tự động chạy script này, dẫn đến xung đột khóa chính hoặc tạo dữ liệu trùng lặp trên database đang có 26 danh mục.
   - Giải pháp: Loại bỏ hoàn toàn migration `V1.0.1` riêng biệt. Dữ liệu hạt giống chuẩn ban đầu (10 danh mục mẫu) được gộp chung vào chính script baseline toàn diện (dành riêng cho Kịch bản B - Fresh Database). Nhờ đó, các CSDL hiện hữu khi baseline sẽ bỏ qua toàn bộ cả schema lẫn seed ban đầu mà không bị ảnh hưởng.
2. **Đánh giá tính năng Flyway B-version baseline migration (`B...`)**:
   - Xác minh tài liệu kỹ thuật: Migration tiền tố `B` (Baseline Migrations) là tính năng thương mại thuộc phiên bản trả phí **Flyway Teams / Enterprise Edition**.
   - Phiên bản đang sử dụng trong dự án là mã nguồn mở **Flyway Community Edition 12.4.0**, do đó không hỗ trợ `B` migrations.
   - Chuẩn hóa giải pháp tương thích Community Edition: Áp dụng lệnh `flyway baseline` tường minh hoặc sử dụng quy ước `V` migration chuẩn.
3. **Đính chính thuật toán Checksum của Flyway**:
   - Xác minh qua mã nguồn Flyway Core (`SqlScriptFlywayCallback` / `DefaultSqlScriptExecutor`): Flyway tính toán checksum của các file migration SQL dựa trên thuật toán **CRC32** (thông qua `java.util.zip.CRC32`) và lưu trữ dưới dạng số nguyên 32-bit có dấu trong cột `checksum` của bảng `flyway_schema_history`.
   - Đính chính tài liệu chiến lược, loại bỏ nhận định sai sót trước đó cho rằng Flyway dùng SHA-256.
4. **Xác định trạng thái CSDL Production (Railway)**:
   - Alembic HEAD trong mã nguồn Python: `c9d0e1f2a3b4` (bổ sung bảng `user_device_tokens`).
   - CSDL snapshot môi trường local dev (`marketplace_java_dev`): `b8c9d0e1f2a3` (chưa có `user_device_tokens`).
   - CSDL Production thực tế trên Railway: Được phân loại chính xác là **UNVERIFIED**. Tuyệt đối không tự ý suy diễn production đang ở HEAD mà phải thực hiện bước kiểm tra trực tiếp an toàn trước giờ cutover.
5. **Gia cố an toàn kiểm thử (Test Safety Hardening)**:
   - Bổ sung phương thức `@BeforeEach void verifyDatabaseSafetyBoundary()` trong `UserReadOnlyRepositoryTest.java`: thực hiện truy vấn `SELECT current_database()` và kiểm tra assert bắt buộc CSDL kết nối phải là `marketplace_java_dev`. Ngăn chặn hoàn toàn rủi ro chạy test đọc nhầm vào các database khác.

### 3. Kết quả kiểm chứng (Verification)
- **Compile**: `./mvnw.cmd clean compile` -> `BUILD SUCCESS` (17 source files biên dịch sạch sẽ).
- **Test**: `./mvnw.cmd test` -> `BUILD SUCCESS` (**12/12 tests PASS**, 0 failures, 0 errors, 0 skipped, thời gian chạy: 54.6s).
  - 1 Context Load test (`MarketplaceApplicationTests`)
  - 3 Read-only tests trên `marketplace_java_dev` (`UserReadOnlyRepositoryTest`)
  - 4 Write tests trên `marketplace_java_test` (`UserWriteIntegrationTest`)
  - 4 Write/Read tests trên `marketplace_java_test` (`CategoryWriteIntegrationTest`)
- **Toàn vẹn CSDL sau kiểm thử**:
  - `marketplace_java_test`: 0 users, 0 categories (rollback hoàn tất 100%).
  - `marketplace_java_dev`: 286 users, 26 categories (nguyên vẹn 100%).
  - `student_marketplace`: 286 users, 26 categories (nguyên vẹn 100%).

### 4. Đánh giá tính sẵn sàng Commit (Commit Readiness)
- Toàn bộ Phase 1 (PR01-A, PR01-B, PR01-C, PR01-D1, PR01-D2) đã hoàn thiện đồng bộ, cấu thành một cột mốc kiến trúc trọn vẹn: "PostgreSQL Database Foundation & Parity".
- Working tree sạch sẽ, không có file rác, không rò rỉ secret, tài liệu được đồng bộ đầy đủ.
- Trạng thái: **READY FOR COMMIT** (chờ xác nhận phê duyệt từ lập trình viên).


---

## [2026-11-19] TASK PR01-E: FOUNDATION FINAL REVIEW & COMMIT READINESS

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Thực hiện rà soát độc lập toàn diện nền tảng CSDL PostgreSQL của Phase 1 (PR01-A đến PR01-D2), đính chính chính xác sự thật kỹ thuật về Flyway B-migrations, đóng gói quy trình review độc lập cho repository, và tiến hành kiểm toán bảo mật - cô lập CSDL trước khi chốt commit.
- **Ranh giới an toàn**:
  - Không thêm tính năng nghiệp vụ mới.
  - Không bật hoặc thực thi Flyway tự động (`spring.flyway.enabled: false`).
  - Không sửa đổi mã nguồn repository tham chiếu (`student-marketplace-be/`, `student-marketplace-fe/`).
  - Không commit, không push, không deploy.

### 2. Các hiệu chỉnh & hoàn thiện cốt lõi
1. **Đính chính Sự thật Kỹ thuật về Flyway B-version Baseline Migrations**:
   - Xác minh tài liệu chính thức của Redgate Flyway: Script baseline có tiền tố `B` (`B<version>__<description>.sql`) được hỗ trợ trên **TẤT CẢ các phiên bản Flyway** (bao gồm cả Flyway Community Edition 12.4.0 đang dùng trong dự án).
   - Phân biệt rõ: Khả năng *tự động sinh script baseline* (Automatic Baseline Generation) mới là tính năng thương mại (Flyway Teams/Enterprise). Việc kỹ sư tự viết script `B...` và thực thi trên bản Community là hoàn toàn hợp lệ.
   - Cập nhật tài liệu chiến lược `docs/DATABASE_MIGRATION_STRATEGY.md` phân tích rành mạch 3 cơ chế: lệnh CLI `flyway baseline`, script `B...`, và migration tuần tự `V...`.
2. **Đóng gói Kỹ năng Review Mã nguồn Độc lập (Portable Review Skill)**:
   - Tạo bản sao cục bộ của kỹ năng kiểm duyệt tại `marketplace-ptit-be/.agents/skills/marketplace-java-review/SKILL.md`.
   - Cập nhật `marketplace-ptit-be/AGENTS.md` (Mục 5) để quy trình kiểm duyệt 8 bước tồn tại bền vững ngay cả khi repository Java được clone độc lập ra khỏi multi-repo workspace.
3. **Kiểm toán Thực thể & Ràng buộc CSDL (Entity & Database Parity Audit)**:
   - Rà soát `User.java`: Khớp kiểu PostgreSQL native enum `user_status`, UUID ngẫu nhiên sinh tự động qua `@PrePersist`, JSON mapping `socialLinks`, boolean `showPhonePublicly`.
   - Rà soát `Category.java`: Giữ nguyên `GenerationType.IDENTITY` AS-IS tương thích với sequence PostgreSQL, partial unique index slug.
   - Rà soát `UserAuthIdentity.java`: Khớp schema `user_auth_identities`, ghi nhận nợ kỹ thuật P2 `@ManyToOne` sẽ chuẩn hóa ở Phase 3.
4. **Kiểm toán Cô lập CSDL (Database Test Isolation Audit)**:
   - `marketplace_java_dev`: Read-only, được bảo vệ bằng `@BeforeEach` trong `UserReadOnlyRepositoryTest`.
   - `marketplace_java_test`: Chỉ ghi cho kiểm thử tích hợp, cưỡng chế kiểm tra database name tại `@BeforeEach`, transaction rollback tự động 100%.
   - `student_marketplace`: Không bao giờ bị cấu hình hoặc can thiệp bởi mã nguồn Java.
   - Không có cấu hình fallback ngầm; cấu hình `ddl-auto: validate` và `flyway.enabled: false` được bảo toàn.
   - Ghi nhận: Sequence increments trong PostgreSQL hoạt động ngoài phạm vi transaction và không bị rollback khi transaction rollback (hành vi tiêu chuẩn của PostgreSQL).

### 3. Kết quả Kiểm chứng Thực tế (Independent Real Verification)
- **Biên dịch**: `.\mvnw.cmd clean compile` -> `BUILD SUCCESS` (17 source files biên dịch trong 11.339s, 0 errors, 0 warnings).
- **Kiểm thử**: `.\mvnw.cmd test` -> `BUILD SUCCESS` (**12/12 tests PASS** trong 20.417s, 0 failures, 0 errors, 0 skipped).
  - 1 test context loads (`MarketplaceApplicationTests`)
  - 3 tests read-only dev DB (`UserReadOnlyRepositoryTest`)
  - 4 tests write test DB (`UserWriteIntegrationTest`)
  - 4 tests write test DB (`CategoryWriteIntegrationTest`)
- **Kiểm tra CSDL sau kiểm thử**:
  - `marketplace_java_test`: 0 users, 0 categories (rollback 100%).
  - `marketplace_java_dev`: 286 users, 26 categories (nguyên vẹn 100%).
  - `student_marketplace`: 286 users, 26 categories (nguyên vẹn 100%).

### 4. Kiểm toán Git & Bảo mật (Git & Security Audit)
- 0 file .env, 0 token/secret, 0 private key Firebase, 0 Cloudinary secret, 0 dump CSDL.
- Các repository tham chiếu (`student-marketplace-be/`, `student-marketplace-fe/`) hoàn toàn không bị sửa đổi bởi task này.
- Danh sách file chuẩn bị commit gồm 16 file (6 file tracked/renamed/modified và 10 file untracked mới) cấu thành cột mốc Phase 1 vững chắc.
- **Trạng thái**: **READY** (Hoàn toàn sẵn sàng để commit).
- **Quy trình tiếp theo**: Dừng lại chờ người dùng phê duyệt ("Proceed" / "Commit"), sau đó chuyển sang **Task PR02-A: Category API Contract Parity**.


---

## [2026-11-20] TASK PR01-F: FINAL DOCUMENTATION CORRECTION & ENVIRONMENT VERIFICATION

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Hoàn thiện và đính chính các thông tin tài liệu kỹ thuật cuối cùng trước khi thực hiện commit đầu tiên cho Phase 1 (PR01 Foundation).
- **Phạm vi an toàn**:
  - Không sửa đổi mã nguồn Java hoặc file test.
  - Không can thiệp repository tham chiếu (`student-marketplace-be/`, `student-marketplace-fe/`).
  - Không kết nối hoặc tác động tới CSDL và hạ tầng production.
  - Không commit, không push lên Git.

### 2. Các đính chính kỹ thuật & quyết định hiệu lực
1. **Đính chính Môi trường Production Thực tế (VPS vs. Railway)**:
   - Backend Python production hiện đang được triển khai trên **VPS do giảng viên cung cấp (Teacher-provided VPS)**, phục vụ domain `choptiters.id.vn`.
   - Các đề cập đến **Railway** trong các tài liệu trước đây đại diện cho nền tảng triển khai lịch sử (historical deployment information) và được bảo lưu chính xác trong nhật ký quá khứ, không xóa bỏ ngữ cảnh lịch sử.
   - Cơ chế deploy VPS thực tế và khả năng tự động redeploy khi push lên GitHub được xác định rõ ràng là **UNVERIFIED** (tuyệt đối không tự suy đoán việc push code lên GitHub sẽ tự động cập nhật VPS).
2. **Quy chuẩn Bắt buộc cho Hợp đồng API Category (Task PR02-A)**:
   - Để tương thích 100% với frontend React và hợp đồng API chuẩn tại `student-marketplace-be/docs/API_CONTRACT.md`, trường định danh JSON trong Category response bắt buộc phải là `"category_id"` (thông qua `@JsonProperty("category_id")`), **tuyệt đối không đổi thành `"categoryId"`**.
3. **Khóa Danh sách File Commit**:
   - Xác nhận 16 file sẵn sàng commit (6 file tracked/renamed/modified và 10 file untracked mới).
   - Kiểm toán bảo mật đạt chuẩn: 0 file .env, 0 token, 0 private key, 0 file backup dump.



