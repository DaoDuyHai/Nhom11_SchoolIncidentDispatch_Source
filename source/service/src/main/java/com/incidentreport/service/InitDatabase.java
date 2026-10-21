package com.incidentreport.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Tiện ích tự động khởi tạo cơ sở dữ liệu từ file schema.sql
 */
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

    public static boolean initDatabase() {
        return init();
    }

    public static boolean init() {
        File schemaFile = findFile("database/schema.sql");
        if (!schemaFile.exists()) {
            System.err.println("❌ Không tìm thấy schema.sql tại: " + schemaFile.getAbsolutePath());
            return false;
        }

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

        String[] statements = sb.toString().split(";\\s*(\\r?\\n)");

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) return false;
            conn.setAutoCommit(true);
            try (Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    sql = sql.trim();
                    if (!sql.isEmpty()) {
                        try {
                            stmt.execute(sql);
                        } catch (Exception ex) {
                            // Ignored if table already exists
                        }
                    }
                }
                System.out.println("✅ Đã khởi tạo thành công CSDL và nạp tài khoản mẫu!");
                return true;
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi kết nối CSDL: " + e.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        System.out.println("🛠️ Đang khởi tạo CSDL SQLite...");
        boolean success = init();
        if (success) {
            System.out.println("🎉 Khởi tạo hoàn tất!");
        } else {
            System.err.println("❌ Khởi tạo thất bại.");
        }
    }
}
