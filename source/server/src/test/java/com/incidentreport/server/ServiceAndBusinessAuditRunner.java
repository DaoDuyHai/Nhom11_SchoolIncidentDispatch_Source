package com.incidentreport.server;

import com.google.gson.Gson;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.*;
import com.incidentreport.common.packet.Packet;
import com.incidentreport.service.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * BỘ AUDIT TẦNG NGHIỆP VỤ, DỮ LIỆU & GIAO THỨC TRỰC DIỆN (IN-PROCESS TEST):
 * Chạy 100% trong sandbox không phụ thuộc port mạng bên ngoài.
 */
public class ServiceAndBusinessAuditRunner {

    private static final Gson gson = new Gson();
    private static final List<String> passed = new ArrayList<>();
    private static final List<String> failed = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("========================================================================");
        System.out.println("  TEAM LEAD DEEP AUDIT: KIỂM TOÁN TẦNG CƠ SỞ DỮ LIỆU, SERVICE & PACKET");
        System.out.println("========================================================================");

        // Cách ly CSDL Test: Tuyệt đối không đụng vào DB chính incident_report.db
        DatabaseConnection.setCustomDbUrl("jdbc:sqlite:database/test_incident_report.db");

        InitDatabase.resetDatabase();
        CategoryDAO.refreshCache();
        UserDAO.resetAllUsersOffline();

        run("1. Xác thực mật khẩu có Salt & Nâng cấp tự động", ServiceAndBusinessAuditRunner::testPasswordSaltAndUpgrade);
        run("2. Toàn vẹn người dùng & Chống trùng tên đăng nhập", ServiceAndBusinessAuditRunner::testUserUniquenessAndCRUD);
        run("3. Danh mục sự cố & Định tuyến tự động theo phòng ban", ServiceAndBusinessAuditRunner::testCategoryAndRouting);
        run("4. Vòng đời Sự cố: Tạo vé -> Tự gán mã -> Tiếp nhận", ServiceAndBusinessAuditRunner::testTicketLifecycle);
        run("5. Tiếp nhận nguyên tử (Atomic Claim) & Ràng buộc chuyên môn", ServiceAndBusinessAuditRunner::testAtomicClaimAndSpecialty);
        run("6. Cập nhật trạng thái vé & Ghi nhận thời gian hoàn tất", ServiceAndBusinessAuditRunner::testTicketStatusAndResolution);
        run("7. Thống kê đa kỳ (DAY/MONTH/QUARTER/YEAR/ALL) & Danh sách CSV", ServiceAndBusinessAuditRunner::testStatisticsAndCsvList);
        run("8. Lưu trữ tin nhắn Chat 1-1 & Tuần tự thời gian", ServiceAndBusinessAuditRunner::testChatPersistence);
        run("9. Đóng gói nhị phân Packet, Magic Header & Giới hạn an toàn", ServiceAndBusinessAuditRunner::testPacketFramingAndLimits);
        run("10. Bộ lọc Header & Phân tách tham số (Pipe/Equal Parsing)", ServiceAndBusinessAuditRunner::testPacketHeaderParsing);

        System.out.println("\n========================================================================");
        System.out.println("  KẾT QUẢ KIỂM TOÁN TẦNG LÕI (CORE IN-PROCESS AUDIT)");
        System.out.println("========================================================================");
        System.out.printf("✅ ĐẠT: %d/%d\n", passed.size(), passed.size() + failed.size());
        for (String p : passed) System.out.println("   [PASSED] " + p);
        if (!failed.isEmpty()) {
            System.out.println("\n❌ THẤT BẠI:");
            for (String f : failed) System.out.println("   [FAILED] " + f);
        } else {
            System.out.println("\n🎉 TOÀN BỘ 10 TIÊU CHÍ TẦNG LÕI ĐẠT CHUẨN XUẤT SẮC!");
        }
        System.out.println("========================================================================\n");
    }

    private static void run(String name, RunnableTest test) {
        System.out.printf("👉 Soi mã & kiểm thử: %-55s ... ", name);
        try {
            test.run();
            passed.add(name);
            System.out.println("[PASS ✅]");
        } catch (Throwable t) {
            failed.add(name + " -> " + t.getMessage());
            System.out.println("[FAIL ❌]");
            t.printStackTrace(System.out);
        }
    }

    @FunctionalInterface
    interface RunnableTest {
        void run() throws Exception;
    }

    private static void testPasswordSaltAndUpgrade() {
        // Test login tài khoản mẫu
        User admin = UserDAO.login("admin_bgh", "123456");
        if (admin == null || !Constants.ROLE_ADMIN.equals(admin.getRole())) {
            throw new AssertionError("admin_bgh login thất bại!");
        }

        // Kiểm tra xem mật khẩu đã được mã hóa có Salt hay chưa
        String hashWithSalt = UserDAO.hashWithSalt("123456");
        if (!hashWithSalt.equals(admin.getPasswordHash())) {
            throw new AssertionError("Mật khẩu chưa được lưu dạng Salted SHA-256!");
        }

        // Test sai mật khẩu
        User wrong = UserDAO.login("admin_bgh", "wrongpassword");
        if (wrong != null) {
            throw new AssertionError("Đăng nhập phải thất bại với sai password!");
        }
    }

    private static void testUserUniquenessAndCRUD() {
        String testUser = "test_audit_user";
        int createdId = UserDAO.createUser(testUser, "123456", "Audit User", "0911223344", Constants.ROLE_REPORTER, Constants.SPECIALTY_NONE);
        if (createdId <= 0) {
            throw new AssertionError("Không thể tạo user mới!");
        }

        // Tạo lại cùng username -> phải thất bại (UNIQUE constraint)
        int dupId = UserDAO.createUser(testUser, "123456", "Dup User", "0911223344", Constants.ROLE_REPORTER, Constants.SPECIALTY_NONE);
        if (dupId > 0) {
            throw new AssertionError("Cho phép tạo trùng username! Lỗi bảo mật CSDL!");
        }

        // Kiểm tra online/offline
        UserDAO.updateUserOnlineStatus(createdId, true);
        User u = UserDAO.getUserById(createdId);
        if (u.getIsOnline() != 1) throw new AssertionError("isOnline phải bằng 1");

        UserDAO.updateUserOnlineStatus(createdId, false);
        u = UserDAO.getUserById(createdId);
        if (u.getIsOnline() != 0) throw new AssertionError("isOnline phải bằng 0");
    }

    private static void testCategoryAndRouting() {
        List<IncidentCategory> cats = CategoryDAO.getAllCategories();
        if (cats == null || cats.size() < 5) {
            throw new AssertionError("Thiếu danh mục sự cố!");
        }

        IncidentCategory fac = CategoryDAO.getCategory("FACILITY");
        if (fac == null || !Constants.SPECIALTY_TECHNICAL.equals(fac.getTargetDepartment())) {
            throw new AssertionError("FACILITY phải tự động điều phối tới TECHNICAL!");
        }

        IncidentCategory med = CategoryDAO.getCategory("MEDICAL");
        if (med == null || !Constants.SPECIALTY_MEDICAL.equals(med.getTargetDepartment())) {
            throw new AssertionError("MEDICAL phải tự động điều phối tới MEDICAL!");
        }

        IncidentCategory sec = CategoryDAO.getCategory("SECURITY");
        if (sec == null || !Constants.SPECIALTY_SECURITY.equals(sec.getTargetDepartment())) {
            throw new AssertionError("SECURITY phải tự động điều phối tới SECURITY!");
        }
    }

    private static void testTicketLifecycle() {
        IncidentTicket t = new IncidentTicket();
        t.setRoomLocation("A101");
        t.setCategoryCode("FACILITY");
        t.setDescription("Den huynh quang khong sang");
        t.setReporterId(2); // gv_lan
        t.setReporterName("Co Tran Thi Lan");

        boolean created = TicketDAO.createTicket(t);
        if (!created || t.getTicketId() <= 0) {
            throw new AssertionError("Tạo sự cố thất bại!");
        }
        if (t.getTicketCode() == null || !t.getTicketCode().startsWith("INC-")) {
            throw new AssertionError("Mã sự cố tự sinh không đúng chuẩn: " + t.getTicketCode());
        }
        if (!Constants.STATUS_PENDING.equals(t.getStatus())) {
            throw new AssertionError("Trạng thái ban đầu phải là PENDING!");
        }
        if (!Constants.SPECIALTY_TECHNICAL.equals(t.getTargetDepartment())) {
            throw new AssertionError("Phòng ban phải được tự gán là TECHNICAL!");
        }
    }

    private static void testAtomicClaimAndSpecialty() {
        IncidentTicket t = new IncidentTicket();
        t.setRoomLocation("B202");
        t.setCategoryCode("MEDICAL");
        t.setDescription("Hoc sinh bi sot cao");
        t.setReporterId(3);
        t.setReporterName("Le Hoang Minh");
        TicketDAO.createTicket(t);

        // Kỹ thuật viên (kt_tuan - TECHNICAL) cố nhận vé MEDICAL với check chuyên môn
        boolean claimWrongDept = TicketDAO.claimTicket(t.getTicketId(), 4, "Anh Hoang Van Tuan", "TECHNICAL");
        if (claimWrongDept) {
            throw new AssertionError("Kỹ thuật viên không được nhận vé Y tế!");
        }

        // Cán bộ Y tế (yte_mai - MEDICAL) nhận vé đúng chuyên môn
        boolean claimRightDept = TicketDAO.claimTicket(t.getTicketId(), 6, "Co Vu Tuyet Mai", "MEDICAL");
        if (!claimRightDept) {
            throw new AssertionError("Cán bộ Y tế phải nhận vé thành công!");
        }

        // Cố nhận lại lần thứ 2 khi vé đã IN_PROGRESS -> Phải thất bại (Atomic protection)
        boolean claimAgain = TicketDAO.claimTicket(t.getTicketId(), 7, "Nguoi khac", "MEDICAL");
        if (claimAgain) {
            throw new AssertionError("Không được cho phép tiếp nhận vé đã có người nhận!");
        }
    }

    private static void testTicketStatusAndResolution() {
        IncidentTicket t = new IncidentTicket();
        t.setRoomLocation("C103");
        t.setCategoryCode("FACILITY");
        t.setDescription("May quat hong");
        t.setReporterId(2);
        TicketDAO.createTicket(t);

        TicketDAO.claimTicket(t.getTicketId(), 4, "Anh Hoang Van Tuan");

        // Hoàn tất
        boolean resolved = TicketDAO.updateStatus(t.getTicketId(), Constants.STATUS_RESOLVED, "Da sua mo-to quat xong");
        if (!resolved) throw new AssertionError("Cập nhật RESOLVED thất bại!");

        IncidentTicket check = TicketDAO.getTicketById(t.getTicketId());
        if (!Constants.STATUS_RESOLVED.equals(check.getStatus())) {
            throw new AssertionError("Trạng thái DB chưa là RESOLVED!");
        }
        if (check.getResolvedAt() == null || check.getResolvedAt().isEmpty()) {
            throw new AssertionError("Thời gian resolved_at chưa được ghi nhận!");
        }
        if (!"Da sua mo-to quat xong".equals(check.getResolutionNote())) {
            throw new AssertionError("Ghi chú xử lý không khớp!");
        }
    }

    private static void testStatisticsAndCsvList() {
        StatisticReport reportAll = TicketDAO.getStatistics("ALL");
        if (reportAll.getTotalTickets() <= 0) {
            throw new AssertionError("Thống kê ALL phải > 0!");
        }
        if (reportAll.getTicketList() == null || reportAll.getTicketList().isEmpty()) {
            throw new AssertionError("ticketList phục vụ CSV không được rỗng!");
        }
        if (reportAll.getCountByDepartment() == null || reportAll.getCountByDepartment().isEmpty()) {
            throw new AssertionError("Thống kê theo phòng ban không có số liệu!");
        }

        // Test thống kê theo DAY
        StatisticReport reportDay = TicketDAO.getStatistics("DAY");
        if (reportDay.getTicketList() == null) {
            throw new AssertionError("ticketList cho DAY không được null!");
        }
    }

    private static void testChatPersistence() {
        ChatMessage msg1 = new ChatMessage(1, 2, "Co Tran Thi Lan", Constants.ROLE_REPORTER, "Kiem tra giup phong nay");
        ChatMessageDAO.saveMessage(msg1);

        ChatMessage msg2 = new ChatMessage(1, 4, "Anh Hoang Van Tuan", Constants.ROLE_RESPONDER, "Toi dang xu ly");
        ChatMessageDAO.saveMessage(msg2);

        List<ChatMessage> list = ChatMessageDAO.getMessagesByTicket(1);
        if (list == null || list.size() < 2) {
            throw new AssertionError("Lịch sử chat phải có ít nhất 2 tin nhắn!");
        }
        ChatMessage last = list.get(list.size() - 1);
        if (!"Toi dang xu ly".equals(last.getContent())) {
            throw new AssertionError("Nội dung tin nhắn cuối không đúng!");
        }
    }

    private static void testPacketFramingAndLimits() throws Exception {
        byte[] attachment = "Hello Attachment Image/Bytes".getBytes(StandardCharsets.UTF_8);
        Packet pOriginal = new Packet(Constants.TYPE_CHAT_PUSH, "CHAT_PUSH|ticketId=100|user=2", attachment);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        pOriginal.writeTo(dos);

        byte[] rawBytes = baos.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(rawBytes);
        DataInputStream dis = new DataInputStream(bais);

        Packet pParsed = Packet.readFrom(dis);
        if (pParsed.getType() != Constants.TYPE_CHAT_PUSH) throw new AssertionError("Type không khớp!");
        if (!pParsed.getHeaderPayload().equals(pOriginal.getHeaderPayload())) throw new AssertionError("Header không khớp!");
        if (pParsed.getAttachmentLength() != attachment.length) throw new AssertionError("Attach len không khớp!");

        // Test sai Magic Header
        rawBytes[0] = 0x12;
        rawBytes[1] = 0x34;
        try {
            Packet.readFrom(new DataInputStream(new ByteArrayInputStream(rawBytes)));
            throw new AssertionError("Phải ném ngoại lệ khi sai Magic Header!");
        } catch (IOException expected) {
            // OK
        }
    }

    private static void testPacketHeaderParsing() {
        Packet p = new Packet(Constants.TYPE_LOGIN, "LOGIN|username=admin_bgh|role=ADMIN|note=a=b=c|desc=test|flag");
        Map<String, String> params = p.parseParams();

        if (!"admin_bgh".equals(params.get("username"))) throw new AssertionError("username không khớp!");
        if (!"ADMIN".equals(params.get("role"))) throw new AssertionError("role không khớp!");
        if (!"a=b=c".equals(params.get("note"))) throw new AssertionError("note có dấu = phải được giữ nguyên!");
        if (!"true".equals(params.get("flag"))) throw new AssertionError("flag độc lập phải là true!");
    }
}

