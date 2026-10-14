# PHÁC THẢO THIẾT KẾ GIAO DIỆN JAVAFX
**Hệ thống Báo cáo & Điều phối Sự cố Trường THPT (School Incident Dispatch)**

> Tài liệu dành cho nhóm code. Đọc kèm: `DESIGN_SchoolIncidentDispatch.md` (kiến trúc mạng) và `schema.sql` (6 bảng DB).
> Công nghệ UI: **JavaFX 17+ (FXML + CSS)**, mô hình **MVC**, kết nối Server bằng `SocketClientManager` (TCP) + UDP Heartbeat + `MulticastListener`.

---

## 0. Tóm tắt nhanh

| Mục | Nội dung |
| :--- | :--- |
| Số ứng dụng | **1 ứng dụng JavaFX duy nhất**, sau khi đăng nhập rẽ nhánh theo `users.role` |
| Số màn hình chính | 1 Login + 3 Dashboard (Reporter / Responder / Admin) + 4 Popup/Dialog |
| Vai trò | `REPORTER` (GV/HS), `RESPONDER` (Kỹ thuật/Y tế/Bảo vệ), `ADMIN` (Hiệu trưởng) |
| Ngôn ngữ giao diện | Tiếng Việt |
| Kích thước cửa sổ | Login: 420×560 (cố định). Dashboard: 1280×760 (min 1024×640, cho phép resize) |
| Real-time | Mọi dữ liệu push từ Server phải qua `Platform.runLater(...)` |

---

## 1. Design System (dùng chung toàn app)

### 1.1. Bảng màu (đặt trong `styles/theme.css` dạng looked-up colors)

| Token | Mã màu | Dùng cho |
| :--- | :--- | :--- |
| `-fx-primary` | `#1E5AA8` | Nút chính, header, tab đang chọn |
| `-fx-primary-dark` | `#16437D` | Hover nút chính |
| `-fx-bg` | `#F4F6F9` | Nền cửa sổ |
| `-fx-surface` | `#FFFFFF` | Card, bảng, form |
| `-fx-border` | `#D9DEE5` | Viền |
| `-fx-text` | `#1F2933` | Chữ chính |
| `-fx-text-muted` | `#6B7785` | Chữ phụ, placeholder |
| `-fx-success` | `#2E9E5B` | AVAILABLE, RESOLVED, kết nối tốt |
| `-fx-warning` | `#E8A317` | BUSY, ASSIGNED, cảnh báo chờ |
| `-fx-danger` | `#D32F2F` | CRITICAL, lỗi, OFFLINE mất kết nối, popup khẩn cấp |
| `-fx-neutral` | `#9AA5B1` | OFFLINE, CANCELLED |

### 1.2. Màu theo mức độ nghiêm trọng (`base_severity` 1–5)

| Severity | Nhãn | Màu nền badge | Màu chữ |
| :---: | :--- | :--- | :--- |
| 1 | Rất thấp | `#E3F2FD` | `#1565C0` |
| 2 | Thấp | `#E8F5E9` | `#2E7D32` |
| 3 | Trung bình | `#FFF8E1` | `#B26A00` |
| 4 | Cao | `#FFE0B2` | `#E65100` |
| 5 | Khẩn cấp | `#D32F2F` | `#FFFFFF` (đậm) |

### 1.3. Màu theo trạng thái Ticket (`incident_tickets.status`)

| Status | Nhãn hiển thị | Màu badge |
| :--- | :--- | :--- |
| `PENDING` | Chờ điều phối | Xám đậm `#6B7785` |
| `ASSIGNED` | Đã giao | Vàng `#E8A317` |
| `IN_PROGRESS` | Đang xử lý | Xanh dương `#1E5AA8` |
| `RESOLVED` | Đã xử lý xong | Xanh lá `#2E9E5B` |
| `CANCELLED` | Đã huỷ | Xám nhạt `#9AA5B1` |

### 1.4. Typography và spacing
- Font: `Segoe UI` (fallback `Arial`). Tiêu đề màn hình 20px bold, tiêu đề card 16px bold, nội dung 13px, chú thích 11px.
- Spacing theo bội số 4: padding card **16**, gap giữa các card **12**, gap trong form **10**.
- Bo góc card/nút/input: **8px**. Card có đổ bóng nhẹ: `dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2)`.
- Nút: cao 36px. Nút chính (`.btn-primary`), nút phụ (`.btn-secondary`, viền), nút nguy hiểm (`.btn-danger`, nền đỏ).

### 1.5. Component dùng lại (đặt trong `client/ui/component/`)

| Component | Mô tả |
| :--- | :--- |
| `SeverityBadge` | `Label` bo tròn, nhận số 1–5, tự đổi màu theo bảng 1.2 |
| `StatusBadge` | `Label` bo tròn, nhận status String, tự đổi màu theo bảng 1.3 |
| `ConnectionIndicator` | Chấm tròn 10px + chữ: xanh "Đã kết nối", vàng "Đang kết nối lại…", đỏ "Mất kết nối" |
| `TicketTableView` | `TableView<TicketRow>` có sẵn các cột chuẩn, dùng chung cho cả 3 role |
| `ToastNotification` | Thông báo nhỏ góc phải dưới, tự tắt sau 4 giây |
| `CountdownBar` | `ProgressBar` + nhãn giây còn lại, dùng cho popup nhận việc |

---

## 2. Sơ đồ điều hướng

~~~text
                        +----------------+
                        |  LoginView     |
                        +--------+-------+
                                 | LOGIN thành công (đọc role)
        +------------------------+-------------------------+
        |                        |                         |
        v                        v                         v
+---------------+       +-----------------+       +------------------+
| ReporterView  |       | ResponderView   |       |   AdminView      |
| - Báo sự cố   |       | - Nhiệm vụ      |       | - Bảng điều khiển|
| - Lịch sử     |       | - Lịch sử       |       | - Nhân sự        |
+---------------+       +-----------------+       | - Cảnh báo       |
                                                  | - Thống kê       |
                                                  +------------------+

Popup toàn cục (hiện trên MỌI role, nhận từ Multicast):  EmergencyAlertDialog
Popup riêng Responder (nhận từ DISPATCH_PUSH):          DispatchRequestDialog
~~~

Điều hướng bằng cách thay `Scene` root của `Stage` chính qua class `SceneManager` (singleton): `SceneManager.switchTo("ReporterView.fxml")`.

---

## 3. Màn hình 1: LOGIN (`LoginView.fxml`)

**Mục đích:** Xác thực, kết nối TCP tới Server, rẽ nhánh theo role.

### 3.1. Wireframe

~~~text
+------------------------------------------+
|                                          |
|              [Logo trường]               |
|        HỆ THỐNG BÁO CÁO SỰ CỐ           |
|          TRƯỜNG THPT                     |
|                                          |
|   Địa chỉ Server                         |
|   [ 192.168.1.10      ] : [ 5000 ]       |
|                                          |
|   Tên đăng nhập                          |
|   [ ______________________________ ]     |
|                                          |
|   Mật khẩu                               |
|   [ ******************************  👁 ]  |
|                                          |
|   [ ] Ghi nhớ tên đăng nhập              |
|                                          |
|   [          ĐĂNG NHẬP            ]      |
|                                          |
|   (thông báo lỗi màu đỏ hiện ở đây)      |
|                                          |
|   ● Chưa kết nối                         |
+------------------------------------------+
~~~

### 3.2. Danh sách control

| `fx:id` | Control | Ghi chú |
| :--- | :--- | :--- |
| `txtServerHost` | `TextField` | Mặc định `127.0.0.1` |
| `txtServerPort` | `TextField` | Mặc định cổng TCP của Server, chỉ nhận số |
| `txtUsername` | `TextField` | Bắt buộc |
| `txtPassword` | `PasswordField` | Bắt buộc, có nút hiện/ẩn |
| `chkRemember` | `CheckBox` | Lưu username vào `Preferences` |
| `btnLogin` | `Button` (`.btn-primary`) | Enter ở ô mật khẩu cũng kích hoạt |
| `lblError` | `Label` (đỏ) | Ẩn khi không có lỗi |
| `progressLogin` | `ProgressIndicator` | Hiện khi đang đăng nhập, khoá toàn bộ form |
| `connIndicator` | `ConnectionIndicator` | Dưới cùng |

### 3.3. Hành vi
1. Validate: username và password không rỗng, port là số 1–65535. Sai thì hiện `lblError`, không gửi.
2. Bấm Login: khoá form, hiện `progressLogin`, **chạy trong `Task` ở luồng nền** (tuyệt đối không connect trên FX thread).
3. Gửi bản tin `0x01 LOGIN` với header dạng `LOGIN|username=...|password=...`.
4. Server trả thành công: client nhận `user_id`, `full_name`, `role`, `sub_role`, `specialty`. Khởi tạo `SessionContext` (singleton), rồi chuyển màn hình theo `role`:
   - `REPORTER` → `ReporterView`
   - `RESPONDER` → `ResponderView` (đồng thời **bật UDP Heartbeat 3 giây/lần**)
   - `ADMIN` → `AdminView`
5. Thất bại: hiện lỗi cụ thể bằng tiếng Việt:
   - Sai tài khoản/mật khẩu → "Tên đăng nhập hoặc mật khẩu không đúng."
   - Không kết nối được → "Không thể kết nối tới máy chủ. Kiểm tra địa chỉ và cổng."
   - Tài khoản đang đăng nhập nơi khác → "Tài khoản này đang được sử dụng ở thiết bị khác."
6. Sau khi login xong, tất cả client (mọi role) bắt đầu lắng nghe **Multicast** `230.0.0.1:9999`.

---

## 4. Khung chung của 3 Dashboard

~~~text
+--------------------------------------------------------------------------------+
| [Logo] Điều phối sự cố THPT     <tên màn hình>      ● Đã kết nối  👤 Cô Lan ▾  |  <- Top bar (BorderPane.top)
+----------+---------------------------------------------------------------------+
|          |                                                                     |
|  MENU    |                    VÙNG NỘI DUNG CHÍNH                              |
|  TRÁI    |                    (StackPane, đổi theo menu)                       |
| (200px)  |                                                                     |
|          |                                                                     |
+----------+---------------------------------------------------------------------+
| Trạng thái: Sẵn sàng  |  Ping: 3ms  |  Phiên bản 1.0                           |  <- Status bar
+--------------------------------------------------------------------------------+
~~~

- Layout gốc: `BorderPane`. Top = `HBox` header, Left = `VBox` menu (các `ToggleButton` trong `ToggleGroup`), Center = `StackPane` chứa các pane nội dung, Bottom = status bar.
- Menu "Đăng xuất" ở góc phải trên (`MenuButton` tên người dùng): Đổi mật khẩu (tuỳ chọn), Đăng xuất.
- Đăng xuất: gửi thông báo, đóng socket, dừng Heartbeat, quay về Login.
- Khi TCP bị mất: `connIndicator` chuyển đỏ, banner đỏ phía trên nội dung: "Mất kết nối máy chủ. Đang thử kết nối lại…", client tự retry mỗi 3 giây, tối đa 10 lần rồi quay về Login.

---

## 5. Màn hình 2: REPORTER (Giáo viên / Học sinh) – `ReporterView.fxml`

Menu trái: **① Báo sự cố mới** · **② Báo cáo của tôi**

### 5.1. Tab ① Báo sự cố mới

~~~text
+----------------------------------------------------------------------+
|  BÁO CÁO SỰ CỐ MỚI                                                   |
|                                                                      |
|  +-------------------------------+  +-----------------------------+  |
|  | Vị trí (phòng) *              |  |   ẢNH HIỆN TRƯỜNG           |  |
|  | [ A201                   ]    |  |  +-----------------------+  |  |
|  |                               |  |  |                       |  |  |
|  | Loại sự cố *                  |  |  |   (xem trước ảnh)     |  |  |
|  | [ Sự cố Điện nguy hiểm   ▾ ]  |  |  |                       |  |  |
|  |                               |  |  +-----------------------+  |  |
|  | Mức độ nghiêm trọng *         |  |  [ Chọn ảnh… ]  [ Xoá ]     |  |
|  | (1)(2)(3)(●4)(5)  Cao         |  |  Kích thước: 240 KB / 2 MB  |  |
|  |                               |  +-----------------------------+  |
|  | Mô tả chi tiết *              |                                   |
|  | +---------------------------+ |                                   |
|  | |                           | |                                   |
|  | +---------------------------+ |                                   |
|  |  0/500 ký tự                  |                                   |
|  |                               |                                   |
|  |  [ Huỷ ]      [ GỬI BÁO CÁO ] |                                   |
|  +-------------------------------+                                   |
+----------------------------------------------------------------------+
~~~

| `fx:id` | Control | Ghi chú / Ràng buộc |
| :--- | :--- | :--- |
| `txtRoom` | `TextField` | **Nhập tay** (DB không có bảng rooms), vd `A101`, `LAB_CHEM1`. Tự `trim()` + `toUpperCase()`. Bắt buộc, tối đa 20 ký tự, chỉ chữ/số/dấu `_` |
| `cbCategory` | `ComboBox<IncidentCategory>` | Nạp từ bảng `incident_categories` khi đăng nhập (hiển thị `category_name`) |
| `severityGroup` | 5 `ToggleButton` tròn trong `ToggleGroup` | Khi chọn loại sự cố, **tự chọn sẵn `default_severity`** của loại đó, người dùng vẫn đổi được |
| `lblSeverityText` | `Label` | Hiện nhãn "Cao", "Khẩn cấp"… |
| `taDescription` | `TextArea` | Bắt buộc, 10–500 ký tự, có bộ đếm `lblCharCount` |
| `imgPreview` | `ImageView` | 260×180, `preserveRatio=true` |
| `btnChooseImage` | `Button` | `FileChooser`, lọc `*.jpg;*.jpeg;*.png`, tối đa **2 MB** (vượt thì báo lỗi, không nhận) |
| `btnClearImage` | `Button` | Xoá ảnh đã chọn |
| `btnSubmit` | `Button` (`.btn-primary`) | Disable cho tới khi đủ các trường bắt buộc |
| `btnCancel` | `Button` | Xoá sạch form (có confirm nếu đã nhập) |

**Luồng gửi:**
1. Bấm "Gửi báo cáo" → `Task` nền: đọc file ảnh thành `byte[]`, đóng gói bản tin `0x02 SUBMIT_INCIDENT`:
   - Header: `INCIDENT|room=A201|type=ELECTRIC|severity=4|desc=...` (nhớ escape ký tự `|` và xuống dòng trong `desc`)
   - Attachment: `byte[]` ảnh (nếu không có ảnh → độ dài 0)
2. Trong lúc gửi: nút Gửi hiện spinner, khoá form.
3. Server trả về `ticket_code` (vd `INC-20261014-0001`) và cờ **gộp trùng** hay không:
   - Báo mới: Toast xanh "Đã gửi báo cáo. Mã sự cố: INC-…"
   - Bị gộp vào ticket có sẵn: hộp thoại thông tin (màu xanh dương): *"Sự cố tại phòng A201 đã có người báo cáo trước đó. Báo cáo của bạn đã được gộp vào mã INC-… (hiện có N người báo)."* (đây là điểm nhấn của tính năng chống trùng lặp, nên hiển thị rõ ràng).
4. Sau khi gửi thành công: reset form, tự chuyển sang tab "Báo cáo của tôi".
5. Lỗi mạng: giữ nguyên dữ liệu form, hiện nút "Thử lại".

### 5.2. Tab ② Báo cáo của tôi

Bảng các ticket mà người dùng đã báo, **tự cập nhật trạng thái real-time** khi Server push `STATUS_UPDATE (0x05)`.

~~~text
+----------------------------------------------------------------------------------+
| Bộ lọc: [Tất cả trạng thái ▾]   [Làm mới]                                        |
+------------------+--------+------------+-----+--------------+-----------+--------+
| Mã sự cố         | Phòng  | Loại       | Mức | Trạng thái   | Người xử lý| Giờ báo|
+------------------+--------+------------+-----+--------------+-----------+--------+
| INC-20261014-0001| A201   | Điện       | [4] | [Đã giao]    | A. Tuấn   | 08:15  |
| INC-20261014-0007| A105   | Thiết bị   | [2] | [Chờ ĐP]     | —         | 08:40  |
+------------------+--------+------------+-----+--------------+-----------+--------+
| Chi tiết (khi chọn 1 dòng): mô tả, ảnh, số người cùng báo, dòng thời gian xử lý   |
+----------------------------------------------------------------------------------+
~~~

- `TableView<TicketRow>` cột: `ticket_code`, `room_location`, `category_name`, `SeverityBadge`, `StatusBadge`, tên nhân viên xử lý (null → "—"), `created_at`.
- Panel chi tiết bên dưới (hoặc `SplitPane` dọc): mô tả, ảnh (nếu có), `report_count` ("5 người đã báo"), và **timeline** 4 mốc: *Đã báo → Đã giao → Đang xử lý → Hoàn thành* (mốc nào đạt thì tô xanh, kèm giờ lấy từ `created_at / dispatched_at / accepted_at / resolved_at`).
- Khi có `STATUS_UPDATE`: cập nhật dòng tương ứng + `ToastNotification` ("Sự cố INC-… đã được anh Tuấn tiếp nhận").
- Khi `RESOLVED`: hiện `resolution_note` trong chi tiết.

---

## 6. Màn hình 3: RESPONDER (Kỹ thuật / Y tế / Bảo vệ) – `ResponderView.fxml`

Menu trái: **① Nhiệm vụ của tôi** · **② Lịch sử xử lý**

**Top bar bổ sung:** công tắc trạng thái sẵn sàng + chỉ số tải.

~~~text
| ... Điều phối sự cố THPT | Anh Tuấn (Kỹ thuật)  [Trạng thái: ● SẴN SÀNG ▾]  Đang xử lý: 1/3  ● Heartbeat OK |
~~~

| `fx:id` | Control | Ghi chú |
| :--- | :--- | :--- |
| `cbAvailability` | `ComboBox` / `ToggleSwitch` | `AVAILABLE` ↔ `BUSY`. Giá trị này được gửi kèm trong gói UDP `HEARTBEAT\|staffId\|status` |
| `lblWorkload` | `Label` | Hiển thị `current_workload` (số sự cố đang xử lý) |
| `heartbeatIndicator` | Chấm nhấp nháy | Nhấp nháy xanh mỗi lần gửi nhịp tim thành công (mỗi 3 giây); đỏ nếu gửi lỗi |

### 6.1. Tab ① Nhiệm vụ của tôi

~~~text
+----------------------------------------------------------------------------------+
| NHIỆM VỤ ĐANG THỰC HIỆN                                                          |
| +------------------------------------------------------------------------------+ |
| | INC-20261014-0001                              [4 Cao]    [Đã giao]          | |
| | Phòng A201 · Sự cố Điện nguy hiểm · 2 người báo                              | |
| | "Chập cầu dao tổng có mùi khét tại phòng A201"                               | |
| | Đã giao lúc 08:15:02                                                         | |
| |            [ Xem chi tiết ]   [ BẮT ĐẦU XỬ LÝ ]                              | |
| +------------------------------------------------------------------------------+ |
| +------------------------------------------------------------------------------+ |
| | INC-20261014-0005                              [3 TB]     [Đang xử lý]       | |
| | ...                              [ Xem chi tiết ]   [ HOÀN THÀNH ]           | |
| +------------------------------------------------------------------------------+ |
|  (Nếu trống: biểu tượng + "Hiện chưa có nhiệm vụ nào. Bạn đang ở trạng thái sẵn sàng.")|
+----------------------------------------------------------------------------------+
~~~

- Danh sách dùng `ListView<TicketRow>` với `CellFactory` tuỳ chỉnh vẽ thành **card** như trên. Sắp xếp: severity giảm dần, rồi thời gian giao.
- Nút trên card đổi theo trạng thái:
  - `ASSIGNED` → nút **"Bắt đầu xử lý"** → gửi `STATUS_UPDATE` với `IN_PROGRESS`.
  - `IN_PROGRESS` → nút **"Hoàn thành"** → mở `ResolveDialog` (xem 8.3) nhập `resolution_note`, rồi gửi `RESOLVED`.
- "Xem chi tiết": mở panel/ dialog hiện đầy đủ mô tả, ảnh hiện trường (nhận kèm attachment), phòng, số người báo.

### 6.2. Popup nhận việc: `DispatchRequestDialog` (quan trọng nhất của Responder)

Hiện **ngay lập tức, luôn nằm trên cùng (`alwaysOnTop`)** khi nhận bản tin `0x03 DISPATCH_PUSH`, kèm âm báo "ting".

~~~text
+--------------------------------------------------+
|  🔔 CÓ SỰ CỐ MỚI CẦN XỬ LÝ                       |
|--------------------------------------------------|
|  Mã: INC-20261014-0009            [5 KHẨN CẤP]   |
|  Phòng: LAB_CHEM1                                |
|  Loại: Cháy nổ / Rò rỉ khí độc                   |
|  Số người báo: 3                                 |
|                                                  |
|  "Có khói trắng bốc ra từ tủ hoá chất"           |
|  [ ảnh thu nhỏ nếu có ]                          |
|                                                  |
|  Thời gian phản hồi còn lại:                     |
|  [██████████░░░░░░░░░░]  18 giây                 |
|                                                  |
|     [ TỪ CHỐI ]            [ NHẬN NHIỆM VỤ ]     |
+--------------------------------------------------+
~~~

- Modal (`Modality.APPLICATION_MODAL`), không có nút đóng (X), chỉ thoát bằng Nhận / Từ chối / Hết giờ.
- `CountdownBar` đếm ngược theo `Timeline`. Thời gian ACK timeout do Server quy định và gửi kèm trong `DISPATCH_PUSH` (field `ackTimeoutSec`, đề xuất mặc định **30 giây**). Còn ≤ 10 giây thì thanh chuyển đỏ.
- **Nhận:** gửi `0x04 ACK_TASK` với `ACCEPT`. Chờ phản hồi từ Server (Server xử lý race condition):
  - Thành công → đóng popup, thêm vào danh sách nhiệm vụ, Toast xanh.
  - Server trả `TASK_ALREADY_TAKEN` → đóng popup, Toast vàng "Nhiệm vụ đã được nhân viên khác tiếp nhận."
- **Từ chối:** gửi `ACK_TASK` với `REJECT` (có thể kèm lý do ngắn từ `ComboBox`: Đang bận / Ngoài chuyên môn / Khác).
- **Hết giờ:** tự đóng popup, gửi/không gửi gì (Server tự đánh `ACK_TIMEOUT`), Toast xám "Bạn đã bỏ lỡ nhiệm vụ INC-…".
- Nếu popup mới đến khi popup cũ đang mở → xếp hàng (`Queue<DispatchPush>`), hiện lần lượt.

### 6.3. Tab ② Lịch sử xử lý

`TableView` các nhiệm vụ đã qua (nguồn: `dispatch_logs` của chính user): Mã sự cố, Phòng, Loại, Trạng thái dispatch (`ACCEPTED / REJECTED / ACK_TIMEOUT / HEARTBEAT_LOST / COMPLETED` hiển thị tiếng Việt), Giờ giao, Giờ phản hồi, Ghi chú. Có bộ lọc theo trạng thái và khoảng ngày.

---

## 7. Màn hình 4: ADMIN (Ban Giám hiệu) – `AdminView.fxml`

Menu trái: **① Bảng điều khiển** · **② Nhân sự** · **③ Cảnh báo toàn trường** · **④ Thống kê**

### 7.1. Tab ① Bảng điều khiển (màn hình trung tâm, theo dõi real-time)

~~~text
+----------------------------------------------------------------------------------+
| +----------+ +----------+ +----------+ +----------+ +----------+                 |
| | CHỜ ĐP   | | ĐÃ GIAO  | | ĐANG XỬ  | | HOÀN     | | NV ONLINE|   <- 5 thẻ KPI  |
| |    3     | |    2     | |   LÝ  4  | | THÀNH 12 | |   4 / 4  |                 |
| +----------+ +----------+ +----------+ +----------+ +----------+                 |
|                                                                                  |
| Lọc: [Trạng thái ▾] [Loại ▾] [Mức ▾] [🔍 Tìm mã/phòng…]            [Làm mới]     |
| +-----+----------------+------+--------+-----+------+--------+--------+--------+ |
| | #   | Mã sự cố       |Phòng | Loại   | Mức |Ưu tiên|Trạng thái| NV xử lý|Chờ(s)| |
| +-----+----------------+------+--------+-----+------+--------+--------+--------+ |
| | 1   | INC-…-0009     |LAB1  | Cháy   | [5] | 7.2  |[Chờ ĐP]| —      | 12     | |
| | 2   | INC-…-0001     |A201  | Điện   | [4] | 4.5  |[Đã giao]| Tuấn  | 0      | |
| +-----+----------------+------+--------+-----+------+--------+--------+--------+ |
|                                                                                  |
| Chi tiết ticket đang chọn:                                                       |
| - Danh sách các báo cáo gộp (reporter, mô tả, ảnh)                               |
| - Lịch sử điều phối (dispatch_logs: lần 1, lần 2…, failover)                     |
| [ Giao thủ công… ]  [ Thu hồi & điều phối lại ]  [ Huỷ ticket ]  [ 🚨 Phát báo động ]|
+----------------------------------------------------------------------------------+
~~~

- **Thẻ KPI** (`KpiCard`): đếm số ticket theo trạng thái, cập nhật real-time.
- **Bảng ticket:** `TableView<TicketRow>`, cột `computed_priority` hiển thị 1 chữ số thập phân; **tự sắp xếp theo `computed_priority` giảm dần**. Cột "Chờ (s)" là thời gian chờ trong hàng đợi, **đếm tăng mỗi giây bằng `Timeline`** với ticket còn `PENDING`. Dòng có severity 5 tô nền đỏ nhạt. Dòng vừa mới đến nhấp nháy nền vàng nhạt 2 giây.
- **Panel chi tiết** (chọn 1 dòng): hai bảng con `reports` (từ `incident_reports`) và `dispatch history` (từ `dispatch_logs`: lần thử, nhân viên, trạng thái, `recovery_time_ms`). Dòng có `failover_count > 0` hiện biểu tượng ⚠ "Đã chuyển giao lỗi N lần".
- **Nút thao tác** (chỉ bật khi hợp lệ):
  - *Giao thủ công…* → `ManualAssignDialog` (8.4), cho ticket `PENDING`.
  - *Thu hồi & điều phối lại* → confirm, cho ticket `ASSIGNED/IN_PROGRESS`.
  - *Huỷ ticket* → confirm + nhập lý do, chuyển `CANCELLED`.
  - *🚨 Phát báo động* → mở `BroadcastAlertDialog` (8.5) đã điền sẵn `related_ticket_id`.

### 7.2. Tab ② Nhân sự

Giám sát toàn bộ Responder theo thời gian thực. Mỗi người là một **card** trong `FlowPane`.

~~~text
+--------------------------+  +--------------------------+  +--------------------------+
| ● Anh Hoàng Văn Tuấn     |  | ● Anh Ngô Bảo Nam        |  | ○ Cô Vũ Tuyết Mai        |
| Kỹ thuật                 |  | Kỹ thuật                 |  | Y tế                     |
| [SẴN SÀNG]               |  | [BẬN]                    |  | [OFFLINE]                |
| Tải: ▓▓░░░ 1             |  | Tải: ▓▓▓▓░ 2             |  | Mất kết nối 15s trước    |
| Heartbeat: 1s trước      |  | Heartbeat: 2s trước      |  | Heartbeat: --            |
| SĐT: 0903000001          |  | SĐT: 0903000002          |  | SĐT: 0903000003          |
+--------------------------+  +--------------------------+  +--------------------------+
~~~

- Chấm trạng thái: xanh = `AVAILABLE`, vàng = `BUSY`, xám = `OFFLINE`; đỏ nhấp nháy = vừa `HEARTBEAT_LOST`.
- Thanh tải theo `current_workload`.
- "Heartbeat: X giây trước" tự đếm bằng `Timeline` 1 giây (tính từ `last_heartbeat_at`). Quá 10 giây chuyển đỏ.
- Bộ lọc nhanh theo chuyên môn: *Tất cả / Kỹ thuật / Y tế / Bảo vệ*.
- Bấm vào card: xem danh sách nhiệm vụ nhân viên đó đang giữ.

### 7.3. Tab ③ Cảnh báo toàn trường

~~~text
+----------------------------------------------------------------------+
|  PHÁT CẢNH BÁO KHẨN CẤP (UDP MULTICAST 230.0.0.1:9999)               |
|  Mức cảnh báo *     (● Nguy hiểm) (○ Sơ tán) (○ Diễn tập) (○ An toàn)|
|  Liên quan sự cố    [ INC-20261014-0009 ▾ ] (tuỳ chọn)               |
|  Nội dung *         [______________________________________]         |
|                     Mẫu nhanh: [Cháy] [Sơ tán] [Khí độc] [Diễn tập]  |
|                                          [ 🚨 PHÁT BÁO ĐỘNG ]         |
|----------------------------------------------------------------------|
|  LỊCH SỬ CẢNH BÁO (bảng từ multicast_alerts)                         |
|  Thời gian | Mức | Nội dung | Ticket liên quan | Người phát          |
+----------------------------------------------------------------------+
~~~

- Nút "Phát báo động" màu đỏ, luôn hiện `Alert` xác nhận trước khi phát (tránh bấm nhầm): *"Cảnh báo sẽ hiện ngay lập tức trên TẤT CẢ máy trong trường. Xác nhận phát?"*
- Mức `EVACUATION` / `CRITICAL` dùng popup đỏ; `DRILL` popup cam kèm chữ "DIỄN TẬP"; `ALL_CLEAR` popup xanh "AN TOÀN".

### 7.4. Tab ④ Thống kê

Dùng `BarChart`, `LineChart`, `PieChart` của JavaFX:
- Số sự cố theo loại (PieChart).
- Số sự cố theo giờ trong ngày (LineChart).
- Thời gian chờ hàng đợi trung bình (`queue_waiting_time_ms`) theo mức severity (BarChart).
- Tỉ lệ gộp trùng lặp (`report_count > 1`) và số lần failover.
- Nút **"Xuất CSV"** xuất bảng ticket (phục vụ báo cáo thực nghiệm).

---

## 8. Popup / Dialog dùng chung

### 8.1. `EmergencyAlertDialog` (Multicast, hiện trên mọi role)

~~~text
+================================================+
||   🚨  CẢNH BÁO KHẨN CẤP TOÀN TRƯỜNG  🚨       ||   <- nền đỏ #D32F2F, chữ trắng
||------------------------------------------------||
||   Mức độ: SƠ TÁN                               ||
||                                                ||
||   Có cháy tại khu nhà A. Toàn bộ học sinh,     ||
||   giáo viên di chuyển ra sân trường ngay.      ||
||                                                ||
||   Phát lúc: 08:32:15  ·  Ban Giám hiệu         ||
||                                                ||
||                  [ ĐÃ HIỂU ]                   ||
+================================================+
~~~
- `Stage` riêng, `alwaysOnTop = true`, ở giữa màn hình, kích thước 640×360, nền nhấp nháy đỏ đậm/đỏ nhạt (animation 0.6 giây) trong 5 giây đầu rồi đứng yên.
- Phát âm thanh còi lặp lại cho tới khi bấm "Đã hiểu".
- Hiện **đè lên mọi cửa sổ và popup khác**, kể cả khi đang mở `DispatchRequestDialog`.
- Nhận từ luồng `MulticastListener` nền → `Platform.runLater(...)`.
- Chống hiện trùng: bỏ qua gói có cùng `alert_id` / nội dung + thời gian gần nhau (UDP có thể nhận lặp).

### 8.2. `ConfirmDialog` / `ToastNotification`
Dùng `Alert` chuẩn của JavaFX cho confirm; Toast tự viết bằng `Popup` hoặc `StackPane` overlay.

### 8.3. `ResolveDialog` (Responder hoàn thành nhiệm vụ)
- `TextArea txtResolutionNote` (bắt buộc, ≥ 5 ký tự): "Mô tả cách đã xử lý".
- Nút `[Huỷ]` `[Xác nhận hoàn thành]` → gửi `STATUS_UPDATE|ticket=...|status=RESOLVED|note=...`.

### 8.4. `ManualAssignDialog` (Admin)
- `TableView` chỉ liệt kê Responder **đúng chuyên môn** (theo `required_specialty` của ticket, `ALL` thì hiện tất cả), đang `is_online=1`; cột: Tên, Trạng thái, Tải hiện tại, Heartbeat.
- Mặc định tô nổi người có tải thấp nhất (gợi ý của hệ thống).
- Nút `[Giao việc]`.

### 8.5. `BroadcastAlertDialog` (Admin)
Phiên bản thu gọn của tab ③ (mức cảnh báo, nội dung, ticket liên quan) mở từ nút "Phát báo động" ở bảng điều khiển.

---

## 9. Ánh xạ UI ↔ Giao thức mạng

| Hành động trên UI | Kênh | Bản tin | Hướng |
| :--- | :--- | :--- | :--- |
| Đăng nhập | TCP | `0x01 LOGIN` | Client → Server |
| Gửi báo cáo + ảnh | TCP | `0x02 SUBMIT_INCIDENT` (+ attachment) | Client → Server |
| Hiện popup nhận việc | TCP | `0x03 DISPATCH_PUSH` | Server → Responder |
| Bấm Nhận / Từ chối | TCP | `0x04 ACK_TASK` | Responder → Server |
| Cập nhật trạng thái ticket (Bắt đầu, Hoàn thành) | TCP | `0x05 STATUS_UPDATE` | Hai chiều |
| Hiện lỗi / `TASK_ALREADY_TAKEN` | TCP | `0x06 ERROR_NOTIFY` | Server → Client |
| Nhịp tim 3 giây/lần | UDP Unicast | `HEARTBEAT\|staffId\|status` | Responder → Server |
| Popup báo động đỏ | UDP Multicast `230.0.0.1:9999` | Gói cảnh báo | Server → tất cả |

### 9.1. ⚠ Các bản tin còn THIẾU trong thiết kế hiện tại (cần nhóm backend chốt thêm)
UI ở trên cần thêm một số bản tin mà tài liệu giao thức chưa liệt kê. Đề xuất bổ sung (dùng mã `0x07+`):

| Mã đề xuất | Tên | Dùng cho |
| :--- | :--- | :--- |
| `0x07` | `LIST_TICKETS` / `TICKET_LIST` | Nạp danh sách ticket khi vào màn hình (Reporter: của mình, Admin: tất cả) |
| `0x08` | `LIST_STAFF` / `STAFF_LIST` | Tab Nhân sự của Admin |
| `0x09` | `ADMIN_ACTION` | Giao thủ công, thu hồi, huỷ ticket |
| `0x0A` | `BROADCAST_ALERT` | Admin yêu cầu Server phát Multicast |
| `0x0B` | `LOGOUT` | Đăng xuất |
| `0x0C` | `GET_CATEGORIES` | Nạp `incident_categories` cho ComboBox |
| `0x0D` | `STATS_REQUEST` | Dữ liệu tab Thống kê |

Ngoài ra, `DISPATCH_PUSH` nên chứa đủ các field UI cần: `ticketCode, room, categoryName, severity, reportCount, description, hasImage, ackTimeoutSec`.

---

## 10. Quy tắc đa luồng ở Client (BẮT BUỘC)

~~~text
[Network Reader Thread] --đọc DataInputStream--> parse Packet
        |
        +--> Platform.runLater(() -> controller.onPacket(packet))   <-- DUY NHẤT cách chạm UI
~~~

1. **Không bao giờ** chạm control JavaFX từ luồng khác `JavaFX Application Thread`.
2. Mọi tác vụ chậm (connect, gửi ảnh, đọc file, gọi mạng) chạy trong `Task<T>` / `ExecutorService`; UI chỉ hiện `ProgressIndicator`.
3. `SocketClientManager` (singleton) giữ socket + luồng đọc; phát sự kiện cho controller qua **listener/observer** (vd `interface PacketListener { void onPacket(Packet p); }`), controller đăng ký khi `initialize()` và gỡ khi đóng màn hình.
4. Dữ liệu bảng dùng `ObservableList<TicketRow>` + `Property` (`StringProperty`, `IntegerProperty`…) để `TableView` tự refresh khi dữ liệu đổi. **Khi update `ObservableList` phải ở FX thread.**
5. Các bộ đếm thời gian trên UI (đếm ngược, "Heartbeat X giây trước", "Chờ (s)") dùng `Timeline` (chạy trên FX thread), không dùng `Thread.sleep` + `Platform.runLater` lặp.
6. Luồng `HeartbeatSender` (`ScheduledExecutorService`, 3 giây/lần) và `MulticastListener` là daemon thread; **đóng sạch khi đăng xuất / tắt app** (`Stage.setOnCloseRequest`).

---

## 11. Cấu trúc thư mục phần Client

~~~text
client/
├── MainApp.java                         # extends Application
├── SceneManager.java                    # chuyển màn hình
├── SessionContext.java                  # user đang đăng nhập
├── network/
│   ├── SocketClientManager.java         # TCP + luồng đọc
│   ├── HeartbeatSender.java             # UDP 3s/lần
│   ├── MulticastListener.java           # nghe 230.0.0.1:9999
│   └── PacketListener.java
├── controller/
│   ├── LoginController.java
│   ├── ReporterController.java
│   ├── ResponderController.java
│   ├── AdminController.java
│   ├── DispatchRequestDialogController.java
│   ├── EmergencyAlertDialogController.java
│   └── ...
├── model/
│   ├── TicketRow.java                   # model có Property cho TableView
│   ├── StaffCard.java
│   └── IncidentCategory.java
├── ui/component/
│   ├── SeverityBadge.java
│   ├── StatusBadge.java
│   ├── ConnectionIndicator.java
│   ├── CountdownBar.java
│   ├── KpiCard.java
│   └── ToastNotification.java
└── resources/
    ├── fxml/
    │   ├── LoginView.fxml
    │   ├── ReporterView.fxml
    │   ├── ResponderView.fxml
    │   ├── AdminView.fxml
    │   └── dialog/ (DispatchRequestDialog, EmergencyAlertDialog, ResolveDialog,
    │                ManualAssignDialog, BroadcastAlertDialog).fxml
    ├── css/
    │   ├── theme.css                    # màu, font, nút, badge
    │   └── dashboard.css
    ├── images/                          # logo, icon
    └── sounds/                          # alert.wav, ding.wav
~~~

`TicketRow` (mô hình dữ liệu cho bảng), ánh xạ từ `incident_tickets` join `incident_categories` và `users`:

| Thuộc tính | Kiểu | Cột DB |
| :--- | :--- | :--- |
| `ticketId` | `IntegerProperty` | `ticket_id` |
| `ticketCode` | `StringProperty` | `ticket_code` |
| `roomLocation` | `StringProperty` | `room_location` |
| `categoryName` | `StringProperty` | `incident_categories.category_name` |
| `baseSeverity` | `IntegerProperty` | `base_severity` |
| `reportCount` | `IntegerProperty` | `report_count` |
| `computedPriority` | `DoubleProperty` | `computed_priority` |
| `status` | `StringProperty` | `status` |
| `assignedStaffName` | `StringProperty` | `users.full_name` (qua `assigned_staff_id`) |
| `createdAt` | `StringProperty` | `created_at` |
| `waitingSeconds` | `LongProperty` | tính từ `created_at` (chỉ khi `PENDING`) |

---

## 12. Quy tắc UX chung

- Mọi nút gửi dữ liệu phải **chống bấm đúp**: disable ngay sau khi bấm cho tới khi có phản hồi.
- Mọi lỗi hiển thị bằng **tiếng Việt, ngắn gọn, nói rõ cách xử lý**; không hiện stack trace cho người dùng (ghi log ra file).
- Phím tắt: `Enter` = xác nhận form chính, `Esc` = đóng dialog (trừ `DispatchRequestDialog` và `EmergencyAlertDialog`), `Ctrl+L` = đăng xuất.
- Thời gian hiển thị `HH:mm:ss` (hôm nay) hoặc `dd/MM HH:mm` (ngày khác); DB lưu ISO-8601 nên cần format lại ở tầng UI.
- Hỗ trợ độ phân giải tối thiểu 1024×640; các bảng có thanh cuộn, không cắt dữ liệu.
- Màu không là kênh thông tin duy nhất: badge luôn kèm chữ (ví dụ "Khẩn cấp"), không chỉ dùng màu.

---

## 13. Checklist nghiệm thu giao diện

**Login**
- [ ] Validate rỗng/sai port; lỗi hiển thị đúng; không đứng UI khi Server tắt
- [ ] Rẽ nhánh đúng 3 role

**Reporter**
- [ ] Chọn loại sự cố tự gợi ý severity mặc định
- [ ] Chọn/preview/xoá ảnh; chặn ảnh > 2 MB
- [ ] Gửi thành công hiện mã ticket; báo trùng hiện thông báo "đã gộp"
- [ ] Bảng "Báo cáo của tôi" tự đổi trạng thái khi Server push

**Responder**
- [ ] Heartbeat gửi đều 3 giây; đổi `AVAILABLE/BUSY` có hiệu lực
- [ ] Popup nhận việc có đếm ngược; xử lý `TASK_ALREADY_TAKEN`; xếp hàng nhiều popup
- [ ] Bắt đầu / Hoàn thành (kèm ghi chú) cập nhật đúng trạng thái

**Admin**
- [ ] KPI và bảng ticket cập nhật real-time, sắp xếp theo `computed_priority`
- [ ] Hiển thị chi tiết gộp báo cáo + lịch sử điều phối/failover
- [ ] Thẻ nhân sự đổi màu khi mất heartbeat > 10 giây
- [ ] Phát báo động có xác nhận; lịch sử hiển thị đúng

**Toàn hệ thống**
- [ ] Popup khẩn cấp Multicast hiện đồng loạt trên mọi client, đè lên mọi cửa sổ khác
- [ ] Không có thao tác UI nào từ luồng nền (kiểm tra không có `IllegalStateException: Not on FX application thread`)
- [ ] Đăng xuất / tắt app dừng sạch mọi luồng nền và socket
- [ ] Mất mạng giữa chừng: hiện banner, tự kết nối lại, không crash

---

## 14. Gợi ý thứ tự code (cho bên dev)

1. `theme.css` + component dùng chung (`SeverityBadge`, `StatusBadge`, `ConnectionIndicator`)
2. `SocketClientManager` + `LoginView` (có thể dùng Mock Server trả dữ liệu giả để dựng UI trước)
3. `ReporterView` (form + gửi ảnh) → bảng "Báo cáo của tôi"
4. `ResponderView` + `HeartbeatSender` + `DispatchRequestDialog`
5. `AdminView` (bảng điều khiển → nhân sự → cảnh báo → thống kê)
6. `MulticastListener` + `EmergencyAlertDialog`
7. Đánh bóng: Toast, âm thanh, animation, phím tắt, chạy checklist mục 13

> **Mẹo cho dev:** dựng toàn bộ UI với dữ liệu giả (`FXCollections.observableArrayList(...)`) trước, chốt giao diện xong mới nối mạng. Cách này giúp bên UI và bên Server làm song song, không chờ nhau.
