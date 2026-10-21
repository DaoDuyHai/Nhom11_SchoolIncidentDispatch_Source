package com.incidentreport.server.queue;

import com.incidentreport.common.model.IncidentTicket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử Đơn vị: Hàng đợi ưu tiên chống đói (Aging Priority Queue)")
public class AgingPriorityQueueTest {

    @Test
    @DisplayName("Kiểm tra sự cố có Severity cao hơn được lấy ra trước")
    void testSeverityPriority() {
        AgingPriorityQueue queue = new AgingPriorityQueue();

        IncidentTicket low = new IncidentTicket();
        low.setTicketId(1);
        low.setTicketCode("INC-LOW");
        low.setBaseSeverity(2);

        IncidentTicket high = new IncidentTicket();
        high.setTicketId(2);
        high.setTicketCode("INC-HIGH");
        high.setBaseSeverity(5);

        queue.enqueue(low);
        queue.enqueue(high);

        assertEquals(2, queue.size());

        IncidentTicket firstOut = queue.poll();
        assertNotNull(firstOut);
        assertEquals("INC-HIGH", firstOut.getTicketCode(), "Sự cố có Severity 5 phải được poll ra trước Severity 2");

        IncidentTicket secondOut = queue.poll();
        assertNotNull(secondOut);
        assertEquals("INC-LOW", secondOut.getTicketCode());
    }

    @Test
    @DisplayName("Kiểm tra cơ chế Aging: Sự cố chờ lâu được tăng điểm ưu tiên")
    void testAgingMechanism() {
        AgingPriorityQueue queue = new AgingPriorityQueue();

        IncidentTicket ticket = new IncidentTicket();
        ticket.setTicketId(10);
        ticket.setBaseSeverity(3);

        queue.enqueue(ticket);
        double initialPriority = ticket.getComputedPriority();
        assertEquals(300.0, initialPriority, 0.001);

        // Chạy updateAllScores
        queue.updateAllScores();
        assertTrue(ticket.getComputedPriority() >= initialPriority, "Điểm sau aging phải lớn hơn hoặc bằng ban đầu");
    }

    @Test
    @DisplayName("Kiểm tra thu hồi/xóa ticket khỏi hàng đợi khi đã được giao (removeTicket)")
    void testRemoveTicketWhenAssigned() {
        AgingPriorityQueue queue = new AgingPriorityQueue();

        IncidentTicket t1 = new IncidentTicket();
        t1.setTicketId(101);
        t1.setTicketCode("INC-101");
        t1.setBaseSeverity(3);

        IncidentTicket t2 = new IncidentTicket();
        t2.setTicketId(102);
        t2.setTicketCode("INC-102");
        t2.setBaseSeverity(4);

        queue.enqueue(t1);
        queue.enqueue(t2);
        assertEquals(2, queue.size());
        assertTrue(queue.containsTicketId(101));

        boolean removed = queue.removeTicket(101);
        assertTrue(removed, "Phải xóa thành công ticket 101 khỏi hàng đợi");
        assertEquals(1, queue.size());
        assertFalse(queue.containsTicketId(101));
        assertTrue(queue.containsTicketId(102));
    }
}
