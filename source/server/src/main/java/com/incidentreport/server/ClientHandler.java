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
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Xử lý kết nối TCP Socket cho từng Client (Multi-threaded & Asynchronous I/O):
 * - Tách biệt hoàn toàn luồng Đọc và luồng Ghi qua BlockingQueue có giới hạn (1000 items).
 * - Triển khai giao thức nhị phân chuẩn 0xCAFE.
 * - Điều phối báo cáo tự động theo phòng ban kỹ thuật / y tế / an ninh.
 * - Đẩy ngay cho cán bộ online hoặc tự động nạp backlog khi cán bộ đăng nhập.
 * - Phân quyền chặt chẽ phía Server:
 *   + Chỉ Admin mới được xem toàn bộ sự cố (scope=ALL), lấy thống kê, tạo tài khoản.
 *   + Responder chỉ được nhận (claim) sự cố đúng chuyên môn phòng ban.
 *   + Chỉ người tạo vé hoặc cán bộ trực tiếp xử lý/Admin mới được cập nhật trạng thái hoặc chat 1-1.
 * - Cache chống trùng lặp (Idempotency) theo người dùng dạng LRU có giới hạn dung lượng.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private final Gson gson = new Gson();

    private User loggedInUser = null;
    private volatile boolean running = true;

    // Hàng đợi gửi bất đồng bộ có giới hạn dung lượng (1000) chống tràn RAM
    private final BlockingQueue<Packet> outgoingQueue = new LinkedBlockingQueue<>(1000);
    private Thread senderThread;

    // LRU Cache an toàn đa luồng giới hạn 500 bản ghi
    private static final int MAX_CACHE_SIZE = 500;
    private static final Map<String, Packet> idempotencyCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Packet>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Packet> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    private static final java.util.concurrent.atomic.AtomicInteger TICKET_COUNTER =
            new java.util.concurrent.atomic.AtomicInteger(java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 9999));

    private static String generateTicketCode() {
        long timePart = System.currentTimeMillis() % 100000;
        int countPart = Math.abs(TICKET_COUNTER.incrementAndGet() % 10);
        return String.format("TK-%05d%d", timePart, countPart);
    }

    // Whitelist kiểm tra hợp lệ
    private static final Set<String> VALID_ROLES = new HashSet<>(Arrays.asList(
            Constants.ROLE_ADMIN, Constants.ROLE_REPORTER, Constants.ROLE_RESPONDER
    ));

    private static final Set<String> VALID_SPECIALTIES = new HashSet<>(Arrays.asList(
            Constants.SPECIALTY_NONE, Constants.SPECIALTY_TECHNICAL, Constants.SPECIALTY_MEDICAL,
            Constants.SPECIALTY_SECURITY, Constants.SPECIALTY_ALL
    ));

    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList(
            Constants.STATUS_PENDING, Constants.STATUS_IN_PROGRESS, Constants.STATUS_RESOLVED, Constants.STATUS_CANCELLED
    ));

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public User getLoggedInUser() {
        return loggedInUser;
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.replace("|", " ").replace("=", ":").trim();
    }

    @Override
    public void run() {
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSoTimeout(300000); // 5 phút không có tín hiệu thì đóng kết nối

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
                if (out != null) {
                    packet.writeTo(out);
                }
            } catch (InterruptedException e) {
                break;
            } catch (IOException e) {
                if (running) {
                    System.err.printf("⚠️ Lỗi ghi socket tới client %s: %s\n", socket.getRemoteSocketAddress(), e.getMessage());
                }
                break;
            }
        }
        closeConnection();
    }

    public void enqueuePacket(Packet packet) {
        if (running && packet != null) {
            boolean added = outgoingQueue.offer(packet);
            if (!added) {
                System.err.println("⚠️ Hàng đợi gửi đầy (1000 items), bỏ qua gói tin: " + packet.getType());
            }
        }
    }

    private void handlePacket(Packet packet) {
        byte type = packet.getType();
        Map<String, String> params = packet.parseParams();

        String reqId = params.get("reqId");
        String cacheKey = (loggedInUser != null ? loggedInUser.getUserId() : "anon") + ":" + reqId;
        if (reqId != null) {
            Packet cachedResp = idempotencyCache.get(cacheKey);
            if (cachedResp != null) {
                enqueuePacket(cachedResp);
                return;
            }
        }

        switch (type) {
            case Constants.TYPE_LOGIN:
                handleLogin(packet, params);
                break;

            case Constants.TYPE_SUBMIT_INCIDENT:
                handleSubmitIncident(packet, params, cacheKey);
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

            case Constants.TYPE_GET_IMAGE:
                handleGetImage(params);
                break;

            case Constants.TYPE_LOGOUT:
                closeConnection();
                break;

            default:
                sendError("UNKNOWN_OPCODE", "Mã loại bản tin không được hỗ trợ: " + type);
                break;
        }
    }

    private void handleLogin(Packet packet, Map<String, String> params) {
        String username = sanitize(params.get("username"));
        String password = packet.getAttachment() != null && packet.getAttachment().length > 0
                ? new String(packet.getAttachment(), StandardCharsets.UTF_8)
                : params.get("password");

        if (username.isEmpty() || password == null || password.isEmpty()) {
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
                sanitize(user.getFullName()), user.getPhone() != null ? sanitize(user.getPhone()) : "");

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

    private void handleSubmitIncident(Packet packet, Map<String, String> params, String cacheKey) {
        if (loggedInUser == null) {
            sendError("UNAUTHORIZED", "Bạn cần đăng nhập trước khi gửi báo cáo sự cố!");
            return;
        }

        String roomLocation = sanitize(params.get("room"));
        String categoryCode = sanitize(params.get("category"));
        String description = sanitize(params.get("desc"));

        if (roomLocation.isEmpty() || categoryCode.isEmpty() || description.isEmpty()) {
            sendError("BAD_REQUEST", "Thiếu thông tin phòng, phân loại hoặc mô tả sự cố!");
            return;
        }

        // Lưu ảnh hiện trường nếu có đính kèm
        String imagePath = null;
        byte[] attach = packet.getAttachment();
        if (attach != null && attach.length > 0) {
            try {
                File uploadDir = InitDatabase.getUploadDirectory();
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs();
                }
                String fileBase = "img_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6) + ".jpg";
                File destFile = new File(uploadDir, fileBase);
                Files.write(destFile.toPath(), attach);
                imagePath = "uploads/" + fileBase;
                System.out.printf("📸 [IMAGE] Đã lưu ảnh hiện trường sự cố vào thư mục cố định: %s (%d bytes)\n", destFile.getAbsolutePath(), attach.length);
            } catch (IOException e) {
                System.err.println("⚠️ Không thể lưu ảnh đính kèm: " + e.getMessage());
            }
        }

        // Tự động phân loại và định tuyến đúng phòng ban
        IncidentCategory category = CategoryDAO.getCategory(categoryCode);
        String targetDept = (category != null && category.getTargetDepartment() != null)
                ? category.getTargetDepartment().trim().toUpperCase()
                : Constants.SPECIALTY_TECHNICAL;

        IncidentTicket ticket = new IncidentTicket();
        ticket.setRoomLocation(roomLocation);
        ticket.setCategoryCode(categoryCode);
        ticket.setTargetDepartment(targetDept);
        ticket.setDescription(description);
        ticket.setReporterId(loggedInUser.getUserId());
        ticket.setReporterName(loggedInUser.getFullName());
        ticket.setReporterPhone(loggedInUser.getPhone());
        ticket.setImageFilePath(imagePath);
        ticket.setStatus(Constants.STATUS_PENDING);

        boolean ok = false;
        for (int retry = 0; retry < 30; retry++) {
            ticket.setTicketCode(generateTicketCode());
            ok = TicketDAO.createTicket(ticket);
            if (ok) break;
            try { Thread.sleep(10 + (int)(Math.random() * 20)); } catch (InterruptedException ignored) {}
        }

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

        if (cacheKey != null) {
            idempotencyCache.put(cacheKey, respPkt);
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

        IncidentTicket ticket = TicketDAO.getTicketById(ticketId);
        if (ticket == null) {
            sendError("NOT_FOUND", "Sự cố không tồn tại!");
            return;
        }

        // PHÂN QUYỀN CHẶT CHẼ: Cán bộ chỉ được nhận sự cố đúng chuyên môn phòng ban của mình
        if (!"ALL".equalsIgnoreCase(loggedInUser.getSpecialty()) &&
                !ticket.getTargetDepartment().equalsIgnoreCase(loggedInUser.getSpecialty())) {
            sendError("FORBIDDEN", String.format("Bạn thuộc phòng %s, không thể nhận việc của phòng %s!",
                    loggedInUser.getSpecialty(), ticket.getTargetDepartment()));
            return;
        }

        boolean success = TicketDAO.claimTicket(ticketId, loggedInUser.getUserId(), loggedInUser.getFullName());
        if (success) {
            ticket = TicketDAO.getTicketById(ticketId);
            Packet respPkt = new Packet(Constants.TYPE_CLAIM_RESP, "CLAIM_RESP|status=OK|ticketId=" + ticketId);
            enqueuePacket(respPkt);

            // Bắn tin cập nhật trạng thái tới Reporter, phòng ban và Admin
            String tCode = ticket != null ? ticket.getTicketCode() : "";
            String tRoom = ticket != null ? ticket.getRoomLocation() : "";
            String tCat = ticket != null && ticket.getCategoryName() != null ? ticket.getCategoryName() : "";
            Packet statusPkt = new Packet(Constants.TYPE_STATUS_BROADCAST,
                    String.format("STATUS_BROADCAST|ticketId=%d|ticketCode=%s|room=%s|category=%s|status=%s|staffId=%d|staffName=%s|action=CLAIM",
                            ticketId, tCode, tRoom, tCat, Constants.STATUS_IN_PROGRESS, loggedInUser.getUserId(), loggedInUser.getFullName()));

            if (ticket != null) {
                ServerSessionManager.sendToUser(ticket.getReporterId(), statusPkt);
                ServerSessionManager.sendToDepartment(ticket.getTargetDepartment(), statusPkt);
            }
            ServerSessionManager.sendToAdmin(statusPkt);
            System.out.printf("🤝 [CLAIM] Cán bộ %s đã tiếp nhận sự cố #%d.\n", loggedInUser.getFullName(), ticketId);
        } else {
            Packet respPkt = new Packet(Constants.TYPE_CLAIM_RESP, "CLAIM_RESP|status=ALREADY_TAKEN|ticketId=" + ticketId + "|message=Sự cố đã được người khác tiếp nhận hoặc không đúng chuyên môn!");
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
        String note = sanitize(params.getOrDefault("note", "Admin phân công trực tiếp"));

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
                String pushHeader = String.format("DISPATCH_NOTIFY|ticketId=%d|code=%s|room=%s|category=%s|department=%s|desc=%s|time=%s|assignedByAdmin=true|staffId=%d|note=%s",
                        ticket.getTicketId(), ticket.getTicketCode(), ticket.getRoomLocation(),
                        ticket.getCategoryName() != null ? ticket.getCategoryName() : ticket.getCategoryCode(),
                        ticket.getTargetDepartment(), ticket.getDescription(), ticket.getCreatedAt(),
                        staffId, note);
                Packet pushPkt = new Packet(Constants.TYPE_DISPATCH_NOTIFY, pushHeader, gson.toJson(ticket).getBytes(StandardCharsets.UTF_8));
                ServerSessionManager.sendToUser(staffId, pushPkt);

                Packet statusPkt = new Packet(Constants.TYPE_STATUS_BROADCAST,
                        String.format("STATUS_BROADCAST|ticketId=%d|ticketCode=%s|room=%s|category=%s|status=%s|staffId=%d|staffName=%s|note=%s|action=ASSIGN",
                                ticketId, ticket.getTicketCode(), ticket.getRoomLocation(),
                                ticket.getCategoryName() != null ? ticket.getCategoryName() : ticket.getCategoryCode(),
                                Constants.STATUS_IN_PROGRESS, staffId, staff.getFullName(), note));
                ServerSessionManager.sendToUser(ticket.getReporterId(), statusPkt);
                ServerSessionManager.sendToDepartment(ticket.getTargetDepartment(), statusPkt);
                ServerSessionManager.sendToAdmin(statusPkt);
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
        String status = sanitize(params.get("status"));
        String note = sanitize(params.getOrDefault("note", ""));

        if (ticketId <= 0 || status.isEmpty()) {
            sendError("BAD_REQUEST", "Thiếu mã sự cố hoặc trạng thái!");
            return;
        }

        if (!VALID_STATUSES.contains(status)) {
            sendError("BAD_REQUEST", "Trạng thái sự cố không hợp lệ: " + status);
            return;
        }

        IncidentTicket ticket = TicketDAO.getTicketById(ticketId);
        if (ticket == null) {
            sendError("NOT_FOUND", "Sự cố không tồn tại!");
            return;
        }

        // BẢO VỆ TOÀN VẸN TRẠNG THÁI: Vé đã HOÀN TẤT hoặc ĐÃ HỦY tuyệt đối không thể sửa đổi
        if (Constants.STATUS_RESOLVED.equalsIgnoreCase(ticket.getStatus())
                || Constants.STATUS_CANCELLED.equalsIgnoreCase(ticket.getStatus())) {
            sendError("INVALID_TRANSITION", "Sự cố đã kết thúc (" + ticket.getStatus() + "), không thể thay đổi trạng thái!");
            return;
        }

        boolean isAdmin = Constants.ROLE_ADMIN.equalsIgnoreCase(loggedInUser.getRole());
        boolean isAssignedStaff = ticket.getAssignedStaffId() != null && ticket.getAssignedStaffId() == loggedInUser.getUserId();
        boolean isReporter = ticket.getReporterId() == loggedInUser.getUserId();

        // PHÂN QUYỀN CHẶT CHẼ TRẠNG THÁI SỰ CỐ:
        if (Constants.STATUS_CANCELLED.equalsIgnoreCase(status)) {
            // Sự cố chỉ có thể hủy khi ĐANG Ở TRẠNG THÁI PENDING (chưa có cán bộ tiếp nhận hay hoàn thành)
            if (!Constants.STATUS_PENDING.equalsIgnoreCase(ticket.getStatus())) {
                sendError("FORBIDDEN", "Sự cố đã được cán bộ tiếp nhận hoặc đang xử lý, không thể hủy!");
                return;
            }
            if (!isAdmin && !isReporter) {
                sendError("FORBIDDEN", "Chỉ Ban Giám Hiệu hoặc người tạo báo cáo mới có thể hủy sự cố!");
                return;
            }
        } else {
            // IN_PROGRESS, RESOLVED chỉ cho phép Admin hoặc cán bộ được giao trực tiếp xử lý
            if (!isAdmin && !isAssignedStaff) {
                sendError("FORBIDDEN", "Chỉ Ban Giám Hiệu hoặc cán bộ trực tiếp phụ trách mới có quyền cập nhật trạng thái sự cố!");
                return;
            }
        }

        boolean ok = TicketDAO.updateStatus(ticketId, status, note);
        if (ok) {
            ticket = TicketDAO.getTicketById(ticketId);
            String tCode = ticket != null ? ticket.getTicketCode() : "";
            String tRoom = ticket != null ? ticket.getRoomLocation() : "";
            String tCat = ticket != null && ticket.getCategoryName() != null ? ticket.getCategoryName() : "";
            Packet statusPkt = new Packet(Constants.TYPE_STATUS_BROADCAST,
                    String.format("STATUS_BROADCAST|ticketId=%d|ticketCode=%s|room=%s|category=%s|status=%s|staffName=%s|note=%s|action=UPDATE",
                            ticketId, tCode, tRoom, tCat, status, loggedInUser.getFullName(), note));

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

        String scope = sanitize(params.getOrDefault("scope", "MY"));
        List<IncidentTicket> list;

        // PHÂN QUYỀN CHẶT CHẼ PHẠM VI XEM VÉ:
        if ("ALL".equalsIgnoreCase(scope)) {
            if (!Constants.ROLE_ADMIN.equalsIgnoreCase(loggedInUser.getRole())) {
                sendError("FORBIDDEN", "Chỉ Ban Giám Hiệu (Admin) mới có quyền xem toàn bộ sự cố!");
                return;
            }
            list = TicketDAO.getAllTickets();
        } else if ("DEPARTMENT".equalsIgnoreCase(scope)) {
            if (!Constants.ROLE_RESPONDER.equalsIgnoreCase(loggedInUser.getRole())) {
                sendError("FORBIDDEN", "Chỉ cán bộ phòng ban (Responder) mới có quyền xem sự cố theo phòng ban!");
                return;
            }
            list = TicketDAO.getTicketsByDepartment(loggedInUser.getSpecialty());
        } else {
            // Scope MY
            if (Constants.ROLE_RESPONDER.equalsIgnoreCase(loggedInUser.getRole())) {
                list = TicketDAO.getTicketsByStaff(loggedInUser.getUserId());
            } else {
                list = TicketDAO.getTicketsByReporter(loggedInUser.getUserId());
            }
        }

        byte[] body = gson.toJson(list).getBytes(StandardCharsets.UTF_8);
        Packet resp = new Packet(Constants.TYPE_LIST_TICKETS_RESP,
                String.format("LIST_TICKETS_RESP|scope=%s|count=%d", scope, list.size()), body);
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

        IncidentTicket ticket = TicketDAO.getTicketById(ticketId);
        if (ticket == null) {
            sendError("NOT_FOUND", "Sự cố không tồn tại!");
            return;
        }

        // PHÂN QUYỀN CHAT 1-1: Chỉ người báo, cán bộ nhận việc hoặc Admin mới được chat
        boolean isAdmin = Constants.ROLE_ADMIN.equalsIgnoreCase(loggedInUser.getRole());
        boolean isReporter = ticket.getReporterId() == loggedInUser.getUserId();
        boolean isAssignedStaff = ticket.getAssignedStaffId() != null && ticket.getAssignedStaffId() == loggedInUser.getUserId();

        if (!isAdmin && !isReporter && !isAssignedStaff) {
            sendError("FORBIDDEN", "Bạn không có quyền tham gia kênh trao đổi của sự cố này!");
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

        // ĐÓNG GÓI GÓI TIN CHAT_PUSH ĐỂ ĐIỀU PHỐI ĐÚNG 1-1
        Packet pushPkt = new Packet(Constants.TYPE_CHAT_PUSH,
                String.format("CHAT_PUSH|ticketId=%d|senderId=%d|senderName=%s|senderRole=%s|time=%s",
                        ticketId, loggedInUser.getUserId(), loggedInUser.getFullName(), loggedInUser.getRole(), msg.getSentAt()),
                msg.getContent().getBytes(StandardCharsets.UTF_8));

        // Gửi cho người báo cáo nếu người gửi không phải họ
        if (ticket.getReporterId() > 0 && ticket.getReporterId() != loggedInUser.getUserId()) {
            ServerSessionManager.sendToUser(ticket.getReporterId(), pushPkt);
        }
        // Gửi cho cán bộ phụ trách nếu người gửi không phải họ
        if (ticket.getAssignedStaffId() != null && ticket.getAssignedStaffId() > 0 && ticket.getAssignedStaffId() != loggedInUser.getUserId()) {
            ServerSessionManager.sendToUser(ticket.getAssignedStaffId(), pushPkt);
        }

        // Server/Admin vẫn có thể giám sát kiểm toán tin nhắn
        if (!isAdmin) {
            ServerSessionManager.sendToAdmin(pushPkt);
        }

        // Echo lại cho chính người gửi để xác nhận tin đã gửi thành công
        enqueuePacket(pushPkt);
    }

    private void handleChatHistory(Map<String, String> params) {
        if (loggedInUser == null) {
            sendError("UNAUTHORIZED", "Vui lòng đăng nhập!");
            return;
        }

        int ticketId = Integer.parseInt(params.getOrDefault("ticketId", "0"));
        if (ticketId <= 0) {
            sendError("BAD_REQUEST", "Mã sự cố không hợp lệ!");
            return;
        }

        IncidentTicket ticket = TicketDAO.getTicketById(ticketId);
        if (ticket == null) {
            sendError("NOT_FOUND", "Sự cố không tồn tại!");
            return;
        }

        // PHÂN QUYỀN XEM LỊCH SỬ CHAT: Chỉ người báo, cán bộ nhận việc hoặc Admin mới được xem
        boolean isAdmin = Constants.ROLE_ADMIN.equalsIgnoreCase(loggedInUser.getRole());
        boolean isReporter = ticket.getReporterId() == loggedInUser.getUserId();
        boolean isAssignedStaff = ticket.getAssignedStaffId() != null && ticket.getAssignedStaffId() == loggedInUser.getUserId();

        if (!isAdmin && !isReporter && !isAssignedStaff) {
            sendError("FORBIDDEN", "Bạn không có quyền xem lịch sử trao đổi của sự cố này!");
            return;
        }

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

        String username = sanitize(params.get("username"));
        String fullName = sanitize(params.get("fullName"));
        String role = sanitize(params.get("role"));
        String specialty = sanitize(params.getOrDefault("specialty", Constants.SPECIALTY_NONE));
        String phone = sanitize(params.getOrDefault("phone", ""));

        String password = packet.getAttachment() != null && packet.getAttachment().length > 0
                ? new String(packet.getAttachment(), StandardCharsets.UTF_8)
                : params.getOrDefault("password", "123456");

        if (username.isEmpty() || fullName.isEmpty() || role.isEmpty()) {
            enqueuePacket(new Packet(Constants.TYPE_ADMIN_CREATE_RESP, "ADMIN_CREATE_RESP|status=FAIL|message=Thiếu thông tin bắt buộc!"));
            return;
        }

        if (!VALID_ROLES.contains(role)) {
            enqueuePacket(new Packet(Constants.TYPE_ADMIN_CREATE_RESP, "ADMIN_CREATE_RESP|status=FAIL|message=Vai trò không hợp lệ: " + role));
            return;
        }

        if (!VALID_SPECIALTIES.contains(specialty)) {
            enqueuePacket(new Packet(Constants.TYPE_ADMIN_CREATE_RESP, "ADMIN_CREATE_RESP|status=FAIL|message=Phòng ban chuyên môn không hợp lệ: " + specialty));
            return;
        }

        if (UserDAO.usernameExists(username)) {
            enqueuePacket(new Packet(Constants.TYPE_ADMIN_CREATE_RESP, "ADMIN_CREATE_RESP|status=FAIL|message=Tên đăng nhập '" + username + "' đã tồn tại!"));
            System.out.printf("⚠️ [ADMIN] Từ chối tạo tài khoản: tên đăng nhập '%s' đã tồn tại.\n", username);
            return;
        }

        User newUser = new User(username, password.trim(), fullName, role, specialty, phone);
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

        String timeRange = sanitize(params.getOrDefault("timeRange", "ALL"));
        StatisticReport report = TicketDAO.getStatistics(timeRange);
        byte[] body = gson.toJson(report).getBytes(StandardCharsets.UTF_8);

        enqueuePacket(new Packet(Constants.TYPE_ADMIN_GET_STATS_RESP, "ADMIN_GET_STATS_RESP|range=" + timeRange, body));
    }

    private void handleGetImage(Map<String, String> params) {
        int ticketId = Integer.parseInt(params.getOrDefault("ticketId", "0"));
        IncidentTicket ticket = TicketDAO.getTicketById(ticketId);
        if (ticket == null || ticket.getImageFilePath() == null || ticket.getImageFilePath().trim().isEmpty()) {
            enqueuePacket(new Packet(Constants.TYPE_GET_IMAGE_RESP, "GET_IMAGE_RESP|ticketId=" + ticketId + "|status=NOT_FOUND"));
            return;
        }

        File imgFile = InitDatabase.findFile(ticket.getImageFilePath());
        if (!imgFile.exists()) {
            enqueuePacket(new Packet(Constants.TYPE_GET_IMAGE_RESP, "GET_IMAGE_RESP|ticketId=" + ticketId + "|status=NOT_FOUND"));
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(imgFile.toPath());
            Packet resp = new Packet(Constants.TYPE_GET_IMAGE_RESP,
                    "GET_IMAGE_RESP|ticketId=" + ticketId + "|status=OK|fileName=" + imgFile.getName(),
                    bytes);
            enqueuePacket(resp);
            System.out.printf("📤 [IMAGE] Đã truyền tải ảnh sự cố #%d (%d bytes) tới client.\n", ticketId, bytes.length);
        } catch (IOException e) {
            System.err.println("⚠️ Lỗi đọc file ảnh truyền tải tới client: " + e.getMessage());
            enqueuePacket(new Packet(Constants.TYPE_GET_IMAGE_RESP, "GET_IMAGE_RESP|ticketId=" + ticketId + "|status=ERROR"));
        }
    }

    private Packet createDispatchPacket(IncidentTicket ticket) {
        String header = String.format("DISPATCH_NOTIFY|ticketId=%d|code=%s|room=%s|category=%s|department=%s|desc=%s|time=%s",
                ticket.getTicketId(), ticket.getTicketCode(), ticket.getRoomLocation(),
                ticket.getCategoryCode(), ticket.getTargetDepartment(),
                sanitize(ticket.getDescription()),
                ticket.getCreatedAt() != null ? ticket.getCreatedAt() : "");
        return new Packet(Constants.TYPE_DISPATCH_NOTIFY, header, gson.toJson(ticket).getBytes(StandardCharsets.UTF_8));
    }

    private void sendError(String code, String message) {
        enqueuePacket(new Packet(Constants.TYPE_ERROR, String.format("ERROR|code=%s|message=%s", code, sanitize(message))));
    }

    public void closeConnection() {
        running = false;
        if (loggedInUser != null) {
            int uid = loggedInUser.getUserId();
            String uname = loggedInUser.getUsername();
            loggedInUser = null;
            ServerSessionManager.removeClient(uid, this);
            if (!ServerSessionManager.isUserOnline(uid)) {
                UserDAO.updateUserOnlineStatus(uid, false);
            }
            System.out.printf("👋 [LOGOUT] Người dùng %s ngắt kết nối.\n", uname);
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }
}
