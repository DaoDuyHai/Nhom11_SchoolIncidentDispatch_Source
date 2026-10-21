-- ============================================================================
-- CƠ SỞ DỮ LIỆU SQLITE - SCHOOL INCIDENT DISPATCH SYSTEM (BASIC CORE)
-- ============================================================================

PRAGMA foreign_keys = OFF;

DROP TABLE IF EXISTS chat_messages;
DROP TABLE IF EXISTS incident_tickets;
DROP TABLE IF EXISTS incident_categories;
DROP TABLE IF EXISTS users;

PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

-- 1. BẢNG USERS: Quản lý tài khoản duy nhất do Admin/Server cấp
CREATE TABLE users (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    full_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('REPORTER', 'RESPONDER', 'ADMIN')),
    specialty TEXT NOT NULL DEFAULT 'NONE' CHECK (specialty IN ('NONE', 'TECHNICAL', 'MEDICAL', 'SECURITY', 'ALL')),
    phone TEXT,
    is_online INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime'))
);

-- 2. BẢNG INCIDENT_CATEGORIES: Định tuyến phòng ban chuyên trách
CREATE TABLE incident_categories (
    category_code TEXT PRIMARY KEY,
    category_name TEXT NOT NULL,
    required_specialty TEXT NOT NULL CHECK (required_specialty IN ('TECHNICAL', 'MEDICAL', 'SECURITY', 'ALL'))
);

-- 3. BẢNG INCIDENT_TICKETS: Lưu trữ sự cố cốt lõi
CREATE TABLE incident_tickets (
    ticket_id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_code TEXT NOT NULL UNIQUE,
    room_location TEXT NOT NULL,
    category_code TEXT NOT NULL,
    target_department TEXT NOT NULL DEFAULT 'TECHNICAL',
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    image_file_path TEXT NULL,
    reporter_id INTEGER NOT NULL,
    reporter_name TEXT NOT NULL,
    reporter_phone TEXT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED')),
    assigned_staff_id INTEGER NULL,
    assigned_staff_name TEXT NULL,
    created_at TEXT NOT NULL DEFAULT (STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime')),
    accepted_at TEXT NULL,
    resolved_at TEXT NULL,
    resolution_note TEXT NULL,
    FOREIGN KEY (category_code) REFERENCES incident_categories(category_code) ON DELETE RESTRICT,
    FOREIGN KEY (reporter_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    FOREIGN KEY (assigned_staff_id) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE INDEX idx_tickets_status ON incident_tickets(status, target_department, created_at);

-- 4. BẢNG CHAT_MESSAGES: Trao đổi trực tiếp 1-1 giữa Reporter và Responder (Admin xem audit)
CREATE TABLE chat_messages (
    message_id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id INTEGER NOT NULL,
    sender_id INTEGER NOT NULL,
    sender_name TEXT NOT NULL,
    sender_role TEXT NOT NULL,
    content TEXT NOT NULL,
    sent_at TEXT NOT NULL DEFAULT (STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime')),
    FOREIGN KEY (ticket_id) REFERENCES incident_tickets(ticket_id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE RESTRICT
);

CREATE INDEX idx_chat_ticket ON chat_messages(ticket_id, sent_at);

-- ============================================================================
-- NẠP DỮ LIỆU KHỞI TẠO (SEED DATA)
-- ============================================================================

-- 1. Danh mục sự cố & Định tuyến phòng ban
INSERT INTO incident_categories (category_code, category_name, required_specialty) VALUES
('FACILITY', 'Hỏng thiết bị lớp học (Quạt, Đèn, Máy chiếu)', 'TECHNICAL'),
('WATER', 'Sự cố Điện nước (Vỡ ống nước, Mất nước)', 'TECHNICAL'),
('ELECTRIC', 'Sự cố Điện nguy hiểm (Chập cầu dao, Rò điện)', 'TECHNICAL'),
('MEDICAL', 'Cấp cứu Y tế (Học sinh ngất, Chấn thương, Sốt)', 'MEDICAL'),
('SECURITY', 'An ninh Trật tự (Xô xát, Người lạ đột nhập, Trộm cắp)', 'SECURITY');

-- 2. Tài khoản mẫu mặc định do Admin/Server cấp (Mật khẩu: 123456)
-- Mật khẩu 123456 đã được băm SHA-256: 8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92
INSERT INTO users (username, password_hash, full_name, role, specialty, phone, is_online) VALUES
('admin_bgh', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'Thầy Nguyễn Văn An (Hiệu trưởng)', 'ADMIN', 'NONE', '0901000001', 0),
('gv_lan', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'Cô Trần Thị Lan (GVCN 10A2)', 'REPORTER', 'NONE', '0902000001', 0),
('hs_minh', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'Lê Hoàng Minh (Lớp trưởng 10A2)', 'REPORTER', 'NONE', '0902000002', 0),
('kt_tuan', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'Anh Hoàng Văn Tuấn (Kỹ thuật Điện 1)', 'RESPONDER', 'TECHNICAL', '0903000001', 0),
('kt_nam', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'Anh Ngô Bảo Nam (Kỹ thuật Thiết bị 2)', 'RESPONDER', 'TECHNICAL', '0903000002', 0),
('yte_mai', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'Cô Vũ Tuyết Mai (Nhân viên Y tế)', 'RESPONDER', 'MEDICAL', '0903000003', 0),
('bv_son', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'Bác Trương Sơn (Đội trưởng Bảo vệ)', 'RESPONDER', 'SECURITY', '0903000004', 0);

-- 3. Sự cố mẫu ban đầu
INSERT INTO incident_tickets (ticket_id, ticket_code, room_location, category_code, target_department, title, description, reporter_id, reporter_name, reporter_phone, status) VALUES
(1, 'TK-INIT-001', 'A101', 'FACILITY', 'TECHNICAL', 'A101 - FACILITY', 'Máy chiếu phòng học chập chờn', 2, 'Cô Trần Thị Lan (GVCN 10A2)', '0902000001', 'PENDING'),
(2, 'TK-INIT-002', 'B202', 'MEDICAL', 'MEDICAL', 'B202 - MEDICAL', 'Học sinh lớp 11B bị đau bụng cần y tế', 3, 'Lê Hoàng Minh (Lớp trưởng 10A2)', '0902000002', 'PENDING');

-- 4. Tin nhắn trao đổi mẫu cho sự cố 1
INSERT INTO chat_messages (message_id, ticket_id, sender_id, sender_name, sender_role, content) VALUES
(1, 1, 2, 'Cô Trần Thị Lan (GVCN 10A2)', 'REPORTER', 'Nhờ kỹ thuật kiểm tra giúp phòng A101 nhé');
