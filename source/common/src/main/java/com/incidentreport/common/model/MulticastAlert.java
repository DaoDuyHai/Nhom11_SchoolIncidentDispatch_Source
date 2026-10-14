package com.incidentreport.common.model;

public class MulticastAlert {
    private int alertId;
    private int senderAdminId;
    private String senderAdminName;
    private Integer relatedTicketId;
    private String multicastGroup;
    private String alertLevel;
    private String alertMessage;
    private String broadcastedAt;

    public MulticastAlert() {
        this.multicastGroup = "230.0.0.1:9999";
        this.alertLevel = "CRITICAL";
    }

    public int getAlertId() { return alertId; }
    public void setAlertId(int alertId) { this.alertId = alertId; }

    public int getSenderAdminId() { return senderAdminId; }
    public void setSenderAdminId(int senderAdminId) { this.senderAdminId = senderAdminId; }

    public String getSenderAdminName() { return senderAdminName; }
    public void setSenderAdminName(String senderAdminName) { this.senderAdminName = senderAdminName; }

    public Integer getRelatedTicketId() { return relatedTicketId; }
    public void setRelatedTicketId(Integer relatedTicketId) { this.relatedTicketId = relatedTicketId; }

    public String getMulticastGroup() { return multicastGroup; }
    public void setMulticastGroup(String multicastGroup) { this.multicastGroup = multicastGroup; }

    public String getAlertLevel() { return alertLevel; }
    public void setAlertLevel(String alertLevel) { this.alertLevel = alertLevel; }

    public String getAlertMessage() { return alertMessage; }
    public void setAlertMessage(String alertMessage) { this.alertMessage = alertMessage; }

    public String getBroadcastedAt() { return broadcastedAt; }
    public void setBroadcastedAt(String broadcastedAt) { this.broadcastedAt = broadcastedAt; }
}

