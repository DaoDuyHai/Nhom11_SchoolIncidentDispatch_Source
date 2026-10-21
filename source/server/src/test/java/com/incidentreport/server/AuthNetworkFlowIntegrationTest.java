package com.incidentreport.server;

import com.incidentreport.common.Constants;
import com.incidentreport.common.packet.Packet;
import com.incidentreport.service.CategoryDAO;
import com.incidentreport.service.UserDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử Tích hợp Mạng Đầu-Cuối: Register, Guest Mode, Admin Create User, UDP OTP Reset Password")
public class AuthNetworkFlowIntegrationTest {

    private static final int TEST_PORT = 5098;
    private static ServerSocket testServerSocket;
    private static ExecutorService threadPool;
    private static volatile boolean running = true;

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
            this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        public void send(Packet p) throws Exception {
            p.writeTo(out);
        }

        public Packet read() throws Exception {
            return Packet.readFrom(in);
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    @Test
    @DisplayName("TCP 1: Luồng Đăng ký tài khoản công cộng (REGISTER -> REGISTER_RESP)")
    void testTcpRegisterFlow() throws Exception {
        try (TestClient client = new TestClient()) {
            String rand = UUID.randomUUID().toString().substring(0, 6);
            String username = "hs_net_" + rand;
            String header = String.format("REGISTER|username=%s|fullName=%s|phone=0912345678|subRole=%s",
                username, "Học sinh Mạng " + rand, Constants.SUB_ROLE_STUDENT);
            byte[] attach = "Password123".getBytes(StandardCharsets.UTF_8);

            client.send(new Packet(Constants.TYPE_REGISTER, header, attach));
            Packet resp = client.read();

            assertEquals(Constants.TYPE_REGISTER_RESP, resp.getType());
            Map<String, String> p = resp.parseParams();
            assertEquals("OK", p.get("status"));
            assertEquals(username, p.get("username"));
            assertEquals(Constants.ROLE_REPORTER, p.get("role"));
            assertEquals(Constants.SUB_ROLE_STUDENT, p.get("subRole"));

            // Kiểm tra đăng nhập bằng tài khoản vừa tạo
            client.send(new Packet(Constants.TYPE_LOGIN, "LOGIN|username=" + username + "|password=Password123"));
            Packet loginResp = client.read();
            assertEquals(Constants.TYPE_LOGIN_RESP, loginResp.getType());
            Map<String, String> lp = loginResp.parseParams();
            assertEquals("OK", lp.get("status"));
            assertEquals(username, lp.get("username"));
        }
    }

    @Test
    @DisplayName("TCP 2: Luồng Đăng nhập Khách (GUEST_LOGIN -> LOGIN_RESP)")
    void testTcpGuestLoginFlow() throws Exception {
        try (TestClient client = new TestClient()) {
            client.send(new Packet(Constants.TYPE_GUEST_LOGIN, "GUEST_LOGIN"));
            Packet resp = client.read();

            assertEquals(Constants.TYPE_LOGIN_RESP, resp.getType());
            Map<String, String> p = resp.parseParams();
            assertEquals("OK", p.get("status"));
            assertTrue(p.get("username").startsWith("guest_"));
            assertEquals(Constants.ROLE_REPORTER, p.get("role"));
            assertEquals(Constants.SUB_ROLE_GUEST, p.get("subRole"));
            assertEquals(Constants.SPECIALTY_NONE, p.get("specialty"));
        }
    }

    @Test
    @DisplayName("TCP 3: Luồng Admin tạo tài khoản nhân sự (ADMIN_CREATE_USER -> ADMIN_CREATE_RESP)")
    void testTcpAdminCreateUserFlow() throws Exception {
        try (TestClient client = new TestClient()) {
            // Admin đăng nhập (sử dụng tài khoản admin_bgh mặc định)
            client.send(new Packet(Constants.TYPE_LOGIN, "LOGIN|username=admin_bgh|password=123456"));
            Packet loginResp = client.read();
            assertEquals("OK", loginResp.parseParams().get("status"));

            // Admin tạo Kỹ thuật viên mới
            String rand = UUID.randomUUID().toString().substring(0, 6);
            String username = "tech_net_" + rand;
            String header = String.format("ADMIN_CREATE_USER|username=%s|fullName=KTV Mạng %s|phone=0909999999|role=%s|subRole=%s|specialty=%s",
                username, rand, Constants.ROLE_RESPONDER, Constants.SUB_ROLE_TECH_STAFF, Constants.SPECIALTY_TECHNICAL);
            byte[] attach = "TechPass@123".getBytes(StandardCharsets.UTF_8);

            client.send(new Packet(Constants.TYPE_ADMIN_CREATE_USER, header, attach));
            Packet createResp = client.read();

            assertEquals(Constants.TYPE_ADMIN_CREATE_RESP, createResp.getType());
            Map<String, String> cp = createResp.parseParams();
            assertEquals("OK", cp.get("status"));
            assertEquals(username, cp.get("username"));
            assertEquals(Constants.ROLE_RESPONDER, cp.get("role"));
            assertEquals(Constants.SPECIALTY_TECHNICAL, cp.get("specialty"));
        }
    }

    @Test
    @DisplayName("TCP + UDP 4: Khôi phục mật khẩu qua UDP Socket OTP & Đặt lại mật khẩu mới")
    void testForgotAndResetPasswordViaUdpOtp() throws Exception {
        // Tạo trước 1 tài khoản để thử nghiệm quên mật khẩu
        String rand = UUID.randomUUID().toString().substring(0, 6);
        String username = "user_otp_" + rand;
        String initialPass = "OldSecret@123";

        try (TestClient regClient = new TestClient()) {
            String header = String.format("REGISTER|username=%s|fullName=%s|phone=0933333333|subRole=%s",
                username, "User OTP Test " + rand, Constants.SUB_ROLE_STUDENT);
            regClient.send(new Packet(Constants.TYPE_REGISTER, header, initialPass.getBytes(StandardCharsets.UTF_8)));
            Packet regResp = regClient.read();
            assertEquals("OK", regResp.parseParams().get("status"));
        }

        // Bước 1: Client mở DatagramSocket lắng nghe tại cổng UDP ngẫu nhiên
        try (DatagramSocket udpSocket = new DatagramSocket(0);
             TestClient client = new TestClient()) {

            int clientUdpPort = udpSocket.getLocalPort();
            udpSocket.setSoTimeout(5000); // 5s timeout

            // Bước 2: Client gửi TCP FORGOT_PASSWORD
            String forgotHeader = String.format("FORGOT_PASSWORD|username=%s|udpPort=%d", username, clientUdpPort);
            client.send(new Packet(Constants.TYPE_FORGOT_PASSWORD, forgotHeader));

            // Server phản hồi TCP FORGOT_RESP
            Packet forgotResp = client.read();
            assertEquals(Constants.TYPE_FORGOT_RESP, forgotResp.getType());
            assertEquals("OK", forgotResp.parseParams().get("status"));

            // Bước 3: Client nhận gói tin UDP chứa mã OTP
            byte[] buf = new byte[1024];
            DatagramPacket udpPacket = new DatagramPacket(buf, buf.length);
            udpSocket.receive(udpPacket);

            String udpContent = new String(udpPacket.getData(), 0, udpPacket.getLength(), StandardCharsets.UTF_8);
            assertTrue(udpContent.startsWith("OTP|"), "Gói tin UDP phải chứa header OTP: " + udpContent);

            // Bóc tách mã OTP 6 chữ số
            String otpCode = "";
            for (String part : udpContent.split("\\|")) {
                if (part.startsWith("code=")) {
                    otpCode = part.substring("code=".length());
                    break;
                }
            }
            assertNotNull(otpCode);
            assertEquals(6, otpCode.length(), "Mã OTP phải đúng 6 chữ số");

            // Bước 4: Nhập OTP sai -> Reset phải thất bại
            String badResetHeader = String.format("RESET_PASSWORD|username=%s|otp=000000", username);
            client.send(new Packet(Constants.TYPE_RESET_PASSWORD, badResetHeader, "NewSecret@456".getBytes(StandardCharsets.UTF_8)));
            Packet badResetResp = client.read();
            assertEquals(Constants.TYPE_RESET_RESP, badResetResp.getType());
            assertEquals("FAIL", badResetResp.parseParams().get("status"));

            // Bước 5: Nhập OTP đúng -> Reset thành công
            String resetHeader = String.format("RESET_PASSWORD|username=%s|otp=%s", username, otpCode);
            String newPass = "BrandNewSecret@789";
            client.send(new Packet(Constants.TYPE_RESET_PASSWORD, resetHeader, newPass.getBytes(StandardCharsets.UTF_8)));
            Packet resetResp = client.read();
            assertEquals(Constants.TYPE_RESET_RESP, resetResp.getType());
            assertEquals("OK", resetResp.parseParams().get("status"));

            // Bước 6: Kiểm tra đăng nhập lại bằng mật khẩu mới
            client.send(new Packet(Constants.TYPE_LOGIN, "LOGIN|username=" + username + "|password=" + newPass));
            Packet newLoginResp = client.read();
            assertEquals("OK", newLoginResp.parseParams().get("status"));

            // Đăng nhập mật khẩu cũ phải bị từ chối
            try (TestClient clientOld = new TestClient()) {
                clientOld.send(new Packet(Constants.TYPE_LOGIN, "LOGIN|username=" + username + "|password=" + initialPass));
                Packet oldLoginResp = clientOld.read();
                assertEquals("FAIL", oldLoginResp.parseParams().get("status"));
            }
        }
    }
}
