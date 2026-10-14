package com.incidentreport.service;

import com.incidentreport.common.model.IncidentReport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ReportDAO {

    public static int saveReport(IncidentReport report) {
        String sql = "INSERT INTO incident_reports (" +
                     "ticket_id, reporter_id, reported_severity, description, " +
                     "image_file_path, image_size_bytes, is_duplicate_merged, client_ip" +
                     ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, report.getTicketId());
            pstmt.setInt(2, report.getReporterId());
            pstmt.setInt(3, report.getReportedSeverity());
            pstmt.setString(4, report.getDescription());
            pstmt.setString(5, report.getImageFilePath());
            pstmt.setInt(6, report.getImageSizeBytes());
            pstmt.setInt(7, report.getIsDuplicateMerged());
            pstmt.setString(8, report.getClientIp());

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        report.setReportId(rs.getInt(1));
                        return report.getReportId();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi ReportDAO.saveReport: " + e.getMessage());
        }
        return -1;
    }

    public static List<IncidentReport> getReportsByTicketId(int ticketId) {
        List<IncidentReport> list = new ArrayList<>();
        String sql = "SELECT r.*, u.full_name as reporter_name FROM incident_reports r " +
                     "JOIN users u ON r.reporter_id = u.user_id " +
                     "WHERE r.ticket_id = ? ORDER BY r.report_id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, ticketId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    IncidentReport r = new IncidentReport();
                    r.setReportId(rs.getInt("report_id"));
                    r.setTicketId(rs.getInt("ticket_id"));
                    r.setReporterId(rs.getInt("reporter_id"));
                    r.setReporterName(rs.getString("reporter_name"));
                    r.setReportedSeverity(rs.getInt("reported_severity"));
                    r.setDescription(rs.getString("description"));
                    r.setImageFilePath(rs.getString("image_file_path"));
                    r.setImageSizeBytes(rs.getInt("image_size_bytes"));
                    r.setIsDuplicateMerged(rs.getInt("is_duplicate_merged"));
                    r.setClientIp(rs.getString("client_ip"));
                    r.setSubmittedAt(rs.getString("submitted_at"));
                    list.add(r);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi getReportsByTicketId: " + e.getMessage());
        }
        return list;
    }
}

