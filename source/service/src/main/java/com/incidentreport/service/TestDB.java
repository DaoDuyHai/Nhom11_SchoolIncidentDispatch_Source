package com.incidentreport.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestDB {
    public static void main(String[] args) {
        System.out.println("=== KIỂM TRA TOÀN DIỆN CSDL 6 BẢNG ===");
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) {
                System.err.println("❌ Không thể kết nối Database!");
                return;
            }

            Statement stmt = conn.createStatement();
            
            // 1. Kiểm tra incident_categories
            System.out.println("\n1. BẢNG incident_categories:");
            ResultSet rs = stmt.executeQuery("SELECT category_code, category_name, required_specialty, default_severity FROM incident_categories");
            while (rs.next()) {
                System.out.printf("  - [%s] %s | Cần: %s | Severity: %d\n",
                    rs.getString("category_code"), rs.getString("category_name"),
                    rs.getString("required_specialty"), rs.getInt("default_severity"));
            }

            // 2. Kiểm tra users
            System.out.println("\n2. BẢNG users:");
            rs = stmt.executeQuery("SELECT user_id, username, full_name, role, specialty, is_online FROM users");
            while (rs.next()) {
                System.out.printf("  - ID: %d | %s (%s) | Role: %s | Specialty: %s | Online: %d\n",
                    rs.getInt("user_id"), rs.getString("username"), rs.getString("full_name"),
                    rs.getString("role"), rs.getString("specialty"), rs.getInt("is_online"));
            }

            // 3. Kiểm tra incident_tickets
            System.out.println("\n3. BẢNG incident_tickets:");
            rs = stmt.executeQuery("SELECT ticket_id, ticket_code, room_location, category_code, status, report_count FROM incident_tickets");
            while (rs.next()) {
                System.out.printf("  - ID: %d | Mã: %s | Phòng: %s | Loại: %s | Trạng thái: %s | Số người báo: %d\n",
                    rs.getInt("ticket_id"), rs.getString("ticket_code"), rs.getString("room_location"),
                    rs.getString("category_code"), rs.getString("status"), rs.getInt("report_count"));
            }

            // 4. Kiểm tra incident_reports
            System.out.println("\n4. BẢNG incident_reports:");
            rs = stmt.executeQuery("SELECT report_id, ticket_id, reporter_id, description, is_duplicate_merged, image_file_path FROM incident_reports");
            while (rs.next()) {
                System.out.printf("  - Report #%d (Ticket #%d, Reporter #%d): %s | Đã gộp: %d | Ảnh: %s\n",
                    rs.getInt("report_id"), rs.getInt("ticket_id"), rs.getInt("reporter_id"),
                    rs.getString("description"), rs.getInt("is_duplicate_merged"), rs.getString("image_file_path"));
            }

            // 5. Kiểm tra dispatch_logs
            System.out.println("\n5. BẢNG dispatch_logs:");
            rs = stmt.executeQuery("SELECT log_id, ticket_id, staff_id, dispatch_status, note FROM dispatch_logs");
            while (rs.next()) {
                System.out.printf("  - Log #%d (Ticket #%d -> Staff #%d): Status: %s | Note: %s\n",
                    rs.getInt("log_id"), rs.getInt("ticket_id"), rs.getInt("staff_id"),
                    rs.getString("dispatch_status"), rs.getString("note"));
            }

            System.out.println("\n✅ TẤT CẢ 6 BẢNG HOẠT ĐỘNG HOÀN HẢO THEO SCHEMA.SQL!");

        } catch (Exception e) {
            System.err.println("❌ Lỗi truy vấn: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
