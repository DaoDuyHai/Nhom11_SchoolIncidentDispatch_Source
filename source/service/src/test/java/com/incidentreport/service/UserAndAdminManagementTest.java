package com.incidentreport.service;

import com.incidentreport.common.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UserAndAdminManagementTest {

    @BeforeAll
    public static void setup() {
        DatabaseConnection.setCustomDbUrl("jdbc:sqlite:database/test_incident_report.db");
        InitDatabase.resetDatabase();
        UserDAO.resetAllUsersOffline();
    }

    @Test
    public void testLoginSuccess() {
        User user = UserDAO.login("admin_bgh", "123456");
        assertNotNull(user, "Đăng nhập Admin phải thành công với mật khẩu 123456");
        assertEquals("ADMIN", user.getRole());
        assertEquals(1, user.getIsOnline(), "User đăng nhập xong phải online");
    }

    @Test
    public void testLoginFailure() {
        User user = UserDAO.login("admin_bgh", "wrong_password");
        assertNull(user, "Đăng nhập sai mật khẩu phải trả về null");
    }

    @Test
    public void testAdminCreateUser() {
        String testUser = "test_staff_" + System.currentTimeMillis();
        User newUser = new User(testUser, "pass123", "Nhân Viên Mới", "RESPONDER", "TECHNICAL", "0988776655");
        boolean created = UserDAO.createUser(newUser);
        assertTrue(created, "Admin tạo người dùng phải thành công");
        assertTrue(newUser.getUserId() > 0, "ID người dùng mới phải lớn hơn 0");

        User retrieved = UserDAO.login(testUser, "pass123");
        assertNotNull(retrieved, "Người dùng mới được tạo phải đăng nhập được");
        assertEquals("Nhân Viên Mới", retrieved.getFullName());

        // Kiểm tra chống trùng lặp username
        User dupUser = new User(testUser, "pass123", "Người Dùng Trùng", "RESPONDER", "TECHNICAL", "0988776655");
        boolean dupCreated = UserDAO.createUser(dupUser);
        assertFalse(dupCreated, "Tạo tài khoản trùng username phải trả về false mà không gây lỗi crash CSDL");
    }

    @Test
    public void testGetUsersBySpecialty() {
        List<User> techStaff = UserDAO.getUsersBySpecialty("TECHNICAL");
        assertNotNull(techStaff);
        assertFalse(techStaff.isEmpty(), "Phải lấy được danh sách nhân sự phòng kỹ thuật");
    }
}

