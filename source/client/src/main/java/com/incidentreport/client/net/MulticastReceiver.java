package com.incidentreport.client.net;

import com.incidentreport.common.Constants;
import javafx.application.Platform;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

public class MulticastReceiver {

    public interface AlertListener {
        void onAlertReceived(String level, String message);
    }

    private MulticastSocket socket;
    private InetAddress group;
    private volatile boolean running = false;
    private Thread listenThread;
    private AlertListener listener;

    public MulticastReceiver(AlertListener listener) {
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) return;
        running = true;

        listenThread = new Thread(() -> {
            try {
                socket = new MulticastSocket(Constants.MULTICAST_PORT);
                group = InetAddress.getByName(Constants.MULTICAST_GROUP_IP);
                socket.joinGroup(group);

                System.out.printf("🚨 [MULTICAST RECEIVER] Đang lắng nghe kênh cảnh báo khẩn cấp tại %s:%d...\n",
                    Constants.MULTICAST_GROUP_IP, Constants.MULTICAST_PORT);

                byte[] buffer = new byte[2048];
                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String data = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();

                    // Format: ALERT|level=...|msg=...
                    String level = "CRITICAL";
                    String message = data;

                    if (data.startsWith("ALERT|")) {
                        String[] parts = data.split("\\|");
                        for (String part : parts) {
                            if (part.startsWith("level=")) level = part.substring(6);
                            else if (part.startsWith("msg=")) message = part.substring(4);
                        }
                    }

                    final String finalLevel = level;
                    final String finalMsg = message;

                    Platform.runLater(() -> {
                        if (listener != null) {
                            listener.onAlertReceived(finalLevel, finalMsg);
                        }
                    });
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("❌ Lỗi MulticastReceiver: " + e.getMessage());
                }
            } finally {
                stop();
            }
        }, "Multicast-Emergency-Listener");

        listenThread.setDaemon(true);
        listenThread.start();
    }

    public synchronized void stop() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                if (group != null) {
                    socket.leaveGroup(group);
                }
                socket.close();
            }
        } catch (Exception ignored) {}
    }
}

