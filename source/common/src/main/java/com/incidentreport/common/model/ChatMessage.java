package com.incidentreport.common.model;

/**
 * Model đại diện cho 1 tin nhắn trao đổi 1-1 giữa Reporter và Responder trong sự cố
 */
public class ChatMessage {
    private int messageId;
    private int ticketId;
    private int senderId;
    private String senderName;
    private String senderRole; // REPORTER, RESPONDER, ADMIN
    private String content;
    private String sentAt;

    public ChatMessage() {}

    public ChatMessage(int ticketId, int senderId, String senderName, String senderRole, String content) {
        this.ticketId = ticketId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderRole = senderRole;
        this.content = content;
    }

    public int getMessageId() { return messageId; }
    public void setMessageId(int messageId) { this.messageId = messageId; }

    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public int getSenderId() { return senderId; }
    public void setSenderId(int senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSentAt() { return sentAt; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }
}
