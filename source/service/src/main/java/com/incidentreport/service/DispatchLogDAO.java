package com.incidentreport.service;

import com.incidentreport.common.model.DispatchLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DispatchLogDAO {

    public static int logDispatch(int ticketId, int staffId, int workload, double score, String status, String note) {
        String sql = "INSERT INTO dispatch_logs (" +
                     "ticket_id, staff_id, workload_at_dispatch, total_dispatch_score, dispatch_status, note" +
                     ") VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, ticketId);
            pstmt.setInt(2, staffId);
            pstmt.setInt(3, workload);
            pstmt.setDouble(4, score);
            pstmt.setString(5, status);
            pstmt.setString(6, note);

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi DispatchLogDAO.logDispatch: " + e.getMessage());
        }
        return -1;
    }

    public static boolean updateStatus(int ticketId, int staffId, String newStatus, String note) {
        String sql = "UPDATE dispatch_logs SET dispatch_status = ?, note = ?, " +
                     "responded_at = STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime') " +
                     "WHERE ticket_id = ? AND staff_id = ? AND dispatch_status = 'PUSHED'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus);
            pstmt.setString(2, note);
            pstmt.setInt(3, ticketId);
            pstmt.setInt(4, staffId);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("❌ Lỗi DispatchLogDAO.updateStatus: " + e.getMessage());
            return false;
        }
    }

    public static boolean recordFailover(int ticketId, int staffId, int recoveryTimeMs, String note) {
        String sql = "UPDATE dispatch_logs SET " +
                     "dispatch_status = 'HEARTBEAT_LOST', " +
                     "failover_triggered_at = STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime'), " +
                     "recovery_time_ms = ?, note = ? " +
                     "WHERE ticket_id = ? AND staff_id = ? AND dispatch_status = 'ACCEPTED'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, recoveryTimeMs);
            pstmt.setString(2, note);
            pstmt.setInt(3, ticketId);
            pstmt.setInt(4, staffId);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("❌ Lỗi DispatchLogDAO.recordFailover: " + e.getMessage());
            return false;
        }
    }

    public static List<DispatchLog> getLogsByStaff(int staffId) {
        List<DispatchLog> list = new ArrayList<>();
        String sql = "SELECT l.*, u.full_name as staff_name FROM dispatch_logs l " +
                     "JOIN users u ON l.staff_id = u.user_id " +
                     "WHERE l.staff_id = ? ORDER BY l.log_id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, staffId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    DispatchLog l = new DispatchLog();
                    l.setLogId(rs.getInt("log_id"));
                    l.setTicketId(rs.getInt("ticket_id"));
                    l.setStaffId(rs.getInt("staff_id"));
                    l.setStaffName(rs.getString("staff_name"));
                    l.setAttemptNumber(rs.getInt("attempt_number"));
                    l.setWorkloadAtDispatch(rs.getInt("workload_at_dispatch"));
                    l.setTotalDispatchScore(rs.getDouble("total_dispatch_score"));
                    l.setDispatchStatus(rs.getString("dispatch_status"));
                    l.setPushedAt(rs.getString("pushed_at"));
                    l.setRespondedAt(rs.getString("responded_at"));
                    l.setFailoverTriggeredAt(rs.getString("failover_triggered_at"));
                    int rec = rs.getInt("recovery_time_ms");
                    if (!rs.wasNull()) l.setRecoveryTimeMs(rec);
                    l.setNote(rs.getString("note"));
                    list.add(l);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getLogsByStaff: " + e.getMessage());
        }
        return list;
    }
}

