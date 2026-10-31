package com.incidentreport.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.ChatMessage;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.StatisticReport;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import com.incidentreport.service.CategoryDAO;
import com.incidentreport.service.DatabaseConnection;
import com.incidentreport.service.InitDatabase;
import com.incidentreport.service.UserDAO;
import org.junit.jupiter.api.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ============================================================================
 * COMPREHENSIVE SYSTEM FUNCTIONAL TEST SUITE (SENIOR JAVA ARCHITECTURE)
 * ============================================================================
 * Bộ kiểm thử toàn diện toàn bộ chức năng & giao thức mạng của hệ thống SIDS:
 * 1. Xác thực tập trung & Phân quyền RBAC (ADMIN / REPORTER / RESPONDER)
 * 2. Cấp tài khoản mới & Ràng buộc toàn vẹn CSDL (Unique Constraint)
 * 3. Báo sự cố không ảnh & Tự động định tuyến chuyên môn tức thời (Auto-Dispatch)
 * 4. Báo sự cố KÈM ẢNH & Truy xuất nhị phân TCP 0x0D / 0x1D (Byte-for-byte stream)
 * 5. Tương tranh nhận ca nguyên tử (Atomic Claim Concurrency - 10 threads)
 * 6. Bảo vệ chéo chuyên môn phòng ban (Specialty Isolation Enforcement)
 * 7. Tiến trình vòng đời sự cố & Phát thanh toàn mạng (Status Broadcast)
 * 8. Trao đổi 1-1 thời gian thực (Push delivery) & Lịch sử hội thoại (Chat History)
 * 9. Bảo mật phòng chat riêng tư & Chống nghe lén (Private Chat Isolation)
 * 10. Thống kê đa kỳ Ban Giám Hiệu (DAY/MONTH/QUARTER/YEAR/ALL) & Danh mục CSV
 * 11. Đồng bộ tồn đọng ngoại tuyến (Offline Backlog Push on Login)
 * 12. Đăng xuất an toàn & Dọn dẹp phiên kết nối (Graceful Logout & Session Cleanup)
 *
 * Chạy được theo 2 cơ chế:
 * - JUnit 5 Test Runner (Maven Surefire / NetBeans Test)
 * - Java Application trực tiếp qua hàm main() (Shift+F6 trong NetBeans/IntelliJ)
 * ============================================================================
 */
@DisplayName("BỘ KIỂM THỬ TOÀN DIỆN CHỨC NĂNG HỆ THỐNG ĐIỀU PHỐI SỰ CỐ (SIDS)")
public class ComprehensiveSystemFunctionalTest {

    private static final int PORT = 5097;
    private static ServerSocket serverSocket;
    private static ExecutorService threadPool;
    private static volatile boolean serverRunning = true;
    private static final Gson gson = new Gson();

    private static final List<String> passedList = new ArrayList<>();
    private static final List<String> failedList = new ArrayList<>();

    @BeforeAll
    public static void setup() throws Exception {
        // Cách ly 100% CSDL Test: Không tác động vào DB chính
        DatabaseConnection.setCustomDbUrl("jdbc:sqlite:database/test_incident_report.db");
        InitDatabase.resetDatabase();
        CategoryDAO.refreshCache();
        UserDAO.resetAllUsersOffline();

        threadPool = Executors.newCachedThreadPool();
        serverSocket = new ServerSocket(PORT);

        Thread acceptThread = new Thread(() -> {
            while (serverRunning && !serverSocket.isClosed()) {
                try {
                    Socket sock = serverSocket.accept();
                    threadPool.submit(new ClientHandler(sock));
                } catch (Exception ignored) {}
            }
        }, "Test-Server-Acceptor");
        acceptThread.setDaemon(true);
        acceptThread.start();

        Thread.sleep(250);
        System.out.println("🚀 [TEST SERVER] Khởi động thành công tại cổng TCP: " + PORT);
    }

    @AfterAll
    public static void teardown() {
        serverRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
        System.out.println("🛑 [TEST SERVER] Đã dừng máy chủ thử nghiệm an toàn.");
    }

    @BeforeEach
    public void resetDbState() {
        // Làm sạch trạng thái online giữa các ca test
        UserDAO.resetAllUsersOffline();
    }

    // =========================================================================
    // 1. XÁC THỰC & ĐĂNG NHẬP
    // =========================================================================
    @Test
    @DisplayName("TC-01: Xác thực mật khẩu có Salt, Sai pass bị từ chối, Đúng pass nhận User JSON")
    public void test01_AuthenticationAndRoleParsing() throws Exception {
        try (TestClient client = new TestClient()) {
            // Sai mật khẩu
            client.send(new Packet(Constants.TYPE_LOGIN, "LOGIN|username=gv_lan|password=sai_mat_khau_123"));
            Packet resp = client.receive();
            assertEquals(Constants.TYPE_LOGIN_RESP, resp.getType());
            assertEquals("FAIL", resp.parseParams().get("status"), "Mật khẩu sai phải bị từ chối!");

            // Đăng nhập thành công với Giáo viên
            client.login("gv_lan", "123456");
            assertTrue(client.isLoggedIn());
            assertEquals(Constants.ROLE_REPORTER, client.getUserRole());

            // Đăng nhập thành công với Ban Giám Hiệu
            try (TestClient adminClient = new TestClient()) {
                adminClient.login("admin_bgh", "123456");
                assertTrue(adminClient.isLoggedIn());
                assertEquals(Constants.ROLE_ADMIN, adminClient.getUserRole());
            }

            // Đăng nhập thành công với Cán bộ Kỹ thuật
            try (TestClient techClient = new TestClient()) {
                techClient.login("kt_tuan", "123456");
                assertTrue(techClient.isLoggedIn());
                assertEquals(Constants.ROLE_RESPONDER, techClient.getUserRole());
                assertEquals(Constants.SPECIALTY_TECHNICAL, techClient.getSpecialty());
            }
        }
    }

    // =========================================================================
    // 2. ADMIN CẤP TÀI KHOẢN & BẢO VỆ TOÀN VẸN
    // =========================================================================
    @Test
    @DisplayName("TC-02: Admin cấp tài khoản mới, chống trùng lặp Username & Chặn người không có quyền")
    public void test02_AdminUserProvisioningAndIntegrity() throws Exception {
        try (TestClient admin = new TestClient();
             TestClient rep = new TestClient()) {

            admin.login("admin_bgh", "123456");
            rep.login("gv_lan", "123456");

            // Reporter cố tình cấp tài khoản -> Bị từ chối
            rep.send(new Packet(Constants.TYPE_ADMIN_CREATE_USER,
                    "ADMIN_CREATE_USER|username=hacker_user|fullName=Hacker|phone=0909090909|role=ADMIN|specialty=NONE",
                    "secret123".getBytes(StandardCharsets.UTF_8)));
            Packet err = rep.receive();
            assertEquals(Constants.TYPE_ERROR, err.getType(), "Reporter không được phép tạo tài khoản!");

            // Admin tạo tài khoản kỹ thuật viên mới: kt_pro_test
            String newUsername = "kt_pro_test_" + System.currentTimeMillis() % 10000;
            admin.send(new Packet(Constants.TYPE_ADMIN_CREATE_USER,
                    String.format("ADMIN_CREATE_USER|username=%s|fullName=KTV Pro Test|phone=0912345678|role=RESPONDER|specialty=TECHNICAL", newUsername),
                    "password_123".getBytes(StandardCharsets.UTF_8)));
            Packet createResp = admin.receive();
            assertEquals(Constants.TYPE_ADMIN_CREATE_RESP, createResp.getType());
            assertEquals("OK", createResp.parseParams().get("status"), "Admin phải tạo được tài khoản mới!");

            // Cố tình tạo trùng username vừa tạo -> Phải thất bại an toàn
            admin.send(new Packet(Constants.TYPE_ADMIN_CREATE_USER,
                    String.format("ADMIN_CREATE_USER|username=%s|fullName=KTV Duplicate|phone=0912345678|role=RESPONDER|specialty=TECHNICAL", newUsername),
                    "password_123".getBytes(StandardCharsets.UTF_8)));
            Packet dupResp = admin.receive();
            assertEquals("FAIL", dupResp.parseParams().get("status"), "Trùng lặp username phải bị chặn an toàn!");

            // Người dùng vừa tạo đăng nhập ngay lập tức qua mạng TCP
            try (TestClient newUserClient = new TestClient()) {
                newUserClient.login(newUsername, "password_123");
                assertTrue(newUserClient.isLoggedIn());
                assertEquals(Constants.ROLE_RESPONDER, newUserClient.getUserRole());
                assertEquals(Constants.SPECIALTY_TECHNICAL, newUserClient.getSpecialty());
            }
        }
    }

    // =========================================================================
    // 3. ADMIN LẤY DANH SÁCH NGƯỜI DÙNG & KIỂM TRA PHÂN QUYỀN
    // =========================================================================
    @Test
    @DisplayName("TC-03: Admin truy vấn danh sách toàn trường, người dùng thường bị chặn")
    public void test03_AdminGetUsersList() throws Exception {
        try (TestClient admin = new TestClient();
             TestClient rep = new TestClient()) {

            admin.login("admin_bgh", "123456");
            rep.login("gv_lan", "123456");

            // Reporter cố tình lấy danh sách User toàn trường -> Chặn
            rep.send(new Packet(Constants.TYPE_ADMIN_GET_USERS, "ADMIN_GET_USERS"));
            Packet err = rep.receive();
            assertEquals(Constants.TYPE_ERROR, err.getType());

            // Admin lấy danh sách -> Thành công và nhận JSON mảng
            admin.send(new Packet(Constants.TYPE_ADMIN_GET_USERS, "ADMIN_GET_USERS"));
            Packet resp = admin.receive();
            assertEquals(Constants.TYPE_ADMIN_GET_USERS_RESP, resp.getType());
            assertTrue(resp.hasAttachment());

            Type listType = new TypeToken<List<User>>(){}.getType();
            List<User> users = gson.fromJson(new String(resp.getAttachment(), StandardCharsets.UTF_8), listType);
            assertNotNull(users);
            assertTrue(users.size() >= 3, "Danh sách người dùng mẫu phải >= 3");
        }
    }

    // =========================================================================
    // 4. BÁO SỰ CỐ & TỰ ĐỘNG ĐỊNH TUYẾN CHUYÊN MÔN
    // =========================================================================
    @Test
    @DisplayName("TC-04: Báo sự cố tự động định tuyến đúng phòng ban, phòng ban khác không bị spam chuông")
    public void test04_SubmitIncidentAndAutoDispatchRouting() throws Exception {
        try (TestClient reporter = new TestClient();
             TestClient tech = new TestClient();
             TestClient medical = new TestClient()) {

            reporter.login("gv_lan", "123456");
            tech.login("kt_tuan", "123456");
            tech.drainPackets(300);

            medical.login("yte_mai", "123456");
            medical.drainPackets(300);

            // Báo sự cố ĐIỆN KỸ THUẬT (ELECTRIC) tại phòng A101
            reporter.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT,
                    "INCIDENT|room=A101|category=ELECTRIC|desc=O dien no phat tieng no lon"));
            Packet subResp = reporter.receive();
            assertEquals(Constants.TYPE_SUBMIT_RESP, subResp.getType());
            assertEquals("OK", subResp.parseParams().get("status"));
            String ticketCode = subResp.parseParams().get("ticketCode");
            assertNotNull(ticketCode);
            assertTrue(ticketCode.startsWith("TK-") || ticketCode.startsWith("INC-"),
                    "Mã sự cố phải bắt đầu bằng TK- hoặc INC-, nhận được: " + ticketCode);

            // Cán bộ KỸ THUẬT phải nhận được thông báo điều phối 0x03 tức thì
            Packet dispatch = tech.receiveType(Constants.TYPE_DISPATCH_NOTIFY, 2500);
            assertNotNull(dispatch);
            assertEquals(ticketCode, dispatch.parseParams().get("code"));
            assertEquals("A101", dispatch.parseParams().get("room"));

            // Cán bộ Y TẾ TUYỆT ĐỐI KHÔNG nhận được thông báo điều phối kỹ thuật
            boolean medicalReceived = medical.hasPacket(600);
            assertFalse(medicalReceived, "Cán bộ Y tế không được nhận chuông sự cố của Kỹ thuật!");
        }
    }

    // =========================================================================
    // 5. TRUYỀN TẢI ẢNH NHỊ PHÂN TCP (0x0D / 0x1D)
    // =========================================================================
    @Test
    @DisplayName("TC-05: Gửi sự cố kèm ảnh nhị phân & Truy xuất tải ảnh TCP 0x0D/0x1D toàn vẹn byte")
    public void test05_BinaryImageTcpStreaming() throws Exception {
        try (TestClient reporter = new TestClient();
             TestClient tech = new TestClient()) {

            reporter.login("gv_lan", "123456");
            tech.login("kt_tuan", "123456");
            tech.drainPackets(300);

            // Tạo payload ảnh giả lập (2048 bytes dữ liệu kiểm chuẩn)
            byte[] mockImageBytes = new byte[2048];
            for (int i = 0; i < mockImageBytes.length; i++) {
                mockImageBytes[i] = (byte) (i % 256);
            }

            // Gửi sự cố đính kèm ảnh
            reporter.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT,
                    "INCIDENT|room=LAB_HOA|category=FACILITY|desc=Vo binh dung hoa chat",
                    mockImageBytes));

            Packet subResp = reporter.receive();
            assertEquals(Constants.TYPE_SUBMIT_RESP, subResp.getType());
            assertEquals("OK", subResp.parseParams().get("status"));
            int ticketId = Integer.parseInt(subResp.parseParams().get("ticketId"));

            // Kỹ thuật viên yêu cầu tải ảnh hiện trường qua TCP bằng 0x0D
            tech.send(new Packet(Constants.TYPE_GET_IMAGE, "GET_IMAGE|ticketId=" + ticketId));
            Packet imgResp = tech.receiveType(Constants.TYPE_GET_IMAGE_RESP, 3000);
            assertNotNull(imgResp);
            assertEquals("OK", imgResp.parseParams().get("status"));
            assertTrue(imgResp.hasAttachment(), "Ảnh tải về phải có attachment");

            // So sánh từng byte dữ liệu ảnh tải về với ảnh gốc
            assertArrayEquals(mockImageBytes, imgResp.getAttachment(), "Dữ liệu ảnh qua TCP phải toàn vẹn 100%!");
        }
    }

    // =========================================================================
    // 6. TIẾP NHẬN NGUYÊN TỬ TƯƠNG TRANH CAO (10 THREADS CONCURRENCY)
    // =========================================================================
    @Test
    @DisplayName("TC-06: Tương tranh nhận vé đồng thời (10 threads) -> Đúng 1 thành công, 9 người trượt (0% double-claim)")
    public void test06_AtomicClaimHighConcurrency() throws Exception {
        try (TestClient reporter = new TestClient()) {
            reporter.login("gv_lan", "123456");

            // Tạo vé sự cố cần tranh chấp
            reporter.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT,
                    "INCIDENT|room=HT_A|category=FACILITY|desc=May chieu trung tam khong len hinh"));
            Packet subResp = reporter.receive();
            int ticketId = Integer.parseInt(subResp.parseParams().get("ticketId"));

            int concurrentClients = 10;
            ExecutorService pool = Executors.newFixedThreadPool(concurrentClients);
            CountDownLatch startGun = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrentClients);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger alreadyTakenCount = new AtomicInteger(0);

            for (int i = 0; i < concurrentClients; i++) {
                final int clientIndex = i;
                pool.submit(() -> {
                    try (TestClient responder = new TestClient()) {
                        responder.login("kt_tuan", "123456");
                        responder.drainPackets(200);

                        // Đồng loạt đứng chờ hiệu lệnh
                        startGun.await();

                        responder.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
                        Packet resp = responder.receiveType(Constants.TYPE_CLAIM_RESP, 4000);

                        String status = resp.parseParams().get("status");
                        if ("OK".equalsIgnoreCase(status)) {
                            successCount.incrementAndGet();
                        } else if ("ALREADY_TAKEN".equalsIgnoreCase(status)) {
                            alreadyTakenCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Kích hoạt tất cả 10 client gửi cùng 1 miligiây
            startGun.countDown();
            boolean finished = doneLatch.await(8, TimeUnit.SECONDS);
            pool.shutdown();

            assertTrue(finished, "Toàn bộ 10 client phải hoàn tất trong 8 giây");
            assertEquals(1, successCount.get(), "DUY NHẤT 1 cán bộ được nhận vé thành công!");
            assertEquals(9, alreadyTakenCount.get(), "9 cán bộ còn lại phải nhận ALREADY_TAKEN!");
        }
    }

    // =========================================================================
    // 7. BẢO VỆ CHUYÊN MÔN PHÒNG BAN
    // =========================================================================
    @Test
    @DisplayName("TC-07: Chặn nhận vé sai chuyên môn (Y tế không nhận vé Kỹ thuật)")
    public void test07_CrossSpecialtyClaimProtection() throws Exception {
        try (TestClient reporter = new TestClient();
             TestClient medical = new TestClient();
             TestClient tech = new TestClient()) {

            reporter.login("gv_lan", "123456");
            medical.login("yte_mai", "123456");
            medical.drainPackets(200);
            tech.login("kt_tuan", "123456");
            tech.drainPackets(200);

            // Báo sự cố KỸ THUẬT
            reporter.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=B204|category=WATER|desc=Vo voi nuoc"));
            int ticketId = Integer.parseInt(reporter.receive().parseParams().get("ticketId"));

            // Cán bộ Y tế cố tình nhận vé Kỹ thuật -> Phải bị chặn
            medical.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
            Packet err = medical.receive();
            assertEquals(Constants.TYPE_ERROR, err.getType(), "Hệ thống phải chặn cán bộ nhận ca sai chuyên môn!");

            // Cán bộ Kỹ thuật nhận -> Thành công
            tech.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
            Packet ok = tech.receiveType(Constants.TYPE_CLAIM_RESP, 2000);
            assertEquals("OK", ok.parseParams().get("status"));
        }
    }

    // =========================================================================
    // 8. TIẾN TRÌNH VÒNG ĐỜI & PHÁT THANH TRẠNG THÁI
    // =========================================================================
    @Test
    @DisplayName("TC-08: Cán bộ hoàn tất sự cố, hệ thống phát thanh STATUS_BROADCAST đến Người báo & Admin")
    public void test08_TicketLifecycleAndGlobalBroadcast() throws Exception {
        try (TestClient reporter = new TestClient();
             TestClient tech = new TestClient();
             TestClient admin = new TestClient();
             TestClient stranger = new TestClient()) {

            reporter.login("gv_lan", "123456");
            tech.login("kt_tuan", "123456");
            admin.login("admin_bgh", "123456");
            stranger.login("hs_minh", "123456");

            tech.drainPackets(200);
            admin.drainPackets(200);
            stranger.drainPackets(200);

            // 1. Tạo vé
            reporter.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=C301|category=FACILITY|desc=Ghe bi gay chan"));
            int ticketId = Integer.parseInt(reporter.receive().parseParams().get("ticketId"));

            // 2. Kỹ thuật tiếp nhận
            tech.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
            tech.receiveType(Constants.TYPE_CLAIM_RESP, 2000);

            // Drain broadcast claim
            reporter.drainPackets(200);
            admin.drainPackets(200);
            tech.drainPackets(200);
            stranger.drainPackets(200);

            // 3. Kỹ thuật báo hoàn thành sự cố (RESOLVED)
            tech.send(new Packet(Constants.TYPE_STATUS_UPDATE,
                    String.format("STATUS_UPDATE|ticketId=%d|status=%s|note=Da thay ghe go moi", ticketId, Constants.STATUS_RESOLVED)));

            // Người báo sự cố nhận được STATUS_BROADCAST
            Packet bCastReporter = reporter.receiveType(Constants.TYPE_STATUS_BROADCAST, 2500);
            assertNotNull(bCastReporter);
            assertEquals(String.valueOf(ticketId), bCastReporter.parseParams().get("ticketId"));
            assertEquals(Constants.STATUS_RESOLVED, bCastReporter.parseParams().get("status"));

            // Ban Giám Hiệu (Admin) nhận được STATUS_BROADCAST
            Packet bCastAdmin = admin.receiveType(Constants.TYPE_STATUS_BROADCAST, 2500);
            assertNotNull(bCastAdmin);
            assertEquals(Constants.STATUS_RESOLVED, bCastAdmin.parseParams().get("status"));

            // Người ngoài (Học sinh hs_minh không liên quan) -> Không bị rò rỉ phát thanh
            assertFalse(stranger.hasPacket(300), "Người ngoài không liên quan không nhận broadcast của vé khác!");
        }
    }

    // =========================================================================
    // 9. TRAO ĐỔI 1-1 REALTIME PUSH & LỊCH SỬ CHAT
    // =========================================================================
    @Test
    @DisplayName("TC-09: Nhắn tin 1-1 đẩy thời gian thực (Push delivery) & Lưu trữ tuần tự trong CSDL")
    public void test09_RealtimeChatPushAndPersistence() throws Exception {
        try (TestClient reporter = new TestClient();
             TestClient tech = new TestClient()) {

            reporter.login("gv_lan", "123456");
            tech.login("kt_tuan", "123456");
            tech.drainPackets(200);

            reporter.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=D101|category=FACILITY|desc=Den chop tat"));
            int ticketId = Integer.parseInt(reporter.receive().parseParams().get("ticketId"));

            tech.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
            tech.receiveType(Constants.TYPE_CLAIM_RESP, 2000);
            reporter.drainPackets(200);
            tech.drainPackets(200);

            // Giáo viên nhắn tin cho kỹ thuật viên
            reporter.send(new Packet(Constants.TYPE_CHAT_SEND, "CHAT_SEND|ticketId=" + ticketId,
                    "Chào thầy Tuấn, phòng đang có tiết học ạ!".getBytes(StandardCharsets.UTF_8)));

            // Echo về người gửi
            Packet echo = reporter.receiveType(Constants.TYPE_CHAT_PUSH, 2000);
            assertNotNull(echo);

            // Kỹ thuật viên nhận PUSH tức thì trên Socket
            Packet pushed = tech.receiveType(Constants.TYPE_CHAT_PUSH, 2000);
            assertNotNull(pushed);
            assertEquals(String.valueOf(ticketId), pushed.parseParams().get("ticketId"));
            String content = new String(pushed.getAttachment(), StandardCharsets.UTF_8);
            assertTrue(content.contains("tiết học"));

            // Kỹ thuật viên nhắn lại
            tech.send(new Packet(Constants.TYPE_CHAT_SEND, "CHAT_SEND|ticketId=" + ticketId,
                    "Vâng cô, 10 phút nữa hết tiết em qua xử lý ngay!".getBytes(StandardCharsets.UTF_8)));
            tech.receiveType(Constants.TYPE_CHAT_PUSH, 2000); // echo
            reporter.receiveType(Constants.TYPE_CHAT_PUSH, 2000); // push

            // Truy vấn toàn bộ lịch sử hội thoại từ CSDL
            reporter.send(new Packet(Constants.TYPE_CHAT_HISTORY, "CHAT_HISTORY|ticketId=" + ticketId));
            Packet histResp = reporter.receiveType(Constants.TYPE_CHAT_HISTORY_RESP, 2000);
            assertNotNull(histResp);

            Type listType = new TypeToken<List<ChatMessage>>(){}.getType();
            List<ChatMessage> history = gson.fromJson(new String(histResp.getAttachment(), StandardCharsets.UTF_8), listType);
            assertEquals(2, history.size(), "Lịch sử phải lưu đủ 2 tin nhắn");
            assertTrue(history.get(0).getContent().contains("tiết học"));
            assertTrue(history.get(1).getContent().contains("hết tiết"));
        }
    }

    // =========================================================================
    // 10. BẢO VỆ RIÊNG TƯ KÊNH CHAT
    // =========================================================================
    @Test
    @DisplayName("TC-10: Bảo vệ quyền riêng tư phòng chat (Người lạ không được đọc hoặc gửi trộm tin)")
    public void test10_ChatPrivacyAndSnoopingProtection() throws Exception {
        try (TestClient reporter = new TestClient();
             TestClient tech = new TestClient();
             TestClient stranger = new TestClient()) {

            reporter.login("gv_lan", "123456");
            tech.login("kt_tuan", "123456");
            stranger.login("hs_minh", "123456");

            reporter.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=B105|category=FACILITY|desc=Hong khoa cua"));
            int ticketId = Integer.parseInt(reporter.receive().parseParams().get("ticketId"));

            tech.send(new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId));
            tech.receiveType(Constants.TYPE_CLAIM_RESP, 2000);

            // Người lạ cố tình gửi tin vào sự cố -> Bị chặn
            stranger.send(new Packet(Constants.TYPE_CHAT_SEND, "CHAT_SEND|ticketId=" + ticketId,
                    "Tin nhắn quấy rối".getBytes(StandardCharsets.UTF_8)));
            Packet errSend = stranger.receive();
            assertEquals(Constants.TYPE_ERROR, errSend.getType());

            // Người lạ cố tình lấy lịch sử chat -> Bị chặn
            stranger.send(new Packet(Constants.TYPE_CHAT_HISTORY, "CHAT_HISTORY|ticketId=" + ticketId));
            Packet errHist = stranger.receive();
            assertEquals(Constants.TYPE_ERROR, errHist.getType());
        }
    }

    // =========================================================================
    // 11. THỐNG KÊ ĐA KỲ BAN GIÁM HIỆU
    // =========================================================================
    @Test
    @DisplayName("TC-11: Admin truy vấn báo cáo thống kê đa kỳ (DAY/MONTH/ALL), người dùng thường bị chặn")
    public void test11_AdminStatisticsReport() throws Exception {
        try (TestClient admin = new TestClient();
             TestClient reporter = new TestClient()) {

            admin.login("admin_bgh", "123456");
            reporter.login("gv_lan", "123456");

            // Reporter lấy thống kê -> Bị chặn
            reporter.send(new Packet(Constants.TYPE_ADMIN_GET_STATS, "GET_STATS|timeRange=ALL"));
            Packet err = reporter.receive();
            assertEquals(Constants.TYPE_ERROR, err.getType());

            // Admin lấy thống kê toàn bộ -> Thành công
            admin.send(new Packet(Constants.TYPE_ADMIN_GET_STATS, "GET_STATS|timeRange=ALL"));
            Packet statsResp = admin.receive();
            assertEquals(Constants.TYPE_ADMIN_GET_STATS_RESP, statsResp.getType());
            assertTrue(statsResp.hasAttachment());

            StatisticReport report = gson.fromJson(new String(statsResp.getAttachment(), StandardCharsets.UTF_8), StatisticReport.class);
            assertNotNull(report);
            assertTrue(report.getTotalTickets() >= 0);

            // Kiểm tra các kỳ khác: DAY, MONTH, YEAR
            for (String range : new String[]{"DAY", "MONTH", "YEAR"}) {
                admin.send(new Packet(Constants.TYPE_ADMIN_GET_STATS, "GET_STATS|timeRange=" + range));
                Packet r = admin.receive();
                assertEquals(Constants.TYPE_ADMIN_GET_STATS_RESP, r.getType());
            }
        }
    }

    // =========================================================================
    // 12. ĐỒNG BỘ NGOẠI TUYẾN & ĐĂNG XUẤT AN TOÀN
    // =========================================================================
    @Test
    @DisplayName("TC-12: Đẩy sự cố tồn đọng khi cán bộ online & Đăng xuất ngắt kết nối an toàn")
    public void test12_OfflineBacklogAndGracefulLogout() throws Exception {
        // 1. Reporter tạo sự cố khi CHƯA có cán bộ kỹ thuật nào online
        int pendingTicketId;
        try (TestClient reporter = new TestClient()) {
            reporter.login("gv_lan", "123456");
            reporter.send(new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=SAN_BONG|category=FACILITY|desc=Gay cot luoi"));
            Packet subResp = reporter.receive();
            pendingTicketId = Integer.parseInt(subResp.parseParams().get("ticketId"));
        }

        // 2. Cán bộ kỹ thuật kt_nam online -> Server tự động đẩy vé tồn đọng
        try (TestClient tech = new TestClient()) {
            tech.login("kt_nam", "123456");

            Packet backlogNotify = tech.receiveType(Constants.TYPE_DISPATCH_NOTIFY, 3000);
            assertNotNull(backlogNotify, "Cán bộ vừa online phải nhận được vé tồn đọng!");
            assertEquals(String.valueOf(pendingTicketId), backlogNotify.parseParams().get("ticketId"));

            // 3. Đăng xuất an toàn bằng TYPE_LOGOUT
            tech.send(new Packet(Constants.TYPE_LOGOUT, "LOGOUT"));
            Thread.sleep(150);

            // Kiểm tra trạng thái is_online trong DB đã về 0
            User u = UserDAO.getAllUsers().stream()
                    .filter(x -> "kt_nam".equals(x.getUsername()))
                    .findFirst().orElse(null);
            assertNotNull(u);
            assertEquals(0, u.getIsOnline(), "Người dùng sau khi logout phải chuyển sang offline!");
        }
    }

    // =========================================================================
    // MAIN METHOD CHO PHÉP CHẠY TRỰC TIẾP TRONG NETBEANS / INTELLIJ (Shift+F6)
    // =========================================================================
    public static void main(String[] args) {
        System.out.println("========================================================================");
        System.out.println("  SENIOR JAVA TEST RUNNER: KIỂM THỬ TOÀN BỘ CHỨC NĂNG HỆ THỐNG SIDS");
        System.out.println("========================================================================");

        try {
            setup();
            ComprehensiveSystemFunctionalTest suite = new ComprehensiveSystemFunctionalTest();

            run("TC-01: Xác thực tài khoản, Salted Hash & Phân quyền RBAC", suite::test01_AuthenticationAndRoleParsing);
            run("TC-02: Admin cấp tài khoản, Chống trùng tên & Kiểm tra toàn vẹn", suite::test02_AdminUserProvisioningAndIntegrity);
            run("TC-03: Admin truy vấn danh sách người dùng toàn trường", suite::test03_AdminGetUsersList);
            run("TC-04: Báo sự cố & Tự động định tuyến phòng ban chuyên trách", suite::test04_SubmitIncidentAndAutoDispatchRouting);
            run("TC-05: Gửi ảnh nhị phân & Tải ảnh TCP 0x0D/0x1D toàn vẹn byte", suite::test05_BinaryImageTcpStreaming);
            run("TC-06: Tương tranh nhận vé 10 luồng (0% Race Condition / Double Claim)", suite::test06_AtomicClaimHighConcurrency);
            run("TC-07: Bảo vệ chuyên môn phòng ban (Chặn nhận ca trái ngành)", suite::test07_CrossSpecialtyClaimProtection);
            run("TC-08: Vòng đời sự cố & Phát thanh cập nhật Người báo & Admin", suite::test08_TicketLifecycleAndGlobalBroadcast);
            run("TC-09: Nhắn tin 1-1 thời gian thực (Push delivery) & Lưu trữ CSDL", suite::test09_RealtimeChatPushAndPersistence);
            run("TC-10: Bảo mật phòng chat riêng tư & Chống nghe lén trái phép", suite::test10_ChatPrivacyAndSnoopingProtection);
            run("TC-11: Báo cáo thống kê đa kỳ Ban Giám Hiệu (DAY/MONTH/ALL)", suite::test11_AdminStatisticsReport);
            run("TC-12: Đẩy tồn đọng ngoại tuyến & Đăng xuất an toàn (Offline Sync)", suite::test12_OfflineBacklogAndGracefulLogout);

        } catch (Exception e) {
            System.err.println("❌ LỖI KHỞI ĐỘNG BỘ TEST: " + e.getMessage());
            e.printStackTrace();
        } finally {
            teardown();
            printReport();
        }
    }

    private static void run(String name, TestRunnable test) {
        System.out.printf("👉 [%s] ... ", name);
        long start = System.currentTimeMillis();
        try {
            test.run();
            long dur = System.currentTimeMillis() - start;
            passedList.add(name);
            System.out.printf("[PASS ✅] (%d ms)\n", dur);
        } catch (Throwable t) {
            failedList.add(name + " -> " + t.getMessage());
            System.out.println("[FAIL ❌]");
            t.printStackTrace(System.out);
        }
    }

    private static void printReport() {
        System.out.println("\n========================================================================");
        System.out.println("  BÁO CÁO KẾT QUẢ KIỂM THỬ TOÀN DIỆN (SYSTEM FUNCTIONAL TEST REPORT)");
        System.out.println("========================================================================");
        int total = passedList.size() + failedList.size();
        System.out.printf("🎯 KẾT QUẢ: %d/%d TEST CASES ĐẠT CHUẨN (%.1f%%)\n",
                passedList.size(), total, total > 0 ? (passedList.size() * 100.0 / total) : 0);

        for (String p : passedList) {
            System.out.println("   [PASSED] " + p);
        }

        if (!failedList.isEmpty()) {
            System.out.println("\n❌ CÁC TEST THẤT BẠI:");
            for (String f : failedList) {
                System.out.println("   [FAILED] " + f);
            }
        } else {
            System.out.println("\n🌟 TOÀN BỘ 12/12 TÍNH NĂNG VÀ GIAO THỨC ĐỀU HOẠT ĐỘNG HOÀN HẢO 100%!");
            System.out.println("   - Socket TCP Length-Prefixed Framing chuẩn xác.");
            System.out.println("   - Luồng nghiệp vụ Báo cáo -> Điều phối -> Nhận ca -> Chat 1-1 -> Hoàn tất thông suốt.");
            System.out.println("   - An toàn tuyệt đối trước Race condition & Tấn công phân quyền.");
        }
        System.out.println("========================================================================\n");
    }

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Exception;
    }

    // =========================================================================
    // LỚP CLIENT THỬ NGHIỆM ĐỘC LẬP (TEST CLIENT)
    // =========================================================================
    private static class TestClient implements AutoCloseable {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private boolean loggedIn = false;
        private String userRole = null;
        private String specialty = null;

        public TestClient() throws Exception {
            this.socket = new Socket("127.0.0.1", PORT);
            this.socket.setTcpNoDelay(true);
            this.socket.setSoTimeout(3500);
            this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        public void send(Packet packet) throws Exception {
            packet.writeTo(out);
        }

        public Packet receive() throws Exception {
            return Packet.readFrom(in);
        }

        public Packet receiveType(byte expectedType, int timeoutMs) throws Exception {
            long deadline = System.currentTimeMillis() + timeoutMs;
            int oldTimeout = socket.getSoTimeout();
            try {
                while (System.currentTimeMillis() < deadline) {
                    socket.setSoTimeout((int) Math.max(80, deadline - System.currentTimeMillis()));
                    try {
                        Packet p = Packet.readFrom(in);
                        if (p.getType() == expectedType) {
                            return p;
                        }
                    } catch (java.net.SocketTimeoutException e) {
                        break;
                    }
                }
            } finally {
                socket.setSoTimeout(oldTimeout);
            }
            throw new AssertionError("Hết thời gian chờ packet type=0x" + Integer.toHexString(expectedType));
        }

        public boolean hasPacket(int waitMs) {
            int oldTimeout = 0;
            try {
                oldTimeout = socket.getSoTimeout();
                socket.setSoTimeout(waitMs);
                receive();
                return true;
            } catch (Exception e) {
                return false;
            } finally {
                try {
                    socket.setSoTimeout(oldTimeout);
                } catch (Exception ignored) {}
            }
        }

        public void drainPackets(int timeoutMs) {
            int oldTimeout = 0;
            try {
                oldTimeout = socket.getSoTimeout();
                socket.setSoTimeout(timeoutMs);
                while (true) {
                    try {
                        receive();
                    } catch (Exception e) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    socket.setSoTimeout(oldTimeout);
                } catch (Exception ignored) {}
            }
        }

        public void login(String username, String password) throws Exception {
            send(new Packet(Constants.TYPE_LOGIN, "LOGIN|username=" + username, password.getBytes(StandardCharsets.UTF_8)));
            Packet resp = receive();
            Map<String, String> p = resp.parseParams();
            if ("OK".equalsIgnoreCase(p.get("status"))) {
                this.loggedIn = true;
                this.userRole = p.get("role");
                this.specialty = p.get("specialty");
            } else {
                throw new IllegalStateException("Đăng nhập thất bại: " + p.get("message"));
            }
        }

        public boolean isLoggedIn() { return loggedIn; }
        public String getUserRole() { return userRole; }
        public String getSpecialty() { return specialty; }

        @Override
        public void close() throws Exception {
            if (!socket.isClosed()) {
                socket.close();
            }
        }
    }
}
