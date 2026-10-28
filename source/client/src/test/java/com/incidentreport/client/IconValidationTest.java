package com.incidentreport.client;

import com.incidentreport.client.ui.ComponentFactory;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IconValidationTest {

    @Test
    public void testAllIconsUsedInApp() {
        List<String> icons = Arrays.asList(
                "mdi2s-school",
                "mdi2c-chart-box",
                "mdi2c-clipboard-text-search",
                "mdi2a-account-group",
                "mdi2m-message-text",
                "mdi2l-logout",
                "mdi2d-download",
                "mdi2c-clipboard-text",
                "mdi2c-clock-alert-outline",
                "mdi2w-wrench",
                "mdi2c-check-circle-outline",
                "mdi2r-refresh",
                "mdi2m-message-text-outline",
                "mdi2l-lock-outline",
                "mdi2s-shield-search",
                "mdi2s-send",
                "mdi2c-check-circle",
                "mdi2a-alert-circle",
                "mdi2e-eye-outline",
                "mdi2f-file-plus",
                "mdi2f-format-list-bulleted",
                "mdi2i-image-plus",
                "mdi2c-close",
                "mdi2c-close-circle-outline",
                "mdi2c-clipboard-text-outline",
                "mdi2i-inbox-arrow-down",
                "mdi2i-inbox-outline",
                "mdi2c-check-bold",
                "mdi2w-wrench-outline",
                "mdi2w-water",
                "mdi2f-flash",
                "mdi2m-medical-bag",
                "mdi2s-shield-account",
                "mdi2h-hand-pointing-right",
                "mdi2p-play",
                "mdi2c-check",
                "mdi2h-help-circle-outline",
                "mdi2p-phone",
                "mdi2c-clock-outline",
                "mdi2a-account-arrow-right"
        );

        for (String iconLiteral : icons) {
            assertDoesNotThrow(() -> {
                FontIcon icon = ComponentFactory.createIcon(iconLiteral, 16, (javafx.scene.paint.Color) null);
                assertNotNull(icon);
            }, "Icon should load successfully: " + iconLiteral);
        }
    }

    @Test
    public void testUserNameParsingAndInitials() {
        List<String> rawNames = Arrays.asList(
                "Thầy Nguyễn Văn An (Hiệu trưởng)",
                "Cô Lê Thị Mai Hương (Phó Hiệu trưởng)",
                "Cô Trần Thị Lan (GVCN 10A2)",
                "Thầy Đỗ Quốc Hùng (GV Toán - GVCN 11A1)",
                "Anh Hoàng Văn Tuấn (Kỹ thuật Điện 1)",
                "Chị Nguyễn Mai Anh (Y tế học đường 1)",
                "Bác Lê Đình Sơn (Tổ trưởng Bảo vệ)",
                "Lê Hoàng Minh (Lớp trưởng 10A2)",
                "Trần Đăng Khoa (Lớp phó Lao động 11A1)"
        );

        for (String raw : rawNames) {
            ComponentFactory.NameParts np = ComponentFactory.parseNameAndTitle(raw);
            assertNotNull(np.displayName);
            assertFalse(np.displayName.contains("("), "Display name should not contain (: " + np.displayName);
            assertFalse(np.displayName.contains(")"), "Display name should not contain ): " + np.displayName);
            assertNotNull(np.roleOrTitle, "Role/title should be extracted: " + raw);

            String initials = ComponentFactory.extractInitials(raw);
            assertNotNull(initials);
            assertTrue(initials.matches("[A-Z]{1,2}"), "Initials should be 1-2 uppercase letters, got: " + initials);

            System.out.printf("Raw: '%s' -> Name: '%s' | Title: '%s' | Initials: '%s'%n",
                    raw, np.displayName, np.roleOrTitle, initials);
        }
    }
}
