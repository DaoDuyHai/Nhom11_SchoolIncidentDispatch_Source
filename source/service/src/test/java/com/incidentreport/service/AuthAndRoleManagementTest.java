package com.incidentreport.service;

import com.incidentreport.common.Constants;
import com.incidentreport.common.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử Nghiệp vụ Xác thực, Phân quyền và Tạo nhân sự")
public class AuthAndRoleManagementTest {

    @Test
    @DisplayName("1. Đăng ký tài khoản công cộng (REPORTER) cho Học sinh và Giáo viên")
    void testPublicRegistration() {
        String rand = UUID.randomUUID().toString().substring(0, 6);
        String studentUser = "hs_" + rand;
        String teacherUser = "gv_" + rand;

        // Đăng ký Học sinh
        AuthService.AuthResult res1 = AuthService.register(
            studentUser, "Pass@123", "Học sinh " + rand, "0901112233", Constants.SUB_ROLE_STUDENT
        );
        assertTrue(res1.success, "Đăng ký học sinh phải thành công: " + res1.message);
        assertNotNull(res1.user);
        assertEquals(Constants.ROLE_REPORTER, res1.user.getRole());
        assertEquals(Constants.SUB_ROLE_STUDENT, res1.user.getSubRole());
        assertEquals(Constants.SPECIALTY_NONE, res1.user.getSpecialty());

        // Đăng nhập lại với mật khẩu plaintext
        User loginUser = AuthService.login(studentUser, "Pass@123");
        assertNotNull(loginUser, "Phải đăng nhập thành công với mật khẩu đúng");
        assertNull(AuthService.login(studentUser, "SaiMatKhau"), "Đăng nhập sai mật khẩu phải trả về null");

        // Đăng ký trùng username phải bị từ chối
        AuthService.AuthResult dup = AuthService.register(
            studentUser, "Pass@456", "Trùng Tên", "0900000000", Constants.SUB_ROLE_STUDENT
        );
        assertFalse(dup.success, "Đăng ký trùng tên đăng nhập phải bị chặn");

        // Đăng ký Giáo viên
        AuthService.AuthResult res2 = AuthService.register(
            teacherUser, "Pass@456", "Cô giáo " + rand, "0912345678", Constants.SUB_ROLE_TEACHER
        );
        assertTrue(res2.success);
        assertEquals(Constants.ROLE_REPORTER, res2.user.getRole());
        assertEquals(Constants.SUB_ROLE_TEACHER, res2.user.getSubRole());
    }

    @Test
    @DisplayName("2. Chế độ đăng nhập Khách (Guest Mode)")
    void testGuestLogin() {
        AuthService.AuthResult guestRes = AuthService.guestLogin();
        assertTrue(guestRes.success, "Guest login phải thành công: " + guestRes.message);
        assertNotNull(guestRes.user);

        User guest = guestRes.user;
        assertTrue(guest.getUsername().startsWith("guest_"));
        assertEquals(Constants.ROLE_REPORTER, guest.getRole());
        assertEquals(Constants.SUB_ROLE_GUEST, guest.getSubRole());
        assertEquals(Constants.SPECIALTY_NONE, guest.getSpecialty());
        assertEquals(1, guest.getIsOnline());
    }

    @Test
    @DisplayName("3. Quản lý & Tạo tài khoản nhân sự từ phía Admin (Phân quyền nghiêm ngặt)")
    void testAdminPersonnelCreation() {
        // Tìm tài khoản Admin có sẵn trong CSDL (admin_bgh hoặc role ADMIN)
        User admin = UserDAO.getUserByUsername("admin_bgh");
        if (admin == null) {
            for (User u : UserDAO.getAllUsers()) {
                if (Constants.ROLE_ADMIN.equalsIgnoreCase(u.getRole())) {
                    admin = u;
                    break;
                }
            }
        }
        assertNotNull(admin, "Tài khoản admin hệ thống phải có sẵn");
        assertEquals(Constants.ROLE_ADMIN, admin.getRole());

        String rand = UUID.randomUUID().toString().substring(0, 6);

        // 3.1 Admin tạo kỹ thuật viên (TECHNICAL -> TECH_STAFF)
        String techUser = "tech_" + rand;
        AuthService.AuthResult techRes = AuthService.createUserByAdmin(
            admin.getUserId(), techUser, "TechPass1", "Kỹ thuật " + rand, "0988111222",
            Constants.ROLE_RESPONDER, "", Constants.SPECIALTY_TECHNICAL
        );
        assertTrue(techRes.success, "Admin tạo kỹ thuật viên phải thành công");
        assertEquals(Constants.ROLE_RESPONDER, techRes.user.getRole());
        assertEquals(Constants.SPECIALTY_TECHNICAL, techRes.user.getSpecialty());
        assertEquals(Constants.SUB_ROLE_TECH_STAFF, techRes.user.getSubRole());

        // 3.2 Admin tạo nhân viên Y tế (MEDICAL -> MEDICAL_STAFF)
        String medUser = "med_" + rand;
        AuthService.AuthResult medRes = AuthService.createUserByAdmin(
            admin.getUserId(), medUser, "MedPass1", "Bác sĩ " + rand, "0988333444",
            Constants.ROLE_RESPONDER, "", Constants.SPECIALTY_MEDICAL
        );
        assertTrue(medRes.success);
        assertEquals(Constants.SPECIALTY_MEDICAL, medRes.user.getSpecialty());
        assertEquals(Constants.SUB_ROLE_MEDICAL_STAFF, medRes.user.getSubRole());

        // 3.3 Admin tạo Bảo vệ (SECURITY -> SECURITY_GUARD)
        String secUser = "sec_" + rand;
        AuthService.AuthResult secRes = AuthService.createUserByAdmin(
            admin.getUserId(), secUser, "SecPass1", "Bảo vệ " + rand, "0988555666",
            Constants.ROLE_RESPONDER, "", Constants.SPECIALTY_SECURITY
        );
        assertTrue(secRes.success);
        assertEquals(Constants.SPECIALTY_SECURITY, secRes.user.getSpecialty());
        assertEquals(Constants.SUB_ROLE_SECURITY_GUARD, secRes.user.getSubRole());

        // 3.4 Admin tạo Phó Hiệu trưởng / Admin mới
        String newAdminUser = "admin_" + rand;
        AuthService.AuthResult newAdminRes = AuthService.createUserByAdmin(
            admin.getUserId(), newAdminUser, "AdminPass1", "Phó HT " + rand, "0988777888",
            Constants.ROLE_ADMIN, Constants.SUB_ROLE_PRINCIPAL, ""
        );
        assertTrue(newAdminRes.success);
        assertEquals(Constants.ROLE_ADMIN, newAdminRes.user.getRole());
        assertEquals(Constants.SUB_ROLE_PRINCIPAL, newAdminRes.user.getSubRole());

        // 3.5 Người dùng không phải Admin thử tạo tài khoản -> Bị từ chối
        AuthService.AuthResult failRes = AuthService.createUserByAdmin(
            techRes.user.getUserId(), "hacker_" + rand, "pass", "Hacker", "",
            Constants.ROLE_ADMIN, "", ""
        );
        assertFalse(failRes.success, "Người không phải Admin tuyệt đối không được phép tạo nhân sự");
    }

    @Test
    @DisplayName("4. Đổi mật khẩu sau khi xác thực (Reset Password Service)")
    void testResetPassword() {
        String rand = UUID.randomUUID().toString().substring(0, 6);
        String username = "reset_user_" + rand;
        AuthService.register(username, "OldPass@123", "User Reset", "0909090909", Constants.SUB_ROLE_STUDENT);

        // Reset mật khẩu mới
        boolean ok = AuthService.resetPassword(username, "NewPass@456");
        assertTrue(ok, "Reset password phải thành công");

        // Đăng nhập bằng mật khẩu cũ phải thất bại
        assertNull(AuthService.login(username, "OldPass@123"));

        // Đăng nhập bằng mật khẩu mới phải thành công
        User updated = AuthService.login(username, "NewPass@456");
        assertNotNull(updated);
        assertEquals(username, updated.getUsername());
    }
}
