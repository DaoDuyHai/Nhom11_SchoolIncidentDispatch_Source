-- ============================================================================
-- SEED DATA: DỮ LIỆU KHỞI TẠO MẪU CHO HỆ THỐNG ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT
-- ============================================================================

-- 1. Danh mục Sự cố
INSERT INTO incident_categories (category_code, category_name, required_specialty, default_severity, dedup_window_seconds) VALUES
('FACILITY', 'Hỏng thiết bị lớp học (Quạt, Đèn, Máy chiếu)', 'TECHNICAL', 2, 300),
('WATER', 'Sự cố Điện nước (Vỡ ống nước, Mất nước)', 'TECHNICAL', 3, 300),
('ELECTRIC', 'Sự cố Điện nguy hiểm (Chập cầu dao, Rò điện)', 'TECHNICAL', 4, 300),
('MEDICAL', 'Cấp cứu Y tế (Học sinh ngất xỉu, Chấn thương)', 'MEDICAL', 5, 180),
('SECURITY', 'An ninh Trật tự (Xô xát, Người lạ đột nhập)', 'SECURITY', 4, 180),
('FIRE', 'Cháy nổ / Rò rỉ khí độc phòng thí nghiệm', 'ALL', 5, 120);

-- 2. Tài khoản Người dùng (Mật khẩu mặc định: 123456)
INSERT INTO users (username, password_hash, full_name, role, sub_role, specialty, phone, is_online, availability_status, current_workload, last_heartbeat_at) VALUES
('admin_bgh', '123456', 'Thầy Nguyễn Văn An (Hiệu trưởng)', 'ADMIN', 'PRINCIPAL', 'NONE', '0901000001', 1, 'AVAILABLE', 0, STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime')),
('gv_lan', '123456', 'Cô Trần Thị Lan (GVCN 10A2)', 'REPORTER', 'TEACHER', 'NONE', '0902000001', 1, 'AVAILABLE', 0, NULL),
('hs_minh', '123456', 'Lê Hoàng Minh (Lớp trưởng 10A2)', 'REPORTER', 'STUDENT', 'NONE', '0902000002', 1, 'AVAILABLE', 0, NULL),
('kt_tuan', '123456', 'Anh Hoàng Văn Tuấn (Kỹ thuật Điện 1)', 'RESPONDER', 'TECH_STAFF', 'TECHNICAL', '0903000001', 1, 'AVAILABLE', 0, STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime')),
('kt_nam', '123456', 'Anh Ngô Bảo Nam (Kỹ thuật Điện 2)', 'RESPONDER', 'TECH_STAFF', 'TECHNICAL', '0903000002', 1, 'AVAILABLE', 2, STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime')),
('yte_mai', '123456', 'Cô Vũ Tuyết Mai (Nhân viên Y tế)', 'RESPONDER', 'MEDICAL_STAFF', 'MEDICAL', '0903000003', 1, 'AVAILABLE', 0, STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime')),
('bv_son', '123456', 'Bác Trương Sơn (Đội trưởng Bảo vệ)', 'RESPONDER', 'SECURITY_GUARD', 'SECURITY', '0903000004', 1, 'AVAILABLE', 0, STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime'));

-- 3. Ticket mẫu đã gom nhóm từ 2 báo cáo trùng lặp tại phòng A201
INSERT INTO incident_tickets (
    ticket_code, room_location, category_code, title,
    base_severity, report_count, computed_priority,
    status, assigned_staff_id, queue_waiting_time_ms, failover_count
) VALUES (
    'INC-20261014-0001', 'A201', 'ELECTRIC', 'Chập cầu dao tổng có mùi khét tại phòng A201',
    4, 2, 4.5,
    'ASSIGNED', 4, 42, 0
);

INSERT INTO incident_reports (ticket_id, reporter_id, reported_severity, description, image_file_path, image_size_bytes, is_duplicate_merged, client_ip) VALUES
(1, 2, 4, 'Cầu dao cuối lớp A201 phát tia lửa điện và có mùi khét.', 'uploads/inc_a201_gv.jpg', 245120, 0, '192.168.1.101'),
(1, 3, 4, 'Bảng điện phòng A201 bị nổ cầu chì, mất điện toàn phòng.', NULL, 0, 1, '192.168.1.102');

INSERT INTO dispatch_logs (
    ticket_id, staff_id, attempt_number,
    workload_at_dispatch, total_dispatch_score,
    dispatch_status, responded_at, note
) VALUES (
    1, 4, 1,
    0, 100.0,
    'ACCEPTED', STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime'), 'Điều phối tự động cho nhân viên kỹ thuật có workload thấp nhất'
);

