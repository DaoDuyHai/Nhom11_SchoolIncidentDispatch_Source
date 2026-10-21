# HỆ THỐNG BÁO CÁO & ĐIỀU PHỐI SỰ CỐ TRƯỜNG HỌC
## (SCHOOL INCIDENT DISPATCH SYSTEM - PHIÊN BẢN CỐT LÕI / BASIC CORE)

> **Mục tiêu phiên bản:** Tập trung vào các nghiệp vụ cốt lõi và thiết thực nhất của hệ thống mạng Client - Server: **Báo cáo sự cố $\rightarrow$ Tự động đẩy đúng tài khoản của phòng ban chuyên trách $\rightarrow$ Nhận việc (Online/Offline) $\rightarrow$ Chat 1-1 giữa người báo & người nhận (tự dọn dẹp khi xong) $\rightarrow$ Thống kê & Xuất báo cáo đa chiều $\rightarrow$ Quản trị tập trung tại Server (Admin).**  
> Loại bỏ các thuật toán phức tạp chưa cần thiết (chống đói, chống trùng lặp, heartbeat rớt mạng) để hệ thống hoạt động ổn định, dễ hiểu, dễ demo trên NetBeans và phát triển từng bước.

---

## 1. MÔ HÌNH KIẾN TRÚC & NGUYÊN TẮC THIẾT KẾ

```
                   ┌──────────────────────────────────────────────┐
                   │           SERVER (KIÊM ADMIN HỆ THỐNG)       │
                   │  - Tiếp nhận kết nối Socket từ các Client    │
                   │  - Là người DUY NHẤT tạo & cấp tài khoản     │
                   │  - 🎯 TỰ ĐỘNG ĐẨY ĐÚNG TÀI KHOẢN PHÒNG BAN   │
                   │  - Lưu trữ tin nhắn để Admin xem lại/giám sát│
                   │  - Lưu hàng đợi sự cố cho nhân sự Offline    │
                   │  - 📊 THỐNG KÊ & XUẤT BÁO CÁO ĐA CHU KỲ      │
                   └──────────────────────┬───────────────────────┘
                                          │
                     ┌────────────────────┴────────────────────┐
                     ▼                                         ▼
         ┌────────────────────────┐               ┌────────────────────────┐
         │     REPORTER CLIENT    │    TCP Chat   │    RESPONDER CLIENT    │
         │   (Giáo viên / Học sinh│ ◄───────────► │  (Kỹ thuật/Y tế/Bảo vệ)│
         │ - Gửi báo cáo sự cố    │     (1 - 1)   │ - Nhận ca đúng phòng   │
         │ - Nhận phản hồi        │               │ - Nhận việc ngay / sau │
         │ - Chat trao đổi sự cố  │               │ - Đánh dấu hoàn thành  │
         └────────────────────────┘               └────────────────────────┘
```

### 1.1. Server chính là Admin (Quản trị tập trung)
* **Server đóng vai trò trung tâm điều hành:** Server vừa là máy chủ Socket tiếp nhận dữ liệu mạng, vừa là nơi quản trị tối cao (Admin). 
* Server có toàn quyền:
  * Quản lý, khởi tạo và cấp phát tài khoản cho toàn bộ người dùng trong trường.
  * Giám sát mọi sự cố phát sinh tại các phòng học.
  * **Tự động định tuyến và phân phối:** Căn cứ vào bản chất sự cố để đẩy thông báo đến đúng các tài khoản thuộc phòng ban phụ trách, không gửi tràn lan.
  * Theo dõi và xem lại toàn bộ nội dung trao đổi (Chat) giữa các bên để phục vụ công tác thanh tra, hậu kiểm.
  * **Thống kê số liệu và xuất báo cáo** định kỳ theo ngày, tháng, quý, năm phục vụ công tác quản lý của Ban Giám Hiệu.

### 1.2. Phân loại 2 nhóm Client tương tác
1. **Người báo cáo (Reporter):** Giáo viên, học sinh tại các lớp học/phòng chức năng.
2. **Người xử lý (Responder):** Nhân sự trực thuộc các phòng ban chuyên môn:
   * **Phòng Kỹ thuật / Thiết bị (TECHNICAL):** Sửa chữa điện, nước, đèn, quạt, máy chiếu, bàn ghế.
   * **Phòng Y tế (MEDICAL):** Xử lý học sinh ngất xỉu, chấn thương, sốt, cấp cứu học đường.
   * **Đội Bảo vệ / An ninh (SECURITY):** Giải quyết xô xát, trộm cắp, người lạ đột nhập.

---

## 2. CÁC CHỨC NĂNG CỐT LÕI (CORE FEATURES)

### 2.1. Quản lý Tài khoản Tập trung (Chỉ Admin/Server cấp)
* **Không đăng ký tự do:** Đảm bảo an ninh môi trường giáo dục, học sinh và giáo viên không thể tự tạo tài khoản bừa bãi.
* **Chỉ Admin (Server) là người duy nhất tạo tài khoản:** Ban Quản trị/Server khởi tạo danh sách tài khoản kèm mật khẩu ban đầu và phân bổ về từng phòng ban/lớp học.
* **Bỏ cơ chế khôi phục mật khẩu phức tạp:** Không dùng OTP hay email rườm rà. Nếu người dùng quên mật khẩu, liên hệ trực tiếp Admin/Server để cấp lại hoặc đặt lại mật khẩu mới.

---

### 2.2. Tạo & Gửi Báo Cáo Sự Cố (Incident Reporting)
* Người dùng (Reporter) mở Client, điền thông tin sự cố:
  * **Vị trí:** Phòng học hoặc khu vực (ví dụ: `A101`, `A201`, `Lab Hoá`, `Sân bóng`...).
  * **Loại sự cố:** Thiết bị/Điện nước, Y tế hoặc An ninh trật tự.
  * **Mô tả chi tiết:** Dấu hiệu sự cố (khói khét, mất điện, vỡ ống nước...).
  * **Ảnh đính kèm:** Cho phép đính kèm ảnh chụp hiện trường để kỹ thuật viên dễ nhận diện.
* Báo cáo được đóng gói và gửi qua TCP Socket tới Server ngay lập tức.

---

### 2.3. Tự Động Đẩy Đúng Tài Khoản Của Phòng Ban Phụ Trách & Cơ Chế Online / Offline
* **Tự động phân phối đích danh theo phòng ban (Targeted Department Push):**
  * Server phân tích loại sự cố trong gói tin và **tự động đẩy bản tin đến đúng danh sách tài khoản thuộc phòng ban chuyên trách**, tuyệt đối không phát tán bừa bãi:
    * Sự cố **Điện, Nước, Thiết bị hỏng** $\rightarrow$ Tự động đẩy thẳng về các tài khoản của **Phòng Kỹ Thuật (`TECHNICAL`)**.
    * Sự cố **Học sinh ngất, Chấn thương, Cấp cứu** $\rightarrow$ Tự động đẩy thẳng về các tài khoản của **Phòng Y Tế (`MEDICAL`)**.
    * Sự cố **Xô xát, Mất trật tự, Người lạ** $\rightarrow$ Tự động đẩy thẳng về các tài khoản của **Đội Bảo Vệ (`SECURITY`)**.
  * *Lợi ích:* Kỹ thuật viên không bị làm phiền bởi các ca y tế, bảo vệ không bị lẫn lộn sự cố hỏng quạt trần $\rightarrow$ Đúng người, đúng việc, xử lý nhanh chóng.
* **Cơ chế nhận sự cố Online / Offline (Đơn giản & Đảm bảo không sót việc):**
  * **Tài khoản đang Online:** Khi có sự cố mới thuộc phạm vi chuyên môn, Server đẩy ngay lập tức thông báo về màn hình Client của nhân sự đang trực tuyến để họ bấm nhận việc.
  * **Tài khoản đang Offline:** Sự cố được lưu trữ an toàn trong cơ sở dữ liệu trên Server. Ngay khi nhân viên của phòng ban đó bật máy và đăng nhập (Online), Server sẽ tự động truy vấn và đẩy toàn bộ danh sách sự cố chưa xử lý thuộc phòng ban đó về máy để nhân viên tiếp nhận.
  * *Không cần hàng đợi chống đói hay điểm ưu tiên phức tạp:* Mọi sự cố đều được lưu vết đầy đủ theo thứ tự thời gian gửi.

---

### 2.4. Kênh Chat Trao Đổi Trực Tiếp 1-1 (Tự động xóa sau khi xong)
* **Giao tiếp đúng đối tượng:**
  * Khung Chat mở riêng biệt giữa **Người gửi báo cáo (Reporter)** và **Nhân sự tiếp nhận xử lý (Responder)** của sự cố đó.
  * Hai bên có thể nhắn tin trao đổi trực tiếp: hướng dẫn xử lý tạm thời (ngắt cầu dao), hẹn thời gian có mặt, cập nhật tiến độ sửa chữa.
* **Tự động xóa sau khi kết thúc:**
  * Khi sự cố được nhân sự xử lý xong và đánh dấu **"Hoàn thành" (Resolved)**, phiên trao đổi kết thúc và đoạn chat sẽ tự động dọn dẹp/xóa trên giao diện của cả 2 Client để tránh rác màn hình.
* **Server/Admin có quyền xem lại:**
  * Toàn bộ lịch sử tin nhắn vẫn được lưu trữ tại Server. Ban Giám Hiệu / Quản trị viên Server có thể tra cứu và xem lại toàn bộ nội dung trao đổi bất cứ lúc nào để đánh giá trách nhiệm và chất lượng xử lý.

---

### 2.5. Thống Kê Đa Chiều & Xuất Báo Cáo Định Kỳ (Admin Analytics & Reporting)
Server (Admin) được trang bị module thống kê và báo cáo chuyên sâu phục vụ công tác thanh tra, bảo trì cơ sở vật chất và đánh giá an toàn trường học:
* **Bộ lọc thời gian linh hoạt (Time-based Filters):**
  * **Theo Ngày (Daily):** Thống kê số lượng ca phát sinh trong ngày, ca đã hoàn thành, ca còn tồn đọng cần bàn giao ca trực.
  * **Theo Tháng (Monthly):** Theo dõi số lượng sự cố trung bình mỗi tháng để chuẩn bị vật tư thay thế.
  * **Theo Quý / Học kỳ (Quarterly / Semester):** Báo cáo sơ kết học kỳ I, học kỳ II phục vụ hội nghị giao ban trường học.
  * **Theo Năm (Annually):** Báo cáo tổng kết toàn năm học nhằm đánh giá chất lượng và mức độ xuống cấp của cơ sở hạ tầng.
* **Phân tích theo Phòng ban & Khu vực (Distribution Analytics):**
  * Cơ cấu sự cố theo bộ phận: tỷ lệ % sự cố Kỹ thuật, Y tế, An ninh.
  * Điểm nóng khu vực (Top Incident Locations): Liệt kê các phòng học hay bị hỏng hóc nhất (ví dụ: Lab Lý, phòng A201) để nhà trường lên kế hoạch bảo dưỡng phòng ngừa.
* **Chỉ số hiệu suất xử lý:** Thời gian xử lý trung bình của từng phòng ban; tỷ lệ hoàn thành đúng hạn.
* **Xuất báo cáo (Export Report):**
  * Xuất trực tiếp dữ liệu thống kê ra file chuẩn định dạng **CSV / Excel (`.csv`)** để lưu trữ hoặc nộp báo cáo lên Sở Giáo dục.

---

## 3. CẤU TRÚC THƯ MỤC SOURCE CODE

Toàn bộ dự án nằm trọn vẹn trong thư mục `source`, tổ chức theo mô hình Maven đa mô-đun chuẩn mực:

```
baitaplon/
├── source/                                 <-- MỞ THƯ MỤC NÀY TRONG NETBEANS
│   ├── pom.xml                             <-- File cấu hình tổng (Maven Parent)
│   ├── common/                             <-- Dùng chung: Constants, Models, Packet
│   │   └── src/main/java/com/incidentreport/common/
│   ├── service/                            <-- Kết nối SQLite & Nghiệp vụ DAO
│   │   └── src/main/java/com/incidentreport/service/
│   ├── server/                             <-- Máy chủ trung tâm (Server / Admin)
│   │   └── src/main/java/com/incidentreport/server/Server.java
│   └── client/                             <-- Ứng dụng giao diện JavaFX
│       └── src/main/java/com/incidentreport/client/IncidentReportApp.java
├── database/
│   └── incident_report.db                  <-- File CSDL SQLite (Tự động khởi tạo)
├── README.md                               <-- TÀI LIỆU DUY NHẤT CỦA DỰ ÁN
├── run_server.bat                          <-- File chạy nhanh Server
└── run_client.bat                          <-- File chạy nhanh Client
```

---

## 4. HƯỚNG DẪN MỞ VÀ CHẠY DỰ ÁN TRÊN APACHE NETBEANS

Hệ thống được thiết kế hoàn toàn tương thích và tối ưu hóa để chạy trực tiếp trên **Apache NetBeans (phiên bản 12, 17, 18, 19, 20+)** sử dụng **JDK 17**.

### Bước 1: Mở dự án trong NetBeans
1. Mở phần mềm **Apache NetBeans**.
2. Trên thanh menu, chọn `File` $\rightarrow$ `Open Project...` (hoặc phím tắt `Ctrl + Shift + O`).
3. Điều hướng tới thư mục bài tập lớn của bạn:
   ```
   D:\Filecode\Laptrinhmang\baitaplon\source
   ```
4. Chọn thư mục **`source`** và nhấn nút **Open Project**.
   > *NetBeans sẽ tự động nhận diện đây là một Maven Multi-Module Project và hiển thị đầy đủ các sub-modules: `common`, `service`, `server`, `client`, `simulator`.*

---

### Bước 2: Build dự án lần đầu
1. Nhấp chuột phải vào Project gốc **`incident-report-system`** ở cây thư mục bên trái NetBeans.
2. Chọn **`Clean and Build`**.
3. Chờ NetBeans biên dịch các module và tải các thư viện phụ thuộc (JavaFX, SQLite JDBC, Gson). Quá trình hoàn tất khi xuất hiện thông báo `BUILD SUCCESS`.

---

### Bước 3: Khởi chạy Server (Admin) trên NetBeans
1. Tại cây thư mục dự án bên trái, mở rộng module **`server`**.
2. Tìm đến file mã nguồn chính của Server:
   `Source Packages` $\rightarrow$ `com.incidentreport.server` $\rightarrow$ **`Server.java`**.
3. Nhấp chuột phải vào file **`Server.java`** $\rightarrow$ Chọn **`Run File`** (hoặc ấn `Shift + F6`).
4. Khung Output của NetBeans sẽ hiển thị thông báo:
   ```
   ✅ [TCP SERVER] Đang lắng nghe kết nối trên cổng TCP 5000...
   ✅ Máy chủ sẵn sàng tiếp nhận điều phối sự cố!
   ```

---

### Bước 4: Khởi chạy Client (Người dùng / Kỹ thuật viên) trên NetBeans
1. Tại cây thư mục dự án bên trái, mở rộng module **`client`**.
2. Tìm đến file khởi chạy Client:
   `Source Packages` $\rightarrow$ `com.incidentreport.client` $\rightarrow$ **`Main.java`** (hoặc `IncidentReportApp.java`).
3. Nhấp chuột phải vào file **`Main.java`** $\rightarrow$ Chọn **`Run File`** (hoặc ấn `Shift + F6`).
4. Cửa sổ giao diện đồ họa JavaFX sẽ xuất hiện.
   > **Mẹo kiểm thử thực tế:** Bạn có thể lặp lại thao tác **`Run File`** trên `Main.java` nhiều lần để mở đồng thời 2 hoặc 3 cửa sổ Client cùng lúc (ví dụ: 1 cửa sổ đăng nhập tài khoản Giáo viên để báo sự cố, 1 cửa sổ đăng nhập Kỹ thuật viên để nhận sự cố và chat thử).

---

## 5. DANH SÁCH TÀI KHOẢN MẪU ĐƯỢC ADMIN CẤP SẴN

Hệ thống đã nạp sẵn danh sách tài khoản theo đúng các vai trò và phòng ban chuyên trách (Mật khẩu mặc định cho tất cả: **`123456`**):

| Tên Đăng Nhập | Họ và Tên | Vai Trò | Phòng Ban / Chuyên Môn | Nhiệm Vụ |
| :--- | :--- | :---: | :---: | :--- |
| **`admin_bgh`** | Thầy Nguyễn Văn An | `ADMIN` | Quản trị tập trung | Ban Giám Hiệu / Quản trị hệ thống Server |
| **`gv_lan`** | Cô Trần Thị Lan | `REPORTER` | Giáo viên 10A2 | Báo cáo các sự cố tại lớp học |
| **`hs_minh`** | Lê Hoàng Minh | `REPORTER` | Lớp trưởng 10A2 | Báo cáo sự cố thiết bị / sự vụ học sinh |
| **`kt_tuan`** | Anh Hoàng Văn Tuấn | `RESPONDER` | **Phòng Kỹ Thuật (TECHNICAL)** | Nhận sửa chữa điện nước, quạt, máy chiếu |
| **`kt_nam`** | Anh Ngô Bảo Nam | `RESPONDER` | **Phòng Kỹ Thuật (TECHNICAL)** | Kỹ thuật viên thiết bị |
| **`yte_mai`** | Cô Vũ Tuyết Mai | `RESPONDER` | **Phòng Y Tế (MEDICAL)** | Nhân viên y tế học đường, sơ cứu |
| **`bv_son`** | Bác Trương Sơn | `RESPONDER` | **Đội Bảo Vệ (SECURITY)** | Đội trưởng an ninh trật tự trường học |

---

## 6. KỊCH BẢN KIỂM THỬ MẪU (TEST SCENARIO)

1. **Bước 1 (Báo sự cố):** 
   * Mở Client 1 $\rightarrow$ Đăng nhập bằng `gv_lan` / `123456`.
   * Vào tab *"Báo sự cố mới"*: Chọn phòng `A201`, Loại sự cố: `Sự cố Điện nguy hiểm`, Mô tả: *"Chập cầu dao phát tia lửa điện"*, bấm **Gửi báo cáo**.
2. **Bước 2 (Server tự động đẩy đúng tài khoản phòng ban):**
   * Server nhận sự cố và nhận diện đây là sự cố điện $\rightarrow$ **tự động chỉ đẩy bản tin về các tài khoản thuộc Phòng Kỹ Thuật (TECHNICAL)** (như `kt_tuan`, `kt_nam`), các tài khoản Y tế hay Bảo vệ hoàn toàn không bị ảnh hưởng.
   * Mở Client 2 $\rightarrow$ Đăng nhập bằng kỹ thuật viên `kt_tuan` / `123456`.
   * Màn hình của `kt_tuan` lập tức nhận được thông báo sự cố phòng `A201` $\rightarrow$ Bấm **"Nhận nhiệm vụ"**.
3. **Bước 3 (Trao đổi trực tiếp 1 - 1):**
   * Cả 2 Client (cô Lan và anh Tuấn) cùng chuyển sang tab **Chat**.
   * Anh Tuấn nhắn: *"Em đang mang dụng cụ lên phòng A201, cô ngắt át phụ trước giúp em ạ!"*.
   * Cô Lan nhắn phản hồi: *"Cô vừa ngắt át rồi nhé Tuấn!"*.
4. **Bước 4 (Hoàn thành & Dọn dẹp):**
   * Anh Tuấn sau khi sửa xong bấm **"Hoàn thành"** và nhập ghi chú *"Đã thay át mới an toàn"*.
   * Phiên chat hoàn tất và tự động dọn dẹp trên cả 2 màn hình Client.
   * Trên Server, Admin mở nhật ký vẫn kiểm tra được trọn vẹn toàn bộ tiến độ và nội dung trao đổi của ca xử lý này.
5. **Bước 5 (Admin Thống kê & Xuất báo cáo):**
   * Tại màn hình Server/Admin, mở tab Thống kê:
   * Chọn xem thống kê sự cố theo ngày, tháng, quý hoặc năm.
   * Xem biểu đồ phân bố sự cố theo phòng ban và danh sách các phòng học hay xảy ra hỏng hóc nhất.
   * Bấm nút **"Xuất báo cáo (CSV)"** để kết xuất dữ liệu lưu trữ hồ sơ thanh tra.

