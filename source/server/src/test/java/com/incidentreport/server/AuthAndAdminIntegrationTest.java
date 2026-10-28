package com.incidentreport.server;

import com.incidentreport.common.Constants;
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

@DisplayName("Kiểm thử Tích hợp Mạng: Đăng nhập tập trung & Admin cấp tài khoản")
public class AuthAndAdminIntegrationTest {

    private static final int TEST_PORT = 5098;
    private static ServerSocket testServerSocket;
    private static ExecutorService threadPool;
    private static volatile boolean running = true;

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

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }

    @Test
    public void testAdminLoginAndCreateUserFlow() throws Exception {
        try (TestClient client = new TestClient()) {
            // 1. Admin đăng nhập
            Packet loginPkt = new Packet(Constants.TYPE_LOGIN, "LOGIN|username=admin_bgh|password=123456");
            client.send(loginPkt);

            Packet loginResp = client.receive();
            assertEquals(Constants.TYPE_LOGIN_RESP, loginResp.getType());
            Map<String, String> loginParams = loginResp.parseParams();
            assertEquals("OK", loginParams.get("status"));
            assertEquals("ADMIN", loginParams.get("role"));

            // 2. Admin tạo tài khoản cho nhân sự mới
            String newUsername = "nv_it_" + System.currentTimeMillis();
            Packet createPkt = new Packet(Constants.TYPE_ADMIN_CREATE_USER,
                String.format("ADMIN_CREATE_USER|username=%s|fullName=Kỹ thuật IT|role=RESPONDER|specialty=TECHNICAL|phone=0912345678", newUsername),
                "pass123".getBytes(StandardCharsets.UTF_8));
            client.send(createPkt);

            Packet createResp = client.receive();
            assertEquals(Constants.TYPE_ADMIN_CREATE_RESP, createResp.getType());
            Map<String, String> createParams = createResp.parseParams();
            assertEquals("OK", createParams.get("status"));

            // 3. Nhân sự mới thử đăng nhập
            try (TestClient newClient = new TestClient()) {
                Packet newLoginPkt = new Packet(Constants.TYPE_LOGIN, String.format("LOGIN|username=%s|password=pass123", newUsername));
                newClient.send(newLoginPkt);

                Packet newLoginResp = newClient.receive();
                assertEquals(Constants.TYPE_LOGIN_RESP, newLoginResp.getType());
                Map<String, String> newParams = newLoginResp.parseParams();
                assertEquals("OK", newParams.get("status"));
                assertEquals("RESPONDER", newParams.get("role"));
                assertEquals("TECHNICAL", newParams.get("specialty"));
            }
        }
    }
}

