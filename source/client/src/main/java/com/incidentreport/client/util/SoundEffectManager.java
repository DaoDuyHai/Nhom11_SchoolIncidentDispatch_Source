package com.incidentreport.client.util;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================================
 * BỘ TỔNG HỢP ÂM THANH PHẢN HỒI GIAO DIỆN THỜI GIAN THỰC (PURE JAVA AUDIO ENGINE)
 * ============================================================================
 * - Không phụ thuộc file mp3/wav ngoài (không lo mất file, lỗi đường dẫn).
 * - Tự động tổng hợp sóng âm hình sin PCM 44.1kHz có đường bao fade-out êm ái.
 * - Chạy trên luồng nền (Daemon Thread), tuyệt đối không block UI JavaFX.
 * - An toàn tuyệt đối: Tự bỏ qua nếu máy tính không có thiết bị phát âm thanh.
 * ============================================================================
 */
public class SoundEffectManager {

    private static final ExecutorService audioPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SIDS-Audio-Player");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean soundEnabled = true;

    public static void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
    }

    public static boolean isSoundEnabled() {
        return soundEnabled;
    }

    /**
     * Âm thanh 1: BÁO SỰ CỐ MỚI HOẶC CHỈ ĐỊNH ĐIỀU PHỐI (DISPATCH_NOTIFY / ASSIGN)
     * Bộ ba nốt chuông dồn dập, ngân vang, âm lượng lớn để người trực dễ nhận biết (E5 -> G5 -> C6: 659Hz -> 784Hz -> 1046Hz)
     */
    public static void playNotification() {
        if (!soundEnabled) return;
        audioPool.submit(() -> {
            try {
                playTone(659.25, 120, 0.85);
                Thread.sleep(35);
                playTone(783.99, 130, 0.90);
                Thread.sleep(35);
                playTone(1046.50, 220, 0.95);
            } catch (Exception ignored) {}
        });
    }

    /**
     * Âm thanh 2: TIẾP NHẬN SỰ CỐ (CLAIM TASK)
     * Hai nốt xác nhận tiếp nhận việc dứt khoát (D5 -> A5: 587Hz -> 880Hz)
     */
    public static void playClaim() {
        if (!soundEnabled) return;
        audioPool.submit(() -> {
            try {
                playTone(587.33, 110, 0.85);
                Thread.sleep(30);
                playTone(880.00, 180, 0.90);
            } catch (Exception ignored) {}
        });
    }

    /**
     * Âm thanh 3: THAO TÁC HOÀN TẤT THÀNH CÔNG (SUCCESS CHIME)
     * Hai nốt C5 -> G5 (523Hz -> 784Hz) thanh thoát, báo hoàn tất ca sự cố
     */
    public static void playSuccess() {
        if (!soundEnabled) return;
        audioPool.submit(() -> {
            try {
                playTone(523.25, 120, 0.85);
                Thread.sleep(30);
                playTone(783.99, 220, 0.95);
            } catch (Exception ignored) {}
        });
    }

    /**
     * Âm thanh 4: TIN NHẮN CHAT ĐẾN (CHAT POP)
     * Nốt gỗ thanh tao (880Hz, 80ms)
     */
    public static void playChat() {
        if (!soundEnabled) return;
        audioPool.submit(() -> {
            try {
                playTone(880.00, 80, 0.70);
            } catch (Exception ignored) {}
        });
    }

    /**
     * Âm thanh 5: CẢNH BÁO / HỦY BÁO CÁO (WARNING TONE)
     * Hai nốt hạ dần (440Hz -> 349Hz) khi hủy sự cố hoặc cảnh báo
     */
    public static void playWarning() {
        if (!soundEnabled) return;
        audioPool.submit(() -> {
            try {
                playTone(440.00, 120, 0.80);
                Thread.sleep(30);
                playTone(349.23, 180, 0.85);
            } catch (Exception ignored) {}
        });
    }

    /**
     * Hàm sinh sóng âm Sine PCM 16-bit Mono 44.1kHz với đường bao âm lượng (Envelope ADSR)
     */
    private static void playTone(double freqHz, int durationMs, double volume) {
        try {
            float sampleRate = 44100f;
            int numSamples = (int) (durationMs * (sampleRate / 1000f));
            byte[] buffer = new byte[numSamples * 2];

            for (int i = 0; i < numSamples; i++) {
                double time = i / sampleRate;
                double angle = 2.0 * Math.PI * freqHz * time;

                // Đường bao Decay mượt mà để âm thanh không bị tiếng tách (click)
                double envelope = 1.0;
                double attack = 0.05 * numSamples;
                double release = 0.40 * numSamples;
                if (i < attack) {
                    envelope = i / attack;
                } else if (i > numSamples - release) {
                    envelope = (numSamples - i) / release;
                }

                short sample = (short) (Math.sin(angle) * Short.MAX_VALUE * volume * envelope);
                // Little-endian 16-bit PCM
                buffer[2 * i] = (byte) (sample & 0xFF);
                buffer[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
            }

            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format);
                line.start();
                line.write(buffer, 0, buffer.length);
                line.drain();
            }
        } catch (Exception ignored) {
            try {
                java.awt.Toolkit.getDefaultToolkit().beep();
            } catch (Exception ignored2) {}
        }
    }
}

