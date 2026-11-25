# BÁO CÁO TRACK B — CONVERSATION (chat trực tiếp + nhóm)

Nhánh: `track/B-conversation` · Worktree: `marketplace-ptit-be-B` · DB test: `marketplace_java_test_b`

## 1. Phạm vi file (Bước 1 — workspace & git)
- **Chỉ** trong `src/main/java/.../conversation/` và `src/test/java/.../conversation/`, cộng một rule trong khối `// >>> TRACK B` của `configuration/SecurityConfig.java`.
- Không đụng entity, enum, repository cơ bản, interface Wave 0, `pom.xml`, `application*.yml`, `PROJECT_STATE/HISTORY`; không đụng `student-marketplace-be/-fe`.
- Thêm mới: `ConversationDtos`, `ConversationQueryRepository`, `ConversationViewBuilder`, `ConversationRealtime`, `ConversationSupport`, `ChatAttachmentValidator`, `GroupConversationService`, `ConversationIntegrationTest`.
- Viết lại: `ConversationController`, `ConversationService`. Xoá các DTO skeleton sai hợp đồng (thay bằng `ConversationDtos`, JSON snake_case giống Python).
- (Kết quả `git diff --stat main..HEAD` xem phần cuối lệnh `git show --stat HEAD` của commit.)

## 2. Build (Bước 2)
`clean test` với JDK 26 + `-proc:full` + `-Dlombok.version=1.18.46`: BUILD SUCCESS, 0 lỗi biên dịch.

## 3. Kiểm thử (Bước 7) — PostgreSQL thật
- Toàn bộ suite trên `marketplace_java_test_b`: **193 test, 0 fail, 0 error** (22 test mới của track).
- Sau test: `users, conversations, conversation_members, conversation_invites, messages, message_attachments, message_mentions, message_reactions, notifications, media_cleanup_jobs, orders, products` đều **0 dòng**.
- Lưu ý chạy suite đầy đủ: cần `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4` (biến môi trường, không sửa yml); mặc định các context cache của suite làm Postgres báo `too many clients already` (lỗi hạ tầng, không phải logic).
- Test đáng chú ý: 12 thread đua tạo direct (đúng 1 conversation + 2 member), 10 thread đua reaction (đúng 1 hàng), 8 thread đua mời (đúng 1 invite mở + 1 notification), direct idempotent hai chiều, nhóm + mời + duyệt `requires_approval`, phân quyền OWNER/ADMIN/MEMBER, chuyển quyền/rời/xoá mềm, phân trang cursor không trùng/sót, joined_at filter, mention (hợp lệ/sai offset/không phải thành viên/DIRECT), đã đọc + coalesce notification, đính kèm với `LocalMediaStorage`, chặn .exe đổi đuôi/zip giả, thu hồi → tombstone + dọn media + gỡ hàng `media_cleanup_jobs`, DB lỗi sau upload → rollback + `destroyAsset`, BOLA (404/403) và 401.

## 4. Parity endpoint (Bước 6)
Đạt: `POST /conversations`, `/direct`, `/from-order`, `/groups`; `GET /conversations` (cursor, type), `/owned-group-count`, `/{id}`, `/{id}/messages`, `/{id}/presence`, `/{id}/attachments`, `/{id}/attachments/summary`, `/invites`, `/official-support`; `POST /invites/{id}/respond`, `/official-support/join`, `/{id}/messages` (multipart + JSON), `/{id}/read`; `PUT/DELETE /{id}/messages/{mid}/reaction`; `DELETE /{id}/messages/{mid}`; nhóm: `PATCH/DELETE /groups/{id}`, `invites`, `pending-approvals` (GET/POST), `invite-candidates` (2 alias), `members` (GET/DELETE), `members/{uid}/role`, `me/nickname`, `transfer-ownership`, `leave`.

Khác biệt còn tồn đọng:
1. **Chưa làm** `download-ticket` / `download` (cần Redis + stream Cloudinary) — để Track E/điều phối.
2. **Chưa làm** rate limit chat và hạn mức video trượt (Redis); chỉ giữ giới hạn 1 video/tin.
3. Notification `NEW_MESSAGE`: Python làm mới bản chưa đọc (title/body/created_at); `NotificationWriter` chỉ tạo mới nên Java giữ đúng 1 bản chưa đọc cho mỗi (user, hội thoại), không làm mới.
4. Chat dùng `ChatAttachmentValidator` riêng (400, PDF/audio/Office/TXT, video 50MB) thay vì `MediaValidator` (dành cho bằng chứng, 422, video 30MB).
5. Python trả 500 ở vài chỗ, Java trả lỗi rõ ràng: danh sách thành viên nhóm không tồn tại (404), mời user không tồn tại (bỏ qua), mời user đang `PENDING_APPROVAL` (bỏ qua, tránh vi phạm unique index).
6. `POST /direct` luôn 200 (đúng code Python; contract nêu 201 khi tạo mới); người nhận không ACTIVE trả 400 (code Python) dù contract ghi 404.
7. Mark-read không cập nhật `messages.read_at` (Python cũng không).

## 5. Rủi ro / việc cho người điều phối
- Đặt `spring.servlet.multipart.max-file-size` / `max-request-size` (mặc định 1MB, tệp chat tới 50MB/60MB tổng) — track không được sửa yml.
- Đặt `marketplace.chat.official-support-group-id` (mặc định 0 → 503 như Python khi chưa cấu hình).
- Bean thật `UserEventPublisher`, `PresenceService`, `PushNotificationSender`, `MediaStorage` do Track E; track dùng mặc định no-op/local khi test.
- Hàng `media_cleanup_jobs` do track chèn/gỡ khi thu hồi (như Python); worker dọn là việc của Track E.
