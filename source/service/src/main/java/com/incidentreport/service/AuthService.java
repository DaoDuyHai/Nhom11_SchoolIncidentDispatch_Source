package com.incidentreport.service;

import com.incidentreport.common.Constants;
import com.incidentreport.common.model.User;

import java.util.UUID;

public class AuthService {

    public static class AuthResult {
        public boolean success;
        public User user;
        public String message;

        public AuthResult(boolean success, User user, String message) {
            this.success = success;
            this.user = user;
            this.message = message;
        }
    }

    /**
     * Dang nhap tai khoan he thong.
     */
    public static User login(String username, String password) {
        return UserDAO.login(username, password);
    }

    /**
     * Dang ky tai khoan cong cong: Chi cho phep tao REPORTER (STUDENT hoac TEACHER).
     */
    public static AuthResult register(String username, String password, String fullName, String phone, String subRole) {
        if (username == null || username.trim().isEmpty()) {
            return new AuthResult(false, null, "Tên đăng nhập không được để trống");
        }
        if (password == null || password.trim().isEmpty()) {
            return new AuthResult(false, null, "Mật khẩu không được để trống");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            return new AuthResult(false, null, "Họ và tên không được để trống");
        }

        String uname = username.trim();
        if (UserDAO.usernameExists(uname)) {
            return new AuthResult(false, null, "Tên đăng nhập '" + uname + "' đã được sử dụng");
        }

        // Validate subRole: Chi cho phep STUDENT hoac TEACHER
        String normalizedSubRole = Constants.SUB_ROLE_STUDENT;
        if (Constants.SUB_ROLE_TEACHER.equalsIgnoreCase(subRole)) {
            normalizedSubRole = Constants.SUB_ROLE_TEACHER;
        }

        String hash = UserDAO.hashSHA256(password);
        int userId = UserDAO.registerUser(uname, hash, fullName.trim(), phone != null ? phone.trim() : "", normalizedSubRole);
        if (userId > 0) {
            User user = UserDAO.getUserById(userId);
            return new AuthResult(true, user, "Đăng ký tài khoản thành công");
        } else {
            return new AuthResult(false, null, "Lỗi tạo tài khoản trong cơ sở dữ liệu");
        }
    }

    /**
     * Dang nhap khach vang lai (GUEST MODE) de bao cao nhanh khong can tao tai khoan truoc.
     */
    public static AuthResult guestLogin() {
        long ts = System.currentTimeMillis();
        int rand = (int) (Math.random() * 9000) + 1000;
        String guestUsername = "guest_" + (ts % 1000000) + "_" + rand;
        String guestName = "Khách vãng lai (" + rand + ")";
        String randomPass = UUID.randomUUID().toString();
        String hash = UserDAO.hashSHA256(randomPass);

        int userId = UserDAO.registerUser(guestUsername, hash, guestName, "", Constants.SUB_ROLE_GUEST);
        if (userId > 0) {
            User user = UserDAO.getUserById(userId);
            UserDAO.setOnlineStatus(userId, 1, Constants.AVAIL_AVAILABLE);
            user.setIsOnline(1);
            user.setAvailabilityStatus(Constants.AVAIL_AVAILABLE);
            return new AuthResult(true, user, "Đăng nhập Khách thành công");
        }
        return new AuthResult(false, null, "Không thể khởi tạo phiên khách vãng lai");
    }

    /**
     * Admin tao tai khoan nhan su: Chi ADMIN moi co quyen tao RESPONDER hoac ADMIN.
     */
    public static AuthResult createUserByAdmin(int adminUserId, String username, String password,
                                              String fullName, String phone, String role,
                                              String subRole, String specialty) {
        User admin = UserDAO.getUserById(adminUserId);
        if (admin == null || !Constants.ROLE_ADMIN.equalsIgnoreCase(admin.getRole())) {
            return new AuthResult(false, null, "Từ chối quyền: Chỉ Quản trị viên (ADMIN) mới có quyền tạo tài khoản nhân sự");
        }

        if (username == null || username.trim().isEmpty()) {
            return new AuthResult(false, null, "Tên đăng nhập không được để trống");
        }
        if (password == null || password.trim().isEmpty()) {
            return new AuthResult(false, null, "Mật khẩu không được để trống");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            return new AuthResult(false, null, "Họ và tên không được để trống");
        }

        String uname = username.trim();
        if (UserDAO.usernameExists(uname)) {
            return new AuthResult(false, null, "Tên đăng nhập '" + uname + "' đã tồn tại");
        }

        String normalizedRole = Constants.ROLE_RESPONDER;
        if (Constants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            normalizedRole = Constants.ROLE_ADMIN;
        }

        String normalizedSpecialty = Constants.SPECIALTY_NONE;
        String normalizedSubRole = subRole != null ? subRole.trim().toUpperCase() : "";

        if (Constants.ROLE_RESPONDER.equals(normalizedRole)) {
            if (Constants.SPECIALTY_MEDICAL.equalsIgnoreCase(specialty)) {
                normalizedSpecialty = Constants.SPECIALTY_MEDICAL;
                if (normalizedSubRole.isEmpty()) normalizedSubRole = Constants.SUB_ROLE_MEDICAL_STAFF;
            } else if (Constants.SPECIALTY_SECURITY.equalsIgnoreCase(specialty)) {
                normalizedSpecialty = Constants.SPECIALTY_SECURITY;
                if (normalizedSubRole.isEmpty()) normalizedSubRole = Constants.SUB_ROLE_SECURITY_GUARD;
            } else {
                normalizedSpecialty = Constants.SPECIALTY_TECHNICAL;
                if (normalizedSubRole.isEmpty()) normalizedSubRole = Constants.SUB_ROLE_TECH_STAFF;
            }
        } else {
            // ADMIN
            normalizedSpecialty = Constants.SPECIALTY_NONE;
            if (normalizedSubRole.isEmpty()) normalizedSubRole = Constants.SUB_ROLE_PRINCIPAL;
        }

        String hash = UserDAO.hashSHA256(password);
        int userId = UserDAO.createUserByAdmin(
            uname, hash, fullName.trim(), phone != null ? phone.trim() : "",
            normalizedRole, normalizedSubRole, normalizedSpecialty
        );

        if (userId > 0) {
            User user = UserDAO.getUserById(userId);
            return new AuthResult(true, user, "Tạo tài khoản nhân sự thành công");
        } else {
            return new AuthResult(false, null, "Không thể lưu tài khoản vào cơ sở dữ liệu");
        }
    }

    /**
     * Khoi phuc / Dat lai mat khau cho user sau khi xac thuc OTP thanh cong.
     */
    public static boolean resetPassword(String username, String newPassword) {
        if (username == null || newPassword == null || newPassword.trim().isEmpty()) {
            return false;
        }
        String uname = username.trim();
        if (!UserDAO.usernameExists(uname)) {
            return false;
        }
        String hash = UserDAO.hashSHA256(newPassword);
        return UserDAO.updatePasswordByUsername(uname, hash);
    }
}
