package com.incidentreport.service;

import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.StatisticReport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TicketRoutingAndStatisticsTest {

    @BeforeAll
    public static void setup() {
        InitDatabase.init();
        CategoryDAO.refreshCache();
    }

    @Test
    public void testCreateAndRouteTicket() {
        IncidentTicket ticket = new IncidentTicket();
        ticket.setRoomLocation("B204");
        ticket.setCategoryCode("ELECTRIC");
        ticket.setTargetDepartment("TECHNICAL");
        ticket.setDescription("Chập điện ổ cắm");
        ticket.setReporterId(2);
        ticket.setReporterName("Cô Lan");

        boolean created = TicketDAO.createTicket(ticket);
        assertTrue(created, "Tạo ticket phải thành công");
        assertTrue(ticket.getTicketId() > 0, "Ticket ID phải hợp lệ");
        assertEquals("TECHNICAL", ticket.getTargetDepartment(), "Sự cố điện phải điều phối về TECHNICAL");

        // Kiểm tra danh sách chờ của phòng Kỹ thuật
        List<IncidentTicket> techPending = TicketDAO.getPendingTicketsBySpecialty("TECHNICAL");
        assertNotNull(techPending);
        boolean found = techPending.stream().anyMatch(t -> t.getTicketId() == ticket.getTicketId());
        assertTrue(found, "Ticket mới tạo phải nằm trong hàng đợi chờ xử lý của phòng Kỹ thuật");
    }

    @Test
    public void testClaimAndStatusUpdate() {
        IncidentTicket ticket = new IncidentTicket();
        ticket.setRoomLocation("C301");
        ticket.setCategoryCode("MEDICAL");
        ticket.setTargetDepartment("MEDICAL");
        ticket.setDescription("Học sinh bị chóng mặt");
        ticket.setReporterId(3);
        ticket.setReporterName("Học sinh Minh");

        assertTrue(TicketDAO.createTicket(ticket));

        // Tiếp nhận (Claim)
        boolean claimed = TicketDAO.claimTicket(ticket.getTicketId(), 6, "Y tá Mai");
        assertTrue(claimed, "Y tá Mai phải tiếp nhận thành công");

        // Tiếp nhận lại lần 2 phải bị từ chối
        boolean claimAgain = TicketDAO.claimTicket(ticket.getTicketId(), 4, "Anh Tuấn");
        assertFalse(claimAgain, "Không được phép tiếp nhận sự cố đã có người xử lý");

        // Cập nhật trạng thái sang RESOLVED
        boolean updated = TicketDAO.updateStatus(ticket.getTicketId(), Constants.STATUS_RESOLVED, "Đã cho học sinh uống nước đường và nghỉ ngơi");
        assertTrue(updated, "Cập nhật hoàn tất sự cố phải thành công");

        IncidentTicket fetched = TicketDAO.getTicketById(ticket.getTicketId());
        assertEquals(Constants.STATUS_RESOLVED, fetched.getStatus());
        assertEquals("Y tá Mai", fetched.getAssignedStaffName());
    }

    @Test
    public void testStatisticsMultiPeriod() {
        StatisticReport allStats = TicketDAO.getStatistics("ALL");
        assertNotNull(allStats);
        assertTrue(allStats.getTotalTickets() > 0, "Phải có tổng số sự cố");
        assertNotNull(allStats.getCountByDepartment(), "Phải có phân loại theo phòng ban");

        StatisticReport monthStats = TicketDAO.getStatistics("MONTH");
        assertNotNull(monthStats);
        assertEquals("MONTH", monthStats.getTimeRange());
    }
}

