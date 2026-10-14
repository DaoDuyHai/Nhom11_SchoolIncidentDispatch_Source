# KIẾN TRÚC HỆ THỐNG (SYSTEM ARCHITECTURE SPECIFICATION)
**Hệ thống Báo cáo và Điều phối Sự cố Trường THPT (School Incident Dispatch System)**

---

## 1. Sơ đồ Kiến trúc Tổng thể (Overall Architecture)

```text
+-----------------------------------+       +-----------------------------------+
|      REPORTER CLIENT (JavaFX)     |       |      RESPONDER CLIENT (JavaFX)    |
|   (Giáo viên CN / Cán bộ lớp)     |       |    (Kỹ thuật / Y tế / Bảo vệ)     |
+-----------------+-----------------+       +-----------------+-----------------+
                  |                                           |         |
                  | (1) TCP Socket:                           |         | (3) UDP Unicast:
                  |     - Login & Session Auth                |         |     Heartbeat (3s)
                  |     - Submit Incident + Image             |         |     (Cổng 5001)
                  |     - Real-time Progress Tracking         |         |
                  |                                           |         v
                  |               (2) TCP Server-Push:        |    +-------------+
                  |                   - Dispatch New Task     |    |  Heartbeat  |
                  |                   - Image Attachment Sync |    |   Monitor   |
                  |                                           |    +------+------+
                  v                                           v           | (Timeout > 10s)
+-------------------------------------------------------------------+     |
|                       CENTRAL DISPATCH SERVER                     |     v
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
|  |  Dispatch Worker Threads (Specialty Matching & Min-Workload)|  |
|  +-------------------------------------------------------------+  |
+---------------------------------+---------------------------------+
                                  |
            (4) JDBC Connection   | (5) UDP Multicast Broadcast (Port 9999)
            (WAL Mode + Pool)     |     Emergency Alert (Cháy nổ/Di tản)
                                  v
+-------------------------------------------------------------------+
|                    SQLite 3 EMBEDDED DATABASE                     |
|  - incident_categories  - users              - incident_tickets   |
|  - incident_reports     - dispatch_logs      - multicast_alerts   |
+-------------------------------------------------------------------+
```

---

## 2. Mô hình Xử lý Đa luồng (Concurrency & Threading Model)

### 2.1. Phía Central Server
1. **TCP Connection Pool:** Sử dụng `ThreadPoolExecutor` (cấu hình 100 worker threads) tiếp nhận các kết nối Client qua `ServerSocket.accept()`.
2. **Per-Session Sender Worker:** Mỗi `ClientHandler` sở hữu một `LinkedBlockingQueue<Packet>` riêng biệt và 1 luồng ghi (`senderThread`). Khi Server cần gửi dữ liệu, gói tin chỉ được đưa vào hàng đợi không khóa, đảm bảo Server không bao giờ bị nghẽn (non-blocking) nếu một Client bị chậm mạng.
3. **Multi-Session Management:** `ServerSessionManager` ánh xạ `userId -> Set<ClientHandler>` bằng cấu trúc dữ liệu `ConcurrentHashMap`, cho phép 1 người dùng mở nhiều kết nối cùng lúc mà không gây ngắt kết nối lẫn nhau.
4. **Dispatch Worker Thread:** Một luồng chạy ngầm liên tục theo dõi `AgingPriorityQueue.dequeue()`, thực hiện thuật toán chấm điểm và chọn nhân viên tối ưu nhất để Push nhiệm vụ.
5. **UDP Heartbeat Listener Thread:** Lắng nghe trên cổng UDP 5001, ghi nhận timestamp nhịp tim cuối cùng và chạy chu kỳ quét timeout 1s/lần.

### 2.2. Phía JavaFX Client
1. **JavaFX Application Thread:** Đảm nhiệm toàn bộ việc dựng hình và tương tác giao diện người dùng. Không bao giờ thực hiện các lệnh Socket I/O trực tiếp trên luồng này.
2. **Network Reader Thread:** Chạy vòng lặp `while (running) { Packet p = Packet.readFrom(in); }` trong luồng Daemon. Khi có dữ liệu trả về từ Server, bản tin được chuyển giao an toàn cho giao diện qua:
   ```java
   Platform.runLater(() -> handleServerResponse(p));
   ```
3. **Heartbeat Sender Thread:** Luồng hẹn giờ định kỳ 3 giây gửi 1 gói tin UDP Datagram tới Server.
4. **Multicast Listener Thread:** Luồng riêng biệt tham gia nhóm `230.0.0.1:9999` để đón bắt các bản tin báo động khẩn cấp mà không can thiệp vào luồng TCP.

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
* **Vấn đề:** Sự cố nghiêm trọng cao (`Severity = 5`) liên tục xuất hiện sẽ làm các sự cố mức độ thấp (`Severity = 2`) bị bỏ quên vô hạn (Starvation).
* **Công thức tính điểm ưu tiên động:**
  $$\text{PriorityScore} = (\text{BaseSeverity} \times 100) + (\Delta t_{\text{wait}} \times \alpha) + ((\text{ReportCount} - 1) \times \beta)$$
  * Trong đó:
    * $\text{BaseSeverity} \in [1, 5]$: Trọng số cơ bản theo loại sự cố.
    * $\Delta t_{\text{wait}}$: Thời gian đã chờ trong hàng đợi (tính bằng giây).
    * $\alpha = 0.5$: Hệ số Aging (cứ mỗi 10 giây chờ được cộng thêm 5 điểm).
    * $\beta = 20.0$: Trọng số gom nhóm (mỗi người báo thêm được cộng 20 điểm).
* **Hiệu quả:** Sự cố mức độ 2 nếu phải chờ sau vài phút sẽ có điểm vượt qua sự cố mức độ 4 mới vào hàng đợi, triệt tiêu hoàn toàn hiện tượng đói tác vụ.

### 3.3. Điều phối Thông minh Cân bằng Tải (Least-Workload Smart Dispatch)
* Khi lấy 1 sự cố ra khỏi Hàng đợi, `DispatchWorker` tìm kiếm tập các nhân sự thỏa mãn:
  1. `role = 'RESPONDER'`
  2. `is_online = 1`
  3. `availability_status = 'AVAILABLE'`
  4. Chuyên môn phù hợp: `user.specialty == category.required_specialty` hoặc `category.required_specialty == 'ALL'`.
* **Tiêu chí lựa chọn:** Chọn nhân sự có tải công việc hiện tại nhỏ nhất:
  $$\text{TargetStaff} = \arg\min_{u \in \text{Candidates}} (u.\text{current\_workload})$$
* Nếu không có nhân viên nào trực tuyến, sự cố được đưa lại vào Hàng đợi và tiếp tục kiểm tra theo chu kỳ.

### 3.4. Chống Đụng độ Nhận ca (Atomic Claim & Race Condition Prevention)
* **Tình huống:** Hai nhân viên kỹ thuật cùng nhìn thấy 1 thông báo sự cố và đồng thời ấn nút "Nhận nhiệm vụ" tại cùng một mili-giây.
* **Cơ chế khóa nguyên tử:** Sử dụng truy vấn SQL nguyên tử kết hợp kiểm tra trạng thái điều kiện:
  ```sql
  UPDATE incident_tickets 
  SET status = 'ASSIGNED', assigned_staff_id = ?, version_lock = version_lock + 1 
  WHERE ticket_id = ? AND status = 'PENDING';
  ```
* **Kết quả:** SQLite đảm bảo tính nguyên tử (Atomicity); đúng 1 câu lệnh cập nhật thành công ($rowsAffected = 1$), câu lệnh còn lại trả về $0$. Nhân sự thứ hai lập tức nhận bản tin `TASK_ALREADY_TAKEN`.

---

## 4. Cơ chế Chịu lỗi & Tự phục hồi (Fault Tolerance & Auto-Failover)

```text
[Responder Client] ---(Mất mạng/Tắt nguồn)---> X Không gửi được UDP Heartbeat
                                                      |
                                                      v
                                        Server phát hiện Δt > 10s
                                                      |
                                                      +--> Đánh dấu OFFLINE
                                                      +--> Đóng phiên TCP cũ
                                                      +--> Ghi nhận nhật ký sự cố
                                                      +--> Chuyển Ticket về PENDING
                                                      +--> Đẩy lại vào Aging Priority Queue
                                                      +--> DispatchWorker giao ngay cho nhân viên rảnh khác!
```
* **Chỉ số kiểm chứng:** Thời gian phục hồi tự động (Failover Recovery Time) $\approx 10.5\text{ giây}$, đảm bảo 0% sự cố bị treo khi nhân sự gặp sự cố mất kết nối mạng.

