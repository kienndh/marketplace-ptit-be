-- Seed 10 category (bảng categories). Chạy SAU db/schema/schema_c9d0e1f2a3b4.sql.
INSERT INTO public.categories VALUES (1, 'Giáo trình & Sách', 'giao-trinh-sach', false, '2026-11-25 00:48:17.20803');
INSERT INTO public.categories VALUES (2, 'Thiết bị điện tử khác', 'thiet-bi-dien-tu-khac', false, '2026-11-25 00:48:17.20803');
INSERT INTO public.categories VALUES (3, 'Máy tính & Phụ kiện', 'may-tinh-phu-kien', false, '2026-11-25 00:48:17.20803');
INSERT INTO public.categories VALUES (4, 'Điện thoại & Phụ kiện', 'dien-thoai-phu-kien', false, '2026-11-25 00:48:17.20803');
INSERT INTO public.categories VALUES (5, 'Nội thất & Đồ phòng trọ', 'noi-that-do-phong-tro', false, '2026-11-25 00:48:17.20803');
INSERT INTO public.categories VALUES (6, 'Xe đạp & Xe máy', 'xe-dap-xe-may', false, '2026-11-25 00:48:17.20803');
INSERT INTO public.categories VALUES (7, 'Thời trang & Phụ kiện', 'thoi-trang-phu-kien', false, '2026-11-25 00:48:17.20803');
INSERT INTO public.categories VALUES (8, 'Đồ gia dụng', 'do-gia-dung', false, '2026-11-25 00:48:17.20803');
INSERT INTO public.categories VALUES (9, 'Thể thao & Giải trí', 'the-thao-giai-tri', false, '2026-11-25 00:48:17.20803');
INSERT INTO public.categories VALUES (10, 'Khác', 'khac', false, '2026-11-25 00:48:17.20803');
SELECT pg_catalog.setval('public.categories_category_id_seq', 10, true);
