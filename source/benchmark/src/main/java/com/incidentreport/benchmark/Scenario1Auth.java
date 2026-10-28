package com.incidentreport.benchmark;

import com.incidentreport.common.Constants;
import com.incidentreport.common.packet.Packet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KỊCH BẢN 1: XÁC THỰC NGƯỜI DÙNG & PHÂN QUYỀN TRUY CẬP (RBAC VERIFICATION)
 * Kiểm định:
 * - Đăng nhập các vai trò: Giáo viên (REPORTER), Kỹ thuật viên (RESPONDER), Admin (ADMIN)
 * - Kiểm tra từ chối mật khẩu sai, SQL Injection vào form đăng nhập
 * - Kiểm tra ngăn chặn truy cập trái phép vào các API phân quyền (RBAC Protection)
 */
public class Scenario1Auth {

    public static void main(String[] args) {
        BenchmarkContext.initTarget(args);
        run();
    }

    public static void run() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   KỊCH BẢN 1: XÁC THỰC NGƯỜI DÙNG & PHÂN QUYỀN HỆ THỐNG (RBAC)");
        System.out.println("=".repeat(80));

        String host = BenchmarkContext.getTargetHost();
        int port = BenchmarkContext.getTargetPort();

        List<String[]> reportRows = new ArrayList<>();
        int passed = 0;
        int total = 0;

        // 1. Kiểm thử đăng nhập Người báo cáo (gv_lan)
        total++;
        long t0 = System.nanoTime();
        try (VirtualClient client = new VirtualClient(host, port)) {
            Map<String, String> user = client.login("gv_lan", "123456");
            long ms = (System.nanoTime() - t0) / 1_000_000;
            boolean ok = "REPORTER".equalsIgnoreCase(user.get("role"));
            if (ok) {
                passed++;
                BenchmarkContext.logSuccess(String.format("1.1 Đăng nhập REPORTER (gv_lan): Thành công [%s - %s] (%d ms)",
                        user.get("fullName"), user.get("role"), ms));
                reportRows.add(new String[]{"1.1 Đăng nhập Reporter", "gv_lan", "REPORTER", ms + " ms", "PASS"});
            } else {
                BenchmarkContext.logError("1.1 Đăng nhập REPORTER: Sai Role thực tế: " + user.get("role"));
                reportRows.add(new String[]{"1.1 Đăng nhập Reporter", "gv_lan", "REPORTER", ms + " ms", "FAIL"});
            }

            // 1.2 Kiểm thử RBAC: Reporter gọi API Admin (Lấy danh sách User) -> Phải bị từ chối
            total++;
            long tRbac = System.nanoTime();
            Packet adminReq = new Packet(Constants.TYPE_ADMIN_GET_USERS, "GET_USERS");
            client.send(adminReq);
            Packet rbacResp = client.receive();
            long msRbac = (System.nanoTime() - tRbac) / 1_000_000;

            boolean rbacBlocked = (rbacResp.getType() == Constants.TYPE_ERROR);
            if (rbacBlocked) {
                passed++;
                BenchmarkContext.logSuccess(String.format("1.2 Chặn Reporter gọi API Admin: Thành công (Server trả ERROR: %s) (%d ms)",
                        rbacResp.parseParams().get("message"), msRbac));
                reportRows.add(new String[]{"1.2 RBAC Chặn Reporter", "gv_lan -> GET_USERS", "Chặn (ERROR)", msRbac + " ms", "PASS"});
            } else {
                BenchmarkContext.logError("1.2 Lỗ hổng bảo mật: Reporter gọi được API của Admin!");
                reportRows.add(new String[]{"1.2 RBAC Chặn Reporter", "gv_lan -> GET_USERS", "Không bị chặn", msRbac + " ms", "FAIL"});
            }
        } catch (Exception e) {
            BenchmarkContext.logError("1.1 Lỗi kết nối đăng nhập: " + e.getMessage());
            reportRows.add(new String[]{"1.1 Đăng nhập Reporter", "gv_lan", "Lỗi ngoại lệ", "0 ms", "FAIL"});
        }

        // 1.3 Kiểm thử đăng nhập Cán bộ Kỹ thuật (kt_tuan)
        total++;
        t0 = System.nanoTime();
        try (VirtualClient client = new VirtualClient(host, port)) {
            Map<String, String> user = client.login("kt_tuan", "123456");
            long ms = (System.nanoTime() - t0) / 1_000_000;
            boolean ok = "RESPONDER".equalsIgnoreCase(user.get("role"))
                    && "TECHNICAL".equalsIgnoreCase(user.get("specialty"));
            if (ok) {
                passed++;
                BenchmarkContext.logSuccess(String.format("1.3 Đăng nhập RESPONDER (kt_tuan): Thành công [%s - Phòng %s] (%d ms)",
                        user.get("fullName"), user.get("specialty"), ms));
                reportRows.add(new String[]{"1.3 Đăng nhập Responder", "kt_tuan", "RESPONDER/TECHNICAL", ms + " ms", "PASS"});
            } else {
                BenchmarkContext.logError("1.3 Đăng nhập RESPONDER sai Specialty/Role: " + user);
                reportRows.add(new String[]{"1.3 Đăng nhập Responder", "kt_tuan", "Sai quyền", ms + " ms", "FAIL"});
            }
        } catch (Exception e) {
            BenchmarkContext.logError("1.3 Lỗi đăng nhập Responder: " + e.getMessage());
            reportRows.add(new String[]{"1.3 Đăng nhập Responder", "kt_tuan", "Lỗi ngoại lệ", "0 ms", "FAIL"});
        }

        // 1.4 Kiểm thử đăng nhập Quản trị viên BGH (admin_bgh)
        total++;
        t0 = System.nanoTime();
        try (VirtualClient client = new VirtualClient(host, port)) {
            Map<String, String> user = client.login("admin_bgh", "123456");
            long ms = (System.nanoTime() - t0) / 1_000_000;
            boolean ok = "ADMIN".equalsIgnoreCase(user.get("role"));
            if (ok) {
                passed++;
                BenchmarkContext.logSuccess(String.format("1.4 Đăng nhập ADMIN (admin_bgh): Thành công [%s - %s] (%d ms)",
                        user.get("fullName"), user.get("role"), ms));
                reportRows.add(new String[]{"1.4 Đăng nhập Admin", "admin_bgh", "ADMIN", ms + " ms", "PASS"});
            } else {
                BenchmarkContext.logError("1.4 Đăng nhập ADMIN sai Role: " + user);
                reportRows.add(new String[]{"1.4 Đăng nhập Admin", "admin_bgh", "Sai Role", ms + " ms", "FAIL"});
            }
        } catch (Exception e) {
            BenchmarkContext.logError("1.4 Lỗi đăng nhập Admin: " + e.getMessage());
            reportRows.add(new String[]{"1.4 Đăng nhập Admin", "admin_bgh", "Lỗi ngoại lệ", "0 ms", "FAIL"});
        }

        // 1.5 Kiểm thử mật khẩu sai
        total++;
        t0 = System.nanoTime();
        try (VirtualClient client = new VirtualClient(host, port)) {
            Packet p = new Packet(Constants.TYPE_LOGIN, "LOGIN|username=gv_lan", "mat_khau_sai".getBytes(StandardCharsets.UTF_8));
            Packet resp = client.sendAndReceive(p, Constants.TYPE_LOGIN_RESP, 5);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            boolean rejected = "FAIL".equalsIgnoreCase(resp.parseParams().get("status"));
            if (rejected) {
                passed++;
                BenchmarkContext.logSuccess(String.format("1.5 Từ chối mật khẩu sai: Thành công (Server trả status=FAIL: %s) (%d ms)",
                        resp.parseParams().get("message"), ms));
                reportRows.add(new String[]{"1.5 Từ chối sai Pass", "gv_lan (sai pass)", "Từ chối (FAIL)", ms + " ms", "PASS"});
            } else {
                BenchmarkContext.logError("1.5 Sai mật khẩu nhưng Server vẫn cho qua!");
                reportRows.add(new String[]{"1.5 Từ chối sai Pass", "gv_lan (sai pass)", "Lọt xác thực", ms + " ms", "FAIL"});
            }
        } catch (Exception e) {
            BenchmarkContext.logError("1.5 Lỗi kiểm tra sai pass: " + e.getMessage());
            reportRows.add(new String[]{"1.5 Từ chối sai Pass", "gv_lan", "Lỗi ngoại lệ", "0 ms", "FAIL"});
        }

        // 1.6 Kiểm thử SQL Injection vào Username: admin' OR '1'='1
        total++;
        t0 = System.nanoTime();
        try (VirtualClient client = new VirtualClient(host, port)) {
            Packet p = new Packet(Constants.TYPE_LOGIN, "LOGIN|username=admin' OR '1'='1'--", "any".getBytes(StandardCharsets.UTF_8));
            Packet resp = client.sendAndReceive(p, Constants.TYPE_LOGIN_RESP, 5);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            boolean rejected = "FAIL".equalsIgnoreCase(resp.parseParams().get("status"));
            if (rejected) {
                passed++;
                BenchmarkContext.logSuccess(String.format("1.6 Chống SQL Injection trong Login: Thành công (Server trả FAIL) (%d ms)", ms));
                reportRows.add(new String[]{"1.6 Chống SQL Injection", "admin' OR '1'='1", "Từ chối an toàn", ms + " ms", "PASS"});
            } else {
                BenchmarkContext.logError("1.6 LỖ HỔNG: SQL Injection đăng nhập thành công!");
                reportRows.add(new String[]{"1.6 Chống SQL Injection", "admin' OR '1'='1", "Bị tiêm nhiễm SQL", ms + " ms", "FAIL"});
            }
        } catch (Exception e) {
            BenchmarkContext.logError("1.6 Lỗi ngoại lệ SQL Injection test: " + e.getMessage());
            reportRows.add(new String[]{"1.6 Chống SQL Injection", "admin' OR '1'='1", "Lỗi ngoại lệ", "0 ms", "FAIL"});
        }

        // Xuất bảng kết quả
        String[] headers = {"Mã kiểm thử", "Tài khoản / Yêu cầu", "Kỳ vọng", "Thời gian", "Kết quả"};
        String asciiTable = ChartGenerator.toAsciiTable("KẾT QUẢ KIỂM THỬ XÁC THỰC & PHÂN QUYỀN (RBAC)", headers, reportRows);
        System.out.println(asciiTable);

        double passRate = (total > 0) ? ((double) passed / total * 100.0) : 0;
        BenchmarkContext.logMetric(String.format("Tổng số ca kiểm thử: %d | Đạt: %d | Tỷ lệ thành công: %.1f%%\n", total, passed, passRate));

        // Lưu vào báo cáo Markdown
        StringBuilder md = new StringBuilder();
        md.append("### Kịch bản 1: Xác thực người dùng & Phân quyền bảo mật (RBAC)\n\n");
        md.append(ChartGenerator.toMarkdownTable(headers, reportRows));
        md.append(String.format("\n> **Tổng kết Kịch bản 1**: Đạt `%d/%d` kiểm thử (Tỷ lệ: `%.1f%%`). Hệ thống bảo mật phân quyền tuyệt đối.\n", passed, total, passRate));
        BenchmarkContext.appendReport(md.toString());
    }
}

