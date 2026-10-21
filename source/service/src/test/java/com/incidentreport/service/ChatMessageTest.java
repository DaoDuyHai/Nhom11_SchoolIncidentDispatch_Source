package com.incidentreport.service;

import com.incidentreport.common.model.ChatMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChatMessageTest {

    @BeforeAll
    public static void setup() {
        ChatMessageDAO.ensureTableExists();
    }

    @Test
    public void testSaveAndGetGeneralMessages() {
        ChatMessage msg = new ChatMessage(
            null, 1, "Thầy Hiệu Trưởng", "ADMIN", "Thông báo thử nghiệm kênh chung"
        );
        boolean saved = ChatMessageDAO.saveMessage(msg);
        assertTrue(saved, "Tin nhắn kênh chung phải được lưu thành công");
        assertTrue(msg.getMessageId() > 0, "Phải sinh được messageId tự tăng");
        assertNotNull(msg.getSentAt(), "Phải có sent_at");

        List<ChatMessage> generalList = ChatMessageDAO.getGeneralMessages(10);
        assertNotNull(generalList);
        assertFalse(generalList.isEmpty(), "Danh sách tin nhắn kênh chung không được rỗng");
    }

    @Test
    public void testSaveAndGetTicketMessages() {
        int testTicketId = 1;
        ChatMessage msg1 = new ChatMessage(
            testTicketId, 2, "Cô Lan", "REPORTER", "Phòng học mất điện đột ngột"
        );
        ChatMessage msg2 = new ChatMessage(
            testTicketId, 4, "Anh Tuấn", "RESPONDER", "Đã tiếp nhận, đang kiểm tra"
        );

        assertTrue(ChatMessageDAO.saveMessage(msg1));
        assertTrue(ChatMessageDAO.saveMessage(msg2));

        List<ChatMessage> ticketMsgs = ChatMessageDAO.getMessagesByTicket(testTicketId);
        assertNotNull(ticketMsgs);
        assertTrue(ticketMsgs.size() >= 2, "Phải lấy được ít nhất 2 tin nhắn của ticket");
        boolean foundMsg1 = ticketMsgs.stream().anyMatch(m -> m.getMessageId() == msg1.getMessageId() && "Cô Lan".equals(m.getSenderName()));
        boolean foundMsg2 = ticketMsgs.stream().anyMatch(m -> m.getMessageId() == msg2.getMessageId() && "Anh Tuấn".equals(m.getSenderName()));
        assertTrue(foundMsg1, "Phải tìm thấy msg1 vừa lưu");
        assertTrue(foundMsg2, "Phải tìm thấy msg2 vừa lưu");
    }
}

