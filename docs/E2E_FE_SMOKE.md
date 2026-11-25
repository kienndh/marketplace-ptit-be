# E2E_FE_SMOKE — Chạy thử FE React với backend Java (local)

> Ngày chạy: 2026-11-25. Java: profile `e2e`, DB `marketplace_java_e2e`, Firebase DEV `chopiters-5739b`, Redis local `sm_redis`.
> FE (`../student-marketplace-fe`) **không bị sửa**, chỉ cấu hình bằng biến môi trường lúc chạy.

## 1. Kết quả tổng quát
Kịch bản tự động (`scripts/e2e/*.mjs`) gọi thẳng Java bằng ID token Firebase DEV của 3 tài khoản (seller/buyer/admin), kiểm status + kiểu dữ liệu theo `types.ts` của FE, kèm WebSocket thật. Tổng cộng **~156 bước, 1 bước lệch phía FE, 0 lỗi Java còn lại** (xem bảng).

> **Lưu ý về schema zod**: FE chỉ có zod cho **form request** (`products/users/orders/schemas.ts`), không có schema response. Vì vậy bước "parse response" được thay bằng bộ kiểm kiểu tự viết đọc trực tiếp `src/features/*/types.ts` của FE (`scripts/e2e/lib.mjs`, `checkType`): kiểm trường bắt buộc, kiểu nguyên thủy, union/literal, mảng, interface lồng nhau.

### Đối chiếu route (bước 2.1)
`node scripts/e2e/fe_routes.mjs`: FE gọi **149** cặp (method, path) khác nhau → **0** cặp Java thiếu/lệch so với `docs/ROUTE_PARITY.md`. (Chỉ quét được các lời gọi có path literal/template; đường gọi dựng động hiếm.)

## 2. Bảng từng luồng

| Luồng | Kết quả | Ghi chú |
|---|---|---|
| Public: categories, products list, banks, tutorials, community hero, feedback list/leaderboard/support, daily-needs | **Pass** | `01_public.mjs` 13/13 |
| Hồ sơ: `/users/me`, access-status, PATCH, bank account (PUT/GET), public profile, sold-products, search | **Pass** | |
| Sản phẩm: tạo → upload ảnh → gửi duyệt → admin duyệt → list/detail/view/update → yêu thích | **Đã sửa** | Lỗi #1 (DTO admin approvals), #2 (upload ảnh là stub) |
| Đơn hàng MEETUP: đặt → xác nhận → sửa điều khoản → biên nhận → xác nhận thanh toán → hoàn tất (+ từ chối `deliver` cho MEETUP) | **Pass** | |
| Đơn hàng SHIPPING: xác nhận → thanh toán → giao → hoàn tất; huỷ đơn | **Pass** | |
| Đánh giá (tạo/xem/sửa/danh sách seller) | **Pass** | |
| Notification (list, unread-count, read, read-all) + realtime `/ws/me` (`ORDER_CREATED`, `ORDER_UPDATED`, `NOTIFICATION_CREATED`, `MESSAGE_CREATED`, `MESSAGE_REACTIONS_UPDATED`, `MESSAGE_DELETED`, `GROUP_MEMBERSHIP_CHANGED`) | **Pass** | Redis bật; 2 tài khoản nhận sự kiện |
| Đấu giá: tạo phiên, 2 người đặt giá, giá thấp bị từ chối, sự kiện `/ws/auctions/{id}` (`BID_PLACED`, `AUCTION_ENDED`), worker chốt phiên, won list, checkout, notification `AUCTION_WON` | **Pass** | Cần bật worker (`MARKETPLACE_WORKER_ORDER_AUCTION_ENABLED=true`, đã có trong `start-java.ps1`); hết hạn được ép bằng SQL trên DB e2e |
| Chat trực tiếp: gửi text/ảnh/thẻ sản phẩm/reply, reaction, thu hồi, danh sách, đính kèm, presence, `read`, from-order | **Pass** | |
| Chat nhóm: tạo, mời, chấp nhận (`action: ACCEPT`), thành viên, gửi tin | **Pass** | |
| Chat tải đính kèm (ticket Redis 1 lần, 60s → `download`) | **Đã sửa (cấu hình e2e)** | Xem lỗi #3 |
| Report: tạo, upload evidence, admin list/detail/status/enforcement (`PRODUCT_HIDE`), report-history, admin xem evidence bằng `?token=` | **Pass** | |
| Feedback (tạo kèm ảnh, mine, admin list/detail, claims, support-contributions) | **Pass** | |
| Community moment (gửi ảnh, admin duyệt, hero) | **Pass** | |
| Daily need (PUT/GET today, summary, recent, public) | **Pass** | |
| Admin: tutorials, users, phân quyền (user thường gọi `/admin/*` → 403) | **Pass** | |
| `GET /conversations/{id}/attachments` — trường `conversation_id` | **Lệch phía FE** | Xem mục 4 |
| Giao diện (Browser pane): trang chủ nạp từ Java, 0 lỗi console, ảnh e2e hiển thị | **Pass (mức tải trang)** | Luồng đăng nhập Google & bấm UI do người dùng tự thử (checklist mục 6) |

## 3. Lỗi phía Java đã sửa (đều có test)
1. **Admin product approvals lệch hợp đồng Python/FE**: list thiếu `listing_type`, `category{}`, `seller{}`, `image_count` (Java trả `category_id/category_name/seller_public_id/seller_name` phẳng); detail thiếu `stock`, `reviewed_by`, `thumbnail_url` và thừa `available_stock`, `view_count`. Sửa `AdminProductApprovalSummary`, `AdminProductApprovalDetailResponse`, `ProductService.formatAdminApproval*`. Test: `AdminProductApprovalIntegrationTest` (thêm assert).
2. **`POST /products/{id}/images/upload` là stub**: không lưu file, sinh URL Cloudinary giả `res.cloudinary.com/synthetic/...`. Đã cài đúng hành vi Python: kiểm tra ảnh (`MediaValidator`), chặn khi sản phẩm đang có phiên đấu giá (400), tối đa 10 ảnh, lưu qua `MediaStorage.uploadProductImage`, `sort_order = max+1`, ảnh đầu là thumbnail, ADMIN cũng được tải, **sửa ảnh làm tin APPROVED/PENDING/REJECTED quay về DRAFT**, huỷ asset nếu lưu DB lỗi, `detail_url` qua `MediaStorage.detailImageUrl`. Thêm `AuctionRepository.existsByProductIdAndStatusIn`, `ProductImageRepository.findMaxSortOrder`. Test: `ProductControllerIntegrationTest.testUploadProductImage_StoresViaMediaStorageAndInvalidatesApproval`.
3. **Chỉ profile `e2e`** (không ảnh hưởng prod): `E2eLocalMediaConfiguration` lưu tệp vào `%TEMP%\marketplace-e2e-media` và phục vụ qua `/e2e-media/**` (tự đoán Content-Type theo chữ ký vì tệp không có đuôi); `LocalMediaStorage` nhận `baseUrl` tuỳ chọn; `ReportEvidenceStreamer.trustLocalPrefixForE2e` cho phép tải đính kèm chat/evidence từ origin cục bộ đó (chặn SSRF vẫn giữ nguyên cho mọi URL khác).

## 4. Lỗi/lệch còn lại (theo ưu tiên)
| Ưu tiên | Mô tả | Phía |
|---|---|---|
| P2 | `SharedAttachmentItem.conversation_id` được FE khai báo bắt buộc nhưng **Python cũng không trả** (`SharedAttachmentItemResponse` không có). Java khớp Python. FE nên bỏ/đổi thành optional hoặc backend (cả hai) thêm trường. | FE |
| P2 | Ảnh sản phẩm: Python trả `thumbnail_url` đã biến đổi 200px và `detail_url` 800px qua Cloudinary; Java `formatProduct*` vẫn trả URL gốc cho cả hai (chỉ ảnh upload mới của e2e/local nên chưa lộ). Cần chuyển sang `MediaStorage.MediaUrls.buildTransformedUrl` khi chạy Cloudinary thật. | Java (chưa sửa, cần test riêng) |
| P3 | Bộ kiểm kiểu chỉ kiểm 3 phần tử đầu mỗi mảng và chỉ khi danh sách có dữ liệu; vài endpoint chưa có dữ liệu mẫu (tutorials có video, support-claims/contributions, admin ban/unban, `from-order` nhóm, đổi chủ nhóm…) chưa chạy. | Phạm vi test |
| P3 | Cổng 8080 trên máy này bị `AgentService.exe` chiếm → Java chạy ở **8081**; `VITE_API_BASE_URL` phải trỏ 8081. | Môi trường |
| P3 | Rate limiting HTTP, Cloudinary/FCM thật, tỉ lệ video 9:16 vẫn là nợ cũ (xem `PROJECT_STATE.md` mục 9). | Java |

## 5. Khởi động lại môi trường
Yêu cầu: Docker `sm_postgres` + `sm_redis` đang chạy; DB `marketplace_java_e2e` (đã có, clone từ `marketplace_java_test` + 10 category); FE có `.env.local` với `VITE_FIREBASE_DEV_*` + `VITE_FIREBASE_DEV_MODE=1`.

Java (cổng 8081, nền, đã gồm JDK 26 + cờ, Redis, worker đấu giá, UTC):
```powershell
.\scripts\e2e\start-java.ps1 -Log "$env:TEMP\java-e2e.log"
```
(Script lấy mật khẩu Postgres từ biến môi trường của container, không ghi ra màn hình; project-id Firebase mặc định `chopiters-5739b`.)

FE (cổng 3000, trỏ về Java):
```powershell
Set-Location ..\student-marketplace-fe
$env:VITE_API_BASE_URL="http://localhost:8081"; $env:VITE_WS_BASE_URL="ws://localhost:8081"
npm run dev
```
Kịch bản tự động (từ `marketplace-ptit-be`; `E2E_CREDS` là file JSON ngoài repo lưu mật khẩu 3 tài khoản test, tự tạo ở lần chạy đầu; `E2E_STATE` là file JSON tạm):
```powershell
$env:E2E_CREDS="$env:TEMP\e2e-creds.json"; $env:E2E_STATE="$env:TEMP\e2e-state.json"
node scripts/e2e/accounts.mjs                    # tạo/đăng nhập 3 tài khoản Email/Password trong Firebase DEV
node scripts/e2e/fe_routes.mjs                   # FE gọi route nào Java thiếu
node scripts/e2e/01_public.mjs
node scripts/e2e/02_profile_product_order.mjs
node scripts/e2e/03_review_auction_chat_misc.mjs
node scripts/e2e/04_auction_close.mjs
```
Tài khoản test: `e2e.seller@`, `e2e.buyer@`, `e2e.admin@example.com` (admin được đặt `role='ADMIN'` trực tiếp trong `marketplace_java_e2e` bởi `ctx.mjs`).

## 6. Checklist bấm tay trên UI (≤ 15 bước, đăng nhập Google)
1. Mở `http://localhost:3000`, bấm **Tiếp tục với Google** (đăng nhập tài khoản Google của bạn → user mới được tạo trong `marketplace_java_e2e`).
2. Vào hồ sơ, sửa họ tên/SĐT, lưu; thêm tài khoản ngân hàng.
3. Đăng tin mới (có ảnh) → Gửi duyệt.
4. Đăng nhập tài khoản admin (cần đặt `role='ADMIN'` cho user Google của bạn: `UPDATE users SET role='ADMIN' WHERE email='...'` trên `marketplace_java_e2e`) → Duyệt tin tại trang quản trị.
5. Xem trang chủ/danh sách/chi tiết sản phẩm; bấm yêu thích.
6. (Dùng tài khoản thứ hai hoặc mở cửa sổ ẩn danh) Đặt mua → bên bán xác nhận → upload biên nhận → xác nhận thanh toán → hoàn tất → đánh giá.
7. Kiểm tra chuông thông báo cập nhật tức thời (WebSocket) khi bên kia thao tác.
8. Mở chat với người bán, gửi text + ảnh + reaction + thu hồi; thử tải tệp đính kèm.
9. Tạo nhóm chat, mời người khác.
10. Tạo phiên đấu giá (sản phẩm loại AUCTION), đặt giá từ hai tài khoản, xem cập nhật giá thời gian thực.
11. Báo cáo một tin → admin xử lý ở trang quản trị báo cáo.
12. Gửi góp ý, gửi "khoảnh khắc", đặt "nhu cầu hôm nay".
Khi bấm, theo dõi log Java: `Get-Content "$env:TEMP\java-e2e.log" -Wait`.
