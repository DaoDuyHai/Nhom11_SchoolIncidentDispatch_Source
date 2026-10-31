package com.incidentreport.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Tiện ích tự động khởi tạo cơ sở dữ liệu từ file schema.sql:
 * - Kiểm tra thông minh: Nếu CSDL đã có bảng và dữ liệu thì GIỮ NGUYÊN, không xóa/drop dữ liệu khi restart.
 * - Chỉ tạo bảng và nạp dữ liệu mẫu khi CSDL chưa từng được khởi tạo.
 */
public class InitDatabase {

    public static File getUploadDirectory() {
        File dbFile = DatabaseConnection.getDatabaseFile();
        if (dbFile != null && dbFile.exists()) {
            File dbDir = dbFile.getParentFile();
            if (dbDir != null) {
                File projectRoot = dbDir.getParentFile();
                if (projectRoot != null && projectRoot.exists()) {
                    File dir = new File(projectRoot, "source/uploads");
                    if (!dir.exists()) dir.mkdirs();
                    return dir;
                }
            }
        }
        String[] candidates = new String[]{
                "source/uploads",
                "uploads",
                "../source/uploads",
                "../uploads",
                "../../source/uploads",
                "../../uploads"
        };
        for (String c : candidates) {
            File d = new File(c);
            if (d.exists() && d.isDirectory()) return d;
        }
        File defaultDir = new File("source/uploads");
        if (!defaultDir.exists()) defaultDir.mkdirs();
        return defaultDir;
    }

    public static File findFile(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) return new File(".");
        String clean = relativePath.trim().replace('\\', '/');

        if (clean.startsWith("uploads/") || clean.equals("uploads")) {
            File uploadDir = getUploadDirectory();
            if (clean.equals("uploads")) return uploadDir;
            String fileName = clean.substring("uploads/".length());
            File f = new File(uploadDir, fileName);
            if (f.exists()) return f;
        }

        File f = new File(clean);
        if (f.exists()) return f;
        f = new File("../" + clean);
        if (f.exists()) return f;
        f = new File("../../" + clean);
        if (f.exists()) return f;
        f = new File("source/" + clean);
        if (f.exists()) return f;
        f = new File("../source/" + clean);
        if (f.exists()) return f;
        return new File(clean);
    }

    public static boolean isDatabaseInitialized(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='users'")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean initDatabase() {
        return init(false);
    }

    public static boolean init() {
        return init(false);
    }

    public static boolean resetDatabase() {
        // BẢO VỆ CSDL GỐC: Khi reset/test, tự động chuyển hướng sang test_incident_report.db
        if (DatabaseConnection.getCustomDbUrl() == null) {
            File testDb = findFile("database/test_incident_report.db");
            DatabaseConnection.setCustomDbUrl("jdbc:sqlite:" + testDb.getAbsolutePath());
        }
        return init(true);
    }

    public static boolean init(boolean forceReset) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) {
                System.err.println("❌ Không thể kết nối cơ sở dữ liệu!");
                return false;
            }

            // Nếu không yêu cầu reset và DB đã tồn tại -> Giữ nguyên dữ liệu, đồng bộ danh mục mới nếu có
            if (!forceReset && isDatabaseInitialized(conn)) {
                executeSqlFile(conn, "database/seed_data.sql");
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("UPDATE incident_tickets SET resolve_note = 'Người báo chủ động hủy' WHERE resolve_note = 'Nguoi bao chu dong huy'");
                } catch (Exception ignored) {}
                System.out.println("ℹ️ Cơ sở dữ liệu SQLite đã sẵn sàng (giữ nguyên toàn bộ dữ liệu hiện có).");
                return true;
            }

            if (forceReset) {
                System.out.println("⚠️ Đang đặt lại CSDL về trạng thái ban đầu...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = OFF;");
                    stmt.execute("DROP TABLE IF EXISTS chat_messages;");
                    stmt.execute("DROP TABLE IF EXISTS incident_tickets;");
                    stmt.execute("DROP TABLE IF EXISTS incident_categories;");
                    stmt.execute("DROP TABLE IF EXISTS users;");
                    stmt.execute("PRAGMA foreign_keys = ON;");
                }
            }

            boolean schemaOk = executeSqlFile(conn, "database/schema.sql");
            if (!schemaOk) return false;

            executeSqlFile(conn, "database/seed_data.sql");
            System.out.println("✅ Đã khởi tạo thành công CSDL và nạp dữ liệu người dùng ban đầu!");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Lỗi kết nối CSDL: " + e.getMessage());
            return false;
        }
    }

    public static boolean executeSqlFile(Connection conn, String relativePath) {
        File f = findFile(relativePath);
        if (!f.exists()) {
            System.err.println("❌ Không tìm thấy file: " + f.getAbsolutePath());
            return false;
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("--") || line.isEmpty()) {
                    continue;
                }
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi đọc " + relativePath + ": " + e.getMessage());
            return false;
        }

        String[] statements = sb.toString().split(";\\s*(\\r?\\n)");
        try (Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    try {
                        stmt.execute(sql);
                    } catch (Exception ex) {
                        // Ignored if table or index already exists
                    }
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("❌ Lỗi thực thi " + relativePath + ": " + e.getMessage());
            return false;
        }
    }

    public static boolean deployMainDatabase() {
        DatabaseConnection.setCustomDbUrl(null);
        return init(true);
    }

    public static void main(String[] args) {
        System.out.println("🛠️ Đang khởi tạo/kiểm tra CSDL SQLite...");
        boolean success = init(false);
        if (success) {
            System.out.println("🎉 Hoàn tất kiểm tra CSDL.");
        } else {
            System.err.println("💥 Khởi tạo CSDL thất bại.");
        }
    }
}
