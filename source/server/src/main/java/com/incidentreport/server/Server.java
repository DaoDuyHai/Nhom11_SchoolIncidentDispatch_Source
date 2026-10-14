package com.incidentreport.server;

import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.server.dispatch.DispatchWorker;
import com.incidentreport.server.heartbeat.HeartbeatMonitor;
import com.incidentreport.server.queue.AgingPriorityQueue;
import com.incidentreport.service.CategoryDAO;
import com.incidentreport.service.InitDatabase;
import com.incidentreport.service.TicketDAO;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Máy chủ Điều phối Sự cố Trung tâm (Central Dispatch Server):
 * 1. Kênh 1: TCP Server (Cổng 5000) - Xác thực, Báo cáo kèm ảnh hiện trường, Server Push.
 * 2. Kênh 2: UDP Heartbeat Monitor (Cổng 5001) - Giám sát nhịp tim 3s & Auto-Failover rớt mạng 10s.
 * 3. Kênh 3: UDP Multicast Broadcaster (230.0.0.1:9999) - Cảnh báo khẩn cấp toàn trường.
 * 4. Lõi: AgingPriorityQueue (chống đói) + Smart Dispatch Worker.
 */
public class Server {

    private static final AgingPriorityQueue agingQueue = new AgingPriorityQueue();
    private static final ExecutorService clientThreadPool = Executors.newFixedThreadPool(100);
    private static DispatchWorker dispatchWorker;

    public static AgingPriorityQueue getAgingQueue() {
        return agingQueue;
    }

    public static void main(String[] args) {
        System.out.println("============================================================================");
        System.out.println("   HỆ THỐNG ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT - CENTRAL DISPATCH SERVER          ");
        System.out.println("   Phiên bản: 2.0 (TCP Socket + UDP Heartbeat + UDP Multicast + JavaFX)    ");
        System.out.println("============================================================================");

        // 1. Tải danh mục sự cố từ CSDL
        CategoryDAO.refreshCache();
        System.out.println("✅ Đã nạp danh mục sự cố từ CSDL thành công!");

        // 2. Nạp các sự cố PENDING sẵn có vào Aging Priority Queue
        List<IncidentTicket> pending = TicketDAO.getPendingTickets();
        for (IncidentTicket t : pending) {
            agingQueue.enqueue(t);
        }
        System.out.printf("✅ Đã phục hồi %d sự cố đang chờ vào Aging Priority Queue!\n", pending.size());

        // 3. Khởi động Smart Dispatch Worker
        dispatchWorker = new DispatchWorker(agingQueue);
        dispatchWorker.start();

        // 4. Khởi động Kênh 2: UDP Heartbeat Listener & Auto-Failover Monitor
        HeartbeatMonitor.start();

        // 5. Khởi động Kênh 1: TCP ServerSocket tiếp nhận kết nối
        int port = Constants.TCP_PORT;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.printf("✅ [TCP SERVER] Đang lắng nghe kết nối trên cổng TCP %d...\n", port);
            System.out.printf("✅ [UDP MULTICAST] Sẵn sàng phát cảnh báo tới nhóm %s:%d\n",
                Constants.MULTICAST_GROUP_IP, Constants.MULTICAST_PORT);

            // Hook dọn dẹp khi tắt Server
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n🛑 Đang dừng Server an toàn...");
                HeartbeatMonitor.stop();
                if (dispatchWorker != null) dispatchWorker.stop();
                clientThreadPool.shutdownNow();
            }));

            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientThreadPool.submit(new ClientHandler(clientSocket));
            }

        } catch (IOException e) {
            System.err.println("❌ Lỗi Server: " + e.getMessage());
        }
    }
}
