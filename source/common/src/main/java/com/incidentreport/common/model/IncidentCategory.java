package com.incidentreport.common.model;

/**
 * Danh mục sự cố và định tuyến phòng ban chuyên trách
 */
public class IncidentCategory {
    private String categoryCode;
    private String categoryName;
    private String requiredSpecialty; // TECHNICAL, MEDICAL, SECURITY, ALL

    public IncidentCategory() {}

    public IncidentCategory(String categoryCode, String categoryName, String requiredSpecialty) {
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.requiredSpecialty = requiredSpecialty;
    }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getRequiredSpecialty() { return requiredSpecialty; }
    public void setRequiredSpecialty(String requiredSpecialty) { this.requiredSpecialty = requiredSpecialty; }

    public String getTargetDepartment() { return requiredSpecialty; }
    public void setTargetDepartment(String targetDepartment) { this.requiredSpecialty = targetDepartment; }

    @Override
    public String toString() {
        return categoryName;
    }
}
