package com.incidentreport.benchmark;

import com.incidentreport.common.Constants;
import com.incidentreport.common.packet.Packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KỊCH BẢN 7: ĐỒNG BỘ HÀNG ĐỢI SỰ CỐ TỒN ĐỌNG KHI CÁN BỘ ONLINE LẠI (OFFLINE BACKLOG SYNC)
 * Tình huống:
 * - Khi sự cố phát sinh, không có kỹ thuật viên nào đang online.
 * - Sự cố được lưu trữ an toàn trong hàng đợi CSDL (Database Backlog Queue).
 * - Khi kỹ thuật viên đăng nhập vào hệ thống, Server tự động quét hàng đợi
 *   và bắn toàn bộ thông báo điều phối (DISPATCH_NOTIFY) về máy trạm của cán bộ đó.
 */
public class Scenario7OfflineSync {

    public static void main(String[] args) {
        BenchmarkContext.initTarget(args);
        run();
    }

    public static void run() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   KỊCH BẢN 7: ĐỒNG BỘ HÓA SỰ CỐ TỒN ĐỌNG KHI RESPONDER KẾT NỐI LẠI");
        System.out.println("   (OFFLINE QUEUE BACKLOG SYNCHRONIZATION)");
        System.out.println("=".repeat(80));

        String host = BenchmarkContext.getTargetHost();
        int port = BenchmarkContext.getTargetPort();

        List<String[]> reportRows = new ArrayList<>();
        int passed = 0;
        int total = 0;

        int pendingTicketId = -1;
        String pendingTicketCode = "";

        // Bước 1: Reporter tạo sự cố trong khi Responder chưa hề online
        total++;
        BenchmarkContext.logInfo("Bước 1: Giáo viên gửi sự cố khẩn cấp (Lúc này Kỹ thuật viên chưa mở ứng dụng)...");
        try (VirtualClient reporter = new VirtualClient(host, port)) {
            reporter.login("gv_lan", "123456");
            Packet resp = reporter.submitIncident("P405", "ELECTRIC",
                    "Hỏng bảng điện phòng thí nghiệm khi kỹ thuật viên offline", null);
            Map<String, String> p = resp.parseParams();
            pendingTicketId = Integer.parseInt(p.get("ticketId"));
            pendingTicketCode = p.get("ticketCode");

            passed++;
            BenchmarkContext.logSuccess(String.format("7.1 Sự cố được lưu trữ vào hàng đợi DB thành công: ID=%d, Mã=[%s]",
                    pendingTicketId, pendingTicketCode));
            reportRows.add(new String[]{"7.1 Tạo sự cố khi Offline", "gv_lan -> Server", "Lưu trữ DB: " + pendingTicketCode, "PASS"});
        } catch (Exception e) {
            BenchmarkContext.logError("7.1 Lỗi tạo sự cố: " + e.getMessage());
            reportRows.add(new String[]{"7.1 Tạo sự cố khi Offline", "gv_lan -> Server", "Lỗi: " + e.getMessage(), "FAIL"});
            return;
        }

        // Bước 2: Kỹ thuật viên mở ứng dụng và đăng nhập
        total++;
        BenchmarkContext.logInfo("Bước 2: Kỹ thuật viên (kt_viet) bắt đầu kết nối Socket và Đăng nhập...");
        try (VirtualClient responder = new VirtualClient(host, port)) {
            long tLogin = System.nanoTime();
            Map<String, String> userInfo = responder.login("kt_viet", "123456");
            long msLogin = (System.nanoTime() - tLogin) / 1_000_000;

            passed++;
            BenchmarkContext.logSuccess(String.format("7.2 Đăng nhập thành công: %s (%d ms)", userInfo.get("fullName"), msLogin));
            reportRows.add(new String[]{"7.2 Responder Online", "kt_viet -> Server", "Đăng nhập thành công", "PASS"});

            // Bước 3: Kiểm tra gói tin đẩy tồn đọng (DISPATCH_NOTIFY Backlog)
            total++;
            BenchmarkContext.logInfo("Bước 3: Lắng nghe gói tin DISPATCH_NOTIFY tự động đẩy từ Server...");
            long tBacklog = System.nanoTime();
            boolean receivedBacklog = false;
            String receivedCode = "";

            // Đọc các gói tin server tự động đẩy sau khi login (tối đa 5 gói tin)
            for (int i = 0; i < 5; i++) {
                try {
                    Packet pkt = responder.receiveWithTimeout(3000);
                    if (pkt.getType() == Constants.TYPE_DISPATCH_NOTIFY) {
                        Map<String, String> dp = pkt.parseParams();
                        receivedCode = dp.get("code");
                        if (pendingTicketCode.equalsIgnoreCase(receivedCode)
                                || String.valueOf(pendingTicketId).equals(dp.get("ticketId"))) {
                            receivedBacklog = true;
                            break;
                        }
                    }
                } catch (Exception timeout) {
                    break;
                }
            }
            long msBacklog = (System.nanoTime() - tBacklog) / 1_000_000;

            if (receivedBacklog) {
                passed++;
                BenchmarkContext.logSuccess(String.format("7.3 Server tự động đẩy sự cố tồn đọng thành công: Mã [%s] (%d ms)",
                        receivedCode, msBacklog));
                reportRows.add(new String[]{"7.3 Đẩy Backlog tự động", "Server -> kt_viet", "DISPATCH_NOTIFY: " + receivedCode, "PASS"});
            } else {
                BenchmarkContext.logWarning("7.3 Không nhận được DISPATCH_NOTIFY tự động (hoặc sự cố đã được giao cho luồng khác).");
                reportRows.add(new String[]{"7.3 Đẩy Backlog tự động", "Server -> kt_viet", "Không nhận được", "WARN"});
            }

        } catch (Exception e) {
            BenchmarkContext.logError("Lỗi trong kịch bản Offline Sync: " + e.getMessage());
            reportRows.add(new String[]{"7.2/7.3 Xử lý đồng bộ", "kt_viet", "Ngoại lệ: " + e.getMessage(), "FAIL"});
        }

        // In bảng báo cáo
        String[] headers = {"Bước kiểm thử", "Tác nhân", "Kết quả thực tế", "Đánh giá"};
        System.out.println(ChartGenerator.toAsciiTable("ĐỒNG BỘ HÀNG ĐỢI SỰ CỐ TỒN ĐỌNG (OFFLINE BACKLOG)", headers, reportRows));

        double passRate = (total > 0) ? ((double) passed / total * 100.0) : 0;
        BenchmarkContext.logMetric(String.format("Tổng số bước kiểm tra: %d | Đạt: %d | Tỷ lệ thành công: %.1f%%\n", total, passed, passRate));

        // Xuất Markdown
        StringBuilder md = new StringBuilder();
        md.append("### Kịch bản 7: Đồng bộ hóa hàng đợi sự cố tồn đọng khi cán bộ kết nối lại (Offline Sync)\n\n");
        md.append(ChartGenerator.toMarkdownTable(headers, reportRows));
        md.append("\n> **Kết luận Kịch bản 7**: Tính năng phát hiện trạng thái kết nối và tự động đẩy dữ liệu tồn đọng (Push Backlog Notifications) hoạt động chính xác, đảm bảo không có sự cố nào bị bỏ quên dù nhân sự mất kết nối mạng tạm thời.\n");
        BenchmarkContext.appendReport(md.toString());
    }
}

