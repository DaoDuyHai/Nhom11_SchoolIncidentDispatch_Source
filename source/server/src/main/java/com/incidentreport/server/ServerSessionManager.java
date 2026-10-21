package com.incidentreport.server;

import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý phiên làm việc đa luồng (Thread-safe Session Manager):
 * - Lưu trữ kết nối của người dùng đang online (User ID -> tập hợp ClientHandler).
 * - Cung cấp cơ chế Server Push có định tuyến: tới người dùng cụ thể, theo phòng ban chuyên trách,
 *   hoặc toàn hệ thống.
 */
public class ServerSessionManager {

    private static final Map<Integer, Set<ClientHandler>> activeSessions = new ConcurrentHashMap<>();

    public static void addClient(int userId, ClientHandler handler) {
        activeSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(handler);
        System.out.printf("👤 [SESSION] User #%d online. Tổng phiên hoạt động: %d\n", userId, getOnlineCount());
    }

    public static void removeClient(int userId, ClientHandler handler) {
        Set<ClientHandler> set = activeSessions.get(userId);
        if (set != null) {
            set.remove(handler);
            if (set.isEmpty()) {
                activeSessions.remove(userId);
            }
            System.out.printf("👤 [SESSION] User #%d rời phiên. Còn lại: %d\n", userId, getOnlineCount());
        }
    }

    public static void removeClient(int userId) {
        Set<ClientHandler> set = activeSessions.remove(userId);
        if (set != null) {
            for (ClientHandler handler : set) {
                handler.closeConnection();
            }
            System.out.printf("👤 [SESSION] Đã đóng toàn bộ phiên của User #%d. Còn lại: %d\n", userId, getOnlineCount());
        }
    }

    public static boolean isUserOnline(int userId) {
        Set<ClientHandler> set = activeSessions.get(userId);
        return set != null && !set.isEmpty();
    }

    public static ClientHandler getClient(int userId) {
        Set<ClientHandler> set = activeSessions.get(userId);
        if (set != null && !set.isEmpty()) {
            return set.iterator().next();
        }
        return null;
    }

    /**
     * Gửi gói tin tới đích danh một người dùng (hỗ trợ nhiều phiên đăng nhập cùng lúc).
     */
    public static boolean sendToUser(int userId, Packet packet) {
        Set<ClientHandler> set = activeSessions.get(userId);
        if (set != null && !set.isEmpty()) {
            for (ClientHandler handler : set) {
                handler.enqueuePacket(packet);
            }
            return true;
        }
        return false;
    }

    /**
     * Gửi gói tin tới tất cả người dùng mang vai trò chỉ định (REPORTER, RESPONDER, ADMIN).
     */
    public static void sendToRole(String role, Packet packet) {
        for (Set<ClientHandler> set : activeSessions.values()) {
            for (ClientHandler handler : set) {
                User u = handler.getLoggedInUser();
                if (u != null && role.equalsIgnoreCase(u.getRole())) {
                    handler.enqueuePacket(packet);
                }
            }
        }
    }

    /**
     * Điều phối báo cáo tới phòng ban chuyên trách (TECHNICAL, MEDICAL, SECURITY).
     * Chỉ những cán bộ Responder thuộc đúng phòng ban (hoặc ALL) mới nhận được.
     */
    public static void sendToDepartment(String targetDepartment, Packet packet) {
        for (Set<ClientHandler> set : activeSessions.values()) {
            for (ClientHandler handler : set) {
                User u = handler.getLoggedInUser();
                if (u != null && "RESPONDER".equalsIgnoreCase(u.getRole())) {
                    if (targetDepartment == null || "ALL".equalsIgnoreCase(targetDepartment) ||
                        "ALL".equalsIgnoreCase(u.getSpecialty()) || targetDepartment.equalsIgnoreCase(u.getSpecialty())) {
                        handler.enqueuePacket(packet);
                    }
                }
            }
        }
    }

    /**
     * Gửi thông báo / báo cáo tới ban giám hiệu (Admin).
     */
    public static void sendToAdmin(Packet packet) {
        sendToRole("ADMIN", packet);
    }

    /**
     * Phát quảng bá toàn hệ thống.
     */
    public static void broadcast(Packet packet) {
        for (Set<ClientHandler> set : activeSessions.values()) {
            for (ClientHandler handler : set) {
                handler.enqueuePacket(packet);
            }
        }
    }

    public static int getOnlineCount() {
        int count = 0;
        for (Set<ClientHandler> set : activeSessions.values()) {
            count += set.size();
        }
        return count;
    }
}
