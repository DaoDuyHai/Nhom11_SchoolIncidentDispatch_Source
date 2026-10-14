package com.incidentreport.common.model;

public class IncidentCategory {
    private String categoryCode;
    private String categoryName;
    private String requiredSpecialty;
    private int defaultSeverity;
    private int dedupWindowSeconds;

    public IncidentCategory() {}

    public IncidentCategory(String categoryCode, String categoryName, String requiredSpecialty, int defaultSeverity, int dedupWindowSeconds) {
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.requiredSpecialty = requiredSpecialty;
        this.defaultSeverity = defaultSeverity;
        this.dedupWindowSeconds = dedupWindowSeconds;
    }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getRequiredSpecialty() { return requiredSpecialty; }
    public void setRequiredSpecialty(String requiredSpecialty) { this.requiredSpecialty = requiredSpecialty; }

    public int getDefaultSeverity() { return defaultSeverity; }
    public void setDefaultSeverity(int defaultSeverity) { this.defaultSeverity = defaultSeverity; }

    public int getDedupWindowSeconds() { return dedupWindowSeconds; }
    public void setDedupWindowSeconds(int dedupWindowSeconds) { this.dedupWindowSeconds = dedupWindowSeconds; }

    @Override
    public String toString() {
        return categoryName;
    }
}

