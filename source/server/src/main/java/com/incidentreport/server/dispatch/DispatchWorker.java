package com.incidentreport.server.dispatch;

import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentCategory;
import com.incidentreport.common.model.IncidentReport;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import com.incidentreport.server.Server;
import com.incidentreport.server.ServerSessionManager;
import com.incidentreport.server.queue.AgingPriorityQueue;
import com.incidentreport.service.CategoryDAO;
import com.incidentreport.service.DispatchLogDAO;
import com.incidentreport.service.ReportDAO;
import com.incidentreport.service.TicketDAO;
import com.incidentreport.service.UserDAO;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Worker Thread điều phối thông minh (Smart Dispatch Worker):
 * 1. Lấy sự cố có điểm ưu tiên cao nhất từ AgingPriorityQueue.
 * 2. Lựa chọn nhân viên phù hợp theo chuyên môn và tải công việc (current_workload) thấp nhất.
 * 3. Đảm bảo mỗi nhân viên chỉ nhận tối đa 1 Popup điều phối tại một thời điểm (Tránh spam, dồn dập).
 * 4. Tự động kiểm soát Timeout 30 giây để thu hồi và điều phối lại nếu nhân viên không phản hồi.
 */
public class DispatchWorker implements Runnable {

    private final AgingPriorityQueue queue;
    private volatile boolean running = true;
    private final ScheduledExecutorService agingScheduler = Executors.newSingleThreadScheduledExecutor();

    // Map theo dõi nhân viên đang chờ phản hồi Popup: staffId -> timestamp (ms) gửi lệnh push
    private static final Map<Integer, Long> staffPendingPush = new ConcurrentHashMap<>();

    // Map theo dõi sự cố đang được push đi chờ ACK: ticketId -> timestamp (ms) gửi lệnh push
    private static final Map<Integer, Long> awaitingAckTickets = new ConcurrentHashMap<>();

    // Map ghi nhận các trường hợp từ chối sự cố để tránh spam lại cùng 1 nhân sự trong 60s
    private static final Map<String, Long> rejectedCache = new ConcurrentHashMap<>();

    public DispatchWorker(AgingPriorityQueue queue) {
        this.queue = queue;
    }

    public static void recordRejection(int staffId, int ticketId) {
        if (staffId > 0 && ticketId > 0) {
            rejectedCache.put(staffId + "_" + ticketId, System.currentTimeMillis());
        }
    }

    /**
     * Giải phóng trạng thái chờ phản hồi khi nhân viên bấm ACCEPT, REJECT hoặc đóng kết nối
     */
    public static void clearPendingPush(int staffId, int ticketId) {
        if (staffId > 0) {
            staffPendingPush.remove(staffId);
        }
        if (ticketId > 0) {
            awaitingAckTickets.remove(ticketId);
        }
    }

    public void start() {
        // Luồng rà soát định kỳ mỗi 5s: cập nhật Aging VÀ kiểm soát Timeout, nạp lại PENDING
        agingScheduler.scheduleAtFixedRate(() -> {
            try {
                queue.updateAllScores();

                long now = System.currentTimeMillis();
                long ackTimeoutMs = Constants.DISPATCH_ACK_TIMEOUT_SEC * 1000L;

                // 1. Quét kiểm tra timeout cho các ticket đang chờ ACK
                for (Map.Entry<Integer, Long> entry : awaitingAckTickets.entrySet()) {
                    int tid = entry.getKey();
                    long pushTime = entry.getValue();
                    if (now - pushTime > ackTimeoutMs) {
                        awaitingAckTickets.remove(tid);
                        System.out.printf("⏰ [DISPATCH TIMEOUT] Sự cố #%d đã quá hạn %ds phản hồi -> Thu hồi để điều phối lại!\n",
                            tid, Constants.DISPATCH_ACK_TIMEOUT_SEC);

                        IncidentTicket expiredTicket = TicketDAO.getTicketById(tid);
                        if (expiredTicket != null && Constants.STATUS_PENDING.equals(expiredTicket.getStatus())) {
                            queue.enqueue(expiredTicket);
                        }
                    }
                }

                // 2. Dọn dẹp trạng thái giữ chỗ của nhân viên đã quá hạn
                staffPendingPush.entrySet().removeIf(e -> (now - e.getValue() > ackTimeoutMs));

                // 3. Nạp tự động các sự cố PENDING từ CSDL vào hàng đợi nếu CHƯA CÓ TRONG HÀNG ĐỢI VÀ KHÔNG ĐANG CHỜ ACK
                List<IncidentTicket> pending = TicketDAO.getPendingTickets();
                for (IncidentTicket t : pending) {
                    if (!awaitingAckTickets.containsKey(t.getTicketId())) {
                        queue.enqueue(t);
                    }
                }
            } catch (Exception ignored) {}
        }, 3, 5, TimeUnit.SECONDS);

        Thread workerThread = new Thread(this, "Smart-Dispatch-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
        System.out.println("✅ [DISPATCH WORKER] Đã khởi động luồng điều phối thông minh!");
    }

    public void stop() {
        running = false;
        agingScheduler.shutdown();
    }

    @Override
    public void run() {
        while (running) {
            try {
                IncidentTicket ticket = queue.dequeue();
                if (ticket == null) continue;

                boolean dispatched = dispatchTicket(ticket);
                if (!dispatched) {
                    // Nếu chưa có nhân viên nào sẵn sàng (hoặc tất cả đang bận xem popup), chờ 3 giây rồi nạp lại
                    Thread.sleep(3000);
                    queue.enqueue(ticket);
                } else {
                    // Đã đẩy thành công: tạm dừng 2 giây trước khi xét duyệt sự cố tiếp theo
                    Thread.sleep(2000);
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("❌ Lỗi trong DispatchWorker: " + e.getMessage());
            }
        }
    }

    private boolean dispatchTicket(IncidentTicket ticket) {
        long now = System.currentTimeMillis();
        long ackTimeoutMs = Constants.DISPATCH_ACK_TIMEOUT_SEC * 1000L;

        // 1. Kiểm tra sự cố còn ở trạng thái PENDING không
        IncidentTicket currentTicket = TicketDAO.getTicketById(ticket.getTicketId());
        if (currentTicket == null || !Constants.STATUS_PENDING.equals(currentTicket.getStatus())) {
            awaitingAckTickets.remove(ticket.getTicketId());
            return true; // Đã được tiếp nhận hoặc xử lý, không điều phối tiếp
        }

        // 2. Nếu sự cố này đang được ai đó xem xét trong hạn 30s thì bỏ qua
        Long ticketPushedAt = awaitingAckTickets.get(ticket.getTicketId());
        if (ticketPushedAt != null && (now - ticketPushedAt < ackTimeoutMs)) {
            return false;
        }

        IncidentCategory category = CategoryDAO.getCategory(ticket.getCategoryCode());
        String reqSpecialty = category != null ? category.getRequiredSpecialty() : "ALL";

        // Tìm kiếm ứng viên phù hợp theo chuyên môn, ưu tiên AVAILABLE và workload thấp nhất
        List<User> candidates = UserDAO.getCandidateResponders(reqSpecialty);

        if (candidates.isEmpty()) {
            return false;
        }

        // 3. Lọc ứng viên: Phải ĐANG KẾT NỐI SOCKET THỰC TẾ, không vừa từ chối, và không có popup chờ
        User chosenStaff = null;
        for (User candidate : candidates) {
            // Bỏ qua nhân sự không kết nối Socket phiên thực tế
            if (!ServerSessionManager.isUserOnline(candidate.getUserId())) {
                continue;
            }

            // Bỏ qua nhân sự vừa từ chối sự cố này trong vòng 60s
            Long rejectedAt = rejectedCache.get(candidate.getUserId() + "_" + ticket.getTicketId());
            if (rejectedAt != null && (now - rejectedAt < 60000L)) {
                continue;
            }

            Long lastPush = staffPendingPush.get(candidate.getUserId());
            if (lastPush == null || (now - lastPush > ackTimeoutMs)) {
                chosenStaff = candidate;
                break;
            }
        }

        if (chosenStaff == null) {
            // Không có nhân sự khả dụng hiện tại
            return false;
        }

        // Đọc ảnh đính kèm (nếu có)
        byte[] imageBytes = loadImageBytes(ticket.getTicketId());

        String headerPayload = String.format(
            "DISPATCH_PUSH|ticketId=%d|code=%s|room=%s|category=%s|categoryName=%s|severity=%d|title=%s|count=%d|ackTimeout=%d",
            ticket.getTicketId(), ticket.getTicketCode(), ticket.getRoomLocation(),
            ticket.getCategoryCode(), category != null ? category.getCategoryName() : ticket.getCategoryCode(),
            ticket.getBaseSeverity(), ticket.getTitle(), ticket.getReportCount(), Constants.DISPATCH_ACK_TIMEOUT_SEC
        );

        Packet pushPacket = new Packet(Constants.TYPE_DISPATCH_PUSH, headerPayload, imageBytes);
        boolean sent = ServerSessionManager.sendToUser(chosenStaff.getUserId(), pushPacket);

        if (sent) {
            // Đánh dấu nhân viên đang có popup hiển thị và ticket đang chờ ACK
            staffPendingPush.put(chosenStaff.getUserId(), now);
            awaitingAckTickets.put(ticket.getTicketId(), now);

            // Ghi nhật ký điều phối
            DispatchLogDAO.logDispatch(
                ticket.getTicketId(), chosenStaff.getUserId(), chosenStaff.getCurrentWorkload(),
                ticket.getComputedPriority(), Constants.DISPATCH_PUSHED,
                "Đã đẩy lệnh nhận việc tới " + chosenStaff.getFullName() + " (Workload: " + chosenStaff.getCurrentWorkload() + ")"
            );

            System.out.printf("🚀 [DISPATCH PUSH] Giao sự cố %s (Phòng %s, Priority=%.1f) -> %s (Tải hiện tại=%d)\n",
                ticket.getTicketCode(), ticket.getRoomLocation(), ticket.getComputedPriority(),
                chosenStaff.getFullName(), chosenStaff.getCurrentWorkload());
            return true;
        }

        return false;
    }

    private byte[] loadImageBytes(int ticketId) {
        try {
            List<IncidentReport> reports = ReportDAO.getReportsByTicketId(ticketId);
            for (IncidentReport r : reports) {
                if (r.getImageFilePath() != null && !r.getImageFilePath().isEmpty()) {
                    File file = new File(r.getImageFilePath());
                    if (!file.exists()) {
                        file = new File("../" + r.getImageFilePath());
                    }
                    if (file.exists()) {
                        try (FileInputStream fis = new FileInputStream(file)) {
                            return fis.readAllBytes();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return new byte[0];
    }
}
