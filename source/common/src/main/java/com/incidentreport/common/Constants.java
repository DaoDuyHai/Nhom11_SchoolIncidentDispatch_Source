package com.incidentreport.common;

public class Constants {
    // =========================================================================
    // 1. GIAO THỨC ĐÓNG KHUNG NHỊ PHÂN (LENGTH-PREFIXED FRAMING)
    // =========================================================================
    public static final short MAGIC_HEADER = (short) 0xCAFE;

    // Các mã loại bản tin (Message Types)
    public static final byte TYPE_LOGIN            = 0x01; // Client -> Server (Header: LOGIN|username=..|password=..)
    public static final byte TYPE_LOGIN_RESP       = 0x11; // Server -> Client (Header: LOGIN_RESP|status=OK/FAIL|role=..)
    public static final byte TYPE_SUBMIT_INCIDENT  = 0x02; // Reporter -> Server (Header: INCIDENT|room=..|type=..|severity=..|desc=.., Attachment: byte[] ảnh)
    public static final byte TYPE_SUBMIT_RESP      = 0x12; // Server -> Reporter (Header: SUBMIT_RESP|ticketCode=..|isMerged=0/1|count=..)
    public static final byte TYPE_DISPATCH_PUSH    = 0x03; // Server -> Responder (Header: DISPATCH_PUSH|ticketId=..|code=..|room=..|type=..|severity=..|desc=..|ackTimeout=30, Attachment: byte[] ảnh)
    public static final byte TYPE_ACK_TASK         = 0x04; // Responder -> Server (Header: ACK_TASK|ticketId=..|action=ACCEPT/REJECT|reason=..)
    public static final byte TYPE_ACK_RESP         = 0x14; // Server -> Responder (Header: ACK_RESP|ticketId=..|status=OK/TASK_ALREADY_TAKEN)
    public static final byte TYPE_STATUS_UPDATE    = 0x05; // Responder -> Server hoặc Server Push (Header: STATUS_UPDATE|ticketId=..|status=IN_PROGRESS/RESOLVED|note=..)
    public static final byte TYPE_ERROR_NOTIFY     = 0x06; // Server -> Client (Header: ERROR|code=..|message=..)
    public static final byte TYPE_MULTICAST_ALERT  = 0x07; // Admin -> Server hoặc Broadcast (Header: ALERT|level=..|message=..)
    public static final byte TYPE_HEARTBEAT_TCP    = 0x08; // TCP Ping/Pong
    public static final byte TYPE_LIST_MY_TICKETS  = 0x09; // Client -> Server (Lấy danh sách ticket của tôi)
    public static final byte TYPE_LIST_TICKETS_RESP= 0x19; // Server -> Client (JSON danh sách ticket)
    public static final byte TYPE_GET_ALL_TICKETS  = 0x0A; // Admin/Responder -> Server (Lấy toàn bộ sự cố)
    public static final byte TYPE_LOGOUT           = 0x0B; // Client -> Server

    // =========================================================================
    // 2. CẤU HÌNH MẠNG MẶC ĐỊNH
    // =========================================================================
    public static final String DEFAULT_SERVER_HOST = "127.0.0.1";
    public static final int TCP_PORT = 5000;
    public static final int UDP_HEARTBEAT_PORT = 5001;
    public static final String MULTICAST_GROUP_IP = "230.0.0.1";
    public static final int MULTICAST_PORT = 9999;

    public static final int UDP_HEARTBEAT_INTERVAL_SEC = 3;  // Responder gửi 3s/lần
    public static final int UDP_HEARTBEAT_TIMEOUT_MS = 10000; // Quá 10s -> Auto-Failover
    public static final int DISPATCH_ACK_TIMEOUT_SEC = 30;   // Responder có 30s để bấm nhận

    // =========================================================================
    // 3. VAI TRÒ & PHÂN QUYỀN (ROLES & SPECIALTIES)
    // =========================================================================
    public static final String ROLE_REPORTER   = "REPORTER";   // GV, Học sinh
    public static final String ROLE_RESPONDER  = "RESPONDER";  // Kỹ thuật, Y tế, Bảo vệ
    public static final String ROLE_ADMIN      = "ADMIN";      // Ban Giám Hiệu

    public static final String SUB_ROLE_TEACHER        = "TEACHER";
    public static final String SUB_ROLE_STUDENT        = "STUDENT";
    public static final String SUB_ROLE_TECH_STAFF     = "TECH_STAFF";
    public static final String SUB_ROLE_MEDICAL_STAFF  = "MEDICAL_STAFF";
    public static final String SUB_ROLE_SECURITY_GUARD = "SECURITY_GUARD";
    public static final String SUB_ROLE_PRINCIPAL      = "PRINCIPAL";

    public static final String SPECIALTY_NONE      = "NONE";
    public static final String SPECIALTY_TECHNICAL = "TECHNICAL";
    public static final String SPECIALTY_MEDICAL   = "MEDICAL";
    public static final String SPECIALTY_SECURITY  = "SECURITY";
    public static final String SPECIALTY_ALL       = "ALL";

    // =========================================================================
    // 4. TRẠNG THÁI TICKET (TICKET STATUS)
    // =========================================================================
    public static final String STATUS_PENDING     = "PENDING";     // Chờ điều phối
    public static final String STATUS_ASSIGNED    = "ASSIGNED";    // Đã giao nhân viên
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS"; // Đang xử lý
    public static final String STATUS_RESOLVED    = "RESOLVED";    // Đã giải quyết xong
    public static final String STATUS_CANCELLED   = "CANCELLED";   // Đã hủy

    // Trạng thái nhân sự (Availability)
    public static final String AVAIL_OFFLINE   = "OFFLINE";
    public static final String AVAIL_AVAILABLE = "AVAILABLE";
    public static final String AVAIL_BUSY      = "BUSY";

    // Trạng thái Dispatch Log
    public static final String DISPATCH_PUSHED         = "PUSHED";
    public static final String DISPATCH_ACCEPTED       = "ACCEPTED";
    public static final String DISPATCH_REJECTED       = "REJECTED";
    public static final String DISPATCH_ACK_TIMEOUT    = "ACK_TIMEOUT";
    public static final String DISPATCH_HEARTBEAT_LOST = "HEARTBEAT_LOST";
    public static final String DISPATCH_COMPLETED      = "COMPLETED";

    // Cấp độ Cảnh báo Multicast
    public static final String ALERT_CRITICAL   = "CRITICAL";
    public static final String ALERT_EVACUATION = "EVACUATION";
    public static final String ALERT_DRILL      = "DRILL";
    public static final String ALERT_ALL_CLEAR  = "ALL_CLEAR";
}