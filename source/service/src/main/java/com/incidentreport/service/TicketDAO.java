package com.incidentreport.service;

import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentCategory;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.StatisticReport;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tầng truy xuất dữ liệu Sự cố (Incident Ticket DAO)
 */
public class TicketDAO {

    private static final AtomicInteger seqCounter =
        new AtomicInteger((int) (System.currentTimeMillis() % 10000));

    public static boolean createTicket(IncidentTicket ticket) {
        if (ticket.getTicketCode() == null || ticket.getTicketCode().isEmpty()) {
            String datePrefix = new SimpleDateFormat("yyyyMMdd").format(new Date());
            int seq = seqCounter.incrementAndGet();
            String salt = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            ticket.setTicketCode(String.format("INC-%s-%04d-%s", datePrefix, seq, salt));
        }

        IncidentCategory cat = CategoryDAO.getCategory(ticket.getCategoryCode());
        if (cat != null) {
            ticket.setCategoryName(cat.getCategoryName());
            if (ticket.getTargetDepartment() == null || ticket.getTargetDepartment().isEmpty()) {
                ticket.setTargetDepartment(cat.getTargetDepartment());
            }
        }

        String sql = "INSERT INTO incident_tickets (" +
            "ticket_code, room_location, category_code, target_department, title, description, image_file_path, " +
            "reporter_id, reporter_name, reporter_phone, status" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, ticket.getTicketCode());
            pstmt.setString(2, ticket.getRoomLocation() != null ? ticket.getRoomLocation().trim().toUpperCase() : "");
            pstmt.setString(3, ticket.getCategoryCode() != null ? ticket.getCategoryCode() : "FACILITY");
            pstmt.setString(4, ticket.getTargetDepartment() != null ? ticket.getTargetDepartment() : Constants.SPECIALTY_TECHNICAL);
            pstmt.setString(5, ticket.getTitle() != null ? ticket.getTitle() : (ticket.getRoomLocation() + " - " + ticket.getCategoryCode()));
            pstmt.setString(6, ticket.getDescription() != null ? ticket.getDescription() : "");
            pstmt.setString(7, ticket.getImageFilePath());
            pstmt.setInt(8, ticket.getReporterId());
            pstmt.setString(9, ticket.getReporterName() != null ? ticket.getReporterName() : ("Người dùng #" + ticket.getReporterId()));
            pstmt.setString(10, ticket.getReporterPhone() != null ? ticket.getReporterPhone() : "");
            pstmt.setString(11, Constants.STATUS_PENDING);

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet gk = pstmt.getGeneratedKeys()) {
                    if (gk.next()) {
                        ticket.setTicketId(gk.getInt(1));
                    }
                }
                return true;
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi TicketDAO.createTicket: " + e.getMessage());
        }
        return false;
    }

    public static boolean claimTicket(int ticketId, int staffId, String staffName) {
        return claimTicket(ticketId, staffId, staffName, null);
    }

    public static boolean claimTicket(int ticketId, int staffId, String staffName, String requiredSpecialty) {
        boolean checkDept = requiredSpecialty != null && !requiredSpecialty.trim().isEmpty() && !"ALL".equalsIgnoreCase(requiredSpecialty);
        String sql = "UPDATE incident_tickets SET " +
                     "status = ?, assigned_staff_id = ?, assigned_staff_name = ?, " +
                     "accepted_at = STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime') " +
                     "WHERE ticket_id = ? AND status = ?" + (checkDept ? " AND target_department = ?" : "");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, Constants.STATUS_IN_PROGRESS);
            pstmt.setInt(2, staffId);
            pstmt.setString(3, staffName);
            pstmt.setInt(4, ticketId);
            pstmt.setString(5, Constants.STATUS_PENDING);
            if (checkDept) {
                pstmt.setString(6, requiredSpecialty.trim());
            }

            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("❌ Lỗi TicketDAO.claimTicket: " + e.getMessage());
        }
        return false;
    }

    public static boolean manualAssign(int ticketId, int staffId, String staffName, String note) {
        String sql = "UPDATE incident_tickets SET " +
                     "status = ?, assigned_staff_id = ?, assigned_staff_name = ?, " +
                     "accepted_at = STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime'), " +
                     "resolution_note = ? " +
                     "WHERE ticket_id = ? AND status IN ('PENDING', 'IN_PROGRESS')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, Constants.STATUS_IN_PROGRESS);
            pstmt.setInt(2, staffId);
            pstmt.setString(3, staffName);
            pstmt.setString(4, note != null ? note : "Admin phân công trực tiếp");
            pstmt.setInt(5, ticketId);

            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("❌ Lỗi manualAssign: " + e.getMessage());
            return false;
        }
    }

    public static boolean assignTicket(int ticketId, int staffId, String staffName, String note) {
        return manualAssign(ticketId, staffId, staffName, note);
    }

    public static boolean updateStatus(int ticketId, String newStatus, String resolutionNote) {
        StringBuilder sql = new StringBuilder("UPDATE incident_tickets SET status = ? ");
        if (Constants.STATUS_RESOLVED.equals(newStatus)) {
            sql.append(", resolved_at = STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW', 'localtime'), resolution_note = ? ");
        } else if (resolutionNote != null && !resolutionNote.isEmpty()) {
            sql.append(", resolution_note = ? ");
        }
        // Khóa bảo vệ trạng thái kết thúc: Vé đã RESOLVED hoặc CANCELLED không thể bị ghi đè!
        sql.append("WHERE ticket_id = ? AND status NOT IN ('")
           .append(Constants.STATUS_RESOLVED).append("', '")
           .append(Constants.STATUS_CANCELLED).append("')");

        // Ràng buộc hủy vé: Chỉ cho phép hủy khi vé đang PENDING (chưa được tiếp nhận/xử lý)
        if (Constants.STATUS_CANCELLED.equals(newStatus)) {
            sql.append(" AND status = '").append(Constants.STATUS_PENDING).append("'");
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            pstmt.setString(1, newStatus);
            int idx = 2;
            if (Constants.STATUS_RESOLVED.equals(newStatus) || (resolutionNote != null && !resolutionNote.isEmpty())) {
                pstmt.setString(idx++, resolutionNote != null ? resolutionNote : "Đã xử lý xong");
            }
            pstmt.setInt(idx, ticketId);

            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("❌ Lỗi updateStatus: " + e.getMessage());
            return false;
        }
    }

    public static boolean updateStatus(int ticketId, int staffId, String newStatus, String resolutionNote) {
        return updateStatus(ticketId, newStatus, resolutionNote);
    }

    public static IncidentTicket getTicketById(int ticketId) {
        String sql = "SELECT t.*, c.category_name FROM incident_tickets t " +
                     "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
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

    public static List<IncidentTicket> getPendingTicketsBySpecialty(String specialty) {
        List<IncidentTicket> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT t.*, c.category_name FROM incident_tickets t " +
            "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
            "WHERE t.status = 'PENDING' "
        );
        if (specialty != null && !"ALL".equalsIgnoreCase(specialty)) {
            sql.append("AND (UPPER(t.target_department) = UPPER(?) OR UPPER(t.target_department) = 'ALL') ");
        }
        sql.append("ORDER BY t.ticket_id ASC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            if (specialty != null && !"ALL".equalsIgnoreCase(specialty)) {
                pstmt.setString(1, specialty);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTicket(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getPendingTicketsBySpecialty: " + e.getMessage());
        }
        return list;
    }

    public static List<IncidentTicket> getTicketsByDepartment(String department) {
        List<IncidentTicket> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT t.*, c.category_name FROM incident_tickets t " +
            "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
            "WHERE (UPPER(t.target_department) = UPPER(?) OR UPPER(?) = 'ALL' OR UPPER(t.target_department) = 'ALL') " +
            "ORDER BY CASE t.status WHEN 'PENDING' THEN 1 WHEN 'IN_PROGRESS' THEN 2 ELSE 3 END, t.ticket_id DESC"
        );

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            pstmt.setString(1, department != null ? department : "TECHNICAL");
            pstmt.setString(2, department != null ? department : "TECHNICAL");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTicket(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getTicketsByDepartment: " + e.getMessage());
        }
        return list;
    }

    public static List<IncidentTicket> getTicketsByReporter(int reporterId) {
        List<IncidentTicket> list = new ArrayList<>();
        String sql = "SELECT t.*, c.category_name FROM incident_tickets t " +
                     "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
                     "WHERE t.reporter_id = ? ORDER BY t.ticket_id DESC";
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
        String sql = "SELECT t.*, c.category_name FROM incident_tickets t " +
                     "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
                     "WHERE t.assigned_staff_id = ? ORDER BY CASE t.status WHEN 'IN_PROGRESS' THEN 1 ELSE 2 END, t.ticket_id DESC";
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
        String sql = "SELECT t.*, c.category_name FROM incident_tickets t " +
                     "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
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

    /**
     * Thống kê đa chiều theo ngày, tháng, quý, năm cho Admin
     */
    public static StatisticReport getStatistics(String timeRange) {
        StatisticReport report = new StatisticReport();
        report.setTimeRange(timeRange != null ? timeRange.toUpperCase() : "ALL");

        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        if ("DAY".equalsIgnoreCase(timeRange)) {
            whereClause.append(" AND DATE(created_at) = DATE('NOW', 'localtime') ");
        } else if ("MONTH".equalsIgnoreCase(timeRange)) {
            whereClause.append(" AND STRFTIME('%Y-%m', created_at) = STRFTIME('%Y-%m', 'NOW', 'localtime') ");
        } else if ("QUARTER".equalsIgnoreCase(timeRange)) {
            whereClause.append(" AND created_at >= DATETIME('NOW', 'localtime', '-3 months') ");
        } else if ("YEAR".equalsIgnoreCase(timeRange)) {
            whereClause.append(" AND STRFTIME('%Y', created_at) = STRFTIME('%Y', 'NOW', 'localtime') ");
        }

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Tổng quan số lượng
            String countSql = "SELECT " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending, " +
                "SUM(CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress, " +
                "SUM(CASE WHEN status = 'RESOLVED' THEN 1 ELSE 0 END) AS resolved, " +
                "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled " +
                "FROM incident_tickets " + whereClause.toString();

            try (ResultSet rs = stmt.executeQuery(countSql)) {
                if (rs.next()) {
                    report.setTotalIncidents(rs.getInt("total"));
                    report.setPendingCount(rs.getInt("pending"));
                    report.setInProgressCount(rs.getInt("in_progress"));
                    report.setResolvedCount(rs.getInt("resolved"));
                    report.setCancelledCount(rs.getInt("cancelled"));
                }
            }

            // 2. Phân loại theo phòng ban (TECHNICAL, MEDICAL, SECURITY)
            String deptSql = "SELECT COALESCE(target_department, 'TECHNICAL') AS dept, COUNT(*) AS cnt " +
                "FROM incident_tickets " + whereClause.toString() + " GROUP BY target_department";
            try (ResultSet rs = stmt.executeQuery(deptSql)) {
                Map<String, Integer> deptMap = new HashMap<>();
                while (rs.next()) {
                    deptMap.put(rs.getString("dept"), rs.getInt("cnt"));
                }
                report.setIncidentsByDepartment(deptMap);
            }

            // 3. Xếp hạng phòng học / vị trí xảy ra nhiều sự cố nhất
            String roomSql = "SELECT room_location, COUNT(*) AS cnt FROM incident_tickets " +
                whereClause.toString() + " GROUP BY room_location ORDER BY cnt DESC LIMIT 10";
            try (ResultSet rs = stmt.executeQuery(roomSql)) {
                List<Map<String, Object>> topRooms = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("room", rs.getString("room_location"));
                    r.put("count", rs.getInt("cnt"));
                    topRooms.add(r);
                }
                report.setTopIncidentRooms(topRooms);
            }

            // 4. Danh sách sự cố chi tiết trong kỳ báo cáo phục vụ xuất file CSV chính xác
            String ticketsSql = "SELECT t.*, c.category_name FROM incident_tickets t " +
                "LEFT JOIN incident_categories c ON t.category_code = c.category_code " +
                whereClause.toString() + " ORDER BY t.ticket_id DESC";
            try (ResultSet rs = stmt.executeQuery(ticketsSql)) {
                List<IncidentTicket> ticketList = new ArrayList<>();
                while (rs.next()) {
                    ticketList.add(mapTicket(rs));
                }
                report.setTicketList(ticketList);
            }

        } catch (Exception e) {
            System.err.println("❌ Lỗi getStatistics: " + e.getMessage());
        }

        return report;
    }

    private static IncidentTicket mapTicket(ResultSet rs) throws SQLException {
        IncidentTicket t = new IncidentTicket();
        t.setTicketId(rs.getInt("ticket_id"));
        t.setTicketCode(rs.getString("ticket_code"));
        t.setRoomLocation(rs.getString("room_location"));
        t.setCategoryCode(rs.getString("category_code"));
        try {
            t.setCategoryName(rs.getString("category_name"));
        } catch (Exception ignored) {}
        try {
            t.setTargetDepartment(rs.getString("target_department"));
        } catch (Exception ignored) {}
        t.setTitle(rs.getString("title"));
        t.setDescription(rs.getString("description"));
        t.setImageFilePath(rs.getString("image_file_path"));
        t.setReporterId(rs.getInt("reporter_id"));
        t.setReporterName(rs.getString("reporter_name"));
        try {
            t.setReporterPhone(rs.getString("reporter_phone"));
        } catch (Exception ignored) {}
        t.setStatus(rs.getString("status"));

        int staffId = rs.getInt("assigned_staff_id");
        if (!rs.wasNull() && staffId > 0) {
            t.setAssignedStaffId(staffId);
        }
        t.setAssignedStaffName(rs.getString("assigned_staff_name"));
        t.setCreatedAt(rs.getString("created_at"));
        t.setAcceptedAt(rs.getString("accepted_at"));
        t.setResolvedAt(rs.getString("resolved_at"));
        t.setResolutionNote(rs.getString("resolution_note"));
        return t;
    }
}
