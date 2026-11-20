# TRẠNG THÁI DỰ ÁN JAVA (PROJECT STATE) — Chợ PTITers Backend Migration
# Repository: marketplace-ptit-be

> **Vai trò tài liệu**: Bộ nhớ ngắn hạn (Short-Term Memory) cho kỹ sư phát triển và AI coding agent, ghi nhận trạng thái hiện tại, các quyết định kiến trúc đang hiệu lực, rủi ro kỹ thuật và kế hoạch hành động tiếp theo cho dự án di trú backend Java Spring Boot.

---

## 1. Current Phase (Giai đoạn hiện tại)
**Phase 1: Database Foundation & Schema Parity (Nền tảng CSDL & Đồng bộ Schema)** — *HOÀN THÀNH (FINALIZED)*.  
Chuẩn bị bước sang **Phase 2: API Contract Parity & Domain Feature Migration (Đồng bộ Hợp đồng API & Nghiệp vụ)**.

---

## 2. Current Task (Công việc hiện tại)
**Task PR01-F — Final Documentation Correction & Environment Verification**:
- [x] Đính chính thông tin môi trường Production thực tế:
  - Backend Python production hiện đang được triển khai trên **VPS do giảng viên cung cấp (Teacher-provided VPS)**, phục vụ domain `choptiters.id.vn`.
  - Thông tin triển khai trên **Railway** trước đây là thông tin lịch sử (historical deployment information) và được bảo lưu trong nhật ký dự án cho mục đích tham chiếu quá khứ.
  - Cơ chế deploy VPS thực tế và khả năng tự động redeploy khi push lên GitHub được xác định rõ là **UNVERIFIED** (tuyệt đối không tự suy đoán việc push Git sẽ tự động kích hoạt deploy lên VPS).
- [x] Thiết lập ràng buộc bất biến cho hợp đồng API Category (Task PR02-A):
  - Khẳng định trường định danh JSON trong Category response bắt buộc phải là `"category_id"` (thông qua `@JsonProperty("category_id")`), **tuyệt đối không đổi thành `"categoryId"`** để bảo đảm tính tương thích 100% với frontend và hợp đồng API chuẩn tại `API_CONTRACT.md`.
- [x] Rà soát và khóa toàn bộ danh mục 16 file sẵn sàng cho commit đầu tiên (Commit Readiness).
- [x] Không thay đổi mã nguồn Java, không sửa đổi tests, không can thiệp CSDL.

---

## 3. Status (Trạng thái)
- **Mã nguồn Entity & Repository**: `User.java`, `Category.java`, `UserAuthIdentity.java` đạt chuẩn tương thích Đọc & Ghi trên PostgreSQL 15.
- **Thư mục Migration Flyway**: Trống hoàn toàn (`src/main/resources/db/migration/`).
- **Thư mục Lưu trữ Migration MySQL**: `docs/legacy/mysql/` (bảo toàn lịch sử).
- **Tài liệu Chiến lược CSDL**: `docs/DATABASE_MIGRATION_STRATEGY.md` ĐÃ ĐÍNH CHÍNH & HOÀN TẤT.
- **Review Skill**: Đã đưa vào nội bộ repo tại `.agents/skills/marketplace-java-review/SKILL.md`.
- **Biên dịch (`.\mvnw.cmd clean compile`)**: `BUILD SUCCESS` (17 classes).
- **Kiểm thử (`.\mvnw.cmd test`)**: `BUILD SUCCESS` (**12/12 tests PASS**, 0 failures, 0 errors, 0 skipped).
- **Flyway**: Đang TẮT an toàn (`spring.flyway.enabled: false`). Di trú CSDL chưa hoàn thành (chờ schema handoff).


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
- [ ] **PR02-A: Category Module API Contract Parity (Controller, DTO, Service)**: `TODO`.
- [ ] **PR03: User & Auth Module Migration (Firebase Token Verification)**: `TODO`.

---

## 6. Recently Completed (Các công việc vừa hoàn thành)
- Đính chính thông tin môi trường Production hiện tại (VPS giảng viên, Railway là lịch sử; cơ chế redeploy là UNVERIFIED).
- Khẳng định quy chuẩn trường JSON Category ID cho Task PR02-A là `"category_id"`, không đổi thành `"categoryId"`.
- Hoàn thiện và đính chính toàn diện tài liệu chiến lược di trú CSDL `docs/DATABASE_MIGRATION_STRATEGY.md` (Flyway B-version baseline migration khả dụng trên bản Community Edition).
- Đóng gói quy trình review 8 bước độc lập cho repository Java tại `.agents/skills/marketplace-java-review/SKILL.md`.
- Cập nhật quy chuẩn kỹ thuật `AGENTS.md` bổ sung Mục 5 dẫn chiếu tới Review Skill nội bộ.
- Bổ sung cơ chế bảo vệ ranh giới CSDL `@BeforeEach` cho `UserReadOnlyRepositoryTest`.
- Xác minh thuật toán checksum CRC32 của Flyway và tính chất sequence độc lập ngoài transaction của PostgreSQL.
- Kiểm thử thực tế: `mvn clean compile` thành công (11.3s), `mvn test` 12/12 ca kiểm thử PASS sạch sẽ (20.4s) với cả môi trường dev và test cô lập.

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

---

## 8. Verification Baseline (Đường cơ sở kiểm thử)
- **Biên dịch**: `./mvnw.cmd clean compile` -> `BUILD SUCCESS` (17 source files compiled trong 11.3s).
- **Kiểm thử**: `./mvnw.cmd test` -> `BUILD SUCCESS` (12 tests PASS trong 20.4s, 0 failures, 0 errors, 0 skipped).
  - 1 Context Load test (`MarketplaceApplicationTests`)
  - 3 Read-only tests trên `marketplace_java_dev` (`UserReadOnlyRepositoryTest`)
  - 4 Write tests trên `marketplace_java_test` (`UserWriteIntegrationTest`)
  - 4 Write/Read tests trên `marketplace_java_test` (`CategoryWriteIntegrationTest`)
- **Tình trạng CSDL sau kiểm thử**:
  - `marketplace_java_test`: 0 users, 0 categories (rollback sạch).
  - `marketplace_java_dev`: 286 users, 26 categories (alembic: `b8c9d0e1f2a3`, nguyên vẹn).
  - `student_marketplace`: 286 users, 26 categories (alembic: `b8c9d0e1f2a3`, nguyên vẹn).

---

## 9. Known Risks & Technical Debt (Rủi ro & nợ kỹ thuật)
1. **Lệch revision Alembic & Cơ chế Deploy VPS Production (P1)**:
   - `marketplace_java_dev` ở `b8c9d0e1f2a3` (thiếu `user_device_tokens`).
   - CSDL và Backend Production hiện tại đang chạy trên VPS của giảng viên (trước đây là Railway), trạng thái revision Alembic thực tế và cơ chế tự động deploy đều là **UNVERIFIED**. Cần làm việc với quản trị viên/DevOps để xác minh revision và quy trình deploy thủ công/tự động trước khi cutover.
2. **Quan hệ 1:1 giữa User và UserAuthIdentity (P2)**:
   - `UserAuthIdentity` đang dùng `@ManyToOne`. Sẽ chuẩn hóa trong module User & Auth.

---

## 10. Next Steps (Các bước tiếp theo)
1. Xác nhận hoàn tất Phase 1 và sẵn sàng thực hiện commit đầu tiên cho milestone PR01 Foundation.
2. Chuyển sang **Task PR02-A: Category Module API Contract Parity**:
   - Rà soát `CategoryController.java`, `CategoryService.java`, `CategoryResponse.java`.
   - Đối chiếu hợp đồng API với `student-marketplace-be/docs/API_CONTRACT.md` (`GET /api/v1/categories`, `GET /api/v1/categories/{categoryId}`).
   - **Quy chuẩn bắt buộc về API Contract**: Trường định danh JSON trong Category response bắt buộc phải duy trì chính xác là `"category_id"` (thông qua `@JsonProperty("category_id")`), **tuyệt đối không được đổi thành camelCase `"categoryId"`**, để đảm bảo tính tương thích 100% với frontend và hợp đồng API chuẩn.
   - Kiểm tra định dạng response JSON và exception handling (404 khi category không tồn tại hoặc bị xóa mềm).
