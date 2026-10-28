package com.incidentreport.server;

import com.google.gson.Gson;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.*;
import com.incidentreport.common.packet.Packet;
import com.incidentreport.service.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BỘ TEST AUDIT TOÀN DIỆN CHUẨN TEAM LEAD & SENIOR QA:
 * Kiểm tra mọi góc cạnh bảo mật, luồng nghiệp vụ, tương tranh và tính toàn vẹn dữ liệu.
 */
public class SystemQualityAuditRunner {

    private static final int AUDIT_PORT = 5888;
    private static ServerSocket serverSocket;
    private static ExecutorService threadPool;
    private static volatile boolean serverRunning = true;
    private static final Gson gson = new Gson();
    private static final List<String> passedTests = new ArrayList<>();
    private static final List<String> failedTests = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("========================================================================");
        System.out.println("  SENIOR AUDIT SUITE: BẮT ĐẦU RÀ SOÁT & KIỂM THỬ TOÀN BỘ HỆ THỐNG");
        System.out.println("========================================================================");

        try {
            setupServer();

            runTest("1. Xác thực & Đăng nhập (Authentication)", SystemQualityAuditRunner::testAuthentication);
            runTest("2. Phân quyền Admin độc quyền (RBAC Admin-Only Scope=ALL)", SystemQualityAuditRunner::testAdminScopeAuthorization);
            runTest("3. Phân quyền Tiếp nhận đúng chuyên môn (Specialty Enforcement)", SystemQualityAuditRunner::testSpecialtyAuthorization);
            runTest("4. Tương tranh Race-Condition (2 Responder cùng nhận 1 vé cùng lúc)", SystemQualityAuditRunner::testConcurrentClaimTicket);
            runTest("5. Phân quyền Kênh Chat 1-1 (Private Chat Security)", SystemQualityAuditRunner::testChatAuthorization);
            runTest("6. Tính toàn vẹn Tin nhắn Chat & Thứ tự thời gian (Chat Persistence)", SystemQualityAuditRunner::testChatPersistenceAndOrdering);
            runTest("7. Phân quyền Cập nhật trạng thái vé (Status Update Authorization)", SystemQualityAuditRunner::testStatusUpdateAuthorization);
            runTest("8. Đồng bộ tồn đọng ngoại tuyến (Offline Backlog Push)", SystemQualityAuditRunner::testOfflineBacklogPush);
            runTest("9. Admin cấp tài khoản mới & Đăng nhập ngay lập tức", SystemQualityAuditRunner::testAdminCreateUserAndLogin);
            runTest("10. Thống kê đa kỳ & Bộ lọc xuất CSV", SystemQualityAuditRunner::testStatisticsAndCsvExport);
            runTest("11. Khả năng chịu lỗi & Chống Injection (Sanitization & Robustness)", SystemQualityAuditRunner::testInjectionAndMalformedPackets);

        } catch (Exception e) {
            System.err.println("❌ LỖI HỆ THỐNG TRONG QUÁ TRÌNH AUDIT: " + e.getMessage());
            e.printStackTrace();
        } finally {
            teardownServer();
            printAuditSummary();
        }
    }

    private static void setupServer() throws Exception {
        // Cách ly CSDL Test: Tuyệt đối không đụng vào DB chính incident_report.db
        DatabaseConnection.setCustomDbUrl("jdbc:sqlite:database/test_incident_report.db");

        InitDatabase.resetDatabase();
        CategoryDAO.refreshCache();
        UserDAO.resetAllUsersOffline();

        threadPool = Executors.newCachedThreadPool();
        serverSocket = new ServerSocket(AUDIT_PORT);

        Thread acceptThread = new Thread(() -> {
            while (serverRunning && !serverSocket.isClosed()) {
                try {
                    Socket sock = serverSocket.accept();
                    threadPool.submit(new ClientHandler(sock));
                } catch (Exception ignored) {}
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
        Thread.sleep(300);
        System.out.println("✅ Test Server đã khởi động tại cổng " + AUDIT_PORT + " và CSDL đã reset sạch sẽ.\n");
    }

    private static void teardownServer() {
        serverRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (Exception ignored) {}
        if (threadPool != null) threadPool.shutdownNow();
    }

    private static void runTest(String testName, TestRunnable test) {
        System.out.printf("👉 Đang chạy: %-60s ... ", testName);
        try {
            test.run();
            passedTests.add(testName);
            System.out.println("[PASS ✅]");
        } catch (Throwable t) {
            failedTests.add(testName + " -> " + t.getMessage());
            System.out.println("[FAIL ❌]");
            t.printStackTrace(System.out);
        }
    }

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Exception;
    }

    // -------------------------------------------------------------------------
    // TEST 1: AUTHENTICATION
    // -------------------------------------------------------------------------
    private static void testAuthentication() throws Exception {
        try (AuditClient client = new AuditClient()) {
            // Sai mật khẩu
            client.send(new Packet(Constants.TYPE_LOGIN, "LOGIN|username=gv_lan|password=wrongpass"));
            Packet resp = client.receive();
            if (!"FAIL".equalsIgnoreCase(resp.parseParams().get("status"))) {
                throw new AssertionError("Kỳ vọng đăng nhập thất bại với sai password");
            }

            // Đúng mật khẩu
            client.send(new Packet(Constants.TYPE_LOGIN, "LOGIN|username=gv_lan|password=123456"));
            resp = client.receive();
            if (!"OK".equalsIgnoreCase(resp.parseParams().get("status"))) {
                throw new AssertionError("Kỳ vọng đăng nhập thành công với gv_lan");
            }
            if (!Constants.ROLE_REPORTER.equalsIgnoreCase(resp.parseParams().get("role"))) {
                throw new AssertionError("Role phải là REPORTER");
            }
        }
    }

    // -------------------------------------------------------------------------
    // TEST 2: RBAC ADMIN-ONLY SCOPE=ALL
    // -------------------------------------------------------------------------
    private static void testAdminScopeAuthorization() throws Exception {
        try (AuditClient userClient = new AuditClient();
             AuditClient adminClient = new AuditClient()) {

            // Reporter gv_lan cố tình xin scope=ALL
            userClient.login("gv_lan", "123456");
            userClient.send(new Packet(Constants.TYPE_LIST_TICKETS, "LIST_TICKETS|scope=ALL"));
            Packet err = userClient.receive();
            if (err.getType() != Constants.TYPE_ERROR) {
                throw new AssertionError("Reporter không được phép lấy scope=ALL");
            }

            // Admin xin scope=ALL -> thành công
            adminClient.login("admin_bgh", "123456");
            adminClient.send(new Packet(Constants.TYPE_LIST_TICKETS, "LIST_TICKETS|scope=ALL"));
            Packet ok = adminClient.receive();
            if (ok.getType() != Constants.TYPE_LIST_TICKETS_RESP) {
                throw new AssertionError("Admin phải lấy được scope=ALL");
            }
        }
    }

    // -------------------------------------------------------------------------
    // TEST 3: SPECIALTY ENFORCEMENT
    // -------------------------------------------------------------------------
    private static void testSpecialtyAuthorization() throws Exception {
        try (AuditClient repClient = new AuditClient();
             AuditClient techClient = new AuditClient();
             AuditClient medClient = new AuditClient()) {

            repClient.login("gv_lan", "123456");
            techClient.login("kt_tuan", "123456");
            // Drain backlog
            techClient.drainPackets(500);

            medClient.login("yte_mai", "123456");
            medClient.drainPackets(500);

            // Tạo sự cố MEDICAL (Y tế)
            repClient.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=SAN_TRUONG|category=MEDICAL|desc=Hoc sinh bi bong gan"));
            Packet subResp = repClient.receive();
            int ticketId = Integer.parseInt(subResp.parseParams().get("ticketId"));

            // Cán bộ kỹ thuật kt_tuan cố tình claim vé Y tế -> Phải bị chặn
            techClient.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
            Packet claimErr = techClient.receive();
            if (claimErr.getType() != Constants.TYPE_ERROR) {
                throw new AssertionError("Kỹ thuật viên không được nhận vé Y tế!");
            }

            // Cán bộ y tế yte_mai nhận -> Thành công
            medClient.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
            Packet claimOk = medClient.receive();
            if (claimOk.getType() != Constants.TYPE_CLAIM_RESP || !"OK".equalsIgnoreCase(claimOk.parseParams().get("status"))) {
                throw new AssertionError("Cán bộ Y tế phải nhận được vé Y tế thành công!");
            }
        }
    }

    // -------------------------------------------------------------------------
    // TEST 4: CONCURRENT CLAIM (RACE CONDITION)
    // -------------------------------------------------------------------------
    private static void testConcurrentClaimTicket() throws Exception {
        try (AuditClient repClient = new AuditClient()) {
            repClient.login("gv_lan", "123456");

            // Tạo vé kỹ thuật mới
            repClient.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=A201|category=FACILITY|desc=May lanh hong"));
            Packet subResp = repClient.receive();
            int ticketId = Integer.parseInt(subResp.parseParams().get("ticketId"));

            // 2 cán bộ kỹ thuật kt_tuan và kt_nam cùng online
            try (AuditClient tech1 = new AuditClient();
                 AuditClient tech2 = new AuditClient()) {

                tech1.login("kt_tuan", "123456");
                tech1.drainPackets(300);

                tech2.login("kt_nam", "123456");
                tech2.drainPackets(300);

                CountDownLatch startGun = new CountDownLatch(1);
                CountDownLatch doneLatch = new CountDownLatch(2);
                AtomicInteger okCount = new AtomicInteger(0);
                AtomicInteger failCount = new AtomicInteger(0);

                Thread t1 = new Thread(() -> {
                    try {
                        startGun.await();
                        tech1.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
                        Packet p = tech1.receive();
                        if (p.getType() == Constants.TYPE_CLAIM_RESP) {
                            if ("OK".equalsIgnoreCase(p.parseParams().get("status"))) okCount.incrementAndGet();
                            else failCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {} finally {
                        doneLatch.countDown();
                    }
                });

                Thread t2 = new Thread(() -> {
                    try {
                        startGun.await();
                        tech2.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
                        Packet p = tech2.receive();
                        if (p.getType() == Constants.TYPE_CLAIM_RESP) {
                            if ("OK".equalsIgnoreCase(p.parseParams().get("status"))) okCount.incrementAndGet();
                            else failCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {} finally {
                        doneLatch.countDown();
                    }
                });

                t1.start();
                t2.start();
                startGun.countDown(); // Bắn súng xuất phát cùng lúc
                boolean finished = doneLatch.await(5, TimeUnit.SECONDS);

                if (!finished) throw new AssertionError("Timeout chờ tương tranh!");
                if (okCount.get() != 1 || failCount.get() != 1) {
                    throw new AssertionError(String.format("Lỗi tương tranh! OK=%d, FAIL=%d (Bắt buộc phải 1 người được và 1 người trượt)",
                            okCount.get(), failCount.get()));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // TEST 5: PRIVATE CHAT AUTHORIZATION
    // -------------------------------------------------------------------------
    private static void testChatAuthorization() throws Exception {
        try (AuditClient rep1 = new AuditClient();
             AuditClient rep2 = new AuditClient();
             AuditClient tech = new AuditClient()) {

            rep1.login("gv_lan", "123456");
            tech.login("kt_tuan", "123456");
            tech.drainPackets(300);

            // rep1 tạo vé
            rep1.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=B101|category=FACILITY|desc=Chay bong den"));
            Packet subResp = rep1.receive();
            int ticketId = Integer.parseInt(subResp.parseParams().get("ticketId"));

            // tech nhận vé
            tech.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
            tech.receive(); // CLAIM_RESP
            rep1.receive(); // STATUS_BROADCAST

            // Học sinh hs_minh (rep2) không liên quan đến vé này cố tình gửi chat
            rep2.login("hs_minh", "123456");
            rep2.send(new Packet(Constants.TYPE_CHAT_SEND, "CHAT_SEND|ticketId=" + ticketId, "Toi muon xen vao".getBytes(StandardCharsets.UTF_8)));
            Packet err = rep2.receive();
            if (err.getType() != Constants.TYPE_ERROR) {
                throw new AssertionError("Người ngoài không được phép gửi tin nhắn vào vé của người khác!");
            }

            // rep2 cố tình xem lịch sử chat của vé đó
            rep2.send(new Packet(Constants.TYPE_CHAT_HISTORY, "CHAT_HISTORY|ticketId=" + ticketId));
            err = rep2.receive();
            if (err.getType() != Constants.TYPE_ERROR) {
                throw new AssertionError("Người ngoài không được phép xem lịch sử chat của vé người khác!");
            }
        }
    }

    // -------------------------------------------------------------------------
    // TEST 6: CHAT PERSISTENCE & CHRONOLOGICAL ORDER
    // -------------------------------------------------------------------------
    private static void testChatPersistenceAndOrdering() throws Exception {
        try (AuditClient rep = new AuditClient();
             AuditClient tech = new AuditClient()) {

            rep.login("gv_lan", "123456");
            tech.login("kt_tuan", "123456");
            tech.drainPackets(300);

            rep.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=LAB_LY|category=FACILITY|desc=Day cap hong"));
            int ticketId = Integer.parseInt(rep.receive().parseParams().get("ticketId"));

            tech.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
            tech.receive();
            rep.receive();

            // Gửi liên tục 3 tin nhắn
            rep.send(new Packet(Constants.TYPE_CHAT_SEND, "CHAT_SEND|ticketId=" + ticketId, "Tin nhắn 1".getBytes(StandardCharsets.UTF_8)));
            rep.receive(); // echo
            tech.receive(); // push

            tech.send(new Packet(Constants.TYPE_CHAT_SEND, "CHAT_SEND|ticketId=" + ticketId, "Tin nhắn 2".getBytes(StandardCharsets.UTF_8)));
            tech.receive(); // echo
            rep.receive(); // push

            rep.send(new Packet(Constants.TYPE_CHAT_SEND, "CHAT_SEND|ticketId=" + ticketId, "Tin nhắn 3".getBytes(StandardCharsets.UTF_8)));
            rep.receive(); // echo
            tech.receive(); // push

            // Yêu cầu lấy toàn bộ lịch sử từ CSDL
            rep.send(new Packet(Constants.TYPE_CHAT_HISTORY, "CHAT_HISTORY|ticketId=" + ticketId));
            Packet histPkt = rep.receive();
            if (histPkt.getType() != Constants.TYPE_CHAT_HISTORY_RESP) {
                throw new AssertionError("Kỳ vọng TYPE_CHAT_HISTORY_RESP");
            }

            List<ChatMessage> list = gson.fromJson(new String(histPkt.getAttachment(), StandardCharsets.UTF_8),
                    new com.google.gson.reflect.TypeToken<List<ChatMessage>>(){}.getType());

            if (list == null || list.size() != 3) {
                throw new AssertionError("Số tin nhắn lưu trong DB phải bằng 3, nhận được: " + (list != null ? list.size() : 0));
            }
            if (!list.get(0).getContent().equals("Tin nhắn 1") ||
                !list.get(1).getContent().equals("Tin nhắn 2") ||
                !list.get(2).getContent().equals("Tin nhắn 3")) {
                throw new AssertionError("Thứ tự tin nhắn không đảm bảo tính tuần tự!");
            }
        }
    }

    // -------------------------------------------------------------------------
    // TEST 7: STATUS UPDATE AUTHORIZATION
    // -------------------------------------------------------------------------
    private static void testStatusUpdateAuthorization() throws Exception {
        try (AuditClient rep = new AuditClient();
             AuditClient tech1 = new AuditClient();
             AuditClient tech2 = new AuditClient()) {

            rep.login("gv_lan", "123456");
            tech1.login("kt_tuan", "123456");
            tech1.drainPackets(300);
            tech2.login("kt_nam", "123456");
            tech2.drainPackets(300);

            rep.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=B302|category=FACILITY|desc=Nuoc chay"));
            int ticketId = Integer.parseInt(rep.receive().parseParams().get("ticketId"));

            // tech1 nhận việc
            tech1.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
            tech1.receive();
            rep.receive();

            // Cán bộ tech2 (không được giao) cố tình bấm hoàn tất (RESOLVED)
            tech2.send(new Packet(Constants.TYPE_STATUS_UPDATE, "STATUS_UPDATE|ticketId=" + ticketId + "|status=RESOLVED"));
            Packet err = tech2.receive();
            if (err.getType() != Constants.TYPE_ERROR) {
                throw new AssertionError("Cán bộ không phụ trách vé không được quyền cập nhật trạng thái!");
            }

            // Cán bộ tech1 phụ trách hoàn tất -> Thành công
            tech1.send(new Packet(Constants.TYPE_STATUS_UPDATE, "STATUS_UPDATE|ticketId=" + ticketId + "|status=RESOLVED|note=Da sua xong"));
            Packet bcTech1 = tech1.receive(); // STATUS_BROADCAST
            if (bcTech1.getType() != Constants.TYPE_STATUS_BROADCAST || !"RESOLVED".equals(bcTech1.parseParams().get("status"))) {
                throw new AssertionError("Cán bộ phụ trách phải hoàn tất vé thành công!");
            }
        }
    }

    // -------------------------------------------------------------------------
    // TEST 8: OFFLINE BACKLOG PUSH
    // -------------------------------------------------------------------------
    private static void testOfflineBacklogPush() throws Exception {
        // Đảm bảo bảo vệ bv_son đang offline
        UserDAO.updateUserOnlineStatus(7, false);

        int ticketId;
        try (AuditClient rep = new AuditClient()) {
            rep.login("gv_lan", "123456");
            // Gửi sự cố an ninh SECURITY trong lúc bv_son offline
            rep.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=CONG_TRUONG|category=SECURITY|desc=Nguoi la vao truong"));
            ticketId = Integer.parseInt(rep.receive().parseParams().get("ticketId"));
        }

        // Bác bảo vệ bv_son đăng nhập sau đó
        try (AuditClient secClient = new AuditClient()) {
            secClient.login("bv_son", "123456");
            // Phải nhận ngay DISPATCH_NOTIFY của sự cố lúc nãy
            Packet backlog = secClient.receive();
            if (backlog.getType() != Constants.TYPE_DISPATCH_NOTIFY) {
                throw new AssertionError("Kỳ vọng nhận DISPATCH_NOTIFY từ offline backlog, nhận được: " + backlog.getType());
            }
            if (!"SECURITY".equals(backlog.parseParams().get("department"))) {
                throw new AssertionError("Department của backlog phải là SECURITY");
            }
        }
    }

    // -------------------------------------------------------------------------
    // TEST 9: ADMIN CREATE USER & IMMEDIATE LOGIN
    // -------------------------------------------------------------------------
    private static void testAdminCreateUserAndLogin() throws Exception {
        String newUname = "gv_moi_" + (System.currentTimeMillis() % 10000);
        try (AuditClient admin = new AuditClient()) {
            admin.login("admin_bgh", "123456");

            String header = String.format("ADMIN_CREATE_USER|username=%s|fullName=Giao Vien Moi|role=REPORTER|specialty=NONE|phone=0988776655", newUname);
            admin.send(new Packet(Constants.TYPE_ADMIN_CREATE_USER, header, "654321".getBytes(StandardCharsets.UTF_8)));
            Packet createResp = admin.receive();
            if (!"OK".equalsIgnoreCase(createResp.parseParams().get("status"))) {
                throw new AssertionError("Admin tạo tài khoản mới thất bại: " + createResp.parseParams().get("message"));
            }
        }

        // Đăng nhập ngay lập tức với tài khoản mới vừa tạo
        try (AuditClient newClient = new AuditClient()) {
            newClient.login(newUname, "654321");
            // Thành công
        }
    }

    // -------------------------------------------------------------------------
    // TEST 10: STATISTICS & CSV EXPORT INTEGRITY
    // -------------------------------------------------------------------------
    private static void testStatisticsAndCsvExport() throws Exception {
        try (AuditClient admin = new AuditClient()) {
            admin.login("admin_bgh", "123456");

            admin.send(new Packet(Constants.TYPE_ADMIN_GET_STATS, "GET_STATS|timeRange=ALL"));
            Packet statResp = admin.receive();
            if (statResp.getType() != Constants.TYPE_ADMIN_GET_STATS_RESP) {
                throw new AssertionError("Admin lấy thống kê thất bại!");
            }

            StatisticReport report = gson.fromJson(new String(statResp.getAttachment(), StandardCharsets.UTF_8), StatisticReport.class);
            if (report == null || report.getTotalTickets() <= 0) {
                throw new AssertionError("Thống kê trả về dữ liệu rỗng!");
            }
            if (report.getTicketList() == null || report.getTicketList().isEmpty()) {
                throw new AssertionError("Danh sách ticketList phục vụ CSV không được nạp!");
            }
            if (report.getCountByDepartment() == null || report.getCountByDepartment().isEmpty()) {
                throw new AssertionError("Phân bổ phòng ban không có số liệu!");
            }
        }
    }

    // -------------------------------------------------------------------------
    // TEST 11: INJECTION & MALFORMED PACKET RESILIENCE
    // -------------------------------------------------------------------------
    private static void testInjectionAndMalformedPackets() throws Exception {
        try (AuditClient client = new AuditClient()) {
            client.login("gv_lan", "123456");

            // 1. Thử chèn SQL Injection và ký tự phân cách pipe (| và =) vào mô tả
            String evilDesc = "Test'; DROP TABLE users; -- |fake=123|role=ADMIN";
            client.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=A101|category=FACILITY|desc=" + evilDesc));
            Packet resp = client.receive();
            if (resp.getType() != Constants.TYPE_SUBMIT_RESP || !"OK".equalsIgnoreCase(resp.parseParams().get("status"))) {
                throw new AssertionError("Hệ thống phải sanitize an toàn và không bị crash bởi SQL injection!");
            }

            // Kiểm tra bảng users vẫn nguyên vẹn
            if (UserDAO.getAllUsers().isEmpty()) {
                throw new AssertionError("Lỗ hổng SQL Injection! Bảng users đã bị mất dữ liệu!");
            }

            // 2. Thử gửi Opcode không xác định
            client.send(new Packet((byte) 0xEE, "FOO|BAR"));
            Packet errPkt = client.receive();
            if (errPkt.getType() != Constants.TYPE_ERROR) {
                throw new AssertionError("Gửi opcode lạ phải nhận về gói tin ERROR!");
            }
        }
    }

    private static void printAuditSummary() {
        System.out.println("\n========================================================================");
        System.out.println("  KẾT QUẢ TỔNG HỢP KIỂM ĐỊNH CHẤT LƯỢNG (TEAM LEAD AUDIT REPORT)");
        System.out.println("========================================================================");
        System.out.println("✅ TỔNG SỐ TEST THÀNH CÔNG: " + passedTests.size() + "/" + (passedTests.size() + failedTests.size()));
        for (String p : passedTests) {
            System.out.println("   [PASSED] " + p);
        }

        if (!failedTests.isEmpty()) {
            System.out.println("\n❌ CÁC TEST THẤT BẠI (" + failedTests.size() + "):");
            for (String f : failedTests) {
                System.out.println("   [FAILED] " + f);
            }
        } else {
            System.out.println("\n🎉 HOÀN TOÀN KHÔNG PHÁT HIỆN LỖI LỚN! TOÀN BỘ CƠ CHẾ BẢO MẬT & NGHIỆP VỤ ĐẠT CHUẨN!");
        }
        System.out.println("========================================================================\n");
    }

    private static class AuditClient implements AutoCloseable {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;

        public AuditClient() throws Exception {
            this.socket = new Socket("127.0.0.1", AUDIT_PORT);
            this.socket.setSoTimeout(3000);
            this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        public void send(Packet p) throws Exception {
            p.writeTo(out);
        }

        public Packet receive() throws Exception {
            return Packet.readFrom(in);
        }

        public void login(String username, String password) throws Exception {
            send(new Packet(Constants.TYPE_LOGIN, "LOGIN|username=" + username, password.getBytes(StandardCharsets.UTF_8)));
            Packet resp = receive();
            if (!"OK".equalsIgnoreCase(resp.parseParams().get("status"))) {
                throw new RuntimeException("Login fail for " + username + ": " + resp.parseParams().get("message"));
            }
        }

        public void drainPackets(int timeoutMs) {
            try {
                int oldTimeout = socket.getSoTimeout();
                socket.setSoTimeout(timeoutMs);
                while (true) {
                    try {
                        receive();
                    } catch (Exception e) {
                        break;
                    }
                }
                socket.setSoTimeout(oldTimeout);
            } catch (Exception ignored) {}
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}

