package com.incidentreport.service;

import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentCategory;
import com.incidentreport.common.model.IncidentReport;
import com.incidentreport.common.model.IncidentTicket;

public class ReportService {

    public static class SubmitResult {
        public boolean success;
        public boolean isMerged;
        public IncidentTicket ticket;
        public String message;

        public SubmitResult(boolean success, boolean isMerged, IncidentTicket ticket, String message) {
            this.success = success;
            this.isMerged = isMerged;
            this.ticket = ticket;
            this.message = message;
        }
    }

    /**
     * Nghiệp vụ báo cáo sự cố kết hợp Spatio-Temporal Deduplication:
     * 1. Tìm xem phòng này + loại sự cố này đã có ai báo trong windowSeconds chưa.
     * 2. Nếu có -> Gộp vào ticket cũ, tăng report_count, tăng điểm ưu tiên.
     * 3. Nếu chưa -> Tạo ticket mới PENDING.
     * 4. Lưu bản ghi incident_reports (kèm đường dẫn ảnh nếu có).
     */
    public static SubmitResult submitIncident(
            int reporterId,
            String roomLocation,
            String categoryCode,
            int severity,
            String description,
            String imageFilePath,
            int imageSizeBytes,
            String clientIp) {

        IncidentCategory category = CategoryDAO.getCategory(categoryCode);
        if (category == null) {
            return new SubmitResult(false, false, null, "Mã danh mục không hợp lệ: " + categoryCode);
        }

        int windowSeconds = category.getDedupWindowSeconds() > 0 ? category.getDedupWindowSeconds() : 300;

        // 1. Kiểm tra Spatio-Temporal Deduplication
        IncidentTicket existingTicket = TicketDAO.findActiveTicketForDedup(roomLocation, categoryCode, windowSeconds);

        if (existingTicket != null) {
            // Gộp vào ticket có sẵn
            boolean merged = TicketDAO.mergeIntoExistingTicket(existingTicket.getTicketId(), severity);
            if (merged) {
                IncidentReport report = new IncidentReport();
                report.setTicketId(existingTicket.getTicketId());
                report.setReporterId(reporterId);
                report.setReportedSeverity(severity);
                report.setDescription(description);
                report.setImageFilePath(imageFilePath);
                report.setImageSizeBytes(imageSizeBytes);
                report.setIsDuplicateMerged(1);
                report.setClientIp(clientIp);
                ReportDAO.saveReport(report);

                // Lấy ticket mới nhất sau khi gộp
                IncidentTicket updated = TicketDAO.getTicketById(existingTicket.getTicketId());
                return new SubmitResult(true, true, updated, "Báo cáo của bạn đã được gộp vào sự cố " + updated.getTicketCode());
            }
        }

        // 2. Tạo mới Ticket
        IncidentTicket newTicket = new IncidentTicket();
        newTicket.setRoomLocation(roomLocation);
        newTicket.setCategoryCode(categoryCode);
        newTicket.setCategoryName(category.getCategoryName());
        newTicket.setTitle(category.getCategoryName() + " tại " + roomLocation);
        newTicket.setBaseSeverity(severity);

        int ticketId = TicketDAO.createTicket(newTicket);
        if (ticketId > 0) {
            IncidentReport report = new IncidentReport();
            report.setTicketId(ticketId);
            report.setReporterId(reporterId);
            report.setReportedSeverity(severity);
            report.setDescription(description);
            report.setImageFilePath(imageFilePath);
            report.setImageSizeBytes(imageSizeBytes);
            report.setIsDuplicateMerged(0);
            report.setClientIp(clientIp);
            ReportDAO.saveReport(report);

            IncidentTicket created = TicketDAO.getTicketById(ticketId);
            return new SubmitResult(true, false, created, "Đã tạo sự cố mới: " + created.getTicketCode());
        }

        return new SubmitResult(false, false, null, "Không thể lưu sự cố vào CSDL!");
    }
}