# CHIẾN LƯỢC DI TRÚ CƠ SỞ DỮ LIỆU FLYWAY (DATABASE MIGRATION STRATEGY)
# Dự án: Chợ PTITers — Backend Java Spring Boot
# Repository: marketplace-ptit-be

> **Tài liệu tham chiếu chuẩn (Bản hiệu chỉnh PR01-D2)**: Quy hoạch và kiến trúc tiếp quản cơ sở dữ liệu từ Python Alembic sang Java Flyway trên nền tảng PostgreSQL 15+, bảo đảm an toàn dữ liệu lịch sử, không tái tạo DDL thừa và tương thích 100% với phiên bản Flyway Community hiện có.

---

## 1. NGUYÊN TẮC QUẢN TRỊ BẤT BIẾN (GOVERNANCE INVARIANTS)

1. **Một CSDL Quan hệ Duy nhất (Single Relational Database)**:
   - Toàn bộ backend (Python production hiện tại và Java target) đều sử dụng chung schema PostgreSQL 15+ theo đặc tả chuẩn tại `student-marketplace-be/docs/DB_v7.md`.
2. **Tuyệt đối Không Tự động Sinh DDL (Zero Hibernate DDL)**:
   - `spring.jpa.hibernate.ddl-auto: validate` là quy chuẩn bắt buộc.
   - Hibernate chỉ đóng vai trò kiểm tra tính tương thích giữa Java Entity và CSDL, không bao giờ tự ý tạo/sửa bảng (`update`, `create`).
3. **Phân định Quyền Sở hữu Schema (Schema Ownership Transition)**:
   - Giai đoạn hiện tại (Phase 0/1): Alembic (Python) nắm toàn quyền quản lý schema và bảng `alembic_version`.
   - Giai đoạn chuyển giao (Cutover): Flyway (Java) tiếp quản quyền quản lý schema thông qua bảng `flyway_schema_history`.
   - **Tuyệt đối không chạy đồng thời cả 2 công cụ di trú tự động trên cùng một CSDL.**
4. **Không chạy script thô (Raw pg_dump Exclusion)**:
   - Không sử dụng file dump từ `pg_dump` làm Flyway migration vì dump chứa thông tin ownership, search_path, và extension privileges không tương thích môi trường sạch.

---

## 2. ĐỐI SOÁT HIỆN TRẠNG PHIÊN BẢN SCHEMA & ĐÍNH CHÍNH QUAN TRỌNG

### 2.1. Hiện trạng phiên bản:
| Khía cạnh | Phiên bản / Revision ID | Trạng thái thực tế | Đánh giá & Rủi ro |
|---|---|:---:|---|
| **A. Mã nguồn Python (Alembic HEAD)** | `c9d0e1f2a3b4` | Đã duyệt trong Python codebase | Bổ sung bảng `user_device_tokens` (FCM Web Push). Tổng cộng: 35 bảng (34 domain + `alembic_version`). |
| **B. CSDL Local Java Dev (`marketplace_java_dev`)** | `b8c9d0e1f2a3` | Snapshot thực tế đang nạp | Mới dừng ở migration `media_cleanup_jobs`. **Chưa có bảng `user_device_tokens`**. |
| **C. CSDL Production Live (VPS giảng viên, lịch sử: Railway)** | **UNVERIFIED** | Chưa thể xác minh trực tiếp | Đang chạy trên VPS do giảng viên cung cấp (trước đây là Railway). Cấm kết nối production. Cơ chế deploy và revision thực tế là UNVERIFIED. Cần xác minh thủ công bởi DevOps/quản trị viên trước khi cutover. |

### 2.2. Đính chính lỗi thiết kế từ Task PR01-D1:
* **Sai lầm ở đề xuất cũ:** Đề xuất tách thành `V1.0.0` (schema) + `V1.0.1` (seed 10 categories) + `V1.1.0` (device tokens).
* **Rủi ro nghiêm trọng:** Nếu CSDL có sẵn dữ liệu (`marketplace_java_dev` hoặc production) được baseline thủ công ở phiên bản `1.0.0`, Flyway sẽ nhận diện `V1.0.1` là migration đang chờ (pending) và **tự động chạy script nạp categories**. Thao tác này sẽ gây lỗi trùng khóa chính/slug hoặc nạp dữ liệu rác vào CSDL đã có sẵn dữ liệu!
* **Quy tắc sửa chữa:**
  1. **Tuyệt đối KHÔNG tạo migration dạng seed data riêng biệt có version `> baseline_version`**.
  2. Dữ liệu danh mục tham chiếu chuẩn tắc cho fresh database phải được tích hợp trọn gói ngay trong migration khởi tạo ban đầu, để khi baseline ở mốc đó trên existing DB, toàn bộ dữ liệu mẫu cũng được bỏ qua an toàn.
  3. Mọi migration trong tương lai sau mốc baseline chỉ được phép là thay đổi cấu trúc schema thực sự (`ALTER TABLE`, `CREATE TABLE` mới).

---

## 3. PHÂN TÍCH BA KHÁI NIỆM FLYWAY (MIGRATION CONCEPTS IN FLYWAY)

Để lựa chọn giải pháp chuẩn xác cho dự án, cần phân biệt rõ ràng 3 khái niệm và cơ chế thực thi của chúng:

### 1. Flyway Baseline Command (`flyway baseline` / `mvn flyway:baseline`)
* **Bản chất:** Là một lệnh quản trị hành chính thực thi một lần duy nhất lên một CSDL đã có sẵn dữ liệu.
* **Hành vi thực thi:**
  - Tạo bảng `flyway_schema_history` (nếu chưa có).
  - Chèn 1 bản ghi duy nhất đánh dấu CSDL đã ở phiên bản chỉ định (ví dụ: `1.0.0`, type `BASELINE`, state `SUCCESS`).
  - **Không thực thi bất kỳ file SQL nào**.
  - Mọi migration có version `<= baselineVersion` sẽ bị bỏ qua (ignored).
* **Khả dụng:** Được hỗ trợ trong **tất cả các phiên bản Flyway** (bao gồm Community, Teams, Enterprise).

### 2. Flyway B-version Baseline Migration (`B<version>__<description>.sql`)
* **Bản chất:** Là file script SQL baseline đặc biệt được đặt trong thư mục migration cùng với các file `V...` và `R...`.
* **Hành vi thực thi:**
  - **Trên CSDL trống hoàn toàn (Fresh DB):** Flyway nhận diện và thực thi script `B<version>` để thiết lập toàn bộ schema ban đầu lên thẳng phiên bản mốc chỉ định, bỏ qua việc phải chạy tuần tự các migration phiên bản cũ hơn (`V < version`).
  - **Trên CSDL đã có sẵn dữ liệu (Existing DB đã ở phiên bản đó):** Flyway tự động nhận diện schema đã tồn tại ở phiên bản mốc tương ứng và đánh dấu baseline mà không thực thi lại script SQL, tránh lỗi `relation already exists`.
* **Đính chính quan trọng về tính khả dụng (Factual Correction):**
  - **Thực thi script B-version được hỗ trợ trên TẤT CẢ các phiên bản Flyway** (kể cả Flyway Community Edition 12.4.0 đang dùng trong dự án).
  - Khả năng **tự động sinh script baseline (Automatic Baseline Generation)** thông qua công cụ so sánh schema mới là tính năng yêu cầu bản quyền thương mại (Flyway Teams/Enterprise). Còn việc kỹ sư tự soạn thảo thủ công và chạy script `B...` thì hoàn toàn hợp lệ trên Community Edition.
* **Định hướng dự án:** Dự án hoàn toàn có thể sử dụng file `B...` hoặc lệnh `flyway baseline` khi đến giai đoạn bàn giao CSDL chính thức. Trong giai đoạn hiện tại (Phase 1/2), ta chưa tạo script `B...` vì chưa đến thời điểm cutover và Flyway vẫn đang được tắt an toàn (`spring.flyway.enabled: false`).

### 3. Flyway V-version Migration (`V<version>__<description>.sql`)
* **Bản chất:** Migration có phiên bản thông thường, chạy tuần tự một chiều theo thứ tự phiên bản tăng dần.
* **Hành vi thực thi:**
  - Thực thi tuần tự từng file theo thứ tự phiên bản: `V1.0.0` -> `V1.1.0` -> `V1.2.0`.
  - Mỗi file chỉ chạy một lần duy nhất khi CSDL chưa có phiên bản đó.
  - Khi chạy xong, Flyway tính toán mã kiểm tra **CRC32** (lưu dưới dạng số nguyên 32-bit trong cột `flyway_schema_history.checksum`, *đính chính: không phải SHA-256*) để kiểm tra tính toàn vẹn (Immutability). Bất kỳ thay đổi nào vào file đã chạy sẽ làm sai lệch checksum và gây lỗi khi khởi động.
* **Khả dụng:** Tính năng cốt lõi cơ bản trong tất cả các phiên bản Flyway.

---

## 4. CHIẾN LƯỢC CHO HAI KỊCH BẢN (DUAL SCENARIO DESIGN)

Dựa trên cơ chế của Flyway 12.4.0, kiến trúc di trú chuẩn tắc được xác lập như sau:

```text
src/main/resources/db/migration/
└── (Chưa tạo script trong task này - dự kiến ở giai đoạn Schema Handoff)
    └── V1.0.0__initial_postgresql_schema.sql (hoặc B1.0.0__initial_baseline.sql)
```

### KỊCH BẢN A: CƠ SỞ DỮ LIỆU ĐÃ TỒN TẠI (EXISTING DATABASE)
*Áp dụng cho:* `marketplace_java_dev` cục bộ, database staging, hoặc CSDL production live đã có sẵn dữ liệu lịch sử từ Alembic.

1. **Xác minh Schema Handoff:**
   - Trước khi thực hiện cutover, xác minh chính xác CSDL đang ở revision Alembic nào:
     - Nếu ở `b8c9d0e1f2a3`: Cần quyết định nâng cấp lên `c9d0e1f2a3b4` bằng Alembic trước, hoặc chọn `b8c9d0e1f2a3` làm mốc bàn giao.
2. **Quy trình Thực hiện Baseline Thủ công (Manual Controlled Baseline):**
   - **CẤM** bật `spring.flyway.baseline-on-migrate: true` tự động trong cấu hình runtime.
   - Kỹ sư thực hiện lệnh baseline tường minh thông qua Maven plugin:
     ```bash
     mvn flyway:baseline -Dflyway.baselineVersion=1.0.0 -Dflyway.baselineDescription="Alembic handoff baseline"
     ```
   - Bảng `flyway_schema_history` được tạo, bản ghi version `1.0.0` được ghi nhận với trạng thái `SUCCESS`.
   - File `V1.0.0__initial_postgresql_schema.sql` sẽ **KHÔNG BAO GIỜ bị chạy lại** trên CSDL này, tránh hoàn toàn lỗi `relation already exists` và tránh duplicate categories.
3. **Các Migration Tương lai:**
   - Mọi thay đổi schema mới sau thời điểm bàn giao sẽ bắt đầu từ `V1.1.0__...` hoặc `V2.0.0__...` và tự động áp dụng.

---

### KỊCH BẢN B: CƠ SỞ DỮ LIỆU TRỐNG HOÀN TOÀN (FRESH EMPTY DATABASE)
*Áp dụng cho:* Môi trường CI/CD (GitHub Actions), Docker test cô lập (`marketplace_java_test`), hoặc máy phát triển mới.

1. **Quy trình Khởi tạo Tự động:**
   - CSDL trống không có bảng nào. Không chạy lệnh `baseline`.
   - Khi ứng dụng khởi động, Flyway quét thư mục migration và thực thi:
     - `V1.0.0__initial_postgresql_schema.sql`:
       - Tạo các kiểu Native ENUM (`user_status`, `payment_status_t`).
       - Tạo 33 hoặc 34 bảng nghiệp vụ (PostgreSQL 15+ native DDL: Identity, UUID `gen_random_uuid()`, JSON, Foreign Keys, Indexes bao gồm partial index `ux_categories_slug`).
       - Nạp 10 danh mục chuẩn tắc của trường PTIT.
   - Kết quả: CSDL trống được tái thiết lập thành một CSDL chuẩn có cấu trúc y hệt CSDL production mà không cần nạp dữ liệu người dùng cá nhân.

---

## 5. ĐÍNH CHÍNH THUẬT TOÁN CHECKSUM VÀ LƯU Ý KỸ THUẬT

1. **Mã Kiểm tra Checksum trong Flyway**:
   - Cột `checksum` trong bảng `flyway_schema_history` có kiểu dữ liệu là `integer` (32-bit signed integer).
   - Flyway tính toán checksum bằng thuật toán **CRC32** (`java.util.zip.CRC32`) trên nội dung chuẩn hóa của file SQL.
   - *Đính chính:* Tài liệu PR01-D1 trước đây ghi nhầm là SHA-256. SHA-256 sinh ra chuỗi 256-bit (64 ký tự hex) không thể lưu vào trường integer của Flyway.
2. **PostgreSQL Sequence trong Giao dịch Kiểm thử**:
   - Trong PostgreSQL, các sequence (ví dụ: `categories_category_id_seq`) hoạt động độc lập ngoài transaction boundary để tránh khóa cổ chai (bottleneck lock).
   - Khi một transaction bị ROLLBACK, các giá trị sequence đã được cấp phát bởi `nextval()` sẽ **KHÔNG bị rollback lại**. Đây là hành vi chuẩn mực của PostgreSQL và không phải là lỗi rò rỉ dữ liệu.

---

## 6. LỘ TRÌNH THỰC THI (ACTIONABLE ROADMAP)

- **PR01-D2 (Hiện tại)**: Hoàn thiện chiến lược CSDL, đính chính sai lệch, khóa an toàn Flyway (`enabled: false`), audit an toàn test.
- **Giai đoạn tiếp theo (API Migration)**: Tập trung chuyển đổi module đầu tiên (`Category API Contract Parity`) trên nền CSDL hiện có mà không chạm vào DDL.
- **Giai đoạn Bàn giao Schema (Schema Handoff Phase)**: Khi toàn bộ backend Java sẵn sàng thay thế Python:
  - Soạn thảo script `V1.0.0__initial_postgresql_schema.sql` hoàn chỉnh.
  - Xác nhận phiên bản Alembic cuối cùng trên production.
  - Thực hiện baseline trên production và kích hoạt Flyway `enabled: true`.
