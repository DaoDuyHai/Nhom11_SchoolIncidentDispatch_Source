package com.incidentreport.client.net;

import com.incidentreport.common.Constants;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UDPHeartbeatSender {

    private final String serverHost;
    private final int serverPort;
    private final int staffId;
    private volatile String availability = Constants.AVAIL_AVAILABLE;

    private ScheduledExecutorService scheduler;
    private DatagramSocket udpSocket;
    private volatile boolean running = false;

    public UDPHeartbeatSender(String serverHost, int staffId) {
        this.serverHost = serverHost;
        this.serverPort = Constants.UDP_HEARTBEAT_PORT;
        this.staffId = staffId;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        try {
            udpSocket = new DatagramSocket();
            scheduler = Executors.newSingleThreadScheduledExecutor();

            scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, Constants.UDP_HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);
            System.out.printf("💓 [UDP HEARTBEAT] Đã khởi động gửi nhịp tim định kỳ %ds/lần cho nhân viên #%d...\n",
                Constants.UDP_HEARTBEAT_INTERVAL_SEC, staffId);
        } catch (Exception e) {
            System.err.println("❌ Không thể khởi động UDPHeartbeatSender: " + e.getMessage());
        }
    }

    private void sendHeartbeat() {
        if (!running || udpSocket == null || udpSocket.isClosed()) return;
        try {
            String payload = staffId + "|" + availability;
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            InetAddress address = InetAddress.getByName(serverHost);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, serverPort);
            udpSocket.send(packet);
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi UDP Heartbeat: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        System.out.println("🛑 [UDP HEARTBEAT] Đã dừng gửi nhịp tim.");
    }
}

