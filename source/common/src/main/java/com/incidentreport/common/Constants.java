package com.incidentreport.common;

/**
 * Hằng số giao thức mạng và phân quyền hệ thống (Basic Core Architecture)
 */
public class Constants {

    // =========================================================================
    // 1. GIAO THỨC ĐÓNG KHUNG NHỊ PHÂN (LENGTH-PREFIXED FRAMING)
    // Format: [Magic: 0xCAFE (2B)] + [Type (1B)] + [HeaderLen (4B)] + [Header (NB)] + [AttachLen (4B)] + [AttachBytes (MB)]
    // =========================================================================
    public static final short MAGIC_HEADER = (short) 0xCAFE;

    // Các mã loại bản tin (OpCodes)
    public static final byte TYPE_LOGIN             = 0x01; // Client -> Server (Header: LOGIN|username=..|password=..)
    public static final byte TYPE_LOGIN_RESP        = 0x11; // Server -> Client (Header: LOGIN_RESP|status=OK/FAIL|role=..)

    public static final byte TYPE_SUBMIT_INCIDENT   = 0x02; // Reporter -> Server (Header: INCIDENT|room=..|category=..|desc=.., Attachment: image)
    public static final byte TYPE_SUBMIT_RESP       = 0x12; // Server -> Reporter (Header: SUBMIT_RESP|ticketCode=..|status=OK/FAIL)

    public static final byte TYPE_DISPATCH_NOTIFY   = 0x03; // Server -> Responder (Header: DISPATCH_NOTIFY|ticketId=..|code=..|room=..|desc=..)
    public static final byte TYPE_CLAIM_TASK        = 0x04; // Responder -> Server (Header: CLAIM_TASK|ticketId=..)
    public static final byte TYPE_CLAIM_RESP        = 0x14; // Server -> Responder (Header: CLAIM_RESP|ticketId=..|status=OK/ALREADY_TAKEN)

    public static final byte TYPE_STATUS_UPDATE     = 0x05; // Responder/Admin -> Server (Header: STATUS_UPDATE|ticketId=..|status=..|note=..)
    public static final byte TYPE_STATUS_BROADCAST  = 0x15; // Server -> All Clients (Header: STATUS_BROADCAST|ticketId=..|status=..|staff=..)

    public static final byte TYPE_LIST_TICKETS      = 0x06; // Client -> Server (Header: LIST_TICKETS|scope=MY/DEPARTMENT/ALL)
    public static final byte TYPE_LIST_TICKETS_RESP = 0x16; // Server -> Client (Attachment: JSON tickets list)

    public static final byte TYPE_CHAT_SEND         = 0x07; // Client -> Server (Header: CHAT_SEND|ticketId=.., Attachment: UTF-8 message)
    public static final byte TYPE_CHAT_PUSH         = 0x17; // Server -> Target Client (Header: CHAT_PUSH|ticketId=..|sender=.., Attachment: UTF-8 message)
    public static final byte TYPE_CHAT_HISTORY      = 0x08; // Client -> Server (Header: CHAT_HISTORY|ticketId=..)
    public static final byte TYPE_CHAT_HISTORY_RESP = 0x18; // Server -> Client (Attachment: JSON chat history)

    public static final byte TYPE_ADMIN_CREATE_USER = 0x09; // Admin -> Server (Header: ADMIN_CREATE_USER|username=..|fullName=..|phone=..|role=..|specialty=.., Attachment: password)
    public static final byte TYPE_ADMIN_CREATE_RESP = 0x19; // Server -> Admin (Header: ADMIN_CREATE_RESP|status=OK/FAIL|message=..)

    public static final byte TYPE_ADMIN_GET_USERS   = 0x0A; // Admin -> Server
    public static final byte TYPE_ADMIN_GET_USERS_RESP = 0x1A; // Server -> Admin (Attachment: JSON users list)

    public static final byte TYPE_ADMIN_GET_STATS   = 0x0B; // Admin -> Server (Header: GET_STATS|timeRange=DAY/MONTH/QUARTER/YEAR/ALL)
    public static final byte TYPE_ADMIN_GET_STATS_RESP = 0x1B; // Server -> Admin (Attachment: JSON StatisticReport)

    public static final byte TYPE_MANUAL_ASSIGN     = 0x0E; // Admin -> Server (Header: MANUAL_ASSIGN|ticketId=..|staffId=..|note=..)
    public static final byte TYPE_MANUAL_ASSIGN_RESP= 0x1E; // Server -> Admin (Header: MANUAL_ASSIGN_RESP|status=OK/FAIL)

    public static final byte TYPE_GET_IMAGE         = 0x0D; // Client -> Server (Header: GET_IMAGE|ticketId=..)
    public static final byte TYPE_GET_IMAGE_RESP    = 0x1D; // Server -> Client (Header: GET_IMAGE_RESP|ticketId=..|status=OK, Attachment: Image bytes)

    public static final byte TYPE_LOGOUT            = 0x0C; // Client -> Server
    public static final byte TYPE_ERROR             = 0x0F; // Server -> Client (Header: ERROR|message=..)

    // =========================================================================
    // 2. CẤU HÌNH MẠNG MẶC ĐỊNH
    // =========================================================================
    public static final String DEFAULT_SERVER_HOST = "127.0.0.1";
    public static final int TCP_PORT = 5000;

    // =========================================================================
    // 3. VAI TRÒ & PHÒNG BAN CHUYÊN TRÁCH
    // =========================================================================
    public static final String ROLE_ADMIN      = "ADMIN";      // Server / Ban Giám Hiệu
    public static final String ROLE_REPORTER   = "REPORTER";   // Giáo viên, Học sinh
    public static final String ROLE_RESPONDER  = "RESPONDER";  // Nhân sự phòng ban

    public static final String SPECIALTY_NONE       = "NONE";
    public static final String SPECIALTY_TECHNICAL  = "TECHNICAL"; // Phòng Kỹ thuật (Điện, Nước, Thiết bị)
    public static final String SPECIALTY_MEDICAL    = "MEDICAL";   // Phòng Y tế học đường
    public static final String SPECIALTY_SECURITY   = "SECURITY";  // Đội Bảo vệ / An ninh
    public static final String SPECIALTY_ALL        = "ALL";

    // =========================================================================
    // 4. TRẠNG THÁI SỰ CỐ
    // =========================================================================
    public static final String STATUS_PENDING     = "PENDING";     // Đang chờ tiếp nhận
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS"; // Đang xử lý
    public static final String STATUS_RESOLVED    = "RESOLVED";    // Đã giải quyết xong
    public static final String STATUS_CANCELLED   = "CANCELLED";   // Đã hủy
}
