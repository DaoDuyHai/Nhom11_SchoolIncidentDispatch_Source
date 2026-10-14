package com.incidentreport.common.model;

public class IncidentTicket {
    private int ticketId;
    private String ticketCode;
    private String roomLocation;
    private String categoryCode;
    private String categoryName;
    private String title;
    private int baseSeverity;
    private int reportCount;
    private double computedPriority;
    private String status;
    private Integer assignedStaffId;
    private String assignedStaffName;
    private int versionLock;
    private String createdAt;
    private String lastMergedAt;
    private String dispatchedAt;
    private String acceptedAt;
    private String resolvedAt;
    private long queueWaitingTimeMs;
    private int failoverCount;
    private String resolutionNote;

    public IncidentTicket() {
        this.reportCount = 1;
        this.status = "PENDING";
        this.computedPriority = 1.0;
        this.versionLock = 0;
    }

    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }

    public String getRoomLocation() { return roomLocation; }
    public void setRoomLocation(String roomLocation) { this.roomLocation = roomLocation; }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getBaseSeverity() { return baseSeverity; }
    public void setBaseSeverity(int baseSeverity) { this.baseSeverity = baseSeverity; }

    public int getReportCount() { return reportCount; }
    public void setReportCount(int reportCount) { this.reportCount = reportCount; }

    public double getComputedPriority() { return computedPriority; }
    public void setComputedPriority(double computedPriority) { this.computedPriority = computedPriority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAssignedStaffId() { return assignedStaffId; }
    public void setAssignedStaffId(Integer assignedStaffId) { this.assignedStaffId = assignedStaffId; }

    public String getAssignedStaffName() { return assignedStaffName; }
    public void setAssignedStaffName(String assignedStaffName) { this.assignedStaffName = assignedStaffName; }

    public int getVersionLock() { return versionLock; }
    public void setVersionLock(int versionLock) { this.versionLock = versionLock; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getLastMergedAt() { return lastMergedAt; }
    public void setLastMergedAt(String lastMergedAt) { this.lastMergedAt = lastMergedAt; }

    public String getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(String dispatchedAt) { this.dispatchedAt = dispatchedAt; }

    public String getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(String acceptedAt) { this.acceptedAt = acceptedAt; }

    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }

    public long getQueueWaitingTimeMs() { return queueWaitingTimeMs; }
    public void setQueueWaitingTimeMs(long queueWaitingTimeMs) { this.queueWaitingTimeMs = queueWaitingTimeMs; }

    public int getFailoverCount() { return failoverCount; }
    public void setFailoverCount(int failoverCount) { this.failoverCount = failoverCount; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
}

