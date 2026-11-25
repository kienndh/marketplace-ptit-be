# Báo cáo PR theo Track

Mỗi track song song ghi báo cáo cuối của mình vào **một file riêng** để merge không xung đột:

| Track | File báo cáo |
|---|---|
| A | `TRACK_A.md` |
| B | `TRACK_B.md` |
| C | `TRACK_C.md` |
| D1 | `TRACK_D1.md` |
| D2 | `TRACK_D2.md` |
| E | `TRACK_E.md` |

Mỗi báo cáo theo 8 bước của skill `marketplace-java-review` (`.agents/skills/marketplace-java-review/SKILL.md`), bằng tiếng Việt, gồm tối thiểu:

1. Phạm vi file đã thêm/sửa (chỉ trong package của track) và xác nhận không đụng file của Wave 0.
2. Kết quả `.\mvnw.cmd clean test` trên DB riêng của track (số test, PASS/FAIL) và trạng thái DB sau test (0 dòng).
   Trước khi viết báo cáo phải chạy TOÀN BỘ suite một lần trên DB riêng của track (đặt `TEST_DB_URL`) và ghi kết quả vào đây (xem `PARALLEL_PLAN.md` mục 9).
3. Các endpoint đã đạt parity với `API_CONTRACT.md` và các khác biệt còn tồn đọng.
4. Rủi ro / việc cần người điều phối xử lý (ví dụ nâng DB dev, bean thật cần Track E).

Các track KHÔNG sửa `docs/PROJECT_STATE.md` và `docs/PROJECT_HISTORY.md`; người điều phối tổng hợp sau khi merge.
Xem quy tắc chung tại `../PARALLEL_PLAN.md`.
