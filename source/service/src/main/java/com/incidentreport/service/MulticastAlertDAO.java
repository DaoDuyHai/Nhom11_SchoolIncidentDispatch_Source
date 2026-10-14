package com.incidentreport.service;

import com.incidentreport.common.model.MulticastAlert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MulticastAlertDAO {

    public static int saveAlert(MulticastAlert alert) {
        String sql = "INSERT INTO multicast_alerts (" +
                     "sender_admin_id, related_ticket_id, multicast_group, alert_level, alert_message" +
                     ") VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, alert.getSenderAdminId());
            if (alert.getRelatedTicketId() != null) {
                pstmt.setInt(2, alert.getRelatedTicketId());
            } else {
                pstmt.setNull(2, java.sql.Types.INTEGER);
            }
            pstmt.setString(3, alert.getMulticastGroup());
            pstmt.setString(4, alert.getAlertLevel());
            pstmt.setString(5, alert.getAlertMessage());

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi MulticastAlertDAO.saveAlert: " + e.getMessage());
        }
        return -1;
    }

    public static List<MulticastAlert> getRecentAlerts(int limit) {
        List<MulticastAlert> list = new ArrayList<>();
        String sql = "SELECT a.*, u.full_name as sender_name FROM multicast_alerts a " +
                     "JOIN users u ON a.sender_admin_id = u.user_id " +
                     "ORDER BY a.alert_id DESC LIMIT ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    MulticastAlert a = new MulticastAlert();
                    a.setAlertId(rs.getInt("alert_id"));
                    a.setSenderAdminId(rs.getInt("sender_admin_id"));
                    a.setSenderAdminName(rs.getString("sender_name"));
                    int rel = rs.getInt("related_ticket_id");
                    if (!rs.wasNull()) a.setRelatedTicketId(rel);
                    a.setMulticastGroup(rs.getString("multicast_group"));
                    a.setAlertLevel(rs.getString("alert_level"));
                    a.setAlertMessage(rs.getString("alert_message"));
                    a.setBroadcastedAt(rs.getString("broadcasted_at"));
                    list.add(a);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getRecentAlerts: " + e.getMessage());
        }
        return list;
    }
}

