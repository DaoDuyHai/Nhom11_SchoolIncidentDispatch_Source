package com.incidentreport.common.model;

public class IncidentReport {
    private int reportId;
    private int ticketId;
    private int reporterId;
    private String reporterName;
    private int reportedSeverity;
    private String description;
    private String imageFilePath;
    private int imageSizeBytes;
    private int isDuplicateMerged;
    private String clientIp;
    private String submittedAt;

    public IncidentReport() {}

    public int getReportId() { return reportId; }
    public void setReportId(int reportId) { this.reportId = reportId; }

    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public int getReporterId() { return reporterId; }
    public void setReporterId(int reporterId) { this.reporterId = reporterId; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public int getReportedSeverity() { return reportedSeverity; }
    public void setReportedSeverity(int reportedSeverity) { this.reportedSeverity = reportedSeverity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageFilePath() { return imageFilePath; }
    public void setImageFilePath(String imageFilePath) { this.imageFilePath = imageFilePath; }

    public int getImageSizeBytes() { return imageSizeBytes; }
    public void setImageSizeBytes(int imageSizeBytes) { this.imageSizeBytes = imageSizeBytes; }

    public int getIsDuplicateMerged() { return isDuplicateMerged; }
    public void setIsDuplicateMerged(int isDuplicateMerged) { this.isDuplicateMerged = isDuplicateMerged; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }
}

