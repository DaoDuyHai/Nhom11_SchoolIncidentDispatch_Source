package com.incidentreport.service;

import com.incidentreport.common.model.ChatMessage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChatMessageDAO {

    static {
        ensureTableExists();
    }

    public static void ensureTableExists() {
        String sql = "CREATE TABLE IF NOT EXISTS chat_messages (\n" +
                     "    message_id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                     "    ticket_id INTEGER NULL,\n" +
                     "    sender_id INTEGER NOT NULL,\n" +
                     "    sender_name TEXT NOT NULL,\n" +
                     "    sender_role TEXT NOT NULL,\n" +
                     "    content TEXT NOT NULL,\n" +
                     "    sent_at TEXT NOT NULL DEFAULT (STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime')),\n" +
                     "    FOREIGN KEY (ticket_id) REFERENCES incident_tickets(ticket_id) ON DELETE CASCADE,\n" +
                     "    FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE RESTRICT\n" +
                     ");";
        String idxSql = "CREATE INDEX IF NOT EXISTS idx_chat_ticket ON chat_messages(ticket_id, sent_at);";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            stmt.execute(idxSql);
        } catch (Exception e) {
            System.err.println("⚠️ Lỗi ensureTableExists (chat_messages): " + e.getMessage());
        }
    }

    public static boolean saveMessage(ChatMessage message) {
        ensureTableExists();
        String sql = "INSERT INTO chat_messages (ticket_id, sender_id, sender_name, sender_role, content, sent_at) " +
                     "VALUES (?, ?, ?, ?, ?, STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime'))";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            if (message.getTicketId() != null && message.getTicketId() > 0) {
                pstmt.setInt(1, message.getTicketId());
            } else {
                pstmt.setNull(1, Types.INTEGER);
            }
            pstmt.setInt(2, message.getSenderId());
            pstmt.setString(3, message.getSenderName());
            pstmt.setString(4, message.getSenderRole());
            pstmt.setString(5, message.getContent());

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        message.setMessageId(keys.getInt(1));
                    }
                }
                // Lấy lại timestamp sent_at chính xác từ DB
                try (PreparedStatement tsStmt = conn.prepareStatement("SELECT sent_at FROM chat_messages WHERE message_id = ?")) {
                    tsStmt.setInt(1, message.getMessageId());
                    try (ResultSet rs = tsStmt.executeQuery()) {
                        if (rs.next()) {
                            message.setSentAt(rs.getString("sent_at"));
                        }
                    }
                }
                return true;
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi ChatMessageDAO.saveMessage: " + e.getMessage());
        }
        return false;
    }

    public static List<ChatMessage> getMessagesByTicket(int ticketId) {
        ensureTableExists();
        List<ChatMessage> list = new ArrayList<>();
        String sql = "SELECT * FROM chat_messages WHERE ticket_id = ? ORDER BY message_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, ticketId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapMessage(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi ChatMessageDAO.getMessagesByTicket: " + e.getMessage());
        }
        return list;
    }

    public static List<ChatMessage> getGeneralMessages(int limit) {
        ensureTableExists();
        List<ChatMessage> list = new ArrayList<>();
        String sql = "SELECT * FROM (\n" +
                     "    SELECT * FROM chat_messages WHERE ticket_id IS NULL OR ticket_id = 0 \n" +
                     "    ORDER BY message_id DESC LIMIT ?\n" +
                     ") ORDER BY message_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit > 0 ? limit : 50);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapMessage(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi ChatMessageDAO.getGeneralMessages: " + e.getMessage());
        }
        return list;
    }

    private static ChatMessage mapMessage(ResultSet rs) throws SQLException {
        ChatMessage msg = new ChatMessage();
        msg.setMessageId(rs.getInt("message_id"));
        int ticketId = rs.getInt("ticket_id");
        if (!rs.wasNull() && ticketId > 0) {
            msg.setTicketId(ticketId);
        } else {
            msg.setTicketId(null);
        }
        msg.setSenderId(rs.getInt("sender_id"));
        msg.setSenderName(rs.getString("sender_name"));
        msg.setSenderRole(rs.getString("sender_role"));
        msg.setContent(rs.getString("content"));
        msg.setSentAt(rs.getString("sent_at"));
        return msg;
    }
}
