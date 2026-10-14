package com.incidentreport.common.model;

public class Report {
    private String id;
    private String type;
    private String priority;
    private String description;
    private String location;
    private String status;
    private String reporterUsername;
    private String handlerUsername;
    private long createdAt;
    
    public Report(String id, String type, String priority, String description, String location, String status, String reporterUsername) {
        this.id = id;
        this.type = type;
        this.priority = priority;
        this.description = description;
        this.location = location;
        this.status = status;
        this.reporterUsername = reporterUsername;
        this.createdAt = System.currentTimeMillis();
    }
    
    // Getters
    public String getId() { return id; }
    public String getType() { return type; }
    public String getPriority() { return priority; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public String getStatus() { return status; }
    public String getReporterUsername() { return reporterUsername; }
    public String getHandlerUsername() { return handlerUsername; }
    public long getCreatedAt() { return createdAt; }
    
    // Setters
    public void setHandlerUsername(String handlerUsername) { this.handlerUsername = handlerUsername; }
    public void setStatus(String status) { this.status = status; }
}