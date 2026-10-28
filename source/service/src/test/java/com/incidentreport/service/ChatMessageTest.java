package com.incidentreport.service;

import com.incidentreport.common.model.ChatMessage;
import com.incidentreport.common.model.IncidentTicket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChatMessageTest {

    private static int validTicketId = 0;

    @BeforeAll
    public static void setup() {
        InitDatabase.resetDatabase();
        ChatMessageDAO.ensureTableExists();

        // Tạo 1 ticket hợp lệ trước để thỏa mãn Foreign Key constraint
        IncidentTicket ticket = new IncidentTicket();
        ticket.setRoomLocation("A201");
        ticket.setCategoryCode("ELECTRIC");
        ticket.setTargetDepartment("TECHNICAL");
        ticket.setTitle("Chập điện ổ cắm");
        ticket.setDescription("Phòng học mất điện đột ngột");
        ticket.setReporterId(3); // gv_lan
        ticket.setReporterName("Cô Trần Thị Lan");
        boolean created = TicketDAO.createTicket(ticket);
        assertTrue(created, "Phải tạo được ticket mẫu trước khi chat");
        validTicketId = ticket.getTicketId();
    }

    @Test
    public void testSaveAndGetTicketMessages() {
        ChatMessage msg1 = new ChatMessage(
            validTicketId, 3, "Cô Lan", "REPORTER", "Phòng học mất điện đột ngột"
        );
        ChatMessage msg2 = new ChatMessage(
            validTicketId, 27, "Anh Tuấn", "RESPONDER", "Đã tiếp nhận, đang kiểm tra"
        );

        assertTrue(ChatMessageDAO.saveMessage(msg1));
        assertTrue(ChatMessageDAO.saveMessage(msg2));

        List<ChatMessage> ticketMsgs = ChatMessageDAO.getMessagesByTicket(validTicketId);
        assertNotNull(ticketMsgs);
        assertTrue(ticketMsgs.size() >= 2, "Phải lấy được ít nhất 2 tin nhắn của ticket");
        boolean foundMsg1 = ticketMsgs.stream().anyMatch(m -> m.getMessageId() == msg1.getMessageId() && "Cô Lan".equals(m.getSenderName()));
        boolean foundMsg2 = ticketMsgs.stream().anyMatch(m -> m.getMessageId() == msg2.getMessageId() && "Anh Tuấn".equals(m.getSenderName()));
        assertTrue(foundMsg1, "Phải tìm thấy msg1 vừa lưu");
        assertTrue(foundMsg2, "Phải tìm thấy msg2 vừa lưu");
    }

    @Test
    public void testGetAllMessagesForAudit() {
        // Đảm bảo có ít nhất 1 tin nhắn để kiểm toán
        ChatMessage auditMsg = new ChatMessage(
            validTicketId, 3, "Cô Lan", "REPORTER", "Tin nhắn kiểm toán phục vụ Admin"
        );
        ChatMessageDAO.saveMessage(auditMsg);

        List<ChatMessage> all = ChatMessageDAO.getAllMessages(50);
        assertNotNull(all);
        assertFalse(all.isEmpty(), "Admin phải xem được toàn bộ lịch sử tin nhắn để kiểm toán");
    }
}
