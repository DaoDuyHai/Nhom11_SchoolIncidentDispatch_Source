# GIAO THỨC MẠNG & ĐẶC TẢ GÓI TIN (APPLICATION PROTOCOL SPECIFICATION)
**Hệ thống Báo cáo và Điều phối Sự cố Trường THPT (School Incident Dispatch System)**

---

## 1. Tổng quan Kiến trúc Mạng Đa Kênh

Hệ thống sử dụng mô hình kết hợp **03 kênh truyền thông mạng độc lập** bằng Java Socket thuần túy:

| Kênh truyền | Giao thức | Địa chỉ / Cổng | Chiều dữ liệu | Mục đích |
| :--- | :--- | :--- | :--- | :--- |
| **Kênh 1: Core TCP Channel** | TCP Persistent | `Server_IP:5000` | Song công (Full-duplex) | Xác thực, gửi báo cáo kèm ảnh, Server-Push nhiệm vụ, cập nhật trạng thái |
| **Kênh 2: Heartbeat Channel** | UDP Unicast | `Server_IP:5001` | Client -> Server (3s/lần) | Giám sát nhịp sống Responder, kích hoạt Auto-Failover khi timeout > 10s |
| **Kênh 3: Emergency Broadcast** | UDP Multicast | `230.0.0.1:9999` | Server -> All Clients | Cảnh báo thảm họa khẩn cấp (Cháy nổ, Di tản) đồng thời tới toàn trường |

---

## 2. Giao thức Đóng khung Nhị phân Kênh TCP (Binary Length-Prefixed Framing)

Để giải quyết triệt để bài toán **Byte Stream** và phân mảnh bản tin (Framing) khi truyền dữ liệu dạng chuỗi kèm mảng byte hình ảnh dung lượng lớn, hệ thống áp dụng cấu trúc khung nhị phân chuẩn hóa:

```text
+---------------+----------------+------------------+-------------------+-------------------+-------------------+
| Magic Header  |  Message Type  |  Header Length   |   Header Payload  | Attachment Length | Attachment Bytes  |
|   (2 Bytes)   |    (1 Byte)    |    (4 Bytes)     |   (UTF-8 String)  |     (4 Bytes)     |   (Raw byte[])    |
+---------------+----------------+------------------+-------------------+-------------------+-------------------+
|     0xCAFE    |   0x01..0x19   |        N         | CMD|key=val|...   |         M         |  [Image Binary]   |
+---------------+----------------+------------------+-------------------+-------------------+-------------------+
```

### 2.1. Chi tiết các trường (Fields)
1. **Magic Header (`short`, 2 bytes):** Luôn có giá trị `0xCAFE`. Dùng để kiểm tra tính hợp lệ của luồng dữ liệu ngay từ byte đầu tiên; loại bỏ ngay các kết nối rác.
2. **Message Type (`byte`, 1 byte):** Định danh loại gói tin (từ `0x01` đến `0x19`).
3. **Header Length (`int`, 4 bytes, Big-Endian):** Độ dài $N$ tính bằng byte của chuỗi văn bản UTF-8 Header Payload ($0 \le N \le 5\text{ MB}$).
4. **Header Payload (`byte[N]`, UTF-8):** Chuỗi lệnh và tham số định dạng theo cặp khóa-giá trị: `COMMAND|key1=value1|key2=value2...`.
5. **Attachment Length (`int`, 4 bytes, Big-Endian):** Độ dài $M$ tính bằng byte của tệp đính kèm (Ảnh hiện trường) ($0 \le M \le 25\text{ MB}$). Nếu không có ảnh, $M = 0$.
6. **Attachment Bytes (`byte[M]`, Raw binary):** Toàn bộ mảng byte thô của tệp ảnh JPEG/PNG. Được đọc chính xác bằng `DataInputStream.readFully()`.

---

## 3. Danh mục Mã Bản tin (Message Types) Kênh TCP

### 3.1. `0x01` - TYPE_LOGIN (Đăng nhập)
* **Client -> Server:**
  `LOGIN|username=gv_lan|password=123456`
* **Attachment:** Không ($M = 0$).

### 3.2. `0x02` - TYPE_LOGIN_RESP (Phản hồi đăng nhập)
* **Server -> Client (Thành công):**
  `LOGIN_RESP|status=OK|userId=2|username=gv_lan|fullName=Cô Trần Thị Lan (GVCN 10A2)|role=REPORTER|subRole=TEACHER|specialty=NONE`
* **Server -> Client (Thất bại):**
  `LOGIN_RESP|status=FAIL|message=Sai tên đăng nhập hoặc mật khẩu`

### 3.3. `0x03` - TYPE_SUBMIT_INCIDENT (Gửi báo cáo sự cố)
* **Client -> Server:**
  `INCIDENT|room=A201|category=ELECTRIC|severity=4|desc=Cầu dao bốc khói|reqId=uuid-12345`
* **Attachment:** Mảng byte của ảnh chụp hiện trường ($M > 0$).
* **Idempotency:** Tham số `reqId` đảm bảo nếu gửi lại cùng yêu cầu thì Server trả lại kết quả cũ mà không tạo bản ghi mới.

### 3.4. `0x04` - TYPE_SUBMIT_RESP (Phản hồi nộp sự cố)
* **Server -> Client:**
  `SUBMIT_RESP|status=OK|ticketId=10|ticketCode=INC-20261014-0010|isMerged=0|message=Đã tạo sự cố mới`
  *(Nếu trùng trong cửa sổ 5 phút: `isMerged=1|message=Báo cáo đã được gộp vào sự cố INC-...`)*.

### 3.5. `0x05` - TYPE_DISPATCH_PUSH (Server Push nhiệm vụ xuống Responder)
* **Server -> Client (Responder):**
  `DISPATCH_PUSH|ticketId=10|ticketCode=INC-20261014-0010|room=A201|category=ELECTRIC|severity=4|title=Chập điện tại A201|desc=Cầu dao bốc khói`
* **Attachment:** Tự động tải mảng byte ảnh chụp hiện trường từ đĩa của Server để gửi thẳng xuống JavaFX Client của Responder hiển thị trên giao diện tiếp nhận.

### 3.6. `0x06` - TYPE_ACK_TASK (Nhận hoặc Từ chối nhiệm vụ)
* **Client (Responder) -> Server:**
  `ACK_TASK|ticketId=10|action=ACCEPT` (hoặc `action=DECLINE`)

### 3.7. `0x07` - TYPE_ACK_RESP (Phản hồi chống Race Condition)
* **Server -> Client (Thành công):**
  `ACK_RESP|status=OK|ticketId=10|action=ACCEPT`
* **Server -> Client (Trùng ca - Đã có người nhận trước):**
  `ACK_RESP|status=TASK_ALREADY_TAKEN|ticketId=10|message=Sự cố đã có nhân viên khác tiếp nhận!`

### 3.8. `0x08` - TYPE_STATUS_UPDATE (Cập nhật tiến độ xử lý)
* **Client (Responder) -> Server:**
  `STATUS_UPDATE|ticketId=10|newStatus=IN_PROGRESS|note=Đang thay thế aptomat`
  (hoặc `newStatus=RESOLVED|note=Đã sửa chữa an toàn`)
* **Server -> Broadcast:** Đẩy bản tin trạng thái cập nhật tới toàn bộ Admin và Reporter liên quan.

### 3.9. `0x09` - TYPE_LIST_MY_TICKETS & `0x0A` - TYPE_LIST_MY_TICKETS_RESP
* Lấy danh sách các sự cố do chính người dùng hiện tại đã báo cáo hoặc được phân công xử lý (JSON Array).

### 3.10. `0x0B` - TYPE_GET_ALL_TICKETS & `0x0C` - TYPE_GET_ALL_TICKETS_RESP
* Dành riêng cho vai trò `ADMIN` để theo dõi và giám sát toàn bộ sự cố trên hệ thống.

### 3.11. `0x0D` - TYPE_MULTICAST_ALERT (Kích hoạt cảnh báo toàn trường)
* **Admin -> Server (TCP):**
  `ALERT_TRIGGER|level=CRITICAL|message=Báo động cháy tại khu thực hành Hóa`
* **Server:** Lưu CSDL bảng `multicast_alerts` và bắn gói tin sang kênh UDP Multicast.

### 3.12. `0x0E` - TYPE_ERROR_NOTIFY (Báo lỗi hệ thống)
* `ERROR|code=UNAUTHORIZED|message=Vui lòng đăng nhập trước`

### 3.13. `0x0F` - TYPE_HEARTBEAT_TCP
* `PING` / `PONG` kiểm tra tính sống còn của kết nối TCP.

### 3.14. `0x10` - TYPE_LOGOUT
* Thông báo đóng phiên làm việc an toàn.

---

## 4. Đặc tả Kênh UDP Heartbeat (Port 5001)

### 4.1. Cơ chế gửi
* Mỗi **3 giây**, luồng `UDPHeartbeatSender` tại Responder Client bắn 01 DatagramPacket kích thước nhỏ về cổng 5001 của Server:
  ```text
  HEARTBEAT|staffId=4|status=AVAILABLE
  ```
### 4.2. Cơ chế Auto-Failover
* Phía Server, `HeartbeatMonitor` duy trì `ConcurrentHashMap<Integer, Long> lastHeartbeats`.
* Mỗi chu kỳ kiểm tra (1 giây):
  $$\Delta t = \text{currentTime} - \text{lastHeartbeatTime}$$
* Nếu $\Delta t > 10,000\text{ ms}$ (10 giây):
  1. Đánh dấu nhân sự `is_online = 0`, `availability_status = 'OFFLINE'`.
  2. Ngắt phiên kết nối trong `ServerSessionManager`.
  3. Quét toàn bộ Ticket đang `ASSIGNED` hoặc `IN_PROGRESS` gán cho nhân sự này.
  4. Thu hồi về `PENDING`, ghi nhận `dispatch_logs` lý do "Mất UDP Heartbeat > 10s".
  5. Đưa ngược sự cố vào `AgingPriorityQueue` với điểm ưu tiên cao để `DispatchWorker` điều phối ngay lập tức cho nhân viên khác.

---

## 5. Đặc tả Kênh UDP Multicast (230.0.0.1:9999)

### 5.1. Cấu trúc gói tin Multicast
* Chuỗi ký tự UTF-8 chứa trong UDP DatagramPacket:
  ```text
  ALERT|level=CRITICAL|title=CẢNH BÁO CHÁY NỔ TOÀN TRƯỜNG|message=Học sinh toàn trường di tản khẩn cấp ra sân bóng|timestamp=2026-10-14 13:30:00.000
  ```
### 5.2. Xử lý tại Client
* Luồng `MulticastReceiver` chạy ngầm gia nhập nhóm `230.0.0.1:9999`.
* Khi nhận gói tin, sử dụng `Platform.runLater()` để hiển thị cửa sổ `EmergencyAlertDialog` màu đỏ rực, phát âm thanh cảnh báo và rung giao diện ngay lập tức mà không làm chậm luồng công việc hiện tại.
