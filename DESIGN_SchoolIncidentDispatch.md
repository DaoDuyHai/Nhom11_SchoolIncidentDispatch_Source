# THIẾT KẾ KỸ THUẬT: HỆ THỐNG BÁO CÁO VÀ ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT
### (High School Real-time Incident Reporting & Dispatch System)

> **Học phần:** Lập trình mạng (Năm học 2026–2027)  
> **Chủ đề:** Chủ đề 3 – Ứng dụng mạng (Client/Server & Real-time Dispatch)  
> **Công nghệ cốt lõi:** Pure Java Socket (TCP, UDP Heartbeat) + JavaFX + JDBC + Multithreading  
> **Hạn nộp bài:** 23:59 – 31/10/2026

---

## 1. Tổng quan Bài toán & Động lực (Problem & Motivation)

### 1.1. Thực trạng (Problem)
Tại các trường THPT hiện nay, quy trình báo cáo và xử lý sự cố (hỏng thiết bị phòng học, chập điện, vỡ ống nước, học sinh gặp vấn đề y tế, mất an ninh...) vẫn diễn ra thủ công qua tin nhắn nhóm (Zalo/Messenger) hoặc gọi điện trực tiếp:
* **Trôi tin nhắn & nhiễu thông tin:** Báo cáo sự cố dễ bị trôi giữa các tin nhắn hành chính và trao đổi chuyên môn.
* **Trùng lặp báo cáo (Duplicate Requests):** Nhiều học sinh/giáo viên cùng báo cáo một sự cố tại cùng một phòng học tạo ra hàng chục thông báo trùng lặp gây hoang mang.
* **Phân phối công việc thụ động & cưỡng bức:** Việc máy chủ tự ý bắn popup giao việc dồn dập khiến nhân viên kỹ thuật bị ức chế, mất tập trung khi đang sửa chữa tại hiện trường. Nhân viên cần một cơ chế chủ động: xem Hồ công việc mở và tự nhận việc khi sẵn sàng.
* **Thiếu theo dõi thời gian thực:** Người báo cáo không biết sự cố đã có ai tiếp nhận hay chưa; nếu nhân viên nhận việc bị mất kết nối giữa chừng, sự cố bị treo không ai xử lý.

### 1.2. Mục tiêu hệ thống (Objectives)
Xây dựng hệ thống mạng theo mô hình **Client–Server kết hợp Hàng đợi ưu tiên (Priority Queue) và Hồ công việc mở (Open Task Pool)** bằng **Java Socket thuần túy**, đáp ứng:
1. Tiếp nhận báo cáo sự cố kèm hình ảnh hiện trường và đồng bộ trạng thái xử lý theo thời gian thực (TCP Socket Framing `0xCAFE`).
2. Tự động gom nhóm các báo cáo trùng lặp tại cùng một vị trí phòng học trong cửa sổ thời gian 5 phút (Spatio-Temporal Deduplication).
3. Hàng đợi ưu tiên động kết hợp cơ chế bù điểm chờ (Aging Priority Queue), chống đói tác vụ 100%.
4. Hồ công việc tập trung (Task Pool) cho phép nhân sự cứu hộ/kỹ thuật chủ động duyệt và tự chọn nhận việc (Self-Pick) với cơ chế Atomic Claim chống Race Condition.
5. Giám sát sống còn của nhân sự qua kênh UDP Heartbeat (3s/lần) và tự động thu hồi nhiệm vụ dở dang về Task Pool (Auto-Failover) trong 10 giây khi mất kết nối.
6. Máy chủ tích hợp trực tiếp **Giao diện Quản trị viên (Server Admin Dashboard)** giúp Ban Giám hiệu điều hành và giám sát toàn trường ngay tại máy chủ.

---

## 2. Kiến trúc Hệ thống (System Architecture)

```text
+-----------------------+                             +------------------------+
|   Reporter Clients    |                             |   Responder Clients    |
| (Giáo viên / Học sinh)|                             | (Kỹ thuật/Y tế/Bảo vệ) |
+-----------+-----------+                             +-----------+------------+
            |                                                     ^     |
            | (1) TCP: Login / Submit Incident + Image            |     | (2) UDP Unicast:
            |          Real-time Status Tracking                  |     |     Heartbeat (3s/lần)
            v                                                     |     v
+-----------+-----------------------------------------------------+-----+------+
|                         API / NETWORK COMMUNICATION LAYER                    |
|                      (TCP ServerSocket 5000 | UDP DatagramSocket 5001)       |
+---------------------------------------+--------------------------------------+
                                        |
                                        v
+------------------------------------------------------------------------------+
|                   CENTRAL DISPATCH SERVER & ADMIN DASHBOARD                  |
|  - Multi-Session Manager (ConcurrentHashMap<Integer, Set<ClientHandler>>)    |
|  - Spatio-Temporal Deduplication Module (Gom nhóm theo Room + Category)      |
|  - Synchronization Lock Manager (Chống Race Condition khi nhận việc)         |
|  - [GUI] Live Embedded Admin Dashboard (Giám sát socket, aging queue)        |
+-------------------+-----------------------------------+----------------------+
                    |                                   |
                    v                                   v
     +--------------+---------------+     +-------------+--------------+
     | Aging PriorityBlockingQueue  |     |     Heartbeat Monitor      |
     | (Sắp xếp: Severity + Aging)  |     |  (Phát hiện Timeout/Fault) |
     +--------------+---------------+     +-------------+--------------+
                    |                                   | (Auto-Failover 10s)
                    v                                   |
     +--------------------------------------------+     |
     |       Open Task Pool & Self-Pick Manager   |<----+
     |      (Nhân viên chủ động tự nhận việc)     |
     +--------------------------------------------+
                    |
                    v (JDBC Connection Pool - WAL Mode)
+------------------------------------------------------------------------------+
|                         SQLITE 3 EMBEDDED DATABASE                           |
|  1. incident_categories   2. users               3. incident_tickets         |
|  4. incident_reports      5. dispatch_logs       6. multicast_alerts         |
|  7. chat_messages                                                            |
+------------------------------------------------------------------------------+
```

---

## 3. Các Phân Hệ Chức Năng Chính

### 3.1. Phân hệ Báo cáo Sự cố (Reporter Subsystem)
* Dành cho Giáo viên và Học sinh trên Client.
* Chọn vị trí phòng học (A101, A201, LAB_CHEM...), phân loại danh mục, mức độ khẩn cấp (1–5).
* Đính kèm hình ảnh hiện trường từ máy tính, tự động phân mảnh nhị phân (`byte[]`) truyền qua kênh TCP.
* Bảng theo dõi thời gian thực: Hiển thị ngay trạng thái khi sự cố được gộp trùng, có người tiếp nhận hoặc đã xử lý xong.

### 3.2. Phân hệ Hồ công việc & Tự nhận việc (Responder Subsystem - Self-Pick)
* Dành cho Kỹ thuật viên điện/nước, Nhân viên y tế và Bảo vệ trường.
* **Hồ công việc mở (Open Task Pool):** Duyệt danh sách các việc đang chờ xử lý (`PENDING`) thuộc chuyên môn hoặc toàn trường, sắp xếp nổi bật theo điểm ưu tiên `PriorityScore`.
* **Tự chọn nhận việc (Claim Task):** Nhân viên chủ động bấm nút "Nhận việc" khi sẵn sàng. Cơ chế Atomic Claim chống đụng độ nếu 2 người cùng bấm 1 lúc.
* **Cập nhật tiến trình:** Cập nhật trạng thái sang `IN_PROGRESS` (Đang xử lý) và `RESOLVED` (Hoàn thành) kèm ghi chú nghiệm thu.

### 3.3. Phân hệ Giám sát Quản trị trên Server (Server Admin Dashboard)
* Tích hợp trực tiếp trên Server: Ban Giám hiệu theo dõi trực quan tình hình toàn trường.
* Giám sát kết nối mạng: Danh sách IP, cổng, định danh người dùng và nhịp tim sống của từng máy đang nối vào Server.
* Theo dõi Hàng đợi ưu tiên (Aging Queue): Trực quan hóa điểm số sự cố tăng dần theo thời gian thực.
* Quyền chỉ định thủ công (Manual Assign): Can thiệp giao việc trực tiếp cho nhân viên cụ thể nếu có yêu cầu đột xuất.

### 3.4. Phân hệ Giám sát Sống còn (Fault Tolerance Engine)
* Lắng nghe gói tin UDP Heartbeat (chu kỳ 3 giây) từ các máy Responder.
* Nếu mất tín hiệu quá 10 giây, tự động kích hoạt Auto-Failover: chuyển nhân viên sang `OFFLINE` và thu hồi ca việc đang dở dang trả lại Task Pool cho người khác tiếp quản.

### 3.5. Phân hệ Trao Đổi & Chat Thời Gian Thực (Real-time Chat Subsystem)
* **Mô hình Kênh đôi linh hoạt (Dual-Channel Chat):**
  * **Chat theo Sự cố (Incident Ticket Chat):** Phòng trao đổi trực tiếp giữa Người báo cáo (Client), Kỹ thuật viên tiếp nhận (Responder) và Ban Giám Hiệu (Admin) để cập nhật hiện trường, thông báo thời gian tới phòng, chỉ đạo và nghiệm thu xử lý.
  * **Kênh Trao đổi Toàn trường (General Dispatch Channel):** Cho phép mọi bên trao đổi thông tin, thông báo nhanh và điều phối liên khoa phòng.
* **Giao thức đóng khung nhị phân (Binary Framing):** Sử dụng các mã bản tin TCP `TYPE_CHAT_SEND (0x0C)`, `TYPE_CHAT_PUSH (0x1C)`, `TYPE_CHAT_HISTORY (0x0D)`, `TYPE_CHAT_HISTORY_RESP (0x1D)` truyền tải an toàn nội dung text đa dòng UTF-8 qua `Attachment Bytes`.
* **Lưu trữ CSDL SQLite:** Bảng `chat_messages` lưu vết lịch sử tin nhắn đa luồng với khóa ngoại liên kết `ticket_id` và `sender_id`.