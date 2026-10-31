package com.incidentreport.client.ui;

import com.incidentreport.client.IncidentReportApp;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.User;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Window;
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

        PauseTransition pause = new PauseTransition(Duration.millis(3500)); // 3.5 giây đảm bảo người dùng kịp đọc nội dung

        FadeTransition fadeOut = new FadeTransition(Duration.millis(150), toast);
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

    /**
     * Thông báo nổi bật thời gian thực (Real-time Urgent Notification Banner)
     * Hiển thị ở góc trên bên phải màn hình, thiết kế nổi bật, có nút đóng [X],
     * hiển thị rõ ràng trong 7 giây và nhấp nháy cửa sổ Taskbar để người dùng không bỏ lỡ.
     */
    public static void showUrgentNotification(Pane root, String title, String message, String type) {
        if (root == null) return;

        // Đẩy cửa sổ ứng dụng lên Taskbar nếu đang ở chế độ nền
        try {
            if (root.getScene() != null && root.getScene().getWindow() != null) {
                Window w = root.getScene().getWindow();
                if (w instanceof javafx.stage.Stage) {
                    ((javafx.stage.Stage) w).toFront();
                }
            }
        } catch (Exception ignored) {}

        HBox banner = new HBox(12);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setManaged(false);
        banner.setPrefWidth(420);
        banner.setMaxWidth(440);
        banner.setPadding(new Insets(12, 16, 12, 16));

        String borderColor = "#CBA447"; // Vàng đồng mặc định
        String iconLiteral = "mdi2c-bell-ring-outline";
        Color iconColor = Color.web("#D4AF37");

        if ("DISPATCH".equalsIgnoreCase(type)) {
            borderColor = "#E74C3C"; // Đỏ cảnh báo sự cố mới
            iconLiteral = "mdi2a-alert-decagram";
            iconColor = Color.web("#E74C3C");
        } else if ("SUCCESS".equalsIgnoreCase(type)) {
            borderColor = "#2ECC71"; // Xanh lá hoàn thành
            iconLiteral = "mdi2c-check-circle";
            iconColor = Color.web("#2ECC71");
        } else if ("CLAIM".equalsIgnoreCase(type)) {
            borderColor = "#3498DB"; // Xanh dương tiếp nhận
            iconLiteral = "mdi2h-hand-pointing-right";
            iconColor = Color.web("#3498DB");
        } else if ("ASSIGN".equalsIgnoreCase(type)) {
            borderColor = "#CBA447"; // Vàng đồng chỉ định
            iconLiteral = "mdi2a-account-arrow-right";
            iconColor = Color.web("#D4AF37");
        }

        banner.setStyle(String.format(
                "-fx-background-color: #14264A; -fx-background-radius: 8px; -fx-border-color: %s; " +
                "-fx-border-width: 1.5px; -fx-border-radius: 8px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.40), 16, 0, 0, 4);", borderColor));

        FontIcon icon = createIcon(iconLiteral, 24, iconColor);

        VBox textBox = new VBox(3);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: white; -fx-wrap-text: true;");
        lblTitle.setWrapText(true);

        Label lblMsg = new Label(message);
        lblMsg.setStyle("-fx-font-size: 12px; -fx-text-fill: #E8DFC8; -fx-line-spacing: 2px; -fx-wrap-text: true;");
        lblMsg.setWrapText(true);
        lblMsg.setMaxWidth(330);

        textBox.getChildren().addAll(lblTitle, lblMsg);

        Button btnClose = new Button();
        btnClose.setGraphic(createIcon("mdi2c-close", 14, Color.web("#C5BDB0")));
        btnClose.setStyle("-fx-background-color: transparent; -fx-padding: 2; -fx-cursor: hand;");

        banner.getChildren().addAll(icon, textBox, btnClose);
        banner.setOpacity(0);

        // Neo cố định ở góc trên bên phải, cách mép trên 20px, cách mép phải 20px
        banner.layoutXProperty().bind(root.widthProperty().subtract(banner.widthProperty()).subtract(20));
        banner.setLayoutY(20);

        root.getChildren().add(banner);
        banner.toFront();

        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), banner);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition pause = new PauseTransition(Duration.millis(7000)); // 7.0 giây hiển thị rõ ràng

        FadeTransition fadeOut = new FadeTransition(Duration.millis(250), banner);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            banner.layoutXProperty().unbind();
            root.getChildren().remove(banner);
        });

        SequentialTransition seq = new SequentialTransition(fadeIn, pause, fadeOut);

        btnClose.setOnAction(e -> {
            seq.stop();
            fadeOut.play();
        });

        seq.play();
    }

    /**
     * Hộp thoại báo lỗi dạng Modal Popup, ngăn hiện tượng cắt chữ và đảm bảo người dùng luôn nhận biết rõ nguyên nhân lỗi.
     */
    public static void showErrorAlert(Window owner, String title, String header, String content) {
        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle(title != null ? title : "Thông báo lỗi");
        applyTheme(dialog.getDialogPane());
        dialog.setHeaderText(header != null ? header : "Thao tác không thành công");
        dialog.getDialogPane().setMinWidth(580);
        dialog.getDialogPane().setPrefWidth(620);

        VBox box = new VBox(12);
        box.setPadding(new Insets(16, 22, 16, 22));
        box.setPrefWidth(580);
        box.setMaxWidth(Double.MAX_VALUE);

        Label l = new Label(content != null ? content : "Đã có lỗi xảy ra trong quá trình xử lý yêu cầu.");
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setStyle("-fx-font-size: 13px; -fx-text-fill: -st-pending; -fx-font-weight: 500; -fx-line-spacing: 5px; -fx-text-overrun: clip;");
        box.getChildren().add(l);
        dialog.getDialogPane().setContent(box);

        ButtonType btnClose = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(btnClose);

        Button b = (Button) dialog.getDialogPane().lookupButton(btnClose);
        if (b != null) {
            ButtonBar.setButtonUniformSize(b, false);
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.getStyleClass().setAll("button", "btn-secondary");
            b.setStyle("-fx-font-size: 12.5px; -fx-padding: 8 24; -fx-cursor: hand; -fx-min-width: -1; -fx-pref-width: -1; -fx-text-overrun: clip;");
        }

        dialog.setOnShown(e -> Platform.runLater(() -> {
            if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
            }
        }));

        dialog.showAndWait();
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

    // =========================================================================
    // HỘP THOẠI HỒ SƠ TÀI KHOẢN (USER PROFILE DIALOG)
    // =========================================================================

    public static void showUserProfileDialog(Window owner, User user) {
        if (user == null) return;

        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Thông tin tài khoản");
        applyTheme(dialog.getDialogPane());

        // Bổ sung CANCEL_CLOSE ButtonType để JavaFX cho phép đóng hộp thoại
        ButtonType closeButtonType = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeButtonType);

        // Ẩn thanh nút mặc định của JavaFX DialogPane để sử dụng thanh nút tùy biến đẹp mắt
        Node defaultCloseBtn = dialog.getDialogPane().lookupButton(closeButtonType);
        if (defaultCloseBtn != null) {
            defaultCloseBtn.setVisible(false);
            defaultCloseBtn.setManaged(false);
        }

        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 20, 24));
        content.setPrefWidth(460);
        content.setMinWidth(460);

        // Header: Avatar lớn nền vàng đồng mềm mại + Tên chữ mực xanh sẫm + Role Badge
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);

        StackPane bigAvatar = new StackPane();
        bigAvatar.setPrefSize(52, 52);
        bigAvatar.setMinSize(52, 52);
        bigAvatar.setMaxSize(52, 52);
        // Nền vàng kem đồng #F7EECD, viền vàng kim #CBA447, chữ vàng đồng đậm #8F6208 -> hoàn toàn khác màu tên chữ mực sẫm #14264A
        bigAvatar.setStyle("-fx-background-color: #F7EECD; -fx-background-radius: 999; -fx-border-color: -brass-500; -fx-border-width: 2; -fx-border-radius: 999;");
        Label avText = new Label(extractInitials(user.getFullName()));
        avText.setStyle("-fx-text-fill: #8F6208; -fx-font-weight: bold; -fx-font-size: 17px;");
        bigAvatar.getChildren().add(avText);

        VBox titleBox = new VBox(4);
        Label lblName = new Label(user.getFullName());
        lblName.setStyle("-fx-font-family: 'Lora', 'Georgia', serif; -fx-font-size: 16.5px; -fx-font-weight: bold; -fx-text-fill: -ink-900;");

        HBox badgesRow = new HBox(6);
        badgesRow.setAlignment(Pos.CENTER_LEFT);

        Label roleBadge = new Label(user.getRole() != null ? user.getRole().toUpperCase() : "USER");
        roleBadge.setStyle("-fx-background-color: -brass-100; -fx-text-fill: -brass-700; -fx-padding: 2 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");
        badgesRow.getChildren().add(roleBadge);

        if (user.getSpecialty() != null && !Constants.SPECIALTY_NONE.equalsIgnoreCase(user.getSpecialty()) && !"ALL".equalsIgnoreCase(user.getSpecialty())) {
            Label deptBadge = createDepartmentBadge(user.getSpecialty());
            badgesRow.getChildren().add(deptBadge);
        }

        titleBox.getChildren().addAll(lblName, badgesRow);
        header.getChildren().addAll(bigAvatar, titleBox);

        Region div1 = new Region();
        div1.getStyleClass().add("divider");

        // Chi tiết thông tin
        VBox infoBox = new VBox(11);
        infoBox.setStyle("-fx-background-color: white; -fx-padding: 16 18; -fx-background-radius: 8; -fx-border-color: -paper-300; -fx-border-radius: 8;");

        HBox onlineRow = new HBox(8);
        onlineRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon iconOnline = createIcon("mdi2c-check-circle", 15, Color.web("#2F6F55"));
        Label lblKeyOnline = new Label("Trạng thái:");
        lblKeyOnline.setStyle("-fx-text-fill: -text-600; -fx-font-size: 12px; -fx-min-width: 105;");
        Label lblValOnline = new Label("Đang trực tuyến (Online)");
        lblValOnline.setStyle("-fx-text-fill: -st-resolved; -fx-font-size: 12px; -fx-font-weight: 600;");
        onlineRow.getChildren().addAll(iconOnline, lblKeyOnline, lblValOnline);

        infoBox.getChildren().addAll(
                createProfileInfoRow("Tên đăng nhập:", "@" + user.getUsername(), "mdi2a-account-group"),
                createProfileInfoRow("Họ và tên:", user.getFullName(), "mdi2c-clipboard-text-outline"),
                createProfileInfoRow("Số điện thoại:", (user.getPhone() != null && !user.getPhone().isEmpty()) ? user.getPhone() : "Chưa cập nhật", "mdi2p-phone"),
                createProfileInfoRow("Vai trò:", getRoleDisplayName(user.getRole()), "mdi2s-shield-search"),
                createProfileInfoRow("Phòng ban trực:", (user.getSpecialty() != null ? user.getSpecialty() : "Không"), "mdi2w-wrench"),
                onlineRow
        );

        // Nút bấm: Đóng & Đăng xuất
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button btnLogout = new Button("Đăng xuất");
        btnLogout.getStyleClass().addAll("btn-secondary", "btn-sm");
        btnLogout.setGraphic(createIcon("mdi2l-logout", 14, Color.web("#A8402F")));
        btnLogout.setStyle("-fx-text-fill: -st-pending;");
        btnLogout.setOnAction(e -> {
            dialog.setResult(null);
            dialog.close();
            IncidentReportApp.logout();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnClose = new Button("Đóng");
        btnClose.getStyleClass().addAll("btn-primary", "btn-sm");
        btnClose.setOnAction(e -> {
            dialog.setResult(null);
            dialog.close();
        });

        btnRow.getChildren().addAll(btnLogout, spacer, btnClose);

        content.getChildren().addAll(header, div1, infoBox, btnRow);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private static HBox createProfileInfoRow(String label, String value, String iconLiteral) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = createIcon(iconLiteral, 15, Color.web("#8A6218"));

        Label lblKey = new Label(label);
        lblKey.setStyle("-fx-text-fill: -text-600; -fx-font-size: 12px; -fx-min-width: 105;");

        Label lblVal = new Label(value != null ? value : "-");
        lblVal.setStyle("-fx-text-fill: -text-900; -fx-font-size: 12px; -fx-font-weight: 600;");

        row.getChildren().addAll(icon, lblKey, lblVal);
        return row;
    }

    public static String getRoleDisplayName(String role) {
        if (Constants.ROLE_ADMIN.equalsIgnoreCase(role)) return "Ban Giám hiệu (Quản trị viên)";
        if (Constants.ROLE_RESPONDER.equalsIgnoreCase(role)) return "Cán bộ chuyên môn phụ trách";
        if (Constants.ROLE_REPORTER.equalsIgnoreCase(role)) return "Giáo viên / Học sinh (Người báo)";
        return role != null ? role : "Người dùng";
    }
}
