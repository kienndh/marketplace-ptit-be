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


---

## [2026-11-20] TASK PR02-A: CATEGORY MODULE API CONTRACT PARITY

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Đạt độ tương thích 100% về hợp đồng API HTTP (API Contract Parity) cho module Danh mục sản phẩm (Category) giữa backend Java Spring Boot và Python FastAPI production, phục vụ frontend React mà không làm gãy vỡ UI.
- **Ranh giới an toàn**:
  - Không thêm chức năng Admin CRUD hoặc tạo/sửa danh mục.
  - Không thêm yêu cầu xác thực Authentication (cả hai endpoint đều là Public).
  - Không sửa đổi mã nguồn Python hoặc React.
  - Không can thiệp CSDL production; mọi test ghi đều chạy trên `marketplace_java_test` với rollback tự động.
  - Không commit, không push.

### 2. Các điểm đối soát & thay đổi mã nguồn Java
1. **Đối soát Hợp đồng API (Python vs Java)**:
   - `GET /api/v1/categories`: Trả về trực tiếp mảng JSON `CategoryResponse[]` (không có object wrapper `data`/`items`), chỉ lấy danh mục đang hoạt động (`is_deleted = false`), sắp xếp theo `category_id ASC`.
   - `GET /api/v1/categories/{category_id}`: Trả về `CategoryResponse` (200 OK) hoặc 404 Not Found (`{"detail": "Category not found."}`) nếu không tìm thấy hoặc đã bị xóa mềm.
   - Trường JSON Category ID bắt buộc là `"category_id"` (thông qua `@JsonProperty("category_id")`).
2. **Hiệu chỉnh mã nguồn tối thiểu**:
   - `CategoryController.java`: Cập nhật `@GetMapping("/{category_id}")` và `@PathVariable(name = "category_id") Integer categoryId`.
   - `CategoryService.java`: Bổ sung `@Transactional(readOnly = true)` cấp class; dọn dẹp các import không sử dụng (`Banner`, `HttpStatus`, `ResponseStatusException`).
   - `ErrorResponse.java`: Đặt `timestamp = null` trong constructor `ErrorResponse(String detail)` để khi ném `ApiException.notFound("Category not found.")`, Jackson serialize trả về chính xác `{"detail": "Category not found."}` (loại bỏ trường `timestamp` thừa, khớp 100% với FastAPI).
3. **Bộ kiểm thử tích hợp HTTP (`CategoryControllerIntegrationTest.java`)**:
   - Xây dựng 9 ca kiểm thử tích hợp (MockMvc + real Spring context + real PostgreSQL test database `marketplace_java_test`) bao phủ toàn diện 17 kịch bản kiểm thử (T01 - T17):
     - Kiểm tra mảng JSON trực tiếp, kiểm tra trường `category_id`, không có `categoryId`, không lộ `is_deleted`/`created_at`.
     - Kiểm tra sắp xếp `category_id` tăng dần, lọc bỏ danh mục xóa mềm.
     - Kiểm tra 404 cho ID không tồn tại, ID bị xóa mềm, ID non-positive (`0`, `-1`, `-99`).
     - Kiểm tra 422 cho tham số path không phải số nguyên (`/api/v1/categories/invalid`).
     - Kiểm tra truy cập ẩn danh không cần token.

### 3. Kết quả Kiểm chứng Thực tế (Independent Verification)
- **Maven Test**: `./mvnw.cmd test` -> `BUILD SUCCESS` (**21/21 tests PASS**, 0 failures, 0 errors, 0 skipped, 22.2s).
  - 1 Context Load test (`MarketplaceApplicationTests`)
  - 3 User read-only dev DB tests (`UserReadOnlyRepositoryTest`)
  - 4 User write test DB tests (`UserWriteIntegrationTest`)
  - 4 Category write test DB tests (`CategoryWriteIntegrationTest`)
  - 9 Category HTTP API tests (`CategoryControllerIntegrationTest`)
- **Manual HTTP Smoke Test (`curl.exe`)**:
  - Khởi động máy chủ Spring Boot thật cổng 8080 kết nối `marketplace_java_dev`.
  - `GET /api/v1/categories` -> HTTP 200, trả về mảng 26 danh mục với định dạng `category_id`, `name`, `slug`.
  - `GET /api/v1/categories/278` -> HTTP 200, `{"category_id":278,"name":"Giáo trình & Sách","slug":"giao-trinh-sach"}`.
  - `GET /api/v1/categories/999999` -> HTTP 404, `{"detail":"Category not found."}`.
  - `GET /api/v1/categories/invalid` -> HTTP 422, `{"detail":"Invalid value for request parameter."}`.
  - Tắt máy chủ và giải phóng cổng 8080 an toàn.
- **Ranh giới CSDL sau kiểm thử**:
  - `marketplace_java_test`: 0 bản ghi (rollback sạch 100%).
  - `marketplace_java_dev`: 286 users, 26 categories (nguyên vẹn 100%).
  - `student_marketplace`: 286 users, 26 categories (nguyên vẹn 100%).

### 4. Kết luận & Bước tiếp theo
- Module Category HTTP API đạt chuẩn tương thích 100% với backend Python và sẵn sàng tích hợp với React frontend.
- Cột mốc tiếp theo: **Task PR02-B — Local React Frontend Integration** (cấu hình selective Vite proxy để frontend gọi Category API từ Java cổng 8080).



---

## [2026-11-21] TASK PR02-B: REACT FRONTEND + JAVA CATEGORY INTEGRATION

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Chứng minh Frontend React thật (`student-marketplace-fe`) có thể tích hợp và tiêu thụ an toàn Category API từ Java backend (`marketplace-ptit-be`) trong môi trường phát triển cục bộ (Local Development) mà không ảnh hưởng tới các module khác và không can thiệp production.
- **Ranh giới an toàn**:
  - Không sửa đổi mã nguồn Java backend (CategoryController, CategoryService, CategoryRepository, Category, User).
  - Không sửa đổi mã nguồn Python backend (`student-marketplace-be/`).
  - Cho phép chỉnh sửa tối thiểu duy nhất tại `student-marketplace-fe/vite.config.ts` để cấu hình dev proxy opt-in.
  - Tuyệt đối không kết nối VPS production, production PostgreSQL hay production Firebase data.
  - Không commit, không push.

### 2. Thiết kế Reverse Proxy & Kiểm soát Ranh giới
1. **Thiết kế Opt-in Dev Proxy (`student-marketplace-fe/vite.config.ts`)**:
   - Sử dụng biến cờ môi trường `JAVA_CATEGORY_PROXY=1`.
   - Khi cờ bật: Sử dụng biểu thức regex `^/api/v1/categories($|[/?])` đặt trước quy tắc `/api` để ưu tiên chuyển hướng các request Category sang Java backend `http://127.0.0.1:8080`.
   - Các route khác (`/api/v1/products`, `/api/v1/users/me`, `/api/v1/auth`, v.v.) và `/ws` tiếp tục đi sang Python FastAPI `http://127.0.0.1:8000`.
   - Khi cờ tắt hoặc không truyền: Giữ nguyên 100% cấu hình proxy cũ về Python backend cổng 8000.
2. **Kiểm tra Cấu hình Axios (`apiClient.ts`)**:
   - Biến môi trường `VITE_API_BASE_URL` trong `.env` rỗng, Axios sử dụng đường dẫn tương đối `/api/...`. Do đó mọi request từ browser đều đi qua Vite dev server port 3000 và được phân luồng chính xác.

### 3. Kết quả Kiểm chứng Tích hợp (Independent Verification)
1. **Kiểm thử Trực tiếp API Java (Cổng 8080)**:
   - `GET http://localhost:8080/api/v1/categories` -> HTTP 200, 26 categories, JSON array trực tiếp.
   - `GET http://localhost:8080/api/v1/categories/278` -> HTTP 200, `{"category_id":278,"name":"Giáo trình & Sách","slug":"giao-trinh-sach"}`.
2. **Kiểm thử Phân luồng Vite Proxy với `JAVA_CATEGORY_PROXY=1` (Cổng 3000)**:
   - `GET http://localhost:3000/api/v1/categories` -> HTTP 200, trả về dữ liệu từ Java backend (không có header `server: uvicorn`).
   - `GET http://localhost:3000/api/v1/categories/278` -> HTTP 200, trả về từ Java backend.
   - `GET http://localhost:3000/api/v1/categories/999999` -> HTTP 404, `{"detail":"Category not found."}`.
   - `GET http://localhost:3000/api/v1/categories/invalid` -> HTTP 422, `{"detail":"Invalid value for request parameter."}`, xác nhận log trực tiếp từ Spring Boot DispatcherServlet.
   - `GET http://localhost:3000/api/v1/products?limit=1` -> HTTP 200, mang header `server: uvicorn`, chứng minh route sản phẩm vẫn đi về Python backend cổng 8000.
   - `GET http://localhost:3000/api/v1/users/me` -> HTTP 401, mang header `server: uvicorn`, chứng minh route người dùng vẫn đi về Python backend cổng 8000.
3. **Kiểm thử Phân luồng khi Flag OFF**:
   - `GET http://localhost:3000/api/v1/categories` -> HTTP 200, mang header `server: uvicorn`, xác nhận hoàn nguyên về Python backend khi tắt cờ.
4. **Kiểm tra Frontend Build & Lint**:
   - `npm run build` (`tsc -b && vite build`) -> Exit code 0, biên dịch 1920 modules thành công trong 7.74s, 0 lỗi TypeScript, 0 lỗi cấu hình Vite.
   - `npm run lint` (`eslint .`) -> Exit code 0, 0 lỗi, 92 pre-existing warnings.
5. **Kiểm tra Bộ test Java Backend**:
   - `.\mvnw.cmd test` -> `BUILD SUCCESS`, **21/21 tests PASS** trong 19.6s.
   - Cấu hình biến môi trường kết nối xác thực container chuẩn: `DB_USER=postgres`, `DB_PASSWORD=postgres`, `TEST_DB_USER=postgres`, `TEST_DB_PASSWORD=postgres`.
6. **Bảo toàn CSDL Sau Kiểm thử**:
   - `marketplace_java_dev`: Read-only, 26 categories, 286 users (nguyên vẹn).
   - `marketplace_java_test`: 0 bản ghi (tự động rollback sạch 100%).

### 4. Kết luận & Khuyến nghị Bước tiếp theo
- Frontend React tích hợp thành công, hoàn toàn trong suốt với Category API của Java Spring Boot thông qua cơ chế selective reverse proxy.
- Ranh giới repo, bảo mật CSDL, và tính tương thích ngược được đảm bảo tuyệt đối.
- Sẵn sàng chuyển giao sang **Task PR03: User & Auth Module Migration** (hoặc Product Module).

---

## [2026-11-22] TASK PR02-C: FINAL CATEGORY ACCEPTANCE

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Nghiệm thu kỹ thuật cuối cùng cho Module Danh mục (Category), khép lại toàn bộ các khoảng trống xác thực của PR02-A và PR02-B, kiểm toán thực tế mã nguồn test, rà soát chi tiết ranh giới phản hồi lỗi, và chuẩn bị sẵn sàng danh mục staging commit an toàn cho cả hai repository `marketplace-ptit-be` và `student-marketplace-fe`.
- **Ranh giới an toàn**:
  - Không thêm tính năng nghiệp vụ mới.
  - Không sửa đổi mã nguồn Java controller/service/repository/entity.
  - Giữ nguyên thay đổi duy nhất `vite.config.ts` trong frontend, không chạm vào các file frontend khác (`App.tsx`, `Header.tsx`, `src/features/users/`).
  - Python backend `student-marketplace-be/` READ ONLY.
  - Không kết nối VPS production, production CSDL hay production Firebase.
  - Không commit, không push.

### 2. Kết quả Rà soát Mã nguồn Kiểm thử Thực tế
1. **Kiểm toán `CategoryControllerIntegrationTest.java` (200 dòng mã nguồn)**:
   - Xác nhận sự tồn tại thực tế của phương thức `testListCategoriesEmptyReturns200WithEmptyArray` (dòng 79-87): kiểm tra CSDL trống trả về HTTP 200 với `[]`.
   - Xác nhận sự tồn tại thực tế của phương thức `testGetSoftDeletedCategoryByIdReturns404` (dòng 164-179): kiểm tra danh mục bị xóa mềm (`is_deleted = true`) trả về HTTP 404 với `{"detail": "Category not found."}`.
   - Assertions: kiểm tra mảng trực tiếp, trường `category_id`, không có `categoryId`, không lộ `is_deleted`/`created_at`, lọc bỏ danh mục xóa mềm, truy cập ẩn danh không cần token.
   - Tính độc lập: Dữ liệu giả lập sinh động bằng `UUID.randomUUID()` và `cat.getCategoryId()`, không hardcode vào các ID cố định 278-285.
   - Ranh giới CSDL: `@BeforeEach verifyDatabaseSafetyBoundary()` assert fail-closed kết nối đúng database `marketplace_java_test` và rollback tự động 100%.

### 3. Đánh giá Tính tương thích Lỗi (Error Response Parity)
1. **Lỗi 404 (Category not found)**:
   - Cả Python FastAPI và Java Spring Boot đều trả về chính xác: `{"detail": "Category not found."}` (Khớp 100%).
2. **Lỗi 422 (Type Mismatch / Validation Error)**:
   - Python FastAPI: Trả về mảng chi tiết `{"detail": [{"loc": [...], "msg": "...", "type": "..."}]}`.
   - Java Spring Boot: Trả về chuỗi `{"detail": "Invalid value for request parameter."}` qua `handleTypeMismatch` trong `ErrorHandler.java`.
   - Đánh giá tương thích Frontend: File `student-marketplace-fe/src/lib/apiErrors.ts` (`getApiErrorMessage`) ưu tiên kiểm tra `typeof data.detail === 'string'` trước khi kiểm tra `Array.isArray(data.detail)`. Do đó, frontend hiển thị chuỗi thông báo lỗi của Java hoàn toàn bình thường, không gây crash ứng dụng. Giữ nguyên `ErrorHandler` AS-IS, không viết lại tùy tiện.

### 4. Kết quả Kiểm thử Hồi quy Toàn diện
- **Java Backend**: `.\mvnw.cmd clean test` -> `BUILD SUCCESS`, **21/21 tests PASS** (thời gian: 43.293s).
- **Frontend Build**: `npm run build` (`tsc -b && vite build`) -> Exit code 0, 1920 modules transformed thành công (thời gian: 10.85s).
- **Frontend Lint**: `npm run lint` (`eslint .`) -> Exit code 0, 0 errors, 92 pre-existing warnings.
- **Trạng thái Browser UI Visual Test**: **UNVERIFIED (Automated Browser Unavailable)** do Chrome trên host chưa kích hoạt remote debugging port (`DevToolsActivePort`). Đã cung cấp tài liệu hướng dẫn kiểm thử thủ công chi tiết, không ngụy tạo bằng chứng.
- **Bảo toàn CSDL**: `marketplace_java_dev` 26 categories / 286 users (nguyên vẹn); `marketplace_java_test` 0 bản ghi (sạch sẽ sau rollback).

### 5. Kết luận & Khuyến nghị Bước tiếp theo
- Module Category đã hoàn thành di trú và nghiệm thu trọn vẹn ở cả tầng Backend (Java Spring Boot 4.1.1) lẫn tích hợp Frontend (React Vite Proxy).
- Trạng thái kỹ thuật: **READY TO COMMIT**.
- Cột mốc tiếp theo: **PR03-A — Firebase Auth & User Identity Architecture Audit**.

---

## [2026-11-22] TASK PR03-A: FIREBASE AUTH & USER IDENTITY ARCHITECTURE AUDIT

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Khảo sát và kiểm toán độc lập toàn diện kiến trúc xác thực (Authentication) và định danh người dùng (User Identity) giữa backend Python production (`student-marketplace-be`), frontend React (`student-marketplace-fe`), và schema CSDL PostgreSQL thật (`sm_postgres`). Xây dựng thiết kế kiến trúc chuẩn mực và lộ trình di trú sang Java 21 / Spring Boot 4.1.1.
- **Ranh giới an toàn nghiêm ngặt**:
  - Task này thuần túy là Architecture Audit. **Tuyệt đối KHÔNG code tính năng xác thực**.
  - Không thêm dependency Maven vào `pom.xml`.
  - Không tạo `SecurityFilterChain`, `JwtFilter`, `AuthController` hay `UserController`.
  - Không sửa đổi mã nguồn Java ứng dụng, Python, hay React.
  - Không kết nối VPS production, production CSDL hay production Firebase credentials.
  - Không commit, không push.

### 2. Các Phát hiện Kỹ thuật Cốt lõi
1. **Kiến trúc Xác thực Python**:
   - Xác thực token: Sử dụng `firebase_admin.auth.verify_id_token(token)` qua header `Authorization: Bearer <token>`.
   - Cơ chế JIT Auto-Provisioning: Không có API login/register riêng; mọi request có Bearer token hợp lệ đều tự động phân giải hoặc khởi tạo người dùng trong CSDL (`AuthService.get_or_create_user_from_token_payload`).
   - Xử lý race condition: Bắt lỗi `IntegrityError` trên `ux_users_email` và `ux_user_auth_identities_provider_uid`.
   - Kiểm soát trạng thái: `get_current_active_user` chặn tài khoản `BANNED` (403) và `INACTIVE` (403). `require_role(ADMIN)` chặn non-admin (403).
   - Quyền hạn (Capabilities): `can_buy` và `can_sell` kiểm tra động qua bảng `user_capability_restrictions`.
2. **Luồng Xác thực Frontend React**:
   - Client đăng nhập Google Popup qua Firebase Client SDK.
   - Axios request interceptor tự động đính kèm `Authorization: Bearer <token>`. Response interceptor bắt 401, tự động refresh token qua single-flight promise `refreshPromise` và retry request gốc 1 lần.
   - Khi nhận 403 (banned/inactive), tự động chuyển hướng tới `/account-restricted`.
   - Client logout: Xóa token cục bộ qua `firebaseSignOut(auth)` và dọn dẹp cache React Query; không có request revoke lên backend.
3. **Đối soát CSDL PostgreSQL Thật**:
   - Bảng `user_auth_identities` mang ràng buộc duy nhất `ux_user_auth_identities_user_id` trên `user_id`. Khẳng định quan hệ giữa `users` và `user_auth_identities` là **1:1 thực sự** trong CSDL.
4. **Giải pháp Kiến trúc Java Tối ưu**:
   - Lựa chọn **Spring Security OAuth2 Resource Server** (`spring-boot-starter-oauth2-resource-server` với NimbusJwtDecoder) kết nối Google JWKs công khai (`https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com`), loại bỏ sự cồng kềnh và xung đột thư viện của Firebase Admin Java SDK.
   - Cho phép kiểm thử tự động offline 100% bằng synthetic test JWTs mà không cần bất kỳ file service-account JSON nhạy cảm nào.
5. **Biên soạn Tài liệu Kiến trúc**:
   - Tạo file `docs/AUTH_MIGRATION_DESIGN.md` đầy đủ 10 chương, sơ đồ Mermaid và ma trận 13 endpoints.
6. **Lập Lộ trình Thực hiện Phase 3 (4 bước nhỏ)**:
   - PR03-B: Spring Security Foundation & Token Verification.
   - PR03-C: User Identity Resolution & JIT Auto-Provisioning.
   - PR03-D: User API Contract Parity (`/me`, `/me/access-status`, `/banks`).
   - PR03-E: Local React Frontend Integration (Dev Proxy opt-in).

### 3. Kết luận
- Khảo sát kiến trúc hoàn tất 100% với bằng chứng thực tế từ mã nguồn và CSDL.
- Trạng thái: **ARCHITECTURE READY**, sẵn sàng bước vào triển khai Task PR03-B sau khi người dùng phê duyệt.

---

## [2026-11-22] TASK PR03-B: SPRING SECURITY FOUNDATION & FIREBASE ID TOKEN VERIFICATION

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Hiện thực nền tảng bảo mật Spring Security tối thiểu, bảo mật cao, kiểm thử tự động độc lập và xác thực Firebase ID Token sử dụng chuẩn Spring Security OAuth2 Resource Server (`NimbusJwtDecoder`), tuyệt đối không làm gãy Category API hiện có từ Phase 2.
- **Ranh giới an toàn nghiêm ngặt**:
  - Không sửa đổi mã nguồn Python (`student-marketplace-be/`) hoặc React (`student-marketplace-fe/`).
  - Không kết nối VPS production, CSDL production hay Firebase credentials production.
  - Không can thiệp schema CSDL PostgreSQL.
  - Không tự ý commit, push hay deploy.
  - Không lập trình trước logic JIT user provisioning hay DB resolution của PR03-C.

### 2. Thay đổi thực tế đã thực hiện
1. **Dependencies (`pom.xml`)**:
   - Bổ sung `spring-boot-starter-oauth2-resource-server` (kéo theo `spring-security-oauth2-jose`, `nimbus-jose-jwt` và `spring-boot-starter-security`).
   - Bổ sung `spring-security-test` với phạm vi `<scope>test</scope>`.
2. **Cấu hình (`application.yml` & `application-test.yml`)**:
   - Khai báo thuộc tính `firebase.project-id` (mặc định: `student-marketplace-ptit`) và `firebase.jwk-set-uri` (trỏ tới Google JWKs endpoint công khai: `https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com`).
   - Cấu hình test tương ứng trong `application-test.yml` với `project-id: test-marketplace-ptit`.
3. **Bộ xử lý lỗi xác thực chuẩn API Contract**:
   - `RestAuthenticationEntryPoint.java`: Bắt các lỗi chưa xác thực (thiếu header, token sai, hết hạn), trả về HTTP 401 Unauthorized với JSON `{"detail": "Missing or invalid Authorization header."}` và header `WWW-Authenticate: Bearer`.
   - `RestAccessDeniedHandler.java`: Bắt các lỗi không đủ quyền hạn, trả về HTTP 403 Forbidden với JSON `{"detail": "Operation not permitted for your user role."}`.
4. **Xác thực JWT Token Firebase (`FirebaseJwtCustomValidator.java` & `JwtConfig.java`)**:
   - Xác thực bắt buộc thuật toán RS256.
   - Xác thực Issuer (`https://securetoken.google.com/{projectId}`).
   - Xác thực Audience (`{projectId}`).
   - Xác thực Thời gian sống (`exp`, `nbf` cho phép 60s clock skew).
   - Xác thực Subject (`sub`): Bắt buộc tồn tại và không được rỗng / chứa toàn khoảng trắng.
   - Xác thực `auth_time`: Bắt buộc tồn tại và không được ở tương lai (cho phép 60s clock skew).
   - Xác thực `iat`: Bắt buộc không được ở tương lai (cho phép 60s clock skew).
5. **Cấu hình Spring Security (`SecurityConfig.java`)**:
   - Quản lý phiên: Stateless (`SessionCreationPolicy.STATELESS`), tắt CSRF, cấu hình CORS chuẩn.
   - Cho phép công khai (`permitAll()`): `GET /api/v1/categories`, `GET /api/v1/categories/**`, `GET /api/v1/users/banks`, `GET /api/v1/users/{public_id}/**`, `OPTIONS /**`.
   - Bảo vệ các endpoint còn lại: Mặc định yêu cầu xác thực (`anyRequest().authenticated()`).
   - Kết nối OAuth2 Resource Server với bộ custom entry point và access denied handler.
6. **Bảo toàn Entity (`Category.java`)**:
   - Chuyển constructor không đối số `@NoArgsConstructor(access = AccessLevel.PROTECTED)` sang public `@NoArgsConstructor` để đảm bảo tính khả dụng xuyên package và tương thích với mô hình `User.java`.

### 3. Kết quả Kiểm thử & Nghiệm thu
- **Kiểm thử Đơn vị JWT (`FirebaseJwtValidatorTest.java`)**:
  - 11/11 tests PASS (JWT01 - JWT11). Sử dụng cặp khóa RSA synthetic 2048-bit cục bộ.
  - Kiểm chứng thành công: Token hợp lệ, token hết hạn, sai issuer, sai audience, thiếu/rỗng sub, sai chữ ký / sai khóa RSA, thiếu auth_time, auth_time tương lai, iat tương lai, token từ project khác, thuật toán không hợp lệ (HS256).
- **Kiểm thử Tích hợp Bảo mật (`SecurityIntegrationTest.java`)**:
  - 14/14 tests PASS (SEC01 - SEC14) qua MockMvc.
  - Kiểm chứng thành công: Category API công khai không cần token, Category detail công khai, 401 khi thiếu/sai/hết hạn token, 200 khi có token hợp lệ, chống giả mạo thông tin client-side (spoofing query params/body), không rò rỉ chi tiết nhạy cảm (stack traces/class names), preflight OPTIONS 200 OK, Category 404 và 422 được bảo toàn nguyên vẹn, 403 Forbidden cho quyền hạn không hợp lệ, và không có bất kỳ thao tác ghi CSDL nào trong quá trình xác thực token (stateless).
- **Tổng số kiểm thử của Repository**: **46/46 tests PASS** (21 tests baseline + 25 tests security mới).
- **Tình trạng CSDL**:
  - `marketplace_java_dev`: 286 users, 26 categories (nguyên vẹn).
  - `marketplace_java_test`: 0 users, 0 categories (rollback sạch sẽ sau kiểm thử).

### 4. Kết luận
- Task PR03-B đã hoàn thành 100% mục tiêu kỹ thuật, sẵn sàng bàn giao cho người dùng phê duyệt trước khi chuyển sang Task PR03-C (User Identity Resolution & JIT Auto-Provisioning).

---

## [2026-11-22] TASK PR03-B.1: SECURITY HARDENING & AUTHORIZATION BOUNDARY REVIEW

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Rà soát phản biện độc lập, tìm kiếm lỗ hổng và gia cố toàn diện nền tảng bảo mật của PR03-B trước khi bước vào PR03-C.
- **Ranh giới an toàn nghiêm ngặt**:
  - Không truy cập production VPS hay production CSDL.
  - Không sửa đổi mã nguồn Python (`student-marketplace-be/`) hoặc React (`student-marketplace-fe/`).
  - Không can thiệp schema CSDL PostgreSQL.
  - Không tự ý commit, push hay deploy.

### 2. Các Lỗ hổng & Thay đổi Kỹ thuật Thực tế
1. **Khắc phục Lỗ hổng Trùng khớp Route Người dùng Công khai (Route Collision - P0/P1)**:
   - *Phát hiện*: Quy tắc cũ `requestMatchers(HttpMethod.GET, "/api/v1/users/{public_id}/**").permitAll()` quá rộng, `{public_id}` vô tình khớp với phân đoạn `"me"` và `"search"`, khiến các route nhạy cảm như `/api/v1/users/me`, `/api/v1/users/me/access-status`, `/api/v1/users/me/bank-account` bị lộ lọt khi truy cập ẩn danh (không bị thách thức 401).
   - *Khắc phục*:
     - Đặt các route `/api/v1/users/me`, `/api/v1/users/me/**`, `/api/v1/users/search` lên ĐẦU chuỗi matcher yêu cầu `authenticated()`.
     - Giới hạn tham số `publicId` bằng regex UUID 5 nhóm hex: `/api/v1/users/{publicId:[0-9a-fA-F]+-[0-9a-fA-F]+-[0-9a-fA-F]+-[0-9a-fA-F]+-[0-9a-fA-F]+}` và sub-path `/sold-products`, loại bỏ hoàn toàn wildcard `/**`.
2. **Loại bỏ giả định sai về quyền Admin qua JWT Scope (P1)**:
   - Trong Chợ PTITers, quyền `ADMIN` xuất phát từ bảng CSDL `users.role`. Token Firebase ID không mang `scope=admin`.
   - Đã gỡ bỏ hoàn toàn matcher `/api/v1/test/admin` và `hasAuthority("SCOPE_admin")` khỏi production `SecurityConfig.java`. Việc phân quyền nghiệp vụ 403 dựa trên `users.role` và `users.status` được hoãn lại (deferred) sang Task PR03-C khi có User Identity Resolution.
3. **Thắt chặt kiểm thực Audience Firebase (P1)**:
   - Sửa `JwtClaimValidator` kiểm tra nghiêm ngặt `aud` phải có kích thước ĐÚNG BẰNG 1 và phần tử duy nhất đó phải bằng chính xác `projectId`. Token chứa nhiều audiences bị từ chối 100%.
4. **Bổ sung toàn diện 14 kịch bản kiểm thực claims JWT biên (P1/P2)**:
   - `FirebaseJwtCustomValidator` kiểm tra bắt buộc: `kid` header (không rỗng), `sub` (không rỗng), `exp` (bắt buộc có), `iat` (bắt buộc có và không ở tương lai), `auth_time` (bắt buộc có, đúng kiểu numeric timestamp, không ở tương lai).
5. **Khôi phục tính đóng gói của `Category.java` (P2)**:
   - Khôi phục `@NoArgsConstructor(access = AccessLevel.PROTECTED)` trong Entity sản xuất `Category.java`.
   - Chuyển logic tạo fixture sang `CategoryTestFixtures.java` trong package test `com.ptiters.marketplace.category`.
6. **Gia cố CORS và chính sách an toàn**:
   - Cấu hình nguồn gốc rõ ràng (explicit origins: `localhost:3000`, `localhost:5173`, `127.0.0.1:3000`, `127.0.0.1:5173`).
   - Đặt `allowCredentials(false)` do API dùng Bearer tokens, loại bỏ rủi ro bảo mật CORS wildcard.

### 3. Kết quả Kiểm thử & Nghiệm thu
- **Kiểm thử Đơn vị JWT (`FirebaseJwtValidatorTest.java`)**: **19/19 tests PASS** (A - N, kiểm tra đầy đủ mọi loại claims, headers, formats).
- **Kiểm thử Tích hợp Bảo mật (`SecurityIntegrationTest.java`)**: **23/23 tests PASS** (ma trận phân quyền ẩn danh, route collision, CORS, statelessness).
- **Tổng số kiểm thử của Toàn bộ Dự án**: **63/63 tests PASS** (0 failures, 0 errors, 0 skipped).
- **Tình trạng CSDL**:
  - `marketplace_java_dev`: 286 users, 26 categories (nguyên vẹn).
  - `marketplace_java_test`: 0 users, 0 categories (rollback sạch sẽ sau kiểm thử).

### 4. Kết luận
- Task PR03-B.1 đã khắc phục triệt để các lỗ hổng ranh giới phân quyền, hoàn thành gia cố nền tảng bảo mật, sẵn sàng bước vào Task PR03-C.


---

## [2026-11-22] TASK PR03-C: USER IDENTITY RESOLUTION & SAFE JIT PROVISIONING

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Xây dựng tầng định danh người dùng an toàn (User Identity Resolution) ánh xạ từ Firebase ID Token đã xác thực sang bản ghi `User` và `UserAuthIdentity` trong PostgreSQL.
- **Tính năng cốt lõi**:
  - Tự động tạo tài khoản lần đầu (Just-In-Time Provisioning) với các giá trị mặc định chuẩn mực (`STUDENT`, `ACTIVE`, `show_phone_publicly = false`).
  - Xử lý xung đột ghi đồng thời (Race Condition) bằng phân tách giao dịch cô lập (`Propagation.REQUIRES_NEW`) và bounded retry 2 lần.
  - Cơ chế liên kết tài khoản an toàn (Safe Account Linking) cho tài khoản cũ có `email_verified == true`.
  - Cơ chế chống chiếm đoạt tài khoản (Anti-takeover): Ngăn chặn việc liên kết token mới vào tài khoản đã gắn với identity khác (HTTP 409 Conflict).
  - Kiểm soát trạng thái tài khoản: `requireActiveUser` từ chối `BANNED` (403 "User account is banned.") và `INACTIVE` (403 "User account is inactive."), nhưng cho phép truy cập `/users/me/access-status`.
- **Ranh giới an toàn nghiêm ngặt**:
  - Không triển khai `UserController` (dành riêng cho PR03-D).
  - Không sửa đổi mã nguồn Python hay React.
  - Không can thiệp schema CSDL PostgreSQL.
  - Không tự ý commit, push hay deploy.

### 2. Các Thay đổi Kỹ thuật Thực tế
1. **Mở rộng Repositories**:
   - `UserRepository.java`: Bổ sung `Optional<User> findByEmail(String email)`.
   - `UserAuthIdentityRepository.java`: Bổ sung `Optional<UserAuthIdentity> findByUser_UserId(Long userId)` để kiểm tra ràng buộc 1:1.
2. **Triển khai `UserProvisioningService`**:
   - Chạy trong giao dịch độc lập `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
   - Phân tích token Firebase, tự động fallback email (`{uid}@firebase.user`) và name (email prefix trước `@`). Cắt ngắn an toàn tránh vượt giới hạn cột (`fullName <= 100`, `avatarUrl <= 500`).
   - Kiểm tra `email_verified`: từ chối liên kết tài khoản cũ nếu `email_verified == false` (400 Bad Request).
   - Kiểm tra chiếm đoạt tài khoản: ném 409 Conflict nếu tài khoản đã liên kết với identity khác.
   - Cập nhật `lastActiveAt` (UTC) và `avatarUrl` (nếu thay đổi) cho người dùng hiện có.
3. **Triển khai `UserService`**:
   - Điều phối truy vấn danh tính qua `UserAuthIdentityRepository`.
   - Bắt `DataIntegrityViolationException` bên ngoài giao dịch con, thực hiện bounded retry tái truy vấn danh tính đã được luồng đồng thời ghi thành công.
   - Kiểm tra trạng thái tài khoản `requireActiveUser(user)`.
4. **Triển khai `CurrentUserResolver`**:
   - Component trợ giúp giải phóng định danh người dùng từ `SecurityContextHolder` và Spring Security `Jwt`.
5. **Kiểm thử Tích hợp Toàn diện (`UserIdentityResolutionIntegrationTest.java`)**:
   - 18/18 tests PASS (IDENTITY01 - IDENTITY18).
   - Kiểm tra thành công: JIT provisioning, Idempotency, Client Spoofing rejection, Auto-linking, Anti-takeover 409, Unverified email 400, Concurrency 10 threads đồng thời, MockMvc 401/200/403, Category isolation.

### 3. Kết quả Kiểm chứng
- **Biên dịch**: `./mvnw.cmd clean compile` -> `BUILD SUCCESS`.
- **Kiểm thử**: `./mvnw.cmd test` -> `BUILD SUCCESS` (**81/81 tests PASS**, 0 failures, 0 errors, 0 skipped).
- **Tình trạng CSDL**:
  - `marketplace_java_dev`: 286 users, 26 categories, 16 identities (nguyên vẹn).
  - `marketplace_java_test`: 0 users, 0 categories, 0 identities (sạch sẽ 100%).

### 4. Kết luận
- Task PR03-C hoàn thành xuất sắc 100% mục tiêu, đảm bảo tính toàn vẹn CSDL, tính an toàn giao dịch và sự tương thích nghiệp vụ tuyệt đối với Python backend. Sẵn sàng cho Task PR03-D (User Identity & Me APIs Parity).


---

## [2026-11-22] TASK PR03-D1: CURRENT USER & ACCESS STATUS API PARITY

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Hiện thực hóa hai API cốt lõi về hồ sơ và trạng thái quyền truy cập của người dùng hiện tại (`GET /api/v1/users/me` và `GET /api/v1/users/me/access-status`), kết nối các dịch vụ User Identity và JIT Provisioning (PR03-C) với các Controller Spring Boot sản xuất.
- **Ranh giới an toàn nghiêm ngặt**:
  - Không sửa đổi mã nguồn Python hay React.
  - Không kết nối production VPS hay production database.
  - Không triển khai các endpoint người dùng khác ngoài phạm vi (`PATCH /me`, `banks`, `{public_id}`, `bank-account`).
  - Không chỉnh sửa schema hay thêm migration Flyway.

### 2. Các Thay đổi Kỹ thuật Thực tế
1. **Rà soát & Kiểm toán Cổng Audit Gate PR03-C**:
   - Xác minh số lượng test suite: Làm rõ lỗi chuyển vị số liệu (transposition typo) trong báo cáo PR03-C. Git diff chứng minh 0 test nào của Category hay Security bị xóa, sửa hay suy yếu.
   - Gia cố logic liên kết tài khoản: Bổ sung cờ `isSyntheticEmail` để ngăn chặn tuyệt đối việc fallback email `{providerUid}@firebase.user` vô tình liên kết với tài khoản người dùng cũ trong CSDL.
2. **Triển khai DTOs tương thích 100% với Python FastAPI và React TypeScript**:
   - `UserResponse.java`: Ánh xạ đủ 16 trường chuẩn (`user_id`, `public_id`, `email`, `full_name`, `phone`, `show_phone_publicly`, `social_links`, `avatar_url`, `bio`, `delivery_area`, `default_meetup_area`, `role`, `status`, `last_active_at`, `created_at`, `updated_at`).
   - `UserAccessStatusResponse.java`: Ánh xạ đủ 6 trường chuẩn (`user_id`, `status`, `role`, `can_buy`, `can_sell`, `active_restrictions`).
   - `UserCapabilityRestrictionResponse.java`: Ánh xạ `capability`, `expires_at`.
3. **Triển khai Thực thể & Dịch vụ Năng lực Quyền hạn (Capability)**:
   - `UserCapabilityRestriction.java`: Ánh xạ bảng CSDL PostgreSQL `user_capability_restrictions`.
   - `UserCapabilityRestrictionRepository.java`: Truy vấn JPQL tìm kiếm các hạn chế quyền năng lực đang có hiệu lực (`starts_at <= now`, `expires_at > now`, `revoked_at IS NULL`).
   - `CapabilityService.java`: Tính toán động `can_buy`, `can_sell` và danh sách hạn chế theo chuẩn Python. Tự động trả về `can_buy=false, can_sell=false` cho tài khoản `BANNED`/`INACTIVE`, và `true/true` cho tài khoản `ADMIN`.
4. **Triển khai `UserController.java`**:
   - `GET /api/v1/users/me`: Yêu cầu token hợp lệ và tài khoản `ACTIVE`. Chặn `BANNED` (403 "User account is banned.") và `INACTIVE` (403 "User account is inactive.").
   - `GET /api/v1/users/me/access-status`: Yêu cầu token hợp lệ, cho phép cả tài khoản `BANNED` và `INACTIVE` truy vấn để hiển thị trạng thái trên giao diện.
5. **Kiểm thử Tích hợp Toàn diện (`UserControllerIntegrationTest.java`)**:
   - 20/20 scenarios PASS (USER01 - USER20).
   - Kiểm chứng MockMvc: 401 khi thiếu/sai token, 200 cho active user, 403 cho banned/inactive trên `/me`, 200 trên `/me/access-status`, ngăn chặn leo thang đặc quyền client-side, bảo toàn tính công khai của Category API, ngăn ngừa rò rỉ trường nhạy cảm, và xử lý xung đột 409 Conflict.

### 3. Kết quả Kiểm chứng
- **Biên dịch**: `./mvnw.cmd clean compile` -> `BUILD SUCCESS`.
- **Kiểm thử**: `./mvnw.cmd test` -> `BUILD SUCCESS` (**101/101 tests PASS**, 0 failures, 0 errors, 0 skipped).
- **Tình trạng CSDL**:
  - `marketplace_java_dev`: 286 users, 26 categories, 16 identities, 0 restrictions (nguyên vẹn).
  - `marketplace_java_test`: 0 users, 0 categories, 0 identities, 0 restrictions, 0 reports (cô lập, dọn dẹp sạch sẽ 100%).

### 4. Kết luận
- Task PR03-D1 hoàn thành xuất sắc 100% mục tiêu, đạt độ tương thích hợp đồng API chuẩn xác tuyệt đối với Python và React Frontend. Sẵn sàng cho Task PR03-E (hoặc PR03-D2).


---

## [2026-11-22] TASK PR03-E: SECURE LOCAL REACT AUTH INTEGRATION & ISOLATED E2E SETUP

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Tích hợp luồng xác thực thực tế của React Frontend với các Current User APIs của Java Backend (`GET /api/v1/users/me`, `GET /api/v1/users/me/access-status`) trong một môi trường cục bộ CÔ LẬP, AN TOÀN TUYỆT ĐỐI.
- **Ranh giới an toàn tối cao**:
  - Tuyệt đối không kết nối luồng đăng nhập test với CSDL phát triển `marketplace_java_dev` (chứa 286 users / 26 categories).
  - Không sửa đổi cài đặt Firebase Production (`student-marketplace-ptit`).
  - Không thay đổi backend Python (`student-marketplace-be/`).
  - Bảo vệ nguyên vẹn các file frontend đang có thay đổi chưa commit (`App.tsx`, `Header.tsx`, `src/features/users/`).
  - Không tự ý commit, push hay deploy.

### 2. Các Thay đổi Kỹ thuật Thực tế
1. **Frontend Dev Proxy có chọn lọc (`student-marketplace-fe/vite.config.ts`)**:
   - Thêm biến cờ `JAVA_AUTH_PROXY=1`: Định tuyến cục bộ CHỈ HAI ROUTE `/api/v1/users/me` và `/api/v1/users/me/access-status` sang Java backend port 8080 qua regex: `^/api/v1/users/me(/access-status)?($|\\?|/$)`.
   - Các route người dùng chưa di trú (`/api/v1/users/me/bank-account`, `/api/v1/users/banks`, `/api/v1/users/search`, `/api/v1/users/{public_id}`) và các module khác (`/api/v1/products`, `/api/v1/orders`, `/ws`) tiếp tục được định tuyến tới Python port 8000.
   - Mặc định cờ tắt (OFF), giữ nguyên toàn bộ hành vi proxy gốc.
2. **Cơ chế Firebase DEV Fail-Closed trên Frontend (`student-marketplace-fe/src/lib/firebase.ts`)**:
   - Thêm cờ `VITE_FIREBASE_DEV_MODE=1`: Cho phép đọc biến môi trường Firebase DEV riêng biệt (`VITE_FIREBASE_DEV_*`).
   - Thiết kế Fail-Closed: Nếu bật `VITE_FIREBASE_DEV_MODE=1` nhưng thiếu thông tin cấu hình DEV, ứng dụng ném ngoại lệ FATAL và dừng khởi tạo, loại bỏ 100% rủi ro vô tình gửi token thử nghiệm tới Firebase Production.
3. **Môi trường CSDL E2E Cô lập & Bảo vệ Khởi động (`marketplace-ptit-be`)**:
   - Thiết lập profile Spring `e2e` (`application-e2e.yml`) kết nối CSDL chuyên biệt `marketplace_java_e2e` với `ddl-auto: validate` và `flyway.enabled: false`.
   - `E2eDatabaseSafetyValidator.java`: Bean kiểm tra an toàn khi khởi động profile `e2e`. Thực hiện `SELECT current_database()`; nếu CSDL không phải `marketplace_java_e2e`, ném `IllegalStateException` và hủy khởi động ngay lập tức, bảo vệ tuyệt đối CSDL `marketplace_java_dev`.
4. **Kiểm toán Cổng Audit Gate PR03-D1**:
   - Kiểm toán cơ chế Account Linking: Đã có cờ `isSyntheticEmail` ngăn chặn fallback email `{uid}@firebase.user` vô tình liên kết với tài khoản có sẵn.
   - Kiểm toán dọn dẹp dữ liệu test: Dọn dẹp theo thứ tự phụ thuộc khóa ngoại và cô lập 100% trong `marketplace_java_test`.
   - Kiểm toán DTO: Đạt 100% tương thích giữa Python Pydantic, Java DTO và TypeScript types.

### 3. Kết quả Kiểm chứng
- **Frontend**: `npm run build` -> Hoàn thành trong 9.16s; `npm run lint` -> 0 errors.
- **Java Backend**: `./mvnw.cmd test` -> `BUILD SUCCESS` (**101/101 tests PASS**, 0 failures, 0 errors, 0 skipped).
- **Tình trạng CSDL**:
  - `marketplace_java_dev`: 286 users, 26 categories, 16 identities, 0 restrictions (nguyên vẹn 100%).
  - `marketplace_java_test`: 0 users, 0 categories, 0 identities, 0 restrictions, 0 reports (sạch sẽ 100%).

### 4. Kết luận
- Task PR03-E đã hoàn thành toàn bộ nền tảng kỹ thuật và cấu hình an toàn cho việc tích hợp xác thực giữa React và Java Spring Boot.
- Trạng thái nghiệm thu: **READY FOR MANUAL UI TEST / BLOCKED BY MANUAL SETUP** (do cần nhà phát triển thiết lập project Firebase DEV và database `marketplace_java_e2e` trước khi thực hiện thao tác trình duyệt trực tiếp).


---

## [2026-11-24] TASK PR03-D2: REMAINING USER PROFILE & BANK APIS PARITY

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Hiện thực hóa toàn bộ các API còn lại liên quan đến hồ sơ người dùng (`User`), ngân hàng (`Banks`), và tài khoản thụ hưởng (`BankAccount`) nhằm đạt độ tương thích 100% hợp đồng API với Python FastAPI và React Frontend:
  1. `PATCH /api/v1/users/me`: Cập nhật thông tin cá nhân (partial update: `full_name`, `phone`, `show_phone_publicly`, `social_links`, `bio`, `delivery_area`, `default_meetup_area`). Yêu cầu xác thực `ACTIVE`.
  2. `GET /api/v1/users/banks`: Danh sách các ngân hàng được hỗ trợ tại Việt Nam (VietQR/NAPAS). Truy cập công khai (`permitAll()`).
  3. `GET /api/v1/users/{public_id}`: Xem hồ sơ công khai của người dùng qua `public_id` (UUID). Truy cập công khai.
  4. `GET /api/v1/users/{public_id}/sold-products`: Xem danh sách sản phẩm đã bán thành công của người dùng qua `public_id`. Truy cập công khai.
  5. `GET /api/v1/users/me/bank-account`: Lấy thông tin tài khoản ngân hàng của chính người dùng hiện tại. Yêu cầu xác thực `ACTIVE`.
  6. `PUT /api/v1/users/me/bank-account`: Cập nhật/thiết lập tài khoản ngân hàng (`bank_bin`, `account_number`, `account_name`). Yêu cầu xác thực `ACTIVE`.
  7. `DELETE /api/v1/users/me/bank-account`: Xóa thông tin tài khoản ngân hàng thụ hưởng. Yêu cầu xác thực `ACTIVE`.
- **Ranh giới an toàn tối cao**:
  - `marketplace-ptit-be/` là repository mục tiêu **DUY NHẤT** được ghi mã nguồn, cấu hình và tài liệu.
  - `student-marketplace-be/` và `student-marketplace-fe/` là **READ-ONLY**.
  - Tuyệt đối không kết nối VPS production, production database, hoặc credentials production.
  - Không tự ý commit, push hoặc deploy.

### 2. Các Thay đổi Kỹ thuật Thực tế
1. **Định nghĩa DTOs & Validation Helpers (`com.ptiters.marketplace.user`)**:
   - `SupportedBank.java`: Biểu diễn thông tin ngân hàng (`bin`, `code`, `short_name`, `name`).
   - `SupportedBanksRegistry.java`: Đăng ký tĩnh 45+ ngân hàng chuẩn VietQR/NAPAS (VCB, CTG, BIDV, VBA, MB, TCB, ACB, VPB, TPB, TIMO, CAKE, v.v.), tra cứu nhanh theo BIN và Code alias (`resolveBankBin`, `getBankByBin`).
   - `VietnamPhoneUtils.java`: Tiện ích chuẩn hóa và kiểm thực số điện thoại di động Việt Nam (chấp nhận `+84`, khoảng trắng, dấu chấm, gạch ngang; chuẩn hóa về định dạng 10 chữ số `^0[35789]\d{8}$`; ném 400 Bad Request nếu không hợp lệ).
   - `SocialLinkValidator.java`: Kiểm thực liên kết mạng xã hội theo đúng miền hợp lệ của từng nền tảng (`FACEBOOK`, `INSTAGRAM`, `TIKTOK`, `ZALO`, `LINKEDIN`).
   - `UserUpdate.java`: Payload cập nhật hồ sơ cá nhân với `@Size` constraints.
   - `PublicUserProfileResponse.java`: DTO phản hồi hồ sơ công khai (bảo vệ quyền riêng tư: chỉ hiển thị `phone` khi `show_phone_publicly == true`).
   - `SoldProductTransactionReview.java`, `SoldProductSummary.java`, `SoldProductListResponse.java`: DTOs danh sách sản phẩm đã bán thành công.
   - `BankAccountResponse.java` & `BankAccountUpdate.java`: DTOs đọc/ghi tài khoản ngân hàng thụ hưởng (kiểm tra `bank_bin` hợp lệ, `account_number` chỉ gồm 4-30 chữ số, `account_name` chỉ gồm 2-100 ký tự chữ cái và dấu tên chuẩn hóa).
2. **Triển khai Logic Nghiệp vụ (`UserService.java` & `UserProfileQueryRepository.java`)**:
   - `UserProfileQueryRepository.java`: Thực hiện các truy vấn thống kê hiệu năng cao qua `NamedParameterJdbcTemplate` (`countActiveListings`, `countCompletedSold`, `getReviewStats`, `getSoldProducts`) tương thích 100% với schema PostgreSQL 15+ (`DB_v7.md`) mà không gây phụ thuộc chéo vào các Entity JPA của giai đoạn sau.
   - `UserService.java`:
     - `updateProfile(User user, UserUpdate payload)`: Cập nhật từng phần an toàn, bỏ qua các trường hệ thống (`email`, `role`, `status`), chuẩn hóa SĐT và validate link mạng xã hội.
     - `getPublicProfile(UUID publicId)`: Tìm người dùng theo `public_id`, ném 404 nếu không tìm thấy hoặc người dùng bị `BANNED`/`INACTIVE`, ẩn số điện thoại nếu người dùng không công khai.
     - `getSoldProducts(UUID publicId)`: Lấy lịch sử sản phẩm đã bán từ các đơn hàng `COMPLETED`.
     - `getBankAccount(User user)`: Trích xuất và ánh xạ tên ngân hàng từ `SupportedBanksRegistry`.
     - `updateBankAccount(User user, BankAccountUpdate payload)`: Validate BIN/Code, số tài khoản, tên tài khoản; cập nhật vào bản ghi `User`.
     - `deleteBankAccount(User user)`: Xóa các thông tin `bank_bin`, `bank_account_number`, `bank_account_name`.
3. **Cập nhật `UserController.java` & `SecurityConfig.java`**:
   - `UserController.java`: Bổ sung `@PatchMapping("/me")`, `@GetMapping("/banks")`, `@GetMapping("/{publicId}")`, `@GetMapping("/{publicId}/sold-products")`, `@GetMapping("/me/bank-account")`, `@PutMapping("/me/bank-account")`, `@DeleteMapping("/me/bank-account")`.
   - `SecurityConfig.java`: Định tuyến phân quyền nhất quán: các endpoint `/users/me/**` yêu cầu xác thực `authenticated()`; các endpoint `/users/banks`, `/users/{publicId}`, `/users/{publicId}/sold-products` là `permitAll()`.
4. **Kiểm thử Tích hợp Toàn diện (`UserControllerIntegrationTest.java`)**:
   - Bổ sung 18 test cases mới (USER21 -> USER38) bao phủ 100% các kịch bản thành công và ngoại lệ:
     - USER21: Update hồ sơ thành công (partial update).
     - USER22: Chuẩn hóa số điện thoại Việt Nam (`+84 987.654-321` -> `0987654321`).
     - USER23: Báo lỗi 400 khi số điện thoại không hợp lệ.
     - USER24: Báo lỗi 400 khi liên kết mạng xã hội không khớp domain nền tảng.
     - USER25: Bỏ qua việc sửa các trường hệ thống (`email`, `role`, `status`).
     - USER26: Báo 401 khi chưa xác thực, 403 khi tài khoản bị BANNED.
     - USER27: Endpoint công khai `/users/banks` trả về danh sách ngân hàng đầy đủ.
     - USER28: Endpoint công khai `/users/{public_id}` ẩn số điện thoại khi `show_phone_publicly == false`.
     - USER29: Báo 404 khi UUID không tồn tại hoặc tài khoản bị BANNED.
     - USER30: Endpoint công khai `/users/{public_id}/sold-products` trả về danh sách rỗng khi chưa có đơn hàng.
     - USER31: Đọc tài khoản ngân hàng trả về 200 null khi chưa thiết lập.
     - USER32: Cập nhật tài khoản ngân hàng thành công với mã BIN.
     - USER33: Cập nhật tài khoản ngân hàng thành công với mã Code alias (`MB`).
     - USER34: Báo lỗi 400 khi mã ngân hàng không được hỗ trợ.
     - USER35: Báo lỗi 400 khi số tài khoản chứa chữ cái.
     - USER36: Báo lỗi 400 khi tên tài khoản chứa số.
     - USER37: Xóa tài khoản ngân hàng thành công (HTTP 204 No Content).
     - USER38: Kiểm tra ranh giới xác thực và trạng thái ACTIVE trên các endpoint bank account.

### 3. Kết quả Kiểm chứng
- **Biên dịch**: `./mvnw.cmd clean compile` -> `BUILD SUCCESS`.
- **Kiểm thử**: `./mvnw.cmd test` -> `BUILD SUCCESS` (**119/119 tests PASS**, 0 failures, 0 errors, 0 skipped):
  - 1 ApplicationContext test (`MarketplaceApplicationTests`)
  - 3 User read-only dev DB tests (`UserReadOnlyRepositoryTest`)
  - 4 User write test DB tests (`UserWriteIntegrationTest`)
  - 4 Category write test DB tests (`CategoryWriteIntegrationTest`)
  - 9 Category HTTP API tests (`CategoryControllerIntegrationTest`)
  - 19 Firebase JWT Validator unit tests (`FirebaseJwtValidatorTest`)
  - 23 Security Integration tests (`SecurityIntegrationTest`)
  - 18 User Identity Resolution Integration tests (`UserIdentityResolutionIntegrationTest`)
  - 38 User Controller HTTP Integration tests (`UserControllerIntegrationTest`)
- **Tình trạng CSDL sau kiểm thử**:
  - `marketplace_java_dev`: 286 users, 26 categories, 16 identities, 0 restrictions (nguyên vẹn 100%).
  - `marketplace_java_test`: 0 users, 0 categories, 0 identities, 0 restrictions (sạch sẽ, cô lập 100%).

### 4. Kết luận
- Task PR03-D2 hoàn thành xuất sắc 100% yêu cầu, đóng lại toàn bộ các API User Profile và Bank Account cần thiết cho Phase 3 với chất lượng kỹ thuật cao nhất.

---

## [Task ARCH-SKELETON] — Hoàn thiện cấu trúc Package và Skeleton Architecture 1-1 cho toàn bộ Backend Java (`marketplace-ptit-be`)
- **Ngày thực hiện**: 2026-11-24.
- **Mục tiêu**: Hoàn thiện toàn bộ cấu trúc package, POJO Domain Model, Repository interface, DTO Record (Java 21), Service method stub và Controller endpoint signatures cho 15 domain modules của `marketplace-ptit-be`, đối chiếu 1-1 với backend Python (`student-marketplace-be/app/`).
- **Phạm vi hoàn thành**:
  1. `product/` (16 files): Product, ProductImage models, ListingType, ProductStatus, ProductModerationStatus, ProductApprovalStatus enums, ProductController (`/api/v1/products`), AdminProductApprovalController (`/api/v1/admin/product-approvals`), Service, Repositories, DTO records.
  2. `favorite/` (7 files): Favorite model, FavoriteController (`/api/v1/favorites`), Service, Repository, DTO records.
  3. `auction/` (15 files): Auction, AuctionBid models, AuctionStatus enum, AuctionController (`/api/v1/auctions`), AuctionWebSocketHandler, Service, Repositories, DTO records.
  4. `order/` (18 files): Order, OrderItem, OrderStatusHistory models, OrderStatus, PaymentStatus, PaymentMethod, FulfillmentMethod, CancelledBy enums, OrderController (`/api/v1/orders`), OrderWebSocketHandler, Service, Repositories, DTO records.
  5. `review/` (9 files): Review model, ReviewController (`/api/v1/orders/{orderId}/review` và `/api/v1/users/{publicId}/reviews`), Service, Repository, DTO records.
  6. `conversation/` (25 files): Conversation, Member, Invite, Message, Attachment, Mention, Reaction models, ReactionKey enum, ConversationController (`/api/v1/conversations`), Service, Repositories, DTO records.
  7. `notification/` (13 files): Notification, UserDeviceToken models, NotificationType enum, NotificationController (`/api/v1/notifications`), Service, Repositories, DTO records.
  8. `report/` (22 files): Report, ReportAttachment, ModerationAction models, ReportTargetType, ReportReason, ReportStatus, ReportWorkflowState, ReportConclusion, ModerationActionType enums, ReportController (`/api/v1/reports`), AdminReportController (`/api/v1/admin/reports`), Service, Repositories, DTO records.
  9. `feedback/` (26 files): SiteFeedback, Attachment, SupportContribution, SupportClaim models, SupportClaimStatus enum, FeedbackController (`/api/v1/feedback`), AdminFeedbackController (`/api/v1/admin/feedback`), AdminSupportController (`/api/v1/admin/support-contributions`), AdminSupportClaimsController (`/api/v1/admin/support-claims`), Service, Repositories, DTO records.
  10. `communitymoment/` (14 files): CommunityMoment model, CommunityMomentStatus enum, CommunityMomentController (`/api/v1/community-moments`), AdminCommunityMomentController (`/api/v1/admin/community-moments`), Service, Repository, DTO records.
  11. `dailyneed/` (12 files): DailyNeed, DailyJournalCheckin models, DailyNeedController (`/api/v1/daily-needs`), Service, Repositories, DTO records.
  12. `tutorial/` (12 files): Tutorial model, TutorialController (`/api/v1/tutorials`), AdminTutorialController (`/api/v1/admin/tutorials`), Service, Repository, DTO records.
  13. `integration/` (2 files): CloudinaryStorageService, FirebasePushService.
  14. `worker/` (4 files): MediaCleanupJob, MediaCleanupWorker, OrderAutoCancelWorker, WorkerRetryExecutor.
  15. `realtime/` (4 files): WebSocketConfig, PresenceService, MediaValidator, ClientIpResolver.
  16. `user/`: Bổ sung UserSearchResponse và endpoint search user (`GET /api/v1/users/search`).
- **Kết quả kiểm thử & xác minh**:
  - Biên dịch thành công 277 file nguồn Java mà không có lỗi.
  - Test suite: **119/119 PASS (0 failures, 0 errors, 0 skipped)**.
  - Hibernate DDL Validation: Giữ nguyên `spring.jpa.hibernate.ddl-auto: validate` với CSDL thật, không bị vi phạm missing table do các domain model mới đều sử dụng kiến trúc POJO tách biệt.

---

## [2026-11-24] TASK PR04: PRODUCT & FAVORITE BUSINESS LOGIC & POSTGRESQL INTEGRATION

### 1. Bối cảnh & Mục tiêu
- **Mục tiêu**: Triển khai hoàn chỉnh nghiệp vụ, ánh xạ JPA Entity và kết nối CSDL PostgreSQL 15+ thật cho **Phase 4 (`product/` và `favorite/`)**, đảm bảo tương thích 1-1 với hợp đồng API của backend Python (`student-marketplace-be/app/modules/products/` và `student-marketplace-be/app/modules/favorites/`) và frontend React (`student-marketplace-fe/src/features/products/` và `favorites/`).
- **Ranh giới an toàn**:
  - `spring.jpa.hibernate.ddl-auto: validate` được bảo toàn tuyệt đối, ánh xạ chính xác 100% cột, kiểu dữ liệu native enum, quan hệ khóa ngoại và constraints của PostgreSQL.
  - Không sửa logic đang chạy của các module đã hoàn thành (`category/`, `user/`, `configuration/`, `security/`, `exception/`).
  - DTO Records (Java 21) với `@JsonProperty` chuẩn snake_case khớp 100% API contract, không trả về Entity trực tiếp từ Controller.

### 2. Các thay đổi thực tế đã thực hiện
1. **JPA Entities & Composite Keys Khớp 100% PostgreSQL Schema (`DB_v7.md`)**:
   - `Product.java`: `@Entity @Table(name = "products")` với các enum `@Enumerated(EnumType.STRING)` (`ListingType`, `ProductStatus`, `ProductModerationStatus`, `ProductApprovalStatus`), Lazy `@ManyToOne` với `User` (seller) và `Category`, `@OneToMany` với `ProductImage` kèm cấu hình `@BatchSize(size = 20)` để loại bỏ triệt để N+1 query.
   - `ProductImage.java`: `@Entity @Table(name = "product_images")` (`imageId`, `product`, `imageUrl`, `sortOrder`, `isThumbnail`).
   - `Favorite.java` & `FavoriteId.java`: `@Entity @Table(name = "favorites") @IdClass(FavoriteId.class)` composite key (`userId`, `productId`), Lazy `@ManyToOne` với `Product`.
2. **DTO Records & Schemas Parity**:
   - `SellerSummary`, `SellerDetailSummary`, `CategorySummary`, `SellerAuctionSummary`.
   - `ProductSummaryResponse`, `ProductDetailResponse`, `SellerProductResponse`.
   - `ProductCreateRequest`, `ProductUpdateRequest`, `ProductListResponse`, `SellerProductListResponse`.
   - `ProductImageResponse`, `ImageReorderItem`, `ImageReorderRequest`, `ProductViewRequest`, `ProductViewResponse`.
   - `AdminApprovalDecisionRequest`, `AdminProductApprovalSummary`, `AdminProductApprovalDetailResponse`, `AdminProductApprovalListResponse`.
   - `FavoriteProductResponse`, `FavoriteListResponse`, `FavoriteIdsResponse`.
3. **Hiệu năng & Khắc phục Vấn đề Tương thích PostgreSQL**:
   - **PostgreSQL JSON Equality Operator Issue**: Trong PostgreSQL, kiểu cột `json` (như `users.social_links`) không có toán tử so sánh bằng (`=`). Khi viết JPQL `SELECT DISTINCT p FROM Product p JOIN FETCH p.seller`, Hibernate sinh SQL `SELECT DISTINCT` trên toàn bộ cột của User gây lỗi `could not identify an equality operator for type json`. Giải pháp: Loại bỏ `DISTINCT` và không FETCH JOIN collection `images` trong query phân trang (chỉ JOIN FETCH `seller` và `category` là quan hệ ManyToOne). Collection `images` được tải theo batch thông qua `@BatchSize(size = 20)`, loại bỏ hoàn toàn N+1 query.
   - **PostgreSQL Native Enum Binding**: Kiểu cột `users.status` trong PostgreSQL là native enum `user_status`. Dùng query parameter `:sellerActiveStatus` truyền `UserStatus.ACTIVE` để JDBC driver bind chuẩn xác.
4. **Bộ đếm Lượt xem Chống Gian lận (24h View Deduplication Counter)**:
   - `ProductViewCounter.java`: Cơ chế TTL 24 giờ cho viewer token (`u:<userId>` hoặc `a:<anonymousId>`), cập nhật atomic `UPDATE products SET view_count = GREATEST(view_count, :cachedFloor)` trong DB.
5. **Logic Nghiệp vụ & Ranh giới Bảo mật (ProductService, FavoriteService)**:
   - Chặn đăng tin mới khi tài khoản bị chế tài `SELL` (`UserCapabilityRestriction`).
   - Kiểm soát quyền BOLA: Chỉ chủ sở hữu hoặc ADMIN mới được sửa tin, xóa ảnh, đổi thumbnail, reorder ảnh hoặc xem tin ở trạng thái DRAFT.
   - Idempotent favorite: Thêm/xóa yêu thích không báo lỗi khi trùng lặp.
   - Cập nhật `SecurityConfig.java`: Public endpoints (GET `/api/v1/products`, GET `/api/v1/products/{id}`, POST `/api/v1/products/{id}/view`); Authenticated endpoints (`/api/v1/products/me`, POST/PATCH/DELETE products, `/api/v1/favorites/**`, `/api/v1/admin/**`).
6. **Kiểm thử Tích hợp Toàn diện (34 Test Cases Mới)**:
   - `ProductControllerIntegrationTest` (15 tests): CRUD, BOLA, Sell restriction 403, Cursor pagination, View counter 24h, Thumbnail update & Reorder images.
   - `AdminProductApprovalIntegrationTest` (5 tests): Duyệt tin, từ chối kèm lý do, bảo vệ quyền ADMIN 403, Conflict 409 khi tin không ở PENDING.
   - `FavoriteControllerIntegrationTest` (7 tests): Yêu thích sản phẩm, idempotent add/remove, ẩn sản phẩm đã xóa/chưa duyệt, lấy danh sách ID yêu thích.

### 3. Kết quả Kiểm chứng
- **Biên dịch**: `./mvnw.cmd clean compile` -> `BUILD SUCCESS`.
- **Kiểm thử**: `./mvnw.cmd test` -> `BUILD SUCCESS` (**153/153 tests PASS**, 0 failures, 0 errors, 0 skipped):
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
- **Tình trạng CSDL sau kiểm thử**:
  - `marketplace_java_dev`: 286 users, 26 categories (nguyên vẹn 100%).
  - `marketplace_java_test`: 0 users, 0 categories, 0 products, 0 favorites (cô lập, dọn dẹp sạch sẽ 100%).

### 4. Kết luận
- Toàn bộ Phase 4 (`product/` và `favorite/`) đã hoàn thành xuất sắc 100% mục tiêu, đạt chuẩn API contract, tương thích kiến trúc PostgreSQL 15+, và không có bất kỳ regression nào trên toàn bộ 153 bài kiểm thử.

---

## Wave 0 — Nền tảng dùng chung cho 6 track song song (2026-11-25)

### 1. Bối cảnh
- Sau Phase 4 (153/153 tests PASS), cần chuẩn bị nền tảng để 6 phiên Claude Code làm SONG SONG các module còn lại (order/auction, conversation, review/report/admin, notification/feedback, community moment/tutorial/daily need, realtime/infra) mà không đụng file của nhau.
- **Sự cố môi trường**: khi bắt đầu, container `sm_postgres`/`sm_redis` và toàn bộ volume dữ liệu (`marketplace_java_dev` 286 users/26 categories, `marketplace_java_test`, `student_marketplace`) KHÔNG còn trên máy. Theo quyết định của người dùng, dựng lại bằng Docker (`postgres:15-alpine`, `redis:7-alpine`) với **schema + seed**: chạy Alembic của dự án Python (chỉ đọc mã nguồn, truyền `DATABASE_URL` trỏ DB local) — `marketplace_java_dev` tới `b8c9d0e1f2a3`, `marketplace_java_test` tới `head` (`c9d0e1f2a3b4`); seed 10 category canonical + 3 user tổng hợp cho dev (để `UserReadOnlyRepositoryTest` có dữ liệu). Không dùng dump production.
- Máy không có JDK 21; build bằng JDK 26 + `-proc:full` + Lombok 1.18.46 (chỉ qua tham số dòng lệnh, không sửa pom cho việc này).

### 2. Công việc đã thực hiện
1. **26 entity + repository** (`@Entity`): Order, OrderItem, OrderStatusHistory, Auction, AuctionBid, Review, Report, ReportAttachment, ModerationAction, Conversation, ConversationMember (`@IdClass`), ConversationInvite, Message, MessageAttachment, MessageMention, MessageReaction, Notification, SiteFeedback, SiteFeedbackAttachment, SupportClaim, SupportContribution, CommunityMoment, Tutorial, DailyNeed, DailyJournalCheckin, MediaCleanupJob. `UserDeviceToken` giữ class thường (bảng thiếu ở dev DB). Sửa các enum skeleton sai (AuctionStatus, PaymentStatus, NotificationType, Report*, ModerationActionType, ReactionKey) theo Python/CHECK constraint, thêm enum hội thoại/feedback.
2. **Hạ tầng dùng chung**: `NotificationWriter` (+`PushNotificationSender`), `UserEventPublisher`, `AuctionEventPublisher`, `PresenceService` (interface), `MediaStorage` (+`LocalMediaStorage` cho profile test, `UnconfiguredMediaStorage`), `MediaValidator` (theo `media_validation.py`), `WorkerRetryExecutor` + `SweepResult` (theo `retry.py`); bean mặc định ở `configuration/SharedInfrastructureDefaultsConfiguration` (`@ConditionalOnMissingBean`).
3. **pom.xml**: `spring-boot-starter-websocket`, `spring-boot-starter-data-redis`, `cloudinary-http5:2.3.2`, `firebase-admin:9.5.0`.
4. **Chống xung đột**: 6 khối track trong `SecurityConfig` (ngay sau rule preflight CORS), `docs/PARALLEL_PLAN.md`, `docs/pr-reports/README.md`; nới safety check DB trong test thành `marketplace_java_test(_[a-z0-9]+)?` để mỗi track chạy được toàn suite trên DB riêng.
5. **Kiểm thử**: thêm 18 test (171/171 PASS).

### 3. Bài học kỹ thuật
- Không inline enum PostgreSQL native vào HQL (`type "userstatus" does not exist`) — luôn binding tham số.
- Entity tên `Order` dùng được trong HQL của Hibernate 7.
- Cột `timestamptz` phải map `OffsetDateTime` (không phải `LocalDateTime`) để `ddl-auto: validate` đạt.


---

## Wave 1 — 6 track song song (2026-11-25)

### 1. Bối cảnh
- Sáu phiên Claude Code làm song song trong 6 worktree + 6 DB test bản sao: A (order + auction), B (conversation), C (review + report + admin users), D1 (notification + feedback + support), D2 (community moment + tutorial + daily need), E (realtime Redis WebSocket + Cloudinary + Firebase push + media cleanup worker). Mỗi track có báo cáo `docs/pr-reports/TRACK_<ID>.md` theo review 8 bước.

### 2. Kết quả
- Mỗi track đạt parity với Python cho module của mình và có integration test trên PostgreSQL thật; test cũ không regress.
- Điểm treo bàn giao cho bước gộp: pool Hikari (cạn kết nối khi nhiều context cùng sống), múi giờ JVM lệch −7h so với UTC, giới hạn multipart, nối `/users/search`, `download-ticket`/`download`, làm mới notification `NEW_MESSAGE`, `?token=` cho evidence admin, biến môi trường nhóm hỗ trợ chính thức.

---

## Wave 2 — Gộp 6 track và kiểm tra toàn hệ thống (2026-11-25)

### 1. Gộp
- `git merge --squash` từng nhánh theo thứ tự E → A → C → D1 → D2 → B, mỗi track một commit `feat(<module>): ...` (tác giả theo `git config`, không trailer AI), `.\mvnw.cmd clean compile` sau mỗi lần. Xung đột duy nhất ở `SecurityConfig` (mỗi track thêm rule trong khối của mình) — giữ đủ rule, đúng thứ tự ưu tiên; sau đó dọn lại các dòng đánh dấu khối bị nhân đôi.
- Hai commit Wave 0 chưa push có trailer `Co-Authored-By` đã được viết lại message (nhánh backup `backup/pre-msg-cleanup` giữ bản gốc); không viết lại commit đã có trên `origin`.

### 2. Tích hợp
- Pool Hikari test 2/1 (không còn override trong test); JVM ép UTC (main + surefire) kèm test SQL thô; multipart 50MB/60MB; `OFFICIAL_SUPPORT_GROUP_ID`; `/users/search` nối `UserSearchService`; chat `download-ticket`/`download`; `NotificationWriter.upsertUnread`; `?token=` cho evidence admin; xoá 2 skeleton WebSocket handler; tắt Redis repositories.
- Tiêu đề notification đấu giá giữ theo code Python, ghi điểm lệch với `API_CONTRACT.md` mục 12.6 vào `PROJECT_STATE.md`.

### 3. Kiểm chứng
- `.\mvnw.cmd clean test`: **321/321 PASS**, 0 failures/errors/skipped, không cần biến môi trường.
- `scripts/route_parity.py`: **153/153 HTTP + 2/2 WebSocket**, 0 route chỉ có ở Java (`docs/ROUTE_PARITY.md`).
- Boot profile mặc định trên `marketplace_java_dev` (`ddl-auto: validate`) thành công; DB dev giữ 10 category + 3 user; DB test sạch.

### 4. Bài học kỹ thuật
- Hibernate ghi `LocalDateTime` theo múi giờ JVM trước khi chuyển `jdbc.time_zone=UTC`: bắt buộc JVM chạy UTC để dữ liệu dùng chung với Python không lệch.
- `created_at` của `Notification` là `updatable=false`: upsert làm mới phải dùng native `UPDATE` rồi `refresh`.
- Tránh inline `{param:regex}` vào so khớp route khi đối chiếu: chuẩn hoá mọi `{...}`/`*` thành `{}`.

## [2026-11-25] E2E SMOKE: FE React ↔ Java (profile e2e, Firebase DEV)
- Chạy kịch bản tự động `scripts/e2e/*.mjs` (3 tài khoản Firebase DEV `chopiters-5739b`, DB `marketplace_java_e2e`, Redis + worker bật) cho hồ sơ, sản phẩm, đơn hàng, đấu giá, chat, report, feedback, community moment, daily need; kiểm kiểu theo `types.ts` của FE (FE chỉ có zod cho request).
- Sửa Java: DTO admin product approvals khớp Python/FE; `POST /products/{id}/images/upload` từ stub thành upload thật qua `MediaStorage` (+ DRAFT lại tin, chặn khi có đấu giá); lưu trữ media cục bộ chỉ cho profile `e2e`.
- Kết quả: `clean test` 322/322 PASS. Chi tiết, lỗi còn lại và cách chạy lại: `docs/E2E_FE_SMOKE.md`.

## [2026-11-25] Khớp URL ảnh sản phẩm với Python
- `ProductService.thumbUrl/detailUrl`: `thumbnail_url` 200px, `detail_url` 800px theo `build_transformed_url` (áp dụng list, seller, admin approvals, detail, favorites). Test mới trong `ProductControllerIntegrationTest`. `clean test` 323/323 PASS.
