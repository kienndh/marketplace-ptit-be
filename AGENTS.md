# AGENTS.MD — JAVA SPRING BOOT REPOSITORY RULES
# Repository: marketplace-ptit-be (Chợ PTITers Java Backend)

Tài liệu này định nghĩa các quy tắc kỹ thuật chi tiết dành riêng cho repository `marketplace-ptit-be`.

---

## 1. REPOSITORY ROLE & SCOPE

- **Vai trò**: Triển khai lại backend Chợ PTITers từ Python FastAPI sang Java Spring Boot với độ chính xác cao về tính năng (Functional Parity) và cấu trúc dữ liệu (Schema Parity).
- **Ngôn ngữ & Runtime**: Java 21 LTS.
- **Framework**: Spring Boot 4.x / Spring Framework 7.x.
- **Quản lý gói & Build**: Maven (`./mvnw`).
- **CSDL mục tiêu**: PostgreSQL 15+ (Local: `marketplace_java_dev` trên Docker `sm_postgres:5432`).
- **Bộ nhớ đệm & Realtime PubSub**: Redis 7+ (Local: `sm_redis:6379`).

---

## 2. PACKAGE STRUCTURE & ARCHITECTURE CONVENTIONS

Mã nguồn tuân theo kiến trúc phân tầng kết hợp chia theo miền nghiệp vụ (Modular Layered Architecture):

```text
com.ptiters.marketplace/
├── configuration/          # Cấu hình Spring (Security, CORS, ModelMapper, Redis, Jackson)
├── exception/              # ApiException, ErrorHandler (@RestControllerAdvice), ErrorResponse
├── category/               # Quản lý danh mục sản phẩm
├── user/                   # Người dùng, vai trò, trạng thái, danh tính Firebase
├── product/                # Tin đăng bán, ảnh sản phẩm, kiểm duyệt tin (tương lai)
├── order/                  # Đơn hàng, chi tiết đơn, lịch sử trạng thái (tương lai)
├── auction/                # Đấu giá, lượt đặt giá, pessimistic lock (tương lai)
├── conversation/           # Hội thoại chat, tin nhắn, thành viên (tương lai)
├── notification/           # Thông báo người dùng (tương lai)
└── report/                 # Báo cáo vi phạm & kiểm duyệt (tương lai)
```

### Quy tắc phân tầng:
1. **Entity**:
   - Khai báo trong package nghiệp vụ tương ứng.
   - Luôn sử dụng `@Entity`, `@Table(name = "...")`.
   - Khóa chính ánh xạ đúng kiểu: `Long` hoặc `Integer` với `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
   - Tuyệt đối không dùng `spring.jpa.hibernate.ddl-auto: update` hoặc `create`. Luôn cấu hình `validate`.
   - Các trường ENUM trong PostgreSQL cần cấu hình chuyển đổi tường minh hoặc `@Enumerated(EnumType.STRING)` tương thích với kiểu ENUM PostgreSQL.
2. **Repository**:
   - Kế thừa `JpaRepository<Entity, ID>`.
   - Các truy vấn đồng thời (Pessimistic Locking) trong đấu giá phải dùng `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
3. **Service**:
   - Chứa 100% nghiệp vụ cốt lõi, kiểm tra điều kiện tiên quyết, phân quyền và tính toán logic.
   - Mặc định thêm `@Transactional(readOnly = true)` ở cấp class; thêm `@Transactional` ở các method ghi/thay đổi trạng thái.
4. **Controller**:
   - Nhận HTTP request, validate tham số (`@Valid`, `@Validated`), gọi Service và trả về DTO.
   - Tuyệt đối không trả về JPA Entity trực tiếp ra API response. Phải chuyển đổi sang Response DTO (Record hoặc Class).
   - Đường dẫn API chuẩn: `/api/v1/<resource>`.
5. **Exception & Error Handling**:
   - Sử dụng `ApiException` kế thừa `RuntimeException`.
   - Bắt và format tập trung qua `ErrorHandler` (`@RestControllerAdvice`).
   - Format phản hồi lỗi: `{ "status": 4xx/5xx, "error": "...", "message": "...", "path": "...", "timestamp": "..." }`.

---

## 3. DATABASE MIGRATION RULES (FLYWAY)

- Toàn bộ thay đổi schema CSDL phải được quản lý qua Flyway trong `src/main/resources/db/migration/`.
- Tên file tuân theo chuẩn: `V<version>__<description>.sql` (hai dấu gạch dưới).
- Cú pháp SQL phải là **PostgreSQL 15+ native DDL**, không dùng cú pháp MySQL (`AUTO_INCREMENT`, `DATETIME`, `ON UPDATE CURRENT_TIMESTAMP` là các cú pháp không hợp lệ trên PostgreSQL).
- Schema chuẩn độc lập của repo: `db/schema/schema_c9d0e1f2a3b4.sql` (Alembic head `c9d0e1f2a3b4`) + `db/seed/categories.sql`. Khi cần đối chiếu đặc tả `docs/DB_v7.md` hoặc `docs/API_CONTRACT.md`, clone repo Python `student-marketplace-be` từ GitHub (<URL repo Python>; thư mục cục bộ này đã bị xóa khỏi workspace).
- Tuyệt đối không xóa hay sửa đổi các file migration đã được áp dụng trong quá khứ nếu đã có môi trường thực tế sử dụng.

---

## 4. TESTING & VERIFICATION CONVENTIONS

1. **Unit Testing**:
   - Kiểm thử các hàm tính toán, validation, mapping DTO với JUnit 5 và Mockito.
2. **Integration Testing**:
   - Các bài kiểm thử nghiệp vụ then chốt (đặt giá đồng thời, pessimistic lock, ràng buộc Foreign Key, Unique partial index) bắt buộc phải chạy trên PostgreSQL thật (`marketplace_java_dev`).
   - Nghiêm cấm giả lập kết quả (fake/mock) đối với các hành vi concurrency và transaction rollback.
3. **Maven Build Verification**:
   - Mọi thay đổi mã nguồn phải biên dịch sạch (`./mvnw clean compile` hoặc `mvn test-compile`) với 0 cảnh báo nghiêm trọng và 0 lỗi.

---

## 5. CODE REVIEW & GOVERNANCE WORKFLOW

- Mọi thay đổi mã nguồn trước khi tích hợp phải tuân thủ nghiêm ngặt Quy trình kiểm duyệt mã nguồn 8 bước tại `.agents/skills/marketplace-java-review/SKILL.md`.

