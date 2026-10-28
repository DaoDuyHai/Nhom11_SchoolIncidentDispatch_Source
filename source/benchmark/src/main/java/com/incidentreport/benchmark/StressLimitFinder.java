package com.incidentreport.benchmark;

import com.incidentreport.common.Constants;
import com.incidentreport.common.packet.Packet;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

/**
 * ============================================================================
 * CÔNG CỤ DÒ TÌM GIỚI HẠN CHỊU TẢI CỰC HẠN (BREAKING POINT STRESS FINDER)
 * ============================================================================
 * - Giúp bạn kiểm tra xem máy chủ chịu được tối đa bao nhiêu Client (150 -> 1000).
 * - HOÀN TOÀN AN TOÀN CHO PHẦN CỨNG MÁY TÍNH:
 *   Chỉ đo đạc phần mềm và tự ngắt khi chạm ngưỡng giới hạn của hệ điều hành.
 * - Cho phép chọn các mốc: 150, 250, 500, 1000 hoặc tự nhập số lượng tùy thích.
 */
public class StressLimitFinder {

    private static final byte[] TEST_PAYLOAD_50KB = new byte[50 * 1024]; // 50 KB vừa đủ để test áp lực cực hạn

    static {
        Arrays.fill(TEST_PAYLOAD_50KB, (byte) 0xBB);
    }

    public static void main(String[] args) {
        BenchmarkContext.initTarget(args);
        Scanner scanner = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);

        System.out.println("================================================================================");
        System.out.println("   🔥 CÔNG CỤ DÒ TÌM NGƯỠNG CHỊU TẢI CỰC HẠN (BREAKING POINT STRESS FINDER)     ");
        System.out.println("   Mục tiêu: Tìm ra điểm giới hạn tối đa mà hệ thống có thể gánh được          ");
        System.out.println("================================================================================");
        System.out.printf("🎯 Target Server: %s:%d\n\n", BenchmarkContext.getTargetHost(), BenchmarkContext.getTargetPort());

        boolean running = true;
        while (running) {
            System.out.println("---------------- HÃY CHỌN MỨC TẢI BẠN MUỐN THỬ THÁCH SERVER ----------------");
            System.out.println(" [1] Thử mức 150 Clients đồng thời (Vượt nhẹ chuẩn trường học)");
            System.out.println(" [2] Thử mức 250 Clients đồng thời (Tương đương 1 trường học lớn)");
            System.out.println(" [3] Thử mức 500 Clients đồng thời (Ngưỡng cao điểm)");
            System.out.println(" [4] Thử mức 1000 Clients đồng thời (ÉP TẢI CỰC HẠN - BREAKING POINT TEST)");
            System.out.println(" [5] Tự nhập số Client bạn muốn thử (Ví dụ: 200, 300, 600, 800...)");
            System.out.println(" [0] Thoát");
            System.out.println("----------------------------------------------------------------------------");
            System.out.print("▶ Nhập lựa chọn của bạn: ");

            String input = scanner.hasNextLine() ? scanner.nextLine().trim() : "0";

            int targetClients = 0;
            switch (input) {
                case "1": targetClients = 150; break;
                case "2": targetClients = 250; break;
                case "3": targetClients = 500; break;
                case "4": targetClients = 1000; break;
                case "5":
                    System.out.print("👉 Nhập số lượng Client bạn muốn thử (VD 300): ");
                    try {
                        targetClients = Integer.parseInt(scanner.nextLine().trim());
                    } catch (Exception e) {
                        System.out.println("⚠️ Số không hợp lệ!");
                        continue;
                    }
                    break;
                case "0":
                    running = false;
                    System.out.println("👋 Thoát công cụ dò tìm giới hạn.");
                    continue;
                default:
                    System.out.println("⚠️ Lựa chọn không hợp lệ!");
                    continue;
            }

            if (targetClients > 0) {
                runStressTest(targetClients);
            }

            System.out.println("\nNhấn [Enter] để tiếp tục...");
            if (scanner.hasNextLine()) {
                scanner.nextLine();
            }
        }
    }

    public static void runStressTest(int clientCount) {
        String host = BenchmarkContext.getTargetHost();
        int port = BenchmarkContext.getTargetPort();

        System.out.println("\n" + "=".repeat(80));
        System.out.printf("🚀 BẮT ĐẦU ÉP TẢI CỰC HẠN: %d CLIENTS ĐỒNG THỜI...\n", clientCount);
        System.out.println("   Đang khởi tạo các kết nối Socket và luồng kiểm thử...");
        System.out.println("=".repeat(80));

        CountDownLatch readyGun = new CountDownLatch(clientCount);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);

        List<Double> latencies = Collections.synchronizedList(new ArrayList<>());
        LongAdder successAdder = new LongAdder();
        LongAdder failAdder = new LongAdder();

        ExecutorService executor = Executors.newFixedThreadPool(clientCount);

        long memBefore = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);

        for (int i = 0; i < clientCount; i++) {
            final int id = i;
            executor.execute(() -> {
                try (VirtualClient client = new VirtualClient(host, port, 35000)) {
                    client.login("gv_lan", "123456");
                    readyGun.countDown();

                    startGun.await(); // Đợi hiệu lệnh bùng nổ cùng 1 mili-giây

                    long t0 = System.nanoTime();
                    Packet resp = client.submitIncident("P" + (100 + (id % 100)), "FACILITY",
                            "Stress Test #" + id, TEST_PAYLOAD_50KB);
                    long t1 = System.nanoTime();

                    double ms = (t1 - t0) / 1_000_000.0;
                    latencies.add(ms);

                    if (resp.getType() == Constants.TYPE_SUBMIT_RESP
                            && "OK".equalsIgnoreCase(resp.parseParams().get("status"))) {
                        successAdder.increment();
                    } else {
                        failAdder.increment();
                    }
                } catch (Exception e) {
                    failAdder.increment();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        try {
            System.out.println("⏳ Đang chờ toàn bộ Client bắt tay Socket TCP và đăng nhập...");
            boolean allReady = readyGun.await(45, TimeUnit.SECONDS);

            long readyCount = clientCount - readyGun.getCount();
            System.out.printf("📊 Đã có %d / %d Client sẵn sàng trên bệ phóng!\n", readyCount, clientCount);

            System.out.println("💥 BẮN SÚNG XUẤT PHÁT: Đồng loạt phóng yêu cầu trong cùng 1 tích tắc!");
            long tStart = System.nanoTime();
            startGun.countDown();

            doneLatch.await(60, TimeUnit.SECONDS);
            long tEnd = System.nanoTime();
            executor.shutdownNow();

            double durationSec = (tEnd - tStart) / 1_000_000_000.0;
            long success = successAdder.sum();
            long fail = failAdder.sum();
            double successRate = (clientCount > 0) ? ((double) success / clientCount * 100.0) : 0;

            Collections.sort(latencies);
            double sum = 0;
            for (double d : latencies) sum += d;
            double avg = latencies.isEmpty() ? 0 : (sum / latencies.size());
            double max = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);

            long memAfter = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);

            System.out.println("\n" + "=".repeat(80));
            System.out.printf("🎯 KẾT QUẢ ĐO LƯỜNG SỨC TẢI TẠI MỨC %d CLIENTS:\n", clientCount);
            System.out.println("=".repeat(80));
            System.out.printf(" ✔ Số Client thành công : %d / %d (%.1f%%)\n", success, clientCount, successRate);
            System.out.printf(" ✖ Số Client thất bại   : %d\n", fail);
            System.out.printf(" ⏱ Thời gian giải quyết : %.2f giây\n", durationSec);
            System.out.printf(" ⚡ Độ trễ trung bình    : %.2f ms (Max: %.2f ms)\n", avg, max);
            System.out.printf(" 🧠 Bộ nhớ RAM JVM Client: Trước: %d MB | Sau: %d MB\n", memBefore, memAfter);

            if (successRate >= 95.0) {
                System.out.println("\n🏆 [ĐÁNH GIÁ]: HỆ THỐNG HOÀN TOÀN CHỊU ĐƯỢC MỨC TẢI NÀY! (Sức mạnh xuất sắc)");
            } else if (successRate >= 70.0) {
                System.out.println("\n⚠️ [ĐÁNH GIÁ]: BẮT ĐẦU CHẠM NGƯỠNG NGHẼN (Có hiện tượng suy giảm nhẹ do trễ mạng)");
            } else {
                System.out.println("\n🛑 [ĐÁNH GIÁ]: ĐÃ CHẠM ĐIỂM GÃY (BREAKING POINT)! (Mô hình Blocking Socket đạt giới hạn tài nguyên)");
            }
            System.out.println("=".repeat(80));

        } catch (Exception e) {
            System.err.println("Lỗi trong quá trình ép tải: " + e.getMessage());
        }
    }
}

