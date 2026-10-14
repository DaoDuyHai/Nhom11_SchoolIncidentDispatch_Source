package com.incidentreport.common.model;

public class DispatchLog {
    private int logId;
    private int ticketId;
    private int staffId;
    private String staffName;
    private int attemptNumber;
    private int workloadAtDispatch;
    private double totalDispatchScore;
    private String dispatchStatus;
    private String pushedAt;
    private String respondedAt;
    private String failoverTriggeredAt;
    private Integer recoveryTimeMs;
    private String note;

    public DispatchLog() {}

    public int getLogId() { return logId; }
    public void setLogId(int logId) { this.logId = logId; }

    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public int getStaffId() { return staffId; }
    public void setStaffId(int staffId) { this.staffId = staffId; }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }

    public int getWorkloadAtDispatch() { return workloadAtDispatch; }
    public void setWorkloadAtDispatch(int workloadAtDispatch) { this.workloadAtDispatch = workloadAtDispatch; }

    public double getTotalDispatchScore() { return totalDispatchScore; }
    public void setTotalDispatchScore(double totalDispatchScore) { this.totalDispatchScore = totalDispatchScore; }

    public String getDispatchStatus() { return dispatchStatus; }
    public void setDispatchStatus(String dispatchStatus) { this.dispatchStatus = dispatchStatus; }

    public String getPushedAt() { return pushedAt; }
    public void setPushedAt(String pushedAt) { this.pushedAt = pushedAt; }

    public String getRespondedAt() { return respondedAt; }
    public void setRespondedAt(String respondedAt) { this.respondedAt = respondedAt; }

    public String getFailoverTriggeredAt() { return failoverTriggeredAt; }
    public void setFailoverTriggeredAt(String failoverTriggeredAt) { this.failoverTriggeredAt = failoverTriggeredAt; }

    public Integer getRecoveryTimeMs() { return recoveryTimeMs; }
    public void setRecoveryTimeMs(Integer recoveryTimeMs) { this.recoveryTimeMs = recoveryTimeMs; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}

