# ĐỀ TÀI BÀI TẬP LỚN: LẬP TRÌNH MẠNG (NĂM HỌC 2026–2027)

## 1. Thông tin Chung
* **Tên đề tài:** Hệ thống Báo cáo và Điều phối Sự cố Trường THPT theo Thời gian thực  
  *(High School Real-time Incident Reporting & Dispatch System)*
* **Nhóm học phần:** Lập trình mạng (Network Programming)
* **Chủ đề đăng ký:** Chủ đề 3 – Ứng dụng mạng đa giao thức (Client/Server, Real-time Dispatch & Fault Tolerance)
* **Thời hạn nộp bài:** 23:59 – 31/10/2026

---

## 2. Đặt vấn đề & Mục tiêu Đề tài
Tại các trường trung học phổ thông, sự cố cơ sở vật chất (chập điện, hỏng máy chiếu, vỡ ống nước) hoặc vấn đề y tế, an ninh thường được báo cáo thủ công qua mạng xã hội (Zalo, Facebook) hoặc gọi điện trực tiếp. Điều này dẫn tới:
1. **Nhiễu và trôi thông tin:** Báo cáo lẫn lộn trong tin nhắn hành chính.
2. **Trùng lặp báo cáo (Duplicate Storm):** Nhiều lớp học cùng phản ánh một sự cố gây lãng phí tài nguyên và hoang mang.
3. **Điều phối thiếu dữ liệu:** Quản lý không biết nhân viên kỹ thuật nào đang rảnh để giao việc.
4. **Treo sự cố khi mất mạng:** Khi nhân viên nhận việc bị mất kết nối, sự cố bị bỏ quên không ai xử lý.

**Mục tiêu của hệ thống:**
Xây dựng một hệ thống mạng hoàn chỉnh bằng **Java Socket thuần túy (không dùng framework web trung gian)**, tích hợp giao diện JavaFX hiện đại, giải quyết triệt để các bài toán trên thông qua mạng đa giao thức (TCP + UDP Heartbeat + UDP Multicast).

---

## 3. Công nghệ Cốt lõi & Áp dụng Mạng
* **Kênh TCP Socket (Port 5000):**
  * Giao thức đóng khung nhị phân tùy biến (`0xCAFE` Magic Header + Length-Prefixed Framing).
  * Hỗ trợ truyền mảng byte hình ảnh hiện trường (Binary Attachment).
  * Đẩy thông báo hai chiều Server-Push theo thời gian thực.
* **Kênh UDP Unicast Heartbeat (Port 5001):**
  * Nhân sự gửi nhịp tim định kỳ 3 giây/lần.
  * Server giám sát và tự động kích hoạt Auto-Failover sau 10 giây mất kết nối.
* **Kênh UDP Multicast (230.0.0.1:9999):**
  * Ban Giám hiệu phát cảnh báo khẩn cấp (Cháy nổ, Di tản) đồng thời tới toàn bộ các lớp học.
* **Lưu trữ CSDL Cấp độ Doanh nghiệp:**
  * SQLite 3 với chế độ WAL (Write-Ahead Logging) và kết nối Connection Pooling.
  * Cơ chế khóa nguyên tử (Atomic Claim) triệt tiêu 100% hiện tượng Race Condition khi nhận ca.
* **Giao diện Người dùng:** JavaFX 17+ theo phong cách Dashboard phẳng, hiện đại.

---

## 4. Các Vai trò Người dùng (User Roles)
1. **Người báo cáo (Reporter - Giáo viên / Lớp trưởng):**
   * Đăng nhập tài khoản.
   * Chọn vị trí phòng học, loại sự cố (Điện, Nước, Thiết bị, Y tế, An ninh), mức độ khẩn cấp (1–5).
   * Đính kèm ảnh chụp hiện trường từ máy tính.
   * Theo dõi danh sách sự cố đã gửi và tiến độ xử lý theo thời gian thực.
2. **Nhân viên xử lý (Responder - Kỹ thuật / Y tế / Bảo vệ):**
   * Nhận thông báo Server-Push khi có sự cố mới đúng chuyên môn.
   * Xem ảnh chụp hiện trường, chấp nhận hoặc từ chối nhiệm vụ.
   * Cập nhật trạng thái xử lý (`IN_PROGRESS` -> `RESOLVED`) kèm ghi chú hoàn thành.
   * Tự động gửi UDP Heartbeat giữ phiên kết nối.
3. **Ban Giám hiệu / Quản trị viên (Admin - Hiệu trưởng / Quản lý):**
   * Giám sát toàn bộ sự cố toàn trường trên giao diện Dashboard.
   * Theo dõi trạng thái Online/Offline và tải công việc (`current_workload`) của từng nhân sự.
   * Kích hoạt phát cảnh báo khẩn cấp UDP Multicast toàn trường.

