package com.incidentreport.common.packet;

import com.incidentreport.common.Constants;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Đóng khung giao thức nhị phân chuẩn (Length-Prefixed Framing Protocol):
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
    public byte[] getAttachment() { return attachmentBytes; }
    public int getAttachmentLength() { return attachmentBytes != null ? attachmentBytes.length : 0; }
    public boolean hasAttachment() { return attachmentBytes != null && attachmentBytes.length > 0; }

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

    public static Packet readFrom(DataInputStream in) throws IOException {
        short magic = in.readShort();
        if (magic != Constants.MAGIC_HEADER) {
            throw new IOException(String.format("Sai Magic Header: 0x%04X (kỳ vọng 0x%04X)", magic, Constants.MAGIC_HEADER));
        }

        byte type = in.readByte();

        int headerLen = in.readInt();
        if (headerLen < 0 || headerLen > 64 * 1024) { // Tối đa 64KB header
            throw new IOException("Độ dài header không hợp lệ: " + headerLen);
        }
        byte[] headerBytes = new byte[headerLen];
        if (headerLen > 0) {
            in.readFully(headerBytes);
        }
        String headerPayload = new String(headerBytes, StandardCharsets.UTF_8);

        int attachLen = in.readInt();
        if (attachLen < 0 || attachLen > 10 * 1024 * 1024) { // Tối đa 10MB file đính kèm
            throw new IOException("Độ dài đính kèm quá lớn: " + attachLen);
        }
        byte[] attachmentBytes = new byte[attachLen];
        if (attachLen > 0) {
            in.readFully(attachmentBytes);
        }

        return new Packet(type, headerPayload, attachmentBytes);
    }

    /**
     * Phân tích Header định dạng PIPE-DELIMITED: COMMAND|KEY=VALUE|KEY=VALUE
     */
    public Map<String, String> parseParams() {
        Map<String, String> map = new HashMap<>();
        if (headerPayload == null || headerPayload.isEmpty()) {
            return map;
        }

        String[] tokens = headerPayload.split("\\|");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            int eqIdx = token.indexOf('=');
            if (eqIdx > 0) {
                String key = token.substring(0, eqIdx).trim();
                String val = token.substring(eqIdx + 1).trim();
                map.put(key, val);
            } else {
                if (i == 0) {
                    map.put("COMMAND", token);
                }
                map.put(token, "true");
            }
        }
        return map;
    }

    @Override
    public String toString() {
        return String.format("Packet[Type=0x%02X, Header='%s', AttachLen=%d]",
                type, headerPayload, getAttachmentLength());
    }
}
