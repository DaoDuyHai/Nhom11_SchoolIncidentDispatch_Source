package com.incidentreport.benchmark;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Quản lý cấu hình toàn cục và xuất báo cáo kết quả kiểm thử.
 * Hỗ trợ tự động nhận diện cấu hình khi chạy "Run File" trong NetBeans
 * hoặc chạy qua lệnh Terminal.
 */
public class BenchmarkContext {

    private static String targetHost = "26.51.180.99";
    private static int targetPort = 5000;
    private static final String REPORT_FILE = "reports/benchmark_report.md";
    private static boolean initialized = false;

    static {
        initEncoding();
    }

    public static void initEncoding() {
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    public static File findFile(String relativePath) {
        File f = new File(relativePath);
        if (f.exists()) return f;
        f = new File("../" + relativePath);
        if (f.exists()) return f;
        f = new File("../../" + relativePath);
        if (f.exists()) return f;
        return new File(relativePath);
    }

    public static synchronized void initTarget(String[] args) {
        initEncoding();
        if (initialized && (args == null || args.length == 0)) {
            return;
        }

        // 1. Kiểm tra nếu có đối số dòng lệnh từ Terminal
        if (args != null && args.length >= 1 && !args[0].trim().isEmpty()) {
            targetHost = args[0].trim();
            if (args.length >= 2 && !args[1].trim().isEmpty()) {
                try {
                    targetPort = Integer.parseInt(args[1].trim());
                } catch (Exception ignored) {}
            }
            initialized = true;
            return;
        }

        // 2. Nếu không có args (chạy Run File trong NetBeans), đọc từ config/benchmark.properties
        File configFile = findFile("config/benchmark.properties");
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                Properties props = new Properties();
                props.load(fis);
                targetHost = props.getProperty("server.host", "26.51.180.99").trim();
                targetPort = Integer.parseInt(props.getProperty("server.port", "5000").trim());
                System.out.printf("⚙️ [NetBeans Run] Đã nạp cấu hình từ %s -> Target Server: %s:%d\n",
                        configFile.getPath(), targetHost, targetPort);
            } catch (Exception e) {
                System.err.println("⚠️ Không thể đọc config/benchmark.properties: " + e.getMessage());
            }
        }

        // 3. Kiểm tra thông mạch: Nếu targetHost cấu hình không kết nối được, thử kiểm tra 127.0.0.1
        if (!isServerReachable(targetHost, targetPort, 1000) && !"127.0.0.1".equals(targetHost)) {
            if (isServerReachable("127.0.0.1", targetPort, 1000)) {
                System.out.printf("ℹ️ Server tại %s không phản hồi, tự động chuyển sang Localhost: 127.0.0.1:%d\n", targetHost, targetPort);
                targetHost = "127.0.0.1";
            }
        }

        initialized = true;
    }

    public static boolean isServerReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void setTarget(String host, int port) {
        targetHost = host;
        targetPort = port;
        initialized = true;
    }

    public static String getTargetHost() {
        return targetHost;
    }

    public static int getTargetPort() {
        return targetPort;
    }

    public static void log(String level, String msg) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        System.out.printf("[%s] [%-5s] %s\n", time, level, msg);
    }

    public static void logInfo(String msg) {
        log("INFO", msg);
    }

    public static void logSuccess(String msg) {
        log("PASS", "✔ " + msg);
    }

    public static void logWarning(String msg) {
        log("WARN", "⚠ " + msg);
    }

    public static void logError(String msg) {
        log("FAIL", "✖ " + msg);
    }

    public static void logMetric(String msg) {
        log("METR", "📊 " + msg);
    }

    public static synchronized void appendReport(String markdownContent) {
        try {
            File file = findFile(REPORT_FILE);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8, true))) {
                writer.println(markdownContent);
                writer.println();
            }
        } catch (Exception e) {
            System.err.println("Không thể ghi file báo cáo: " + e.getMessage());
        }
    }

    public static synchronized void initReportFile() {
        try {
            File file = findFile(REPORT_FILE);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8, false))) {
                writer.println("# BÁO CÁO KẾT QUẢ ĐO LƯỜNG HIỆU NĂNG HỆ THỐNG SIDS");
                writer.println("*School Incident Dispatch System - Performance & Reliability Benchmark*");
                writer.println();
                writer.printf("- **Thời gian thực hiện**: %s\n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                writer.printf("- **Mục tiêu kiểm thử**: `%s:%d`\n", targetHost, targetPort);
                writer.println("- **Mô hình**: Đa luồng Socket TCP đồng bộ (Synchronized TCP Sockets with CountDownLatch)");
                writer.println();
                writer.println("---");
                writer.println();
            }
        } catch (Exception e) {
            System.err.println("Không thể tạo file báo cáo: " + e.getMessage());
        }
    }
}
