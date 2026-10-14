package com.incidentreport.server.heartbeat;

import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.packet.Packet;
import com.incidentreport.server.Server;
import com.incidentreport.server.ServerSessionManager;
import com.incidentreport.service.DispatchLogDAO;
import com.incidentreport.service.TicketDAO;
import com.incidentreport.service.UserDAO;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Quản lý nhịp tim UDP Unicast và cơ chế Tự động chuyển giao lỗi (Auto-Failover Engine):
 * 1. Nhận gói tin UDP: staffId|availability định kỳ 3 giây/lần.
 * 2. Giám sát rớt mạng: Nếu quá 10 giây không có nhịp tim -> Tự động thu hồi sự cố và điều phối lại!
 */
public class HeartbeatMonitor {
    private static final Map<Integer, Long> lastHeartbeats = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static volatile boolean running = false;
    private static DatagramSocket udpSocket;

    public static void start() {
        if (running) return;
        running = true;

        // 1. Luồng lắng nghe UDP DatagramSocket cổng 5001
        Thread udpListenerThread = new Thread(() -> {
            try {
                udpSocket = new DatagramSocket(Constants.UDP_HEARTBEAT_PORT);
                System.out.println("✅ [UDP HEARTBEAT] Đang lắng nghe nhịp tim trên cổng UDP " + Constants.UDP_HEARTBEAT_PORT + "...");
                byte[] buffer = new byte[1024];

                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    String data = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();

                    // Format: staffId|status (Ví dụ: 4|AVAILABLE)
                    String[] parts = data.split("\\|");
                    if (parts.length >= 2) {
                        try {
                            int staffId = Integer.parseInt(parts[0].trim());
                            String status = parts[1].trim();
                            recordHeartbeat(staffId, status);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("❌ Lỗi UDPHeartbeatListener: " + e.getMessage());
                }
            }
        }, "UDP-Heartbeat-Listener");
        udpListenerThread.setDaemon(true);
        udpListenerThread.start();

        // 2. Bộ lập lịch rà soát Timeout (quét mỗi 2 giây)
        scheduler.scheduleAtFixedRate(HeartbeatMonitor::checkTimeouts, 2, 2, TimeUnit.SECONDS);
    }

    public static void stop() {
        running = false;
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        scheduler.shutdown();
    }

    public static void recordHeartbeat(int staffId, String availability) {
        lastHeartbeats.put(staffId, System.currentTimeMillis());
        UserDAO.updateHeartbeat(staffId, availability);
    }

    private static void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, Long> entry : lastHeartbeats.entrySet()) {
            int staffId = entry.getKey();
            long lastTime = entry.getValue();

            if (now - lastTime > Constants.UDP_HEARTBEAT_TIMEOUT_MS) {
                // Đã quá 10 giây không có nhịp tim -> Xử lý rớt mạng
                lastHeartbeats.remove(staffId);
                System.out.printf("⚠️ [UDP TIMEOUT] Nhân viên #%d mất nhịp tim sau %d ms! Kích hoạt Auto-Failover...\n",
                    staffId, (now - lastTime));

                UserDAO.setOnlineStatus(staffId, 0, Constants.AVAIL_OFFLINE);
                ServerSessionManager.removeClient(staffId);

                // Thu hồi toàn bộ ca đang gán cho nhân viên này
                triggerFailoverForStaff(staffId, (int) (now - lastTime));
            }
        }
    }

    private static void triggerFailoverForStaff(int staffId, int recoveryTimeMs) {
        List<IncidentTicket> activeTickets = TicketDAO.getTicketsByStaff(staffId);
        for (IncidentTicket ticket : activeTickets) {
            if (Constants.STATUS_ASSIGNED.equals(ticket.getStatus()) || Constants.STATUS_IN_PROGRESS.equals(ticket.getStatus())) {
                boolean reset = TicketDAO.resetTicketToPendingForFailover(ticket.getTicketId());
                if (reset) {
                    DispatchLogDAO.recordFailover(ticket.getTicketId(), staffId, recoveryTimeMs, "Mất UDP Heartbeat > 10s");

                    // Đưa ngược sự cố vào Hàng đợi ưu tiên để DispatchWorker giao ngay cho người khác!
                    IncidentTicket refreshed = TicketDAO.getTicketById(ticket.getTicketId());
                    if (refreshed != null) {
                        Server.getAgingQueue().enqueue(refreshed);
                    }

                    // Thông báo cho Reporter biết sự cố đang được điều phối lại
                    String notifyHeader = String.format("STATUS_UPDATE|ticketId=%d|code=%s|status=PENDING|msg=%s",
                        ticket.getTicketId(), ticket.getTicketCode(), "Đang điều phối lại do nhân sự mất kết nối");
                    ServerSessionManager.broadcast(new Packet(Constants.TYPE_STATUS_UPDATE, notifyHeader));

                    System.out.printf("🔄 [AUTO-FAILOVER SUCCESS] Đã thu hồi sự cố %s (Phòng %s) từ nhân viên #%d -> Tái điều phối!\n",
                        ticket.getTicketCode(), ticket.getRoomLocation(), staffId);
                }
            }
        }
    }
}

