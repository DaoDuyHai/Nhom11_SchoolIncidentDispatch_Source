package com.incidentreport.service;

import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class TicketDAO {

    /**
     * Spatio-Temporal Deduplication: Tìm ticket chưa hoàn thành tại cùng phòng và cùng loại
     * trong khoảng thời gian dedup_window_seconds
     */
    public static IncidentTicket findActiveTicketForDedup(String roomLocation, String categoryCode, int windowSeconds) {
        String sql = "SELECT t.*, c.category_name, u.full_name as staff_name FROM incident_tickets t " +
                     "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
                     "LEFT JOIN users u ON t.assigned_staff_id = u.user_id " +
                     "WHERE t.room_location = ? AND t.category_code = ? " +
                     "AND t.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS') " +
                     "AND (STRFTIME('%s', 'NOW', 'localtime') - STRFTIME('%s', t.last_merged_at)) <= ? " +
                     "ORDER BY t.ticket_id DESC LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, roomLocation.trim().toUpperCase());
            pstmt.setString(2, categoryCode);
            pstmt.setInt(3, windowSeconds);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapTicket(rs);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi findActiveTicketForDedup: " + e.getMessage());
        }
        return null;
    }

    /**
     * Gộp báo cáo trùng: Tăng report_count, cộng điểm ưu tiên, cập nhật mốc thời gian
     */
    public static boolean mergeIntoExistingTicket(int ticketId, int reportedSeverity) {
        String sql = "UPDATE incident_tickets SET " +
                     "report_count = report_count + 1, " +
                     "computed_priority = computed_priority + 0.5, " +
                     "base_severity = MAX(base_severity, ?), " +
                     "last_merged_at = STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime') " +
                     "WHERE ticket_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, reportedSeverity);
            pstmt.setInt(2, ticketId);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("❌ Lỗi mergeIntoExistingTicket: " + e.getMessage());
            return false;
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger seqCounter =
        new java.util.concurrent.atomic.AtomicInteger((int) (System.currentTimeMillis() % 10000));

    /**
     * Tạo Ticket mới
     */
    public static int createTicket(IncidentTicket ticket) {
        String datePrefix = new SimpleDateFormat("yyyyMMdd").format(new Date());
        int seq = seqCounter.incrementAndGet();
        String salt = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String ticketCode = String.format("INC-%s-%04d-%s", datePrefix, seq, salt);
        ticket.setTicketCode(ticketCode);

        try (Connection conn = DatabaseConnection.getConnection()) {
            String insertSql = "INSERT INTO incident_tickets (" +
                "ticket_code, room_location, category_code, title, base_severity, " +
                "report_count, computed_priority, status, version_lock" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";

            try (PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, ticket.getTicketCode());
                pstmt.setString(2, ticket.getRoomLocation().trim().toUpperCase());
                pstmt.setString(3, ticket.getCategoryCode());
                pstmt.setString(4, ticket.getTitle());
                pstmt.setInt(5, ticket.getBaseSeverity());
                pstmt.setInt(6, 1);
                pstmt.setDouble(7, (double) ticket.getBaseSeverity());
                pstmt.setString(8, Constants.STATUS_PENDING);

                int affected = pstmt.executeUpdate();
                if (affected > 0) {
                    try (ResultSet gk = pstmt.getGeneratedKeys()) {
                        if (gk.next()) {
                            ticket.setTicketId(gk.getInt(1));
                            return ticket.getTicketId();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi createTicket: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Nhận ca độc quyền (Atomic Claim with Optimistic Locking)
     */
    public static boolean claimTicketAtomic(int ticketId, int staffId) {
        String sql = "UPDATE incident_tickets SET " +
                     "status = ?, assigned_staff_id = ?, " +
                     "accepted_at = STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime'), " +
                     "version_lock = version_lock + 1 " +
                     "WHERE ticket_id = ? AND (assigned_staff_id IS NULL OR assigned_staff_id = ?) AND status = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, Constants.STATUS_ASSIGNED);
            pstmt.setInt(2, staffId);
            pstmt.setInt(3, ticketId);
            pstmt.setInt(4, staffId);
            pstmt.setString(5, Constants.STATUS_PENDING);

            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("❌ Lỗi claimTicketAtomic: " + e.getMessage());
            return false;
        }
    }

    /**
     * Giao nhiệm vụ / Chỉ định thủ công bởi Ban Giám Hiệu (Admin)
     */
    public static boolean assignTicketManual(int ticketId, int staffId) {
        String sql = "UPDATE incident_tickets SET " +
                     "status = ?, assigned_staff_id = ?, " +
                     "accepted_at = STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime'), " +
                     "version_lock = version_lock + 1 " +
                     "WHERE ticket_id = ? AND status IN ('PENDING', 'ASSIGNED')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, Constants.STATUS_ASSIGNED);
            pstmt.setInt(2, staffId);
            pstmt.setInt(3, ticketId);

            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("❌ Lỗi assignTicketManual: " + e.getMessage());
            return false;
        }
    }

    public static boolean updateTicketStatus(int ticketId, int staffId, String newStatus, String resolutionNote) {
        StringBuilder sql = new StringBuilder("UPDATE incident_tickets SET status = ?, version_lock = version_lock + 1 ");
        if (Constants.STATUS_RESOLVED.equals(newStatus)) {
            sql.append(", resolved_at = STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime'), resolution_note = ? ");
        }
        sql.append("WHERE ticket_id = ? AND assigned_staff_id = ?");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            pstmt.setString(1, newStatus);
            int idx = 2;
            if (Constants.STATUS_RESOLVED.equals(newStatus)) {
                pstmt.setString(idx++, resolutionNote != null ? resolutionNote : "Đã xử lý xong");
            }
            pstmt.setInt(idx++, ticketId);
            pstmt.setInt(idx++, staffId);

            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("❌ Lỗi updateTicketStatus: " + e.getMessage());
            return false;
        }
    }

    public static boolean resetTicketToPendingForFailover(int ticketId) {
        String sql = "UPDATE incident_tickets SET " +
                     "status = 'PENDING', assigned_staff_id = NULL, " +
                     "failover_count = failover_count + 1, " +
                     "computed_priority = computed_priority + 2.0 " + // Tăng độ ưu tiên khi bị failover
                     "WHERE ticket_id = ? AND status IN ('ASSIGNED', 'IN_PROGRESS')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, ticketId);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("❌ Lỗi resetTicketToPendingForFailover: " + e.getMessage());
            return false;
        }
    }

    public static IncidentTicket getTicketById(int ticketId) {
        String sql = "SELECT t.*, c.category_name, u.full_name as staff_name FROM incident_tickets t " +
                     "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
                     "LEFT JOIN users u ON t.assigned_staff_id = u.user_id " +
                     "WHERE t.ticket_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, ticketId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapTicket(rs);
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getTicketById: " + e.getMessage());
        }
        return null;
    }

    public static List<IncidentTicket> getPendingTickets() {
        List<IncidentTicket> list = new ArrayList<>();
        String sql = "SELECT t.*, c.category_name, u.full_name as staff_name FROM incident_tickets t " +
                     "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
                     "LEFT JOIN users u ON t.assigned_staff_id = u.user_id " +
                     "WHERE t.status = 'PENDING' ORDER BY t.computed_priority DESC, t.created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapTicket(rs));
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getPendingTickets: " + e.getMessage());
        }
        return list;
    }

    public static List<IncidentTicket> getTicketsByReporter(int reporterId) {
        List<IncidentTicket> list = new ArrayList<>();
        String sql = "SELECT DISTINCT t.*, c.category_name, u.full_name as staff_name FROM incident_tickets t " +
                     "JOIN incident_reports r ON t.ticket_id = r.ticket_id " +
                     "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
                     "LEFT JOIN users u ON t.assigned_staff_id = u.user_id " +
                     "WHERE r.reporter_id = ? ORDER BY t.ticket_id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, reporterId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTicket(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getTicketsByReporter: " + e.getMessage());
        }
        return list;
    }

    public static List<IncidentTicket> getTicketsByStaff(int staffId) {
        List<IncidentTicket> list = new ArrayList<>();
        String sql = "SELECT t.*, c.category_name, u.full_name as staff_name FROM incident_tickets t " +
                     "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
                     "LEFT JOIN users u ON t.assigned_staff_id = u.user_id " +
                     "WHERE t.assigned_staff_id = ? ORDER BY CASE t.status WHEN 'IN_PROGRESS' THEN 1 WHEN 'ASSIGNED' THEN 2 ELSE 3 END, t.base_severity DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, staffId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTicket(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getTicketsByStaff: " + e.getMessage());
        }
        return list;
    }

    public static List<IncidentTicket> getAllTickets() {
        List<IncidentTicket> list = new ArrayList<>();
        String sql = "SELECT t.*, c.category_name, u.full_name as staff_name FROM incident_tickets t " +
                     "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
                     "LEFT JOIN users u ON t.assigned_staff_id = u.user_id " +
                     "ORDER BY t.ticket_id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapTicket(rs));
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getAllTickets: " + e.getMessage());
        }
        return list;
    }

    private static IncidentTicket mapTicket(ResultSet rs) throws Exception {
        IncidentTicket t = new IncidentTicket();
        t.setTicketId(rs.getInt("ticket_id"));
        t.setTicketCode(rs.getString("ticket_code"));
        t.setRoomLocation(rs.getString("room_location"));
        t.setCategoryCode(rs.getString("category_code"));
        t.setCategoryName(rs.getString("category_name"));
        t.setTitle(rs.getString("title"));
        t.setBaseSeverity(rs.getInt("base_severity"));
        t.setReportCount(rs.getInt("report_count"));
        t.setComputedPriority(rs.getDouble("computed_priority"));
        t.setStatus(rs.getString("status"));
        int staffId = rs.getInt("assigned_staff_id");
        if (!rs.wasNull()) {
            t.setAssignedStaffId(staffId);
        }
        t.setAssignedStaffName(rs.getString("staff_name"));
        t.setVersionLock(rs.getInt("version_lock"));
        t.setCreatedAt(rs.getString("created_at"));
        t.setLastMergedAt(rs.getString("last_merged_at"));
        t.setDispatchedAt(rs.getString("dispatched_at"));
        t.setAcceptedAt(rs.getString("accepted_at"));
        t.setResolvedAt(rs.getString("resolved_at"));
        t.setQueueWaitingTimeMs(rs.getLong("queue_waiting_time_ms"));
        t.setFailoverCount(rs.getInt("failover_count"));
        t.setResolutionNote(rs.getString("resolution_note"));
        return t;
    }
}
