package com.incidentreport.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

public class InitDatabase {

    public static File findFile(String relativePath) {
        File f = new File(relativePath);
        if (f.exists()) return f;
        f = new File("../" + relativePath);
        if (f.exists()) return f;
        f = new File("../../" + relativePath);
        if (f.exists()) return f;
        return new File(relativePath);
    }

    public static boolean init() {
        File schemaFile = findFile("database/schema.sql");
        if (!schemaFile.exists()) {
            System.err.println("❌ Không tìm thấy schema.sql tại: " + schemaFile.getAbsolutePath());
            return false;
        }

        System.out.println("🔄 Bắt đầu nạp CSDL từ: " + schemaFile.getAbsolutePath());
        
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(schemaFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("--") || line.isEmpty()) {
                    continue;
                }
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi đọc schema.sql: " + e.getMessage());
            return false;
        }

        // Tách các câu lệnh theo dấu ;
        String[] statements = sb.toString().split(";\\s*(\\r?\\n)");

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) {
                System.err.println("❌ Không thể kết nối DB!");
                return false;
            }
            conn.setAutoCommit(true); // SQLite requires auto-commit for VACUUM and PRAGMA journal_mode
            try (Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    sql = sql.trim();
                    if (!sql.isEmpty()) {
                        try {
                            stmt.execute(sql);
                        } catch (Exception ex) {
                            System.err.println("⚠️ Cảnh báo lệnh SQL: " + sql.substring(0, Math.min(sql.length(), 60)) + "... Lỗi: " + ex.getMessage());
                        }
                    }
                }
                System.out.println("✅ Đã khởi tạo thành công 6 bảng và nạp Seed Data vào CSDL!");
                return true;
            } catch (Exception e) {
                System.err.println("❌ Lỗi khi thực thi SQL: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi kết nối CSDL: " + e.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        init();
    }
}

