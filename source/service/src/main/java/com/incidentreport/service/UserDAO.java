package com.incidentreport.service;

import com.incidentreport.common.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Tầng truy xuất dữ liệu người dùng (Admin là người duy nhất cấp tài khoản)
 */
public class UserDAO {

    public static final String SYSTEM_SALT = "SIDS_SALT_2026_SECURE_#$!";

    public static String hashWithSalt(String input) {
        if (input == null) return "";
        return hashSHA256(input + ":" + SYSTEM_SALT);
    }

    public static String hashSHA256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private static boolean isSHA256Hash(String s) {
        return s != null && s.length() == 64 && s.matches("[0-9a-f]+");
    }

    public static User login(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String stored = rs.getString("password_hash");
                    boolean matched = false;

                    if (stored.equalsIgnoreCase(hashWithSalt(password))) {
                        matched = true;
                    } else if (isSHA256Hash(stored) && stored.equalsIgnoreCase(hashSHA256(password))) {
                        matched = true;
                        // Tự động nâng cấp sang SHA-256 có Salt
                        stored = hashWithSalt(password);
                        updatePassword(rs.getInt("user_id"), stored);
                    } else if (stored.equals(password)) {
                        matched = true;
                        stored = hashWithSalt(password);
                        updatePassword(rs.getInt("user_id"), stored);
                    }

                    if (matched) {
                        User user = mapUser(rs);
                        user.setPasswordHash(stored);
                        setOnlineStatus(user.getUserId(), 1);
                        user.setIsOnline(1);
                        return user;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi UserDAO.login: " + e.getMessage());
        }
        return null;
    }

    public static int createUser(String username, String password, String fullName, String phone, String role, String specialty) {
        String sql = "INSERT INTO users (username, password_hash, full_name, phone, role, specialty, is_online) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 0)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashWithSalt(password));
            pstmt.setString(3, fullName);
            pstmt.setString(4, phone != null ? phone : "");
            pstmt.setString(5, role);
            pstmt.setString(6, specialty != null ? specialty : "NONE");

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi UserDAO.createUser: " + e.getMessage());
        }
        return -1;
    }

    public static boolean createUser(User user) {
        if (user == null) return false;
        int id = createUser(user.getUsername(), user.getPasswordHash(), user.getFullName(), user.getPhone(), user.getRole(), user.getSpecialty());
        if (id > 0) {
            user.setUserId(id);
            return true;
        }
        return false;
    }

    public static boolean usernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi UserDAO.usernameExists: " + e.getMessage());
        }
        return false;
    }

    public static void setOnlineStatus(int userId, int isOnline) {
        String sql = "UPDATE users SET is_online = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, isOnline);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("❌ Lỗi setOnlineStatus: " + e.getMessage());
        }
    }

    public static void updateUserOnlineStatus(int userId, boolean isOnline) {
        setOnlineStatus(userId, isOnline ? 1 : 0);
    }

    public static void resetAllUsersOffline() {
        String sql = "UPDATE users SET is_online = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("❌ Lỗi resetAllUsersOffline: " + e.getMessage());
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

    public static List<User> getUsersBySpecialty(String specialty) {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = 'RESPONDER' AND (specialty = ? OR ? = 'ALL' OR specialty = 'ALL') ORDER BY full_name ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, specialty);
            pstmt.setString(2, specialty);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapUser(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getUsersBySpecialty: " + e.getMessage());
        }
        return list;
    }

    public static boolean updatePassword(int userId, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPasswordHash);
            pstmt.setInt(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("❌ Lỗi updatePassword: " + e.getMessage());
            return false;
        }
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));
        u.setRole(rs.getString("role"));
        u.setSpecialty(rs.getString("specialty"));
        u.setPhone(rs.getString("phone"));
        u.setIsOnline(rs.getInt("is_online"));
        u.setCreatedAt(rs.getString("created_at"));
        return u;
    }
}
