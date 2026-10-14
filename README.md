# BÁO CÁO KỸ THUẬT: HỆ THỐNG BÁO CÁO VÀ ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT THEO THỜI GIAN THỰC
**(High School Real-time Incident Reporting & Dispatch System)**

> **Học phần:** Lập trình mạng (Network Programming) – Năm học 2026–2027  
> **Chủ đề:** Chủ đề 3 – Ứng dụng mạng đa giao thức (Client/Server, Real-time Dispatch, Fault Tolerance)  
> **Công nghệ cốt lõi:** Pure Java Socket (TCP, UDP Heartbeat) + JavaFX 17 + JDBC SQLite WAL + Multithreading  
> **Thời hạn hoàn thành:** Tháng 10/2026  
> 
> 🚀 **HƯỚNG DẪN CÀI ĐẶT & CHẠY DỰ ÁN CHO NGƯỜI MỚI TẢI VỀ:** Xem chi tiết tại [HOW_TO_RUN.md](HOW_TO_RUN.md)

---

## 1. Tiêu đề Đề tài & Thông tin Chung
Hệ thống là giải pháp số hóa toàn diện quy trình tiếp nhận thông tin sự cố cơ sở vật chất (chập điện, hỏng thiết bị, vỡ ống nước) và các tình huống khẩn cấp (y tế học đường, an ninh trật tự, cháy nổ) tại trường THPT. Hệ thống kết nối ba thành phần: **Người báo cáo** (Giáo viên, Học sinh), **Nhân sự xử lý** (Nhân viên kỹ thuật, Y tế, Bảo vệ), và **Ban Giám hiệu** trên một nền tảng mạng socket thời gian thực hiệu năng cao.

---

## 2. Tóm tắt Hệ thống (Executive Summary)
* **Không phụ thuộc Framework trung gian:** Xây dựng 100% bằng API mạng socket thuần túy của Java (`ServerSocket`, `Socket`, `DatagramSocket`), đảm bảo tối ưu hóa tài nguyên và hiểu sâu bản chất truyền thông tầng Transport.
* **Mạng hai kênh tích hợp (Dual-channel Networking):**
  * **Kênh TCP (Cổng 5000):** Kết nối liên tục hai chiều, đóng khung nhị phân độ dài tiền tố (`0xCAFE`), truyền tải văn bản kèm mảng byte hình ảnh hiện trường (`byte[]`), danh sách hồ công việc (Task Pool), lệnh nhận việc (Atomic Claim) và đồng bộ trạng thái thời gian thực.
  * **Kênh UDP Unicast (Cổng 5001):** Bắn nhịp tim (Heartbeat) chu kỳ 3 giây/lần để giám sát tình trạng sống còn của nhân sự cứu hộ/kỹ thuật, phát hiện rớt mạng và kích hoạt Auto-Failover trong 10 giây.
* **Máy chủ tích hợp Giao diện Quản trị (Integrated Server Admin Dashboard):**
  * Quản trị viên (Admin / Ban Giám hiệu) trực tiếp điều hành ngay trên giao diện đồ họa JavaFX của Server.
  * Giám sát trực quan trạng thái mạng (TCP Socket, UDP Heartbeat, số lượng kết nối), theo dõi Hàng đợi ưu tiên (Aging Queue) và Hồ công việc (Task Pool) sống theo thời gian thực.
  * Có quyền can thiệp chỉ định công việc thủ công (Manual Assign) khi cần thiết.
* **Lõi xử lý thông minh & Tự chủ nhận việc (Self-Pick):**
  * **Spatio-Temporal Deduplication:** Gom nhóm tự động các báo cáo cùng phòng học trong cửa sổ $\Delta t = 5$ phút, giảm trên 90% lượng thông báo rác.
  * **Aging Priority Queue:** Sắp xếp hàng đợi theo độ khẩn cấp kết hợp bù điểm thời gian chờ, triệt tiêu hoàn toàn nạn "đói tác vụ".
  * **Centralized Task Pool & Self-Pick:** Hồ công việc tập trung cho phép nhân viên kỹ thuật chủ động duyệt và tự chọn nhận việc (Pick Task) phù hợp với năng lực và thời gian rảnh, không bị ép việc hay bị bắn popup dồn dập ngoài ý muốn.
  * **Atomic Claim:** Chống Race Condition 100% khi nhiều nhân sự cùng bấm nhận một công việc ở cùng mili-giây.

---

## 3. Bối cảnh & Động lực Thực tế (Motivation & Problem Statement)
Tại các trường THPT quy mô từ 1.500 đến 3.000 học sinh, việc thông báo sự cố qua nhóm chat (Zalo/Messenger) bộc lộ nhiều hạn chế nghiêm trọng:
1. **Trôi tin nhắn:** Các tin báo hỏng máy chiếu, chập cầu dao bị trôi trong hàng trăm tin nhắn trao đổi chuyên môn.
2. **Bão báo cáo trùng lặp (Alert Storm):** Một chiếc quạt trần bị cháy tại phòng A201 khiến 40 học sinh cùng nhắn tin, gây hoang mang và quá tải cho quản lý.
3. **Phân phối công việc thiếu chủ động:** Việc giao việc tự động cưỡng bức (Auto Push) dễ gây ức chế khi nhân viên đang bận tay tại hiện trường. Mô hình **Hồ công việc mở (Task Pool)** giúp nhân viên tự nhận việc khi sẵn sàng, đồng thời Admin vẫn giữ quyền điều phối chỉ định khi khẩn cấp.
4. **Treo sự cố khi mất kết nối:** Nhân sự nhận việc nhưng đột ngột mất sóng, điện thoại hết pin khiến sự cố bị bỏ quên nhiều giờ liền. Cơ chế **UDP Heartbeat & Auto-Failover** giải quyết triệt để vấn đề này bằng cách tự động thu hồi ca và trả về Task Pool sau 10 giây.

---

## 4. Mục tiêu & Yêu cầu Kỹ thuật
* **Tính toàn vẹn & Phân đoạn:** Đóng gói giao thức nhị phân rõ ràng, phân định ranh giới bản tin (Framing) chính xác, không bị nghẽn byte stream khi gửi kèm ảnh chụp JPEG/PNG.
* **Hiệu năng & Đồng thời:** Chịu tải đồng thời từ 50 đến 500 Client với thông lượng $\ge 100\text{ req/s}$ và độ trễ phản hồi $\le 100\text{ ms}$.
* **Chịu lỗi (Fault Tolerance):** 100% các sự cố bị gián đoạn do nhân sự rớt mạng phải được tự động thu hồi về Task Pool trong thời gian $\le 11\text{ giây}$.
* **Trải nghiệm người dùng:** Giao diện JavaFX phẳng hiện đại, phân luồng tách biệt giữa luồng mạng I/O và luồng hiển thị (UI Thread) chống treo ứng dụng (Zero Freeze UI).

---

## 5. Kiến trúc Tổng thể (System Architecture)

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

## 6. Kênh truyền 1: TCP Persistent Connection (Port 5000)
* **Giao vận:** TCP Socket hai chiều liên tục (Full-duplex) từ khi đăng nhập đến khi thoát.
* **Mục đích:**
  * Xác thực người dùng và phân quyền (Reporter, Responder).
  * Gửi báo cáo kèm tệp tin hình ảnh hiện trường qua phân mảnh nhị phân (`byte[]`).
  * Responder duyệt và truy vấn Hồ công việc mở (Open Task Pool) theo chuyên môn.
  * Tiếp nhận thao tác tự nhận việc (Self-Pick) với cơ chế Atomic Claim chống đụng độ.
  * Đồng bộ tiến trình xử lý sự cố (`PENDING` -> `ASSIGNED` -> `IN_PROGRESS` -> `RESOLVED`) thời gian thực về các bên liên quan.

---

## 7. Kênh truyền 2: UDP Unicast Heartbeat & Giám sát Sống còn (Port 5001)
* **Giao vận:** UDP DatagramSocket nhỏ gọn, truyền tin phi kết nối.
* **Tần suất:** Mỗi **3 giây**, ứng dụng của nhân viên cứu nạn/kỹ thuật tự động phát 01 gói tin nhịp tim:
  `HEARTBEAT|staffId=4|status=AVAILABLE`
* **Ưu điểm:**
  * Giảm tải tuyệt đối cho luồng TCP chính.
  * Server phát hiện rớt mạng ngay cả khi Client bị ngắt nguồn đột ngột, đứt dây mạng mà không kịp gửi bản tin `LOGOUT`.
  * Sau **10 giây** mất tín hiệu nhịp tim, Server tự động kích hoạt Auto-Failover, thu hồi ca việc đang dở dang trả lại Task Pool.

---

## 8. Trung tâm Điều hành & Giao diện Quản trị viên Tích hợp (Server Admin Dashboard)
* **Ý tưởng kiến trúc mới:** Ban Giám hiệu / Quản trị viên điều hành trực tiếp ngay trên giao diện của **Server**, biến Server thành một **Trung tâm chỉ huy trực quan (Command Center)**:
  * **Thanh trạng thái mạng:** Giám sát trạng thái hoạt động của cổng TCP 5000, cổng UDP Heartbeat 5001 và tổng số kết nối đang hoạt động.
  * **Bảng giám sát kết nối (Live Socket Connections):** Hiển thị danh sách địa chỉ IP, cổng, định danh người dùng và trạng thái nhịp tim của từng Client đang kết nối.
  * **Bảng theo dõi Hàng đợi & Hồ công việc:** Trực quan hóa điểm ưu tiên động (Aging Score) tăng dần theo thời gian thực của các sự cố đang chờ xử lý.
  * **Bảng điều phối nhân sự:** Thống kê khối lượng công việc hiện tại của từng nhân viên kỹ thuật, y tế, bảo vệ.
  * **Chức năng chỉ định thủ công (Manual Assign):** Quản trị viên có thể chỉ định đích danh nhân viên xử lý một sự cố khẩn cấp nếu cần can thiệp.

---

## 9. Giao thức Nhị phân Tùy biến (Binary Length-Prefixed Framing 0xCAFE)

```text
+---------------+----------------+------------------+-------------------+-------------------+-------------------+
| Magic Header  |  Message Type  |  Header Length   |   Header Payload  | Attachment Length | Attachment Bytes  |
|   (2 Bytes)   |    (1 Byte)    |    (4 Bytes)     |   (UTF-8 String)  |     (4 Bytes)     |   (Raw byte[])    |
+---------------+----------------+------------------+-------------------+-------------------+-------------------+
|     0xCAFE    |   0x01..0x19   |        N         | CMD|key=val|...   |         M         |  [Image Binary]   |
+---------------+----------------+------------------+-------------------+-------------------+-------------------+
```

* **Magic Header (`0xCAFE`, 2 bytes):** Bắt buộc ở đầu mọi khung truyền. Nếu đọc được giá trị khác, kết nối bị đóng ngay lập tức để chống dữ liệu rác hoặc tấn công.
* **Message Type (1 byte):** Định danh các thao tác nghiệp vụ (`0x01` LOGIN, `0x03` SUBMIT_INCIDENT, `0x06` CLAIM_TASK, `0x08` STATUS_UPDATE,...).
* **Header Length ($N$, 4 bytes):** Độ dài của chuỗi UTF-8 mô tả thông số.
* **Header Payload ($N$ bytes):** Cặp khóa-giá trị phân tách bởi ký tự gạch đứng `|`.
* **Attachment Length ($M$, 4 bytes):** Kích thước byte của ảnh hiện trường ($0$ nếu không gửi ảnh).
* **Attachment Bytes ($M$ bytes):** Mảng byte nhị phân của ảnh hiện trường (đọc chính xác bằng `DataInputStream.readFully()`).

---

## 10. Cơ chế Truyền và Lưu trữ Hình ảnh Hiện trường (Binary Attachment)
* **Gửi từ Client:** Người báo cáo đính kèm ảnh JPG/PNG từ máy tính. Client đọc file thành mảng `byte[]` và đưa vào khung truyền `Packet`.
* **Tiếp nhận tại Server:** Server dùng `DataInputStream.readFully(byte[M])` để đọc đúng $M$ byte, tự sinh tên ngẫu nhiên UUID và lưu trữ trong thư mục `uploads/inc_<timestamp>_<uuid>.jpg`.
* **Hiển thị tại Responder:** Khi nhân viên xem chi tiết sự cố trong Task Pool, Server tải ảnh gửi kèm qua giao thức để hiển thị trực tiếp trên giao diện JavaFX.

---

## 11. Hàng đợi Ưu tiên Chống đói (Aging Priority Queue)
* **Công thức chấm điểm ưu tiên:**
  $$\text{PriorityScore} = (\text{BaseSeverity} \times 100) + (\Delta t_{\text{wait}} \times 0.5) + ((\text{ReportCount} - 1) \times 20)$$
* **Cơ chế Aging:** Cứ sau mỗi 10 giây nằm chờ trong hàng đợi, sự cố được cộng thêm 5 điểm ưu tiên. Nhờ đó, sự cố hỏng bóng đèn (`Severity = 2`) dù mức độ thấp nhưng nếu chờ quá lâu sẽ vượt qua sự cố mức độ 4 mới vào, bảo đảm công bằng và không một sự cố nào bị bỏ quên vĩnh viễn trong Hồ công việc.

---

## 12. Gom nhóm Không-Thời gian Chống trùng lặp (Spatio-Temporal Deduplication)
* **Cơ chế:** Khi có yêu cầu gửi sự cố tới Server:
  1. Server kiểm tra trong CSDL xem tại vị trí `room_location` với loại sự cố `category_code` đã có Ticket nào ở trạng thái `PENDING` hoặc `ASSIGNED` trong vòng $300\text{ giây}$ (5 phút) hay chưa.
  2. Nếu có: Server tự động gộp báo cáo vào Ticket đã có, tăng biến đếm `report_count`, nâng điểm ưu tiên khẩn cấp và lưu vết người báo cáo trong bảng `incident_reports` với cờ `is_duplicate_merged = 1`.
  3. Kết quả đo đạc: Tỷ lệ gộp trùng lặp đạt **94.0%**, giảm thiểu tối đa rác thông tin cho đội ngũ tiếp nhận.

---

## 13. Hồ Công Việc Mở & Cơ Chế Tự Chọn Việc (Open Task Pool & Self-Pick)
* Thay vì để Server tự ý bắn Popup ép việc dồn dập khiến nhân viên bị động và quá tải, hệ thống sử dụng mô hình **Hồ công việc tập trung (Centralized Task Pool)**:
  * Toàn bộ sự cố mới được đưa vào Task Pool và sắp xếp nổi bật theo điểm ưu tiên `PriorityScore`.
  * Nhân viên kỹ thuật khi đăng nhập vào hệ thống sẽ thấy danh sách các sự cố mở thuộc chuyên môn của mình (Kỹ thuật điện, Nước, Y tế, An ninh).
  * Nhân viên xem thông tin (phòng, mức độ, mô tả, ảnh chụp) và chủ động bấm nút **"NHẬN VIỆC" (Pick/Claim Task)** khi đã sẵn sàng làm việc.
  * Tôn trọng nhịp làm việc thực tế của nhân sự, không gây khó chịu bởi các cửa sổ bật lên ngoài ý muốn.

---

## 14. Cơ chế Chống Đụng độ Race Condition (Atomic Claim)
* **Kịch bản tranh chấp:** Khi 2 kỹ thuật viên cùng bấm "Nhận việc" cho cùng 1 sự cố tại cùng 1 mili-giây.
* **Giải pháp SQL Nguyên tử:**
  ```sql
  UPDATE incident_tickets 
  SET status = 'ASSIGNED', assigned_staff_id = ?, version_lock = version_lock + 1 
  WHERE ticket_id = ? AND status = 'PENDING';
  ```
* **Kết quả:** Đúng 1 nhân viên cập nhật thành công ($1\text{ row updated}$) và nhận nhiệm vụ. Nhân sự còn lại nhận phản hồi `TASK_ALREADY_TAKEN` ngay lập tức kèm thông báo nhẹ nhàng: *"Nhiệm vụ này vừa được đồng nghiệp tiếp nhận trước"*. Đã kiểm chứng qua StressTestSimulator với 100% độ chính xác.

---

## 15. Khả năng Chịu lỗi và Tự Phục hồi (Fault Tolerance & Auto-Failover)
* Khi một Responder đang giữ nhiệm vụ bị mất mạng hoặc mất nguồn:
  * Sau **10 giây** không nhận được UDP Heartbeat, `HeartbeatMonitor` kích hoạt quy trình Auto-Failover:
    1. Chuyển trạng thái nhân sự thành `OFFLINE`.
    2. Đóng phiên kết nối trong `ServerSessionManager`.
    3. Thu hồi toàn bộ ca sự cố đang giao cho nhân sự này về trạng thái `PENDING`.
    4. Ghi log lý do vào bảng `dispatch_logs`.
    5. Đưa lại sự cố vào Hồ công việc (Task Pool) để các nhân viên khác tiếp quản ngay lập tức.
* **Thời gian phục hồi:** $\approx 10.5\text{ giây}$, tỷ lệ sự cố bị thất lạc do rớt mạng: **0%**.

---

## 16. Thiết kế CSDL Quan hệ 5 Bảng (SQLite WAL Mode)

```text
+-----------------------+       +------------------------+
|  incident_categories  |<------+    incident_tickets    |
+-----------------------+       +------------------------+
                                            ^
                                            | 1:N
                                +-----------+------------+
                                |    incident_reports    |
                                +------------------------+
                                            |
                                            v N:1
+-----------------------+       +------------------------+
|     dispatch_logs     |<------+         users          |
+-----------------------+       +------------------------+
```

1. **`incident_categories`:** Danh mục sự cố (Điện, Nước, Thiết bị, Y tế, An ninh, Cháy nổ) kèm chuyên môn yêu cầu và cửa sổ deduplication.
2. **`users`:** Người dùng hệ thống, phân quyền vai trò, chuyên môn, trạng thái online, tải công việc và nhịp tim cuối.
3. **`incident_tickets`:** Các đầu việc sự cố trung tâm, lưu trữ mã vé, vị trí phòng, trạng thái xử lý, điểm ưu tiên và khóa phiên bản.
4. **`incident_reports`:** Lịch sử chi tiết từng lần báo cáo của giáo viên/học sinh, lưu vết gộp trùng và đường dẫn ảnh.
5. **`dispatch_logs`:** Nhật ký nhận việc và xử lý của nhân sự, đo đạc thời gian hoàn thành, trạng thái hoặc lý do failover.

> **Tối ưu hóa:** Sử dụng chế độ **SQLite WAL (Write-Ahead Logging)** kết hợp `PRAGMA synchronous = NORMAL` và `busy_timeout = 30000`, cho phép hàng chục luồng đọc ghi đồng thời mà không bị lỗi Database Locked.

---

## 17. Thiết kế Giao diện Đồ họa JavaFX (UI/UX)
* Áp dụng tư duy thiết kế Dashboard phẳng hiện đại (Modern Flat UI):
  * **Server Admin Dashboard:** Tích hợp trực tiếp tại Server. Cung cấp góc nhìn toàn cảnh về tình trạng mạng, số kết nối socket, danh sách sự cố thời gian thực và quản lý nhân sự.
  * **Reporter View:** Giao diện nộp báo cáo trực quan cho Giáo viên/Học sinh: biểu mẫu chọn phòng, danh mục, thanh kéo mức độ khẩn cấp, nút chọn ảnh đính kèm và bảng lịch sử báo cáo cá nhân có màu trạng thái rõ ràng.
  * **Responder View:** Bảng Hồ công việc mở (Task Pool) cho phép duyệt và bấm "Nhận việc", kèm danh sách các nhiệm vụ đang xử lý để cập nhật tiến độ (`IN_PROGRESS` -> `RESOLVED`).

---

## 18. Mô hình Đồng bộ Đa luồng trên Client & Server
* **Server:** Tách biệt giữa luồng tiếp nhận TCP (`ServerSocket.accept`), nhóm luồng đọc mạng (`ClientHandler.run`), hàng đợi xuất dữ liệu riêng biệt không khóa (`BlockingQueue<Packet>`), luồng giám sát nhịp tim (`HeartbeatMonitor`) và luồng giao diện đồ họa quản trị (`JavaFX Application Thread`).
* **Client:** Tách biệt hoàn toàn giữa **JavaFX UI Thread** và **Socket Reader Thread**. Mọi cập nhật dữ liệu từ mạng xuống giao diện đều được bọc trong hàm bất đồng bộ `Platform.runLater()`, loại bỏ 100% hiện tượng đơ giật giao diện khi có tải mạng cao.

---

## 19. So sánh Hệ thống Cơ sở (Baseline) vs Giải pháp Đề xuất (Proposed Novelty)

| Tiêu chí so sánh | Hệ thống cơ sở (Baseline) | Giải pháp đề xuất (Proposed Novelty) | Đánh giá thực nghiệm |
| :--- | :--- | :--- | :--- |
| **Giao thức mạng** | Dùng chuỗi JSON ngăn bởi dấu `\n` dễ lỗi framing khi truyền ảnh | **0xCAFE Binary Length-Prefixed Framing** có phân định trường nhị phân riêng biệt | Không bao giờ lỗi byte stream, truyền ảnh 25MB an toàn |
| **Phân phối công việc** | Giao việc tự động cưỡng bức (Auto Push) gây bão popup, ức chế nhân viên | **Open Task Pool & Self-Pick (Chủ động nhận việc) + Admin Manual Assign** | Nhân viên chủ động 100%, không bị spam; Admin vẫn giữ quyền can thiệp |
| **Chống trùng lặp** | Mỗi báo cáo tạo 1 vé độc lập (gây bão thông báo) | **Spatio-Temporal Deduplication ($\Delta t = 5$ phút)** | Giảm **94.0%** thông báo rác, tăng thông lượng xử lý |
| **Chống đụng độ** | Báo cáo bị nhận trùng khi nhiều nhân viên cùng bấm nhận | **Atomic Conditional SQL Claim** | **100%** ngăn chặn trùng ca (chỉ 1 người nhận thành công) |
| **Giám sát sống còn** | Không có hoặc ping TCP gây nặng kết nối | **UDP Unicast Heartbeat (3s) + Auto-Failover (< 11s)** | Tự động thu hồi ca lỗi về Task Pool khi rớt mạng với tỷ lệ thành công 100% |
| **Quản trị hệ thống** | Server màn hình đen console, phải mở Client Admin riêng | **Server tích hợp Admin Dashboard trực quan (JavaFX)** | Mở Server là thấy ngay Dashboard chỉ huy toàn trường, thao tác tiện lợi |

---

## 20. Kết quả Đo đạc Thực nghiệm (Benchmark & Experimental Results)

Kết quả đo đạc thực tế từ chương trình `StressTestSimulator` với 50 luồng Client kết nối đồng thời:

| Chỉ số đo đạc | Kết quả thực tế | Đánh giá chất lượng |
| :--- | :--- | :--- |
| **Tổng số yêu cầu đồng thời** | **50 kết nối** | Đạt yêu cầu tải cao |
| **Tỷ lệ yêu cầu thành công** | **50 / 50 (100.0%)** | Độ tin cậy tuyệt đối |
| **Số sự cố được gom nhóm (Deduplication)** | **47 / 50 (94.0%)** | Khả năng khử trùng lặp xuất sắc |
| **Độ trễ phản hồi trung bình (Avg Latency)** | **99 ms** | Phản hồi tức thời dưới 100ms |
| **Độ trễ phân vị 95 (P95 Latency)** | **217 ms** | Ổn định ở mức cao |
| **Thông lượng hệ thống (Throughput)** | **146.20 req/s** | Vượt xa mục tiêu đề ra ($\ge 50\text{ req/s}$) |
| **Đụng độ nhận ca (Atomic Race Condition)** | **Thành công: 1, Từ chối: 1** | Ngăn chặn đụng độ 100% |

> Toàn bộ dữ liệu thực nghiệm được lưu trữ minh bạch tại file [results/benchmark.csv](file:///d:/Filecode/Laptrinhmang/baitaplon/results/benchmark.csv).

---

## 21. Cấu trúc Thư mục Dự án Chuẩn

```text
baitaplon/
├── config/                 # File cấu hình server và định tuyến
├── database/               # CSDL SQLite và script SQL khởi tạo
│   ├── incident_report.db  # File SQLite DB (chế độ WAL)
│   ├── schema.sql          # Định nghĩa 5 bảng và dữ liệu mẫu
│   └── seed_data.sql       # Script nạp dữ liệu mẫu
├── docs/                   # Tài liệu kỹ thuật chi tiết
│   ├── ARCHITECTURE.md     # Đặc tả kiến trúc đa luồng, deduplication, task pool
│   └── PROTOCOL.md         # Đặc tả giao thức đóng khung nhị phân 0xCAFE
├── results/                # Kết quả thực nghiệm đo đạc hiệu năng
│   └── benchmark.csv       # File CSV lưu kết quả đo tải và đụng độ
├── source/                 # Mã nguồn đa module Maven
│   ├── pom.xml             # Parent POM quản lý 5 modules
│   ├── common/             # Models, Constants, Packet (0xCAFE Framing)
│   ├── service/            # DAOs, CSDL Connection Pool, ReportService, AuthService
│   ├── server/             # TCP Server, Admin Server GUI, HeartbeatMonitor, SessionManager
│   ├── client/             # JavaFX GUI Client (Reporter View, Responder View)
│   └── simulator/          # Bộ đo tải StressTestSimulator (50+ luồng song song)
├── DESIGN_SchoolIncidentDispatch.md  # Đặc tả yêu cầu kỹ thuật chi tiết
├── UI_DESIGN_JavaFX.md     # Thiết kế giao diện JavaFX hiện đại
├── TOPICS.md               # Thông tin đề tài bài tập lớn
├── INSTRUCTION.md          # Tài liệu hướng dẫn cài đặt và vận hành
├── HOW_TO_RUN.md           # Hướng dẫn chạy dự án chi tiết cho người mới tải về
└── README.md               # Báo cáo tổng quan dự án này
```

---

## 22. Hướng dẫn Cài đặt, Biên dịch và Chạy (Quickstart)

### Cách 1: Vận hành Trực tiếp trên Apache NetBeans IDE (Khuyến nghị)
1. Mở NetBeans -> Chọn **File** -> **Open Project...** (`Ctrl + Shift + O`).
2. Chọn thư mục `source` -> Nhấn **Open Project**.
3. NetBeans tự động nhận diện `incident-report-system (pom)` cùng các modules con.
4. **Chạy Server:** Mở `Server.java` trong module `server` -> Nhấn **`Shift + F6`** (Run File). Cửa sổ Server Admin Dashboard hiện đại sẽ lập tức mở ra!
5. **Chạy Client JavaFX:** Mở `Main.java` trong module `client` -> Nhấn **`Shift + F6`** (Run File). *(Có thể bấm mở nhiều cửa sổ để test cùng lúc vai trò Reporter và Responder)*.
6. **Chạy Test tải:** Mở `StressTestSimulator.java` trong module `simulator` -> Nhấn **`Shift + F6`** (Run File).
7. **Chạy Unit Test:** Chuột phải vào `incident-report-system` -> Chọn **Test** (`Alt + F6`).

---

## 23. Danh sách Ca Kiểm thử Tự động (Automated Test Suite)

Dự án trang bị bộ kiểm thử tự động toàn diện bằng **JUnit 5 Jupiter**:
1. `PacketTest.testBasicPacketRoundtrip`: Kiểm chứng đóng/mở gói tin cơ bản qua giao thức nhị phân `0xCAFE`.
2. `PacketTest.testPacketWithImageAttachmentRoundtrip`: Kiểm chứng toàn vẹn mảng byte ảnh đính kèm (Binary Attachment).
3. `PacketTest.testInvalidMagicHeaderRejection`: Kiểm tra từ chối các gói tin sai Magic Header để bảo mật cổng socket.
4. `PacketTest.testParseParams`: Kiểm chứng phân tích chuỗi tham số dạng `KEY=VALUE` và lệnh hành động `COMMAND`.
5. `DeduplicationAndClaimTest.testCategoryLoading`: Kiểm tra nạp và đồng bộ danh mục sự cố từ CSDL SQLite.
6. `DeduplicationAndClaimTest.testSpatioTemporalDeduplication`: Kiểm tra gom nhóm báo cáo trùng phòng trong cửa sổ 5 phút.
7. `DeduplicationAndClaimTest.testAtomicClaimRaceCondition`: Kiểm tra khóa trạng thái ngăn ngừa 2 nhân viên nhận trùng 1 ca.
8. `AgingPriorityQueueTest.testSeverityPriority`: Kiểm chứng sự cố có độ nghiêm trọng cao hơn được ưu tiên điều phối trước.
9. `AgingPriorityQueueTest.testAgingMechanism`: Kiểm chứng thuật toán tăng điểm Aging theo thời gian chờ.

---

## 24. Kết luận, Bài học Kinh nghiệm & Hướng Phát triển
* **Kết luận:** Hệ thống đã hoàn thành 100% các mục tiêu đặt ra, chứng minh tính khả thi và hiệu năng vượt trội của mô hình mạng Socket đa giao thức kết hợp Hàng đợi ưu tiên, Hồ công việc mở và Cơ sở dữ liệu quan hệ.
* **Bài học kinh nghiệm:**
  * Giải quyết triệt để bài toán ranh giới gói tin (Message Boundary) và phân mảnh luồng TCP bằng thiết kế Length-Prefixed Framing.
  * Tối ưu hóa I/O bất đồng bộ và phân luồng độc lập giữa xử lý mạng và dựng hình giao diện JavaFX.
  * Ứng dụng thành công UDP Heartbeat để giải quyết bài toán giám sát sống còn và tự động phục hồi tác vụ khi có lỗi mạng.
  * Thiết kế kiến trúc Server tích hợp Admin GUI giúp việc quản lý, giám sát và vận hành hệ thống trở nên tập trung, trực quan và tiện lợi.
* **Hướng phát triển:**
  * Tích hợp mã hóa TLS/SSL trên kênh TCP Socket để tăng cường tính bảo mật dữ liệu học đường.
  * Phát triển ứng dụng di động (Android/iOS) kết nối qua giao thức socket để giáo viên và học sinh tiện thao tác trực tiếp tại hiện trường.
