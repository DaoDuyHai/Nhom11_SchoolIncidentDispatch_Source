package com.incidentreport.common.packet;

import com.incidentreport.common.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử Đơn vị: Giao thức đóng khung nhị phân Packet (0xCAFE)")
public class PacketTest {

    @Test
    @DisplayName("Kiểm tra serialize và deserialize Packet cơ bản không kèm attachment")
    void testBasicPacketRoundtrip() throws IOException {
        String payload = "LOGIN|username=hs_minh|password=123456";
        Packet original = new Packet(Constants.TYPE_LOGIN, payload);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        original.writeTo(dos);

        byte[] rawBytes = baos.toByteArray();
        assertTrue(rawBytes.length > 7, "Độ dài byte stream phải lớn hơn 7 byte header");

        ByteArrayInputStream bais = new ByteArrayInputStream(rawBytes);
        DataInputStream dis = new DataInputStream(bais);
        Packet deserialized = Packet.readFrom(dis);

        assertEquals(Constants.MAGIC_HEADER, deserialized.getMagicHeader());
        assertEquals(Constants.TYPE_LOGIN, deserialized.getType());
        assertEquals(payload, deserialized.getHeaderPayload());
        assertFalse(deserialized.hasAttachment());
        assertEquals(0, deserialized.getAttachmentLength());
    }

    @Test
    @DisplayName("Kiểm tra serialize và deserialize Packet kèm mảng byte hình ảnh (Binary Attachment)")
    void testPacketWithImageAttachmentRoundtrip() throws IOException {
        String payload = "INCIDENT|room=A201|category=ELECTRIC|severity=4|desc=Chập cầu dao";
        byte[] fakeImageBytes = new byte[2048];
        Arrays.fill(fakeImageBytes, (byte) 0xAB);

        Packet original = new Packet(Constants.TYPE_SUBMIT_INCIDENT, payload, fakeImageBytes);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        original.writeTo(dos);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream dis = new DataInputStream(bais);
        Packet deserialized = Packet.readFrom(dis);

        assertEquals(Constants.TYPE_SUBMIT_INCIDENT, deserialized.getType());
        assertEquals(payload, deserialized.getHeaderPayload());
        assertTrue(deserialized.hasAttachment());
        assertEquals(2048, deserialized.getAttachmentLength());
        assertArrayEquals(fakeImageBytes, deserialized.getAttachmentBytes());
    }

    @Test
    @DisplayName("Kiểm tra từ chối gói tin có Magic Header sai (không phải 0xCAFE)")
    void testInvalidMagicHeaderRejection() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        try {
            dos.writeShort((short) 0xDEAD); // Magic Header sai
            dos.writeByte(Constants.TYPE_LOGIN);
            dos.writeInt(5);
            dos.write("HELLO".getBytes());
            dos.writeInt(0);
            dos.flush();

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            DataInputStream dis = new DataInputStream(bais);

            IOException ex = assertThrows(IOException.class, () -> Packet.readFrom(dis));
            assertTrue(ex.getMessage().contains("Sai Magic Header"));
        } catch (IOException e) {
            fail("Lỗi I/O không mong muốn: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Kiểm tra parseParams trích xuất key-value từ Header Payload")
    void testParseParams() {
        String payload = "INCIDENT|room=B302|category=FACILITY|severity=5|desc=Hỏng quạt trần";
        Packet packet = new Packet(Constants.TYPE_SUBMIT_INCIDENT, payload);

        Map<String, String> params = packet.parseParams();
        assertEquals("INCIDENT", params.get("COMMAND"));
        assertEquals("B302", params.get("room"));
        assertEquals("FACILITY", params.get("category"));
        assertEquals("5", params.get("severity"));
        assertEquals("Hỏng quạt trần", params.get("desc"));
    }
}

