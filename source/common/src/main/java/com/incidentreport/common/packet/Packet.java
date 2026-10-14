package com.incidentreport.common.packet;

import com.incidentreport.common.Constants;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Lớp Packet thực hiện đóng khung giao thức nhị phân (Length-Prefixed Framing Protocol):
 * [Magic: 0xCAFE (2B)] + [Type (1B)] + [Header Len (4B)] + [Header UTF-8 (N B)] + [Attachment Len (4B)] + [Attachment Bytes (M B)]
 */
public class Packet {
    private short magicHeader = Constants.MAGIC_HEADER;
    private byte type;
    private String headerPayload;
    private byte[] attachmentBytes;

    public Packet(byte type, String headerPayload, byte[] attachmentBytes) {
        this.magicHeader = Constants.MAGIC_HEADER;
        this.type = type;
        this.headerPayload = headerPayload != null ? headerPayload : "";
        this.attachmentBytes = attachmentBytes != null ? attachmentBytes : new byte[0];
    }

    public Packet(byte type, String headerPayload) {
        this(type, headerPayload, new byte[0]);
    }

    public short getMagicHeader() { return magicHeader; }
    public byte getType() { return type; }
    public String getHeaderPayload() { return headerPayload; }
    public byte[] getAttachmentBytes() { return attachmentBytes; }
    public int getAttachmentLength() { return attachmentBytes != null ? attachmentBytes.length : 0; }
    public boolean hasAttachment() { return attachmentBytes != null && attachmentBytes.length > 0; }

    /**
     * Ghi bản tin ra luồng DataOutputStream theo đúng đặc tả framing
     */
    public void writeTo(DataOutputStream out) throws IOException {
        synchronized (out) {
            out.writeShort(magicHeader);
            out.writeByte(type);
            
            byte[] headerBytes = headerPayload.getBytes(StandardCharsets.UTF_8);
            out.writeInt(headerBytes.length);
            if (headerBytes.length > 0) {
                out.write(headerBytes);
            }
            
            int attachLen = (attachmentBytes != null) ? attachmentBytes.length : 0;
            out.writeInt(attachLen);
            if (attachLen > 0) {
                out.write(attachmentBytes);
            }
            out.flush();
        }
    }

    /**
     * Đọc chính xác 1 bản tin từ luồng DataInputStream
     */
    public static Packet readFrom(DataInputStream in) throws IOException {
        short magic = in.readShort();
        if (magic != Constants.MAGIC_HEADER) {
            throw new IOException("Sai Magic Header! Kỳ vọng 0xCAFE, nhận được: 0x" + Integer.toHexString(magic & 0xFFFF));
        }

        byte type = in.readByte();

        int headerLen = in.readInt();
        if (headerLen < 0 || headerLen > 5 * 1024 * 1024) { // Tối đa 5MB cho header
            throw new IOException("Header length không hợp lệ: " + headerLen);
        }
        byte[] headerBytes = new byte[headerLen];
        if (headerLen > 0) {
            in.readFully(headerBytes);
        }
        String headerPayload = new String(headerBytes, StandardCharsets.UTF_8);

        int attachLen = in.readInt();
        if (attachLen < 0 || attachLen > 25 * 1024 * 1024) { // Tối đa 25MB cho file đính kèm
            throw new IOException("Attachment length không hợp lệ: " + attachLen);
        }
        byte[] attachmentBytes = new byte[attachLen];
        if (attachLen > 0) {
            in.readFully(attachmentBytes);
        }

        return new Packet(type, headerPayload, attachmentBytes);
    }

    /**
     * Trợ giúp trích xuất tham số từ header dạng: CMD|key1=val1|key2=val2...
     */
    public Map<String, String> parseParams() {
        Map<String, String> map = new HashMap<>();
        if (headerPayload == null || headerPayload.isEmpty()) return map;

        String[] parts = headerPayload.split("\\|");
        if (parts.length > 0 && !parts[0].contains("=")) {
            map.put("COMMAND", parts[0].trim());
        }
        for (String part : parts) {
            int eqIdx = part.indexOf('=');
            if (eqIdx > 0) {
                String key = part.substring(0, eqIdx).trim();
                String val = part.substring(eqIdx + 1).trim();
                map.put(key, val);
            }
        }
        return map;
    }

    public String getParam(String key) {
        return parseParams().get(key);
    }

    @Override
    public String toString() {
        return String.format("Packet[type=0x%02X, headerLen=%d, attachLen=%d, header='%s']",
            type, headerPayload.length(), attachmentBytes.length,
            headerPayload.length() > 80 ? headerPayload.substring(0, 80) + "..." : headerPayload);
    }
}
