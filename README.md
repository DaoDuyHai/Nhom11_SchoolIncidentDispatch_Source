# HỆ THỐNG BÁO CÁO & ĐIỀU PHỐI SỰ CỐ TRƯỜNG HỌC
## SCHOOL INCIDENT DISPATCH SYSTEM (SIDS) - BASIC CORE ARCHITECTURE

> **Đề tài Môn học:** Lập trình Mạng (Network Programming)  
> **Nền tảng & Công nghệ:** Java 17, Maven Multi-Module, JavaFX GUI, Raw TCP Sockets, Length-Prefixed Binary Protocol (`0xCAFE`), SQLite WAL Mode, Salted SHA-256 Hashing.  
> **Triết lý thiết kế:** Tối giản cốt lõi, tường minh, chuẩn mực kỹ thuật, cách ly môi trường kiểm thử tự động và CSDL thực tế, sẵn sàng chạy ngay trên Apache NetBeans hoặc thông qua kịch bản lệnh Windows Batch.

---

## MỤC LỤC
1. [Mô hình Kiến trúc & Nguyên tắc Hoạt động](#1-mô-hình-kiến-trúc--nguyên-tắc-hoạt-động)
2. [Các Chức năng Nghiệp vụ Cốt lõi](#2-các-chức-năng-nghiệp-vụ-cốt-lõi)
3. [Giao thức Truyền thông Nhị phân (Binary Framing Protocol)](#3-giao-thức-truyền-thông-nhị-phân-binary-framing-protocol)
4. [Thiết kế Cơ sở Dữ liệu & Cơ chế Bảo mật](#4-thiết-kế-cơ-sở-dữ-liệu--cơ-chế-bảo-mật)
5. [Cấu trúc Thư mục Dự án (Maven Multi-Module)](#5-cấu-trúc-thư-mục-dự-án-maven-multi-module)
6. [Danh sách Tài khoản Cấp sẵn (50 Tài khoản Thực tế)](#6-danh-sách-tài-khoản-cấp-sẵn-50-tài-khoản-thực-tế)
7. [Hướng dẫn Biên dịch & Khởi chạy Hệ thống](#7-hướng-dẫn-biên-dịch--khởi-chạy-hệ-thống)
8. [Kịch bản Kiểm thử Nghiệp vụ Mẫu (E2E Test Scenarios)](#8-kịch-bản-kiểm-thử-nghiệp-vụ-mẫu-e2e-test-scenarios)
9. [Bộ Kiểm thử Tự động & Cách ly Dữ liệu (Automated Test Suite)](#9-bộ-kiểm-thử-tự-động--cách-ly-dữ-liệu-automated-test-suite)

---

## 1. MÔ HÌNH KIẾN TRÚC & NGUYÊN TẮC HOẠT ĐỘNG

Hệ thống được xây dựng theo mô hình **Client - Server tập trung** qua kết nối **TCP Socket 2 chiều (Duplex Streaming)**. Server đóng vai trò trung tâm điều hành kiêm giao diện quản trị toàn diện của Ban Giám Hiệu trường học.

```
                   ┌─────────────────────────────────────────────────────────┐
                   │             CENTRAL TCP SERVER & ADMIN CONSOLE          │
                   │  - Lắng nghe kết nối TCP Socket trên cổng 5000          │
                   │  - Quản lý phiên đa luồng (ExecutorService ThreadPool)  │
                   │  - Cấp phát tài khoản tập trung (Không đăng ký tự do)   │
                   │  - 🎯 Tự động định tuyến sự cố về đúng phòng ban        │
                   │  - Quản lý hàng đợi nhận việc Online & tích lũy Offline │
                   │  - 🔒 Xử lý nhận việc nguyên tử (Atomic Claiming)       │
                   │  - 💬 Relay tin nhắn 1-1 & Lưu vết kiểm toán sự cố      │
                   │  - 📊 Bộ lọc đa tiêu chí thời gian thực & Thống kê CSV  │
                   └───────────────────────────┬─────────────────────────────┘
                                               │
                                               │ (TCP Binary Protocol: 0xCAFE)
                                               │
                     ┌─────────────────────────┴─────────────────────────┐
                     ▼                                                   ▼
         ┌────────────────────────┐                          ┌────────────────────────┐
         │     REPORTER CLIENT    │         TCP Chat         │    RESPONDER CLIENT    │
         │  (Giáo viên / Học sinh)│ ◄──────────────────────► │ (Kỹ thuật/Y tế/Bảo vệ) │
         │ - Gửi báo cáo kèm ảnh  │    (Trực tiếp 1 - 1)     │ - Nhận ca đúng chuyên  │
         │ - Theo dõi trạng thái  │                          │ - Nhận việc Online/Off │
         │ - ❌ Hủy ca chờ xử lý  │                          │ - Chat hướng dẫn xử lý │
         │ - Chat Split-Pane 1-1  │                          │ - Hoàn tất & ghi chú   │
         └────────────────────────┘                          └────────────────────────┘
```

### 1.1. Server kiêm Admin Quản trị Tập trung
* **Quản trị độc quyền người dùng:** Nhà trường không cho phép tự đăng ký tài khoản tự do trên mạng nhằm bảo đảm tính kỷ luật và bảo mật học đường. Toàn bộ tài khoản do Ban Giám Hiệu (Admin) khởi tạo và phân quyền theo phòng ban.
* **Bộ điều phối sự cố tự động (Incident Dispatcher):** Phân tích danh mục sự cố và đẩy ngay lập tức đến các nhân sự chuyên trách, không gửi thông báo rác tới các phòng ban không liên quan.
* **Kiểm soát tính nhất quán & Lưu vết kiểm toán:** Toàn bộ tin nhắn chat, lịch sử tiếp nhận và thời gian hoàn thành đều được lưu vết đầy đủ trong CSDL SQLite để Ban Giám Hiệu kiểm tra lại bất cứ lúc nào.

### 1.2. Phân loại 3 Nhóm Vai trò Người dùng (RBAC)
1. **`ADMIN` (Ban Giám Hiệu / Quản trị viên Trung tâm):**
   * Quản lý danh sách người dùng toàn trường (Thêm mới, Đặt lại mật khẩu, Giám sát trạng thái Online/Offline).
   * Giám sát danh sách toàn bộ sự cố với bộ lọc thời gian thực: lọc theo từ khóa tìm kiếm, lọc theo trạng thái (`PENDING`, `IN_PROGRESS`, `RESOLVED`, `CANCELLED`), lọc theo phòng ban (`TECHNICAL`, `MEDICAL`, `SECURITY`).
   * Can thiệp điều phối thủ công (Re-assign ticket) cho nhân sự phụ trách khi cần thiết.
   * Xem thống kê đa chu kỳ (Hôm nay, Tháng này, Quý này, Năm nay, Toàn thời gian) và xuất dữ liệu ra file chuẩn định dạng **CSV (UTF-8 BOM)**.
2. **`REPORTER` (Giáo viên bộ môn, Giáo viên chủ nhiệm, Lớp trưởng, Cán bộ lớp):**
   * Khởi tạo báo cáo sự cố kèm phòng học, phân loại, mô tả chi tiết và hình ảnh hiện trường đính kèm.
   * Theo dõi danh sách sự cố đã gửi và tiến độ xử lý của nhà trường.
   * **Chức năng Hủy báo cáo (`❌ Hủy báo cáo`):** Cho phép người báo hủy các sự cố đang ở trạng thái `PENDING` nếu gửi nhầm hoặc sự cố đã tự khắc phục, có hộp thoại xác nhận an toàn.
   * Trao đổi trực tiếp 1-1 với nhân sự phụ trách tiếp nhận thông qua giao diện Chat Split-Pane chuyên nghiệp.
3. **`RESPONDER` (Nhân sự các phòng ban chuyên môn):**
   * **Phòng Kỹ thuật & Cơ sở vật chất (`TECHNICAL`):** Tiếp nhận sự cố hỏng quạt, bóng đèn, máy chiếu, chập điện, vỡ đường ống nước, hỏng bàn ghế, cửa kính.
   * **Phòng Y tế học đường (`MEDICAL`):** Tiếp nhận các ca sơ cứu học sinh ngất xỉu, chấn thương thể dục, sốt cao, cấp cứu học đường.
   * **Đội Bảo vệ & An ninh trật tự (`SECURITY`):** Tiếp nhận giải quyết các vụ việc xô xát, mất trật tự, người lạ đột nhập, mất cắp tài sản.
   * Xem hàng đợi sự cố chờ tiếp nhận của phòng ban mình, bấm **"Nhận việc"**, cập nhật trạng thái `IN_PROGRESS` $\rightarrow$ `RESOLVED` kèm ghi chú nghiệm thu.

---

## 2. CÁC CHỨC NĂNG NGHIỆP VỤ CỐT LÕI

### 2.1. Đăng nhập Tập trung & Quản lý Tài khoản Phân quyền
* Tài khoản được cấp bởi Admin với vai trò (`REPORTER`, `RESPONDER`, `ADMIN`) và chuyên môn phòng ban (`TECHNICAL`, `MEDICAL`, `SECURITY`, `NONE`, `ALL`).
* Mật khẩu được mã hóa an toàn bằng thuật toán **SHA-256 kết hợp Chuỗi Salt bí mật** (`SIDS_SALT_2026_SECURE_#$!`).
* Quản lý phiên đăng nhập chặt chẽ trên Socket TCP, tự động cập nhật cờ `is_online` khi kết nối hoặc ngắt kết nối.

### 2.2. Báo cáo Sự cố Hiện trường Kèm Hình ảnh
* Người báo nhập thông tin: Vị trí phòng học (ví dụ: `A201`, `Lab Hóa`, `Sân bóng`), Phân loại sự cố, Tiêu đề và Mô tả chi tiết.
* Hỗ trợ đính kèm ảnh chụp hiện trường (`.jpg`, `.png`). Dữ liệu ảnh được nén nhị phân đưa trực tiếp vào payload nhị phân của gói tin TCP và lưu trữ an toàn tại thư mục `uploads/` trên Server.

### 2.3. Tự động Định tuyến Phòng ban & Cơ chế Xử lý Online / Offline
* **Định tuyến chuyên biệt (Targeted Dispatching):** Khi sự cố mới được tạo, Server tra cứu danh mục và chỉ đẩy thông báo (`TYPE_DISPATCH_NOTIFY`) tới các tài khoản thuộc đúng phòng ban phụ trách:
  * Sự cố Điện nước, Quạt, Đèn, Máy chiếu $\rightarrow$ Gửi đến các nhân viên **Kỹ thuật (`TECHNICAL`)**.
  * Sự cố Cấp cứu, Chấn thương học sinh $\rightarrow$ Gửi đến nhân viên **Y tế (`MEDICAL`)**.
  * Sự cố Xô xát, Mất an ninh $\rightarrow$ Gửi đến nhân viên **Bảo vệ (`SECURITY`)**.
* **Tích lũy hàng đợi Offline (Offline Backlog Delivery):**
  * Nhân sự đang trực tuyến (`Online`): Nhận thông báo ngay lập tức trên màn hình.
  * Nhân sự đang ngoại tuyến (`Offline`): Sự cố được lưu trữ an toàn trong CSDL SQLite. Khi nhân sự bật ứng dụng đăng nhập, hệ thống tự động tải toàn bộ danh sách sự cố tồn đọng thuộc phòng ban của họ để tiếp nhận ngay.
* **Tiếp nhận Nguyên tử (Atomic Claiming):**
  * Sử dụng câu lệnh SQL điều kiện: `UPDATE incident_tickets SET assigned_staff_id=?, status='IN_PROGRESS' WHERE ticket_id=? AND status='PENDING' AND target_department=?`.
  * Tránh hoàn toàn tình trạng xung đột (Race Condition) khi hai nhân viên cùng nhấn "Nhận việc" tại một thời điểm.

### 2.4. Quyền Hủy Báo Cáo Chờ Xử Lý (Reporter Cancel Ticket)
* Người gửi báo cáo có toàn quyền hủy yêu cầu của mình nếu sự cố chưa được kỹ thuật viên nhận (`status = PENDING`).
* Nhấp nút `❌ Hủy`, ứng dụng hiển thị hộp thoại xác nhận: *"Bạn có chắc chắn muốn hủy sự cố này không?"*.
* Khi xác nhận, sự cố chuyển sang trạng thái `CANCELLED`, Server phát bản tin đồng bộ giải phóng sự cố khỏi hàng đợi của các nhân sự phòng ban.

### 2.5. Giao diện Chat Split-Pane 1-1 (Master-Detail Messenger)
* Thiết kế hiện đại dạng Split-Pane chia làm 2 cột:
  * **Cột trái (Sidebar):** Danh sách các phiên trò chuyện theo Ticket. Hiển thị mã sự cố, tiêu đề tóm tắt, tên đối tác trao đổi, huy hiệu trạng thái màu, chấm đỏ báo tin nhắn chưa đọc, ô tìm kiếm nhanh và nút lọc ca Đang xử lý / Đã đóng.
  * **Cột phải (Chat Area):** Khung chat dạng bong bóng tin nhắn (Bong bóng xanh bên phải cho người gửi, bong bóng xám bên trái cho đối phương), hiển thị thời gian gửi, tiêu đề sự cố tóm tắt ở đầu trang.
* **Khóa phiên tự động khi hoàn thành:** Khi sự cố chuyển sang trạng thái `RESOLVED`, phiên chat hiển thị thanh thông báo màu vàng `🔒 Sự cố đã hoàn tất. Phiên chat đã đóng.` và tự động vô hiệu hóa ô nhập văn bản để giữ nguyên vẹn nội dung làm bằng chứng nghiệm thu.
* **Lưu trữ kiểm toán cho Admin:** Mọi tin nhắn trao đổi được lưu trữ bền vững trong bảng `chat_messages`, Admin có thể xem lại toàn bộ lịch sử trao đổi bất kỳ lúc nào.

### 2.6. Bảng Điều khiển Giám sát & Báo cáo Thống kê Đa chu kỳ (Admin Oversight)
* **Bộ lọc đa tiêu chí thời gian thực (Real-time Filtering):** Kết hợp đồng thời 3 tiêu chí thông qua JavaFX `FilteredList` & `SortedList`:
  1. *Tìm kiếm văn bản:* Lọc tức thì theo Mã sự cố, Tiêu đề, Vị trí phòng học, Tên người báo hoặc Người xử lý.
  2. *Bộ lọc Trạng thái:* Tất cả, Chờ tiếp nhận (`PENDING`), Đang xử lý (`IN_PROGRESS`), Đã hoàn tất (`RESOLVED`), Đã hủy (`CANCELLED`).
  3. *Bộ lọc Phòng ban:* Tất cả, Kỹ thuật (`TECHNICAL`), Y tế (`MEDICAL`), An ninh (`SECURITY`).
* **Thống kê chuyên sâu đa chu kỳ:**
  * Các chu kỳ lọc: Hôm nay (`DAY`), Tháng này (`MONTH`), Quý này (`QUARTER`), Năm học này (`YEAR`), Tất cả (`ALL`).
  * Thống kê tỷ lệ hoàn thành, thời gian xử lý trung bình, biểu đồ cơ cấu sự cố theo phòng ban.
* **Xuất Báo cáo CSV Chuẩn Quốc tế:**
  * Bấm nút **"Xuất Báo Cáo (CSV)"**, hệ thống xuất danh sách sự cố tương ứng theo chu kỳ đã chọn.
  * Tích hợp mã Byte Order Mark **UTF-8 BOM (`\ufeff`)**, bảo đảm hiển thị tiếng Việt có dấu hoàn hảo trên Microsoft Excel, LibreOffice hoặc Google Sheets.

---

## 3. GIAO THỨC TRUYỀN THÔNG NHỊ PHÂN (BINARY FRAMING PROTOCOL)

Hệ thống sử dụng giao thức đóng khung nhị phân định độ dài (Length-Prefixed Framing Protocol) chạy trên nền TCP Socket thuần (Raw TCP Socket), loại bỏ hoàn toàn hiện tượng dính gói hoặc phân mảnh gói tin trong luồng stream mạng.

### 3.1. Cấu trúc Gói tin Nhị phân (Binary Packet Framing)
Mỗi bản tin trao đổi giữa Client và Server được đóng gói theo cấu trúc byte chuẩn mực như sau:

| Vị trí Byte | Kiểu dữ liệu | Tên trường | Ý nghĩa Kỹ thuật |
| :---: | :---: | :--- | :--- |
| **0 .. 1** | `short` (2 Bytes) | **Magic Header** | Giá trị định danh cố định: **`0xCAFE`** |
| **2** | `byte` (1 Byte) | **Message Type** | Mã loại bản tin (OpCode) |
| **3 .. 6** | `int` (4 Bytes) | **Header Length** | Độ dài chuỗi Header UTF-8 ($N$ bytes) |
| **7 .. $6+N$** | `byte[]` ($N$ Bytes) | **Header Payload** | Dữ liệu văn bản chứa các tham số (Command/JSON/Query string) |
| **$7+N$ .. $10+N$** | `int` (4 Bytes) | **Attach Length** | Độ dài khối nhị phân đính kèm ($M$ bytes, $= 0$ nếu không có) |
| **$11+N$ .. Cuối** | `byte[]` ($M$ Bytes) | **Attachment Bytes** | Dữ liệu đính kèm (Ảnh hiện trường `.jpg`/`.png` hoặc JSON mảng lớn) |

### 3.2. Bảng Mã Bản tin Hoạt động (Protocol OpCodes)

| OpCode | Tên Hằng số | Chiều truyền | Mô tả Nghiệp vụ |
| :---: | :--- | :---: | :--- |
| `0x01` | `TYPE_LOGIN` | Client $\rightarrow$ Server | Gửi thông tin đăng nhập (`username`, `password`) |
| `0x11` | `TYPE_LOGIN_RESP` | Server $\rightarrow$ Client | Trả lời đăng nhập kèm thông tin vai trò, họ tên, chuyên môn |
| `0x02` | `TYPE_SUBMIT_INCIDENT` | Reporter $\rightarrow$ Server | Gửi sự cố mới kèm ảnh chụp nhị phân hiện trường |
| `0x12` | `TYPE_SUBMIT_RESP` | Server $\rightarrow$ Reporter | Xác nhận tiếp nhận sự cố kèm Mã sự cố vừa tạo |
| `0x03` | `TYPE_DISPATCH_NOTIFY` | Server $\rightarrow$ Responder | Thông báo đẩy sự cố mới đến đúng phòng ban chuyên trách |
| `0x04` | `TYPE_CLAIM_TASK` | Responder $\rightarrow$ Server | Yêu cầu nhận xử lý sự cố (Atomic Claim) |
| `0x14` | `TYPE_CLAIM_RESP` | Server $\rightarrow$ Responder | Kết quả nhận việc (`OK` hoặc `ALREADY_TAKEN`) |
| `0x05` | `TYPE_STATUS_UPDATE` | Client $\rightarrow$ Server | Cập nhật trạng thái sự cố (`IN_PROGRESS`, `RESOLVED`, `CANCELLED`) |
| `0x15` | `TYPE_STATUS_BROADCAST`| Server $\rightarrow$ Clients | Phát thanh trạng thái sự cố tới các bên liên quan |
| `0x06` | `TYPE_LIST_TICKETS` | Client $\rightarrow$ Server | Truy vấn danh sách sự cố theo phạm vi (`MY`, `DEPARTMENT`, `ALL`) |
| `0x16` | `TYPE_LIST_TICKETS_RESP`| Server $\rightarrow$ Client | Trả về mảng JSON danh sách sự cố tương ứng |
| `0x07` | `TYPE_CHAT_SEND` | Client $\rightarrow$ Server | Gửi tin nhắn 1-1 cho một sự cố |
| `0x17` | `TYPE_CHAT_PUSH` | Server $\rightarrow$ Target Client| Đẩy tin nhắn tức thì tới người nhận đang online |
| `0x08` | `TYPE_CHAT_HISTORY` | Client $\rightarrow$ Server | Tải toàn bộ lịch sử tin nhắn của một sự cố |
| `0x18` | `TYPE_CHAT_HISTORY_RESP`| Server $\rightarrow$ Client | Trả về mảng JSON lịch sử tin nhắn phục vụ hiển thị |
| `0x09` | `TYPE_ADMIN_CREATE_USER`| Admin $\rightarrow$ Server | Admin tạo tài khoản người dùng mới |
| `0x19` | `TYPE_ADMIN_CREATE_RESP`| Server $\rightarrow$ Admin | Kết quả tạo tài khoản |
| `0x0A` | `TYPE_ADMIN_GET_USERS` | Admin $\rightarrow$ Server | Tải danh sách người dùng toàn trường |
| `0x1A` | `TYPE_ADMIN_GET_USERS_RESP`| Server $\rightarrow$ Admin | Trả về JSON danh sách người dùng toàn trường |
| `0x0B` | `TYPE_ADMIN_GET_STATS` | Admin $\rightarrow$ Server | Yêu cầu báo cáo thống kê theo chu kỳ thời gian |
| `0x1B` | `TYPE_ADMIN_GET_STATS_RESP`| Server $\rightarrow$ Admin | Trả về JSON tổng hợp số liệu thống kê & danh sách sự cố |
| `0x0E` | `TYPE_MANUAL_ASSIGN` | Admin $\rightarrow$ Server | Admin can thiệp điều phối chỉ định nhân viên xử lý |
| `0x1E` | `TYPE_MANUAL_ASSIGN_RESP`| Server $\rightarrow$ Admin | Kết quả phân công chỉ định |
| `0x0C` | `TYPE_LOGOUT` | Client $\rightarrow$ Server | Đăng xuất và đóng phiên an toàn |
| `0x0F` | `TYPE_ERROR` | Server $\rightarrow$ Client | Báo lỗi nghiệp vụ hoặc định dạng gói tin |

---

## 4. THIẾT KẾ CƠ SỞ DỮ LIỆU & CƠ CHẾ BẢO MẬT

Cơ sở dữ liệu được xây dựng trên nền **SQLite** với kiến trúc phân tách rõ ràng giữa cấu trúc bảng (DDL) và dữ liệu mẫu khởi tạo (DML).

### 4.1. Phân Tách DDL & DML Chuyên Nghiệp
* **`database/schema.sql` (Chỉ chứa DDL):**
  * Định nghĩa 4 bảng cốt lõi: `users`, `incident_categories`, `incident_tickets`, `chat_messages`.
  * Kích hoạt chế độ ghi nhật ký trước **WAL (`PRAGMA journal_mode = WAL;`)** giúp đọc và ghi đồng thời tốc độ cao, không khóa database.
  * Kích hoạt kiểm tra toàn vẹn khóa ngoại **Foreign Keys (`PRAGMA foreign_keys = ON;`)**.
  * Thiết lập các ràng buộc `CHECK` và chỉ mục tối ưu truy vấn: `idx_tickets_status`, `idx_tickets_reporter`, `idx_tickets_staff`, `idx_chat_ticket`.
* **`database/seed_data.sql` (Chỉ chứa DML):**
  * Nạp sẵn **5 danh mục sự cố cốt lõi** và **50 tài khoản người dùng thực tế**.
  * **100% để trống bảng `incident_tickets` và `chat_messages`**: Tạo điều kiện tốt nhất để người dùng và giảng viên tự tay thao tác kiểm thử từ đầu mà không bị lẫn lộn dữ liệu cũ.

### 4.2. Cơ chế Mã hóa Mật khẩu Salted SHA-256
Hệ thống áp dụng chuẩn băm bảo mật:
$$\text{PasswordHash} = \text{SHA-256}(\text{RawPassword} + \text{":"} + \text{SYSTEM\_SALT})$$
* **Chuỗi Salt bí mật:** `SIDS_SALT_2026_SECURE_#$!`
* **Cơ chế Legacy Auto-Upgrade:** Khi người dùng cũ đăng nhập với mật khẩu thô hoặc băm chưa có salt, hệ thống tự động nhận diện và cập nhật chuỗi hash salted vào CSDL ngay trong lần đăng nhập đầu tiên.

### 4.3. Kiến trúc Cách ly Môi trường Kiểm thử Tự động (Test Isolation)
Hệ thống phân tách rạch ròi 2 file CSDL vật lý:
1. **`database/incident_report.db` (CSDL Chính / Demo):**
   * Được ứng dụng chính (`Server.java`, `Main.java`) sử dụng khi chạy thực tế hoặc chấm điểm trên NetBeans.
   * Chứa 50 tài khoản sạch, 0 sự cố tồn dư.
2. **`database/test_incident_report.db` (CSDL Kiểm thử):**
   * Được các bộ kiểm thử tự động JUnit (`ChatMessageTest`, `TicketRoutingAndStatisticsTest`, v.v.) sử dụng.
   * Hàm `InitDatabase.resetDatabase()` tự động trỏ kết nối vào CSDL kiểm thử này. Mọi hành vi tạo dữ liệu giả lập, xóa bảng trong test case **tuyệt đối không bao giờ làm ảnh hưởng hay làm biến đổi CSDL chính**.

---

## 5. CẤU TRÚC THƯ MỤC DỰ ÁN (MAVEN MULTI-MODULE)

```
baitaplon/
├── config/
│   └── server.properties                       <-- Cấu hình Port TCP (5000), Timeout, MaxThreads
├── database/
│   ├── schema.sql                              <-- File DDL tạo bảng, khóa ngoại, chỉ mục
│   ├── seed_data.sql                           <-- File DML nạp 50 người dùng & 5 danh mục
│   ├── incident_report.db                      <-- File CSDL chính cho Demo thực tế
│   └── test_incident_report.db                 <-- File CSDL độc lập cho kiểm thử tự động
├── source/                                     <-- [MỞ THƯ MỤC NÀY TRONG NETBEANS]
│   ├── pom.xml                                 <-- Maven Parent POM (JDK 17 release cấu hình)
│   ├── common/                                 <-- Sub-module 1: Mô hình & Giao thức nhị phân
│   │   └── src/main/java/com/incidentreport/common/
│   │       ├── Constants.java                  <-- Hằng số Magic 0xCAFE, OpCodes, Roles
│   │       ├── model/                          <-- Các DTO: User, IncidentTicket, ChatMessage...
│   │       └── packet/Packet.java              <-- Bộ đóng gói/giải mã Length-Prefixed Binary
│   ├── service/                                <-- Sub-module 2: Tầng DAO & CSDL SQLite
│   │   └── src/main/java/com/incidentreport/service/
│   │       ├── DatabaseConnection.java         <-- Kết nối SQLite đa luồng WAL Mode
│   │       ├── InitDatabase.java               <-- Tự động khởi tạo DB từ schema & seed_data
│   │       ├── UserDAO.java                    <-- Đăng nhập, Salted SHA-256, Quản lý tài khoản
│   │       ├── TicketDAO.java                  <-- Tạo, Nhận việc nguyên tử, Thống kê chu kỳ
│   │       ├── ChatMessageDAO.java             <-- Lưu và đọc tin nhắn chat 1-1
│   │       └── CategoryDAO.java                <-- Định tuyến sự cố theo phòng ban
│   ├── server/                                 <-- Sub-module 3: TCP Server & Admin Daemon
│   │   └── src/main/java/com/incidentreport/server/
│   │       ├── Server.java                     <-- Điểm khởi chạy Server chính (Shift+F6)
│   │       ├── ClientHandler.java              <-- Xử lý luồng kết nối từng Client (RBAC)
│   │       └── ServerSessionManager.java       <-- Quản lý danh sách kết nối Socket Online
│   └── client/                                 <-- Sub-module 4: Ứng dụng Desktop JavaFX
│       └── src/main/java/com/incidentreport/client/
│           ├── IncidentReportApp.java          <-- Điểm khởi chạy giao diện chính (Shift+F6)
│           ├── Main.java                       <-- Lớp khởi chạy bổ trợ
│           ├── net/SocketClientManager.java    <-- Quản lý Socket nền & Luồng đọc gói tin TCP
│           └── ui/
│               ├── LoginView.java              <-- Màn hình Đăng nhập hiện đại
│               ├── ReporterView.java           <-- Giao diện Giáo viên/Học sinh (Báo sự cố & Hủy ca)
│               ├── ResponderView.java          <-- Giao diện Kỹ thuật/Y tế/Bảo vệ (Nhận việc)
│               ├── AdminView.java              <-- Bảng điều khiển Admin (Lọc tức thì & Xuất CSV)
│               └── ChatPanel.java              <-- Giao diện Chat Split-Pane 1-1 chuyên nghiệp
├── run_server.bat                              <-- Script chạy nhanh Server bằng 1 cú nhấp chuột
├── run_client.bat                              <-- Script chạy nhanh Client bằng 1 cú nhấp chuột
└── README.md                                   <-- TÀI LIỆU TOÀN DIỆN CỦA DỰ ÁN
```

---

## 6. DANH SÁCH TÀI KHOẢN CẤP SẴN (50 TÀI KHOẢN THỰC TẾ)

Tất cả 50 tài khoản đã được nạp sẵn vào CSDL với mật khẩu mặc định: **`123456`**. Dưới đây là các tài khoản tiêu biểu theo từng nhóm vai trò và phòng ban:

### 6.1. Ban Giám Hiệu / Quản trị viên Trung tâm (`ADMIN`)
| Tên Đăng Nhập | Mật Khẩu | Họ và Tên | Vai Trò | Phòng Ban | Nhiệm Vụ |
| :--- | :---: | :--- | :---: | :---: | :--- |
| **`admin_bgh`** | `123456` | Thầy Nguyễn Văn An | `ADMIN` | Ban Giám Hiệu | Hiệu trưởng - Quản trị tập trung toàn trường |
| **`admin_pht`** | `123456` | Cô Lê Thị Mai Hương | `ADMIN` | Ban Giám Hiệu | Phó Hiệu trưởng - Quản lý cơ sở vật chất |

### 6.2. Người Báo Cáo - Giáo Viên & Cán Bộ Lớp (`REPORTER`)
| Tên Đăng Nhập | Mật Khẩu | Họ và Tên | Vai Trò | Đối tượng Đại diện |
| :--- | :---: | :--- | :---: | :--- |
| **`gv_lan`** | `123456` | Cô Trần Thị Lan | `REPORTER` | Giáo viên chủ nhiệm lớp 10A2 |
| **`gv_hung`** | `123456` | Thầy Đỗ Quốc Hùng | `REPORTER` | Giáo viên Toán - GVCN 11A1 |
| **`gv_hoa`** | `123456` | Cô Phạm Quỳnh Hoa | `REPORTER` | Giáo viên Hóa học - Phụ trách Phòng Thí nghiệm |
| **`gv_duc`** | `123456` | Thầy Bùi Minh Đức | `REPORTER` | Giáo viên Tin học - Phụ trách Phòng máy 1 |
| **`hs_minh`** | `123456` | Lê Hoàng Minh | `REPORTER` | Lớp trưởng 10A2 |
| **`hs_anh`** | `123456` | Nguyễn Phương Anh | `REPORTER` | Bí thư Chi đoàn 10A2 |
| **`hs_khoa`** | `123456` | Trần Đăng Khoa | `REPORTER` | Lớp phó Lao động 11A1 |
| **`hs_trang`** | `123456` | Vũ Huyền Trang | `REPORTER` | Lớp trưởng 11B3 |

*(Ngoài ra còn có các giáo viên `gv_dung`, `gv_phuong`, `gv_thanh`, `gv_hang`, `gv_tuan`, `gv_quang`, `gv_yen`, `gv_linh` và học sinh `hs_nam`, `hs_vy`, `hs_dat`, `hs_nga`, `hs_long`, `hs_chi`, `hs_hoang`, `hs_thao`).*

### 6.3. Người Xử Lý - Phòng Kỹ Thuật & Thiết Bị (`RESPONDER` - `TECHNICAL`)
| Tên Đăng Nhập | Mật Khẩu | Họ và Tên | Chuyên Môn |
| :--- | :---: | :--- | :--- |
| **`kt_tuan`** | `123456` | Anh Hoàng Văn Tuấn | Kỹ thuật Điện 1 (Sửa chập điện, quạt, đèn) |
| **`kt_nam`** | `123456` | Anh Ngô Bảo Nam | Kỹ thuật Thiết bị (Máy chiếu, màn hình) |
| **`kt_dung`** | `123456` | Anh Phan Anh Dũng | Kỹ thuật Điện nước 2 (Đường ống nước, vòi nước) |
| **`kt_phong`** | `123456` | Anh Đặng Thanh Phong | Kỹ thuật Mạng & Máy tính phòng Lab |
| **`kt_son`** | `123456` | Anh Vũ Thái Sơn | Kỹ thuật mộc bàn ghế, cửa kính phòng học |

*(Cùng các nhân viên kỹ thuật: `kt_hai`, `kt_kien`, `kt_thang`, `kt_viet`, `kt_huy`).*

### 6.4. Người Xử Lý - Phòng Y Tế Học Đường (`RESPONDER` - `MEDICAL`)
| Tên Đăng Nhập | Mật Khẩu | Họ và Tên | Chuyên Môn |
| :--- | :---: | :--- | :--- |
| **`yte_mai`** | `123456` | Cô Vũ Tuyết Mai | Trưởng phòng Y tế học đường |
| **`yte_huong`** | `123456` | Cô Nguyễn Thu Hương | Bác sĩ học đường trực sơ cứu |
| **`yte_linh`** | `123456` | Cô Đỗ Thùy Linh | Điều dưỡng viên sơ cứu chấn thương |
| **`yte_thao`** | `123456` | Cô Phạm Thanh Thảo | Chuyên viên tư vấn tâm lý học sinh |

*(Cùng các nhân viên y tế: `yte_nhung`, `yte_tam`, `yte_ngoc`).*

### 6.5. Người Xử Lý - Đội Bảo Vệ & An Ninh (`RESPONDER` - `SECURITY`)
| Tên Đăng Nhập | Mật Khẩu | Họ và Tên | Chuyên Môn |
| :--- | :---: | :--- | :--- |
| **`bv_son`** | `123456` | Bác Trương Sơn | Đội trưởng Bảo vệ & An ninh trật tự |
| **`bv_duc`** | `123456` | Bác Hoàng Văn Đức | Bảo vệ trực Cổng chính A |
| **`bv_thanh`** | `123456` | Bác Đinh Công Thành | Bảo vệ trực Cổng phụ B |
| **`bv_hung`** | `123456` | Chú Vũ Mạnh Hùng | Tuần tra Nhà xe học sinh & Khuôn viên |
| **`bv_tien`** | `123456` | Chú Lê Minh Tiến | Giám sát Camera an ninh trung tâm |

*(Cùng các nhân viên bảo vệ: `bv_loi`, `bv_binh`).*

---

## 7. HƯỚNG DẪN BIÊN DỊCH & KHỞI CHẠY HỆ THỐNG

### Cách 1: Khởi Chạy Trên Apache NetBeans (Khuyên dùng cho Giảng viên / Chấm điểm)

Hệ thống được thiết kế hoàn toàn tương thích và tối ưu hóa để chạy trực tiếp trên **Apache NetBeans (từ bản 12 đến 20+)** với **JDK 17**:

1. **Mở dự án:**
   * Mở phần mềm NetBeans $\rightarrow$ Menu `File` $\rightarrow$ `Open Project...` (`Ctrl + Shift + O`).
   * Chọn thư mục:
     ```
     D:\Filecode\Laptrinhmang\baitaplon\source
     ```
   * Nhấn nút **Open Project**. NetBeans sẽ tự động nhận diện Parent Project `incident-report-system` và 4 sub-modules (`common`, `service`, `server`, `client`).
2. **Biên dịch dự án lần đầu:**
   * Nhấp chuột phải vào dự án gốc **`incident-report-system`** $\rightarrow$ Chọn **`Clean and Build`**.
   * Chờ vài giây cho đến khi khung Output hiển thị `BUILD SUCCESS`.
3. **Chạy Server (Admin):**
   * Mở rộng module **`server`** $\rightarrow$ `Source Packages` $\rightarrow$ `com.incidentreport.server`.
   * Nhấp chuột phải vào file **`Server.java`** $\rightarrow$ Chọn **`Run File`** (hoặc phím tắt **`Shift + F6`**).
   * Server sẽ khởi động và thông báo:
     ```
     ======================================================================
       HỆ THỐNG ĐIỀU PHỐI SỰ CỐ TRƯỜNG HỌC (SERVER TRUNG TÂM)
     ======================================================================
     ✅ [TCP SERVER] Đang lắng nghe kết nối trên cổng TCP 5000...
     ✅ Máy chủ sẵn sàng tiếp nhận điều phối sự cố!
     ```
4. **Chạy Client (Giao diện người dùng):**
   * Mở rộng module **`client`** $\rightarrow$ `Source Packages` $\rightarrow$ `com.incidentreport.client`.
   * Nhấp chuột phải vào file **`IncidentReportApp.java`** $\rightarrow$ Chọn **`Run File`** (**`Shift + F6`**).
   * Màn hình Đăng nhập JavaFX sẽ xuất hiện ngay lập tức.
   * *Mẹo kiểm thử thực tế:* Bạn có thể ấn **`Shift + F6`** nhiều lần trên `IncidentReportApp.java` để mở đồng thời 2 hoặc 3 cửa sổ Client cùng lúc (ví dụ: 1 cửa sổ giáo viên báo sự cố, 1 cửa sổ kỹ thuật viên nhận việc để trải nghiệm tính năng đẩy thông báo và chat thời gian thực).

---

### Cách 2: Khởi Chạy Nhanh Bằng Kịch Bản Windows Batch Script

Dự án cung cấp sẵn 2 kịch bản chạy nhanh ở thư mục gốc:

1. **Khởi động Server:** Nhấp đúp chuột vào file **`run_server.bat`**. Cửa sổ điều khiển Server sẽ khởi động ngay lập tức trên cổng 5000.
2. **Khởi động Client:** Nhấp đúp chuột vào file **`run_client.bat`** (có thể mở nhiều lần để chạy nhiều Client độc lập).

---

## 8. KỊCH BẢN KIỂM THỬ NGHIỆP VỤ MẪU (E2E TEST SCENARIOS)

Dưới đây là các kịch bản kiểm thử mẫu hoàn chỉnh, mô phỏng đúng các tình huống thực tế trong trường học:

### Kịch bản 1: Báo Sự Cố Điện $\rightarrow$ Tự Động Định Tuyến Phòng Kỹ Thuật $\rightarrow$ Nhận Việc & Chat 1-1
1. **Bước 1 (Gửi báo cáo):**
   * Mở Client 1 $\rightarrow$ Đăng nhập bằng tài khoản Giáo viên: `gv_lan` / `123456`.
   * Tại tab *"Báo sự cố mới"*:
     * Vị trí: `Phòng A201 (Tầng 2 nhà A)`.
     * Danh mục: `Sự cố Điện nguy hiểm (Chập cầu dao, Rò điện)`.
     * Tiêu đề: `Chập át-tô-mát góc cuối lớp`.
     * Mô tả: `Cầu dao bốc khói khét và phát tia lửa điện, lớp đã tắt thiết bị`.
     * Bấm nút **"Gửi Báo Cáo"**.
2. **Bước 2 (Server tự động đẩy đúng phòng ban chuyên trách):**
   * Server phân tích thấy sự cố thuộc chuyên môn `TECHNICAL` $\rightarrow$ Tự động đẩy bản tin tức thì đến nhân sự phòng Kỹ thuật đang online (`kt_tuan`, `kt_nam`), các tài khoản Y tế (`yte_mai`) hay Bảo vệ (`bv_son`) hoàn toàn không bị ảnh hưởng.
   * Mở Client 2 $\rightarrow$ Đăng nhập bằng kỹ thuật viên: `kt_tuan` / `123456`.
   * Tab *"Chờ tiếp nhận"* của `kt_tuan` hiển thị ngay sự cố của phòng `A201`.
   * `kt_tuan` bấm nút **"Nhận việc"** $\rightarrow$ Sự cố chuyển sang trạng thái `IN_PROGRESS` và gán cho `kt_tuan`.
3. **Bước 3 (Giao tiếp trực tiếp 1-1 qua Chat Split-Pane):**
   * Cả 2 màn hình Client (Cô Lan và Anh Tuấn) cùng chuyển sang tab **"Trò chuyện (Chat)"**.
   * Bên cột trái xuất hiện phiên chat tương ứng với sự cố phòng A201 kèm chấm đỏ thông báo tin nhắn mới.
   * Anh Tuấn nhắn: *"Em đang chuẩn bị đồ nghề lên tầng 2, cô ngắt át phụ phía ngoài giúp em trước nhé!"*.
   * Cô Lan nhắn phản hồi: *"Cô vừa ngắt át tổng hành lang rồi Tuấn nhé!"*.
   * Tin nhắn được đẩy tức thì qua luồng TCP, hiển thị dạng bong bóng tin nhắn Messenger chuyên nghiệp.
4. **Bước 4 (Nghiệm thu & Tự động khóa phiên chat):**
   * Anh Tuấn sửa xong, chuyển trạng thái sự cố sang **"Hoàn thành" (RESOLVED)** kèm ghi chú *"Đã thay át-tô-mát 32A mới an toàn"*.
   * Khung chat lập tức hiển thị thanh thông báo: `🔒 Sự cố đã hoàn tất. Phiên chat đã đóng.` và vô hiệu hóa ô nhập liệu để lưu trữ bằng chứng.

---

### Kịch bản 2: Người Báo Hủy Ca Chờ Xử Lý (Reporter Cancel Ticket)
1. Mở Client $\rightarrow$ Đăng nhập bằng học sinh: `hs_minh` / `123456`.
2. Tạo sự cố: Vị trí `A102`, Danh mục `Hỏng thiết bị lớp học`, Tiêu đề `Quạt trần số 2 không quay`.
3. Nhận ra học sinh lớp khác vừa cắm nhầm phích cắm phụ và quạt đã quay bình thường.
4. Tại danh sách sự cố đã gửi (trạng thái `PENDING`), bấm nút **`❌ Hủy`**.
5. Hộp thoại xác nhận xuất hiện: *"Bạn có chắc chắn muốn hủy sự cố này không?"* $\rightarrow$ Chọn **OK**.
6. Sự cố chuyển sang trạng thái `CANCELLED` và tự động biến mất khỏi hàng đợi tiếp nhận của phòng Kỹ thuật.

---

### Kịch bản 3: Ban Giám Hiệu (Admin) Lọc Đa Tiêu Chí & Xuất Báo Cáo CSV
1. Mở Client $\rightarrow$ Đăng nhập bằng tài khoản Hiệu trưởng: `admin_bgh` / `123456`.
2. Giao diện xuất hiện đầy đủ các tab quản trị cao cấp:
   * **Tab Giám sát sự cố:**
     * Nhập vào ô tìm kiếm: `A201` $\rightarrow$ Bảng tự động lọc tức thì sự cố tại phòng A201.
     * Chọn ComboBox trạng thái: `IN_PROGRESS` hoặc `RESOLVED` $\rightarrow$ Bảng phản hồi ngay lập tức.
     * Xem chi tiết sự cố và nút chỉ định thủ công (Re-assign) cho nhân sự khác khi ca bị chậm tiến độ.
   * **Tab Thống kê & Báo cáo:**
     * Chọn chu kỳ thời gian: `Hôm nay (DAY)`, `Tháng này (MONTH)`, `Năm nay (YEAR)`.
     * Xem tỷ lệ giải quyết sự cố và biểu đồ cơ cấu theo từng phòng ban.
     * Bấm nút **"Xuất Báo Cáo (CSV)"** $\rightarrow$ File CSV được tạo với chuẩn mã hóa UTF-8 BOM, mở trực tiếp trên Excel hiển thị tiếng Việt sắc nét.
   * **Tab Quản lý người dùng:**
     * Admin có thể cấp tài khoản mới hoặc đặt lại mật khẩu cho giáo viên, học sinh khi quên mật khẩu.

---

## 9. BỘ KIỂM THỬ TỰ ĐỘNG & CÁCH LY DỮ LIỆU (AUTOMATED TEST SUITE)

Dự án tích hợp đầy đủ hệ thống kiểm thử tự động (Unit Tests & Integration Tests) với JUnit 5:

| Tên Lớp Kiểm Thử | Vị trí Module | Phạm vi Kiểm thử |
| :--- | :--- | :--- |
| **`PacketTest`** | `common` | Kiểm thử đóng khung gói tin nhị phân, kiểm tra Magic Header `0xCAFE`, payload UTF-8 và khối nhị phân đính kèm. |
| **`ChatMessageTest`** | `service` | Kiểm thử lưu trữ tin nhắn chat 1-1, kiểm tra toàn vẹn Foreign Key và truy vấn kiểm toán cho Admin. |
| **`TicketRoutingAndStatisticsTest`** | `service` | Kiểm thử quy trình tạo sự cố, định tuyến phòng ban, nhận việc nguyên tử và thuật toán thống kê đa chu kỳ. |
| **`UserAndAdminManagementTest`** | `service` | Kiểm thử đăng nhập xác thực Salted SHA-256, chuyển trạng thái Online/Offline, Admin cấp phát tài khoản. |
| **`AuthAndAdminIntegrationTest`** | `server` | Kiểm thử tích hợp TCP Socket: luồng bắt tay, đăng nhập, phân quyền RBAC và quản lý phiên kết nối. |
| **`EndToEndDispatchIntegrationTest`** | `server` | Kiểm thử toàn trình (E2E): Gửi báo cáo $\rightarrow$ Server đẩy thông báo $\rightarrow$ Nhận việc $\rightarrow$ Chat 1-1 $\rightarrow$ Đóng sự cố. |

> [!NOTE]  
> Tất cả các kiểm thử tự động đều chạy biệt lập trên file **`database/test_incident_report.db`**, hoàn toàn **không can thiệp, không thêm/xóa** dữ liệu của file CSDL thực tế **`database/incident_report.db`**.

---

*Tài liệu được cập nhật toàn diện và đồng bộ 100% với mã nguồn hệ thống SIDS phiên bản mới nhất.*
