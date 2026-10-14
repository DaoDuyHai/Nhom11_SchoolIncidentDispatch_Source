# PHÁC THẢO THIẾT KẾ GIAO DIỆN JAVAFX
### Hệ thống Báo cáo & Điều phối Sự cố Trường THPT (School Incident Dispatch)

> Tài liệu dành cho nhóm phát triển. Đọc kèm: `DESIGN_SchoolIncidentDispatch.md` (kiến trúc mạng) và `schema.sql` (5 bảng DB).  
> Công nghệ UI: **JavaFX 17+**, mô hình **MVC / Component**, kết nối Server bằng `SocketClientManager` (TCP) + UDP Heartbeat.

---

## 0. Tóm tắt Kiến trúc Giao diện

| Mục | Nội dung |
| :--- | :--- |
| **Ứng dụng Server** | **Server Admin Dashboard (JavaFX)**: Khởi chạy cùng Server, hiển thị trung tâm giám sát mạng, kết nối socket, Aging Queue và điều phối nhân sự |
| **Ứng dụng Client** | Giao diện Client JavaFX đa vai trò (rẽ nhánh sau khi đăng nhập): **Reporter** (Giáo viên / Học sinh) hoặc **Responder** (Kỹ thuật / Y tế / Bảo vệ) |
| **Cơ chế nhận việc** | **Hồ công việc mở (Open Task Pool) & Tự chọn việc (Self-Pick)**: Nhân viên chủ động bấm "Nhận việc", không bị bắn popup cưỡng bức |
| **Ngôn ngữ giao diện** | Tiếng Việt hoàn toàn |
| **Kích thước cửa sổ** | Login: 420×560. Client Dashboard: 1100×720. Server Admin Console: 1280×780 |
| **Real-time** | Mọi cập nhật dữ liệu từ Socket xuống giao diện đều bọc qua `Platform.runLater(...)` (Zero Freeze UI) |

---

## 1. Design System (Hệ thống Thiết kế dùng chung)

### 1.1. Bảng màu (Theme Tokens)

| Token | Mã màu | Dùng cho |
| :--- | :--- | :--- |
| `-fx-primary` | `#1E5AA8` | Nút chính, Header thanh điều hướng, Tab đang chọn |
| `-fx-primary-dark` | `#16437D` | Hover nút chính |
| `-fx-bg` | `#F4F6F9` | Nền cửa sổ |
| `-fx-surface` | `#FFFFFF` | Card sự cố, Bảng dữ liệu, Khung nhập liệu |
| `-fx-border` | `#D9DEE5` | Đường viền các phần tử |
| `-fx-text` | `#1F2933` | Màu chữ chính |
| `-fx-text-muted` | `#6B7785` | Chữ phụ, Ghi chú, Placeholder |
| `-fx-success` | `#2E9E5B` | Trạng thái `AVAILABLE`, `RESOLVED`, Nút "Nhận việc" |
| `-fx-warning` | `#E8A317` | Trạng thái `ASSIGNED`, `IN_PROGRESS`, Cảnh báo |
| `-fx-danger` | `#D32F2F` | Sự cố khẩn cấp (Mức 5), Mất kết nối `OFFLINE` |
| `-fx-neutral` | `#9AA5B1` | Trạng thái `PENDING`, Vết hủy |

### 1.2. Màu theo mức độ nghiêm trọng (`base_severity` 1–5)

| Severity | Nhãn hiển thị | Màu nền badge | Màu chữ |
| :---: | :--- | :--- | :--- |
| 1 | Rất thấp | `#E3F2FD` | `#1565C0` |
| 2 | Thấp | `#E8F5E9` | `#2E7D32` |
| 3 | Trung bình | `#FFF8E1` | `#B26A00` |
| 4 | Cao | `#FFE0B2` | `#E65100` |
| 5 | Khẩn cấp | `#D32F2F` | `#FFFFFF` (in đậm) |

---

## 2. Màn hình 1: ĐĂNG NHẬP (Client Login)

* Kích thước: `420 × 560`, căn giữa màn hình.
* Giao diện phong cách phẳng, logo trường THPT, form nhập tên đăng nhập và mật khẩu.
* Thông báo trạng thái kết nối tới Server Socket (`127.0.0.1:5000`).

---

## 3. Màn hình 2: BÁO CÁO SỰ CỐ (Reporter View - Giáo viên / Học sinh)

* **Tab 1: Gửi báo cáo mới:**
  * Nhập vị trí phòng học (A101, A201, LAB_CHEM...).
  * Chọn loại sự cố (Điện, Nước, Thiết bị, Y tế, An ninh).
  * Mức độ nghiêm trọng (1–5) với thanh gạt / nút chọn trực quan.
  * Khung xem trước ảnh và nút "Chọn ảnh minh chứng" (đọc mảng byte nhị phân).
  * Nút "GỬI BÁO CÁO SỰ CỐ".
* **Tab 2: Báo cáo của tôi:**
  * Bảng TableView hiển thị các sự cố người dùng đã gửi.
  * Tự động cập nhật thời gian thực khi sự cố có người tiếp nhận hoặc hoàn thành.
  * Hiển thị thông báo khi báo cáo được gộp trùng (Spatio-Temporal Deduplication).

---

## 4. Màn hình 3: TIẾP NHẬN & XỬ LÝ (Responder View - Kỹ thuật / Y tế / Bảo vệ)

### 4.1. Tab 1: Hồ công việc mở (Open Task Pool - Tự nhận việc)
* **Khái niệm:** Thay vì Server tự ý bắn Popup dồn dập, nhân viên được trao quyền chủ động hoàn toàn.
* **Giao diện:** Danh sách các Card sự cố đang chờ xử lý (`PENDING`) thuộc chuyên môn của nhân sự, sắp xếp nổi bật theo điểm ưu tiên `PriorityScore`.
* **Cấu trúc mỗi Card sự cố:**
  ```text
  +----------------------------------------------------------------------------------+
  | INC-20261014-0009               [5 KHẨN CẤP]   [Điểm ưu tiên: 532.5]             |
  | Phòng: LAB_CHEM1 · Loại: Cháy nổ / Hóa chất · 3 người báo                       |
  | "Có khói trắng bốc ra từ tủ hóa chất, mùi nồng nặc"                             |
  | [ Ảnh thu nhỏ hiện trường ]     Chờ trong hàng đợi: 45 giây                      |
  |                                                  [ NHẬN VIỆC (CLAIM TASK) ]      |
  +----------------------------------------------------------------------------------+
  ```
* **Thao tác "Nhận việc":**
  * Nhân viên bấm nút **"NHẬN VIỆC"**.
  * Client gửi gói tin `CLAIM_TASK|ticketId=...`.
  * Cơ chế Atomic Claim chống đụng độ: Nếu thành công, sự cố chuyển ngay sang Tab "Nhiệm vụ của tôi". Nếu bị người khác nhận trước, hiện thông báo nhẹ nhàng *"Nhiệm vụ vừa được đồng nghiệp tiếp nhận trước"*.

### 4.2. Tab 2: Nhiệm vụ của tôi (Đang thực hiện)
* Hiển thị danh sách các sự cố do chính nhân viên này đang tiếp nhận (`ASSIGNED` hoặc `IN_PROGRESS`).
* Nút chuyển trạng thái:
  * Khi bắt đầu đến hiện trường: Bấm **"BẮT ĐẦU XỬ LÝ"** -> Chuyển thành `IN_PROGRESS`.
  * Khi xử lý xong: Bấm **"HOÀN THÀNH"** -> Hộp thoại ghi chú nghiệm thu mở ra -> Cập nhật `RESOLVED`.

### 4.3. Tab 3: Lịch sử xử lý
* Bảng thống kê các ca đã hoàn thành của nhân viên, thời gian bắt đầu và kết thúc.

---

## 5. Màn hình 4: SERVER ADMIN DASHBOARD (Tích hợp trực tiếp trên Server)

Giao diện quản trị viên trung tâm, tự động mở khi chạy Server:

### 5.1. Khung Giám sát Mạng & Kết nối Sống (Live Network & Socket Monitor)
* **Thanh trạng thái:** 
  * `TCP Server Port 5000: LISTENING (ACTIVE)`
  * `UDP Heartbeat Port 5001: MONITORING`
  * `Tổng số kết nối Client: N online`
* **Bảng danh sách kết nối:** Hiển thị IP, Port, Tên người dùng, Vai trò, Trạng thái nhịp tim (nhấp nháy xanh chu kỳ 3s; quá 10s chuyển sang cảnh báo đỏ).

### 5.2. Khung Quản lý Sự cố & Hàng đợi Ưu tiên (Aging Priority Queue)
* Bảng toàn bộ sự cố thời gian thực: Mã vé, Phòng, Loại, Mức độ, Điểm ưu tiên động, Trạng thái, Nhân sự tiếp nhận.
* Điểm ưu tiên `PriorityScore` tự động tăng dần theo thời gian thực (chống đói tác vụ) ngay trước mắt quản trị viên.
* **Chức năng Chỉ định thủ công (Manual Assign):** Chọn 1 sự cố `PENDING` -> Chọn 1 nhân viên đang rảnh -> Bấm "Chỉ định" để giao việc trực tiếp trong tình huống khẩn cấp.

### 5.3. Khung Giám sát Tải Công việc (Personnel Workload)
* Danh sách toàn bộ nhân viên kỹ thuật, y tế, bảo vệ.
* Thước đo tải công việc: `current_workload` (số ca đang giữ), trạng thái `AVAILABLE` / `BUSY` / `OFFLINE`.
