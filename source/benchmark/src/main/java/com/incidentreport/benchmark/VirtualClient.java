package com.incidentreport.benchmark;

import com.google.gson.Gson;
import com.incidentreport.common.Constants;
import com.incidentreport.common.packet.Packet;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Lớp đại diện cho một Virtual Client kết nối Socket TCP tới Server SIDS.
 */
public class VirtualClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private static final Gson gson = new Gson();
    private Map<String, String> userParams;

    public VirtualClient(String host, int port) throws Exception {
        this(host, port, 6000);
    }

    public VirtualClient(String host, int port, int timeoutMs) throws Exception {
        this.host = host;
        this.port = port;
        this.socket = new Socket(host, port);
        this.socket.setTcpNoDelay(true);
        this.socket.setSoTimeout(timeoutMs);
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public synchronized void send(Packet packet) throws Exception {
        packet.writeTo(out);
    }

    public synchronized Packet receive() throws Exception {
        return Packet.readFrom(in);
    }

    public synchronized Packet receiveWithTimeout(int timeoutMs) throws Exception {
        int oldTimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(timeoutMs);
            return Packet.readFrom(in);
        } finally {
            socket.setSoTimeout(oldTimeout);
        }
    }

    public synchronized Packet sendAndReceive(Packet request, byte expectedType, int maxWaitPackets) throws Exception {
        send(request);
        for (int i = 0; i < maxWaitPackets; i++) {
            Packet resp = receive();
            if (resp.getType() == expectedType || resp.getType() == Constants.TYPE_ERROR) {
                return resp;
            }
            // Gói tin push/broadcast khác có thể chen vào -> tiếp tục đọc gói tin phản hồi mong đợi
        }
        throw new RuntimeException("Không nhận được gói tin mong đợi (OpCode: " + expectedType + ") sau " + maxWaitPackets + " lượt đọc");
    }

    public Map<String, String> login(String username, String password) throws Exception {
        Packet loginPacket = new Packet(Constants.TYPE_LOGIN, "LOGIN|username=" + username, password.getBytes(StandardCharsets.UTF_8));
        Packet resp = sendAndReceive(loginPacket, Constants.TYPE_LOGIN_RESP, 10);
        Map<String, String> params = resp.parseParams();
        if (!"OK".equalsIgnoreCase(params.get("status"))) {
            throw new RuntimeException("Đăng nhập thất bại cho user '" + username + "': " + params.get("message"));
        }
        this.userParams = params;
        return params;
    }

    public Packet submitIncident(String room, String category, String desc, byte[] imageBytes) throws Exception {
        String header = String.format("INCIDENT|room=%s|category=%s|desc=%s", room, category, desc);
        Packet packet = new Packet(Constants.TYPE_SUBMIT_INCIDENT, header, imageBytes != null ? imageBytes : new byte[0]);
        return sendAndReceive(packet, Constants.TYPE_SUBMIT_RESP, 5);
    }

    public Packet claimTask(int ticketId) throws Exception {
        Packet packet = new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId);
        return sendAndReceive(packet, Constants.TYPE_CLAIM_RESP, 5);
    }

    public Packet updateStatus(int ticketId, String status, String note) throws Exception {
        Packet packet = new Packet(Constants.TYPE_STATUS_UPDATE,
                String.format("STATUS_UPDATE|ticketId=%d|status=%s|note=%s", ticketId, status, note));
        send(packet);
        // Server phát tán STATUS_BROADCAST sau khi update
        return receive();
    }

    public void sendChat(int ticketId, String message) throws Exception {
        Packet packet = new Packet(Constants.TYPE_CHAT_SEND, "CHAT_SEND|ticketId=" + ticketId,
                message.getBytes(StandardCharsets.UTF_8));
        send(packet);
    }

    public Packet listTickets(String scope) throws Exception {
        Packet packet = new Packet(Constants.TYPE_LIST_TICKETS, "LIST_TICKETS|scope=" + scope);
        return sendAndReceive(packet, Constants.TYPE_LIST_TICKETS_RESP, 5);
    }

    public Packet getStats(String timeRange) throws Exception {
        Packet packet = new Packet(Constants.TYPE_ADMIN_GET_STATS, "GET_STATS|timeRange=" + timeRange);
        return sendAndReceive(packet, Constants.TYPE_ADMIN_GET_STATS_RESP, 5);
    }

    public void drainPackets(int timeoutMs) {
        try {
            int oldTimeout = socket.getSoTimeout();
            socket.setSoTimeout(timeoutMs);
            while (true) {
                try {
                    receive();
                } catch (Exception e) {
                    break;
                }
            }
            socket.setSoTimeout(oldTimeout);
        } catch (Exception ignored) {}
    }

    public Map<String, String> getUserParams() {
        return userParams;
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (Exception ignored) {}
    }
}

