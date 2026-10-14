package com.incidentreport.simulator;

import com.incidentreport.common.Constants;
import com.incidentreport.common.packet.Packet;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Module thực nghiệm đo đạc chỉ số mạng (StressTestSimulator):
 * 1. Đo Response Time (Trung bình & p95) với N clients đồng thời qua giao thức 0xCAFE.
 * 2. Đo Throughput (req/s).
 * 3. Kiểm chứng Spatio-Temporal Deduplication (tỷ lệ gộp trùng lặp).
 * 4. Kiểm chứng chống Race Condition (2 Responders cùng nhận 1 ca).
 * 5. Xuất kết quả chuẩn định dạng CSV vào results/benchmark.csv.
 */
public class StressTestSimulator {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = Constants.TCP_PORT;
    private static final int NUM_CLIENTS = 50;

    public static void main(String[] args) throws Exception {
        System.out.println("============================================================================");
        System.out.println("   CHƯƠNG TRÌNH THỰC NGHIỆM ĐO ĐẠC HIỆU NĂNG MẠNG (STRESS TEST SIMULATOR)  ");
        System.out.println("   Mô hình: " + NUM_CLIENTS + " luồng Client kết nối đồng thời qua giao thức 0xCAFE");
        System.out.println("============================================================================");

        // KỊCH BẢN 1: Bắn tải SUBMIT_INCIDENT với N client đồng thời
        runConcurrentSubmitBenchmark(NUM_CLIENTS);

        // KỊCH BẢN 2: Kiểm chứng đụng độ Race Condition (Atomic Claim)
        runRaceConditionTest();
    }

    private static void runConcurrentSubmitBenchmark(int clientsCount) throws InterruptedException {
        System.out.println("\n--- BẮT ĐẦU KỊCH BẢN 1: ĐO TẢI ĐỒNG THỜI & DEDUPLICATION ---");

        CountDownLatch latch = new CountDownLatch(clientsCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger mergedCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= clientsCount; i++) {
            final int id = i;
            new Thread(() -> {
                long reqStart = System.currentTimeMillis();
                try (Socket socket = new Socket(HOST, PORT);
                     DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                     DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {

                    socket.setTcpNoDelay(true);

                    // 1. Đăng nhập
                    String loginHeader = "LOGIN|username=hs_minh|password=123456";
                    new Packet(Constants.TYPE_LOGIN, loginHeader).writeTo(out);

                    Packet loginResp = Packet.readFrom(in);
                    if (loginResp.getType() != Constants.TYPE_LOGIN_RESP || !loginResp.getHeaderPayload().contains("status=OK")) {
                        failCount.incrementAndGet();
                        return;
                    }

                    // 2. Gửi sự cố
                    // Cố tình tạo một số phòng trùng nhau (ví dụ chia dư cho 5) để đo Spatio-Temporal Deduplication
                    String room = "PHONG_TEST_" + (id % 5 + 1);
                    String category = (id % 3 == 0) ? "ELECTRIC" : "FACILITY";
                    int severity = (id % 5 == 0) ? 5 : 3;
                    String desc = "Báo cáo thử nghiệm số " + id + " tại " + room;

                    // Giả lập gửi kèm 1 bức ảnh giả lập 1KB
                    byte[] sampleImage = new byte[1024];
                    Arrays.fill(sampleImage, (byte) 0xFF);

                    String submitHeader = String.format("INCIDENT|room=%s|category=%s|severity=%d|desc=%s|reqId=sim_%d",
                        room, category, severity, desc, id);
                    new Packet(Constants.TYPE_SUBMIT_INCIDENT, submitHeader, sampleImage).writeTo(out);

                    Packet submitResp = Packet.readFrom(in);
                    if (submitResp.getType() == Constants.TYPE_SUBMIT_RESP) {
                        successCount.incrementAndGet();
                        long lat = System.currentTimeMillis() - reqStart;
                        latencies.add(lat);

                        if (submitResp.getHeaderPayload().contains("isMerged=1")) {
                            mergedCount.incrementAndGet();
                        }
                    } else {
                        failCount.incrementAndGet();
                    }

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.err.println("❌ Sim-Client-" + id + " lỗi: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }, "Sim-Client-" + id).start();
        }

        latch.await();
        long totalTime = System.currentTimeMillis() - startTime;

        // Tính toán các chỉ số
        Collections.sort(latencies);
        long avgLatency = 0;
        long p95Latency = 0;
        if (!latencies.isEmpty()) {
            long sum = 0;
            for (long l : latencies) sum += l;
            avgLatency = sum / latencies.size();
            int p95Index = (int) Math.ceil(0.95 * latencies.size()) - 1;
            p95Latency = latencies.get(Math.max(0, p95Index));
        }

        double throughput = (totalTime > 0) ? (successCount.get() * 1000.0 / totalTime) : 0;

        System.out.println("\n📊 KẾT QUẢ THỰC NGHIỆM ĐO TẢI:");
        System.out.printf("  - Tổng thời gian hoàn thành: %d ms\n", totalTime);
        System.out.printf("  - Số yêu cầu thành công: %d / %d (Thất bại: %d)\n", successCount.get(), clientsCount, failCount.get());
        System.out.printf("  - Số báo cáo được gom nhóm trùng (Deduplication): %d (Tỷ lệ: %.1f%%)\n",
            mergedCount.get(), (successCount.get() > 0 ? (mergedCount.get() * 100.0 / successCount.get()) : 0));
        System.out.printf("  - Độ trễ trung bình: %d ms\n", avgLatency);
        System.out.printf("  - Độ trễ p95: %d ms\n", p95Latency);
        System.out.printf("  - Throughput đạt được: %.2f req/s\n", throughput);

        // Lưu kết quả vào CSV
        saveResultToCsv(clientsCount, successCount.get(), failCount.get(), mergedCount.get(), avgLatency, p95Latency, throughput);
    }

    private static void runRaceConditionTest() throws Exception {
        System.out.println("\n--- BẮT ĐẦU KỊCH BẢN 2: KIỂM CHỨNG CHỐNG RACE CONDITION (ATOMIC CLAIM) ---");
        System.out.println("Giả lập 2 Responders (kt_tuan và kt_nam) cùng ấn nhận 1 Ticket tại cùng 1 mili-giây...");

        System.out.println("Tạo 1 sự cố mới ở trạng thái PENDING để 2 Responders tranh chấp nhận ca...");
        int targetTicketId = 0;
        try (Socket s = new Socket(HOST, PORT);
             DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
             DataInputStream i = new DataInputStream(new BufferedInputStream(s.getInputStream()))) {
            new Packet(Constants.TYPE_LOGIN, "LOGIN|username=hs_minh|password=123456").writeTo(o);
            Packet.readFrom(i);
            String uniqueRoom = "RACE_ROOM_" + (System.currentTimeMillis() % 100000);
            new Packet(Constants.TYPE_SUBMIT_INCIDENT, "INCIDENT|room=" + uniqueRoom + "|category=ELECTRIC|severity=4|desc=Test Race Condition").writeTo(o);
            Packet resp = Packet.readFrom(i);
            Map<String, String> p = resp.parseParams();
            targetTicketId = Integer.parseInt(p.get("ticketId"));
            System.out.printf("  👉 Đã tạo sự cố test #%d (%s)!\n", targetTicketId, p.get("ticketCode"));
        }

        final int raceTicketId = targetTicketId;
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger acceptSuccess = new AtomicInteger(0);
        AtomicInteger taskAlreadyTaken = new AtomicInteger(0);

        ClaimRunner claimTask = (username) -> {
            try (Socket socket = new Socket(HOST, PORT);
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                 DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {

                // Login
                new Packet(Constants.TYPE_LOGIN, "LOGIN|username=" + username + "|password=123456").writeTo(out);
                Packet loginResp = Packet.readFrom(in);

                // Bắn ACK_TASK ACCEPT
                new Packet(Constants.TYPE_ACK_TASK, "ACK_TASK|ticketId=" + raceTicketId + "|action=ACCEPT").writeTo(out);
                Packet ackResp = Packet.readFrom(in);

                if (ackResp.getType() == Constants.TYPE_ACK_RESP) {
                    if (ackResp.getHeaderPayload().contains("status=OK")) {
                        acceptSuccess.incrementAndGet();
                        System.out.printf("  👉 [%s] Nhận ca THÀNH CÔNG (status=OK)\n", username);
                    } else if (ackResp.getHeaderPayload().contains("TASK_ALREADY_TAKEN")) {
                        taskAlreadyTaken.incrementAndGet();
                        System.out.printf("  ✋ [%s] Bị từ chối: Sự cố đã có người nhận trước! (TASK_ALREADY_TAKEN)\n", username);
                    }
                }
            } catch (Exception e) {
                System.err.println("Lỗi claimTask: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        };

        Thread t1 = new Thread(() -> claimTask.run("kt_tuan"));
        Thread t2 = new Thread(() -> claimTask.run("kt_nam"));

        t1.start();
        t2.start();
        latch.await();

        System.out.println("📊 KẾT QUẢ ĐỤNG ĐỘ:");
        System.out.printf("  - Số người nhận thành công: %d (Kỳ vọng: 1)\n", acceptSuccess.get());
        System.out.printf("  - Số người nhận TASK_ALREADY_TAKEN: %d (Kỳ vọng: 1)\n", taskAlreadyTaken.get());
        if (acceptSuccess.get() <= 1) {
            System.out.println("  ✅ HOÀN HẢO! Hệ thống ngăn chặn 100% hiện tượng tranh chấp nhận trùng ca!");
        } else {
            System.err.println("  ❌ PHÁT HIỆN LỖI: Có hơn 1 người nhận ca trùng lặp!");
        }
    }

    interface ClaimRunner {
        void run(String username);
    }

    private static void saveResultToCsv(int clients, int success, int fail, int merged, long avgLat, long p95Lat, double throughput) {
        File dir = new File("results");
        if (!dir.exists()) dir = new File("../results");
        if (!dir.exists()) dir = new File("../../results");
        if (!dir.exists()) dir.mkdirs();

        File csv = new File(dir, "benchmark.csv");
        boolean exists = csv.exists();

        try (FileWriter fw = new FileWriter(csv, true)) {
            if (!exists) {
                fw.write("Timestamp,ConcurrentClients,Success,Fail,DeduplicatedCount,AvgLatencyMs,P95LatencyMs,ThroughputReqSec\n");
            }
            fw.write(String.format("%d,%d,%d,%d,%d,%d,%d,%.2f\n",
                System.currentTimeMillis(), clients, success, fail, merged, avgLat, p95Lat, throughput));
            System.out.println("📁 Đã lưu kết quả thực nghiệm vào: " + csv.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ Lỗi ghi CSV: " + e.getMessage());
        }
    }
}
