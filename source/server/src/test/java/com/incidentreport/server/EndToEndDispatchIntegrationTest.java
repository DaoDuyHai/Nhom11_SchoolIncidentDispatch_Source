package com.incidentreport.server;

import com.google.gson.Gson;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.packet.Packet;
import com.incidentreport.service.CategoryDAO;
import com.incidentreport.service.InitDatabase;
import com.incidentreport.service.UserDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử Tích hợp Đầu-Cuối (End-to-End): Gửi báo cáo, Điều phối phòng ban, Chat 1-1 & Đóng sự cố")
public class EndToEndDispatchIntegrationTest {

    private static final int TEST_PORT = 5099;
    private static ServerSocket testServerSocket;
    private static ExecutorService threadPool;
    private static volatile boolean running = true;
    private static final Gson gson = new Gson();

    @BeforeAll
    static void startTestServer() throws Exception {
        com.incidentreport.service.DatabaseConnection.setCustomDbUrl("jdbc:sqlite:database/test_incident_report.db");
        InitDatabase.resetDatabase();
        CategoryDAO.refreshCache();
        UserDAO.resetAllUsersOffline();

        threadPool = Executors.newCachedThreadPool();
        testServerSocket = new ServerSocket(TEST_PORT);

        Thread acceptThread = new Thread(() -> {
            while (running && !testServerSocket.isClosed()) {
                try {
                    Socket sock = testServerSocket.accept();
                    threadPool.submit(new ClientHandler(sock));
                } catch (Exception ignored) {}
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
        Thread.sleep(200);
    }

    @AfterAll
    static void stopTestServer() throws Exception {
        running = false;
        if (testServerSocket != null && !testServerSocket.isClosed()) {
            testServerSocket.close();
        }
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
    }

    private static class TestClient implements AutoCloseable {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;

        public TestClient() throws Exception {
            this.socket = new Socket("127.0.0.1", TEST_PORT);
            this.socket.setSoTimeout(5000);
            this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        public void send(Packet p) throws Exception {
            p.writeTo(out);
        }

        public Packet receive() throws Exception {
            return Packet.readFrom(in);
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    @Test
    public void testFullIncidentLifecycleFlow() throws Exception {
        try (TestClient reporterClient = new TestClient();
             TestClient responderClient = new TestClient();
             TestClient adminClient = new TestClient()) {

            // 1. Giáo viên đăng nhập
            reporterClient.send(new Packet(Constants.TYPE_LOGIN, "LOGIN|username=gv_lan|password=123456"));
            Packet repLoginResp = reporterClient.receive();
            assertEquals("OK", repLoginResp.parseParams().get("status"));

            // 1.1 Giáo viên gửi sự cố phòng A101 khi cán bộ kỹ thuật chưa online (tạo tồn đọng backlog)
            reporterClient.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT,
                "INCIDENT|room=A101|category=FACILITY|desc=Den chieu sang chap chon phong A101"));
            Packet initResp = reporterClient.receive();
            assertEquals(Constants.TYPE_SUBMIT_RESP, initResp.getType());

            // 2. Cán bộ kỹ thuật đăng nhập
            responderClient.send(new Packet(Constants.TYPE_LOGIN, "LOGIN|username=kt_tuan|password=123456"));
            Packet resLoginResp = responderClient.receive();
            assertEquals("OK", resLoginResp.parseParams().get("status"));

            // Kiểm tra tính năng Offline Backlog Push: kt_tuan vừa online sẽ nhận ngay sự cố tồn đọng của phòng Kỹ thuật
            Packet backlogPush = responderClient.receive();
            assertEquals(Constants.TYPE_DISPATCH_NOTIFY, backlogPush.getType());
            assertEquals("TECHNICAL", backlogPush.parseParams().get("department"));
            assertEquals("A101", backlogPush.parseParams().get("room"));

            // 3. Admin đăng nhập
            adminClient.send(new Packet(Constants.TYPE_LOGIN, "LOGIN|username=admin_bgh|password=123456"));
            Packet admLoginResp = adminClient.receive();
            assertEquals("OK", admLoginResp.parseParams().get("status"));

            // 4. Giáo viên gửi báo cáo sự cố Thiết bị (FACILITY -> TECHNICAL)
            reporterClient.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT,
                "INCIDENT|room=A105|category=FACILITY|desc=May chieu khong len nguon"));

            // Giáo viên nhận xác nhận SUBMIT_RESP
            Packet submitResp = reporterClient.receive();
            assertEquals(Constants.TYPE_SUBMIT_RESP, submitResp.getType());
            int ticketId = Integer.parseInt(submitResp.parseParams().get("ticketId"));
            assertTrue(ticketId > 0);

            // Cán bộ kỹ thuật (kt_tuan) lập tức nhận được push DISPATCH_NOTIFY tự động tới phòng ban
            Packet dispatchPush = responderClient.receive();
            assertEquals(Constants.TYPE_DISPATCH_NOTIFY, dispatchPush.getType());
            assertEquals("TECHNICAL", dispatchPush.parseParams().get("department"));
            assertEquals("A105", dispatchPush.parseParams().get("room"));

            // 5. Cán bộ kỹ thuật tiếp nhận sự cố (CLAIM_TASK)
            responderClient.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
            Packet claimResp = responderClient.receive();
            assertEquals(Constants.TYPE_CLAIM_RESP, claimResp.getType());
            assertEquals("OK", claimResp.parseParams().get("status"));

            // Giáo viên nhận được thông báo trạng thái cập nhật (STATUS_BROADCAST)
            Packet statusPush = reporterClient.receive();
            assertEquals(Constants.TYPE_STATUS_BROADCAST, statusPush.getType());
            assertEquals(Constants.STATUS_IN_PROGRESS, statusPush.parseParams().get("status"));

            // 6. Kênh chat 1-1 giữa Giáo viên và Kỹ thuật viên
            reporterClient.send(new Packet(Constants.TYPE_CHAT_SEND, "CHAT_SEND|ticketId=" + ticketId,
                "Anh ơi máy chiếu phòng A105 đang cần gấp cho tiết 3 ạ".getBytes(StandardCharsets.UTF_8)));

            // Reporter nhận ACK/Echo
            Packet chatEcho = reporterClient.receive();
            assertEquals(Constants.TYPE_CHAT_PUSH, chatEcho.getType());

            // Responder nhận được tin nhắn 1-1 (bỏ qua STATUS_BROADCAST nếu có)
            Packet chatToResponder = responderClient.receive();
            if (chatToResponder.getType() == Constants.TYPE_STATUS_BROADCAST) {
                chatToResponder = responderClient.receive();
            }
            assertEquals(Constants.TYPE_CHAT_PUSH, chatToResponder.getType());
            String chatContent = new String(chatToResponder.getAttachment(), StandardCharsets.UTF_8);
            assertTrue(chatContent.contains("tiết 3"));

            // 7. Cán bộ kỹ thuật hoàn tất và đóng sự cố
            responderClient.send(new Packet(Constants.TYPE_STATUS_UPDATE,
                "STATUS_UPDATE|ticketId=" + ticketId + "|status=RESOLVED|note=Da thay day nguon xong"));

            // Giáo viên nhận thông báo RESOLVED -> đóng kênh chat cục bộ
            Packet resolvedPush = reporterClient.receive();
            assertEquals(Constants.TYPE_STATUS_BROADCAST, resolvedPush.getType());
            assertEquals(Constants.STATUS_RESOLVED, resolvedPush.parseParams().get("status"));

            // 8. Admin kiểm tra thống kê
            adminClient.send(new Packet(Constants.TYPE_ADMIN_GET_STATS, "GET_STATS|timeRange=ALL"));
            Packet statResp = null;
            for (int i = 0; i < 5; i++) {
                Packet p = adminClient.receive();
                if (p.getType() == Constants.TYPE_ADMIN_GET_STATS_RESP) {
                    statResp = p;
                    break;
                }
            }
            assertNotNull(statResp);
            assertTrue(statResp.getAttachment().length > 0);
        }
    }
}
