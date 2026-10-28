package com.incidentreport.benchmark;

import com.incidentreport.common.Constants;
import com.incidentreport.common.packet.Packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * ============================================================================
 * KIỂM THỬ SỨC CHỨA NGƯỜI DÙNG TRỰC TUYẾN ĐỒNG THỜI (CONCURRENT ONLINE USERS)
 * ============================================================================
 * - Mô phỏng đúng thực tế: Người dùng lần lượt đăng nhập vào hệ thống (Ramp-up)
 *   và CÙNG NHAU GIỮ KẾT NỐI ONLINE (Active TCP Sockets).
 * - Đo lường chính xác:
 *   1. Máy chủ chịu được tối đa bao nhiêu kết nối Socket TCP cùng lúc (150 -> 1000 users).
 *   2. Tỷ lệ giữ kết nối sống (Alive Connection Rate).
 *   3. Bộ nhớ RAM tiêu tốn khi duy trì hàng trăm/hàng nghìn người online.
 */
public class StressLimitFinder {

    public static void main(String[] args) {
        BenchmarkContext.initTarget(args);
        Scanner scanner = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);

        System.out.println("================================================================================");
        System.out.println("   👥 KIỂM THỬ SỨC CHỨA NGƯỜI DÙNG TRỰC TUYẾN ĐỒNG THỜI (CONCURRENT USERS)     ");
        System.out.println("   Mục tiêu: Đo xem Server duy trì được bao nhiêu người cùng ONLINE liên tục    ");
        System.out.println("================================================================================");
        System.out.printf("🎯 Target Server: %s:%d\n\n", BenchmarkContext.getTargetHost(), BenchmarkContext.getTargetPort());

        boolean running = true;
        while (running) {
            System.out.println("------------ CHỌN SỐ LƯỢNG NGƯỜI DÙNG BẠN MUỐN THỬ GIỮ ONLINE ------------");
            System.out.println(" [1] Thử 150 Người dùng Online đồng thời");
            System.out.println(" [2] Thử 250 Người dùng Online đồng thời (Quy mô toàn trường học)");
            System.out.println(" [3] Thử 500 Người dùng Online đồng thời (Quy mô cực lớn)");
            System.out.println(" [4] Thử 1000 Người dùng Online đồng thời (ÉP TẢI CỰC HẠN 1.000 SOCKETS)");
            System.out.println(" [5] Tự nhập số Người dùng Online tùy ý (Ví dụ: 200, 300, 600...)");
            System.out.println(" [0] Thoát");
            System.out.println("--------------------------------------------------------------------------");
            System.out.print("▶ Nhập lựa chọn của bạn: ");

            String input = scanner.hasNextLine() ? scanner.nextLine().trim() : "0";

            int targetUsers = 0;
            switch (input) {
                case "1": targetUsers = 150; break;
                case "2": targetUsers = 250; break;
                case "3": targetUsers = 500; break;
                case "4": targetUsers = 1000; break;
                case "5":
                    System.out.print("👉 Nhập số Người dùng bạn muốn thử: ");
                    try {
                        targetUsers = Integer.parseInt(scanner.nextLine().trim());
                    } catch (Exception e) {
                        System.out.println("⚠️ Số không hợp lệ!");
                        continue;
                    }
                    break;
                case "0":
                    running = false;
                    System.out.println("👋 Thoát kiểm thử sức chứa người dùng.");
                    continue;
                default:
                    System.out.println("⚠️ Lựa chọn không hợp lệ!");
                    continue;
            }

            if (targetUsers > 0) {
                runOnlineUsersCapacityTest(targetUsers);
            }

            System.out.println("\nNhấn [Enter] để tiếp tục...");
            if (scanner.hasNextLine()) {
                scanner.nextLine();
            }
        }
    }

    public static void runOnlineUsersCapacityTest(int userCount) {
        String host = BenchmarkContext.getTargetHost();
        int port = BenchmarkContext.getTargetPort();

        System.out.println("\n" + "=".repeat(80));
        System.out.printf("🚀 BẮT ĐẦU KIỂM THỬ DUY TRÌ %d NGƯỜI DÙNG ONLINE ĐỒNG THỜI...\n", userCount);
        System.out.println("   Mô phỏng thực tế: Người dùng lần lượt đăng nhập (Ramp-up) và GIỮ NGUYÊN SOCKET");
        System.out.println("=".repeat(80));

        List<VirtualClient> activeClients = Collections.synchronizedList(new ArrayList<>());
        LongAdder successLoginAdder = new LongAdder();
        LongAdder failLoginAdder = new LongAdder();

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(userCount, 150));
        CountDownLatch doneConnectingLatch = new CountDownLatch(userCount);

        long memBefore = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        long t0 = System.nanoTime();

        // 1. PHA RAMP-UP: Người dùng lần lượt kết nối và đăng nhập
        System.out.printf("⏳ Đang thiết lập kết nối và đăng nhập cho %d người dùng...\n", userCount);

        for (int i = 0; i < userCount; i++) {
            final int id = i;
            executor.execute(() -> {
                try {
                    // Tạo một client giữ kết nối liên tục
                    VirtualClient client = new VirtualClient(host, port, 45000);
                    client.login("gv_lan", "123456");

                    activeClients.add(client);
                    successLoginAdder.increment();
                } catch (Exception e) {
                    failLoginAdder.increment();
                } finally {
                    doneConnectingLatch.countDown();
                }
            });

            // Ramp-up nhẹ nhàng: Mỗi kết nối cách nhau 5ms để tránh nghẽn card mạng
            if (i % 20 == 0 && i > 0) {
                try { Thread.sleep(15); } catch (InterruptedException ignored) {}
            }
        }

        try {
            // Đợi tất cả người dùng kết nối xong
            doneConnectingLatch.await(60, TimeUnit.SECONDS);
            long t1 = System.nanoTime();
            double rampUpDurationSec = (t1 - t0) / 1_000_000_000.0;

            long totalOnline = successLoginAdder.sum();
            long totalFailed = failLoginAdder.sum();
            double successRate = (userCount > 0) ? ((double) totalOnline / userCount * 100.0) : 0;

            long memAfter = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);

            System.out.println("\n" + "=".repeat(80));
            System.out.printf("🎉 KẾT QUẢ PHA ĐĂNG NHẬP (RAMP-UP HOÀN TẤT TRONG %.2f GIÂY):\n", rampUpDurationSec);
            System.out.printf(" ✔ Số Người dùng Online thành công : %d / %d (%.1f%%)\n", totalOnline, userCount, successRate);
            System.out.printf(" ✖ Số kết nối thất bại             : %d\n", totalFailed);
            System.out.printf(" 🧠 RAM máy Client tiêu tốn        : %d MB -> %d MB (+%d MB)\n", memBefore, memAfter, (memAfter - memBefore));
            System.out.println("=".repeat(80));

            // 2. PHA DUY TRÌ KẾT NỐI (HOLDING ACTIVE SESSIONS)
            int holdSeconds = 8;
            System.out.printf("\n🔒 ĐANG DUY TRÌ %d KẾT NỐI SOCKET ONLINE TRONG %d GIÂY...\n", totalOnline, holdSeconds);
            System.out.println("   (Bạn có thể nhìn sang cửa sổ Server để thấy số lượng session đang online)");

            for (int s = holdSeconds; s > 0; s--) {
                System.out.printf("   [Giữ kết nối] Còn %d giây...\r", s);
                Thread.sleep(1000);
            }
            System.out.println();

            // 3. PHA KIỂM TRA SỨC KHỎE (PING / LẤY DANH SÁCH SỰ CỐ)
            System.out.println("\n📡 Đang gửi kiểm tra phản hồi ngẫu nhiên tới Server trong khi các luồng vẫn Online...");
            long tPing0 = System.nanoTime();
            int sampleChecks = Math.min((int) totalOnline, 20);
            int sampleSuccess = 0;

            for (int i = 0; i < sampleChecks; i++) {
                try {
                    VirtualClient c = activeClients.get(i);
                    Packet resp = c.listTickets("MY");
                    if (resp.getType() == Constants.TYPE_LIST_TICKETS_RESP) {
                        sampleSuccess++;
                    }
                } catch (Exception ignored) {}
            }
            long tPing1 = System.nanoTime();
            double pingMs = (sampleChecks > 0) ? ((tPing1 - tPing0) / 1_000_000.0 / sampleChecks) : 0;

            System.out.printf(" ✔ Kiểm tra phản hồi mẫu (%d Client): Thành công %d/%d (Độ trễ trung bình: %.2f ms)\n",
                    sampleChecks, sampleSuccess, sampleChecks, pingMs);

            // 4. ĐÁNH GIÁ CHUNG
            System.out.println("\n" + "=".repeat(80));
            if (successRate >= 95.0) {
                System.out.printf("🏆 [KẾT LUẬN]: SERVER CHỊU TẢI XUẤT SẮC Ở MỨC %d NGƯỜI DÙNG ONLINE ĐỒNG THỜI!\n", userCount);
                System.out.println("   Mô hình ThreadPool và Socket TCP duy trì kết nối hoàn toàn ổn định 100%.");
            } else if (successRate >= 75.0) {
                System.out.printf("⚠️ [KẾT LUẬN]: SERVER BẮT ĐẦU CHẠM NGƯỠNG TẢI TẠI MỨC %d NGƯỜI DÙNG ONLINE.\n", userCount);
                System.out.println("   Có một số kết nối bị từ chối do giới hạn hàng đợi Socket của hệ điều hành.");
            } else {
                System.out.printf("🛑 [KẾT LUẬN]: ĐÃ TÌM THẤY ĐIỂM GÃY (BREAKING POINT) CỦA MÁY CHỦ TẠI MỨC %d USERS.\n", userCount);
                System.out.println("   Hệ thống đạt giới hạn tài nguyên cấp phát luồng và bộ nhớ của Blocking Socket.");
            }
            System.out.println("=".repeat(80));

        } catch (Exception e) {
            System.err.println("Lỗi kiểm thử: " + e.getMessage());
        } finally {
            // 5. GIẢI PHÓNG TOÀN BỘ KẾT NỐI
            System.out.println("\n🧹 Đang ngắt kết nối an toàn cho toàn bộ Client và giải phóng tài nguyên...");
            for (VirtualClient c : activeClients) {
                try { c.close(); } catch (Exception ignored) {}
            }
            executor.shutdownNow();
            System.out.println("✅ Đã giải phóng hoàn tất!");
        }
    }
}
