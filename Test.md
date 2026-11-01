# 📋 SIDS - TÀI LIỆU KỊCH BẢN KIỂM THỬ HỆ THỐNG TOÀN DIỆN (TEST PLAN & ACCEPTANCE TESTING)

> **Dự án:** Hệ thống Điều phối & Xử lý Sự cố Học đường Thời gian thực (SIDS - School Incident Dispatch System)  
> **Nhóm thực hiện:** Nhóm 11 - Lập trình mạng  
> **Phiên bản tài liệu:** 2.0 (Bản nghiệm thu bảo vệ đồ án)  
> **Mục đích:** Hướng dẫn chi tiết từng bước cho Giảng viên và Nhóm phát triển ngồi kiểm thử (test) nghiệm thu toàn bộ tính năng, luồng nghiệp vụ và trường hợp biên của hệ thống.

---

## 📌 MỤC LỤC
1. [Chuẩn bị môi trường & Bảng tài khoản kiểm thử](#1-chuẩn-bị-môi-trường--bảng-tài-khoản-kiểm-thử)
2. [Quy trình khởi động hệ thống](#2-quy-trình-khởi-động-hệ-thống)
3. [10 Kịch bản kiểm thử then chốt (Test Scenarios)](#3-10-kịch-bản-kiểm-thử-then-chốt)
   - [Kịch bản 1: Đăng nhập & Chuyển đổi cấu hình mạng (Local vs Radmin VPN)](#kịch-bản-1-kiểm-thử-đăng-nhập--cấu-hình-mạng)
   - [Kịch bản 2: Giáo viên tạo sự cố mới (Kèm ảnh & Hộp thoại xác nhận)](#kịch-bản-2-giáo-viên-tạo-báo-cáo-sự-cố-mới)
   - [Kịch bản 3: Server tự động điều phối & Đẩy cảnh báo khẩn cấp Real-time](#kịch-bản-3-server-tự-động-điều-phối--cảnh-báo-real-time)
   - [Kịch bản 4: Cán bộ tiếp nhận sự cố & Cơ chế chống tranh chấp (Atomic Claiming)](#kịch-bản-4-tiếp-nhận-sự-cố--chống-tranh-chấp-concurrency)
   - [Kịch bản 5: Nhắn tin trao đổi 1-1 Real-time giữa Giáo viên và Kỹ thuật viên](#kịch-bản-5-nhắn-tin-trao-đổi-1-1-real-time)
   - [Kịch bản 6: Hoàn tất xử lý & Nghiệm thu công việc](#kịch-bản-6-hoàn-tất-xử-lý--nghiệm-thu-sự-cố)
   - [Kịch bản 7: Ban Giám Hiệu chỉ định điều phối thủ công (Giao việc)](#kịch-bản-7-ban-giám-hiệu-chỉ-định-giao-việc-thủ-công)
   - [Kịch bản 8: Hủy sự cố & Kiểm thử điều kiện biên đồng thời (Edge Cases)](#kịch-bản-8-hủy-sự-cố--kiểm-thử-điều-kiện-biên-edge-cases)
   - [Kịch bản 9: Bộ lọc thống kê & Xuất báo cáo CSV mở trực tiếp bằng Excel (UTF-8 BOM)](#kịch-bản-9-thống-kê--xuất-báo-cáo-csv-cho-excel)
   - [Kịch bản 10: Xử lý ngắt kết nối mạng đột ngột & Khôi phục phiên](#kịch-bản-10-xử-lý-ngắt-kết-nối-mạng-đột-ngột)
4. [Bảng Checklist nghiệm thu tổng kết](#4-bảng-checklist-nghiệm-thu-tổng-kết)

---

## 1. Chuẩn bị môi trường & Bảng tài khoản kiểm thử

### 1.1. Yêu cầu môi trường
* **Hệ điều hành:** Windows 10/11 (khuyến nghị có Radmin VPN nếu test mạng nhiều máy).
* **JDK:** Java LTS 17 (Eclipse Temurin, Oracle JDK hoặc OpenJDK 17).
* **Công cụ chạy:** Apache NetBeans (12.x – 20+) hoặc dòng lệnh Maven (`mvn`).
* **Mạng:** 
  * Cùng một máy: Dùng cấu hình `127.0.0.1` (Localhost).
  * Khác máy qua mạng ảo: Bật Radmin VPN, sử dụng IP `26.51.180.99` (cấu hình trong `config/server.properties` - hoặc IP Radmin của máy chủ Server).

### 1.2. Bảng tài khoản kiểm thử có sẵn (Mật khẩu chung: `123456`)

| STT | Họ tên hiển thị | Username | Mật khẩu | Vai trò (Role) | Chuyên môn / Phòng ban | Nút chọn nhanh (Login Chip) |
|:---:|:---|:---|:---:|:---|:---|:---:|
| 1 | **Thầy Nguyễn Văn An** | `admin_bgh` | `123456` | Ban Giám Hiệu (`ADMIN`) | Toàn trường | `Admin BGH` |
| 2 | **Cô Trần Thị Lan** | `gv_lan` | `123456` | Giáo viên (`REPORTER`) | Lớp 10A2 | `GV Lan` |
| 3 | **Lê Hoàng Minh** | `hs_minh` | `123456` | Học sinh (`REPORTER`) | Lớp 10A2 | `HS Minh` |
| 4 | **Anh Hoàng Văn Tuấn** | `kt_tuan` | `123456` | Kỹ thuật viên (`RESPONDER`) | Kỹ thuật (`TECHNICAL`) | `Kỹ thuật Tuấn` |
| 5 | **Chị Nguyễn Mai Anh** | `yte_mai` | `123456` | Cán bộ Y tế (`RESPONDER`) | Y tế (`MEDICAL`) | `Y tế Mai` |
| 6 | **Bác Lê Đình Sơn** | `bv_son` | `123456` | Nhân viên Bảo vệ (`RESPONDER`) | An ninh (`SECURITY`) | `Bảo vệ Sơn` |

> 💡 **Mẹo:** Bạn có thể click trực tiếp vào các viên thuốc (Chip) màu phía dưới màn hình Đăng nhập để tự động điền Username/Password và đăng nhập ngay lập tức mà không cần gõ phím.

---

## 2. Quy trình khởi động hệ thống

> ⚠️ **NGUYÊN TẮC VÀNG VỀ KIẾN TRÚC CLIENT - SERVER:**  
> * Toàn bộ dữ liệu CSDL (`database/incident_report.db`) **chỉ được lưu trữ và quản lý tập trung DUY NHẤT tại 01 MÁY CHỦ (SERVER)**.
> * Các ứng dụng Client (`source/client`) là **Client thuần giao diện (Thin Client)**, **hoàn toàn KHÔNG kết nối trực tiếp đến Database**, mà mọi thao tác đều gửi/nhận qua Socket TCP tới Server.
> * Dù bạn sao chép mã nguồn sang bao nhiêu máy tính đi nữa, **chỉ được bật Server trên 01 máy duy nhất (Máy Chủ)**. Các máy khác **chỉ được bật Client** và kết nối về IP của Máy Chủ đó!

---

### Cách 2.1: Kiểm thử trên 01 máy tính (Mô phỏng nhiều vai trò)
1. **Khởi động Server:**
   * Trong NetBeans: Chuột phải vào project con `server` -> **Run** (chạy class `ServerApp.java`).
   * *Hoặc qua dòng lệnh:*
     ```cmd
     mvn exec:java -f source/server/pom.xml
     ```
   * *Console Server in thông báo:*
     ```text
     ============================================================================
       HE THONG DIEU PHOI SU CO TRUONG THPT (SERVER) - DA KHOI DONG THANH CONG
       Cong lang nghe TCP: 5000
       Che do luu tru: SQLite WAL Mode (Zero-Lock)
     ============================================================================
     ```

2. **Khởi động các Client JavaFX:**
   * Trong NetBeans: Chuột phải vào project con `client` -> **Run** (chạy class `IncidentReportApp.java`). Bấm Run 3 - 4 lần để mở nhiều cửa sổ song song.
   * Trên mỗi cửa sổ: Chọn `🖥️ Mặc định (Local)` (`127.0.0.1:5000`) và click vào các chip tài khoản khác nhau (`admin_bgh`, `gv_lan`, `kt_tuan`).

---

### Cách 2.2: Kiểm thử thực tế trên 02 hoặc nhiều máy tính (Qua Radmin VPN / Mạng LAN)

* **MÁY A (Đóng vai trò MÁY CHỦ - SERVER):**
  1. Bật Radmin VPN, ghi lại địa chỉ IP của Máy A (ví dụ: `26.51.180.99`). Có thể chỉnh sửa IP này trong `config/server.properties`.
  2. Khởi động **Server** (chạy `Server.java` hoặc `run_server.bat`).
  3. *(Tùy chọn)* Có thể mở thêm 1 Client trên chính Máy A để đóng vai Giáo viên (`gv_lan`).

* **MÁY B (Đóng vai trò MÁY TRẠM - CLIENT của Kỹ thuật viên / Giáo viên):**
  1. Bật Radmin VPN, tham gia (Join) cùng phòng mạng ảo với Máy A.
  2. **⚠️ TUYỆT ĐỐI KHÔNG BẬT SERVER TRÊN MÁY B!**
  3. Chỉ mở **Client** (chạy `IncidentReportApp.java` hoặc `run_client.bat`).
  4. Tại màn hình Đăng nhập của Máy B:
     * Bấm vào nút `🌐 Radmin VPN` (hệ thống tự điền `26.51.180.99:5000` - hoặc bạn gõ đúng IP Radmin của Máy A).
     * Bấm vào chip tài khoản `Kỹ thuật Tuấn` (`kt_tuan`).
     * Bấm **ĐĂNG NHẬP**.
  5. **Xác nhận thành công:**
     * Client ở Máy B lập tức kết nối tới Server của Máy A.
     * Khi Máy A bấm gửi sự cố, Máy B lập tức đổ chuông và hiện thông báo!
     * Toàn bộ dữ liệu của Máy B thao tác đều được lưu tập trung vào CSDL của Máy A!
     mvn javafx:run -f source/client/pom.xml
     ```
   * *Phân bổ cửa sổ đề xuất khi test:*
     * **Cửa sổ 1:** Đăng nhập `gv_lan` (Giáo viên báo sự cố).
     * **Cửa sổ 2:** Đăng nhập `kt_tuan` (Kỹ thuật viên nhận và giải quyết việc).
     * **Cửa sổ 3:** Đăng nhập `admin_bgh` (Ban Giám Hiệu theo dõi, điều phối và xuất báo cáo).
     * *(Tùy chọn) Cửa sổ 4:* Đăng nhập `yte_mai` (để kiểm tra xem phòng ban khác có bị gửi nhầm thông báo không).

---

## 3. 10 Kịch bản kiểm thử then chốt

---

### Kịch bản 1: Kiểm thử Đăng nhập & Cấu hình mạng

| Mã kịch bản | TC-01 |
|:---|:---|
| **Mục tiêu** | Kiểm tra giao diện đăng nhập, tính năng 1-click chọn giữa Localhost và Radmin VPN, ghi nhớ cấu hình (Persistence) và xác thực dữ liệu. |
| **Tiền điều kiện** | Server đang chạy tại cổng `5000`. |

#### Các bước thực hiện:
1. Mở Client, quan sát khu vực **"Máy chủ kết nối:"** phía dưới nút Đăng nhập.
2. Bấm vào nút `🌐 Radmin VPN`:
   * Quan sát dòng chữ bên dưới tự động đổi thành: `▸ Tùy chỉnh IP/Port (26.51.180.99:5000)`.
3. Bấm vào nút `🖥️ Mặc định (Local)`:
   * Quan sát dòng chữ tự động đổi thành: `▸ Tùy chỉnh IP/Port (127.0.0.1:5000)`.
4. Bấm vào mũi tên `▸` để bung mở ô nhập: Kiểm tra xem ô IP có đổi chính xác giữa `127.0.0.1` và `26.51.180.99` hay không.
5. Tắt hoàn toàn Client và mở lại:
   * Kiểm tra xem cấu hình bạn vừa chọn lần trước có được tự động nạp lại không (không phải nhập lại bằng tay).
6. Kiểm tra xử lý lỗi đăng nhập:
   * Để trống mật khẩu và bấm "ĐĂNG NHẬP" -> Báo lỗi: *"Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!"*.
   * Nhập sai mật khẩu (ví dụ `sai_mat_khau`) -> Báo lỗi banner đỏ: *"Tên đăng nhập hoặc mật khẩu không chính xác!"*.
   * Tắt Server rồi bấm Đăng nhập -> Báo lỗi: *"Không thể kết nối đến Máy chủ tại..."*.
7. Bấm vào Chip `Admin BGH`: Đăng nhập thành công, chuyển mượt mà vào màn hình làm việc của Ban Giám Hiệu.

✅ **Kết quả mong đợi:** Chuyển đổi IP 1-click nhạy bén, nhớ cấu hình qua các lần mở app, hiển thị cảnh báo lỗi tiếng Việt rõ ràng.

---

### Kịch bản 2: Giáo viên tạo báo cáo sự cố mới

| Mã kịch bản | TC-02 |
|:---|:---|
| **Mục tiêu** | Kiểm tra luồng gửi báo cáo sự cố từ Giáo viên, đính kèm file ảnh, hộp thoại xác nhận trước khi gửi và âm thanh chuông báo. |
| **Tài khoản** | `gv_lan` (Cô Trần Thị Lan) |

#### Các bước thực hiện:
1. Tại màn hình Giáo viên, nhấn vào tab **"Báo cáo sự cố mới"**.
2. Nhập các thông tin kiểm thử:
   * **Vị trí / Phòng học:** `Phòng Thực hành Hóa B204`
   * **Phân loại sự cố:** Chọn `Hỏng thiết bị giảng đường (Máy chiếu, Âm thanh, Bàn ghế, Quạt)` -> Nhóm `KỸ THUẬT`.
   * **Mô tả chi tiết:** `Máy chiếu bị nhòe màu đỏ, quạt trần số 2 kêu rè rè có nguy cơ rơi.`
   * **Ảnh hiện trường:** Bấm `Chọn ảnh đính kèm...` -> Chọn 1 file ảnh bất kỳ (`.jpg` hoặc `.png`).
3. Bấm nút **"GỬI BÁO CÁO SỰ CỐ"**.
4. **Quan sát Hộp thoại xác nhận (Modal Confirmation Dialog):**
   * Tiêu đề: *Gửi báo cáo sự cố tới hệ thống điều phối?*
   * Đầy đủ thông tin: Vị trí, Phân loại, Mô tả, Kích thước file ảnh đính kèm.
   * Nội dung chữ và 2 nút bấm `Xác nhận gửi` / `Kiểm tra lại` hiển thị trọn vẹn, căn chỉnh đẹp, không bị tràn hay cắt chữ.
5. Bấm `Kiểm tra lại` -> Hộp thoại đóng lại, thông tin trong form vẫn giữ nguyên.
6. Bấm lại `GỬI BÁO CÁO SỰ CỐ` -> Bấm `Xác nhận gửi`:
   * Loa phát âm thanh chuông báo tinh tang (Notification Chime).
   * Form nhập liệu được làm mới sạch sẽ.
   * Bên bảng danh sách sự cố xuất hiện ngay một bản ghi mới với mã vé dạng `TK-XXXXXX`, trạng thái huy hiệu màu vàng: **"Chờ tiếp nhận"**.

✅ **Kết quả mong đợi:** Có bước hỏi xác nhận trước khi gửi, âm thanh phản hồi, tạo mã vé tức thì và hình ảnh được upload lên thư mục `uploads/` của Server.

---

### Kịch bản 3: Server tự động điều phối & Cảnh báo Real-time

| Mã kịch bản | TC-03 |
|:---|:---|
| **Mục tiêu** | Xác minh tính năng định tuyến theo chuyên môn (Routing), đẩy thông báo tức thì (Push Notification), âm thanh khẩn cấp và không gửi sai phòng ban. |
| **Tài khoản** | Cùng lúc mở 3 tài khoản: `kt_tuan` (Kỹ thuật), `yte_mai` (Y tế), `admin_bgh` (Admin) |

#### Các bước thực hiện:
1. Để 3 màn hình cạnh nhau.
2. Tại màn hình `gv_lan`, gửi 1 sự cố thuộc nhóm **KỸ THUẬT** (như ở Kịch bản 2).
3. Ngay lập tức quan sát màn hình của Kỹ thuật viên `kt_tuan`:
   * Loa phát âm thanh thông báo khẩn cấp (Urgent Alarm) với âm lượng rõ ràng.
   * Góc trên bên phải xuất hiện Banner thông báo màu hổ phách:
     * Tiêu đề: `🔔 CÓ SỰ CỐ MỚI CẦN XỬ LÝ!`
     * Nội dung: `Sự cố TK-XXXXXX tại Phòng Thực hành Hóa B204 cần phòng ban KỸ THUẬT tiếp nhận.`
     * Banner đếm lùi tự động biến mất sau 7 giây hoặc khi bấm dấu `✕`.
   * Danh sách công việc của `kt_tuan` tự động nhảy thêm sự cố mới mà không cần bấm F5 hay Reload.
4. Quan sát màn hình của Y tế `yte_mai`:
   * Hoàn toàn **không nhận âm thanh hay thông báo** của sự cố kỹ thuật này (bảo đảm tính bảo mật và đúng chuyên môn).
5. Quan sát màn hình Admin `admin_bgh`:
   * Biểu đồ và danh sách tổng quan toàn trường tự động tăng thêm 1 sự cố mới.

✅ **Kết quả mong đợi:** Định tuyến chuẩn xác theo `specialty`, Server đẩy gói tin TCP tức thì, âm thanh to rõ cảnh báo người trực ca.

---

### Kịch bản 4: Tiếp nhận sự cố & Chống tranh chấp (Concurrency)

| Mã kịch bản | TC-04 |
|:---|:---|
| **Mục tiêu** | Kiểm thử luồng tiếp nhận công việc, cập nhật trạng thái thời gian thực và kiểm thử cơ chế khóa lạc quan (Atomic Lock) chống giẫm chân/nhận trùng. |
| **Tài khoản** | `kt_tuan` (Kỹ thuật viên) và `gv_lan` (Giáo viên) |

#### Các bước thực hiện:
1. Tại tài khoản `kt_tuan`, tìm sự cố vừa tạo ở trạng thái "Chờ tiếp nhận", bấm nút **"Tiếp nhận"**.
2. Hộp thoại xác nhận xuất hiện: *"Bạn có chắc chắn muốn tiếp nhận xử lý sự cố TK-XXXXXX tại Phòng...? Sau khi tiếp nhận, các kỹ thuật viên khác sẽ không thể nhận lại."*
3. Bấm **"Tiếp nhận việc"**:
   * Âm thanh nhận việc vang lên (`playClaim`).
   * Sự cố chuyển sang trạng thái màu xanh dương: **"Đang xử lý"**. Tên người xử lý gán cho `Anh Hoàng Văn Tuấn`.
4. Quan sát ngay màn hình Giáo viên `gv_lan`:
   * Góc trên xuất hiện Toast thông báo nổi: `Anh Hoàng Văn Tuấn đã tiếp nhận sự cố TK-XXXXXX`.
   * Trạng thái sự cố trên màn hình giáo viên lập tức đổi sang `Đang xử lý` kèm tên người nhận.
5. **Kiểm thử tranh chấp (Race Condition):**
   * Nếu có một kỹ thuật viên thứ 2 cùng bấm "Tiếp nhận" đúng sự cố đó cùng thời điểm:
   * Kỹ thuật viên thứ 2 sẽ nhận được thông báo lỗi từ Server: *"Sự cố này đã được cán bộ khác tiếp nhận trước đó!"*.
   * Hệ thống đảm bảo **không bao giờ có 2 người cùng nhận 1 sự cố**.

✅ **Kết quả mong đợi:** Chuyển trạng thái tức thời 2 chiều, thông báo người nhận việc cho giáo viên, chống đè việc 100%.

---

### Kịch bản 5: Nhắn tin trao đổi 1-1 Real-time

| Mã kịch bản | TC-05 |
|:---|:---|
| **Mục tiêu** | Kiểm tra kênh chat riêng biệt cho từng sự cố (Ticket Chat), truyền tin tức thời, phân biệt bong bóng chat và âm thanh nhận tin. |
| **Tài khoản** | `gv_lan` (Giáo viên) và `kt_tuan` (Kỹ thuật viên) |

#### Các bước thực hiện:
1. Tại sự cố đang xử lý, ở cả 2 màn hình `gv_lan` và `kt_tuan`, bấm vào nút **"💬 Chat"** (hoặc mở chi tiết sự cố).
2. Phía `gv_lan`, gõ tin nhắn: `"Anh Tuấn mang giúp em dây cáp HDMI chuyển đổi Type-C nhé!"` và bấm Gửi (hoặc nhấn phím `Enter`).
3. Quan sát màn hình `kt_tuan`:
   * Tin nhắn xuất hiện ngay lập tức trong khung chat (độ trễ < 50ms qua TCP).
   * Loa phát tiếng "Pop" đặc trưng của tin nhắn mới.
   * Bong bóng chat của đối phương có màu nền sáng dịu (`#F4EFE6`), căn bên trái.
4. Phía `kt_tuan`, gõ trả lời: `"Vâng cô, em đang cầm hộp đồ nghề lên tầng 2 rồi ạ."` và bấm Gửi:
   * Phía `kt_tuan`, tin nhắn hiển thị bên phải với màu xanh mực (`#14264A`), chữ trắng.
   * Phía `gv_lan`, nhận tin nhắn kèm âm thanh "Pop".

✅ **Kết quả mong đợi:** Kênh chat 1-1 thông suốt, tin nhắn được lưu vĩnh viễn vào bảng `chat_messages` trong CSDL SQLite để làm bằng chứng nghiệm thu (audit trail).

---

### Kịch bản 6: Hoàn tất xử lý & Nghiệm thu sự cố

| Mã kịch bản | TC-06 |
|:---|:---|
| **Mục tiêu** | Kỹ thuật viên kết thúc công việc, ghi chú biên bản nghiệm thu, kiểm tra nút bấm không bị cắt chữ và thông báo chúc mừng tới giáo viên. |
| **Tài khoản** | `kt_tuan` và `gv_lan` |

#### Các bước thực hiện:
1. Tại màn hình `kt_tuan`, ở sự cố đang xử lý, bấm nút **"Hoàn thành"**.
2. Hộp thoại xác nhận nghiệm thu xuất hiện:
   * Tiêu đề: `Xác nhận hoàn thành xử lý sự cố TK-XXXXXX`
   * Ô nhập text: *"Ghi chú nghiệm thu:"* (mặc định gợi ý: `Đã sửa chữa và kiểm tra an toàn.`).
   * **Kiểm tra 2 nút bấm:** Nút bên trái hiển thị trọn vẹn chữ **"Xác nhận hoàn thành"** (không bị cụt thành `Xác nhận hoàn...`). Nút bên phải: **"Hủy"**.
3. Sửa ghi chú thành: `"Đã thay tụ điện quạt trần, cân chỉnh lại thấu kính máy chiếu hiển thị sắc nét."`
4. Bấm **"Xác nhận hoàn thành"**:
   * Sự cố chuyển sang trạng thái màu xanh lá: **"Đã hoàn tất"**.
   * Ô chat của sự cố tự động khóa lại (đóng luồng trao đổi trực tiếp, chỉ cho xem lịch sử).
5. Quan sát ngay màn hình Giáo viên `gv_lan`:
   * Loa phát âm thanh hoàn tất/chúc mừng (`playSuccess`).
   * Góc trên màn hình hiện thông báo nổi:
     * Tiêu đề: `✅ SỰ CỐ ĐÃ HOÀN TẤT XỬ LÝ!`
     * Nội dung: `Anh Hoàng Văn Tuấn đã khắc phục xong sự cố TK-XXXXXX. Ghi chú: Đã thay tụ điện quạt trần...`
   * Bấm vào xem chi tiết sự cố: Mục *"KẾT QUẢ NGHIỆM THU / GHI CHÚ XỬ LÝ"* hiển thị đúng nội dung trên bằng tiếng Việt chuẩn có dấu.

✅ **Kết quả mong đợi:** Hoàn thành chu trình xử lý sự cố, text button rõ ràng, giáo viên nhận thông báo kết quả ngay lập tức.

---

### Kịch bản 7: Ban Giám Hiệu chỉ định giao việc thủ công

| Mã kịch bản | TC-07 |
|:---|:---|
| **Mục tiêu** | Kiểm tra quyền hạn quản trị của Admin: phân bổ sự cố trực tiếp cho một nhân sự cụ thể, kiểm tra nút "Giao việc" tự ẩn khi đã có người làm. |
| **Tài khoản** | `admin_bgh` và `kt_tuan` |

#### Các bước thực hiện:
1. Dùng `gv_lan` tạo 1 sự cố mới ở trạng thái "Chờ tiếp nhận".
2. Đăng nhập `admin_bgh`, quan sát danh sách sự cố:
   * Tại dòng sự cố mới, cột hành động hiển thị nút màu vàng: **"👤 Giao việc"**.
3. Bấm vào nút **"Giao việc"**:
   * Hộp thoại mở ra, tự động lọc danh sách nhân sự phù hợp chuyên môn (ví dụ danh sách các kỹ thuật viên điện/nước).
   * Chọn nhân sự: `Anh Hoàng Văn Tuấn (Kỹ thuật điện 1)`.
   * Nhập chỉ đạo: `Ưu tiên sửa trước 14h chiều nay để phục vụ giờ thực hành.`
4. Bấm **"Xác nhận giao việc"**:
   * Cột trạng thái đổi sang "Đang xử lý", cột người tiếp nhận hiện tên `Anh Hoàng Văn Tuấn`.
   * **Nút "Giao việc" tại dòng đó lập tức biến mất** (chỉ còn nút "Chat" và "Chi tiết"), ngăn chặn việc giao trùng lặp.
5. Quan sát màn hình `kt_tuan`:
   * Nhận ngay thông báo đẩy khẩn cấp kèm âm thanh: `BGH đã chỉ định bạn xử lý sự cố TK-XXXXXX!`

✅ **Kết quả mong đợi:** Admin có toàn quyền điều động nhân sự; sau khi giao việc nút "Giao việc" tự ẩn, nhân sự được giao nhận thông báo tức thời.

---

### Kịch bản 8: Hủy sự cố & Kiểm thử điều kiện biên (Edge Cases)

| Mã kịch bản | TC-08 |
|:---|:---|
| **Mục tiêu** | Kiểm tra tính chặt chẽ trong nghiệp vụ hủy vé: khi nào được hủy, khi nào bị chặn lỗi và kiểm tra đồng thời (Concurrency check). |
| **Tài khoản** | `gv_lan` và `kt_tuan` |

#### Trường hợp 8.1: Hủy hợp lệ khi sự cố còn "Chờ tiếp nhận"
1. Tại `gv_lan`, chọn 1 sự cố do chính mình tạo đang ở trạng thái **"Chờ tiếp nhận"**.
2. Bấm nút **"Hủy sự cố"**:
   * Hộp thoại hỏi: *"Bạn có chắc chắn muốn hủy sự cố này? Sau khi xác nhận hủy, sự cố sẽ đóng lại và không thể mở lại."*
   * Kiểm tra nút bấm: Nút hiển thị đầy đủ chữ **"Hủy sự cố"** (không bị `Hủy sự...`), nút phụ **"Giữ lại"**.
3. Bấm `Hủy sự cố`:
   * Sự cố chuyển sang trạng thái xám: **"Đã hủy"**.
   * Bấm vào xem chi tiết: Mục Ghi chú nghiệm thu hiển thị chuẩn tiếng Việt có dấu: **`Người báo chủ động hủy`** (không bị lỗi không dấu `Nguoi bao chu dong huy`).

#### Trường hợp 8.2: Bị chặn khi sự cố đã được tiếp nhận hoặc đã hoàn thành
1. Chọn một sự cố đang ở trạng thái **"Đang xử lý"** hoặc **"Đã hoàn tất"**.
2. Nút "Hủy sự cố" sẽ bị ẩn hoặc vô hiệu hóa.
3. **Thử thách Race Condition (Trường hợp người dùng đã mở sẵn popup):**
   * Tại màn hình `gv_lan`, bấm "Hủy sự cố" để popup xác nhận hiện lên sẵn trên màn hình, **chưa bấm vội**.
   * Sang màn hình `kt_tuan`, bấm **"Tiếp nhận"** hoặc **"Hoàn thành"** sự cố đó.
   * Quay lại màn hình `gv_lan`, bấm nút "Hủy sự cố" trên popup có sẵn:
   * Hệ thống **tuyệt đối không cho phép hủy**, mà lập tức bật hộp thoại báo lỗi:  
     `❌ Không thể hủy sự cố này vì sự cố đã được cán bộ tiếp nhận hoặc đã hoàn tất xử lý!`
   * Danh sách tự động đồng bộ lại trạng thái mới nhất từ Server.

✅ **Kết quả mong đợi:** Chống lỗi logic dữ liệu triệt để, thông báo tiếng Việt có dấu rõ ràng, không để xảy ra trường hợp "đã làm xong mà vẫn bị hủy mất".

---

### Kịch bản 9: Thống kê & Xuất báo cáo CSV cho Excel

| Mã kịch bản | TC-09 |
|:---|:---|
| **Mục tiêu** | Kiểm tra bộ lọc thống kê theo Thời gian (Ngày, Tháng, Năm), Phòng ban, Trạng thái và tính tương thích 100% của file CSV khi mở trực tiếp bằng Microsoft Excel. |
| **Tài khoản** | `admin_bgh` (Ban Giám Hiệu) |

#### Các bước thực hiện:
1. Đăng nhập tài khoản `admin_bgh`, chọn tab **"Thống kê & Báo cáo"**.
2. **Kiểm tra bộ lọc đa tiêu chí:**
   * Chọn `Khoảng thời gian`: Thử lọc theo *Hôm nay*, *Tháng này*, hoặc *Khoảng ngày tùy chọn* (ví dụ từ `01/07/2026` đến `31/07/2026`).
   * Chọn `Phòng ban`: `Kỹ thuật`, `Y tế`, `An ninh` hoặc `Tất cả`.
   * Chọn `Trạng thái`: `Đã hoàn tất`, `Đang xử lý`...
   * Quan sát: Các thẻ số liệu (KPI Cards) và bảng dữ liệu bên dưới lọc tức thì, chính xác theo ngày tháng.
3. **Xuất file báo cáo:**
   * Bấm nút **"Xuất báo cáo (CSV)"**.
   * Chọn thư mục lưu trên máy (ví dụ `Desktop/Bao_Cao_Su_Co.csv`).
   * Màn hình thông báo xuất file thành công.
4. **Kiểm tra file trên Microsoft Excel:**
   * Mở trực tiếp file `Bao_Cao_Su_Co.csv` vừa xuất bằng phần mềm **Microsoft Excel** (chỉ cần double click vào file).
   * **Quan sát hiển thị:**
     * Toàn bộ chữ tiếng Việt có dấu (như *"Hỏng thiết bị giảng đường"*, *"Đã sửa chữa và kiểm tra an toàn"*, *"Người báo chủ động hủy"*) **hiển thị chuẩn 100%, tuyệt đối không bị lỗi font hay ký tự lạ** nhờ chuẩn mã hóa UTF-8 BOM (`\uFEFF`).
     * Đầy đủ các cột nghiệp vụ: `Mã sự cố`, `Thời gian tạo`, `Thời gian hoàn tất`, `Vị trí`, `Phân loại`, `Mức độ`, `Trạng thái`, `Người báo`, `Người xử lý`, `Ghi chú nghiệm thu`.
     * Người dùng có thể dùng chức năng Filter của Excel để lọc ngày tháng, làm báo cáo gửi cấp trên một cách dễ dàng.

✅ **Kết quả mong đợi:** Lọc đa chiều mượt mà, file CSV tương thích hoàn hảo với Excel, phục vụ công tác thanh tra báo cáo.

---

### Kịch bản 10: Xử lý ngắt kết nối mạng đột ngột

| Mã kịch bản | TC-10 |
|:---|:---|
| **Mục tiêu** | Kiểm tra độ bền vững (Robustness) của kết nối Socket TCP khi Server bị tắt ngang hoặc mất mạng, trạng thái phục hồi khi kết nối lại. |
| **Tài khoản** | Bất kỳ |

#### Các bước thực hiện:
1. Khi Client đang mở bình thường, hãy **tắt cửa sổ Server** (bấm nút Stop trên NetBeans hoặc tắt cửa sổ CMD).
2. Tại màn hình Client, bấm thử một tác vụ (ví dụ: gửi tin nhắn hoặc gửi báo cáo mới):
   * Hệ thống không bị treo hoặc sập ứng dụng (No Crash).
   * Chấm đèn trạng thái chuyển sang màu đỏ: `Mất kết nối máy chủ`.
   * Có hộp thoại cảnh báo: *"Mất kết nối với máy chủ. Vui lòng kiểm tra lại mạng hoặc đăng nhập lại."*
3. Bật lại Server.
4. Tại Client, bấm Đăng xuất -> Đăng nhập lại:
   * Kết nối lại thành công, toàn bộ dữ liệu lịch sử trong SQLite được nạp đầy đủ nguyên vẹn.

✅ **Kết quả mong đợi:** Bắt lỗi ngoại lệ (Try-Catch) an toàn, không sập app đột ngột, khôi phục dữ liệu toàn vẹn.

---

## 4. Bảng Checklist nghiệm thu tổng kết

Trước khi bước vào buổi bảo vệ đồ án, hãy thực hiện lần lượt các mục dưới đây và tích dấu `[x]` để đảm bảo hệ thống đạt điểm tối đa:

- [ ] **Môi trường & Cài đặt:**
  - [ ] NetBeans nhận diện dự án dưới dạng `Maven Reactor` (biểu tượng chữ M).
  - [ ] CSDL SQLite tự động tạo file `database/incident_report.db` và nạp dữ liệu mẫu khi chạy lần đầu.
  - [ ] File ảnh upload lưu tự động vào thư mục `uploads/`.

- [ ] **Giao diện & Cấu hình Đăng nhập:**
  - [ ] Nút 1-click chuyển đổi nhanh `🖥️ Mặc định (Local)` và `🌐 Radmin VPN` hoạt động mượt mà.
  - [ ] Ứng dụng tự động lưu nhớ IP/Port cuối cùng đã chọn qua các lần tắt/mở app.
  - [ ] Các chip tài khoản demo click vào là tự điền và đăng nhập ngay.
  - [ ] Mật khẩu có nút con mắt ẩn/hiện tiện lợi.
  - [ ] Giao diện tự động thích ứng (Responsive) đẹp mắt trên cả laptop 13 inch và 16 inch (chiều cao tự căn lọt thỏm phía trên taskbar, không bị che khuất).

- [ ] **Luồng Báo cáo & Điều phối Sự cố:**
  - [ ] Có hộp thoại xác nhận đầy đủ thông tin trước khi tạo sự cố mới.
  - [ ] Gửi thành công có chuông báo tinh tang.
  - [ ] Server tự định tuyến sự cố đến đúng phòng ban chuyên trách (Kỹ thuật / Y tế / An ninh).
  - [ ] Banner thông báo khẩn cấp nổi góc phải và chuông cảnh báo to rõ ở màn hình cán bộ tiếp nhận.
  - [ ] Bộ lọc theo Ngày, Tháng, Năm hoạt động mượt mà tại "Sự cố của tôi", "Nhiệm vụ của tôi", và "Giám sát & Điều phối".

- [ ] **Tiếp nhận, Phân công & Chống tranh chấp:**
  - [ ] Cán bộ bấm nhận việc có hộp thoại xác nhận; giao diện chuyển "Đang xử lý".
  - [ ] Giáo viên nhận thông báo ngay khi có cán bộ nhận việc.
  - [ ] Hai người cùng nhận 1 sự cố thì người thứ hai bị từ chối (Chống đè việc).
  - [ ] BGH có thể chỉ định cán bộ xử lý; sau khi giao việc nút "Giao việc" tự biến mất.

- [ ] **Trao đổi & Nghiệm thu:**
  - [ ] Khung chat 1-1 hoạt động real-time, có âm thanh "Pop", lưu lịch sử đầy đủ.
  - [ ] Hoàn thành sự cố có biên bản nghiệm thu, nút "Xác nhận hoàn thành" không bị cắt chữ.
  - [ ] Giáo viên nhận thông báo hoàn tất kèm ghi chú nghiệm thu.
  - [ ] Khi đã hoàn tất thì khóa khung chat.

- [ ] **Kiểm thử biên & Báo cáo:**
  - [ ] Hủy sự cố ở trạng thái "Chờ tiếp nhận" ghi chú tiếng Việt có dấu chuẩn: `Người báo chủ động hủy`.
  - [ ] Đã hoàn thành hoặc đang xử lý thì không thể hủy (có thông báo lỗi giải thích rõ).
  - [ ] Nút bấm trên mọi hộp thoại đều hiển thị đủ chữ (`Xác nhận gửi`, `Hủy sự cố`, `Xác nhận hoàn thành`).
  - [ ] Xuất báo cáo CSV mở bằng Excel hiển thị tiếng Việt chuẩn 100% không lỗi font (UTF-8 BOM).
  - [ ] Mất mạng có try-catch thông báo, không văng app.

---
> 🏆 **Chúc Nhóm 11 tự tin thực hiện bài kiểm thử nghiệm thu và đạt kết quả xuất sắc nhất trong buổi bảo vệ môn học!**







