# BÁO CÁO THỰC NGHIỆM: KIỂM THỬ SỨC CHỨA & GIỚI HẠN CHỊU TẢI (STRESS LIMIT FINDER)

**Hệ thống:** School Incident Dispatch System (SIDS)  
**Mục tiêu:** Đo lường sức chứa tối đa số lượng người dùng trực tuyến đồng thời (Concurrent Online Sockets), xác định điểm gãy (Breaking Point) và đánh giá độ ổn định của kiến trúc Socket TCP đa luồng.  
**Địa chỉ Server thử nghiệm:** `26.239.191.202:5000` (Mạng diện rộng qua Radmin VPN)

---

## 1. Môi trường Thực nghiệm Phần cứng & Mạng

| Thông số | Máy Chủ (Server - Đối tượng thử nghiệm) | Máy Trạm Sinh Tải (Benchmark Client) |
| :--- | :--- | :--- |
| **Vai trò** | Tiếp nhận Socket, xác thực, quản lý Session & CSDL | Tạo hàng trăm kết nối luồng song song (Multi-threading) |
| **CPU** | *(Đang cập nhật - ví dụ: Intel Core i3...)* | *(Đang cập nhật - ví dụ: Intel Core i5/i7...)* |
| **RAM** | *(Đang cập nhật - ví dụ: 4 GB)* | *(Đang cập nhật - ví dụ: 8 GB / 16 GB)* |
| **Hệ điều hành** | Windows | Windows |
| **Môi trường thực thi** | Java JDK 17 (JVM 64-bit) | Java JDK 17 (JVM 64-bit) |
| **Kết nối mạng** | Radmin VPN (Mạng riêng ảo P2P) | Radmin VPN (Mạng riêng ảo P2P) |
| **Ping trung bình** | ~5 - 15 ms | ~5 - 15 ms |

---

## 2. Bảng Nhật Ký Thực Nghiệm (Chi tiết 20 - 30 lần chạy)

> *Ghi chú:* Mỗi lần test bao gồm:
> 1. Pha Ramp-up: Các client lần lượt mở Socket TCP và Đăng nhập.
> 2. Pha Duy trì: Giữ nguyên toàn bộ kết nối sống liên tục trong 8 giây.
> 3. Pha Health-check: Gửi truy vấn lấy danh sách sự cố ngẫu nhiên để đo độ trễ phản hồi khi server đang chịu tải.

| Lần | Số User thử nghiệm | Thành công | Thất bại | Tỷ lệ (%) | T/g Ramp-up (s) | Độ trễ mẫu (ms) | RAM Client tốn (MB) | Đánh giá & Hiện tượng ghi nhận |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **#01** | *(Chờ cập nhật)* | | | | | | | |
| **#02** | *(Chờ cập nhật)* | | | | | | | |
| **#03** | *(Chờ cập nhật)* | | | | | | | |
| **#04** | *(Chờ cập nhật)* | | | | | | | |
| **#05** | *(Chờ cập nhật)* | | | | | | | |
| **#06** | *(Chờ cập nhật)* | | | | | | | |
| **#07** | *(Chờ cập nhật)* | | | | | | | |
| **#08** | *(Chờ cập nhật)* | | | | | | | |
| **#09** | *(Chờ cập nhật)* | | | | | | | |
| **#10** | *(Chờ cập nhật)* | | | | | | | |
| **#11** | *(Chờ cập nhật)* | | | | | | | |
| **#12** | *(Chờ cập nhật)* | | | | | | | |
| **#13** | *(Chờ cập nhật)* | | | | | | | |
| **#14** | *(Chờ cập nhật)* | | | | | | | |
| **#15** | *(Chờ cập nhật)* | | | | | | | |
| **#16** | *(Chờ cập nhật)* | | | | | | | |
| **#17** | *(Chờ cập nhật)* | | | | | | | |
| **#18** | *(Chờ cập nhật)* | | | | | | | |
| **#19** | *(Chờ cập nhật)* | | | | | | | |
| **#20** | *(Chờ cập nhật)* | | | | | | | |
| **#21** | *(Chờ cập nhật)* | | | | | | | |
| **#22** | *(Chờ cập nhật)* | | | | | | | |
| **#23** | *(Chờ cập nhật)* | | | | | | | |
| **#24** | *(Chờ cập nhật)* | | | | | | | |
| **#25** | *(Chờ cập nhật)* | | | | | | | |
| **#26** | *(Chờ cập nhật)* | | | | | | | |
| **#27** | *(Chờ cập nhật)* | | | | | | | |
| **#28** | *(Chờ cập nhật)* | | | | | | | |
| **#29** | *(Chờ cập nhật)* | | | | | | | |
| **#30** | *(Chờ cập nhật)* | | | | | | | |

---

## 3. Tổng Hợp Số Liệu Theo Các Dải Tải

*(Sẽ tự động tính toán giá trị trung bình sau khi hoàn tất các lần chạy)*

| Dải Tải (User) | Số lần test | Tỷ lệ thành công TB (%) | Độ trễ trung bình (ms) | Mức độ ổn định |
| :--- | :---: | :---: | :---: | :--- |
| **Tải an toàn (< 150)** | - | - | - | Rất mượt mà |
| **Tải cao (150 - 300)** | - | - | - | Ổn định, đáp ứng tốt quy mô trường học |
| **Tải cực đại (300 - 600)** | - | - | - | Bắt đầu có độ dốc trễ |
| **Ngưỡng cực hạn (600 - 1000)** | - | - | - | Điểm gãy tài nguyên (Breaking Point) |

---

## 4. Biểu Đồ Diễn Biến Độ Trễ & Tỷ Lệ Thành Công

```
(Sẽ vẽ biểu đồ ASCII và đồ thị tương quan sau khi có số liệu)
```

---

## 5. Phân Tích Kỹ Thuật & Điểm Nghẽn (Bottlenecks)

1. **Hiệu năng cấp phát luồng (Thread-per-client / ThreadPool):**
   * *(Phân tích sau test)*
2. **Hàng đợi kết nối Socket của Hệ điều hành (TCP Backlog):**
   * *(Phân tích sau test)*
3. **Tiêu thụ bộ nhớ JVM (Heap Memory & Thread Stack):**
   * *(Phân tích sau test)*

---

## 6. Kết Luận Kiến Trúc & Khuyến Nghị Thực Tế
* *(Đưa ra kết luận hệ thống có thể đáp ứng cho trường học bao nhiêu học sinh/giáo viên)*
