# THIẾT KẾ KỸ THUẬT: HỆ THỐNG BÁO CÁO VÀ ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT
**(High School Real-time Incident Reporting & Dispatch System)**

> **Học phần:** Lập trình mạng (Năm học 2026–2027)  
> **Chủ đề:** Chủ đề 3 – Ứng dụng mạng (Client/Server & Real-time Dispatch)  
> **Công nghệ cốt lõi:** Pure Java Socket (TCP, UDP, Multicast) + JavaFX + JDBC + Multithreading  
> **Hạn nộp bài:** 23:59 – 31/10/2026

---

## 1. Tổng quan Bài toán & Động lực (Problem & Motivation)

### 1.1. Thực trạng (Problem)
Tại các trường THPT hiện nay, quy trình báo cáo và xử lý sự cố (hỏng thiết bị phòng học, chập điện, vỡ ống nước, học sinh gặp vấn đề y tế, mất an ninh...) vẫn diễn ra thủ công qua tin nhắn nhóm (Zalo/Messenger) hoặc gọi điện trực tiếp:
* **Trôi tin nhắn & nhiễu thông tin:** Báo cáo sự cố dễ bị trôi giữa các tin nhắn hành chính.
* **Trùng lặp báo cáo (Duplicate Requests):** Nhiều học sinh/giáo viên cùng báo cáo một sự cố tại cùng một phòng học tạo ra hàng chục thông báo trùng lặp.
* **Điều phối mù (Blind Dispatch):** Ban giám hiệu/Quản lý không nắm được trạng thái kết nối thực tế (Online/Offline, Rảnh/Bận) và số lượng công việc đang gánh (`Workload`) của nhân viên kỹ thuật, y tế, bảo vệ để phân công tối ưu.
* **Thiếu theo dõi thời gian thực:** Người báo cáo không biết sự cố đã có ai tiếp nhận hay chưa; nếu nhân viên nhận việc bị mất kết nối giữa chừng, sự cố bị treo không ai xử lý.

### 1.2. Mục tiêu hệ thống (Objectives)
Xây dựng hệ thống mạng theo mô hình **Client–Server kết hợp Hàng đợi ưu tiên (Priority Queue) và Worker Pool** bằng **Java Socket thuần túy**, đáp ứng:
1. Tiếp nhận báo cáo sự cố kèm hình ảnh hiện trường và đồng bộ trạng thái xử lý theo thời gian thực (Real-time Server Push).
2. Tự động gom nhóm các báo cáo trùng lặp tại cùng một vị trí phòng học trong cửa sổ thời gian xác định.
3. Tự động điều phối sự cố theo mức độ khẩn cấp và cân bằng tải công việc (`Least-Workload Dispatch`) giữa các nhân sự đúng chuyên môn.
4. Giám sát kết nối của nhân sự qua UDP Heartbeat và tự động chuyển giao nhiệm vụ (Auto-Failover) khi mất kết nối.
5. Phát cảnh báo khẩn cấp toàn trường tức thời qua UDP Multicast.

---

## 2. Kiến trúc Hệ thống (System Architecture)

~~~text
+-----------------------+                             +------------------------+
|   Reporter Clients    |                             |   Responder Clients    |
| (Giáo viên / Học sinh)|                             | (Kỹ thuật/Y tế/Bảo vệ) |
+-----------+-----------+                             +-----------+------------+
            |                                                     ^     |
            | (1) TCP: Login / Submit Incident + Image            |     | (3) UDP Unicast:
            | (5) UDP Multicast: Nhận cảnh báo khẩn cấp           |     |     Heartbeat (3s/lần)
            v                                                     |     v
+-----------+-----------------------------------------------------+-----+------+
|                         API / NETWORK COMMUNICATION LAYER                    |
|              (TCP ServerSocket | UDP DatagramSocket | MulticastSocket)       |
+---------------------------------------+--------------------------------------+
                                        |
                                        v
+------------------------------------------------------------------------------+
|                            CENTRAL DISPATCH SERVER                           |
|  - Session Manager (ConcurrentHashMap<Integer, ClientHandler>)               |
|  - Spatio-Temporal Deduplication Module (Gom nhóm theo Room + Category)      |
|  - Synchronization Lock Manager (Chống Race Condition khi nhận việc)         |
+-------------------+-----------------------------------+----------------------+
                    |                                   |
                    v                                   v
     +--------------+---------------+     +-------------+--------------+
     | Aging PriorityBlockingQueue  |     |     Heartbeat Monitor      |
     | (Sắp xếp: Severity + Aging)  |     |  (Phát hiện Timeout/Fault) |
     +--------------+---------------+     +-------------+--------------+
                    |                                   |
                    v                                   v
     +--------------+---------------+     +-------------+--------------+
     |   Dispatch Worker Threads    |     |    Auto-Failover Engine    |
     | (Chọn Specialty + Min Load)  |     | (Thu hồi & Điều phối lại)  |
     +--------------+---------------+     +-------------+--------------+
                    \                                   /
                     \                                 /
                      v                               v
                   +-------------------------------------+
                   |   Relational Database (6 Tables)    |
                   +-------------------------------------+
~~~

---

## 3. Thiết kế Giao tiếp Mạng Đa Giao Thức (TCP + UDP + Multicast)

Để tận dụng tối đa các công nghệ trong đề cương (TCP Socket, UDP Socket, Multicast, Custom Protocol, Binary transfer), hệ thống tách thành **03 kênh giao tiếp chạy song song**:

### 3.1. Kênh TCP Persistent Connection (`ServerSocket` & `Socket`)
* **Vai trò:** Duy trì kết nối hai chiều liên tục (Full-duplex) giữa từng JavaFX Client và Server từ lúc đăng nhập đến khi đăng xuất.
* **Chức năng:**
  * Xác thực tài khoản và quản lý phiên làm việc (Session).
  * Gửi báo cáo sự cố kèm truyền mảng byte hình ảnh hiện trường (Binary Transfer).
  * Đẩy lệnh điều phối từ Server xuống Responder Client (**Server Push**).
  * Cập nhật và đồng bộ tiến độ xử lý (`PENDING` -> `ASSIGNED` -> `IN_PROGRESS` -> `RESOLVED`) theo thời gian thực.

### 3.2. Kênh UDP Unicast Heartbeat (`DatagramSocket` & `DatagramPacket`)
* **Vai trò:** Định kỳ mỗi **3 giây**, các Responder Client (Nhân viên kỹ thuật, Y tế, Bảo vệ) bắn một gói tin UDP nhỏ chứa `HEARTBEAT|staffId|status` về cổng UDP của Server.
* **Ưu điểm kỹ thuật:**
  * Không làm nghẽn luồng TCP chính.
  * Giúp Server phát hiện tức thời nhân sự nào bị rớt mạng (quá **10 giây** không có nhịp tim) để tự động thu hồi sự cố và điều phối cho nhân viên khác (**Fault Tolerance / Auto-Failover**).

### 3.3. Kênh UDP Multicast Khẩn cấp (`MulticastSocket`)
* **Vai trò:** Khi có sự cố cấp độ Nguy hiểm cao nhất (ví dụ: Cháy nổ, Rò rỉ khí độc, Mất an ninh nghiêm trọng), Ban Giám hiệu kích hoạt lệnh báo động.
* **Cơ chế:** Server bắn 01 gói tin vào địa chỉ nhóm Multicast (ví dụ `230.0.0.1:9999`), toàn bộ JavaFX Client ở tất cả các phòng học đang lắng nghe sẽ đồng loạt bật Popup đỏ cảnh báo khẩn cấp mà Server không cần lặp qua từng kết nối TCP.

---

## 4. Thiết kế Custom Application Protocol & Xử lý Phân mảnh (Framing)

### 4.1. Giải quyết bài toán luồng byte (Byte Stream) trên TCP
Để phân biệt rõ ranh giới giữa các bản tin khi gửi liên tiếp hoặc khi vừa gửi chuỗi thông tin sự cố vừa gửi kèm file ảnh dung lượng lớn, hệ thống sử dụng `DataInputStream` và `DataOutputStream` với thiết kế **Length-Prefixed Framing Protocol** (Giao thức đóng khung có độ dài tiền tố):

~~~text
+---------------+----------------+------------------+-------------------+-------------------+-------------------+
| Magic Header  |  Message Type  |  Header Length   |   Header Payload  | Attachment Length | Attachment Bytes  |
|   (2 Bytes)   |    (1 Byte)    |    (4 Bytes)     |   (UTF-8 String)  |     (4 Bytes)     |   (Raw byte[])    |
+---------------+----------------+------------------+-------------------+-------------------+-------------------+
|     0xCAFE    |   0x01..0xFF   |        N         | CMD|key=val|...   |         M         |  [Image Binary]   |
+---------------+----------------+------------------+-------------------+-------------------+-------------------+
~~~

### 4.2. Cách hoạt động trên luồng I/O
* **Đọc 2 byte `short` (Magic Header):** Nếu khác `0xCAFE` thì lập tức từ chối xử lý và đóng kết nối (bắt lỗi **Invalid data / Unexpected input**).
* **Đọc 1 byte `type` (Message Type):** Định danh loại bản tin:
  * `0x01`: `LOGIN`
  * `0x02`: `SUBMIT_INCIDENT`
  * `0x03`: `DISPATCH_PUSH`
  * `0x04`: `ACK_TASK`
  * `0x05`: `STATUS_UPDATE`
  * `0x06`: `ERROR_NOTIFY`
* **Đọc `int N` (Header Length):** Dùng `readFully(byte[N])` để lấy chuỗi đặc tả UTF-8 (ví dụ: `INCIDENT|room=A201|type=ELECTRIC|severity=4|desc=Chập cầu dao`).
* **Đọc `int M` (Attachment Length):** Nếu `M > 0`, dùng `readFully(byte[M])` để đọc chính xác toàn bộ mảng byte của bức ảnh hiện trường mà không bao giờ bị lẫn sang bản tin tiếp theo.

---

## 5. Cơ chế Concurrency trên Server & Đồng bộ UI trên JavaFX

### 5.1. Tại phía Server
* **`ExecutorService` (Thread Pool):** Dùng `ThreadPoolExecutor` giới hạn số luồng để quản lý các `ClientHandler`, chống cạn kiệt tài nguyên khi nhiều Client kết nối cùng lúc.
* **`ConcurrentHashMap<Integer, ClientHandler>`:** Lưu danh sách các phiên làm việc (Session) đang active để Server có thể Push thông báo tới bất kỳ Client nào một cách an toàn đa luồng.
* **`PriorityBlockingQueue<IncidentTicket>` + Worker Threads:** Khi nhiều lớp học gửi báo cáo cùng lúc, các `ClientHandler` chỉ việc xác thực và đẩy nhanh sự cố vào `PriorityBlockingQueue` (sắp xếp theo độ khẩn cấp `Severity` và thời gian chờ), sau đó nhóm `DispatchWorker` chạy ngầm sẽ lấy sự cố ra để tìm nhân viên phù hợp nhất.
* **Chống Race Condition bằng `ReentrantLock` / `synchronized`:** Khi một sự cố được thông báo cho 2 nhân viên kỹ thuật và cả hai cùng bấm "Nhận nhiệm vụ" ở cùng một mili-giây, khối đồng bộ hóa trạng thái trên Server đảm bảo chỉ người đầu tiên chuyển trạng thái từ `PENDING -> ASSIGNED` thành công, người thứ hai sẽ nhận bản tin phản hồi `TASK_ALREADY_TAKEN`.

### 5.2. Tại phía JavaFX Client
* Tách biệt hoàn toàn **Network Reader Thread** (luồng chạy vòng lặp `while(isRunning)` chờ đọc dữ liệu từ `DataInputStream`) và **JavaFX Application Thread**.
* Mọi gói tin Server Push nhận được (như có sự cố mới, cập nhật tiến độ trên `TableView`, bật thông báo) đều được bọc trong `Platform.runLater(() -> controller.updateUI(packet))` để giao diện mượt mà, không bao giờ bị treo (Freeze UI).

---

## 6. Điểm Mới (Novelty & Contributions) & Thực Nghiệm (Experiment)

### 6.1. Đóng góp 1 – Hàng đợi ưu tiên chống đói (Aging Priority Queue) & Điều phối cân bằng tải (Least-Workload Dispatch)
* **Baseline:** Xử lý sự cố theo hàng đợi FIFO tuần tự và giao việc xoay vòng (Round-Robin) mà không quan tâm nhân sự đang ôm nhiều hay ít việc.
* **Proposed:** Hàng đợi ưu tiên kết hợp cơ chế **Aging** (sự cố mức độ thấp nếu chờ quá lâu sẽ tự động được cộng điểm ưu tiên để không bị "đói" khi trường có nhiều sự cố lớn), kết hợp phân công tự động cho nhân sự đúng chuyên môn (`Specialty`) đang có tải công việc hiện tại thấp nhất (`current_workload`).

### 6.2. Đóng góp 2 – Cơ chế Gom nhóm chống trùng lặp (Spatio-Temporal Deduplication) & Tự động chuyển giao lỗi (Auto-Failover)
* **Baseline:** 20 học sinh cùng báo hỏng máy chiếu phòng `A201` sẽ tạo ra 20 bản ghi và 20 lệnh điều phối độc lập; nếu nhân viên nhận việc bị rớt mạng thì sự cố bị treo vĩnh viễn.
* **Proposed:** Server tự động gộp các báo cáo cùng `room_location + category_code` trong vòng `Δt = 5 phút` thành 1 Ticket duy nhất (tăng trọng số khẩn cấp theo số người báo) và tự động thu hồi/điều phối lại khi mất UDP Heartbeat quá 10 giây hoặc quá hạn ACK.

### 6.3. Bảng tổng hợp Baseline vs. Proposed

| Tiêu chí | Hệ thống cơ sở (Baseline) | Giải pháp đề xuất (Proposed Novelty) | Bằng chứng thực nghiệm (Evidence) |
| :--- | :--- | :--- | :--- |
| **Hàng đợi & Điều phối** | Hàng đợi FIFO tuần tự + phân công xoay vòng (Round-Robin). | **Aging Priority Queue + Least-Workload Smart Dispatch** (chọn đúng chuyên môn + ít việc nhất). | Giảm mạnh thời gian chờ (Queue Waiting Time) của các sự cố khẩn cấp khi tải cao. |
| **Chống trùng lặp & Chịu lỗi** | Tạo bản ghi trùng lặp; treo tác vụ khi nhân viên mất kết nối. | **Spatio-Temporal Deduplication** (`Δt = 5 phút`) + **UDP Heartbeat Auto-Failover**. | Tăng Throughput, giảm bản tin điều phối dư thừa và phục hồi 100% sự cố khi rớt mạng. |

### 6.4. Cách đo số liệu thực nghiệm (`StressTestSimulator`)
Viết một module `StressTestSimulator` (chạy bằng Java Socket thuần không cần bật UI JavaFX) giả lập `50 -> 500` luồng Client bắn gói tin liên tục để xuất ra file CSV các chỉ số:
* **Response Time (ms)**
* **Throughput (req/s)**
* **Emergency Queue Waiting Time (ms)**
* **Failover Recovery Time (ms)**

---

## 7. Cấu trúc Thư mục Dự án Chuẩn

~~~text
NhomXX_SchoolIncidentDispatch/
├── Report/
│   └── NhomXX_SchoolIncidentDispatch_Report.docx
└── Source/
    ├── README.md
    ├── INSTRUCTION.md
    ├── TOPICS.md
    ├── statics/
    │   ├── architecture.png
    │   ├── protocol.png
    │   └── results/
    └── source/
        ├── common/          # Định nghĩa Packet, Protocol Framing (0xCAFE), Constants, Models
        ├── server/          # TCPServer, UDPHeartbeatListener, PriorityQueue, DispatchWorker, DAO
        ├── client/          # JavaFX Controllers, FXML Views, SocketClientManager, MulticastListener
        ├── simulator/       # Code chạy thực nghiệm bắn tải đồng thời (Baseline vs Proposed)
        └── database/        # schema.sql & seed_data.sql (6 bảng gọn nhẹ)
~~~