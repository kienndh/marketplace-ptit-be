# BÁO CÁO TRACK A — ORDER + AUCTION

Nhánh: `track/A-order-auction` (worktree `marketplace-ptit-be-A`). Báo cáo theo 8 bước của skill `marketplace-java-review`.

## Bước 1 — Ranh giới workspace & git
- Chỉ sửa trong `order/`, `auction/`, `worker/OrderAutoCancelWorker.java`, khối `// >>> TRACK A` của `SecurityConfig.java`, và test mới (`src/test/.../order`, `.../auction`).
- Không sửa: entity, enum, repository cơ bản, interface Wave 0, `pom.xml`, `application*.yml`, `PROJECT_STATE.md`, `PROJECT_HISTORY.md`, `user/ product/ favorite/ category/`. Không đụng `student-marketplace-be/`, `student-marketplace-fe/`.
- `OrderWebSocketHandler`, `AuctionWebSocketHandler` giữ nguyên (Track E).
- File mới: `PostCommit`, `CapabilityRestrictedException/Advice`, `DateTimes`, `OrderMapper`, `OrderQueryRepository`, `AuctionQueryRepository`, `AuctionLifecycleService`, `AuctionSweeper`, `AuctionNames`.
- Chi tiết: xem `git diff --stat main..HEAD` sau commit.

## Bước 2 — Build
`.\mvnw.cmd clean test` (JDK 26 + `-proc:full` + `-Dlombok.version=1.18.46`): BUILD SUCCESS, 0 lỗi biên dịch.

## Bước 3 — Schema
Không thêm migration; `ddl-auto: validate` giữ nguyên. Mọi tham số enum dùng `setParameter` (không inline enum native vào HQL).

## Bước 4 — Mapping/Entity
Dùng nguyên entity Wave 0. Không có sửa entity.

## Bước 5 — Kiến trúc & lỗi
Controller → Service → Repository (EntityManager) → Entity. Không trả entity ra API. Lỗi qua `ApiException`/`ErrorHandler` (`{"detail": "..."}`); riêng `BUY_RESTRICTED`/`SELL_RESTRICTED` trả `{"detail":{"code","message"}}` như Python (advice mới trong package `order`).
- Chống oversell: khoá bi quan `FOR UPDATE` trên `products`/`orders`; thứ tự khoá auction → product, order → product.
- Đặt giá: khoá hàng `auctions`; realtime/push/notification chỉ chạy sau commit (best-effort).
- Worker: mỗi bản ghi một transaction, bọc `WorkerRetryExecutor`; scheduler MẶC ĐỊNH TẮT (`marketplace.worker.order-auction.enabled=true` để bật).

## Bước 6 — Parity với API_CONTRACT
| Endpoint | Trạng thái |
|---|---|
| POST `/orders`, GET `/orders/me/buying`, `/me/selling`, GET `/orders/{id}` | Đạt |
| POST `/orders/{id}/confirm`, PATCH `/{id}/transaction-terms`, POST `/receipt`, `/confirm-payment`, `/deliver`, `/complete`, `/cancel` | Đạt |
| POST `/auctions`, `/{id}/cancel`, `/{id}/bids`, `/{id}/checkout`, `/products/{pid}/convert-fixed-price` | Đạt |
| GET `/auctions/{id}`, `/by-product/{pid}`, `/{id}/bids`, `/me/won` | Đạt (3 endpoint đầu public, `/me/won` cần đăng nhập) |
| WebSocket `/ws/me`, `/ws/auctions/{id}` | Ngoài phạm vi (Track E) |

Khác biệt còn tồn đọng:
1. Thông báo đấu giá: theo CODE Python ("Có lượt đấu giá mới", "Bạn đã bị vượt giá", "Bạn đã thắng đấu giá", "Đấu giá đã kết thúc"), khác tiêu đề trong mục 12.6 của `API_CONTRACT.md`. Cần điều phối chọn nguồn chuẩn.
2. `GET /orders/{id}`: chỉ buyer/seller (như code Python), contract nói thêm ADMIN.
3. Biên nhận: Content-Type sai → 400 (như Python); nội dung không phải ảnh thật (magic bytes) → 422 từ `MediaValidator`. Storage chưa cấu hình → 503, upload lỗi → 502 (Python không xử lý rõ).
4. Cursor lịch sử bid hỏng → 400 (Python để lỗi 500). Cursor đơn hàng hỏng bị bỏ qua như Python.
5. Lỗi cross-field của `CreateOrderRequest` trả `{"detail": "..."}` 422 thay vì mảng lỗi pydantic.
6. Không có anti-snipe (Python cũng không có).
7. GET đấu giá công khai kèm token sai: filter OAuth2 trả 401 (Python coi như anonymous).
8. Mã đơn: kiểm tra trùng trước khi chèn (không dùng savepoint retry như Python); trùng cực hiếm vẫn bị unique `ux_orders_order_code` chặn.

## Bước 7 — Test (PostgreSQL thật, `marketplace_java_test_a`)
- Test mới: `OrderLifecycleIntegrationTest` (18), `AuctionIntegrationTest` (12). Gồm: vòng đời đơn, huỷ + hoàn kho, tranh chấp 8 thread mua món cuối (đúng 1 đơn), 8 thread đặt cùng mức giá (đúng 1 bid), chuỗi bid đồng thời luôn cách nhau ≥ bid_step, auto-cancel worker, close/winner-timeout sweep, checkout người thắng (1 đơn/phiên), BOLA (404/403), 401 khi thiếu token, `BUY_RESTRICTED`.
- **Toàn bộ suite: 201 test, 0 failure, 0 error, BUILD SUCCESS.** Sau test các bảng liên quan = 0 dòng (đã kiểm tra bằng psql).
- Lưu ý khi chạy: Postgres dùng chung `max_connections=100`; Track B đang giữ 40–80 kết nối nên lần chạy mặc định bị `too many clients`. Đã chạy lại với `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2` và hai test của Track A đặt pool 4.

## Bước 8 — Rủi ro / việc cho người điều phối
1. **Múi giờ**: JVM máy dev là `Asia/Saigon` + `hibernate.jdbc.time_zone=UTC` ⇒ Hibernate ghi `LocalDateTime.now(UTC)` lệch −7h xuống DB (đọc lại bằng Hibernate vẫn nhất quán, nhưng SQL thô/Python dùng chung DB sẽ lệch). Nên chạy JVM với `-Duser.timezone=UTC` hoặc xử lý ở cấp ứng dụng (ngoài phạm vi Track A). Test Track A chỉnh thời gian qua JPA nên không phụ thuộc điều này.
2. **Multipart**: mặc định Spring giới hạn 1MB/file, Python cho biên nhận tới 5MB — cần đặt `spring.servlet.multipart.max-file-size` (≥5MB) trong `application.yml` (ngoài phạm vi).
3. Bean thật `UserEventPublisher`/`AuctionEventPublisher`/`MediaStorage` cần Track E; hiện dùng NoOp/Local.
4. Cần `@EnableScheduling` đã nằm sẵn trong cấu hình điều kiện của `OrderAutoCancelWorker`; nếu Track E cũng bật scheduling thì worker vẫn được chặn bởi cờ `marketplace.worker.order-auction.enabled`.
5. Kết nối DB dùng chung giữa các track chạy song song dễ cạn (xem Bước 7).
