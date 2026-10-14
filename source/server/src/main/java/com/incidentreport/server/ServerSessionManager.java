package com.incidentreport.server;

import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý phiên làm việc đa luồng (Thread-safe Session Manager):
 * Lưu trữ Map<userId, Set<ClientHandler>> để Server hỗ trợ đa phiên đồng thời
 * và thực hiện Server-Push tới các Client một cách an toàn.
 */
public class ServerSessionManager {

    private static final Map<Integer, Set<ClientHandler>> activeSessions = new ConcurrentHashMap<>();

    public static void addClient(int userId, ClientHandler handler) {
        activeSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(handler);
        System.out.printf("👤 [SESSION] User #%d đã kết nối phiên mới. Tổng số phiên online: %d\n", userId, getOnlineCount());
    }

    public static void removeClient(int userId, ClientHandler handler) {
        Set<ClientHandler> set = activeSessions.get(userId);
        if (set != null) {
            set.remove(handler);
            if (set.isEmpty()) {
                activeSessions.remove(userId);
            }
            System.out.printf("👤 [SESSION] User #%d đã đóng một phiên. Còn lại: %d\n", userId, getOnlineCount());
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

    public static void sendToResponders(String requiredSpecialty, Packet packet) {
        for (Set<ClientHandler> set : activeSessions.values()) {
            for (ClientHandler handler : set) {
                User u = handler.getLoggedInUser();
                if (u != null && "RESPONDER".equalsIgnoreCase(u.getRole())) {
                    if (requiredSpecialty == null || "ALL".equalsIgnoreCase(requiredSpecialty) ||
                        "ALL".equalsIgnoreCase(u.getSpecialty()) || requiredSpecialty.equalsIgnoreCase(u.getSpecialty())) {
                        handler.enqueuePacket(packet);
                    }
                }
            }
        }
    }

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