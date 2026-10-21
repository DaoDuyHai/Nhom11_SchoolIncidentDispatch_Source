package com.incidentreport.server;

import com.incidentreport.common.Constants;
import com.incidentreport.service.CategoryDAO;
import com.incidentreport.service.InitDatabase;
import com.incidentreport.service.UserDAO;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Máy chủ Điều phối Sự cố & Quản trị Trung tâm (Central Server & Admin Engine):
 * - Tiếp nhận kết nối TCP từ Client (Reporter, Responder, Admin).
 * - Điều phối báo cáo tự động theo phòng ban kỹ thuật / y tế / an ninh.
 * - Hỗ trợ push tức thì khi cán bộ online và chuyển giao backlog khi cán bộ đăng nhập.
 * - Định tuyến kênh chat 1-1 riêng biệt và lưu vết kiểm toán cho Admin.
 */
public class Server {

    private static final ExecutorService clientThreadPool = Executors.newCachedThreadPool();
    private static volatile boolean running = true;
    private static ServerSocket serverSocket;

    public static void main(String[] args) {
        System.out.println("============================================================================");
        System.out.println("   HỆ THỐNG ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT - CENTRAL DISPATCH & ADMIN SERVER  ");
        System.out.println("   Kiến trúc: TCP Socket chuẩn Senior - Clean Architecture                  ");
        System.out.println("============================================================================");

        // 1. Đảm bảo CSDL và bảng biểu sẵn sàng
        try {
            InitDatabase.initDatabase();
        } catch (Exception e) {
            System.err.println("⚠️ Kiểm tra CSDL: " + e.getMessage());
        }

        // 2. Đặt lại trạng thái offline cho toàn bộ người dùng khi Server khởi động
        UserDAO.resetAllUsersOffline();

        // 3. Tải danh mục sự cố & quy tắc điều phối vào RAM cache
        CategoryDAO.refreshCache();
        System.out.println("✅ Đã nạp danh mục sự cố và cấu hình phòng ban vào RAM cache!");

        // 4. Khởi động TCP ServerSocket
        int port = Constants.TCP_PORT;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Đang dừng Server an toàn...");
            running = false;
            UserDAO.resetAllUsersOffline();
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException ignored) {}
            clientThreadPool.shutdownNow();
            System.out.println("👋 Server đã dừng hoàn tất.");
        }));

        try {
            serverSocket = new ServerSocket(port);
            System.out.printf("🚀 [TCP SERVER] Sẵn sàng lắng nghe kết nối tại cổng %d...\n", port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientThreadPool.submit(new ClientHandler(clientSocket));
                } catch (IOException e) {
                    if (!running) break;
                    System.err.println("⚠️ Lỗi tiếp nhận client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Lỗi ServerSocket: " + e.getMessage());
        }
    }
}
