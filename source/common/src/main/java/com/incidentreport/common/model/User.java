package com.incidentreport.common.model;

public class User {
    private int userId;
    private String username;
    private String passwordHash;
    private String fullName;
    private String role;
    private String subRole;
    private String specialty;
    private String phone;
    private int isOnline;
    private String availabilityStatus;
    private int currentWorkload;
    private String lastHeartbeatAt;
    private String createdAt;

    public User() {}

    public User(int userId, String username, String fullName, String role, String subRole, String specialty) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.subRole = subRole;
        this.specialty = specialty;
        this.availabilityStatus = "AVAILABLE";
        this.isOnline = 1;
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

    public String getSubRole() { return subRole; }
    public void setSubRole(String subRole) { this.subRole = subRole; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public int getIsOnline() { return isOnline; }
    public void setIsOnline(int isOnline) { this.isOnline = isOnline; }

    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }

    public int getCurrentWorkload() { return currentWorkload; }
    public void setCurrentWorkload(int currentWorkload) { this.currentWorkload = currentWorkload; }

    public String getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(String lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
