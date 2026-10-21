package com.incidentreport.common.model;

/**
 * Model đại diện cho 1 tin nhắn trao đổi trong phòng Chat:
 * - ticketId != null && ticketId > 0: Tin nhắn trong phòng chat của sự cố cụ thể
 * - ticketId == null || ticketId == 0: Tin nhắn trong Kênh Chat chung toàn trường
 */
public class ChatMessage {
    private int messageId;
    private Integer ticketId;
    private int senderId;
    private String senderName;
    private String senderRole; // REPORTER, RESPONDER, ADMIN
    private String content;
    private String sentAt;

    public ChatMessage() {}

    public ChatMessage(Integer ticketId, int senderId, String senderName, String senderRole, String content) {
        this.ticketId = ticketId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderRole = senderRole;
        this.content = content;
    }

    public ChatMessage(int messageId, Integer ticketId, int senderId, String senderName, String senderRole, String content, String sentAt) {
        this.messageId = messageId;
        this.ticketId = ticketId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderRole = senderRole;
        this.content = content;
        this.sentAt = sentAt;
    }

    public int getMessageId() { return messageId; }
    public void setMessageId(int messageId) { this.messageId = messageId; }

    public Integer getTicketId() { return ticketId; }
    public void setTicketId(Integer ticketId) { this.ticketId = ticketId; }

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

    public boolean isTicketChat() {
        return ticketId != null && ticketId > 0;
    }

    @Override
    public String toString() {
        return String.format("ChatMessage[id=%d, ticket=%s, sender=%s (%s), text='%s', at=%s]",
            messageId, ticketId, senderName, senderRole,
            content != null && content.length() > 30 ? content.substring(0, 30) + "..." : content,
            sentAt);
    }
}
