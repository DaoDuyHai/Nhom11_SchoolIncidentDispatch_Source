package com.incidentreport.service;

import com.incidentreport.common.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    public static User login(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password_hash = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    User user = mapUser(rs);
                    // Đánh dấu online
                    setOnlineStatus(user.getUserId(), 1, "AVAILABLE");
                    user.setIsOnline(1);
                    user.setAvailabilityStatus("AVAILABLE");
                    return user;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi UserDAO.login: " + e.getMessage());
        }
        return null;
    }

    public static void setOnlineStatus(int userId, int isOnline, String availability) {
        String sql = "UPDATE users SET is_online = ?, availability_status = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, isOnline);
            pstmt.setString(2, availability);
            pstmt.setInt(3, userId);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("❌ Lỗi setOnlineStatus: " + e.getMessage());
        }
    }

    public static void updateHeartbeat(int userId, String availability) {
        String sql = "UPDATE users SET last_heartbeat_at = STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime'), " +
                     "is_online = 1, availability_status = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, availability);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("❌ Lỗi updateHeartbeat: " + e.getMessage());
        }
    }

    public static User getUserById(int userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapUser(rs);
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getUserById: " + e.getMessage());
        }
        return null;
    }

    public static User getUserByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapUser(rs);
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getUserByUsername: " + e.getMessage());
        }
        return null;
    }

    /**
     * Thuật toán Smart Dispatch: Lấy danh sách nhân viên thỏa mãn chuyên môn,
     * sắp xếp ưu tiên: Sẵn sàng (AVAILABLE) trước, và Tải công việc (current_workload) thấp nhất!
     */
    public static List<User> getCandidateResponders(String requiredSpecialty) {
        List<User> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM users WHERE role = 'RESPONDER' AND is_online = 1 "
        );
        if (requiredSpecialty != null && !"ALL".equalsIgnoreCase(requiredSpecialty)) {
            sql.append(" AND (specialty = ? OR specialty = 'ALL') ");
        }
        sql.append(" ORDER BY CASE availability_status WHEN 'AVAILABLE' THEN 1 ELSE 2 END ASC, current_workload ASC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            if (requiredSpecialty != null && !"ALL".equalsIgnoreCase(requiredSpecialty)) {
                pstmt.setString(1, requiredSpecialty);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapUser(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getCandidateResponders: " + e.getMessage());
        }
        return list;
    }

    public static void adjustWorkload(int userId, int delta) {
        String sql = "UPDATE users SET current_workload = MAX(0, current_workload + ?) WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, delta);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("❌ Lỗi adjustWorkload: " + e.getMessage());
        }
    }

    public static List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY role DESC, full_name ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapUser(rs));
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getAllUsers: " + e.getMessage());
        }
        return list;
    }

    private static User mapUser(ResultSet rs) throws Exception {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));
        u.setRole(rs.getString("role"));
        u.setSubRole(rs.getString("sub_role"));
        u.setSpecialty(rs.getString("specialty"));
        u.setPhone(rs.getString("phone"));
        u.setIsOnline(rs.getInt("is_online"));
        u.setAvailabilityStatus(rs.getString("availability_status"));
        u.setCurrentWorkload(rs.getInt("current_workload"));
        u.setLastHeartbeatAt(rs.getString("last_heartbeat_at"));
        u.setCreatedAt(rs.getString("created_at"));
        return u;
    }
}

