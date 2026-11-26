# Chợ PTITers — Backend Java (marketplace-ptit-be)

Backend Java 21 / Spring Boot 4 / PostgreSQL 15 / Redis 7, viết lại từ backend Python FastAPI để đạt parity về API và schema.
Repo Python (chuẩn hành vi, chỉ để đối chiếu) lấy từ GitHub: <URL repo Python>.

## Mục lục
1. [Cấu trúc workspace](#1-cấu-trúc-workspace)
2. [Yêu cầu](#2-yêu-cầu)
3. [Dựng môi trường trên máy mới](#3-dựng-môi-trường-trên-máy-mới)
4. [Chạy app](#4-chạy-app)
5. [Chạy test](#5-chạy-test)
6. [Chạy FE với backend Java](#6-chạy-fe-với-backend-java)
7. [Khôi phục backup production và xuất CSV](#7-khôi-phục-backup-production-và-xuất-csv)
8. [Đối chiếu route với Python](#8-đối-chiếu-route-với-python)
9. [Quy tắc git](#9-quy-tắc-git)
10. [Sự cố thường gặp](#10-sự-cố-thường-gặp)

## 1. Cấu trúc workspace
```text
choptiters_java/
├── marketplace-ptit-be/     # repo này (nhánh main)
├── student-marketplace-fe/  # FE React/Vite, chỉ đọc (cấu hình qua biến môi trường)
├── _backups/                # (tùy chọn) dump + CSV production, NGOÀI repo git
└── AGENTS.md                # quy tắc làm việc cho AI/kỹ sư
```
Trong repo này: `src/` (code), `db/schema/` + `db/seed/` (SQL dựng DB độc lập), `compose.yaml` (Postgres + Redis),
`scripts/` (e2e, route parity), `docs/` (PROJECT_STATE, PROJECT_HISTORY, PARALLEL_PLAN, E2E_FE_SMOKE...).

## 2. Yêu cầu
- **JDK**: khuyến nghị JDK 21. Máy dev hiện chỉ có JDK 17 (mặc định) và JDK 26; với JDK 26 cần cờ build ở mục 5.
- Docker (Docker Desktop), Node.js (cho FE và `scripts/e2e`), Python 3 (cho `scripts/route_parity.py`).
- Maven dùng wrapper `.\mvnw.cmd` (không cần cài Maven).

## 3. Dựng môi trường trên máy mới
> ⚠️ Chỉ chạy trên máy **chưa có** container `sm_postgres`/`sm_redis`. Máy đã có sẵn thì KHÔNG chạy `docker compose up/down`
> và không xóa volume (sẽ mất DB).

```powershell
docker compose up -d
```
Mặc định: user `postgres`, mật khẩu `dev_password`, DB `marketplace_java_dev`, Postgres `localhost:5432`, Redis `localhost:6379`
(đổi bằng `POSTGRES_USER`/`POSTGRES_PASSWORD`/`POSTGRES_DB`).

Tạo thêm DB test/e2e rồi nạp schema + seed vào từng DB (Flyway đang tắt; schema do `db/schema/*.sql` cung cấp):
```powershell
docker exec sm_postgres psql -U postgres -c "CREATE DATABASE marketplace_java_test"
docker exec sm_postgres psql -U postgres -c "CREATE DATABASE marketplace_java_e2e"
docker cp .\db sm_postgres:/tmp/db
foreach ($d in "marketplace_java_dev","marketplace_java_test","marketplace_java_e2e") {
  docker exec sm_postgres psql -U postgres -v ON_ERROR_STOP=1 -q -d $d -f /tmp/db/schema/schema_c9d0e1f2a3b4.sql
  docker exec sm_postgres psql -U postgres -v ON_ERROR_STOP=1 -q -d $d -f /tmp/db/seed/categories.sql
}
```
Kết quả: 34 bảng nghiệp vụ + `alembic_version = c9d0e1f2a3b4` + 10 category. Test chỉ chấp nhận DB tên
`marketplace_java_test` hoặc `marketplace_java_test_<hậu tố>` (và cấm `marketplace_java_dev`).

## 4. Chạy app
Các biến môi trường chính (đều có mặc định cho local):

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/marketplace_java_dev` / `postgres` / `dev_password` | CSDL (profile mặc định) |
| `E2E_DB_URL` / `E2E_DB_USER` / `E2E_DB_PASSWORD` | `.../marketplace_java_e2e` / `postgres` / `dev_password` | CSDL profile `e2e` |
| `FIREBASE_PROJECT_ID` | `student-marketplace-ptit` | project Firebase kiểm token (profile mặc định) |
| `FIREBASE_DEV_PROJECT_ID` | `marketplace-ptit-dev` | project Firebase DEV (profile `e2e`) |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_URL` | `localhost` / `6379` / – / – | Redis |
| `SERVER_PORT` | `8080` | cổng HTTP |
| `MARKETPLACE_WORKER_ORDER_AUCTION_ENABLED` | `false` | bật worker hủy đơn/chốt đấu giá |
| `MARKETPLACE_WORKER_POLL_INTERVAL_MS` | `30000` | chu kỳ worker |
| `MEDIA_CLEANUP_ENABLED` | `false` | bật worker dọn media |
| `CLOUDINARY_URL` hoặc `CLOUDINARY_CLOUD_NAME`/`CLOUDINARY_API_KEY`/`CLOUDINARY_API_SECRET` | – | upload ảnh thật (tùy chọn; profile `e2e` lưu ảnh cục bộ) |
| `FIREBASE_CREDENTIALS_PATH` hoặc `FIREBASE_CREDENTIALS_JSON` | – | FCM push (tùy chọn) |
| `OFFICIAL_SUPPORT_GROUP_ID` | `0` | nhóm hỗ trợ chính thức (0 = route trả 503) |

Profile mặc định (JDK 21):
```powershell
.\mvnw.cmd spring-boot:run
```
Profile `e2e` (DB `marketplace_java_e2e`, ảnh lưu cục bộ, cổng 8081 vì 8080 hay bị chiếm; script dùng đường dẫn JDK 26 cố định
`C:\Users\Acer\.jdks\openjdk-26.0.2.1`, sửa trong `scripts/e2e/start-java.ps1` nếu máy bạn khác):
```powershell
.\scripts\e2e\start-java.ps1 -Log "$env:TEMP\java-e2e.log"
```
Script tự lấy mật khẩu Postgres từ biến môi trường của container `sm_postgres`; truyền `-FirebaseProjectId` / `-Port` nếu cần.

## 5. Chạy test
Test tích hợp chạy trên PostgreSQL thật (`marketplace_java_test`), mỗi test rollback.
```powershell
# JDK 21: chỉ cần
.\mvnw.cmd clean test

# JDK 26 (cần -proc:full cho Lombok):
$env:JAVA_HOME = "C:\Users\Acer\.jdks\openjdk-26.0.2.1"; $env:JDK_JAVAC_OPTIONS = "-proc:full"
.\mvnw.cmd "-Dmaven.compiler.fork=true" "-Dlombok.version=1.18.46" clean test
```
Dùng DB test khác: `$env:TEST_DB_URL = "jdbc:postgresql://localhost:5432/marketplace_java_test_x"` (tên phải khớp quy tắc ở mục 3).
Mốc hiện tại: 323 test pass. Chi tiết: `docs/PARALLEL_PLAN.md` mục 7.

## 6. Chạy FE với backend Java
FE ở `..\student-marketplace-fe` (không sửa code FE). Tạo `.env.local` trong thư mục FE với các **tên biến** sau (tự điền giá trị
từ Firebase DEV của bạn, không commit):
`VITE_FIREBASE_DEV_API_KEY`, `VITE_FIREBASE_DEV_AUTH_DOMAIN`, `VITE_FIREBASE_DEV_PROJECT_ID`, `VITE_FIREBASE_DEV_STORAGE_BUCKET`,
`VITE_FIREBASE_DEV_MESSAGING_SENDER_ID`, `VITE_FIREBASE_DEV_APP_ID`, `VITE_FIREBASE_DEV_MODE=1`.

Sau khi Java e2e chạy ở cổng 8081:
```powershell
Set-Location ..\student-marketplace-fe
$env:VITE_API_BASE_URL = "http://localhost:8081"; $env:VITE_WS_BASE_URL = "ws://localhost:8081"
npm install
npm run dev
```
FE chạy ở `http://localhost:3000`. Kịch bản tự động và kết quả: `docs/E2E_FE_SMOKE.md` (`scripts/e2e/*.mjs`).

## 7. Khôi phục backup production và xuất CSV
> ⚠️ Backup chứa **dữ liệu cá nhân thật**. Chỉ khôi phục vào DB riêng (không phải `marketplace_java_*`), không commit dump/CSV,
> không gửi đi nơi khác, không dán nội dung ra chat/log.

Dump do pg_dump 18 tạo ra nên `pg_restore` 15 trong `sm_postgres` không đọc được (`unsupported version`); dùng container 18:
```powershell
docker exec sm_postgres psql -U postgres -c "CREATE DATABASE student_marketplace_restore"
docker run --rm -e PGPASSWORD=dev_password -v "D:\choptiters_java\_backups:/backup:ro" postgres:18-alpine `
  pg_restore -h host.docker.internal -p 5432 -U postgres -d student_marketplace_restore --no-owner --no-privileges /backup/<ten_file>.dump
```
Một lỗi `unrecognized configuration parameter "transaction_timeout"` là bình thường (bỏ qua). Xuất CSV một bảng (UTF-8 có BOM cho Excel):
```powershell
docker exec sm_postgres psql -U postgres -d student_marketplace_restore -c "COPY (SELECT * FROM categories) TO '/tmp/categories.csv' WITH (FORMAT csv, HEADER, ENCODING 'UTF8')"
docker cp sm_postgres:/tmp/categories.csv .\categories.csv
```

## 8. Đối chiếu route với Python
```powershell
git clone <URL repo Python> $env:TEMP\student-marketplace-be
python scripts/route_parity.py $env:TEMP\student-marketplace-be --markdown docs/ROUTE_PARITY.md
```
Thoát mã 1 nếu còn route Python chưa có ở Java. Không sửa repo Python.

## 9. Quy tắc git
- Làm trên `main`, mỗi thay đổi một commit rõ ràng (`feat:`, `fix:`, `chore:`, `docs:`).
- KHÔNG commit `.env*`, file Firebase service account, mật khẩu/API key, dump `.dump`, CSV dữ liệu thật.
- Message commit không chứa dòng sinh bởi AI (`Co-Authored-By`, `Generated with ...`).
- Không kết nối production (Railway, Cloudflare R2, `choptiters.id.vn`); chỉ dùng Docker local.

## 10. Sự cố thường gặp
| Triệu chứng | Cách xử lý |
|---|---|
| `Port 8080 was already in use` | Đặt `SERVER_PORT=8081` (script e2e mặc định 8081) và trỏ FE về cổng đó |
| `FATAL: sorry, too many clients already` | Quá nhiều kết nối Postgres: tắt app/test đang chạy, hoặc giảm pool bằng `spring.datasource.hikari.maximum-pool-size` |
| Dữ liệu lệch giờ | App chạy UTC: đặt `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` (đã có trong script e2e); cột timestamp lưu UTC |
| `pg_restore: error: unsupported version (1.16) in file header` | Dump tạo bằng pg_dump mới hơn; dùng `postgres:18-alpine` như mục 7 |
| `release version 21 not supported` | Đang dùng JDK 17: dùng JDK 21 hoặc cờ JDK 26 ở mục 5 |
| Test báo `SAFETY VIOLATION` | `TEST_DB_URL` trỏ DB không khớp `marketplace_java_test(_hậu tố)` |
