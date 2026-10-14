package com.incidentreport.service;

import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentCategory;
import com.incidentreport.common.model.IncidentTicket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử Đơn vị & Tích hợp: Deduplication và Atomic Claim trong Service")
public class DeduplicationAndClaimTest {

    @BeforeAll
    static void setup() {
        // Khởi tạo và nạp cache danh mục
        CategoryDAO.refreshCache();
    }

    @Test
    @DisplayName("Kiểm tra nạp danh mục sự cố từ CSDL")
    void testCategoryLoading() {
        IncidentCategory electric = CategoryDAO.getCategory("ELECTRIC");
        assertNotNull(electric, "Danh mục ELECTRIC phải tồn tại trong CSDL");
        assertEquals("Sự cố Điện nguy hiểm (Chập cầu dao, Rò điện)", electric.getCategoryName());
        assertEquals("TECHNICAL", electric.getRequiredSpecialty());
        assertEquals(300, electric.getDedupWindowSeconds());
    }

    @Test
    @DisplayName("Kiểm tra Spatio-Temporal Deduplication: Báo cáo cùng phòng và danh mục trong 5 phút sẽ được gom nhóm")
    void testSpatioTemporalDeduplication() {
        String testRoom = "UNIT_ROOM_" + UUID.randomUUID().toString().substring(0, 8);
        String category = "FACILITY";

        // Báo cáo lần 1 -> Phải tạo Ticket mới
        ReportService.SubmitResult res1 = ReportService.submitIncident(
            3, testRoom, category, 3, "Hỏng bóng đèn lần 1", null, 0, "127.0.0.1"
        );
        assertTrue(res1.success);
        assertFalse(res1.isMerged, "Lần đầu tiên gửi không được coi là trùng lặp");
        assertNotNull(res1.ticket);
        int initialTicketId = res1.ticket.getTicketId();
        assertEquals(1, res1.ticket.getReportCount());

        // Báo cáo lần 2 cùng phòng, cùng category -> Phải được gom nhóm vào Ticket cũ
        ReportService.SubmitResult res2 = ReportService.submitIncident(
            3, testRoom, category, 4, "Hỏng bóng đèn lần 2 từ học sinh khác", null, 0, "127.0.0.1"
        );
        assertTrue(res2.success);
        assertTrue(res2.isMerged, "Lần gửi thứ 2 trong cửa sổ 5 phút phải được gom nhóm");
        assertEquals(initialTicketId, res2.ticket.getTicketId(), "Phải gộp vào đúng ticketId ban đầu");
        assertEquals(2, res2.ticket.getReportCount(), "Số lượng báo cáo phải tăng lên 2");
        assertEquals(4, res2.ticket.getBaseSeverity(), "Độ khẩn cấp phải được nâng lên mức cao hơn (4)");
    }

    @Test
    @DisplayName("Kiểm tra Atomic Claim: Chống Race Condition khi 2 nhân sự cùng tranh chấp nhận 1 sự cố")
    void testAtomicClaimRaceCondition() {
        String testRoom = "UNIT_RACE_" + UUID.randomUUID().toString().substring(0, 8);
        ReportService.SubmitResult res = ReportService.submitIncident(
            3, testRoom, "ELECTRIC", 4, "Cháy quạt trần", null, 0, "127.0.0.1"
        );
        assertTrue(res.success);
        int ticketId = res.ticket.getTicketId();

        int staff1 = 4; // kt_tuan
        int staff2 = 5; // kt_nam

        // Staff 1 nhận trước
        boolean claim1 = TicketDAO.claimTicketAtomic(ticketId, staff1);
        assertTrue(claim1, "Nhân viên 1 phải nhận ca thành công");

        // Staff 2 nhận sau ngay lập tức
        boolean claim2 = TicketDAO.claimTicketAtomic(ticketId, staff2);
        assertFalse(claim2, "Nhân viên 2 phải bị từ chối vì sự cố không còn ở trạng thái PENDING");

        // Kiểm tra trạng thái cuối cùng của Ticket
        IncidentTicket ticket = TicketDAO.getTicketById(ticketId);
        assertNotNull(ticket);
        assertEquals(Constants.STATUS_ASSIGNED, ticket.getStatus());
        assertEquals(staff1, ticket.getAssignedStaffId());
    }
}
