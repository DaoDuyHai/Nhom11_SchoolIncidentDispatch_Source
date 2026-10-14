# GIAO THỨC MẠNG & ĐẶC TẢ GÓI TIN (APPLICATION PROTOCOL SPECIFICATION)
### HỆ THỐNG BÁO CÁO VÀ ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT THEO THỜI GIAN THỰC
*(High School Real-time Incident Reporting & Dispatch System)*

---

## 1. Tổng quan Kiến trúc Mạng Hai Kênh (Dual-Channel Architecture)

Hệ thống sử dụng mô hình kết hợp **02 kênh truyền thông mạng độc lập** bằng Java Socket thuần túy:

| Kênh truyền | Giao thức | Địa chỉ / Cổng | Chiều dữ liệu | Mục đích |
| :--- | :--- | :--- | :--- | :--- |
| **Kênh 1: Core TCP Channel** | TCP Persistent | `Server_IP:5000` | Song công (Full-duplex) | Xác thực, gửi báo cáo kèm ảnh, xem Hồ công việc mở, tự nhận việc (Atomic Claim), cập nhật tiến trình |
| **Kênh 2: Heartbeat Channel** | UDP Unicast | `Server_IP:5001` | Client -> Server (3s/lần) | Giám sát nhịp sống Responder, phát hiện rớt mạng và kích hoạt Auto-Failover thu hồi tác vụ khi timeout > 10s |

---

## 2. Giao thức Đóng khung Nhị phân Kênh TCP (Binary Length-Prefixed Framing 0xCAFE)

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
1. **Magic Header (`short`, 2 bytes):** Luôn có giá trị `0xCAFE`. Dùng để kiểm tra tính hợp lệ của luồng dữ liệu ngay từ byte đầu tiên; loại bỏ ngay các kết nối rác hoặc dữ liệu không hợp lệ.
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

### 3.5. `0x05` - TYPE_LIST_TASK_POOL (Yêu cầu danh sách Hồ công việc mở)
* **Client (Responder) -> Server:**
  `LIST_TASK_POOL|specialty=TECHNICAL`
* **Mục đích:** Nhân viên cứu hộ/kỹ thuật truy vấn danh sách các sự cố mở (`PENDING`) thuộc chuyên môn của mình hoặc toàn trường.

### 3.6. `0x06` - TYPE_TASK_POOL_RESP (Trả về danh sách Hồ công việc mở)
* **Server -> Client:**
  Chuỗi JSON chứa danh sách mảng các đối tượng `IncidentTicket` ở trạng thái `PENDING`, đã được sắp xếp giảm dần theo điểm ưu tiên `PriorityScore`.

### 3.7. `0x07` - TYPE_CLAIM_TASK (Tự chủ nhận việc - Self-Pick)
* **Client (Responder) -> Server:**
  `CLAIM_TASK|ticketId=10`
* **Mục đích:** Nhân viên chủ động bấm nhận một sự cố cụ thể từ Task Pool khi rảnh tay.

### 3.8. `0x08` - TYPE_CLAIM_RESP (Phản hồi chống Race Condition)
* **Server -> Client (Thành công):**
  `CLAIM_RESP|status=OK|ticketId=10|message=Nhận việc thành công`
* **Server -> Client (Trùng ca - Đã có người nhận trước):**
  `CLAIM_RESP|status=TASK_ALREADY_TAKEN|ticketId=10|message=Nhiệm vụ này vừa được đồng nghiệp tiếp nhận trước!`

### 3.9. `0x09` - TYPE_STATUS_UPDATE (Cập nhật tiến độ xử lý)
* **Client (Responder) -> Server:**
  `STATUS_UPDATE|ticketId=10|newStatus=IN_PROGRESS|note=Đang thay thế aptomat`
  (hoặc `newStatus=RESOLVED|note=Đã sửa chữa an toàn`)
* **Server -> Broadcast:** Đẩy bản tin trạng thái cập nhật tới toàn bộ các bên liên quan và cập nhật trực tiếp lên Server Admin Dashboard.

### 3.10. `0x0A` - TYPE_LIST_MY_TICKETS & TYPE_LIST_MY_TICKETS_RESP
* **Client -> Server:**
  `LIST_MY_TICKETS`
* Lấy danh sách các sự cố do chính người dùng hiện tại đã báo cáo (đối với Reporter) hoặc các nhiệm vụ đang đảm trách (đối với Responder).

### 3.11. `0x0B` - TYPE_MANUAL_ASSIGN (Admin chỉ định việc thủ công)
* **Server Admin Dashboard -> Server Internal / Client:**
  `MANUAL_ASSIGN|ticketId=10|staffId=4|note=Hiệu trưởng chỉ định xử lý gấp`
* Dành riêng cho Ban Giám hiệu trên Server Dashboard để can thiệp chỉ định trực tiếp nhân viên tiếp nhận ca sự cố đặc biệt.

### 3.12. `0x0C` - TYPE_ERROR_NOTIFY (Báo lỗi hệ thống)
* `ERROR|code=UNAUTHORIZED|message=Vui lòng đăng nhập trước`

---

## 4. Đặc tả Kênh Giám sát Sống còn UDP Unicast (Port 5001)

### 4.1. Cấu trúc Gói tin UDP Heartbeat
* **Client (Responder) -> Server:**
  `HEARTBEAT|staffId=4|status=AVAILABLE|workload=1|timestamp=1728956400000`
* **Tần suất:** Đúng chu kỳ **3 giây/lần**.
* **Xử lý tại Server:** Cập nhật trường `last_heartbeat_at` trong CSDL và cập nhật trạng thái kết nối trên bảng giám sát Server Dashboard. Nếu quá **10 giây** không nhận được heartbeat, tự động kích hoạt Auto-Failover thu hồi tác vụ dở dang về Task Pool.
