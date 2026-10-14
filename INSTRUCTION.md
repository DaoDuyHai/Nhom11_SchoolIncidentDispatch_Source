# HƯỚNG DẪN CÀI ĐẶT VÀ VẬN HÀNH HỆ THỐNG (INSTRUCTION & USER MANUAL)
### HỆ THỐNG BÁO CÁO VÀ ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT THEO THỜI GIAN THỰC
*(High School Real-time Incident Reporting & Dispatch System)*

---

## 1. Yêu cầu Môi trường (Prerequisites)

* **Hệ điều hành:** Windows 10/11, Linux (Ubuntu/Debian) hoặc macOS.
* **Java Development Kit (JDK):** OpenJDK / Oracle JDK **17** trở lên (Khuyến nghị JDK 17 LTS).
* **Quản lý dự án & Build Tool:** Apache Maven 3.8 trở lên.
* **Giao diện đồ họa:** Hỗ trợ JavaFX 17+ (Các thư viện JavaFX Controls và FXML đã được cấu hình tự động trong Maven).
* **Cơ sở dữ liệu:** SQLite 3 (Tự động nạp qua thư viện nhúng `sqlite-jdbc:3.42.0.0`, không cần cài đặt phần mềm DBMS bên ngoài).

---

## 2. Cấu trúc Thư mục Dự án

```text
baitaplon/
├── config/                 # File cấu hình server và định tuyến
├── database/               # CSDL SQLite và script SQL khởi tạo
│   ├── incident_report.db  # File SQLite DB (chế độ WAL)
│   ├── schema.sql          # Định nghĩa các bảng và dữ liệu mẫu
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
│   ├── server/             # TCP Server, Server Admin Dashboard, HeartbeatMonitor, SessionManager
│   ├── client/             # JavaFX GUI Client (Reporter View, Responder View)
│   └── simulator/          # Bộ đo tải StressTestSimulator (50+ luồng song song)
├── DESIGN_SchoolIncidentDispatch.md  # Đặc tả yêu cầu kỹ thuật chi tiết
├── UI_DESIGN_JavaFX.md     # Thiết kế giao diện JavaFX hiện đại
├── TOPICS.md               # Thông tin đề tài bài tập lớn
├── INSTRUCTION.md          # Tài liệu hướng dẫn này
├── HOW_TO_RUN.md           # Hướng dẫn chạy dự án chi tiết cho người mới tải về
└── README.md               # Báo cáo tổng quan dự án
```

---

## 3. Biên dịch và Kiểm thử Đơn vị Tự động

Mở Terminal / PowerShell tại thư mục `source/`:

```powershell
# 1. Biên dịch toàn bộ các module
mvn clean compile -DskipTests

# 2. Chạy toàn bộ bộ kiểm thử tự động (JUnit 5 Test Suite)
mvn test
```

> **Kết quả kỳ vọng:** Cả 3 module `common`, `service`, `server` đều vượt qua 100% các bài test (Binary Packet Roundtrip, Spatio-Temporal Deduplication, Atomic Claim Race Condition, Aging Priority Queue) mà không có lỗi (`BUILD SUCCESS`).

---

## 4. Danh sách Tài khoản Kiểm thử (Test Accounts)

Tất cả tài khoản mẫu đều có mật khẩu mặc định là: **`123456`**

| Tên đăng nhập | Vai trò | Chuyên môn / Vị trí | Họ và tên | Nơi đăng nhập / Mục đích |
| :--- | :--- | :--- | :--- | :--- |
| `admin_bgh` | **ADMIN** | Quản lý / Hiệu trưởng | Thầy Nguyễn Văn An | Tích hợp trực tiếp trên Server Dashboard: Giám sát toàn trường, chỉ định việc |
| `gv_lan` | **REPORTER** | Giáo viên chủ nhiệm 10A2 | Cô Trần Thị Lan | Đăng nhập Client: Gửi báo cáo kèm ảnh minh chứng |
| `hs_minh` | **REPORTER** | Học sinh / Lớp trưởng 10A2 | Lê Hoàng Minh | Đăng nhập Client: Gửi báo cáo thử nghiệm gộp trùng phòng |
| `kt_tuan` | **RESPONDER** | Kỹ thuật điện (TECHNICAL) | Anh Hoàng Văn Tuấn | Đăng nhập Client: Xem Task Pool, tự nhận việc (Claim Task) |
| `kt_nam` | **RESPONDER** | Kỹ thuật điện (TECHNICAL) | Anh Ngô Bảo Nam | Đăng nhập Client: Thử nghiệm đụng độ nhận việc Atomic Claim |
| `yte_mai` | **RESPONDER** | Y tế học đường (MEDICAL) | Cô Vũ Tuyết Mai | Đăng nhập Client: Nhận ca sơ cứu y tế học sinh |
| `bv_son` | **RESPONDER** | An ninh trật tự (SECURITY) | Bác Trương Sơn | Đăng nhập Client: Nhận việc an ninh trật tự |

---

## 5. Hướng dẫn Mở và Vận hành trên NetBeans IDE (Khuyến nghị)

Dự án được xây dựng theo chuẩn **Maven Multi-Module**, hoàn toàn tương thích và hỗ trợ tối đa trên **Apache NetBeans (từ NetBeans 12, 17, 20 đến các bản mới nhất)**:

### 5.1. Mở Dự án trên NetBeans
1. Mở Apache NetBeans.
2. Chọn menu **File** -> **Open Project...** (hoặc bấm tổ hợp phím `Ctrl + Shift + O`).
3. Điều hướng đến thư mục: `d:\Filecode\Laptrinhmang\baitaplon\source` (thư mục `source` chứa file `pom.xml` gốc).
4. NetBeans sẽ hiển thị biểu tượng Maven Project với tên: **`incident-report-system`** (Parent Project).
5. Nhấn nút **Open Project**.
6. Chuột phải vào project cha `incident-report-system` -> chọn **Clean and Build** để NetBeans đồng bộ toàn bộ dependencies.
7. Chuột phải vào `incident-report-system` -> chọn **Test** (hoặc bấm `Alt + F6`) để xác nhận toàn bộ 9 bài kiểm thử JUnit 5 đều vượt qua (thanh trạng thái màu xanh lá).

---

### 5.2. Chạy Central Dispatch Server tích hợp Admin Dashboard
> [!IMPORTANT]
> **Admin chính là Server!** Khi chạy `Server.java`, Server vừa mở các cổng Socket mạng vừa hiển thị ngay cửa sổ **Admin Server Dashboard** trực quan trên màn hình.

1. Trong cây thư mục `Projects` của NetBeans, mở rộng:  
   `server` -> `Source Packages` -> `com.incidentreport.server` -> tìm file **`Server.java`**.
2. Chuột phải vào file `Server.java` -> Chọn **Run File** (hoặc bấm phím tắt **`Shift + F6`**).
3. Cửa sổ **Server Admin Dashboard** sẽ xuất hiện, đồng thời cửa sổ Output hiển thị:
   ```text
   ✅ Đã nạp danh mục sự cố từ CSDL thành công!
   ✅ [TCP SERVER] Đang lắng nghe kết nối trên cổng TCP 5000...
   ✅ [UDP HEARTBEAT] Đang lắng nghe nhịp tim trên cổng UDP 5001...
   ✅ [SERVER GUI] Đã khởi chạy Trung tâm Quản trị Điều hành Server!
   ```

---

### 5.3. Khởi chạy Giao diện Đồ họa JavaFX Client trên NetBeans
1. Trong cây thư mục `Projects`, mở rộng:  
   `client` -> `Source Packages` -> `com.incidentreport.client` -> tìm file **`Main.java`**.
2. Chuột phải vào file `Main.java` -> Chọn **Run File** (hoặc bấm phím tắt **`Shift + F6`**).
3. Màn hình Đăng nhập hiện đại xuất hiện.
4. **Mở song song nhiều cửa sổ Client cùng lúc:**  
   Tiếp tục bấm **`Shift + F6`** trên file `Main.java` thêm 1–2 lần để mở song song các cửa sổ Client khác nhau:
   * **Cửa sổ 1:** Đăng nhập vai trò Reporter (`gv_lan`) để gửi báo cáo sự cố kèm ảnh.
   * **Cửa sổ 2:** Đăng nhập vai trò Responder (`kt_tuan`) để duyệt Hồ công việc mở và bấm tự nhận việc (Claim Task).

---

### 5.4. Chạy Thực nghiệm Đo tải Mạng (Simulator) trên NetBeans
1. Trong cây thư mục `Projects`, mở rộng:  
   `simulator` -> `Source Packages` -> `com.incidentreport.simulator` -> tìm file **`StressTestSimulator.java`**.
2. Chuột phải vào file `StressTestSimulator.java` -> Chọn **Run File** (hoặc bấm **`Shift + F6`**).
3. Quan sát kết quả đo tải 50 luồng đồng thời và kiểm chứng chống Race Condition hiển thị ngay tại cửa sổ **Output** của NetBeans. Kết quả tự động ghi nhận vào `results/benchmark.csv`.

---

## 6. Hướng dẫn Kịch bản Kiểm thử & Đánh giá Tính năng

1. **Thử nghiệm Báo cáo Sự cố kèm Ảnh (`gv_lan`):**
   * Điền phòng học `A201`, chọn loại sự cố `Sự cố Điện nguy hiểm`, chọn mức độ 4.
   * Chọn nút "Chọn ảnh minh chứng" để đính kèm 1 file ảnh JPG/PNG.
   * Ấn **"Gửi Báo Cáo Sự Cố"** -> Quan sát mã sự cố được sinh tự động và hiển thị trên bảng lịch sử cá nhân.
2. **Thử nghiệm Tự nhận việc từ Hồ công việc (Self-Pick Task Pool - `kt_tuan`):**
   * Mở cửa sổ Responder của anh Tuấn (`kt_tuan`).
   * Mục **Hồ công việc mở (Open Task Pool)** tự động hiển thị sự cố phòng `A201` vừa báo với đầy đủ điểm ưu tiên và nút xem ảnh hiện trường.
   * Anh Tuấn chủ động bấm **"NHẬN VIỆC"** (Claim Task).
   * Sự cố chuyển thành công sang danh sách nhiệm vụ đang xử lý của Tuấn. Tuấn bấm **"BẮT ĐẦU XỬ LÝ"** (`IN_PROGRESS`) và **"HOÀN THÀNH"** (`RESOLVED`).
3. **Thử nghiệm Chống đụng độ Race Condition (Atomic Claim):**
   * Mở 2 cửa sổ Responder của `kt_tuan` và `kt_nam`.
   * Khi có 1 sự cố mở, cả 2 cùng bấm nút "Nhận việc".
   * Đúng 1 người nhận được việc, người còn lại nhận thông báo *"Nhiệm vụ này vừa được đồng nghiệp tiếp nhận trước"*.
4. **Theo dõi Giám sát trên Server Admin Dashboard:**
   * Ngay trên màn hình Server, Ban Giám hiệu theo dõi danh sách IP, cổng của các máy đang kết nối socket.
   * Điểm ưu tiên động trong hàng đợi Aging tăng dần theo thời gian thực nếu sự cố phải chờ lâu.
   * Có thể sử dụng chức năng "Chỉ định thủ công" (Manual Assign) để giao việc đích danh khi cần thiết.
