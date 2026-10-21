package com.incidentreport.common.model;

/**
 * Model đại diện cho người dùng hệ thống do Admin/Server quản lý
 */
public class User {
    private int userId;
    private String username;
    private String passwordHash;
    private String fullName;
    private String role;       // ADMIN, REPORTER, RESPONDER
    private String specialty;  // TECHNICAL, MEDICAL, SECURITY, NONE, ALL
    private String phone;
    private int isOnline;
    private String createdAt;

    public User() {}

    public User(int userId, String username, String fullName, String role, String specialty, String phone) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.specialty = specialty;
        this.phone = phone;
        this.isOnline = 0;
    }

    public User(String username, String passwordHash, String fullName, String role, String specialty, String phone) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.specialty = specialty;
        this.phone = phone;
        this.isOnline = 0;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public int getIsOnline() { return isOnline; }
    public void setIsOnline(int isOnline) { this.isOnline = isOnline; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s)", specialty, fullName, username);
    }
}
