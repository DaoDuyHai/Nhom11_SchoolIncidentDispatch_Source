package com.incidentreport.server;

import com.google.gson.Gson;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import com.incidentreport.server.dispatch.DispatchWorker;
import com.incidentreport.server.multicast.MulticastBroadcaster;
import com.incidentreport.service.*;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Xử lý kết nối từ một Client qua TCP Socket:
 * 1. Tách biệt hoàn toàn luồng Đọc (Network Reader) và luồng Ghi (Sender Worker) qua BlockingQueue.
 * 2. Đóng khung nhị phân bằng Packet (0xCAFE).
 * 3. Hỗ trợ truyền mảng byte ảnh hiện trường và lưu trữ tại uploads/.
 * 4. Idempotency cache tránh xử lý lặp bản tin.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final Gson gson = new Gson();

    private User loggedInUser = null;
    private volatile boolean running = true;

    // Hàng đợi gửi riêng cho từng session để không bao giờ bị block khi 1 client bị chậm
    private final BlockingQueue<Packet> outgoingQueue = new LinkedBlockingQueue<>();
    private Thread senderThread;

    // Bộ đệm Idempotency lưu kết quả cũ
    private static final Map<String, Packet> idempotencyCache = new ConcurrentHashMap<>();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public User getLoggedInUser() {
        return loggedInUser;
    }

    @Override
    public void run() {
        try {
            socket.setTcpNoDelay(true);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            // Khởi động luồng gửi riêng (Sender Worker)
            senderThread = new Thread(this::sendLoop, "SenderWorker-" + socket.getRemoteSocketAddress());
            senderThread.setDaemon(true);
            senderThread.start();

            // Vòng lặp đọc bản tin từ Client
            while (running) {
                Packet packet = Packet.readFrom(in);
                handlePacket(packet);
            }
        } catch (EOFException e) {
            // Client đóng kết nối bình thường
        } catch (Exception e) {
            if (running) {
                System.out.println("ℹ️ Client ngắt kết nối: " + socket.getRemoteSocketAddress() + " (" + e.getMessage() + ")");
            }
        } finally {
            closeConnection();
        }
    }

    private void sendLoop() {
        while (running) {
            try {
                Packet packet = outgoingQueue.take();
                packet.writeTo(out);
            } catch (InterruptedException e) {
                break;
            } catch (IOException e) {
                closeConnection();
                break;
            }
        }
    }

    public void enqueuePacket(Packet packet) {
        if (running && packet != null) {
            outgoingQueue.offer(packet);
        }
    }

    private void handlePacket(Packet packet) {
        byte type = packet.getType();
        Map<String, String> params = packet.parseParams();

        // Idempotency: Kiểm tra nếu có requestId trùng lặp
        String reqId = params.get("reqId");
        if (reqId != null) {
            Packet cachedResp = idempotencyCache.get(reqId);
            if (cachedResp != null) {
                System.out.println("🔁 [IDEMPOTENCY] Trả lại kết quả cũ cho yêu cầu trùng: " + reqId);
                enqueuePacket(cachedResp);
                return;
            }
        }

        switch (type) {
            case Constants.TYPE_LOGIN:
                handleLogin(params);
                break;

            case Constants.TYPE_SUBMIT_INCIDENT:
                handleSubmitIncident(packet, params, reqId);
                break;

            case Constants.TYPE_ACK_TASK:
                handleAckTask(params);
                break;

            case Constants.TYPE_STATUS_UPDATE:
                handleStatusUpdate(params);
                break;

            case Constants.TYPE_LIST_MY_TICKETS:
                handleListMyTickets();
                break;

            case Constants.TYPE_GET_ALL_TICKETS:
                handleGetAllTickets();
                break;

            case Constants.TYPE_MULTICAST_ALERT:
                handleMulticastAlert(params);
                break;

            case Constants.TYPE_HEARTBEAT_TCP:
                enqueuePacket(new Packet(Constants.TYPE_HEARTBEAT_TCP, "PONG"));
                break;

            case Constants.TYPE_LOGOUT:
                closeConnection();
                break;

            default:
                sendError("UNKNOWN_TYPE", "Loại bản tin không được hỗ trợ: " + type);
        }
    }

    private void handleLogin(Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        User user = AuthService.login(username, password);
        if (user != null) {
            this.loggedInUser = user;
            ServerSessionManager.addClient(user.getUserId(), this);

            String reply = String.format(
                "LOGIN_RESP|status=OK|userId=%d|username=%s|fullName=%s|role=%s|subRole=%s|specialty=%s",
                user.getUserId(), user.getUsername(), user.getFullName(),
                user.getRole(), user.getSubRole(), user.getSpecialty()
            );
            enqueuePacket(new Packet(Constants.TYPE_LOGIN_RESP, reply));
            System.out.printf("✅ [LOGIN OK] %s (%s) đăng nhập thành công!\n", user.getFullName(), user.getRole());
        } else {
            String reply = "LOGIN_RESP|status=FAIL|message=Sai tên đăng nhập hoặc mật khẩu";
            enqueuePacket(new Packet(Constants.TYPE_LOGIN_RESP, reply));
        }
    }

    private void handleSubmitIncident(Packet packet, Map<String, String> params, String reqId) {
        if (loggedInUser == null) {
            sendError("UNAUTHORIZED", "Vui lòng đăng nhập trước khi gửi báo cáo");
            return;
        }

        String room = params.get("room");
        String category = params.get("category");
        int severity = 3;
        try { severity = Integer.parseInt(params.get("severity")); } catch (Exception ignored) {}
        String desc = params.get("desc");

        // Lưu ảnh hiện trường vào uploads/ nếu có
        String savedImagePath = null;
        int imageSize = 0;
        if (packet.hasAttachment()) {
            savedImagePath = saveAttachmentFile(packet.getAttachmentBytes());
            imageSize = packet.getAttachmentBytes().length;
        }

        String clientIp = socket.getInetAddress().getHostAddress();

        // Gọi nghiệp vụ xử lý gộp trùng lặp (Spatio-Temporal Deduplication)
        ReportService.SubmitResult result = ReportService.submitIncident(
            loggedInUser.getUserId(), room, category, severity, desc, savedImagePath, imageSize, clientIp
        );

        if (result.success && result.ticket != null) {
            IncidentTicket ticket = result.ticket;

            // Nếu là ticket mới tạo -> Đưa ngay vào Hàng đợi ưu tiên (Aging Priority Queue)
            if (!result.isMerged) {
                Server.getAgingQueue().enqueue(ticket);
            }

            String respHeader = String.format(
                "SUBMIT_RESP|ticketCode=%s|ticketId=%d|isMerged=%d|reportCount=%d|message=%s",
                ticket.getTicketCode(), ticket.getTicketId(), result.isMerged ? 1 : 0,
                ticket.getReportCount(), result.message
            );
            Packet respPacket = new Packet(Constants.TYPE_SUBMIT_RESP, respHeader);

            if (reqId != null) {
                idempotencyCache.put(reqId, respPacket);
            }
            enqueuePacket(respPacket);

            // Đồng thời push thông báo cập nhật tới toàn bộ Admin và Reporter
            String notifyHeader = String.format(
                "STATUS_UPDATE|ticketId=%d|code=%s|room=%s|category=%s|status=%s|count=%d",
                ticket.getTicketId(), ticket.getTicketCode(), ticket.getRoomLocation(),
                ticket.getCategoryCode(), ticket.getStatus(), ticket.getReportCount()
            );
            ServerSessionManager.sendToRole(Constants.ROLE_ADMIN, new Packet(Constants.TYPE_STATUS_UPDATE, notifyHeader));
        } else {
            sendError("SUBMIT_FAILED", result.message);
        }
    }

    private void handleAckTask(Map<String, String> params) {
        if (loggedInUser == null || !Constants.ROLE_RESPONDER.equals(loggedInUser.getRole())) {
            sendError("FORBIDDEN", "Chỉ nhân sự kỹ thuật/y tế/bảo vệ mới được phản hồi nhận việc");
            return;
        }

        int ticketId = 0;
        try { ticketId = Integer.parseInt(params.get("ticketId")); } catch (Exception ignored) {}
        String action = params.get("action"); // ACCEPT hoặc REJECT

        if ("ACCEPT".equalsIgnoreCase(action)) {
            DispatchWorker.clearPendingPush(loggedInUser.getUserId(), ticketId);

            // Nhận ca độc quyền (Atomic Claim with Optimistic Locking)
            boolean claimed = TicketDAO.claimTicketAtomic(ticketId, loggedInUser.getUserId());
            if (claimed) {
                UserDAO.adjustWorkload(loggedInUser.getUserId(), 1);
                DispatchLogDAO.updateStatus(ticketId, loggedInUser.getUserId(), Constants.DISPATCH_ACCEPTED, "Nhân viên đã nhận ca thành công");

                enqueuePacket(new Packet(Constants.TYPE_ACK_RESP, "ACK_RESP|ticketId=" + ticketId + "|status=OK"));

                // Thông báo tới tất cả mọi người là sự cố này đã có người tiếp nhận
                IncidentTicket ticket = TicketDAO.getTicketById(ticketId);
                String notifyHeader = String.format(
                    "STATUS_UPDATE|ticketId=%d|code=%s|status=ASSIGNED|staffName=%s|staffId=%d",
                    ticketId, ticket != null ? ticket.getTicketCode() : "",
                    loggedInUser.getFullName(), loggedInUser.getUserId()
                );
                ServerSessionManager.broadcast(new Packet(Constants.TYPE_STATUS_UPDATE, notifyHeader));

                System.out.printf("🤝 [CLAIM OK] Nhân viên %s (#%d) đã nhận ca sự cố #%d\n",
                    loggedInUser.getFullName(), loggedInUser.getUserId(), ticketId);
            } else {
                // Đã bị người khác nhận trước (Race Condition prevented)
                enqueuePacket(new Packet(Constants.TYPE_ACK_RESP, "ACK_RESP|ticketId=" + ticketId + "|status=TASK_ALREADY_TAKEN"));
                System.out.printf("✋ [CLAIM REJECTED] Sự cố #%d đã có người nhận trước khi %s bấm nhận!\n",
                    ticketId, loggedInUser.getFullName());
            }
        } else if ("REJECT".equalsIgnoreCase(action)) {
            DispatchWorker.clearPendingPush(loggedInUser.getUserId(), ticketId);

            String reason = params.get("reason");
            DispatchLogDAO.updateStatus(ticketId, loggedInUser.getUserId(), Constants.DISPATCH_REJECTED, "Từ chối: " + reason);

            // Đưa lại sự cố vào Hàng đợi ưu tiên để DispatchWorker tìm người khác
            IncidentTicket ticket = TicketDAO.getTicketById(ticketId);
            if (ticket != null) {
                Server.getAgingQueue().enqueue(ticket);
            }
            System.out.printf("↩️ [TASK REJECTED] %s từ chối sự cố #%d (Lý do: %s) -> Đã đưa lại vào hàng đợi\n",
                loggedInUser.getFullName(), ticketId, reason);
        }
    }

    private void handleStatusUpdate(Map<String, String> params) {
        if (loggedInUser == null) return;
        int ticketId = 0;
        try { ticketId = Integer.parseInt(params.get("ticketId")); } catch (Exception ignored) {}
        String newStatus = params.get("status");
        String note = params.get("note");

        boolean updated = TicketDAO.updateTicketStatus(ticketId, loggedInUser.getUserId(), newStatus, note);
        if (updated) {
            if (Constants.STATUS_RESOLVED.equals(newStatus)) {
                UserDAO.adjustWorkload(loggedInUser.getUserId(), -1);
                DispatchLogDAO.updateStatus(ticketId, loggedInUser.getUserId(), Constants.DISPATCH_COMPLETED, note);
            }

            IncidentTicket ticket = TicketDAO.getTicketById(ticketId);
            String notifyHeader = String.format(
                "STATUS_UPDATE|ticketId=%d|code=%s|status=%s|staffName=%s|note=%s",
                ticketId, ticket != null ? ticket.getTicketCode() : "", newStatus,
                loggedInUser.getFullName(), note != null ? note : ""
            );
            ServerSessionManager.broadcast(new Packet(Constants.TYPE_STATUS_UPDATE, notifyHeader));

            System.out.printf("🔄 [STATUS UPDATE] Sự cố #%d chuyển sang trạng thái: %s bởi %s\n",
                ticketId, newStatus, loggedInUser.getFullName());
        } else {
            sendError("UPDATE_FAILED", "Không thể cập nhật trạng thái sự cố #" + ticketId);
        }
    }

    private void handleListMyTickets() {
        if (loggedInUser == null) return;
        List<IncidentTicket> list;
        if (Constants.ROLE_REPORTER.equals(loggedInUser.getRole())) {
            list = TicketDAO.getTicketsByReporter(loggedInUser.getUserId());
        } else if (Constants.ROLE_RESPONDER.equals(loggedInUser.getRole())) {
            list = TicketDAO.getTicketsByStaff(loggedInUser.getUserId());
        } else {
            list = TicketDAO.getAllTickets();
        }

        String json = gson.toJson(list);
        enqueuePacket(new Packet(Constants.TYPE_LIST_TICKETS_RESP, json));
    }

    private void handleGetAllTickets() {
        List<IncidentTicket> list = TicketDAO.getAllTickets();
        String json = gson.toJson(list);
        enqueuePacket(new Packet(Constants.TYPE_LIST_TICKETS_RESP, json));
    }

    private void handleMulticastAlert(Map<String, String> params) {
        if (loggedInUser == null || !Constants.ROLE_ADMIN.equals(loggedInUser.getRole())) {
            sendError("FORBIDDEN", "Chỉ Ban Giám Hiệu mới được phát cảnh báo khẩn cấp toàn trường");
            return;
        }

        String level = params.get("level");
        String message = params.get("message");

        // Phát qua cổng UDP Multicast tới 230.0.0.1:9999
        boolean sent = MulticastBroadcaster.broadcastAlert(level, message, loggedInUser.getUserId());
        if (sent) {
            enqueuePacket(new Packet(Constants.TYPE_STATUS_UPDATE, "STATUS_UPDATE|multicast=OK"));
        } else {
            sendError("MULTICAST_FAIL", "Không thể gửi gói tin Multicast");
        }
    }

    private String saveAttachmentFile(byte[] bytes) {
        try {
            File dir = new File("uploads");
            if (!dir.exists()) {
                File parentDir = new File("../uploads");
                if (parentDir.exists()) {
                    dir = parentDir;
                } else {
                    File grandDir = new File("../../uploads");
                    if (grandDir.exists()) {
                        dir = grandDir;
                    }
                }
            }
            if (!dir.exists()) dir.mkdirs();
            String fileName = "inc_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
            File target = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(bytes);
            }
            return target.getPath();
        } catch (Exception e) {
            System.err.println("❌ Lỗi lưu file ảnh: " + e.getMessage());
            return null;
        }
    }

    private void sendError(String code, String message) {
        enqueuePacket(new Packet(Constants.TYPE_ERROR_NOTIFY, "ERROR|code=" + code + "|message=" + message));
    }

    public void closeConnection() {
        running = false;
        if (loggedInUser != null) {
            DispatchWorker.clearPendingPush(loggedInUser.getUserId(), 0);
            ServerSessionManager.removeClient(loggedInUser.getUserId(), this);
            if (!ServerSessionManager.isUserOnline(loggedInUser.getUserId())) {
                UserDAO.setOnlineStatus(loggedInUser.getUserId(), 0, Constants.AVAIL_OFFLINE);
            }
        }
        if (senderThread != null) {
            senderThread.interrupt();
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}