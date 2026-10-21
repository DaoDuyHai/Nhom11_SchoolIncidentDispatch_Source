package com.incidentreport.server;

import com.google.gson.Gson;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.ChatMessage;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import com.incidentreport.server.auth.OtpManager;
import com.incidentreport.server.dispatch.DispatchWorker;
import com.incidentreport.server.multicast.MulticastBroadcaster;
import com.incidentreport.service.*;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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

            case Constants.TYPE_CHAT_SEND:
                handleChatSend(packet, params);
                break;

            case Constants.TYPE_CHAT_HISTORY:
                handleChatHistory(params);
                break;

            case Constants.TYPE_MANUAL_ASSIGN:
                handleManualAssign(params);
                break;

            case Constants.TYPE_GET_OPEN_POOL:
                handleGetOpenPool();
                break;

            case Constants.TYPE_GET_STAFF_LIST:
                handleGetStaffList();
                break;

            case Constants.TYPE_HEARTBEAT_TCP:
                enqueuePacket(new Packet(Constants.TYPE_HEARTBEAT_TCP, "PONG"));
                break;

            case Constants.TYPE_LOGOUT:
                closeConnection();
                break;

            case Constants.TYPE_REGISTER:
                handleRegister(packet, params);
                break;

            case Constants.TYPE_GUEST_LOGIN:
                handleGuestLogin();
                break;

            case Constants.TYPE_ADMIN_CREATE_USER:
                handleAdminCreateUser(packet, params);
                break;

            case Constants.TYPE_FORGOT_PASSWORD:
                handleForgotPassword(params);
                break;

            case Constants.TYPE_RESET_PASSWORD:
                handleResetPassword(packet, params);
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

    private void handleRegister(Packet packet, Map<String, String> params) {
        String username = params.get("username");
        String fullName = params.get("fullName");
        String phone = params.get("phone");
        String subRole = params.get("subRole");
        String password = packet.hasAttachment()
                ? new String(packet.getAttachmentBytes(), StandardCharsets.UTF_8)
                : params.get("password");

        AuthService.AuthResult result = AuthService.register(username, password, fullName, phone, subRole);
        if (result.success && result.user != null) {
            User u = result.user;
            String reply = String.format(
                "REGISTER_RESP|status=OK|userId=%d|username=%s|fullName=%s|role=%s|subRole=%s|message=%s",
                u.getUserId(), u.getUsername(), u.getFullName(), u.getRole(), u.getSubRole(), result.message
            );
            enqueuePacket(new Packet(Constants.TYPE_REGISTER_RESP, reply));
            System.out.printf("📝 [REGISTER OK] Đăng ký thành công tài khoản: %s (%s - %s)\n",
                u.getUsername(), u.getFullName(), u.getSubRole());
        } else {
            String reply = String.format("REGISTER_RESP|status=FAIL|message=%s",
                result.message != null ? result.message : "Đăng ký thất bại");
            enqueuePacket(new Packet(Constants.TYPE_REGISTER_RESP, reply));
        }
    }

    private void handleGuestLogin() {
        AuthService.AuthResult result = AuthService.guestLogin();
        if (result.success && result.user != null) {
            this.loggedInUser = result.user;
            ServerSessionManager.addClient(result.user.getUserId(), this);

            String reply = String.format(
                "LOGIN_RESP|status=OK|userId=%d|username=%s|fullName=%s|role=%s|subRole=%s|specialty=%s",
                result.user.getUserId(), result.user.getUsername(), result.user.getFullName(),
                result.user.getRole(), result.user.getSubRole(), result.user.getSpecialty()
            );
            enqueuePacket(new Packet(Constants.TYPE_LOGIN_RESP, reply));
            System.out.printf("🚀 [GUEST LOGIN] Khách vãng lai %s (%s) đã tham gia hệ thống!\n",
                result.user.getUsername(), result.user.getFullName());
        } else {
            String reply = "LOGIN_RESP|status=FAIL|message=" + (result.message != null ? result.message : "Đăng nhập Khách thất bại");
            enqueuePacket(new Packet(Constants.TYPE_LOGIN_RESP, reply));
        }
    }

    private void handleAdminCreateUser(Packet packet, Map<String, String> params) {
        if (loggedInUser == null || !Constants.ROLE_ADMIN.equalsIgnoreCase(loggedInUser.getRole())) {
            enqueuePacket(new Packet(Constants.TYPE_ADMIN_CREATE_RESP,
                "ADMIN_CREATE_RESP|status=FAIL|message=Từ chối quyền: Chỉ ADMIN mới có quyền tạo nhân sự"));
            return;
        }

        String username = params.get("username");
        String fullName = params.get("fullName");
        String phone = params.get("phone");
        String role = params.get("role");
        String subRole = params.get("subRole");
        String specialty = params.get("specialty");
        String password = packet.hasAttachment()
                ? new String(packet.getAttachmentBytes(), StandardCharsets.UTF_8)
                : params.get("password");

        AuthService.AuthResult result = AuthService.createUserByAdmin(
            loggedInUser.getUserId(), username, password, fullName, phone, role, subRole, specialty
        );

        if (result.success && result.user != null) {
            String reply = String.format(
                "ADMIN_CREATE_RESP|status=OK|userId=%d|username=%s|fullName=%s|role=%s|subRole=%s|specialty=%s|message=%s",
                result.user.getUserId(), result.user.getUsername(), result.user.getFullName(),
                result.user.getRole(), result.user.getSubRole(), result.user.getSpecialty(), result.message
            );
            enqueuePacket(new Packet(Constants.TYPE_ADMIN_CREATE_RESP, reply));
            System.out.printf("👤 [ADMIN CREATE USER] Admin %s đã tạo user %s (%s - %s)\n",
                loggedInUser.getUsername(), result.user.getUsername(), result.user.getRole(), result.user.getSpecialty());
        } else {
            String reply = "ADMIN_CREATE_RESP|status=FAIL|message=" + (result.message != null ? result.message : "Tạo tài khoản thất bại");
            enqueuePacket(new Packet(Constants.TYPE_ADMIN_CREATE_RESP, reply));
        }
    }

    private void handleForgotPassword(Map<String, String> params) {
        String username = params.get("username");
        String udpPortStr = params.get("udpPort");

        if (username == null || username.trim().isEmpty() || udpPortStr == null) {
            enqueuePacket(new Packet(Constants.TYPE_FORGOT_RESP,
                "FORGOT_RESP|status=FAIL|message=Thiếu thông tin tên đăng nhập hoặc cổng UDP"));
            return;
        }

        int udpPort;
        try {
            udpPort = Integer.parseInt(udpPortStr.trim());
        } catch (NumberFormatException e) {
            enqueuePacket(new Packet(Constants.TYPE_FORGOT_RESP,
                "FORGOT_RESP|status=FAIL|message=Cổng UDP không hợp lệ: " + udpPortStr));
            return;
        }

        User user = UserDAO.getUserByUsername(username.trim());
        if (user == null) {
            enqueuePacket(new Packet(Constants.TYPE_FORGOT_RESP,
                "FORGOT_RESP|status=FAIL|message=Không tìm thấy tài khoản '" + username + "' trong hệ thống"));
            return;
        }

        String otp = OtpManager.generateOtp(username.trim());
        String clientIp = socket.getInetAddress().getHostAddress();

        try (DatagramSocket udpSocket = new DatagramSocket()) {
            String payload = String.format(
                "OTP|code=%s|username=%s|expiresIn=60|message=Mã xác thực OTP đặt lại mật khẩu của bạn là %s (có hiệu lực trong 60 giây).",
                otp, username.trim(), otp
            );
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName(clientIp);
            DatagramPacket udpPacket = new DatagramPacket(data, data.length, addr, udpPort);
            udpSocket.send(udpPacket);

            System.out.printf("📡 [UDP OTP SENT] Đã gửi mã OTP %s tới %s:%d cho user '%s'\n",
                otp, clientIp, udpPort, username.trim());

            enqueuePacket(new Packet(Constants.TYPE_FORGOT_RESP,
                "FORGOT_RESP|status=OK|message=Mã OTP đã được gửi qua socket UDP tới thiết bị của bạn"));
        } catch (Exception e) {
            System.err.println("❌ Lỗi gửi UDP OTP: " + e.getMessage());
            enqueuePacket(new Packet(Constants.TYPE_FORGOT_RESP,
                "FORGOT_RESP|status=FAIL|message=Lỗi mạng khi gửi OTP qua UDP: " + e.getMessage()));
        }
    }

    private void handleResetPassword(Packet packet, Map<String, String> params) {
        String username = params.get("username");
        String otp = params.get("otp");
        String newPassword = packet.hasAttachment()
                ? new String(packet.getAttachmentBytes(), StandardCharsets.UTF_8)
                : params.get("newPassword");

        if (username == null || otp == null || newPassword == null || newPassword.trim().isEmpty()) {
            enqueuePacket(new Packet(Constants.TYPE_RESET_RESP,
                "RESET_RESP|status=FAIL|message=Thiếu thông tin OTP hoặc mật khẩu mới"));
            return;
        }

        boolean validOtp = OtpManager.validateAndConsume(username.trim(), otp.trim());
        if (!validOtp) {
            enqueuePacket(new Packet(Constants.TYPE_RESET_RESP,
                "RESET_RESP|status=FAIL|message=Mã OTP không chính xác hoặc đã quá hạn 60 giây"));
            return;
        }

        boolean success = AuthService.resetPassword(username.trim(), newPassword);
        if (success) {
            enqueuePacket(new Packet(Constants.TYPE_RESET_RESP,
                "RESET_RESP|status=OK|message=Đặt lại mật khẩu thành công! Bạn có thể đăng nhập bằng mật khẩu mới"));
            System.out.printf("🔑 [RESET PASSWORD OK] User '%s' đã đổi mật khẩu thành công qua xác thực UDP OTP\n", username.trim());
        } else {
            enqueuePacket(new Packet(Constants.TYPE_RESET_RESP,
                "RESET_RESP|status=FAIL|message=Không thể cập nhật mật khẩu mới vào cơ sở dữ liệu"));
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
            DispatchWorker.recordRejection(loggedInUser.getUserId(), ticketId);

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

    private void handleChatSend(Packet packet, Map<String, String> params) {
        if (loggedInUser == null) {
            sendError("UNAUTHORIZED", "Vui lòng đăng nhập trước khi gửi tin nhắn chat");
            return;
        }

        int ticketId = 0;
        try { ticketId = Integer.parseInt(params.get("ticketId")); } catch (Exception ignored) {}

        String content = null;
        if (packet.hasAttachment()) {
            content = new String(packet.getAttachmentBytes(), StandardCharsets.UTF_8);
        } else {
            content = params.get("content");
        }

        if (content == null || content.trim().isEmpty()) {
            return;
        }
        content = content.trim();

        ChatMessage chatMsg = new ChatMessage(
            ticketId > 0 ? ticketId : null,
            loggedInUser.getUserId(),
            loggedInUser.getFullName(),
            loggedInUser.getRole(),
            content
        );

        boolean saved = ChatMessageDAO.saveMessage(chatMsg);
        if (saved) {
            String pushHeader = String.format(
                "CHAT_PUSH|msgId=%d|ticketId=%d|senderId=%d|senderName=%s|senderRole=%s|sentAt=%s",
                chatMsg.getMessageId(),
                ticketId,
                chatMsg.getSenderId(),
                chatMsg.getSenderName(),
                chatMsg.getSenderRole(),
                chatMsg.getSentAt() != null ? chatMsg.getSentAt() : ""
            );
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            Packet pushPacket = new Packet(Constants.TYPE_CHAT_PUSH, pushHeader, contentBytes);

            // Broadcast tới tất cả client đang online (mỗi client tự hiển thị nếu đang ở kênh tương ứng)
            ServerSessionManager.broadcast(pushPacket);

            System.out.printf("💬 [CHAT] %s (%s) gửi vào %s: %s\n",
                loggedInUser.getFullName(),
                loggedInUser.getRole(),
                ticketId > 0 ? "Sự cố #" + ticketId : "Kênh Chung",
                content.length() > 40 ? content.substring(0, 40) + "..." : content
            );
        } else {
            sendError("CHAT_FAILED", "Không thể lưu tin nhắn chat vào CSDL");
        }
    }

    private void handleChatHistory(Map<String, String> params) {
        if (loggedInUser == null) return;

        int ticketId = 0;
        try { ticketId = Integer.parseInt(params.get("ticketId")); } catch (Exception ignored) {}

        List<ChatMessage> list;
        if (ticketId > 0) {
            list = ChatMessageDAO.getMessagesByTicket(ticketId);
        } else {
            list = ChatMessageDAO.getGeneralMessages(50);
        }

        String json = gson.toJson(list);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        Packet resp = new Packet(Constants.TYPE_CHAT_HISTORY_RESP, "CHAT_HISTORY_RESP|ticketId=" + ticketId, jsonBytes);
        enqueuePacket(resp);
    }

    private void handleManualAssign(Map<String, String> params) {
        if (loggedInUser == null || !Constants.ROLE_ADMIN.equals(loggedInUser.getRole())) {
            sendError("FORBIDDEN", "Chỉ Ban Giám Hiệu mới có quyền giao nhiệm vụ trực tiếp");
            return;
        }

        int ticketId = 0;
        int staffId = 0;
        try {
            ticketId = Integer.parseInt(params.get("ticketId"));
            staffId = Integer.parseInt(params.get("staffId"));
        } catch (Exception ignored) {}

        if (ticketId <= 0 || staffId <= 0) {
            sendError("BAD_REQUEST", "Thiếu mã sự cố hoặc mã nhân sự phụ trách");
            return;
        }

        String note = params.get("note");
        IncidentTicket oldTicket = TicketDAO.getTicketById(ticketId);
        if (oldTicket == null) {
            sendError("NOT_FOUND", "Không tìm thấy sự cố #" + ticketId);
            return;
        }

        User staff = UserDAO.getUserById(staffId);
        if (staff == null) {
            sendError("STAFF_NOT_FOUND", "Không tìm thấy nhân viên #" + staffId);
            return;
        }

        Integer oldStaffId = oldTicket.getAssignedStaffId();
        boolean success = TicketDAO.assignTicketManual(ticketId, staffId);
        if (success) {
            if (oldStaffId == null || oldStaffId == 0) {
                UserDAO.adjustWorkload(staffId, 1);
            } else if (oldStaffId != staffId) {
                UserDAO.adjustWorkload(oldStaffId, -1);
                UserDAO.adjustWorkload(staffId, 1);
            }

            // Ghi nhật ký điều phối
            DispatchLogDAO.logDispatch(
                ticketId, staffId, staff.getCurrentWorkload(),
                oldTicket.getComputedPriority(), Constants.DISPATCH_ACCEPTED,
                "Ban Giám hiệu (" + loggedInUser.getFullName() + ") chỉ định: " + (note != null ? note : "Chỉ định trực tiếp")
            );

            // Dọn dẹp hàng đợi và push đang chờ
            Server.getAgingQueue().removeTicket(ticketId);
            DispatchWorker.clearPendingPush(staffId, ticketId);
            if (oldStaffId != null && oldStaffId > 0) {
                DispatchWorker.clearPendingPush(oldStaffId, ticketId);
            }

            // Phản hồi cho Admin
            String reply = String.format("MANUAL_ASSIGN_RESP|ticketId=%d|status=OK|staffName=%s", ticketId, staff.getFullName());
            enqueuePacket(new Packet(Constants.TYPE_MANUAL_ASSIGN_RESP, reply));

            // Broadcast thông báo cập nhật trạng thái tới tất cả client
            String notifyHeader = String.format(
                "STATUS_UPDATE|ticketId=%d|code=%s|status=ASSIGNED|staffName=%s|staffId=%d|note=%s",
                ticketId, oldTicket.getTicketCode(), staff.getFullName(), staffId, note != null ? note : ""
            );
            ServerSessionManager.broadcast(new Packet(Constants.TYPE_STATUS_UPDATE, notifyHeader));

            System.out.printf("🎯 [MANUAL ASSIGN] BGH %s đã chỉ định sự cố %s (#%d) cho %s\n",
                loggedInUser.getFullName(), oldTicket.getTicketCode(), ticketId, staff.getFullName());
        } else {
            sendError("ASSIGN_FAILED", "Không thể cập nhật phân công sự cố #" + ticketId);
        }
    }

    private void handleGetOpenPool() {
        if (loggedInUser == null) return;
        List<IncidentTicket> list = TicketDAO.getPendingTickets();
        String json = gson.toJson(list);
        enqueuePacket(new Packet(Constants.TYPE_GET_OPEN_POOL_RESP, json));
    }

    private void handleGetStaffList() {
        if (loggedInUser == null) return;
        List<User> list = UserDAO.getAllResponders();
        for (User u : list) {
            u.setIsOnline(ServerSessionManager.isUserOnline(u.getUserId()) ? 1 : 0);
        }
        String json = gson.toJson(list);
        enqueuePacket(new Packet(Constants.TYPE_GET_STAFF_LIST_RESP, json));
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