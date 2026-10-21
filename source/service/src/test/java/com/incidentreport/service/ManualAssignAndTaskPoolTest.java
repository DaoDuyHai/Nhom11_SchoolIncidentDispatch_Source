package com.incidentreport.service;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.User;

@DisplayName("Kiểm thử Đơn vị & Tích hợp: Giao việc thủ công (Manual Assign) và Hồ công việc mở (Open Task Pool)")
public class ManualAssignAndTaskPoolTest {

    @BeforeAll
    static void setup() {
        CategoryDAO.refreshCache();
    }

    @Test
    @DisplayName("Kiểm tra chức năng Admin giao việc thủ công (Manual Assign) cho nhân viên kỹ thuật")
    void testManualAssignByAdmin() {
        String testRoom = "ASSIGN_ROOM_" + UUID.randomUUID().toString().substring(0, 8);
        ReportService.SubmitResult res = ReportService.submitIncident(
            3, testRoom, "ELECTRIC", 4, "Sự cố chập điện cần phân công gấp", null, 0, "127.0.0.1"
        );
        assertTrue(res.success, "Tạo sự cố phải thành công");
        assertNotNull(res.ticket);
        int ticketId = res.ticket.getTicketId();

        // 1. Kiểm tra ban đầu ở trạng thái PENDING
        IncidentTicket initialTicket = TicketDAO.getTicketById(ticketId);
        assertNotNull(initialTicket);
        assertEquals(Constants.STATUS_PENDING, initialTicket.getStatus());
        assertNull(initialTicket.getAssignedStaffId(), "Ban đầu chưa có nhân sự phụ trách");

        // 2. Admin giao việc cho Anh Hoàng Văn Tuấn (staffId = 4)
        int staff1 = 4;
        boolean assigned = TicketDAO.assignTicketManual(ticketId, staff1);
        assertTrue(assigned, "Admin giao việc cho staff 4 phải thành công");

        IncidentTicket assignedTicket = TicketDAO.getTicketById(ticketId);
        assertNotNull(assignedTicket);
        assertEquals(Constants.STATUS_ASSIGNED, assignedTicket.getStatus());
        assertEquals(staff1, assignedTicket.getAssignedStaffId());
        assertNotNull(assignedTicket.getAcceptedAt(), "Thời gian tiếp nhận phải được ghi nhận");

        // 3. Admin đổi người (Reassign) sang Anh Ngô Bảo Nam (staffId = 5)
        int staff2 = 5;
        boolean reassigned = TicketDAO.assignTicketManual(ticketId, staff2);
        assertTrue(reassigned, "Admin đổi người sang staff 5 phải thành công");

        IncidentTicket reassignedTicket = TicketDAO.getTicketById(ticketId);
        assertNotNull(reassignedTicket);
        assertEquals(Constants.STATUS_ASSIGNED, reassignedTicket.getStatus());
        assertEquals(staff2, reassignedTicket.getAssignedStaffId());
    }

    @Test
    @DisplayName("Kiểm tra truy vấn Hồ công việc mở (Open Task Pool) lấy danh sách sự cố PENDING")
    void testOpenTaskPoolQuery() {
        String testRoom = "POOL_ROOM_" + UUID.randomUUID().toString().substring(0, 8);
        ReportService.SubmitResult res = ReportService.submitIncident(
            3, testRoom, "FACILITY", 3, "Hỏng rèm cửa phòng học", null, 0, "127.0.0.1"
        );
        assertTrue(res.success);
        int ticketId = res.ticket.getTicketId();

        List<IncidentTicket> pendingList = TicketDAO.getPendingTickets();
        assertNotNull(pendingList);
        assertTrue(pendingList.stream().anyMatch(t -> t.getTicketId() == ticketId),
            "Sự cố vừa tạo phải xuất hiện trong Hồ công việc mở (Open Task Pool)");

        // Sau khi được nhận ca -> Phải biến mất khỏi Task Pool
        boolean claimed = TicketDAO.claimTicketAtomic(ticketId, 4);
        assertTrue(claimed);

        List<IncidentTicket> updatedPool = TicketDAO.getPendingTickets();
        assertFalse(updatedPool.stream().anyMatch(t -> t.getTicketId() == ticketId),
            "Sự cố đã được giao/nhận ca không được phép còn trong Hồ công việc mở");
    }

    @Test
    @DisplayName("Kiểm tra lấy danh sách toàn bộ nhân sự kỹ thuật/y tế/bảo vệ (Responders List)")
    void testGetAllResponders() {
        List<User> responders = UserDAO.getAllResponders();
        assertNotNull(responders);
        assertFalse(responders.isEmpty(), "Danh sách responders không được rỗng");

        for (User u : responders) {
            assertEquals("RESPONDER", u.getRole(), "Toàn bộ nhân sự trả về phải có role là RESPONDER");
            assertNotNull(u.getFullName());
            assertNotNull(u.getSpecialty());
        }
    }
}

