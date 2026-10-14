package com.incidentreport.server.multicast;

import com.incidentreport.common.Constants;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Kênh UDP Multicast: Phát cảnh báo khẩn cấp toàn trường tức thời tới toàn bộ Client
 */
public class MulticastBroadcaster {

    public static boolean broadcastAlert(String alertLevel, String message, int adminId) {
        String payload = String.format("ALERT|level=%s|msg=%s|adminId=%d|time=%d",
            alertLevel, message, adminId, System.currentTimeMillis());

        byte[] buffer = payload.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress group = InetAddress.getByName(Constants.MULTICAST_GROUP_IP);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, Constants.MULTICAST_PORT);
            socket.send(packet);
            System.out.println("🚨 [MULTICAST SENT] Đã phát cảnh báo khẩn cấp tới nhóm " +
                Constants.MULTICAST_GROUP_IP + ":" + Constants.MULTICAST_PORT + " -> " + payload);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Lỗi MulticastBroadcaster: " + e.getMessage());
            return false;
        }
    }
}

