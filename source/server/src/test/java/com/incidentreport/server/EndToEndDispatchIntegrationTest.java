package com.incidentreport.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import com.incidentreport.service.CategoryDAO;
import com.incidentreport.service.ReportService;
import com.incidentreport.service.TicketDAO;
import com.incidentreport.service.UserDAO;

@DisplayName("Kiểm thử Tích hợp Đầu-Cuối (End-to-End): TCP Socket Dispatch, Task Pool & Manual Assign")
public class EndToEndDispatchIntegrationTest {

    private static final int TEST_PORT = 5099;
    private static ServerSocket testServerSocket;
    private static ExecutorService threadPool;
    private static volatile boolean running = true;
    private static final Gson gson = new Gson();

    @BeforeAll
    static void startTestServer() throws Exception {
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

    @Test
    @DisplayName("E2E Test: BGH đăng nhập qua TCP Socket, chỉ định giao việc và nhân viên nhận việc thành công")
    void testEndToEndDispatchFlow() throws Exception {
        // 1. Client Admin kết nối Socket
        try (Socket adminSock = new Socket("127.0.0.1", TEST_PORT);
             DataOutputStream adminOut = new DataOutputStream(new BufferedOutputStream(adminSock.getOutputStream()));
             DataInputStream adminIn = new DataInputStream(new BufferedInputStream(adminSock.getInputStream()))) {

            // Admin Đăng nhập
            Packet loginPkt = new Packet(Constants.TYPE_LOGIN, "LOGIN|username=admin_bgh|password=123456");
            loginPkt.writeTo(adminOut);

            Packet loginResp = Packet.readFrom(adminIn);
            assertEquals(Constants.TYPE_LOGIN_RESP, loginResp.getType());
            assertTrue(loginResp.getHeaderPayload().contains("status=OK"));

            // 2. Tạo một sự cố PENDING mới
            String testRoom = "E2E_" + UUID.randomUUID().toString().substring(0, 6);
            ReportService.SubmitResult subRes = ReportService.submitIncident(
                2, testRoom, "TECHNICAL".equals("TECHNICAL") ? "ELECTRIC" : "FACILITY",
                4, "Chập cháy át tổng phòng " + testRoom, null, 0, "127.0.0.1"
            );
            assertTrue(subRes.success);
            int ticketId = subRes.ticket.getTicketId();

            // 3. Admin lấy danh sách staff
            Packet reqStaff = new Packet(Constants.TYPE_GET_STAFF_LIST, "GET_STAFF_LIST");
            reqStaff.writeTo(adminOut);

            Packet staffResp = Packet.readFrom(adminIn);
            assertEquals(Constants.TYPE_GET_STAFF_LIST_RESP, staffResp.getType());
            Type staffListType = new TypeToken<ArrayList<User>>(){}.getType();
            List<User> staffList = gson.fromJson(staffResp.getHeaderPayload(), staffListType);
            assertNotNull(staffList);
            assertTrue(staffList.stream().anyMatch(u -> u.getUserId() == 4), "Phải có Anh Hoàng Văn Tuấn (ID=4)");

            // 4. Admin thực hiện Giao việc thủ công (TYPE_MANUAL_ASSIGN) cho Hoàng Văn Tuấn (staffId = 4)
            Packet assignPkt = new Packet(Constants.TYPE_MANUAL_ASSIGN,
                String.format("MANUAL_ASSIGN|ticketId=%d|staffId=4|note=BGH chi dinh xu ly ngay", ticketId));
            assignPkt.writeTo(adminOut);

            Packet assignResp = Packet.readFrom(adminIn);
            // Có thể nhận STATUS_UPDATE do broadcast trước hoặc MANUAL_ASSIGN_RESP
            if (assignResp.getType() == Constants.TYPE_STATUS_UPDATE) {
                assignResp = Packet.readFrom(adminIn);
            }
            assertEquals(Constants.TYPE_MANUAL_ASSIGN_RESP, assignResp.getType());
            assertTrue(assignResp.getHeaderPayload().contains("status=OK"), "Kết quả giao việc phải trả về status=OK");

            // 5. Kiểm tra CSDL xác nhận sự cố đã được chuyển sang ASSIGNED và gán cho staff 4
            IncidentTicket assignedTicket = TicketDAO.getTicketById(ticketId);
            assertNotNull(assignedTicket);
            assertEquals(Constants.STATUS_ASSIGNED, assignedTicket.getStatus());
            assertEquals(4, assignedTicket.getAssignedStaffId());
        }
    }

    @Test
    @DisplayName("E2E Test: Kỹ thuật viên truy vấn Hồ công việc mở (GET_OPEN_POOL) và bấm nhận việc (ACK_TASK)")
    void testEndToEndOpenPoolAndClaimFlow() throws Exception {
        // Tạo sự cố PENDING mới
        String testRoom = "POOL_E2E_" + UUID.randomUUID().toString().substring(0, 6);
        ReportService.SubmitResult subRes = ReportService.submitIncident(
            3, testRoom, "FACILITY", 3, "Hỏng bóng đèn neon", null, 0, "127.0.0.1"
        );
        assertTrue(subRes.success);
        int ticketId = subRes.ticket.getTicketId();

        // Client Kỹ thuật viên kết nối Socket
        try (Socket responderSock = new Socket("127.0.0.1", TEST_PORT);
             DataOutputStream respOut = new DataOutputStream(new BufferedOutputStream(responderSock.getOutputStream()));
             DataInputStream respIn = new DataInputStream(new BufferedInputStream(responderSock.getInputStream()))) {

            // Đăng nhập kt_tuan
            Packet loginPkt = new Packet(Constants.TYPE_LOGIN, "LOGIN|username=kt_tuan|password=123456");
            loginPkt.writeTo(respOut);

            Packet loginResp = Packet.readFrom(respIn);
            assertEquals(Constants.TYPE_LOGIN_RESP, loginResp.getType());
            assertTrue(loginResp.getHeaderPayload().contains("status=OK"));

            // Yêu cầu lấy Hồ công việc mở
            Packet poolReq = new Packet(Constants.TYPE_GET_OPEN_POOL, "GET_OPEN_POOL");
            poolReq.writeTo(respOut);

            Packet poolResp = Packet.readFrom(respIn);
            assertEquals(Constants.TYPE_GET_OPEN_POOL_RESP, poolResp.getType());
            Type ticketListType = new TypeToken<ArrayList<IncidentTicket>>(){}.getType();
            List<IncidentTicket> poolList = gson.fromJson(poolResp.getHeaderPayload(), ticketListType);
            assertNotNull(poolList);
            assertTrue(poolList.stream().anyMatch(t -> t.getTicketId() == ticketId),
                "Sự cố PENDING mới tạo phải xuất hiện trong Open Pool của Responder");

            // Bấm nhận việc (ACK_TASK ACCEPT)
            Packet claimPkt = new Packet(Constants.TYPE_ACK_TASK, "ACK_TASK|ticketId=" + ticketId + "|action=ACCEPT");
            claimPkt.writeTo(respOut);

            Packet claimResp = Packet.readFrom(respIn);
            // Có thể nhận broadcast STATUS_UPDATE trước hoặc ACK_RESP
            if (claimResp.getType() == Constants.TYPE_STATUS_UPDATE) {
                claimResp = Packet.readFrom(respIn);
            }
            assertEquals(Constants.TYPE_ACK_RESP, claimResp.getType());
            assertTrue(claimResp.getHeaderPayload().contains("status=OK"));

            // Cập nhật trạng thái thành IN_PROGRESS rồi RESOLVED
            Packet updateProg = new Packet(Constants.TYPE_STATUS_UPDATE,
                "STATUS_UPDATE|ticketId=" + ticketId + "|status=" + Constants.STATUS_IN_PROGRESS);
            updateProg.writeTo(respOut);

            // Gửi hoàn thành
            Packet updateDone = new Packet(Constants.TYPE_STATUS_UPDATE,
                "STATUS_UPDATE|ticketId=" + ticketId + "|status=" + Constants.STATUS_RESOLVED + "|note=Da thay bong den xong");
            updateDone.writeTo(respOut);

            // Đợi 200ms để server cập nhật CSDL
            Thread.sleep(200);

            IncidentTicket resolvedTicket = TicketDAO.getTicketById(ticketId);
            assertNotNull(resolvedTicket);
            assertEquals(Constants.STATUS_RESOLVED, resolvedTicket.getStatus());
            assertEquals("Da thay bong den xong", resolvedTicket.getResolutionNote());
        }
    }
}

