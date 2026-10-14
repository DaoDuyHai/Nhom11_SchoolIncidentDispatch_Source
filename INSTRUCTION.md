# HƯỚNG DẪN CÀI ĐẶT VÀ VẬN HÀNH HỆ THỐNG (INSTRUCTION & USER MANUAL)
**Hệ thống Báo cáo và Điều phối Sự cố Trường THPT (School Incident Dispatch System)**

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
│   ├── schema.sql          # Định nghĩa 6 bảng và dữ liệu mẫu
│   └── seed_data.sql       # Script nạp dữ liệu mẫu
├── docs/                   # Tài liệu kỹ thuật chi tiết
│   ├── ARCHITECTURE.md     # Đặc tả kiến trúc đa luồng, deduplication, aging queue
│   └── PROTOCOL.md         # Đặc tả giao thức đóng khung nhị phân 0xCAFE
├── results/                # Kết quả thực nghiệm đo đạc hiệu năng
│   └── benchmark.csv       # File CSV lưu kết quả đo tải và đụng độ
├── source/                 # Mã nguồn đa module Maven
│   ├── pom.xml             # Parent POM quản lý 5 modules
│   ├── common/             # Models, Constants, Packet (0xCAFE Framing)
│   ├── service/            # DAOs, CSDL Connection Pool, ReportService, AuthService
│   ├── server/             # TCP Server, DispatchWorker, HeartbeatMonitor, SessionManager
│   ├── client/             # JavaFX GUI Client (Reporter, Responder, Admin Views)
│   └── simulator/          # Bộ đo tải StressTestSimulator (50+ luồng song song)
├── statics/                # Dữ liệu hình ảnh và sơ đồ
├── DESIGN_SchoolIncidentDispatch.md  # Đặc tả yêu cầu kỹ thuật chi tiết
├── UI_DESIGN_JavaFX.md     # Thiết kế giao diện JavaFX hiện đại
├── TOPICS.md               # Thông tin đề tài bài tập lớn
├── INSTRUCTION.md          # Tài liệu hướng dẫn này
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

> **Kết quả kỳ vọng:** Cả 3 module `common`, `service`, `server` đều vượt qua 100% các bài test (Binary Packet Roundtrip, Spatio-Temporal Deduplication, Atomic Claim Race Condition, Aging Priority Queue) mà không có lỗi.

---

## 4. Danh sách Tài khoản Kiểm thử (Test Accounts)

Tất cả tài khoản mẫu đều có mật khẩu mặc định là: **`123456`**

| Tên đăng nhập | Vai trò | Chuyên môn / Vị trí | Họ và tên |
| :--- | :--- | :--- | :--- |
| `admin_bgh` | **ADMIN** | Quản lý / Hiệu trưởng | Thầy Nguyễn Văn An |
| `gv_lan` | **REPORTER** | Giáo viên chủ nhiệm 10A2 | Cô Trần Thị Lan |
| `hs_minh` | **REPORTER** | Học sinh / Lớp trưởng 10A2 | Lê Hoàng Minh |
| `kt_tuan` | **RESPONDER** | Kỹ thuật điện (TECHNICAL) | Anh Hoàng Văn Tuấn |
| `kt_nam` | **RESPONDER** | Kỹ thuật điện (TECHNICAL) | Anh Ngô Bảo Nam |
| `yte_mai` | **RESPONDER** | Y tế học đường (MEDICAL) | Cô Vũ Tuyết Mai |
| `bv_son` | **RESPONDER** | An ninh trật tự (SECURITY) | Bác Trương Sơn |

---

## 5. Hướng dẫn Mở và Vận hành trên NetBeans IDE (Khuyến nghị)

Dự án được xây dựng theo chuẩn **Maven Multi-Module**, hoàn toàn tương thích và hỗ trợ tối đa trên **Apache NetBeans (từ NetBeans 12, 17, 20 đến các bản mới nhất)**:

### 5.1. Mở Dự án trên NetBeans
1. Mở Apache NetBeans.
2. Chọn menu **File** -> **Open Project...** (hoặc bấm tổ hợp phím `Ctrl + Shift + O`).
3. Điều hướng đến thư mục: `d:\Filecode\Laptrinhmang\baitaplon\source` (thư mục `source` chứa file `pom.xml` gốc).
4. NetBeans sẽ hiển thị biểu tượng Maven Project với tên:
   * **`incident-report-system`** (Parent Project)
   * Bên trong nhánh con (Modules) gồm 5 dự án thành phần:
     * `common`
     * `service`
     * `server`
     * `client`
     * `simulator`
5. Nhấn nút **Open Project**.
6. Chuột phải vào project cha `incident-report-system` -> chọn **Clean and Build** để NetBeans đồng bộ toàn bộ dependencies.
7. Chuột phải vào `incident-report-system` -> chọn **Test** (hoặc bấm `Alt + F6`) để xác nhận toàn bộ 9 bài kiểm thử JUnit 5 đều vượt qua (thanh trạng thái màu xanh lá).

---

### 5.2. Chạy Central Dispatch Server trên NetBeans
* **Cách nhanh nhất (Khuyến nghị):**
  1. Trong cây thư mục `Projects` của NetBeans, mở rộng:
     `server` -> `Source Packages` -> `com.incidentreport.server` -> tìm file **`Server.java`**.
  2. Chuột phải vào file `Server.java` -> Chọn **Run File** (hoặc bấm phím tắt **`Shift + F6`**).
* **Cách 2:**
  * Chuột phải trực tiếp vào module `server` trên cây Projects -> Chọn **Run** (NetBeans sẽ tự động kích hoạt `com.incidentreport.server.Server` đã cấu hình sẵn trong `pom.xml`).
* Cửa sổ **Output** của NetBeans sẽ hiển thị:
  ```text
  ✅ [TCP SERVER] Đang lắng nghe kết nối trên cổng TCP 5000...
  ✅ [UDP HEARTBEAT] Đang lắng nghe nhịp tim trên cổng UDP 5001...
  ✅ [UDP MULTICAST] Sẵn sàng phát cảnh báo tới nhóm 230.0.0.1:9999
  ✅ [DISPATCH WORKER] Đã khởi động luồng điều phối thông minh!
  ```

---

### 5.3. Khởi chạy Giao diện Đồ họa JavaFX Client trên NetBeans
* **Cách thực hiện:**
  1. Trong cây thư mục `Projects`, mở rộng:
     `client` -> `Source Packages` -> `com.incidentreport.client` -> tìm file **`Main.java`**.
  2. Chuột phải vào file `Main.java` -> Chọn **Run File** (hoặc bấm phím tắt **`Shift + F6`**).
  > **Lưu ý kỹ thuật:** File `Main.java` được thiết kế riêng biệt để khởi chạy `IncidentReportApp.main(args)`. Cơ chế này giúp NetBeans vượt qua kiểm tra mô-đun JavaFX khắt khe của JDK 17+, **không bao giờ gặp lỗi "JavaFX runtime components are missing"**.
* **Mở nhiều cửa sổ Client cùng lúc:**
  * Tiếp tục bấm **`Shift + F6`** trên file `Main.java` thêm 2–3 lần để mở song song nhiều cửa sổ Client khác nhau phục vụ kịch bản kiểm thử:
    * Cửa sổ 1: Đăng nhập vai trò Reporter (`gv_lan` / `hs_minh`) để gửi báo cáo kèm ảnh.
    * Cửa sổ 2: Đăng nhập vai trò Responder (`kt_tuan` / `kt_nam`) để nhận nhiệm vụ tự động đẩy về (Server Push) và cập nhật tiến độ.
    * Cửa sổ 3: Đăng nhập vai trò Admin (`admin_bgh`) để giám sát toàn trường và phát cảnh báo khẩn cấp Multicast.

---

### 5.4. Chạy Thực nghiệm Đo tải Mạng (Simulator) trên NetBeans
1. Trong cây thư mục `Projects`, mở rộng:
   `simulator` -> `Source Packages` -> `com.incidentreport.simulator` -> tìm file **`StressTestSimulator.java`**.
2. Chuột phải vào file `StressTestSimulator.java` -> Chọn **Run File** (hoặc bấm **`Shift + F6`**).
3. Quan sát kết quả đo tải 50 luồng đồng thời và kiểm chứng chống Race Condition hiển thị ngay tại cửa sổ **Output** của NetBeans. Kết quả tự động ghi nhận vào `results/benchmark.csv`.

---

## 6. Hướng dẫn Chạy bằng Dòng lệnh (Terminal / PowerShell Alternative)

**Thử nghiệm các vai trò:**
1. **Đăng nhập với vai trò Reporter (`gv_lan`):**
   * Điền phòng học `A201`, chọn loại sự cố `Sự cố Điện nguy hiểm`, chọn mức độ 4.
   * Chọn nút "Chọn ảnh minh chứng" để đính kèm 1 file ảnh.
   * Ấn **"Gửi Báo Cáo Sự Cố"** -> Quan sát mã sự cố được sinh tự động và hiển thị trên bảng lịch sử.
2. **Đăng nhập với vai trò Responder (`kt_tuan`):**
   * Mở song song một cửa sổ Client thứ hai.
   * Ngay khi Reporter gửi sự cố điện, màn hình Responder lập tức bật Popup màu xanh thông báo nhiệm vụ kèm ảnh hiện trường.
   * Bấm **"Chấp Nhận Nhiệm Vụ"** -> Sự cố chuyển sang trạng thái `ASSIGNED`.
   * Cập nhật trạng thái sang `IN_PROGRESS` và `RESOLVED` khi hoàn tất.
3. **Đăng nhập với vai trò Admin (`admin_bgh`):**
   * Mở một cửa sổ Client thứ ba.
   * Bảng Dashboard hiển thị đầy đủ toàn bộ sự cố, trạng thái nhân sự và tải công việc.
   * Nhập thông điệp vào ô "Phát cảnh báo khẩn cấp" và ấn **"Phát Cảnh Báo Multicast"** -> Toàn bộ các Client đang mở đều đồng loạt bật Popup cảnh báo thảm họa màu đỏ.

---

### Bước 3: Chạy Thực nghiệm Đo tải Mạng (Stress Test Simulator)
Chương trình mô phỏng bắn tải đồng thời 50 kết nối Client và kiểm chứng chống Race Condition:

```powershell
java -cp "source/simulator/target/classes;source/common/target/classes;C:/Users/Admin/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar" com.incidentreport.simulator.StressTestSimulator
```

> **Kết quả xuất ra:**
> * Số yêu cầu thành công: **50 / 50 (100%)**
> * Tỷ lệ gom nhóm trùng (Deduplication): **> 90%**
> * Throughput: **~146 req/s**
> * Độ trễ trung bình: **~99 ms**
> * Kiểm chứng Atomic Claim: **1 người nhận thành công, 1 người nhận TASK_ALREADY_TAKEN** (0% tranh chấp trùng).
> * Dữ liệu tự động cập nhật vào `results/benchmark.csv`.

