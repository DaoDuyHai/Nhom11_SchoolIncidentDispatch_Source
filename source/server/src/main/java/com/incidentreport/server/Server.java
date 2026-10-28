package com.incidentreport.server;

import com.incidentreport.common.Constants;
import com.incidentreport.service.CategoryDAO;
import com.incidentreport.service.DatabaseConnection;
import com.incidentreport.service.InitDatabase;
import com.incidentreport.service.UserDAO;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.*;

/**
 * Máy chủ TCP Trung tâm kiêm Admin Hệ thống (Clean Architecture):
 * - Nạp cấu hình từ config/server.properties (fallback default Constants.TCP_PORT = 5000).
 * - Sử dụng ThreadPoolExecutor có giới hạn dung lượng (Bounded Thread Pool) chống cạn kiệt tài nguyên.
 * - Kiểm tra CSDL an toàn (không xóa bảng khi restart).
 * - Khởi tạo Socket Server lắng nghe các Client kết nối.
 */
public class Server {

    private static ExecutorService clientThreadPool;
    private static volatile boolean running = true;
    private static ServerSocket serverSocket;

    public static void main(String[] args) {
        try {
            System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
            System.setErr(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err), true, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {}

        System.out.println("============================================================================");
        System.out.println("   HỆ THỐNG ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT - CENTRAL DISPATCH & ADMIN SERVER  ");
        System.out.println("   Kiến trúc: TCP Socket chuẩn Senior - Clean Architecture                  ");
        System.out.println("============================================================================");

        // 1. Nạp cấu hình từ config/server.properties
        Properties props = new Properties();
        File configFile = InitDatabase.findFile("config/server.properties");
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                System.out.println("⚙️ Đã nạp cấu hình từ: " + configFile.getPath());
            } catch (Exception e) {
                System.err.println("⚠️ Không thể đọc file cấu hình: " + e.getMessage());
            }
        }

        int port = Integer.parseInt(props.getProperty("server.port", String.valueOf(Constants.TCP_PORT)));
        int maxThreads = Integer.parseInt(props.getProperty("server.max_threads", "50"));

        // Khởi tạo ThreadPool có giới hạn dung lượng chống cạn kiệt tài nguyên
        clientThreadPool = new ThreadPoolExecutor(
                10, maxThreads, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadFactory() {
                    private int count = 1;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "ClientWorker-" + (count++));
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 2. Đảm bảo CSDL và bảng biểu sẵn sàng (giữ nguyên dữ liệu)
        try {
            if (DatabaseConnection.getCustomDbUrl() == null) {
                String dbProp = props.getProperty("database.url");
                if (dbProp != null && !dbProp.trim().isEmpty()) {
                    File dbF = InitDatabase.findFile(dbProp);
                    DatabaseConnection.setCustomDbUrl("jdbc:sqlite:" + dbF.getAbsolutePath());
                }
            }
            System.out.println("🗄️ CSDL đang sử dụng: " + (DatabaseConnection.getCustomDbUrl() != null
                    ? DatabaseConnection.getCustomDbUrl()
                    : DatabaseConnection.getDatabaseFile().getAbsolutePath()));
            InitDatabase.initDatabase();
        } catch (Exception e) {
            System.err.println("⚠️ Kiểm tra CSDL: " + e.getMessage());
        }

        // 3. Đặt lại trạng thái offline cho toàn bộ người dùng khi Server khởi động
        UserDAO.resetAllUsersOffline();

        // 4. Tải danh mục sự cố & quy tắc điều phối vào RAM cache
        CategoryDAO.refreshCache();
        System.out.println("✅ Đã nạp danh mục sự cố và cấu hình phòng ban vào RAM cache!");

        // 5. Khởi động TCP ServerSocket
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
            System.out.printf("🚀 Server đang lắng nghe trên cổng TCP: %d (Max Threads: %d)...\n", port, maxThreads);

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    clientThreadPool.execute(new ClientHandler(socket));
                } catch (IOException e) {
                    if (!running) {
                        break;
                    }
                    System.err.println("⚠️ Lỗi accept kết nối: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("💥 Không thể mở cổng TCP " + port + ": " + e.getMessage());
        } finally {
            running = false;
            clientThreadPool.shutdown();
        }
    }
}
