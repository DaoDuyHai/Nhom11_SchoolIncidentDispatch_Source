package com.incidentreport.benchmark;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Quản lý cấu hình toàn cục và xuất báo cáo kết quả kiểm thử.
 */
public class BenchmarkContext {

    private static String targetHost = "127.0.0.1";
    private static int targetPort = 5000;
    private static final String REPORT_FILE = "reports/benchmark_report.md";

    public static void setTarget(String host, int port) {
        targetHost = host;
        targetPort = port;
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
            File file = new File(REPORT_FILE);
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
            File file = new File(REPORT_FILE);
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
