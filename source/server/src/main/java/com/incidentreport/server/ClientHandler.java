package com.incidentreport.server;

import com.google.gson.Gson;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.*;
import com.incidentreport.common.packet.Packet;
import com.incidentreport.service.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Xử lý kết nối TCP Socket cho từng Client (Multi-threaded & Asynchronous I/O):
 * - Tách biệt hoàn toàn luồng Đọc và luồng Ghi qua BlockingQueue.
 * - Triển khai giao thức nhị phân chuẩn 0xCAFE.
 * - Điều phối báo cáo tự động theo phòng ban kỹ thuật / y tế / an ninh.
 * - Đẩy ngay cho cán bộ online hoặc tự động nạp backlog khi cán bộ đăng nhập.
 * - Phân quyền chặt chẽ: Chỉ Admin mới tạo được tài khoản & xem kiểm toán/thống kê.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final Gson gson = new Gson();

    private User loggedInUser = null;
    private volatile boolean running = true;

    // Hàng đợi gửi bất đồng bộ tránh nghẽn luồng đọc
    private final BlockingQueue<Packet> outgoingQueue = new LinkedBlockingQueue<>();
    private Thread senderThread;

    // Cache tránh lặp xử lý bản tin
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

            senderThread = new Thread(this::sendLoop, "SenderWorker-" + socket.getRemoteSocketAddress());
            senderThread.setDaemon(true);
            senderThread.start();

            while (running) {
                Packet packet = Packet.readFrom(in);
                handlePacket(packet);
            }
        } catch (EOFException e) {
            // Client ngắt kết nối bình thường
        } catch (Exception e) {
            if (running) {
                System.out.printf("ℹ️ Client ngắt kết nối: %s (%s)\n", socket.getRemoteSocketAddress(), e.getMessage());
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

        String reqId = params.get("reqId");
        if (reqId != null) {
            Packet cachedResp = idempotencyCache.get(reqId);
            if (cachedResp != null) {
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

            case Constants.TYPE_CLAIM_TASK:
                handleClaimTask(params);
                break;

            case Constants.TYPE_MANUAL_ASSIGN:
                handleManualAssign(params);
                break;

            case Constants.TYPE_STATUS_UPDATE:
                handleStatusUpdate(params);
                break;

            case Constants.TYPE_LIST_TICKETS:
                handleListTickets(params);
                break;

            case Constants.TYPE_CHAT_SEND:
                handleChatSend(packet, params);
                break;

            case Constants.TYPE_CHAT_HISTORY:
                handleChatHistory(params);
                break;

            case Constants.TYPE_ADMIN_CREATE_USER:
                handleAdminCreateUser(packet, params);
                break;

            case Constants.TYPE_ADMIN_GET_USERS:
                handleAdminGetUsers();
                break;

            case Constants.TYPE_ADMIN_GET_STATS:
                handleAdminGetStats(params);
                break;

            case Constants.TYPE_LOGOUT:
                closeConnection();
                break;

            default:
                sendError("UNKNOWN_OPCODE", "Mã loại bản tin không được hỗ trợ: " + type);
                break;
        }
    }

    private void handleLogin(Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        if (username == null || password == null) {
            sendError("INVALID_CREDENTIALS", "Tên đăng nhập và mật khẩu không được để trống!");
            return;
        }

        User user = UserDAO.login(username, password);
        if (user == null) {
            Packet failPkt = new Packet(Constants.TYPE_LOGIN_RESP, "LOGIN_RESP|status=FAIL|message=Sai tên đăng nhập hoặc mật khẩu!");
            enqueuePacket(failPkt);
            return;
        }

        this.loggedInUser = user;
        UserDAO.updateUserOnlineStatus(user.getUserId(), true);
        ServerSessionManager.addClient(user.getUserId(), this);

        String header = String.format("LOGIN_RESP|status=OK|userId=%d|username=%s|role=%s|specialty=%s|fullName=%s|phone=%s",
                user.getUserId(), user.getUsername(), user.getRole(),
                user.getSpecialty() != null ? user.getSpecialty() : "NONE",
                user.getFullName(), user.getPhone() != null ? user.getPhone() : "");

        Packet successPkt = new Packet(Constants.TYPE_LOGIN_RESP, header, gson.toJson(user).getBytes(StandardCharsets.UTF_8));
        enqueuePacket(successPkt);

        System.out.printf("👤 [LOGIN] Người dùng %s (%s - %s) đăng nhập thành công.\n",
                user.getUsername(), user.getRole(), user.getSpecialty());

        // CHỨC NĂNG CỐT LÕI: ĐẨY SỰ CỐ TỒN ĐỌNG (OFFLINE BACKLOG) CHO CÁN BỘ PHÒNG BAN VỪA ONLINE
        if (Constants.ROLE_RESPONDER.equalsIgnoreCase(user.getRole())) {
            List<IncidentTicket> backlog = TicketDAO.getPendingTicketsBySpecialty(user.getSpecialty());
            for (IncidentTicket ticket : backlog) {
                Packet pushPkt = createDispatchPacket(ticket);
                enqueuePacket(pushPkt);
            }
            if (!backlog.isEmpty()) {
                System.out.printf("📥 [OFFLINE SYNC] Đã đẩy %d sự cố tồn đọng cho Responder %s vừa online.\n",
                        backlog.size(), user.getUsername());
            }
        }
    }

    private void handleSubmitIncident(Packet packet, Map<String, String> params, String reqId) {
        if (loggedInUser == null) {
            sendError("UNAUTHORIZED", "Bạn cần đăng nhập trước khi gửi báo cáo sự cố!");
            return;
        }

        String roomLocation = params.get("room");
        String categoryCode = params.get("category");
        String description = params.get("desc");

        if (roomLocation == null || categoryCode == null || description == null) {
            sendError("BAD_REQUEST", "Thiếu thông tin phòng, phân loại hoặc mô tả sự cố!");
            return;
        }

        // Lưu ảnh hiện trường nếu có đính kèm
        String imagePath = null;
        byte[] attach = packet.getAttachment();
        if (attach != null && attach.length > 0) {
            try {
                Files.createDirectories(Paths.get("uploads"));
                String fileName = "uploads/img_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6) + ".jpg";
                Files.write(Paths.get(fileName), attach);
                imagePath = fileName;
            } catch (IOException e) {
                System.err.println("⚠️ Không thể lưu ảnh đính kèm: " + e.getMessage());
            }
        }

        // Tự động phân loại và định tuyến đúng phòng ban
        IncidentCategory category = CategoryDAO.getCategory(categoryCode);
        String targetDept = (category != null) ? category.getTargetDepartment() : Constants.SPECIALTY_TECHNICAL;

        String ticketCode = "TK-" + (System.currentTimeMillis() % 1000000);
        IncidentTicket ticket = new IncidentTicket();
        ticket.setTicketCode(ticketCode);
        ticket.setRoomLocation(roomLocation.trim());
        ticket.setCategoryCode(categoryCode);
        ticket.setTargetDepartment(targetDept);
        ticket.setDescription(description.trim());
        ticket.setReporterId(loggedInUser.getUserId());
        ticket.setReporterName(loggedInUser.getFullName());
        ticket.setReporterPhone(loggedInUser.getPhone());
        ticket.setImageFilePath(imagePath);
        ticket.setStatus(Constants.STATUS_PENDING);

        boolean ok = TicketDAO.createTicket(ticket);
        if (!ok) {
            Packet failPkt = new Packet(Constants.TYPE_SUBMIT_RESP, "SUBMIT_RESP|status=FAIL|message=Không thể lưu sự cố vào CSDL!");
            enqueuePacket(failPkt);
            return;
        }

        // Phản hồi cho người báo cáo
        Packet respPkt = new Packet(Constants.TYPE_SUBMIT_RESP,
                String.format("SUBMIT_RESP|status=OK|ticketCode=%s|ticketId=%d", ticket.getTicketCode(), ticket.getTicketId()),
                gson.toJson(ticket).getBytes(StandardCharsets.UTF_8));
        enqueuePacket(respPkt);

        if (reqId != null) {
            idempotencyCache.put(reqId, respPkt);
        }

        // CHỨC NĂNG CỐT LÕI: TỰ ĐỘNG ĐẨY SỰ CỐ TỚI TÀI KHOẢN PHÒNG BAN CHUYÊN TRÁCH ĐANG ONLINE
        Packet dispatchPkt = createDispatchPacket(ticket);
        ServerSessionManager.sendToDepartment(targetDept, dispatchPkt);

        // Đẩy báo cáo tới Admin để theo dõi giám sát
        ServerSessionManager.sendToAdmin(dispatchPkt);

        System.out.printf("📢 [AUTO DISPATCH] Sự cố %s tại %s -> Đã tự động đẩy tới phòng ban: %s\n",
                ticket.getTicketCode(), ticket.getRoomLocation(), targetDept);
    }

    private void handleClaimTask(Map<String, String> params) {
        if (loggedInUser == null || !Constants.ROLE_RESPONDER.equalsIgnoreCase(loggedInUser.getRole())) {
            sendError("FORBIDDEN", "Chỉ cán bộ phòng ban (Responder) mới có quyền tiếp nhận sự cố!");
            return;
        }

        int ticketId = Integer.parseInt(params.getOrDefault("ticketId", "0"));
        if (ticketId <= 0) {
            sendError("BAD_REQUEST", "Mã sự cố không hợp lệ!");
            return;
        }

        boolean success = TicketDAO.claimTicket(ticketId, loggedInUser.getUserId(), loggedInUser.getFullName());
        if (success) {
            IncidentTicket ticket = TicketDAO.getTicketById(ticketId);
            Packet respPkt = new Packet(Constants.TYPE_CLAIM_RESP, "CLAIM_RESP|status=OK|ticketId=" + ticketId);
            enqueuePacket(respPkt);

            // Bắn tin cập nhật trạng thái tới Reporter, phòng ban và Admin
            Packet statusPkt = new Packet(Constants.TYPE_STATUS_BROADCAST,
                    String.format("STATUS_BROADCAST|ticketId=%d|status=%s|staffId=%d|staffName=%s",
                            ticketId, Constants.STATUS_IN_PROGRESS, loggedInUser.getUserId(), loggedInUser.getFullName()));

            if (ticket != null) {
                ServerSessionManager.sendToUser(ticket.getReporterId(), statusPkt);
                ServerSessionManager.sendToDepartment(ticket.getTargetDepartment(), statusPkt);
            }
            ServerSessionManager.sendToAdmin(statusPkt);
            System.out.printf("🤝 [CLAIM] Cán bộ %s đã tiếp nhận sự cố #%d.\n", loggedInUser.getFullName(), ticketId);
        } else {
            Packet respPkt = new Packet(Constants.TYPE_CLAIM_RESP, "CLAIM_RESP|status=ALREADY_TAKEN|ticketId=" + ticketId + "|message=Sự cố đã được người khác tiếp nhận!");
            enqueuePacket(respPkt);
        }
    }

    private void handleManualAssign(Map<String, String> params) {
        if (loggedInUser == null || !Constants.ROLE_ADMIN.equalsIgnoreCase(loggedInUser.getRole())) {
            sendError("FORBIDDEN", "Chỉ Admin/Ban Giám Hiệu mới có quyền chỉ định nhân sự trực tiếp!");
            return;
        }

        int ticketId = Integer.parseInt(params.getOrDefault("ticketId", "0"));
        int staffId = Integer.parseInt(params.getOrDefault("staffId", "0"));
        String note = params.getOrDefault("note", "Admin phân công trực tiếp");

        User staff = UserDAO.getUserById(staffId);
        if (staff == null) {
            sendError("NOT_FOUND", "Không tìm thấy cán bộ có ID: " + staffId);
            return;
        }

        boolean ok = TicketDAO.assignTicket(ticketId, staffId, staff.getFullName(), note);
        if (ok) {
            IncidentTicket ticket = TicketDAO.getTicketById(ticketId);
            enqueuePacket(new Packet(Constants.TYPE_MANUAL_ASSIGN_RESP, "MANUAL_ASSIGN_RESP|status=OK|ticketId=" + ticketId));

            // Bắn thông báo đẩy thẳng cho cán bộ được chỉ định
            if (ticket != null) {
                Packet pushPkt = createDispatchPacket(ticket);
                ServerSessionManager.sendToUser(staffId, pushPkt);

                Packet statusPkt = new Packet(Constants.TYPE_STATUS_BROADCAST,
                        String.format("STATUS_BROADCAST|ticketId=%d|status=%s|staffId=%d|staffName=%s",
                                ticketId, Constants.STATUS_IN_PROGRESS, staffId, staff.getFullName()));
                ServerSessionManager.sendToUser(ticket.getReporterId(), statusPkt);
                ServerSessionManager.sendToDepartment(ticket.getTargetDepartment(), statusPkt);
            }
        } else {
            enqueuePacket(new Packet(Constants.TYPE_MANUAL_ASSIGN_RESP, "MANUAL_ASSIGN_RESP|status=FAIL|message=Lỗi chỉ định nhân sự!"));
        }
    }

    private void handleStatusUpdate(Map<String, String> params) {
        if (loggedInUser == null) {
            sendError("UNAUTHORIZED", "Vui lòng đăng nhập!");
            return;
        }

        int ticketId = Integer.parseInt(params.getOrDefault("ticketId", "0"));
        String status = params.get("status");
        String note = params.getOrDefault("note", "");

        if (ticketId <= 0 || status == null) {
            sendError("BAD_REQUEST", "Thiếu mã sự cố hoặc trạng thái!");
            return;
        }

        boolean ok = TicketDAO.updateStatus(ticketId, status, note);
        if (ok) {
            IncidentTicket ticket = TicketDAO.getTicketById(ticketId);
            Packet statusPkt = new Packet(Constants.TYPE_STATUS_BROADCAST,
                    String.format("STATUS_BROADCAST|ticketId=%d|status=%s|staffName=%s|note=%s",
                            ticketId, status, loggedInUser.getFullName(), note));

            if (ticket != null) {
                ServerSessionManager.sendToUser(ticket.getReporterId(), statusPkt);
                ServerSessionManager.sendToDepartment(ticket.getTargetDepartment(), statusPkt);
            }
            ServerSessionManager.sendToAdmin(statusPkt);
            System.out.printf("🔄 [STATUS] Sự cố #%d chuyển sang trạng thái: %s bởi %s\n", ticketId, status, loggedInUser.getFullName());
        } else {
            sendError("UPDATE_FAILED", "Không thể cập nhật trạng thái sự cố!");
        }
    }

    private void handleListTickets(Map<String, String> params) {
        if (loggedInUser == null) {
            sendError("UNAUTHORIZED", "Vui lòng đăng nhập!");
            return;
        }

        String scope = params.getOrDefault("scope", "MY");
        List<IncidentTicket> list;

        if ("DEPARTMENT".equalsIgnoreCase(scope)) {
            list = TicketDAO.getTicketsByDepartment(loggedInUser.getSpecialty());
        } else if ("ALL".equalsIgnoreCase(scope) || Constants.ROLE_ADMIN.equalsIgnoreCase(loggedInUser.getRole())) {
            list = TicketDAO.getAllTickets();
        } else {
            if (Constants.ROLE_RESPONDER.equalsIgnoreCase(loggedInUser.getRole())) {
                list = TicketDAO.getTicketsByStaff(loggedInUser.getUserId());
            } else {
                list = TicketDAO.getTicketsByReporter(loggedInUser.getUserId());
            }
        }

        byte[] body = gson.toJson(list).getBytes(StandardCharsets.UTF_8);
        Packet resp = new Packet(Constants.TYPE_LIST_TICKETS_RESP, "LIST_TICKETS_RESP|count=" + list.size(), body);
        enqueuePacket(resp);
    }

    private void handleChatSend(Packet packet, Map<String, String> params) {
        if (loggedInUser == null) {
            sendError("UNAUTHORIZED", "Vui lòng đăng nhập để gửi tin nhắn!");
            return;
        }

        int ticketId = Integer.parseInt(params.getOrDefault("ticketId", "0"));
        if (ticketId <= 0) {
            sendError("BAD_REQUEST", "Thiếu mã sự cố trao đổi!");
            return;
        }

        String content = packet.getAttachment() != null && packet.getAttachment().length > 0
                ? new String(packet.getAttachment(), StandardCharsets.UTF_8)
                : params.get("content");

        if (content == null || content.trim().isEmpty()) {
            return;
        }

        ChatMessage msg = new ChatMessage(ticketId, loggedInUser.getUserId(), loggedInUser.getFullName(), loggedInUser.getRole(), content.trim());
        boolean saved = ChatMessageDAO.saveMessage(msg);

        if (!saved) {
            sendError("CHAT_FAILED", "Không thể lưu tin nhắn!");
            return;
        }

        IncidentTicket ticket = TicketDAO.getTicketById(ticketId);

        // ĐÓNG GÓI GÓI TIN CHAT_PUSH ĐỂ ĐIỀU PHỐI ĐÚNG 1-1
        Packet pushPkt = new Packet(Constants.TYPE_CHAT_PUSH,
                String.format("CHAT_PUSH|ticketId=%d|senderId=%d|senderName=%s|senderRole=%s|time=%s",
                        ticketId, loggedInUser.getUserId(), loggedInUser.getFullName(), loggedInUser.getRole(), msg.getSentAt()),
                msg.getContent().getBytes(StandardCharsets.UTF_8));

        // CHỨC NĂNG CỐT LÕI: Chat 1-1 giữa người báo cáo và người tiếp nhận
        if (ticket != null) {
            // Gửi cho người báo cáo nếu người gửi không phải họ
            if (ticket.getReporterId() > 0 && ticket.getReporterId() != loggedInUser.getUserId()) {
                ServerSessionManager.sendToUser(ticket.getReporterId(), pushPkt);
            }
            // Gửi cho cán bộ phụ trách nếu người gửi không phải họ
            if (ticket.getAssignedStaffId() != null && ticket.getAssignedStaffId() > 0 && ticket.getAssignedStaffId() != loggedInUser.getUserId()) {
                ServerSessionManager.sendToUser(ticket.getAssignedStaffId(), pushPkt);
            }
        }

        // Server/Admin vẫn có thể giám sát kiểm toán tin nhắn
        ServerSessionManager.sendToAdmin(pushPkt);

        // Echo lại cho chính người gửi để xác nhận tin đã gửi thành công
        enqueuePacket(pushPkt);
    }

    private void handleChatHistory(Map<String, String> params) {
        if (loggedInUser == null) {
            sendError("UNAUTHORIZED", "Vui lòng đăng nhập!");
            return;
        }

        int ticketId = Integer.parseInt(params.getOrDefault("ticketId", "0"));
        List<ChatMessage> list = ChatMessageDAO.getMessagesByTicket(ticketId);
        byte[] body = gson.toJson(list).getBytes(StandardCharsets.UTF_8);

        Packet resp = new Packet(Constants.TYPE_CHAT_HISTORY_RESP, "CHAT_HISTORY_RESP|ticketId=" + ticketId, body);
        enqueuePacket(resp);
    }

    private void handleAdminCreateUser(Packet packet, Map<String, String> params) {
        if (loggedInUser == null || !Constants.ROLE_ADMIN.equalsIgnoreCase(loggedInUser.getRole())) {
            sendError("FORBIDDEN", "Chỉ Admin/Ban Giám Hiệu mới có quyền tạo tài khoản!");
            return;
        }

        String username = params.get("username");
        String fullName = params.get("fullName");
        String role = params.get("role");
        String specialty = params.getOrDefault("specialty", Constants.SPECIALTY_NONE);
        String phone = params.getOrDefault("phone", "");

        String password = packet.getAttachment() != null && packet.getAttachment().length > 0
                ? new String(packet.getAttachment(), StandardCharsets.UTF_8)
                : params.getOrDefault("password", "123456");

        if (username == null || fullName == null || role == null) {
            enqueuePacket(new Packet(Constants.TYPE_ADMIN_CREATE_RESP, "ADMIN_CREATE_RESP|status=FAIL|message=Thiếu thông tin bắt buộc!"));
            return;
        }

        User newUser = new User(username.trim(), password.trim(), fullName.trim(), role.trim(), specialty.trim(), phone.trim());
        boolean created = UserDAO.createUser(newUser);

        if (created) {
            enqueuePacket(new Packet(Constants.TYPE_ADMIN_CREATE_RESP, "ADMIN_CREATE_RESP|status=OK|username=" + username));
            System.out.printf("👤 [ADMIN USER] Đã cấp mới tài khoản: %s (%s - %s)\n", username, role, specialty);
        } else {
            enqueuePacket(new Packet(Constants.TYPE_ADMIN_CREATE_RESP, "ADMIN_CREATE_RESP|status=FAIL|message=Tên đăng nhập đã tồn tại hoặc lỗi CSDL!"));
        }
    }

    private void handleAdminGetUsers() {
        if (loggedInUser == null || !Constants.ROLE_ADMIN.equalsIgnoreCase(loggedInUser.getRole())) {
            sendError("FORBIDDEN", "Chỉ Admin mới có quyền xem danh sách người dùng!");
            return;
        }

        List<User> list = UserDAO.getAllUsers();
        byte[] body = gson.toJson(list).getBytes(StandardCharsets.UTF_8);
        enqueuePacket(new Packet(Constants.TYPE_ADMIN_GET_USERS_RESP, "ADMIN_GET_USERS_RESP|count=" + list.size(), body));
    }

    private void handleAdminGetStats(Map<String, String> params) {
        if (loggedInUser == null || !Constants.ROLE_ADMIN.equalsIgnoreCase(loggedInUser.getRole())) {
            sendError("FORBIDDEN", "Chỉ Admin mới có quyền xem thống kê!");
            return;
        }

        String timeRange = params.getOrDefault("timeRange", "ALL");
        StatisticReport report = TicketDAO.getStatistics(timeRange);
        byte[] body = gson.toJson(report).getBytes(StandardCharsets.UTF_8);

        enqueuePacket(new Packet(Constants.TYPE_ADMIN_GET_STATS_RESP, "ADMIN_GET_STATS_RESP|range=" + timeRange, body));
    }

    private Packet createDispatchPacket(IncidentTicket ticket) {
        String header = String.format("DISPATCH_NOTIFY|ticketId=%d|code=%s|room=%s|category=%s|department=%s|desc=%s|time=%s",
                ticket.getTicketId(), ticket.getTicketCode(), ticket.getRoomLocation(),
                ticket.getCategoryCode(), ticket.getTargetDepartment(),
                ticket.getDescription().replace("\n", " ").replace("|", " "),
                ticket.getCreatedAt() != null ? ticket.getCreatedAt() : "");
        return new Packet(Constants.TYPE_DISPATCH_NOTIFY, header, gson.toJson(ticket).getBytes(StandardCharsets.UTF_8));
    }

    private void sendError(String code, String message) {
        enqueuePacket(new Packet(Constants.TYPE_ERROR, String.format("ERROR|code=%s|message=%s", code, message)));
    }

    public void closeConnection() {
        running = false;
        if (loggedInUser != null) {
            UserDAO.updateUserOnlineStatus(loggedInUser.getUserId(), false);
            ServerSessionManager.removeClient(loggedInUser.getUserId(), this);
            loggedInUser = null;
        }
        if (senderThread != null) {
            senderThread.interrupt();
        }
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
