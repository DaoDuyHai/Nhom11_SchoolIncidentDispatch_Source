package com.incidentreport.service;

import com.incidentreport.common.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    // =========================================================================
    // SHA-256 Password Hashing
    // =========================================================================

    /**
     * Bam mat khau bang SHA-256. Tra ve chuoi hex 64 ky tu.
     */
    public static String hashSHA256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            System.err.println("Loi hashSHA256: " + e.getMessage());
            return input;
        }
    }

    /**
     * Kiem tra xem chuoi co phai SHA-256 hash khong (64 ky tu hex).
     */
    private static boolean isSHA256Hash(String s) {
        return s != null && s.length() == 64 && s.matches("[0-9a-f]+");
    }

    // =========================================================================
    // Authentication
    // =========================================================================

    /**
     * Dang nhap voi ho tro ca plaintext (backward compat) va SHA-256 hash.
     * Neu login thanh cong bang plaintext -> tu dong migrate sang hash.
     */
    public static User login(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String stored = rs.getString("password_hash");
                    boolean matched = false;

                    if (isSHA256Hash(stored)) {
                        // Mat khau da duoc hash: so sanh voi SHA-256 cua input
                        matched = stored.equals(hashSHA256(password));
                    } else {
                        // Backward compat: mat khau plaintext
                        if (stored.equals(password)) {
                            matched = true;
                            // Tu dong migrate sang SHA-256
                            int uid = rs.getInt("user_id");
                            migratePasswordToHash(uid, password);
                        }
                    }

                    if (matched) {
                        User user = mapUser(rs);
                        setOnlineStatus(user.getUserId(), 1, "AVAILABLE");
                        user.setIsOnline(1);
                        user.setAvailabilityStatus("AVAILABLE");
                        return user;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Loi UserDAO.login: " + e.getMessage());
        }
        return null;
    }

    private static void migratePasswordToHash(int userId, String plainPassword) {
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, hashSHA256(plainPassword));
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
            System.out.println("[AUTH] Migrated password to SHA-256 for userId=" + userId);
        } catch (Exception e) {
            System.err.println("Loi migratePasswordToHash: " + e.getMessage());
        }
    }

    // =========================================================================
    // Registration & Account Creation
    // =========================================================================

    /**
     * Dang ky tai khoan cong cong: Chi tao duoc REPORTER (STUDENT hoac TEACHER).
     * @return userId moi neu thanh cong, -1 neu that bai.
     */
    public static int registerUser(String username, String passwordHash, String fullName, String phone, String subRole) {
        String sql = "INSERT INTO users (username, password_hash, full_name, role, sub_role, specialty, phone, is_online, availability_status, current_workload) " +
                     "VALUES (?, ?, ?, 'REPORTER', ?, 'NONE', ?, 0, 'OFFLINE', 0)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            pstmt.setString(3, fullName);
            pstmt.setString(4, subRole);
            pstmt.setString(5, phone);
            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) return generatedKeys.getInt(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Loi UserDAO.registerUser: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Admin tao tai khoan: Co the tao RESPONDER hoac ADMIN.
     * @return userId moi neu thanh cong, -1 neu that bai.
     */
    public static int createUserByAdmin(String username, String passwordHash, String fullName, String phone,
                                        String role, String subRole, String specialty) {
        String sql = "INSERT INTO users (username, password_hash, full_name, role, sub_role, specialty, phone, is_online, availability_status, current_workload) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'OFFLINE', 0)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            pstmt.setString(3, fullName);
            pstmt.setString(4, role);
            pstmt.setString(5, subRole);
            pstmt.setString(6, specialty);
            pstmt.setString(7, phone);
            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) return generatedKeys.getInt(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Loi UserDAO.createUserByAdmin: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Kiem tra username da ton tai chua.
     */
    public static boolean usernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            System.err.println("Loi UserDAO.usernameExists: " + e.getMessage());
        }
        return false;
    }

    // =========================================================================
    // Password Reset
    // =========================================================================

    /**
     * Cap nhat mat khau moi (da hash) cho user theo userId.
     */
    public static boolean updatePassword(int userId, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPasswordHash);
            pstmt.setInt(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("Loi UserDAO.updatePassword: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cap nhat mat khau moi (da hash) cho user theo username.
     */
    public static boolean updatePasswordByUsername(String username, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPasswordHash);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("Loi UserDAO.updatePasswordByUsername: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // Online Status & Heartbeat
    // =========================================================================

    public static void setOnlineStatus(int userId, int isOnline, String availability) {
        String sql = "UPDATE users SET is_online = ?, availability_status = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, isOnline);
            pstmt.setString(2, availability);
            pstmt.setInt(3, userId);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("Loi setOnlineStatus: " + e.getMessage());
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
            System.err.println("Loi updateHeartbeat: " + e.getMessage());
        }
    }

    public static void resetAllUsersOffline() {
        String sql = "UPDATE users SET is_online = 0, availability_status = 'OFFLINE'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("Loi resetAllUsersOffline: " + e.getMessage());
        }
    }

    // =========================================================================
    // Queries
    // =========================================================================

    public static User getUserById(int userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapUser(rs);
            }
        } catch (Exception e) {
            System.err.println("Loi getUserById: " + e.getMessage());
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
            System.err.println("Loi getUserByUsername: " + e.getMessage());
        }
        return null;
    }

    /**
     * Smart Dispatch: Lay danh sach nhan vien thoa man chuyen mon,
     * sap xep uu tien: San sang (AVAILABLE) truoc, va Tai cong viec thap nhat!
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
            System.err.println("Loi getCandidateResponders: " + e.getMessage());
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
            System.err.println("Loi adjustWorkload: " + e.getMessage());
        }
    }

    public static List<User> getAllResponders() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = 'RESPONDER' ORDER BY specialty ASC, current_workload ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapUser(rs));
            }
        } catch (Exception e) {
            System.err.println("Loi getAllResponders: " + e.getMessage());
        }
        return list;
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
            System.err.println("Loi getAllUsers: " + e.getMessage());
        }
        return list;
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

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