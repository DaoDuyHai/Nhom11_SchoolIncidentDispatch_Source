package com.incidentreport.server;

import com.incidentreport.service.DatabaseConnection;
import com.incidentreport.service.InitDatabase;

import java.io.File;

/**
 * ============================================================================
 * MÁY CHỦ KIỂM THỬ ĐỘC LẬP (SIDS TEST & BENCHMARK SERVER)
 * ============================================================================
 * - Sử dụng riêng biệt cơ sở dữ liệu: database/test_incident_report.db
 * - Đảm bảo cơ sở dữ liệu gốc (database/incident_report.db) hoàn toàn sạch sẽ,
 *   nguyên vẹn 100% để phục vụ báo cáo và bảo vệ đồ án trước hội đồng.
 * - Có thể reset hoặc bơm tải hàng nghìn vé sự cố kiểm thử mà không lo ảnh hưởng.
 */
public class TestServer {

    public static void main(String[] args) {
        System.out.println("============================================================================");
        System.out.println("   🛡️ SIDS TEST SERVER - MÁY CHỦ KIỂM THỬ & ĐO LƯỜNG HIỆU NĂNG             ");
        System.out.println("   Kiến trúc: Cô lập hoàn toàn cơ sở dữ liệu kiểm thử                      ");
        System.out.println("============================================================================");

        File testDb = InitDatabase.findFile("database/test_incident_report.db");
        DatabaseConnection.setCustomDbUrl("jdbc:sqlite:" + testDb.getAbsolutePath());

        System.out.println("🔒 CSDL Test đang kích hoạt: " + testDb.getAbsolutePath());
        System.out.println("✅ CSDL gốc (incident_report.db) ĐƯỢC BẢO VỆ NGUYÊN VẸN, KHÔNG BỊ GHI ĐÈ!\n");

        // Đảm bảo test database đã có đầy đủ bảng và 50 tài khoản mẫu
        InitDatabase.init(false);

        // Khởi động server
        Server.main(args);
    }
}
