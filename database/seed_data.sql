-- ============================================================================
-- DỮ LIỆU MẪU KHỞI TẠO (SEED DATA) - HỆ THỐNG ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT
-- Mật khẩu mặc định toàn bộ tài khoản: 123456
-- Chuỗi Salted SHA-256 hash chuẩn: fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197
-- Tích hợp sẵn 10 sự cố mẫu thực tế & tin nhắn trao đổi 1-1 phục vụ demo ngay khi khởi chạy
-- ============================================================================

-- 1. DANH MỤC SỰ CỐ & ĐỊNH TUYẾN PHÒNG BAN CHUYÊN TRÁCH (12 DANH MỤC QUY MÔ ĐẠI HỌC)
INSERT OR IGNORE INTO incident_categories (category_code, category_name, required_specialty) VALUES
-- [KHỐI 1: PHÒNG KỸ THUẬT & QUẢN TRỊ THIẾT BỊ (TECHNICAL)]
('FACILITY', 'Hỏng thiết bị giảng đường (Máy chiếu, Âm thanh, Bàn ghế, Quạt)', 'TECHNICAL'),
('IT_NETWORK', 'Mạng WiFi & Phòng máy thực hành (Mất mạng, Lỗi switch, Server lab)', 'TECHNICAL'),
('ELECTRIC', 'Sự cố Điện kỹ thuật (Chập điện, Nhảy aptomat, Mất điện toà nhà)', 'TECHNICAL'),
('WATER', 'Cấp thoát nước & Vệ sinh (Vỡ ống nước, Mất nước, Tắc nghẽn cống)', 'TECHNICAL'),
('ELEVATOR', 'Sự cố Thang máy toà nhà cao tầng (Kẹt thang máy, Lỗi cửa tầng, Rung lắc)', 'TECHNICAL'),
('LAB_EQUIP', 'Thiết bị Phòng thí nghiệm & Đo lường (Hỏng tủ hút khói, Kính hiển vi, Lò nung)', 'TECHNICAL'),

-- [KHỐI 2: PHÒNG Y TẾ & CHĂM SÓC SỨC KHỎE (MEDICAL)]
('MEDICAL', 'Cấp cứu Y tế & Chấn thương (Ngất xỉu, Chấn thương thể thao, Co giật, Sốt cao)', 'MEDICAL'),
('FIRST_AID', 'Tai nạn Phòng thí nghiệm / Xưởng thực hành (Bỏng hóa chất, Đứt tay, Rách da)', 'MEDICAL'),
('FOOD_SAFETY', 'Nghi vấn Ngộ độc thực phẩm căng tin (Buồn nôn hàng loạt, Đau bụng dữ dội)', 'MEDICAL'),

-- [KHỐI 3: ĐỘI AN NINH TRẬT TỰ & BẢO VỆ (SECURITY)]
('SECURITY', 'An ninh Trật tự & Gây rối (Xô xát ẩu đả, Mất trật tự giảng đường/KTX)', 'SECURITY'),
('THEFT', 'Mất cắp tài sản & Đột nhập (Mất xe máy bãi xe, Mất laptop, Đột nhập đêm)', 'SECURITY'),
('FIRE_HAZARD', 'Cảnh báo Cháy nổ & Khí gas (Báo cháy reo, Khói bốc nhà ăn, Mùi rò rỉ gas)', 'SECURITY');

-- 2. TÀI KHOẢN NGƯỜI DÙNG TOÀN TRƯỜNG (50 TÀI KHOẢN CHUẨN THỰC TẾ)
INSERT OR IGNORE INTO users (user_id, username, password_hash, full_name, role, specialty, phone, is_online) VALUES
-- [KHỐI 1: BAN GIÁM HIỆU / ADMIN TRUNG TÂM (2 TÀI KHOẢN)]
(1, 'admin_bgh', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Thầy Nguyễn Văn An (Hiệu trưởng)', 'ADMIN', 'NONE', '0901000001', 0),
(2, 'admin_pht', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Lê Thị Mai Hương (Phó Hiệu trưởng)', 'ADMIN', 'NONE', '0901000002', 0),

-- [KHỐI 2: NGƯỜI BÁO CÁO - GIÁO VIÊN BỘ MÔN & CHỦ NHIỆM (12 TÀI KHOẢN)]
(3, 'gv_lan', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Trần Thị Lan (GVCN 10A2)', 'REPORTER', 'NONE', '0902000001', 0),
(4, 'gv_hung', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Thầy Đỗ Quốc Hùng (GV Toán - GVCN 11A1)', 'REPORTER', 'NONE', '0902000002', 0),
(5, 'gv_hoa', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Phạm Quỳnh Hoa (GV Hóa học - PTN)', 'REPORTER', 'NONE', '0902000003', 0),
(6, 'gv_dung', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Thầy Hoàng Tiến Dũng (GV Vật lý - Lab)', 'REPORTER', 'NONE', '0902000004', 0),
(7, 'gv_phuong', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Vũ Bích Phương (GV Sinh học - Vườn sinh thái)', 'REPORTER', 'NONE', '0902000005', 0),
(8, 'gv_duc', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Thầy Bùi Minh Đức (GV Tin học - Phòng máy 1)', 'REPORTER', 'NONE', '0902000006', 0),
(9, 'gv_thanh', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Ngô Kim Thanh (GVCN 12A3)', 'REPORTER', 'NONE', '0902000007', 0),
(10, 'gv_hang', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Trịnh Thu Hằng (GV Tiếng Anh - Lab Ngoại ngữ)', 'REPORTER', 'NONE', '0902000008', 0),
(11, 'gv_tuan', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Thầy Đinh Anh Tuấn (GV Thể dục - Sân bóng/Nhà ĐN)', 'REPORTER', 'NONE', '0902000009', 0),
(12, 'gv_quang', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Thầy Lê Nhật Quang (GV Địa lý - Hội trường)', 'REPORTER', 'NONE', '0902000010', 0),
(13, 'gv_yen', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Đặng Hải Yến (Cán bộ Thư viện)', 'REPORTER', 'NONE', '0902000011', 0),
(14, 'gv_linh', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Tạ Thùy Linh (Bí thư Đoàn trường)', 'REPORTER', 'NONE', '0902000012', 0),

-- [KHỐI 3: NGƯỜI BÁO CÁO - HỌC SINH / CÁN BỘ LỚP (12 TÀI KHOẢN)]
(15, 'hs_minh', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Lê Hoàng Minh (Lớp trưởng 10A2)', 'REPORTER', 'NONE', '0902000013', 0),
(16, 'hs_anh', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Nguyễn Phương Anh (Bí thư 10A2)', 'REPORTER', 'NONE', '0902000014', 0),
(17, 'hs_khoa', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Trần Đăng Khoa (Lớp phó Lao động 11A1)', 'REPORTER', 'NONE', '0902000015', 0),
(18, 'hs_trang', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Vũ Huyền Trang (Lớp trưởng 11B3)', 'REPORTER', 'NONE', '0902000016', 0),
(19, 'hs_nam', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Đỗ Hải Nam (Lớp trưởng 12A1)', 'REPORTER', 'NONE', '0902000017', 0),
(20, 'hs_vy', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Phạm Thảo Vy (Lớp phó Học tập 12A1)', 'REPORTER', 'NONE', '0902000018', 0),
(21, 'hs_dat', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Bùi Tiến Đạt (Thành viên Đội Cờ đỏ)', 'REPORTER', 'NONE', '0902000019', 0),
(22, 'hs_nga', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Ngô Thanh Nga (CLB Truyền thông học đường)', 'REPORTER', 'NONE', '0902000020', 0),
(23, 'hs_long', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Lý Thành Long (Đội trưởng CLB Bóng rổ)', 'REPORTER', 'NONE', '0902000021', 0),
(24, 'hs_chi', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Dương Khánh Chi (Lớp trưởng 10D1)', 'REPORTER', 'NONE', '0902000022', 0),
(25, 'hs_hoang', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cao Huy Hoàng (Lớp phó Văn thể 11A4)', 'REPORTER', 'NONE', '0902000023', 0),
(26, 'hs_thao', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Mai Phương Thảo (Đội Tình nguyện hoa phượng đỏ)', 'REPORTER', 'NONE', '0902000024', 0),

-- [KHỐI 4: CÁN BỘ PHỤ TRÁCH - PHÒNG KỸ THUẬT & CƠ SỞ VẬT CHẤT (10 TÀI KHOẢN)]
(27, 'kt_tuan', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Anh Hoàng Văn Tuấn (Kỹ thuật Điện 1)', 'RESPONDER', 'TECHNICAL', '0903000001', 0),
(28, 'kt_nam', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Anh Ngô Bảo Nam (Kỹ thuật Thiết bị Máy chiếu)', 'RESPONDER', 'TECHNICAL', '0903000002', 0),
(29, 'kt_dung', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Anh Phan Anh Dũng (Kỹ thuật Điện nước 2)', 'RESPONDER', 'TECHNICAL', '0903000003', 0),
(30, 'kt_phong', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Anh Đặng Thanh Phong (Kỹ thuật Mạng & Máy tính)', 'RESPONDER', 'TECHNICAL', '0903000004', 0),
(31, 'kt_son', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Anh Vũ Thái Sơn (Kỹ thuật mộc bàn ghế cửa kính)', 'RESPONDER', 'TECHNICAL', '0903000005', 0),
(32, 'kt_hai', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Anh Bùi Quang Hải (Kỹ thuật Âm thanh ánh sáng)', 'RESPONDER', 'TECHNICAL', '0903000006', 0),
(33, 'kt_kien', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Anh Lương Trung Kiên (Kỹ thuật Điều hòa quạt trần)', 'RESPONDER', 'TECHNICAL', '0903000007', 0),
(34, 'kt_thang', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Anh Mai Chiến Thắng (Kỹ thuật Thiết bị Lab thí nghiệm)', 'RESPONDER', 'TECHNICAL', '0903000008', 0),
(35, 'kt_viet', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Anh Trần Hoàng Việt (Kỹ thuật Cơ khí sân bãi)', 'RESPONDER', 'TECHNICAL', '0903000009', 0),
(36, 'kt_huy', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Anh Nguyễn Gia Huy (Kỹ thuật Tổng hợp ca chiều)', 'RESPONDER', 'TECHNICAL', '0903000010', 0),

-- [KHỐI 5: CÁN BỘ PHỤ TRÁCH - PHÒNG Y TẾ HỌC ĐƯỜNG (7 TÀI KHOẢN)]
(37, 'yte_mai', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Vũ Tuyết Mai (Trưởng phòng Y tế)', 'RESPONDER', 'MEDICAL', '0904000001', 0),
(38, 'yte_huong', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Nguyễn Thu Hương (Bác sĩ học đường)', 'RESPONDER', 'MEDICAL', '0904000002', 0),
(39, 'yte_linh', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Đỗ Thùy Linh (Điều dưỡng viên sơ cứu)', 'RESPONDER', 'MEDICAL', '0904000003', 0),
(40, 'yte_thao', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Phạm Thanh Thảo (Chuyên viên tư vấn tâm lý)', 'RESPONDER', 'MEDICAL', '0904000004', 0),
(41, 'yte_nhung', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Hoàng Hồng Nhung (Nhân viên Y tế trực ca sáng)', 'RESPONDER', 'MEDICAL', '0904000005', 0),
(42, 'yte_tam', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Anh Lê Đức Tâm (Dược sĩ học đường)', 'RESPONDER', 'MEDICAL', '0904000006', 0),
(43, 'yte_ngoc', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Cô Bùi Bích Ngọc (Nhân viên Y tế trực ca chiều)', 'RESPONDER', 'MEDICAL', '0904000007', 0),

-- [KHỐI 6: CÁN BỘ PHỤ TRÁCH - ĐỘI BẢO VỆ & AN NINH TRẬT TỰ (7 TÀI KHOẢN)]
(44, 'bv_son', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Bác Trương Sơn (Đội trưởng Bảo vệ)', 'RESPONDER', 'SECURITY', '0905000001', 0),
(45, 'bv_duc', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Bác Hoàng Văn Đức (Bảo vệ Cổng chính A)', 'RESPONDER', 'SECURITY', '0905000002', 0),
(46, 'bv_thanh', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Bác Đinh Công Thành (Bảo vệ Cổng phụ B)', 'RESPONDER', 'SECURITY', '0905000003', 0),
(47, 'bv_hung', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Chú Vũ Mạnh Hùng (Bảo vệ Tuần tra Nhà xe học sinh)', 'RESPONDER', 'SECURITY', '0905000004', 0),
(48, 'bv_tien', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Chú Lê Minh Tiến (Bảo vệ Giám sát Camera trung tâm)', 'RESPONDER', 'SECURITY', '0905000005', 0),
(49, 'bv_loi', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Bác Nguyễn Tấn Lợi (Bảo vệ Sân vận động & Nhà Đa năng)', 'RESPONDER', 'SECURITY', '0905000006', 0),
(50, 'bv_binh', 'fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197', 'Chú Phạm Thanh Bình (Bảo vệ Cơ động trực đêm)', 'RESPONDER', 'SECURITY', '0905000007', 0);

-- 3. SỰ CỐ MẪU TOÀN TRƯỜNG (10 SỰ CỐ ĐỦ 3 PHÒNG BAN & 4 TRẠNG THÁI: PENDING, IN_PROGRESS, RESOLVED, CANCELLED)
INSERT OR IGNORE INTO incident_tickets (
    ticket_id, ticket_code, room_location, category_code, target_department, title, description,
    image_file_path, reporter_id, reporter_name, reporter_phone, status,
    assigned_staff_id, assigned_staff_name, created_at, accepted_at, resolved_at, resolution_note
) VALUES
(1, 'TK-100001', 'A101', 'FACILITY', 'TECHNICAL', 'Quạt trần rung lắc mạnh và phát tiếng kêu lớn', 'Quạt trần số 2 dãy giữa phát ra tiếng kêu cọt kẹt to, cánh quạt có dấu hiệu lỏng ốc đảo lắc, gây nguy hiểm cho học sinh ngồi dưới.', NULL, 3, 'Cô Trần Thị Lan (GVCN 10A2)', '0902000001', 'RESOLVED', 27, 'Anh Hoàng Văn Tuấn (Kỹ thuật Điện 1)', '2026-10-31 08:15:00', '2026-10-31 08:20:00', '2026-10-31 08:45:00', 'Đã siết lại bu lông ty treo, căn chỉnh cánh quạt và tra dầu ổ trục. Quạt vận hành êm ái, an toàn.'),
(2, 'TK-100002', 'LAB-TIN1', 'IT_NETWORK', 'TECHNICAL', 'Mất kết nối mạng toàn bộ phòng máy thực hành 1', 'Switch trung tâm phòng máy bị treo đèn đỏ, 40 máy tính học sinh không vào được mạng nội bộ để làm bài kiểm tra trắc nghiệm.', NULL, 8, 'Thầy Bùi Minh Đức (GV Tin học - Phòng máy 1)', '0902000006', 'RESOLVED', 30, 'Anh Đặng Thanh Phong (Kỹ thuật Mạng & Máy tính)', '2026-10-31 09:30:00', '2026-10-31 09:35:00', '2026-10-31 10:05:00', 'Đã khởi động lại Core Switch tầng 2, bấm lại đầu cáp uplink bị move chân đồng. Toàn bộ 40 máy đã online ổn định.'),
(3, 'TK-100003', 'SAN-BONG', 'MEDICAL', 'MEDICAL', 'Học sinh chấn thương trật khớp cổ chân khi đá bóng', 'Trong giờ giải lao, học sinh tiếp đất sai tư thế, cổ chân sưng to đau nhức không tự đi lại được, cần cáng và nẹp cố định.', NULL, 11, 'Thầy Đinh Anh Tuấn (GV Thể dục - Sân bóng/Nhà ĐN)', '0902000009', 'RESOLVED', 37, 'Cô Vũ Tuyết Mai (Trưởng phòng Y tế)', '2026-10-31 10:10:00', '2026-10-31 10:12:00', '2026-10-31 10:40:00', 'Đã sơ cứu chườm đá lạnh, nẹp cố định khớp cổ chân và chuyển học sinh về phòng y tế nghỉ ngơi. Đã liên hệ phụ huynh đón về.'),
(4, 'TK-100004', 'BAI-XE-HS', 'THEFT', 'SECURITY', 'Nghi vấn mất cắp xe đạp điện khu vực lán xe số 2', 'Học sinh báo tan học ra về không tìm thấy xe đạp điện VinFast màu đen biển số 29-MD-12345 đã gửi tại lán xe từ sáng.', NULL, 15, 'Lê Hoàng Minh (Lớp trưởng 10A2)', '0902000013', 'RESOLVED', 44, 'Bác Trương Sơn (Đội trưởng Bảo vệ)', '2026-10-31 11:30:00', '2026-10-31 11:35:00', '2026-10-31 12:15:00', 'Trích xuất camera phát hiện bạn học dắt nhầm sang lán xe số 3 do xe cùng màu. Đã bàn giao lại xe nguyên vẹn cho học sinh.'),
(5, 'TK-100005', 'B204', 'ELECTRIC', 'TECHNICAL', 'Ổ cắm điện góc giảng đường bị tóe tia lửa khi cắm sạc', 'Ổ cắm điện chân tường gần bàn giáo viên bị lỏng chân tiếp xúc, khi cắm sạc laptop phát ra tia lửa xẹt xẹt và có mùi khét nhẹ.', NULL, 4, 'Thầy Đỗ Quốc Hùng (GV Toán - GVCN 11A1)', '0902000002', 'IN_PROGRESS', 27, 'Anh Hoàng Văn Tuấn (Kỹ thuật Điện 1)', '2026-11-01 08:10:00', '2026-11-01 08:15:00', NULL, NULL),
(6, 'TK-100006', 'CANG-TIN', 'FOOD_SAFETY', 'MEDICAL', 'Nhiều học sinh có biểu hiện đau bụng buồn nôn sau giờ ăn trưa', '3 học sinh khối 10 sau khi ăn cơm trưa tại căng tin trường xuất hiện triệu chứng đau quặn bụng và buồn nôn liên tục.', NULL, 21, 'Bùi Tiến Đạt (Thành viên Đội Cờ đỏ)', '0902000019', 'IN_PROGRESS', 38, 'Cô Nguyễn Thu Hương (Bác sĩ học đường)', '2026-11-01 12:20:00', '2026-11-01 12:25:00', NULL, NULL),
(7, 'TK-100007', 'CONG-CHINH', 'SECURITY', 'SECURITY', 'Nhóm thanh niên lạ mặt tụ tập gây rối trước cổng trường', 'Một nhóm 4 thanh niên không mặc đồng phục điều khiển xe máy lạng lách, to tiếng chửi bới gây mất trật tự ngay phía ngoài cổng chính.', NULL, 17, 'Trần Đăng Khoa (Lớp phó Lao động 11A1)', '0902000015', 'IN_PROGRESS', 45, 'Bác Hoàng Văn Đức (Bảo vệ Cổng chính A)', '2026-11-01 13:00:00', '2026-11-01 13:02:00', NULL, NULL),
(8, 'TK-100008', 'LAB-HOA', 'FIRST_AID', 'MEDICAL', 'Học sinh bị mảnh thủy tinh cứa rách tay trong giờ thực hành', 'Học sinh lớp 10A2 trong lúc làm thí nghiệm Hóa học làm vỡ ống nghiệm bị cứa vào lòng bàn tay chảy máu nhiều, cần bông băng garo và cồn sát khuẩn gấp.', NULL, 5, 'Cô Phạm Quỳnh Hoa (GV Hóa học - PTN)', '0902000003', 'PENDING', NULL, NULL, '2026-11-01 13:45:00', NULL, NULL, NULL),
(9, 'TK-100009', 'HOI-TRUONG', 'SECURITY', 'SECURITY', 'Phát hiện đối tượng lạ mặt khả nghi tại hành lang tầng 2', 'Bảo vệ tuần tra phát hiện 1 thanh niên không đeo thẻ ra vào, có hành vi lén lút dòm ngó các phòng chức năng tầng 2 Hội trường lớn, cần cử thêm lực lượng kiểm tra thẻ căn cước.', NULL, 12, 'Thầy Lê Nhật Quang (GV Địa lý - Hội trường)', '0902000010', 'PENDING', NULL, NULL, '2026-11-01 14:15:00', NULL, NULL, NULL),
(10, 'TK-100010', 'THU-VIEN', 'FACILITY', 'TECHNICAL', 'Điều hòa nhiệt độ phòng đọc tự ngắt sau 5 phút', 'Điều hòa âm trần số 1 nháy đèn báo lỗi rồi tự ngắt lốc làm lạnh, học sinh báo nhầm sự cố do chưa bật cầu dao điều khiển riêng.', NULL, 13, 'Cô Đặng Hải Yến (Cán bộ Thư viện)', '0902000011', 'CANCELLED', NULL, NULL, '2026-10-31 14:00:00', NULL, '2026-10-31 14:10:00', 'Người báo chủ động hủy (đã kiểm tra và bật cầu dao phụ điều hòa hoạt động bình thường)');

-- 4. TIN NHẮN TRAO ĐỔI 1-1 MẪU GIỮA NGƯỜI BÁO CÁO & CÁN BỘ XỬ LÝ / ADMIN
INSERT OR IGNORE INTO chat_messages (
    message_id, ticket_id, sender_id, sender_name, sender_role, content, sent_at
) VALUES
-- Ca 1: Quạt trần A101 (Đã hoàn tất)
(1, 1, 3, 'Cô Trần Thị Lan (GVCN 10A2)', 'REPORTER', 'Chào anh Tuấn, quạt trần số 2 phòng A101 rung lắc dữ quá, phiền anh ghé kiểm tra giúp lớp ạ.', '2026-10-31 08:16:00'),
(2, 1, 27, 'Anh Hoàng Văn Tuấn (Kỹ thuật Điện 1)', 'RESPONDER', 'Vâng cô Lan, em vừa nhận ca rồi, em đang lấy thang nhôm và bộ cờ lê qua ngay đây ạ.', '2026-10-31 08:21:00'),
(3, 1, 27, 'Anh Hoàng Văn Tuấn (Kỹ thuật Điện 1)', 'RESPONDER', 'Em đã xử lý siết bu lông ty treo, cân cánh và tra dầu ổ trục xong rồi cô nhé. Quạt chạy êm ru rồi ạ.', '2026-10-31 08:44:00'),
(4, 1, 3, 'Cô Trần Thị Lan (GVCN 10A2)', 'REPORTER', 'Cảm ơn anh Tuấn nhiều nhé, nhanh và nhiệt tình quá!', '2026-10-31 08:46:00'),

-- Ca 2: Mất mạng Lab 1 (Đã hoàn tất)
(5, 2, 8, 'Thầy Bùi Minh Đức (GV Tin học - Phòng máy 1)', 'REPORTER', 'Phong ơi, switch trung tâm phòng lab 1 sáng đèn đỏ rực, lớp sắp thi học kỳ trắc nghiệm rồi em qua gấp nhé!', '2026-10-31 09:31:00'),
(6, 2, 30, 'Anh Đặng Thanh Phong (Kỹ thuật Mạng & Máy tính)', 'RESPONDER', 'Dạ thầy Đức, em đang trên phòng máy chủ tầng 2 kiểm tra đường uplink ạ.', '2026-10-31 09:36:00'),
(7, 2, 30, 'Anh Đặng Thanh Phong (Kỹ thuật Mạng & Máy tính)', 'RESPONDER', 'Em đã bấm lại hạt mạng RJ45 cổng uplink và reboot switch rồi, 40 máy con đã có mạng ổn định rồi thầy kiểm tra lại giúp em nhé.', '2026-10-31 10:04:00'),
(8, 2, 8, 'Thầy Bùi Minh Đức (GV Tin học - Phòng máy 1)', 'REPORTER', 'Tốt quá Phong ơi, các em đã bắt đầu làm bài thi được rồi. Cảm ơn em!', '2026-10-31 10:06:00'),

-- Ca 3: Trật chân sân bóng (Đã hoàn tất)
(9, 3, 11, 'Thầy Đinh Anh Tuấn (GV Thể dục - Sân bóng/Nhà ĐN)', 'REPORTER', 'Cô Mai ơi, sân bóng có một bạn học sinh tranh chấp bóng bị trật khớp cổ chân khá nặng, cần cáng y tế gấp ạ!', '2026-10-31 10:11:00'),
(10, 3, 37, 'Cô Vũ Tuyết Mai (Trưởng phòng Y tế)', 'RESPONDER', 'Đội y tế đang mang nẹp cố định, túi đá lạnh và cáng chạy ra sân bóng ngay đây thầy Tuấn!', '2026-10-31 10:13:00'),
(11, 3, 37, 'Cô Vũ Tuyết Mai (Trưởng phòng Y tế)', 'RESPONDER', 'Đã đưa em về phòng y tế chườm đá và nẹp tạm thời, phụ huynh em cũng đang trên đường tới trường đón rồi thầy nhé.', '2026-10-31 10:39:00'),
(12, 3, 11, 'Thầy Đinh Anh Tuấn (GV Thể dục - Sân bóng/Nhà ĐN)', 'REPORTER', 'Dạ cảm ơn cô Mai và phòng y tế đã hỗ trợ kịp thời!', '2026-10-31 10:41:00'),

-- Ca 4: Xe đạp điện lán 2 (Đã hoàn tất)
(13, 4, 15, 'Lê Hoàng Minh (Lớp trưởng 10A2)', 'REPORTER', 'Bác Sơn ơi, lớp cháu có bạn tan học ra tìm không thấy xe đạp điện VinFast ở lán số 2 ạ!', '2026-10-31 11:31:00'),
(14, 4, 44, 'Bác Trương Sơn (Đội trưởng Bảo vệ)', 'RESPONDER', 'Bác đang cho tổ trực camera kiểm tra các lượt xe ra vào cổng, cháu dắt bạn qua phòng bảo vệ cổng chính ngay nhé.', '2026-10-31 11:36:00'),
(15, 4, 44, 'Bác Trương Sơn (Đội trưởng Bảo vệ)', 'RESPONDER', 'Tìm thấy xe rồi nhé các cháu! Bạn lớp 10D1 đi xe giống hệt dắt nhầm sang lán 3, xe vẫn còn nguyên chìa khóa.', '2026-10-31 12:14:00'),
(16, 4, 15, 'Lê Hoàng Minh (Lớp trưởng 10A2)', 'REPORTER', 'Dạ may quá bạn cháu mừng rớt nước mắt, cháu cảm ơn các bác bảo vệ nhiều ạ!', '2026-10-31 12:16:00'),

-- Ca 5: Ổ cắm B204 tóe lửa (Đang xử lý)
(17, 5, 4, 'Thầy Đỗ Quốc Hùng (GV Toán - GVCN 11A1)', 'REPORTER', 'Anh Tuấn ơi, phòng B204 ổ cắm chân tường gần bàn giáo viên bị tóe lửa khét quá, em đã dập tạm aptomat nhánh số 3 rồi.', '2026-11-01 08:11:00'),
(18, 5, 27, 'Anh Hoàng Văn Tuấn (Kỹ thuật Điện 1)', 'RESPONDER', 'Thầy Hùng ngắt tạm aptomat nhánh là rất đúng quy trình an toàn điện! Em đang mang cụm ổ cắm Sino mới và đồng hồ đo điện áp lên thay ngay đây ạ.', '2026-11-01 08:16:00'),

-- Ca 6: Đau bụng căng tin (Đang xử lý - có Admin BGH kiểm toán)
(19, 6, 21, 'Bùi Tiến Đạt (Thành viên Đội Cờ đỏ)', 'REPORTER', 'Cô Hương ơi, ở căng tin có 3 bạn học sinh lớp 10 sau khi ăn trưa bị đau quặn bụng và buồn nôn liên tục ạ!', '2026-11-01 12:21:00'),
(20, 6, 38, 'Cô Nguyễn Thu Hương (Bác sĩ học đường)', 'RESPONDER', 'Cô và cô Nhung đang xuống đưa các bạn lên phòng y tế uống trà gừng và men tiêu hóa, đồng thời niêm phong lưu mẫu thức ăn để kiểm tra.', '2026-11-01 12:26:00'),
(21, 6, 1, 'Thầy Nguyễn Văn An (Hiệu trưởng)', 'ADMIN', 'Ban Giám hiệu yêu cầu bộ phận Y tế theo dõi sát sao mạch, nhiệt độ của các em, nếu có dấu hiệu nặng phải gọi cấp cứu 115 ngay lập tức!', '2026-11-01 12:30:00'),
(22, 6, 38, 'Cô Nguyễn Thu Hương (Bác sĩ học đường)', 'RESPONDER', 'Báo cáo Thầy Hiệu trưởng: Hiện tại 3 em đã đỡ đau bụng, huyết áp và nhịp thở ổn định, phụ huynh các em đã có mặt tại phòng y tế.', '2026-11-01 12:45:00'),

-- Ca 7: Thanh niên gây rối cổng trường (Đang xử lý)
(23, 7, 17, 'Trần Đăng Khoa (Lớp phó Lao động 11A1)', 'REPORTER', 'Bác Đức ơi, ngoài cổng trường có nhóm thanh niên lạ mặt đi xe máy nẹt bô và to tiếng chửi bới, học sinh tan trường không dám đi ra ạ!', '2026-11-01 13:01:00'),
(24, 7, 45, 'Bác Hoàng Văn Đức (Bảo vệ Cổng chính A)', 'RESPONDER', 'Tổ bảo vệ đã đóng bớt cổng phụ, 3 đồng chí bảo vệ đã mang gậy cao su ra nhắc nhở giải tán, đồng thời bác đã báo công an phường hỗ trợ tuần tra rồi, các cháu cứ ở yên trong sảnh nhé.', '2026-11-01 13:03:00');
