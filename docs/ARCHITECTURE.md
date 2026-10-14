# KIẾN TRÚC HỆ THỐNG (SYSTEM ARCHITECTURE SPECIFICATION)
### HỆ THỐNG BÁO CÁO VÀ ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT THEO THỜI GIAN THỰC
*(High School Real-time Incident Reporting & Dispatch System)*

---

## 1. Sơ đồ Kiến trúc Tổng thể (Overall Architecture)

```text
+-----------------------------------+       +-----------------------------------+
|      REPORTER CLIENT (JavaFX)     |       |      RESPONDER CLIENT (JavaFX)    |
|   (Giáo viên CN / Cán bộ lớp)     |       |    (Kỹ thuật / Y tế / Bảo vệ)     |
+-----------------+-----------------+       +-----------------+-----------------+
                  |                                           |         |
                  | (1) TCP Socket:                           |         | (2) UDP Unicast:
                  |     - Login & Session Auth                |         |     Heartbeat (3s)
                  |     - Submit Incident + Image             |         |     (Cổng 5001)
                  |     - Real-time Progress Tracking         |         |
                  |                                           |         v
                  |               (1) TCP Socket:             |    +-------------+
                  |                   - View Open Task Pool   |    |  Heartbeat  |
                  |                   - Self-Pick Task (Claim)|    |   Monitor   |
                  |                   - Update Progress       |    +------+------+
                  v                                           v           | (Timeout > 10s)
+-------------------------------------------------------------------+     |
|              CENTRAL DISPATCH SERVER & ADMIN DASHBOARD            |     v
|  +-------------------------------------------------------------+  |  +---------+
|  | Multi-Session Manager (ConcurrentHashMap<userId, Set<Conn>>) |  |  |  Auto-  |
|  +-------------------------------------------------------------+  |  | Failover|
|  | Spatio-Temporal Deduplicator (Spatial: Room, Temporal: 5min)|  |  | Engine  |
|  +-------------------------------------------------------------+  |  +----+----+
|                                 |                                 |       |
|                                 v                                 |       |
|  +-------------------------------------------------------------+  |       |
|  |     Aging PriorityBlockingQueue (Starvation-free Dispatch)  |<-+-------+
|  +-------------------------------------------------------------+  |
|                                 |                                 |
|                                 v                                 |
|  +-------------------------------------------------------------+  |
|  | Open Task Pool & Atomic Claim Manager (Race Condition Free) |  |
|  +-------------------------------------------------------------+  |
|  | [GUI] Live Server Admin Console (JavaFX Embedded Dashboard) |  |
|  +-------------------------------------------------------------+  |
+---------------------------------+---------------------------------+
                                  |
            (3) JDBC Connection   | 
            (WAL Mode + Pool)     | 
                                  v
+-------------------------------------------------------------------+
|                    SQLite 3 EMBEDDED DATABASE                     |
|  - incident_categories  - users              - incident_tickets   |
|  - incident_reports     - dispatch_logs                           |
+-------------------------------------------------------------------+
```

---

## 2. Mô hình Xử lý Đa luồng (Concurrency & Threading Model)

### 2.1. Phía Central Server
1. **TCP Connection Pool:** Sử dụng `ThreadPoolExecutor` (cấu hình 100 worker threads) tiếp nhận các kết nối Client qua `ServerSocket.accept()`.
2. **Per-Session Sender Worker:** Mỗi `ClientHandler` sở hữu một `LinkedBlockingQueue<Packet>` riêng biệt và 1 luồng ghi (`senderThread`). Khi Server cần gửi dữ liệu, gói tin chỉ được đưa vào hàng đợi không khóa, đảm bảo Server không bao giờ bị nghẽn (non-blocking) nếu một Client bị chậm mạng.
3. **Multi-Session Management:** `ServerSessionManager` ánh xạ `userId -> Set<ClientHandler>` bằng cấu trúc dữ liệu `ConcurrentHashMap`, cho phép 1 người dùng mở nhiều kết nối cùng lúc mà không gây ngắt kết nối lẫn nhau.
4. **UDP Heartbeat Listener Thread:** Lắng nghe trên cổng UDP 5001, ghi nhận timestamp nhịp tim cuối cùng và chạy chu kỳ quét timeout 1s/lần để phát hiện rớt mạng.
5. **Server GUI (JavaFX Application Thread):** Tích hợp giao diện quản trị trực tiếp trên Server. Khi có sự kiện mạng (kết nối mới, ngắt kết nối, sự cố mới, nhận việc), dữ liệu được cập nhật tức thời lên giao diện qua `Platform.runLater()`.

### 2.2. Phía JavaFX Client
1. **JavaFX Application Thread:** Đảm nhiệm toàn bộ việc dựng hình và tương tác giao diện người dùng. Không bao giờ thực hiện các lệnh Socket I/O trực tiếp trên luồng này.
2. **Network Reader Thread:** Chạy vòng lặp `while (running) { Packet p = Packet.readFrom(in); }` trong luồng Daemon. Khi có dữ liệu trả về từ Server, bản tin được chuyển giao an toàn cho giao diện qua:
   ```java
   Platform.runLater(() -> handleServerResponse(p));
   ```
3. **Heartbeat Sender Thread:** Luồng hẹn giờ định kỳ 3 giây gửi 1 gói tin UDP Datagram tới Server để duy trì trạng thái `ONLINE` / `AVAILABLE`.

---

## 3. Các Giải pháp Kỹ thuật Cốt lõi (Core Algorithms)

### 3.1. Gom nhóm Không-Thời gian Chống trùng lặp (Spatio-Temporal Deduplication)
* **Vấn đề:** Khi có sự cố hỏng hóc hoặc chập cháy tại 1 phòng học (ví dụ A201), hàng chục giáo viên và học sinh đều dùng ứng dụng để gửi báo cáo, gây tràn ngập hệ thống và phân tán nhân sự.
* **Thuật toán:**
  * Khóa không gian: `room_location` + `category_code`.
  * Cửa sổ thời gian: $\Delta t \le 300\text{ giây}$ (5 phút) tính từ thời điểm báo cáo đầu tiên.
  * Nếu tìm thấy Ticket đang hoạt động (`PENDING` hoặc `ASSIGNED`):
    1. Không tạo Ticket mới.
    2. Tăng số đếm người báo `report_count = report_count + 1`.
    3. Cập nhật mức độ nghiêm trọng `base_severity = MAX(base_severity, new_severity)`.
    4. Thêm bản ghi chi tiết vào bảng `incident_reports` với cờ `is_duplicate_merged = 1` để lưu vết đầy đủ ai đã báo cáo.
    5. Trả về phản hồi `SUBMIT_RESP` kèm cờ `isMerged = 1`.

### 3.2. Hàng đợi Ưu tiên Chống đói (Aging Priority Queue)
* **Vấn đề:** Sự cố nghiêm trọng cao (`Severity = 5`) liên tục xuất hiện sẽ làm các sự cố mức độ thấp (`Severity = 2`) bị bỏ quên vô hạn trong Hồ công việc (Starvation).
* **Công thức tính điểm ưu tiên động:**
  $$\text{PriorityScore} = (\text{BaseSeverity} \times 100) + (\Delta t_{\text{wait}} \times \alpha) + ((\text{ReportCount} - 1) \times \beta)$$
  * Trong đó:
    * $\text{BaseSeverity} \in [1, 5]$: Trọng số cơ bản theo loại sự cố.
    * $\Delta t_{\text{wait}}$: Thời gian đã chờ trong hàng đợi (tính bằng giây).
    * $\alpha = 0.5$: Hệ số Aging (cứ mỗi 10 giây chờ được cộng thêm 5 điểm).
    * $\beta = 20.0$: Trọng số gom nhóm (mỗi người báo thêm được cộng 20 điểm).
* **Hiệu quả:** Sự cố mức độ 2 nếu phải chờ sau vài phút sẽ có điểm tăng dần và nổi bật lên đầu danh sách Hồ công việc, triệt tiêu hoàn toàn hiện tượng đói tác vụ.

### 3.3. Hồ Công Việc Mở & Cơ Chế Tự Chủ Nhận Việc (Open Task Pool & Self-Pick)
* **Mô hình:**
  * Thay vì cơ chế cưỡng bức (Auto Push) khiến nhân viên bị dồn dập popup và mất quyền kiểm soát thời gian làm việc, hệ thống lưu trữ toàn bộ các sự cố mở trong một **Hồ công việc tập trung (Open Task Pool)**.
  * Nhân viên kỹ thuật, y tế, bảo vệ chủ động truy cập Task Pool để duyệt danh sách các việc đang chờ xử lý (`PENDING`) thuộc chuyên môn của mình.
  * Nhân viên xem xét chi tiết vị trí, mức độ, ảnh hiện trường và bấm **"Nhận việc" (Claim Task)** khi họ sẵn sàng.
  * **Quản trị viên can thiệp (Manual Assign):** Ban Giám hiệu trên Server Dashboard vẫn có quyền chỉ định trực tiếp một nhân viên cụ thể tiếp nhận sự cố nếu xảy ra tình huống khẩn cấp.

### 3.4. Cơ chế Chống Race Condition (Atomic Claim)
* **Vấn đề:** Khi 2 kỹ thuật viên cùng nhìn thấy 1 công việc trong Task Pool và cùng bấm "Nhận việc" tại cùng 1 mili-giây.
* **Giải pháp SQL Nguyên tử:**
  ```sql
  UPDATE incident_tickets 
  SET status = 'ASSIGNED', assigned_staff_id = ?, version_lock = version_lock + 1 
  WHERE ticket_id = ? AND status = 'PENDING';
  ```
* **Kết quả:** Đúng 1 nhân viên cập nhật thành công ($1\text{ row updated}$) và nhận nhiệm vụ. Nhân sự còn lại nhận phản hồi `TASK_ALREADY_TAKEN` ngay lập tức kèm thông báo: *"Nhiệm vụ này vừa được đồng nghiệp tiếp nhận trước"*.

### 3.5. Giám sát Sống còn & Tự Phục hồi (Fault Tolerance & Auto-Failover)
* **Kênh giám sát:** UDP Unicast Heartbeat độc lập (Cổng 5001), chu kỳ 3 giây/lần.
* **Thu hồi ca khi mất kết nối:**
  * Nếu nhân viên đang giữ một ca sự cố (`ASSIGNED` hoặc `IN_PROGRESS`) nhưng bị mất mạng, hết pin hoặc đóng ứng dụng đột ngột:
  * Sau **10 giây** không nhận được nhịp tim, Server kích hoạt quy trình Auto-Failover:
    1. Đánh dấu nhân viên `OFFLINE`.
    2. Thu hồi ca sự cố về trạng thái `PENDING`.
    3. Trả lại sự cố vào Hồ công việc (Open Task Pool) và thông báo tới các nhân sự khác để tiếp quản kịp thời.
    4. Ghi vết vào `dispatch_logs` với trạng thái `HEARTBEAT_LOST`.
