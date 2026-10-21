package com.incidentreport.server.auth;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quan ly ma OTP xac thuc qua UDP cho chuc nang quen mat khau.
 * Thoi gian song mac dinh cua ma OTP la 60 giay.
 */
public class OtpManager {

    private static final int OTP_EXPIRY_MS = 60 * 1000; // 60 giay
    private static final ConcurrentHashMap<String, OtpEntry> otpCache = new ConcurrentHashMap<>();
    private static final SecureRandom random = new SecureRandom();

    public static class OtpEntry {
        private final String code;
        private final long expiryMillis;

        public OtpEntry(String code, long expiryMillis) {
            this.code = code;
            this.expiryMillis = expiryMillis;
        }

        public String getCode() {
            return code;
        }

        public long getExpiryMillis() {
            return expiryMillis;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryMillis;
        }
    }

    /**
     * Sinh ma OTP 6 chu so ngau nhien va luu vao bo nho tam kem TTL 60s.
     */
    public static String generateOtp(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        String key = username.trim().toLowerCase();
        int num = random.nextInt(1000000);
        String code = String.format("%06d", num);
        long expiry = System.currentTimeMillis() + OTP_EXPIRY_MS;
        otpCache.put(key, new OtpEntry(code, expiry));
        return code;
    }

    /**
     * Kiem tra ma OTP va huy ma sau khi dung (one-time use).
     * @return true neu dung va con han, nguoc lai false.
     */
    public static boolean validateAndConsume(String username, String code) {
        if (username == null || code == null) {
            return false;
        }
        String key = username.trim().toLowerCase();
        OtpEntry entry = otpCache.get(key);
        if (entry == null) {
            return false;
        }

        if (entry.isExpired()) {
            otpCache.remove(key);
            return false;
        }

        if (entry.getCode().equals(code.trim())) {
            otpCache.remove(key);
            return true;
        }

        return false;
    }

    /**
     * Xoa ma OTP cua username neu can.
     */
    public static void clearOtp(String username) {
        if (username != null) {
            otpCache.remove(username.trim().toLowerCase());
        }
    }
}

