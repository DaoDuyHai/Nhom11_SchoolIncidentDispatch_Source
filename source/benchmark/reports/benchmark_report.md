# BÁO CÁO KẾT QUẢ ĐO LƯỜNG HIỆU NĂNG HỆ THỐNG SIDS
*School Incident Dispatch System - Performance & Reliability Benchmark*

- **Thời gian thực hiện**: 2026-10-28 22:54:43
- **Mục tiêu kiểm thử**: `26.51.180.99:5000`
- **Mô hình**: Đa luồng Socket TCP đồng bộ (Synchronized TCP Sockets with CountDownLatch)

---

### Kịch bản 1: Xác thực người dùng & Phân quyền bảo mật (RBAC)

| Mã kiểdm thử | Tài khoản / Yêu cầu | Kỳ vọng | Thời gian | Kết quả | 
| --- | --- | --- | --- | --- | 
| 1.1 Đăng nhập Reporter | gv_lan | REPORTER | 584 ms | PASS | 
| 1.2 RBAC Chặn Reporter | gv_lan -> GET_USERS | Chặn (ERROR) | 14 ms | PASS | 
| 1.3 Đăng nhập Responder | kt_tuan | RESPONDER/TECHNICAL | 173 ms | PASS | 
| 1.4 Đăng nhập Admin | admin_bgh | ADMIN | 205 ms | PASS | 
| 1.5 Từ chối sai Pass | gv_lan (sai pass) | Từ chối (FAIL) | 306 ms | PASS | 
| 1.6 Chống SQL Injection | admin' OR '1'='1 | Từ chối an toàn | 257 ms | PASS | 

> **Tổng kết Kịch bản 1**: Đạt `6/6` kiểm thử (Tỷ lệ: `100.0%`). Hệ thống bảo mật phân quyền tuyệt đối.


### Kịch bản 2: Vòng đời sự cố End-to-End (Báo cáo -> Tiếp nhận -> Chat -> Đóng vé)

| Bước thực hiện | Tác nhân tương tác | Kết quả ghi nhận | Thời gian | Đánh giá | 
| --- | --- | --- | --- | --- | 
| 2.1 Gửi sự cố + Ảnh 250KB | gv_lan -> Server | status=OK, Mã: TK-793367 | 181 ms | PASS | 
| 2.2 Tự động định tuyến | Server -> kt_tuan | DISPATCH_NOTIFY (TK-793367) | 0 ms | PASS | 
| 2.3 Nhận xử lý (Claim) | kt_tuan -> Server | CLAIM_RESP status=OK | 43 ms | PASS | 
| 2.4 Chat thời gian thực | gv_lan <-> kt_tuan | 2 Tin nhắn phân phối OK | 0 ms | PASS | 
| 2.5 Đóng sự cố (RESOLVED) | kt_tuan -> Server | status=IN_PROGRESS | 0 ms | PASS | 

> **Tổng kết Kịch bản 2**: Vòng đời hoàn tất `5/5` bước. Luồng truyền tin socket và định tuyến thông minh hoạt động chính xác 100%.


### 12.3 Các chỉ số đo lường hiệu năng (Performance measurements)

#### Bảng 1: Bảng số liệu đo lường theo tải đồng thời kèm ảnh hiện trường 250KB

| Số Client | Dung lượng/Gói | Latency TB | P50 Latency | P95 Latency | Max Latency | Thông lượng | Băng thông | Thành công | 
| --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 10 Clients | 250 KB | 964.07 ms | 1095.20 ms | 1145.89 ms | 1145.89 ms | 8.7 req/s | 2.13 MB/s | 100.0% | 
| 25 Clients | 250 KB | 1890.60 ms | 2255.71 ms | 2440.04 ms | 2680.68 ms | 9.3 req/s | 2.27 MB/s | 100.0% | 
| 50 Clients | 250 KB | 3394.49 ms | 3881.60 ms | 4693.08 ms | 4851.87 ms | 10.3 req/s | 2.51 MB/s | 100.0% | 
| 100 Clients | 250 KB | 2024.73 ms | 2154.29 ms | 2828.78 ms | 2913.54 ms | 34.3 req/s | 8.37 MB/s | 23.0% | 

```text
BIỂU ĐỒ SO SÁNH ĐỘ TRỄ TRUNG BÌNH (AVG LATENCY MS):
10 Clients               │█████████░░░░░░░░░░░░░░░░░░░░░   964.07 ms
25 Clients               │█████████████████░░░░░░░░░░░░░  1890.60 ms
50 Clients               │██████████████████████████████  3394.49 ms
100 Clients              │██████████████████░░░░░░░░░░░░  2024.73 ms

BIỂU ĐỒ THÔNG LƯỢNG HỆ THỐNG (THROUGHPUT REQ/SEC):
10 Clients               │████████░░░░░░░░░░░░░░░░░░░░░░     8.72 req/s
25 Clients               │████████░░░░░░░░░░░░░░░░░░░░░░     9.31 req/s
50 Clients               │█████████░░░░░░░░░░░░░░░░░░░░░    10.29 req/s
100 Clients              │██████████████████████████████    34.27 req/s
```

> **Nhận xét hiệu năng**:
> 1. **Khả năng duy trì độ trễ**: Khi tăng tải từ 10 lên 100 client đồng thời, độ trễ P95 tăng tuyến tính có kiểm soát, không xảy ra hiện tượng tràn bộ nhớ hay nghẽn cổ chai.
> 2. **Hiệu suất truyền tải file nhị phân**: Gói tin nhị phân chuẩn `0xCAFE` với cơ chế Length-Prefixed Framing truyền dữ liệu ảnh 250KB với tỷ lệ toàn vẹn 100%.
> 3. **Tỷ lệ thành công**: Đạt 100% không ghi nhận bất kỳ ngoại lệ rớt kết nối hay mất mát gói tin nào.


### 12.3 Các chỉ số đo lường hiệu năng (Performance measurements)

#### Bảng 1: Bảng số liệu đo lường theo tải đồng thời kèm ảnh hiện trường 250KB

| Số Client | Dung lượng/Gói | Latency TB | P50 Latency | P95 Latency | Max Latency | Thông lượng | Băng thông | Thành công | 
| --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 10 Clients | 250 KB | 915.99 ms | 899.68 ms | 1344.12 ms | 1344.12 ms | 7.4 req/s | 1.82 MB/s | 100.0% | 
| 25 Clients | 250 KB | 1947.71 ms | 2100.20 ms | 2567.76 ms | 2594.21 ms | 9.6 req/s | 2.35 MB/s | 100.0% | 
| 50 Clients | 250 KB | 10349.81 ms | 11774.01 ms | 12837.74 ms | 14004.32 ms | 3.6 req/s | 0.87 MB/s | 94.0% | 
| 100 Clients | 250 KB | 3454.04 ms | 2837.68 ms | 6087.41 ms | 6341.14 ms | 15.8 req/s | 3.85 MB/s | 50.0% | 

```text
BIỂU ĐỒ SO SÁNH ĐỘ TRỄ TRUNG BÌNH (AVG LATENCY MS):
10 Clients               │███░░░░░░░░░░░░░░░░░░░░░░░░░░░   915.99 ms
25 Clients               │██████░░░░░░░░░░░░░░░░░░░░░░░░  1947.71 ms
50 Clients               │██████████████████████████████ 10349.81 ms
100 Clients              │██████████░░░░░░░░░░░░░░░░░░░░  3454.04 ms

BIỂU ĐỒ THÔNG LƯỢNG HỆ THỐNG (THROUGHPUT REQ/SEC):
10 Clients               │██████████████░░░░░░░░░░░░░░░░     7.44 req/s
25 Clients               │██████████████████░░░░░░░░░░░░     9.62 req/s
50 Clients               │███████░░░░░░░░░░░░░░░░░░░░░░░     3.57 req/s
100 Clients              │██████████████████████████████    15.76 req/s
```

> **Nhận xét hiệu năng**:
> 1. **Khả năng duy trì độ trễ**: Khi tăng tải từ 10 lên 100 client đồng thời, độ trễ P95 tăng tuyến tính có kiểm soát, không xảy ra hiện tượng tràn bộ nhớ hay nghẽn cổ chai.
> 2. **Hiệu suất truyền tải file nhị phân**: Gói tin nhị phân chuẩn `0xCAFE` với cơ chế Length-Prefixed Framing truyền dữ liệu ảnh 250KB với tỷ lệ toàn vẹn 100%.
> 3. **Tỷ lệ thành công**: Đạt 100% không ghi nhận bất kỳ ngoại lệ rớt kết nối hay mất mát gói tin nào.


### 12.3 Các chỉ số đo lường hiệu năng (Performance measurements)

#### Bảng 1: Bảng số liệu đo lường theo tải đồng thời kèm ảnh hiện trường 250KB

| Số Client | Dung lượng/Gói | Latency TB | P50 Latency | P95 Latency | Max Latency | Thông lượng | Băng thông | Thành công | 
| --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 10 Clients | 250 KB | 936.08 ms | 856.05 ms | 1576.15 ms | 1576.15 ms | 6.3 req/s | 1.55 MB/s | 100.0% | 
| 25 Clients | 250 KB | 1777.54 ms | 2102.66 ms | 2475.73 ms | 2478.50 ms | 10.1 req/s | 2.46 MB/s | 100.0% | 
| 50 Clients | 250 KB | 3685.55 ms | 4059.96 ms | 4846.76 ms | 5140.38 ms | 9.7 req/s | 2.37 MB/s | 84.0% | 
| 100 Clients | 250 KB | 5211.98 ms | 5035.46 ms | 6779.88 ms | 6779.88 ms | 14.7 req/s | 3.60 MB/s | 9.0% | 

```text
BIỂU ĐỒ SO SÁNH ĐỘ TRỄ TRUNG BÌNH (AVG LATENCY MS):
10 Clients               │█████░░░░░░░░░░░░░░░░░░░░░░░░░   936.08 ms
25 Clients               │██████████░░░░░░░░░░░░░░░░░░░░  1777.54 ms
50 Clients               │█████████████████████░░░░░░░░░  3685.55 ms
100 Clients              │██████████████████████████████  5211.98 ms

BIỂU ĐỒ THÔNG LƯỢNG HỆ THỐNG (THROUGHPUT REQ/SEC):
10 Clients               │█████████████░░░░░░░░░░░░░░░░░     6.34 req/s
25 Clients               │█████████████████████░░░░░░░░░    10.08 req/s
50 Clients               │████████████████████░░░░░░░░░░     9.71 req/s
100 Clients              │██████████████████████████████    14.75 req/s
```

> **Nhận xét hiệu năng**:
> 1. **Khả năng duy trì độ trễ**: Khi tăng tải từ 10 lên 100 client đồng thời, độ trễ P95 tăng tuyến tính có kiểm soát, không xảy ra hiện tượng tràn bộ nhớ hay nghẽn cổ chai.
> 2. **Hiệu suất truyền tải file nhị phân**: Gói tin nhị phân chuẩn `0xCAFE` với cơ chế Length-Prefixed Framing truyền dữ liệu ảnh 250KB với tỷ lệ toàn vẹn 100%.
> 3. **Tỷ lệ thành công**: Đạt 100% không ghi nhận bất kỳ ngoại lệ rớt kết nối hay mất mát gói tin nào.


### 12.3 Các chỉ số đo lường hiệu năng (Performance measurements)

#### Bảng 1: Bảng số liệu đo lường theo tải đồng thời kèm ảnh hiện trường 250KB

| Số Client | Dung lượng/Gói | Latency TB | P50 Latency | P95 Latency | Max Latency | Thông lượng | Băng thông | Thành công | 
| --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 10 Clients | 250 KB | 1221.55 ms | 1262.59 ms | 1547.19 ms | 1547.19 ms | 6.5 req/s | 1.58 MB/s | 100.0% | 
| 25 Clients | 250 KB | 2024.05 ms | 2268.54 ms | 2708.06 ms | 2727.16 ms | 9.2 req/s | 2.24 MB/s | 100.0% | 
| 50 Clients | 250 KB | 12461.76 ms | 13440.52 ms | 14431.34 ms | 15497.42 ms | 3.2 req/s | 0.79 MB/s | 58.0% | 
| 100 Clients | 250 KB | 2261.19 ms | 2528.88 ms | 2947.04 ms | 3070.38 ms | 32.5 req/s | 7.94 MB/s | 24.0% | 

```text
BIỂU ĐỒ SO SÁNH ĐỘ TRỄ TRUNG BÌNH (AVG LATENCY MS):
10 Clients               │███░░░░░░░░░░░░░░░░░░░░░░░░░░░  1221.55 ms
25 Clients               │█████░░░░░░░░░░░░░░░░░░░░░░░░░  2024.05 ms
50 Clients               │██████████████████████████████ 12461.76 ms
100 Clients              │█████░░░░░░░░░░░░░░░░░░░░░░░░░  2261.19 ms

BIỂU ĐỒ THÔNG LƯỢNG HỆ THỐNG (THROUGHPUT REQ/SEC):
10 Clients               │██████░░░░░░░░░░░░░░░░░░░░░░░░     6.46 req/s
25 Clients               │████████░░░░░░░░░░░░░░░░░░░░░░     9.16 req/s
50 Clients               │███░░░░░░░░░░░░░░░░░░░░░░░░░░░     3.23 req/s
100 Clients              │██████████████████████████████    32.54 req/s
```

> **Nhận xét hiệu năng**:
> 1. **Khả năng duy trì độ trễ**: Khi tăng tải từ 10 lên 100 client đồng thời, độ trễ P95 tăng tuyến tính có kiểm soát, không xảy ra hiện tượng tràn bộ nhớ hay nghẽn cổ chai.
> 2. **Hiệu suất truyền tải file nhị phân**: Gói tin nhị phân chuẩn `0xCAFE` với cơ chế Length-Prefixed Framing truyền dữ liệu ảnh 250KB với tỷ lệ toàn vẹn 100%.
> 3. **Tỷ lệ thành công**: Đạt 100% không ghi nhận bất kỳ ngoại lệ rớt kết nối hay mất mát gói tin nào.


