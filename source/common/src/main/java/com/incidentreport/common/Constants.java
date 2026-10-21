package com.incidentreport.common;

public class Constants {
    // =========================================================================
    // 1. GIAO THUC DONG KHUNG NHAN PHAN (LENGTH-PREFIXED FRAMING)
    // =========================================================================
    public static final short MAGIC_HEADER = (short) 0xCAFE;

    // Cac ma loai ban tin (Message Types)
    public static final byte TYPE_LOGIN            = 0x01; // Client -> Server (Header: LOGIN|username=..|password=..)
    public static final byte TYPE_LOGIN_RESP       = 0x11; // Server -> Client (Header: LOGIN_RESP|status=OK/FAIL|role=..)
    public static final byte TYPE_SUBMIT_INCIDENT  = 0x02; // Reporter -> Server (Header: INCIDENT|room=..|type=..|severity=..|desc=.., Attachment: byte[] anh)
    public static final byte TYPE_SUBMIT_RESP      = 0x12; // Server -> Reporter (Header: SUBMIT_RESP|ticketCode=..|isMerged=0/1|count=..)
    public static final byte TYPE_DISPATCH_PUSH    = 0x03; // Server -> Responder (Header: DISPATCH_PUSH|ticketId=..|code=..|room=..|type=..|severity=..|desc=..|ackTimeout=30, Attachment: byte[] anh)
    public static final byte TYPE_ACK_TASK         = 0x04; // Responder -> Server (Header: ACK_TASK|ticketId=..|action=ACCEPT/REJECT|reason=..)
    public static final byte TYPE_ACK_RESP         = 0x14; // Server -> Responder (Header: ACK_RESP|ticketId=..|status=OK/TASK_ALREADY_TAKEN)
    public static final byte TYPE_STATUS_UPDATE    = 0x05; // Responder -> Server hoac Server Push (Header: STATUS_UPDATE|ticketId=..|status=IN_PROGRESS/RESOLVED|note=..)
    public static final byte TYPE_ERROR_NOTIFY     = 0x06; // Server -> Client (Header: ERROR|code=..|message=..)
    public static final byte TYPE_MULTICAST_ALERT  = 0x07; // Admin -> Server hoac Broadcast (Header: ALERT|level=..|message=..)
    public static final byte TYPE_HEARTBEAT_TCP    = 0x08; // TCP Ping/Pong
    public static final byte TYPE_LIST_MY_TICKETS  = 0x09; // Client -> Server (Lay danh sach ticket cua toi)
    public static final byte TYPE_LIST_TICKETS_RESP= 0x19; // Server -> Client (JSON danh sach ticket)
    public static final byte TYPE_GET_ALL_TICKETS  = 0x0A; // Admin/Responder -> Server (Lay toan bo su co)
    public static final byte TYPE_LOGOUT           = 0x0B; // Client -> Server
    public static final byte TYPE_CHAT_SEND        = 0x0C; // Client -> Server (Header: CHAT_SEND|ticketId=.., Attachment: byte[] UTF-8)
    public static final byte TYPE_CHAT_PUSH        = 0x1C; // Server -> Client (Header: CHAT_PUSH|msgId=..|ticketId=..|senderId=..|senderName=..|senderRole=..|sentAt=.., Attachment: byte[] UTF-8)
    public static final byte TYPE_CHAT_HISTORY     = 0x0D; // Client -> Server (Header: CHAT_HISTORY|ticketId=..)
    public static final byte TYPE_CHAT_HISTORY_RESP= 0x1D; // Server -> Client (Header: CHAT_HISTORY_RESP|ticketId=.., Attachment: byte[] JSON UTF-8)
    public static final byte TYPE_MANUAL_ASSIGN    = 0x0E; // Admin -> Server (Header: MANUAL_ASSIGN|ticketId=..|staffId=..|note=..)
    public static final byte TYPE_MANUAL_ASSIGN_RESP=0x1E; // Server -> Admin (Header: MANUAL_ASSIGN_RESP|ticketId=..|status=OK/FAIL|staffName=..)
    public static final byte TYPE_GET_OPEN_POOL    = 0x0F; // Responder -> Server (Lay danh sach su co PENDING)
    public static final byte TYPE_GET_OPEN_POOL_RESP=0x1F; // Server -> Responder (JSON danh sach ticket PENDING)
    public static final byte TYPE_GET_STAFF_LIST   = 0x20; // Admin -> Server (Lay danh sach responders)
    public static final byte TYPE_GET_STAFF_LIST_RESP=0x21;// Server -> Admin (JSON danh sach responders)

    // Auth & Account Management (0x22 - 0x36)
    public static final byte TYPE_REGISTER         = 0x22; // Client -> Server (Header: REGISTER|username=..|fullName=..|phone=..|subRole=.., Attach: password UTF-8)
    public static final byte TYPE_REGISTER_RESP    = 0x32; // Server -> Client (Header: REGISTER_RESP|status=OK/FAIL|message=..)
    public static final byte TYPE_GUEST_LOGIN      = 0x23; // Client -> Server (Header: GUEST_LOGIN)
    public static final byte TYPE_ADMIN_CREATE_USER= 0x24; // Admin -> Server (Header: ADMIN_CREATE_USER|username=..|fullName=..|phone=..|role=..|subRole=..|specialty=.., Attach: password UTF-8)
    public static final byte TYPE_ADMIN_CREATE_RESP= 0x34; // Server -> Admin (Header: ADMIN_CREATE_RESP|status=OK/FAIL|userId=..|message=..)
    public static final byte TYPE_FORGOT_PASSWORD  = 0x25; // Client -> Server (Header: FORGOT_PASSWORD|username=..|udpPort=..)
    public static final byte TYPE_FORGOT_RESP      = 0x35; // Server -> Client (Header: FORGOT_RESP|status=OK/FAIL|message=..)
    public static final byte TYPE_RESET_PASSWORD   = 0x26; // Client -> Server (Header: RESET_PASSWORD|username=..|otp=.., Attach: newPassword UTF-8)
    public static final byte TYPE_RESET_RESP       = 0x36; // Server -> Client (Header: RESET_RESP|status=OK/FAIL|message=..)

    // =========================================================================
    // 2. CAU HINH MANG MAC DINH
    // =========================================================================
    public static final String DEFAULT_SERVER_HOST = "127.0.0.1";
    public static final int TCP_PORT = 5000;
    public static final int UDP_HEARTBEAT_PORT = 5001;
    public static final String MULTICAST_GROUP_IP = "230.0.0.1";
    public static final int MULTICAST_PORT = 9999;

    public static final int UDP_HEARTBEAT_INTERVAL_SEC = 3;  // Responder gui 3s/lan
    public static final int UDP_HEARTBEAT_TIMEOUT_MS = 10000; // Qua 10s -> Auto-Failover
    public static final int DISPATCH_ACK_TIMEOUT_SEC = 30;   // Responder co 30s de bam nhan

    // =========================================================================
    // 3. VAI TRO & PHAN QUYEN (ROLES & SPECIALTIES)
    // =========================================================================
    public static final String ROLE_REPORTER   = "REPORTER";   // GV, Hoc sinh
    public static final String ROLE_RESPONDER  = "RESPONDER";  // Ky thuat, Y te, Bao ve
    public static final String ROLE_ADMIN      = "ADMIN";      // Ban Giam Hieu

    public static final String SUB_ROLE_TEACHER        = "TEACHER";
    public static final String SUB_ROLE_STUDENT        = "STUDENT";
    public static final String SUB_ROLE_GUEST          = "GUEST";
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
    // 4. TRANG THAI TICKET (TICKET STATUS)
    // =========================================================================
    public static final String STATUS_PENDING     = "PENDING";     // Cho dieu phoi
    public static final String STATUS_ASSIGNED    = "ASSIGNED";    // Da giao nhan vien
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS"; // Dang xu ly
    public static final String STATUS_RESOLVED    = "RESOLVED";    // Da giai quyet xong
    public static final String STATUS_CANCELLED   = "CANCELLED";   // Da huy

    // Trang thai nhan su (Availability)
    public static final String AVAIL_OFFLINE   = "OFFLINE";
    public static final String AVAIL_AVAILABLE = "AVAILABLE";
    public static final String AVAIL_BUSY      = "BUSY";

    // Trang thai Dispatch Log
    public static final String DISPATCH_PUSHED         = "PUSHED";
    public static final String DISPATCH_ACCEPTED       = "ACCEPTED";
    public static final String DISPATCH_REJECTED       = "REJECTED";
    public static final String DISPATCH_ACK_TIMEOUT    = "ACK_TIMEOUT";
    public static final String DISPATCH_HEARTBEAT_LOST = "HEARTBEAT_LOST";
    public static final String DISPATCH_COMPLETED      = "COMPLETED";

    // Cap do Canh bao Multicast
    public static final String ALERT_CRITICAL   = "CRITICAL";
    public static final String ALERT_EVACUATION = "EVACUATION";
    public static final String ALERT_DRILL      = "DRILL";
    public static final String ALERT_ALL_CLEAR  = "ALL_CLEAR";
}