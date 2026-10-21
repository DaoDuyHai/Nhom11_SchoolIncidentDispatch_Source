package com.incidentreport.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestDB {
    public static void main(String[] args) {
        System.out.println("=== KIỂM TRA TOÀN DIỆN CSDL 4 BẢNG CHUẨN KIẾN TRÚC ===");
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) {
                System.err.println("❌ Không thể kết nối Database!");
                return;
            }

            Statement stmt = conn.createStatement();
            
            // 1. Kiểm tra incident_categories
            System.out.println("\n1. BẢNG incident_categories:");
            ResultSet rs = stmt.executeQuery("SELECT category_code, category_name, required_specialty FROM incident_categories");
            while (rs.next()) {
                System.out.printf("  - [%s] %s -> Phòng ban điều phối: %s\n",
                    rs.getString("category_code"), rs.getString("category_name"),
                    rs.getString("required_specialty"));
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
            rs = stmt.executeQuery("SELECT ticket_id, ticket_code, room_location, category_code, target_department, status, reporter_name, assigned_staff_name FROM incident_tickets");
            while (rs.next()) {
                System.out.printf("  - ID: %d | Mã: %s | Phòng: %s | Loại: %s | PB: %s | Trạng thái: %s | Người báo: %s | Phụ trách: %s\n",
                    rs.getInt("ticket_id"), rs.getString("ticket_code"), rs.getString("room_location"),
                    rs.getString("category_code"), rs.getString("target_department"), rs.getString("status"),
                    rs.getString("reporter_name"), rs.getString("assigned_staff_name"));
            }

            // 4. Kiểm tra chat_messages
            System.out.println("\n4. BẢNG chat_messages:");
            rs = stmt.executeQuery("SELECT message_id, ticket_id, sender_name, sender_role, content, sent_at FROM chat_messages");
            while (rs.next()) {
                System.out.printf("  - Msg #%d (Ticket #%d): [%s - %s] %s (%s)\n",
                    rs.getInt("message_id"), rs.getInt("ticket_id"),
                    rs.getString("sender_role"), rs.getString("sender_name"),
                    rs.getString("content"), rs.getString("sent_at"));
            }

            System.out.println("\n✅ TẤT CẢ 4 BẢNG ĐÃ ĐƯỢC KIỂM TRA THÀNH CÔNG!");

        } catch (Exception e) {
            System.err.println("❌ Lỗi truy vấn: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
