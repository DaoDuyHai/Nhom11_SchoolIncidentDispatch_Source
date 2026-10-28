package com.incidentreport.client.ui;

import com.incidentreport.common.Constants;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.function.Function;

/**
 * ComponentFactory: Quản lý nạp Theme, Icons (Ikonli FontIcon), Badges, TableCells dùng chung và Toast
 * Concept: "Sổ trực nhà trường" (Giấy - Mực - Đồng) theo DESIGN_SYSTEM.md
 */
public class ComponentFactory {

    private static final String THEME_CSS_PATH = "/css/sids-theme.css";

    public static void applyTheme(Scene scene) {
        if (scene != null) {
            URL url = ComponentFactory.class.getResource(THEME_CSS_PATH);
            if (url != null) {
                String css = url.toExternalForm();
                if (!scene.getStylesheets().contains(css)) {
                    scene.getStylesheets().add(css);
                }
            }
        }
    }

    public static void applyTheme(DialogPane dialogPane) {
        if (dialogPane != null) {
            URL url = ComponentFactory.class.getResource(THEME_CSS_PATH);
            if (url != null) {
                String css = url.toExternalForm();
                if (!dialogPane.getStylesheets().contains(css)) {
                    dialogPane.getStylesheets().add(css);
                }
            }
        }
    }

    public static FontIcon createIcon(String iconLiteral, int size, String styleClass) {
        FontIcon icon;
        try {
            icon = new FontIcon(iconLiteral);
        } catch (Exception e) {
            System.err.println("[WARN] Failed to load icon: " + iconLiteral + ", using fallback");
            try {
                icon = new FontIcon("mdi2h-help-circle-outline");
            } catch (Exception ex) {
                icon = new FontIcon();
            }
        }
        icon.setIconSize(size);
        if (styleClass != null && !styleClass.isEmpty()) {
            icon.getStyleClass().add(styleClass);
        }
        return icon;
    }

    public static FontIcon createIcon(String iconLiteral, int size, Color color) {
        FontIcon icon;
        try {
            icon = new FontIcon(iconLiteral);
        } catch (Exception e) {
            System.err.println("[WARN] Failed to load icon: " + iconLiteral + ", using fallback");
            try {
                icon = new FontIcon("mdi2h-help-circle-outline");
            } catch (Exception ex) {
                icon = new FontIcon();
            }
        }
        icon.setIconSize(size);
        if (color != null) {
            icon.setIconColor(color);
        }
        return icon;
    }

    public static Label createDepartmentBadge(String department) {
        Label badge = new Label();
        badge.getStyleClass().add("dept");

        if (department == null) department = Constants.SPECIALTY_TECHNICAL;
        String deptUpper = department.toUpperCase();

        switch (deptUpper) {
            case Constants.SPECIALTY_TECHNICAL:
                badge.setText("KỸ THUẬT");
                badge.getStyleClass().add("dept-tech");
                break;
            case Constants.SPECIALTY_MEDICAL:
                badge.setText("Y TẾ");
                badge.getStyleClass().add("dept-medical");
                break;
            case Constants.SPECIALTY_SECURITY:
                badge.setText("AN NINH");
                badge.getStyleClass().add("dept-security");
                break;
            default:
                badge.setText(deptUpper);
                badge.getStyleClass().add("dept-tech");
                break;
        }
        return badge;
    }

    public static Label createStatusBadge(String status) {
        Label badge = new Label();
        badge.getStyleClass().add("badge");
        badge.setMinWidth(Region.USE_PREF_SIZE);

        if (status == null) status = Constants.STATUS_PENDING;

        Circle dot = new Circle(3.5);
        badge.setGraphic(dot);
        badge.setGraphicTextGap(5);

        switch (status) {
            case Constants.STATUS_IN_PROGRESS:
                badge.setText("Đang xử lý");
                badge.getStyleClass().add("badge-progress");
                dot.setFill(Color.web("#8F6208"));
                break;
            case Constants.STATUS_RESOLVED:
                badge.setText("Đã hoàn tất");
                badge.getStyleClass().add("badge-resolved");
                dot.setFill(Color.web("#2F6F55"));
                break;
            case Constants.STATUS_CANCELLED:
                badge.setText("Đã hủy");
                badge.getStyleClass().add("badge-cancelled");
                dot.setFill(Color.web("#706B5F"));
                break;
            case Constants.STATUS_PENDING:
            default:
                badge.setText("Chờ tiếp nhận");
                badge.getStyleClass().add("badge-pending");
                dot.setFill(Color.web("#A8402F"));
                break;
        }
        return badge;
    }

    public static class NameParts {
        public final String displayName;
        public final String roleOrTitle;

        public NameParts(String displayName, String roleOrTitle) {
            this.displayName = displayName;
            this.roleOrTitle = roleOrTitle;
        }
    }

    public static NameParts parseNameAndTitle(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return new NameParts("Chưa rõ", null);
        }
        String trimmed = fullName.trim();
        int openIdx = trimmed.indexOf('(');
        int closeIdx = trimmed.lastIndexOf(')');
        if (openIdx > 0 && closeIdx > openIdx) {
            String name = trimmed.substring(0, openIdx).trim();
            String title = trimmed.substring(openIdx + 1, closeIdx).trim();
            return new NameParts(name, title);
        }
        return new NameParts(trimmed, null);
    }

    public static Node createInitialsAvatar(String name, boolean isBrass) {
        String initials = extractInitials(name);
        Label av = new Label(initials);
        av.getStyleClass().add("avatar");
        if (isBrass) {
            av.getStyleClass().add("avatar-brass");
        }
        return av;
    }

    public static String extractInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "U";
        NameParts np = parseNameAndTitle(name);
        String clean = np.displayName.replaceAll("(?i)(thầy|cô|bác|chú|anh|chị|em)\\s+", "").trim();
        if (clean.isEmpty()) clean = np.displayName.trim();

        // Chuẩn hóa ký tự tiếng Việt Đ/đ thành D và loại bỏ dấu thanh cho chữ cái avatar
        clean = clean.replace("Đ", "D").replace("đ", "d");
        clean = java.text.Normalizer.normalize(clean, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        String[] parts = clean.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "U";
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        } else {
            char first = parts[0].charAt(0);
            char last = parts[parts.length - 1].charAt(0);
            return ("" + first + last).toUpperCase();
        }
    }

    /**
     * Định dạng thời gian ngắn gọn (14:50 · 28/10) để vừa vặn trong bảng, không bị tràn dòng hay cắt chữ.
     * Chi tiết thời gian đầy đủ được hiển thị trong Tooltip khi rê chuột.
     */
    public static String formatShortTime(String isoTime) {
        if (isoTime == null || isoTime.trim().isEmpty()) return "N/A";
        String trimmed = isoTime.trim();
        try {
            if (trimmed.length() >= 16) {
                String datePart = trimmed.substring(0, 10);
                String timePart = trimmed.substring(11, 16);
                String[] dp = datePart.split("-");
                if (dp.length == 3) {
                    return timePart + " · " + dp[2] + "/" + dp[1];
                }
            }
        } catch (Exception ignored) {}
        return trimmed;
    }

    public static HBox createConnectionIndicator(boolean isConnected) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        Circle dot = new Circle(4);
        Label label = new Label();

        if (isConnected) {
            dot.setFill(Color.web("#2F6F55"));
            label.setText("Đã kết nối");
            label.getStyleClass().setAll("conn-ok");
        } else {
            dot.setFill(Color.web("#A8402F"));
            label.setText("Mất kết nối");
            label.getStyleClass().setAll("conn-lost");
        }
        box.getChildren().addAll(dot, label);
        return box;
    }

    public static void showToast(Pane root, String message, boolean isSuccess) {
        if (root == null) return;

        HBox toast = new HBox(8);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.getStyleClass().addAll("toast", isSuccess ? "toast-success" : "toast-error");
        toast.setManaged(false); // Tuyệt đối không để container cha (BorderPane/VBox) co giãn toast
        toast.setMouseTransparent(true);
        toast.setMaxWidth(360);

        FontIcon icon = createIcon(isSuccess ? "mdi2c-check-circle" : "mdi2a-alert-circle",
                16, isSuccess ? Color.web("#7FD0A8") : Color.web("#F0A99A"));

        Label lbl = new Label(message);
        lbl.setWrapText(true);
        lbl.setMaxWidth(300);
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: 500;");

        toast.getChildren().addAll(icon, lbl);
        toast.setOpacity(0);

        // Neo cố định ở góc dưới bên phải, cách mép 20px
        toast.layoutXProperty().bind(root.widthProperty().subtract(toast.widthProperty()).subtract(20));
        toast.layoutYProperty().bind(root.heightProperty().subtract(toast.heightProperty()).subtract(20));

        root.getChildren().add(toast);
        toast.toFront();

        FadeTransition fadeIn = new FadeTransition(Duration.millis(120), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition pause = new PauseTransition(Duration.millis(1800)); // 1.8 giây tinh gọn, nhanh chóng biến mất

        FadeTransition fadeOut = new FadeTransition(Duration.millis(120), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            toast.layoutXProperty().unbind();
            toast.layoutYProperty().unbind();
            root.getChildren().remove(toast);
        });

        SequentialTransition seq = new SequentialTransition(fadeIn, pause, fadeOut);
        seq.play();
    }

    // =========================================================================
    // 3 TABLECELL DÙNG CHUNG (DESIGN_SYSTEM.md Mục 8.5)
    // =========================================================================

    public static class StatusBadgeCell<S> extends TableCell<S, String> {
        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);
            if (empty || status == null) {
                setText(null);
                setGraphic(null);
            } else {
                setGraphic(createStatusBadge(status));
                setText(null);
                setAlignment(Pos.CENTER_LEFT);
            }
        }
    }

    public static class DeptBadgeCell<S> extends TableCell<S, String> {
        @Override
        protected void updateItem(String dept, boolean empty) {
            super.updateItem(dept, empty);
            if (empty || dept == null) {
                setText(null);
                setGraphic(null);
            } else {
                setGraphic(createDepartmentBadge(dept));
                setText(null);
                setAlignment(Pos.CENTER_LEFT);
            }
        }
    }

    public static class PersonCell<S> extends TableCell<S, String> {
        private final Function<S, String> subTextExtractor;

        public PersonCell() {
            this(null);
        }

        public PersonCell(Function<S, String> subTextExtractor) {
            this.subTextExtractor = subTextExtractor;
        }

        @Override
        protected void updateItem(String fullName, boolean empty) {
            super.updateItem(fullName, empty);
            if (empty || fullName == null || fullName.trim().isEmpty() || "Đang chờ tiếp nhận".equals(fullName) || "Chưa tiếp nhận".equals(fullName)) {
                setTooltip(null);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label unassigned = new Label("Chưa tiếp nhận");
                    unassigned.setStyle("-fx-font-style: italic; -fx-text-fill: -text-400; -fx-font-size: 12px;");
                    setGraphic(unassigned);
                    setText(null);
                }
            } else {
                HBox box = new HBox(8);
                box.setAlignment(Pos.CENTER_LEFT);

                NameParts np = parseNameAndTitle(fullName);
                Node avatar = createInitialsAvatar(fullName, false);

                Label lblName = new Label(np.displayName);
                lblName.setStyle("-fx-font-weight: 600; -fx-text-fill: -text-900; -fx-font-size: 12.5px;");

                box.getChildren().addAll(avatar, lblName);

                String sub = null;
                if (subTextExtractor != null && getTableRow() != null && getTableRow().getItem() != null) {
                    sub = subTextExtractor.apply(getTableRow().getItem());
                }
                if (sub == null || sub.trim().isEmpty()) {
                    sub = np.roleOrTitle;
                }

                String tooltipText = fullName;
                if (sub != null && !sub.trim().isEmpty() && !fullName.contains(sub)) {
                    tooltipText += " · " + sub;
                }
                setTooltip(new Tooltip(tooltipText));
                setGraphic(box);
                setText(null);
                setAlignment(Pos.CENTER_LEFT);
            }
        }
    }
}
