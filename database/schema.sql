-- ============================================================================
-- CƠ SỞ DỮ LIỆU SQLITE - SCHOOL INCIDENT DISPATCH SYSTEM (SCHEMA DDL)
-- Kiến trúc: Chuẩn Senior 4 bảng liên kết, hỗ trợ WAL Mode & Foreign Keys
-- ============================================================================

PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

-- 1. BẢNG USERS: Quản lý tài khoản toàn trường (Admin/Server cấp độc quyền)
CREATE TABLE IF NOT EXISTS users (
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

CREATE INDEX IF NOT EXISTS idx_users_role ON users(role, specialty);

-- 2. BẢNG INCIDENT_CATEGORIES: Danh mục sự cố & Định tuyến phòng ban chuyên trách
CREATE TABLE IF NOT EXISTS incident_categories (
    category_code TEXT PRIMARY KEY,
    category_name TEXT NOT NULL,
    required_specialty TEXT NOT NULL CHECK (required_specialty IN ('TECHNICAL', 'MEDICAL', 'SECURITY', 'ALL'))
);

-- 3. BẢNG INCIDENT_TICKETS: Lưu trữ sự cố cốt lõi trường học
CREATE TABLE IF NOT EXISTS incident_tickets (
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

CREATE INDEX IF NOT EXISTS idx_tickets_status ON incident_tickets(status, target_department, created_at);
CREATE INDEX IF NOT EXISTS idx_tickets_reporter ON incident_tickets(reporter_id, created_at);
CREATE INDEX IF NOT EXISTS idx_tickets_staff ON incident_tickets(assigned_staff_id, status);

-- 4. BẢNG CHAT_MESSAGES: Trao đổi trực tiếp 1-1 giữa Reporter và Responder (Admin kiểm toán)
CREATE TABLE IF NOT EXISTS chat_messages (
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

CREATE INDEX IF NOT EXISTS idx_chat_ticket ON chat_messages(ticket_id, sent_at);
