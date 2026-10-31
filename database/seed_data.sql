-- ============================================================================
-- DỮ LIỆU MẪU KHỞI TẠO (SEED DATA) - HỆ THỐNG ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT
-- Mật khẩu mặc định toàn bộ tài khoản: 123456
-- Chuỗi Salted SHA-256 hash chuẩn: fca344408ed5ac284ec7f629e36d9ec0c313809338f6fd9ed9d773f4713ae197
-- Không nạp sẵn sự cố hay tin nhắn (để trống 100% cho người dùng tự trải nghiệm)
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

