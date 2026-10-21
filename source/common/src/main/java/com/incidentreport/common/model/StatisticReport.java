package com.incidentreport.common.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO dữ liệu phục vụ thống kê sự cố theo ngày, tháng, quý, năm và xuất báo cáo CSV
 */
public class StatisticReport {
    private String timeRange; // DAY, MONTH, QUARTER, YEAR, ALL
    private int totalTickets;
    private int pendingCount;
    private int inProgressCount;
    private int resolvedCount;
    private int cancelledCount;
    private Map<String, Integer> countByDepartment = new HashMap<>();
    private Map<String, Integer> topRooms = new HashMap<>();
    private List<Map<String, Object>> topIncidentRooms = new ArrayList<>();
    private List<IncidentTicket> ticketList = new ArrayList<>();

    public StatisticReport() {}

    public String getTimeRange() { return timeRange; }
    public void setTimeRange(String timeRange) { this.timeRange = timeRange; }

    public int getTotalTickets() { return totalTickets; }
    public void setTotalTickets(int totalTickets) { this.totalTickets = totalTickets; }
    public int getTotalIncidents() { return totalTickets; }
    public void setTotalIncidents(int total) { this.totalTickets = total; }

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public int getInProgressCount() { return inProgressCount; }
    public void setInProgressCount(int inProgressCount) { this.inProgressCount = inProgressCount; }

    public int getResolvedCount() { return resolvedCount; }
    public void setResolvedCount(int resolvedCount) { this.resolvedCount = resolvedCount; }

    public int getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(int cancelledCount) { this.cancelledCount = cancelledCount; }

    public Map<String, Integer> getCountByDepartment() { return countByDepartment; }
    public void setCountByDepartment(Map<String, Integer> countByDepartment) { this.countByDepartment = countByDepartment; }
    public Map<String, Integer> getIncidentsByDepartment() { return countByDepartment; }
    public void setIncidentsByDepartment(Map<String, Integer> deptMap) { this.countByDepartment = deptMap; }

    public Map<String, Integer> getTopRooms() { return topRooms; }
    public void setTopRooms(Map<String, Integer> topRooms) { this.topRooms = topRooms; }

    public List<Map<String, Object>> getTopIncidentRooms() { return topIncidentRooms; }
    public void setTopIncidentRooms(List<Map<String, Object>> topIncidentRooms) { this.topIncidentRooms = topIncidentRooms; }

    public List<IncidentTicket> getTicketList() { return ticketList; }
    public void setTicketList(List<IncidentTicket> ticketList) { this.ticketList = ticketList; }
}

