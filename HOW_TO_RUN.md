# 🚀 HƯỚNG DẪN CÀI ĐẶT VÀ CHẠY DỰ ÁN TỪ GITHUB
### HỆ THỐNG BÁO CÁO VÀ ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT THEO THỜI GIAN THỰC
*(High School Real-time Incident Reporting & Dispatch System)*

> **Dành cho:** Giảng viên, Hội đồng chấm bảo vệ hoặc bất kỳ ai vừa tải / clone dự án này từ GitHub về máy tính cá nhân.

---

## 1. Yêu cầu Môi trường (Cài đặt sẵn trên máy)

Dự án được thiết kế chuẩn mực, **không cần cài đặt phần mềm DBMS bên ngoài** (như MySQL, SQL Server) vì đã tích hợp sẵn CSDL SQLite dạng file nhúng:

* **Java JDK:** Phiên bản **JDK 17 trở lên** (Khuyến nghị Oracle JDK 17 hoặc OpenJDK 17 LTS).
* **Công cụ lập trình (IDE) - Chọn 1 trong các IDE sau:**
  * **Apache NetBeans (Khuyến nghị số 1 cho học phần này):** Bản 12.0, 17, 20 hoặc mới hơn.
  * **IntelliJ IDEA (Community hoặc Ultimate)**.
  * **Visual Studio Code** (đã cài Java Extension Pack).
* **Quản lý dự án:** Apache Maven (đã tích hợp sẵn trong cả NetBeans và IntelliJ, không cần cài riêng).

---

## 2. Tải Dự Án Về Máy Tính

### Cách A: Tải file nén .ZIP từ GitHub
1. Trên trang GitHub của dự án: Bấm vào nút màu xanh lá cây **`<> Code`** -> Chọn **`Download ZIP`**.
2. Giải nén file vừa tải vào một thư mục trên máy tính (ví dụ: `D:\SchoolIncidentDispatch\`).

### Cách B: Clone bằng Git
Mở Terminal / Git Bash / Command Prompt và chạy:
```bash
git clone https://github.com/DaoDuyHai/SchoolIncidentDispatch.git
```

---

## 3. Cách 1: Chạy bằng Apache NetBeans (Khuyến nghị số 1)

Dự án được thiết kế theo cấu trúc **Maven Multi-Module** chuẩn, NetBeans sẽ tự động nhận diện và nạp toàn bộ thư viện chỉ với 1 cú click chuột:

### Bước 1: Mở Dự Án trong NetBeans
1. Mở phần mềm **Apache NetBeans**.
2. Chọn menu: **File** -> **Open Project...** (hoặc bấm tổ hợp phím `Ctrl + Shift + O`).
3. Điều hướng vào thư mục dự án vừa tải về, chọn thư mục con mang tên **`source`** (nơi chứa file `pom.xml` gốc).
4. NetBeans sẽ nhận diện biểu tượng dự án Maven mang tên **`incident-report-system`**. Nhấn nút **Open Project**.
5. Trong cửa sổ **Projects** bên trái, bạn sẽ thấy dự án cha `incident-report-system` cùng 5 module con:
   * `common`
   * `service`
   * `server`
   * `client`
   * `simulator`

### Bước 2: Tự động tải thư viện (Clean and Build)
1. Nhấp chuột phải vào dự án cha **`incident-report-system`** -> Chọn **Clean and Build**.
2. NetBeans sẽ tự động tải các thư viện cần thiết (JavaFX 17, SQLite JDBC, Gson, JUnit 5) vào máy. Chờ khoảng 10–30 giây cho đến khi cửa sổ Output báo **`BUILD SUCCESS`**.
3. *(Tùy chọn)* Chuột phải vào `incident-report-system` -> Chọn **Test** (hoặc bấm `Alt + F6`) để kiểm tra toàn bộ 9 bài test tự động đều xanh lá 100%.

### Bước 3: Khởi động Server tích hợp Admin Dashboard (BẮT BUỘC CHẠY TRƯỚC)
> [!IMPORTANT]
> **Admin chính là Server!** Khi bạn khởi động Server, ứng dụng vừa mở các cổng mạng Socket (TCP 5000, UDP Heartbeat 5001), vừa hiển thị ngay lập tức cửa sổ **Giao diện Quản trị viên (Admin Server Dashboard)** trực quan để theo dõi toàn bộ hệ thống.

1. Trong cây thư mục **Projects** của NetBeans, mở rộng module:  
   `server` ➡️ `Source Packages` ➡️ `com.incidentreport.server` ➡️ tìm file **`Server.java`**.
2. Nhấp chuột phải vào file **`Server.java`** ➡️ Chọn **Run File** (hoặc bấm phím tắt **`Shift + F6`**).
3. Cửa sổ **Server Admin Dashboard** sẽ xuất hiện trên màn hình, đồng thời tab Output hiển thị:
   ```text
   ============================================================================
      HỆ THỐNG ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT - CENTRAL DISPATCH SERVER          
      Phiên bản: 2.0 (TCP Socket + UDP Heartbeat + Server Admin GUI)          
   ============================================================================
   ✅ Đã nạp danh mục sự cố từ CSDL thành công!
   ✅ [UDP HEARTBEAT] Đang lắng nghe nhịp tim trên cổng UDP 5001...
   ✅ [TCP SERVER] Đang lắng nghe kết nối trên cổng TCP 5000...
   ✅ [SERVER GUI] Đã khởi chạy Trung tâm Quản trị Điều hành Server!
   ```

### Bước 4: Khởi chạy Giao diện Đồ họa JavaFX (Client)
1. Trong cây thư mục **Projects**, mở rộng module:  
   `client` ➡️ `Source Packages` ➡️ `com.incidentreport.client` ➡️ tìm file **`Main.java`**.
2. Nhấp chuột phải vào file **`Main.java`** ➡️ Chọn **Run File** (hoặc bấm phím tắt **`Shift + F6`**).
   > **Lưu ý:** Luôn chạy từ file `Main.java` (thay vì `IncidentReportApp.java`). File này được thiết kế theo chuẩn JavaFX Launcher để tránh hoàn toàn lỗi *"JavaFX runtime components are missing"*.
3. Màn hình Đăng nhập hiện đại màu xanh Navy sẽ lập tức xuất hiện!
4. **Mở song song nhiều cửa sổ Client để Demo:**  
   Bạn tiếp tục nhấp chuột phải vào `Main.java` và chọn **Run File (Shift + F6)** thêm 1–2 lần nữa. NetBeans sẽ mở ra nhiều cửa sổ ứng dụng độc lập để bạn đăng nhập:
   * **Cửa sổ 1:** Đăng nhập Giáo viên / Học sinh (`gv_lan` / `hs_minh`) để gửi báo cáo.
   * **Cửa sổ 2:** Đăng nhập Kỹ thuật viên / Y tế (`kt_tuan` / `yte_mai`) để xem Hồ công việc mở và bấm tự nhận việc.

---

## 4. Cách 2: Chạy bằng IntelliJ IDEA

1. Mở IntelliJ IDEA ➡️ Chọn **Open** ➡️ Chọn thư mục **`source`**.
2. Chọn **Trust Project** và chờ IntelliJ đồng bộ Maven trong vài giây.
3. Chạy Server: Điều hướng đến `source/server/src/main/java/com/incidentreport/server/Server.java` ➡️ Nhấn icon **Play** màu xanh lá (hoặc `Ctrl + Shift + F10`).
4. Chạy Client: Điều hướng đến `source/client/src/main/java/com/incidentreport/client/Main.java` ➡️ Nhấn icon **Play** màu xanh lá. Để mở thêm cửa sổ, nhấn Play một lần nữa.

---

## 5. Cách 3: Chạy bằng Dòng lệnh (Terminal / Command Line)

Nếu bạn không muốn dùng IDE, bạn có thể chạy hoàn toàn bằng Terminal:

```bash
# Di chuyển vào thư mục source
cd source

# 1. Biên dịch toàn bộ hệ thống
mvn clean compile

# 2. Bật Server (Kênh TCP 5000 + UDP Heartbeat 5001 + Giao diện Admin)
mvn exec:java -pl server

# 3. Mở một cửa sổ Terminal mới để chạy Client JavaFX (Có thể mở nhiều cửa sổ)
mvn javafx:run -pl client
```

---

## 6. Danh Sách Tài Khoản Đăng Nhập Mẫu

Hệ thống đã nạp sẵn các tài khoản đại diện cho đầy đủ các bên liên quan trong trường học.  
**MẬT KHẨU MẶC ĐỊNH CHO TẤT CẢ TÀI KHOẢN:** **`123456`**

| Tên Đăng Nhập | Vai Trò (Role) | Chuyên Môn / Chức Danh | Họ và Tên | Mục Đích Thử Nghiệm |
| :--- | :--- | :--- | :--- | :--- |
| **`admin_bgh`** | **ADMIN** | Hiệu trưởng (Quản trị) | Thầy Nguyễn Văn An | Tích hợp trực tiếp trên Server Dashboard: Giám sát toàn trường, chỉ định việc |
| **`gv_lan`** | **REPORTER** | Giáo viên chủ nhiệm 10A2 | Cô Trần Thị Lan | Gửi báo cáo hỏng hóc kèm ảnh chụp hiện trường |
| **`hs_minh`** | **REPORTER** | Lớp trưởng 10A2 | Lê Hoàng Minh | Gửi báo cáo sự cố (dùng thử nghiệm kiểm tra gộp báo cáo trùng) |
| **`kt_tuan`** | **RESPONDER** | Kỹ thuật điện (TECHNICAL) | Anh Hoàng Văn Tuấn | Xem Hồ công việc mở, bấm Tự nhận việc (Claim Task), cập nhật Hoàn thành |
| **`kt_nam`** | **RESPONDER** | Kỹ thuật điện (TECHNICAL) | Anh Ngô Bảo Nam | Nhân viên kỹ thuật dự phòng (kiểm chứng chống Race Condition khi 2 người cùng nhận 1 việc) |
| **`yte_mai`** | **RESPONDER** | Y tế học đường (MEDICAL) | Cô Vũ Tuyết Mai | Nhận các ca cấp cứu, sơ cứu y tế học sinh |
| **`bv_son`** | **RESPONDER** | Bảo vệ trường (SECURITY) | Bác Trương Sơn | Nhận thông báo sự cố an ninh trật tự, người lạ đột nhập |

---

## 7. Kịch Bản Demo Nhanh Các Tính Năng Cốt Lõi (Nên Thử)

Để kiểm chứng toàn bộ các điểm ưu việt của đề tài, hãy mở **Server** và **2 cửa sổ Client** trên màn hình:

### 🔹 Kịch bản 1: Gửi báo cáo kèm ảnh & Tự chủ nhận việc (Self-Pick from Task Pool)
1. **Server Dashboard:** Đang hiển thị trực tiếp trên màn hình, theo dõi kết nối sống.
2. **Cửa sổ Client 1:** Đăng nhập giáo viên `gv_lan` (mật khẩu `123456`).
3. **Cửa sổ Client 2:** Đăng nhập kỹ thuật viên `kt_tuan` (mật khẩu `123456`).
4. Tại máy của `gv_lan`:
   * Nhập vị trí phòng: `A201`
   * Chọn loại sự cố: `Sự cố Điện nguy hiểm (Chập cầu dao, Rò điện)`
   * Mức độ: `4 - Khẩn cấp`
   * Bấm nút **"Chọn ảnh minh chứng"** để chọn một bức ảnh bất kỳ trên máy tính.
   * Bấm nút **"GỬI BÁO CÁO SỰ CỐ"**.
5. **Kết quả tức thì:** 
   * Trên máy anh Tuấn (`kt_tuan`): Mục **"Hồ công việc mở" (Open Task Pool)** lập tức xuất hiện thẻ sự cố phòng `A201` kèm điểm ưu tiên và ảnh chụp.
   * Anh Tuấn không bị Server ép nhận hay bị bắn popup dồn dập. Khi sẵn sàng, anh Tuấn chủ động bấm nút **"NHẬN VIỆC" (Pick/Claim Task)**.
   * Thẻ công việc chuyển sang danh sách "Nhiệm vụ đang xử lý" của Tuấn.
   * Tuấn bấm **"BẮT ĐẦU XỬ LÝ"** (`IN_PROGRESS`) và sau đó bấm **"HOÀN THÀNH"** (`RESOLVED`).
   * Trên Server Dashboard của Ban Giám hiệu, tiến trình được cập nhật thời gian thực!

### 🔹 Kịch bản 2: Kiểm chứng Chống bão báo cáo trùng lặp (Spatio-Temporal Deduplication)
1. Giữ nguyên sự cố phòng `A201` do `gv_lan` vừa báo.
2. Mở thêm 1 Client khác, đăng nhập học sinh `hs_minh`.
3. Cho `hs_minh` tiếp tục gửi một báo cáo cùng phòng `A201` với danh mục điện.
4. **Kết quả:** Hệ thống **không tạo sự cố mới**, không nhân đôi công việc mà tự động gộp vào sự cố gốc, số lượng người báo tăng lên `2`, và điểm ưu tiên được cộng thêm thưởng.

### 🔹 Kịch bản 3: Kiểm chứng Chống Race Condition (Atomic Claim)
1. Mở song song 2 Client Kỹ thuật: 1 máy của `kt_tuan` và 1 máy của `kt_nam`.
2. Có một sự cố mới xuất hiện trong Hồ công việc.
3. Cả 2 nhân viên cùng nhìn thấy sự cố và cùng bấm nút **"NHẬN VIỆC"** tại cùng một thời điểm:
   * Đúng **1 người** bấm trước (hoặc gói tin đến socket trước) nhận việc thành công (`ASSIGNED`).
   * Người thứ hai nhận thông báo: *"⚠️ Nhiệm vụ này vừa được đồng nghiệp tiếp nhận trước!"*.
   * Triệt tiêu hoàn toàn lỗi 2 người cùng tới xử lý 1 việc!

---

## 8. Chạy Bài Kiểm Tra Tải & Đo Đạc Thực Nghiệm (Benchmark Simulator)

Dự án có sẵn một công cụ giả lập bắn tải 50 luồng đồng thời để đo đạc thông lượng và kiểm chứng chống Race Condition:

* **Trên NetBeans:** Mở module `simulator` ➡️ `Source Packages` ➡️ chuột phải vào **`StressTestSimulator.java`** ➡️ Chọn **Run File (Shift + F6)**.
* **Trên Dòng lệnh:**
  ```bash
  cd source
  mvn exec:java -pl simulator -Dexec.mainClass="com.incidentreport.simulator.StressTestSimulator"
  ```
* **Kết quả đo đạc:**
  * Thông lượng: **~146 yêu cầu/giây**.
  * Độ trễ trung bình: **~99 mili-giây**.
  * Tỷ lệ khử trùng lặp (Deduplication Rate): **> 90%**.
  * Chống Race Condition: **100%** (Chỉ duy nhất 1 nhân sự nhận được việc, người thứ hai nhận mã `TASK_ALREADY_TAKEN`).
  * Toàn bộ dữ liệu được tự động lưu vào file `results/benchmark.csv`.

---

## 9. Các Vấn Đề Thường Gặp & Cách Xử Lý Nhanh (FAQ)

### ❓ 1. Lỗi: `Không thể kết nối Server: Connection refused: connect`
* **Nguyên nhân:** Bạn vừa bấm chạy `Main.java` (Client) trong khi `Server.java` (Server) chưa được bật.
* **Cách sửa:** Tắt cửa sổ Client, vào NetBeans chạy `Server.java` trước. Chờ Server Dashboard mở lên rồi mới bấm chạy lại `Main.java`.

### ❓ 2. Lỗi: `Address already in use: bind`
* **Nguyên nhân:** Cổng 5000 hoặc 5001 đang bị chiếm do một phiên bản Server cũ đang chạy ngầm chưa tắt.
* **Cách sửa:** Vào tab Output của NetBeans, tìm tiến trình Server cũ và bấm nút **Stop** màu đỏ (hình vuông). Hoặc mở Task Manager kết thúc các tiến trình Java đang chạy ngầm.

### ❓ 3. Lỗi: `JavaFX runtime components are missing`
* **Nguyên nhân:** Bấm chạy nhầm trực tiếp vào file `IncidentReportApp.java`.
* **Cách sửa:** Luôn luôn chạy file **`Main.java`**. File này là wrapper chuẩn giúp JavaFX chạy mượt mà trên mọi phiên bản JDK 17+.

### ❓ 4. Làm sao để khôi phục lại CSDL về trạng thái sạch sẽ ban đầu?
* Nếu bạn chạy thử nghiệm nhiều và muốn xóa sạch toàn bộ dữ liệu để demo lại từ đầu:
  1. Trong NetBeans, mở module `service` ➡️ `com.incidentreport.service` ➡️ chuột phải vào file **`InitDatabase.java`** ➡️ Chọn **Run File (Shift + F6)**.
  2. File SQLite `database/incident_report.db` sẽ được xóa sạch và nạp lại toàn bộ các bảng cùng dữ liệu hạt giống (Seed Data) chuẩn mực 100%!
