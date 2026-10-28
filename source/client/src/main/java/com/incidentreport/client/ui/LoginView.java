package com.incidentreport.client.ui;

import com.google.gson.Gson;
import com.incidentreport.client.IncidentReportApp;
import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Giao diện Đăng nhập tái thiết kế theo DESIGN_SYSTEM.md (Mục 5.1):
 * - Bố cục 40/60: Bìa sổ mực xanh (trái) và Trang giấy (phải).
 * - Kích thước cửa sổ: 880 x 540 cố định.
 * - Hỗ trợ ẩn hiện mật khẩu, cấu hình máy chủ thu gọn, chip chọn nhanh tài khoản demo.
 */
public class LoginView extends HBox implements SocketClientManager.PacketListener {

    private final TextField txtHost = new TextField(Constants.DEFAULT_SERVER_HOST);
    private final TextField txtPort = new TextField(String.valueOf(Constants.TCP_PORT));
    private final TextField txtUsername = new TextField("admin_bgh");
    private final PasswordField txtPassword = new PasswordField();
    private final TextField txtPasswordVisible = new TextField();
    private boolean isPasswordVisible = false;

    private final Button btnLogin = new Button("ĐĂNG NHẬP");
    private final Label lblError = new Label();
    private final TitledPane serverPane = new TitledPane();

    private final Gson gson = new Gson();

    public LoginView() {
        setPrefSize(880, 540);
        setMaxSize(880, 540);
        setMinSize(880, 540);

        // Nạp 2 cột chính
        Node leftBrand = createBrandPanel();
        Node rightForm = createFormPanel();

        getChildren().addAll(leftBrand, rightForm);
        HBox.setHgrow(rightForm, Priority.ALWAYS);

        SocketClientManager.getInstance().addListener(this);
    }

    private Node createBrandPanel() {
        VBox brandBox = new VBox(20);
        brandBox.setPrefWidth(350);
        brandBox.setMinWidth(350);
        brandBox.setMaxWidth(350);
        brandBox.getStyleClass().add("login-brand");
        brandBox.setAlignment(Pos.TOP_LEFT);

        // Vòng tròn viền đồng + Icon trường học
        StackPane iconCircle = new StackPane();
        iconCircle.setPrefSize(48, 48);
        iconCircle.setMinSize(48, 48);
        iconCircle.setMaxSize(48, 48);
        iconCircle.setStyle("-fx-border-color: -brass-500; -fx-border-width: 1.5; -fx-border-radius: 999; -fx-background-radius: 999;");
        FontIcon schoolIcon = ComponentFactory.createIcon("mdi2s-school", 24, Color.web("#E3BE6A"));
        iconCircle.getChildren().add(schoolIcon);

        VBox titleBox = new VBox(4);
        Label lblBrandTitle = new Label("SIDS");
        lblBrandTitle.getStyleClass().add("brand-title");

        Label lblBrandSub = new Label("Sổ trực sự cố trường học");
        lblBrandSub.getStyleClass().add("brand-sub");
        lblBrandSub.setStyle("-fx-font-size: 13px; -fx-text-fill: #C9D3E8;");

        titleBox.getChildren().addAll(lblBrandTitle, lblBrandSub);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Kẻ đồng 1px
        Region brassRule = new Region();
        brassRule.getStyleClass().add("brass-rule");

        // Câu quote triết lý
        Label lblQuote = new Label("\"Mỗi sự cố được ghi nhận, mỗi em học sinh được an toàn.\"");
        lblQuote.getStyleClass().add("brand-quote");
        lblQuote.setWrapText(true);
        lblQuote.setStyle("-fx-line-spacing: 4px;");

        brandBox.getChildren().addAll(iconCircle, titleBox, spacer, brassRule, lblQuote);
        return brandBox;
    }

    private Node createFormPanel() {
        VBox formBox = new VBox(10);
        formBox.getStyleClass().add("login-form");
        formBox.setAlignment(Pos.CENTER_LEFT);
        formBox.setPadding(new Insets(32, 44, 32, 44));

        Label lblTitle = new Label("Đăng nhập");
        lblTitle.getStyleClass().add("h1");

        Label lblSub = new Label("Dùng tài khoản do nhà trường cấp.");
        lblSub.getStyleClass().add("muted");
        lblSub.setStyle("-fx-font-size: 12.5px; -fx-padding: 0 0 10 0;");

        // Tên đăng nhập
        Label lblUser = new Label("Tên đăng nhập");
        lblUser.getStyleClass().add("field-label");

        txtUsername.setPromptText("Tên đăng nhập");
        txtUsername.setText("admin_bgh");

        // Mật khẩu
        Label lblPass = new Label("Mật khẩu");
        lblPass.getStyleClass().add("field-label");

        txtPassword.setPromptText("Mật khẩu");
        txtPassword.setText("123456");

        txtPasswordVisible.setPromptText("Mật khẩu");
        txtPasswordVisible.setText("123456");
        txtPasswordVisible.setVisible(false);
        txtPasswordVisible.setManaged(false);

        // Đồng bộ 2 ô mật khẩu
        txtPassword.textProperty().addListener((obs, old, val) -> {
            if (!isPasswordVisible) txtPasswordVisible.setText(val);
        });
        txtPasswordVisible.textProperty().addListener((obs, old, val) -> {
            if (isPasswordVisible) txtPassword.setText(val);
        });

        // Nút con mắt ẩn hiện mật khẩu
        Button btnToggleEye = new Button();
        btnToggleEye.getStyleClass().setAll("btn-icon");
        FontIcon eyeIcon = ComponentFactory.createIcon("mdi2e-eye-outline", 16, Color.web("#8C8677"));
        btnToggleEye.setGraphic(eyeIcon);
        btnToggleEye.setOnAction(e -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                txtPasswordVisible.setText(txtPassword.getText());
                txtPassword.setVisible(false);
                txtPassword.setManaged(false);
                txtPasswordVisible.setVisible(true);
                txtPasswordVisible.setManaged(true);
                eyeIcon.setIconLiteral("mdi2e-eye-off-outline");
                txtPasswordVisible.requestFocus();
                txtPasswordVisible.positionCaret(txtPasswordVisible.getText().length());
            } else {
                txtPassword.setText(txtPasswordVisible.getText());
                txtPasswordVisible.setVisible(false);
                txtPasswordVisible.setManaged(false);
                txtPassword.setVisible(true);
                txtPassword.setManaged(true);
                eyeIcon.setIconLiteral("mdi2e-eye-outline");
                txtPassword.requestFocus();
                txtPassword.positionCaret(txtPassword.getText().length());
            }
        });

        StackPane passStack = new StackPane(txtPassword, txtPasswordVisible, btnToggleEye);
        StackPane.setAlignment(btnToggleEye, Pos.CENTER_RIGHT);
        StackPane.setMargin(btnToggleEye, new Insets(0, 6, 0, 0));

        // Nút Đăng nhập
        btnLogin.getStyleClass().addAll("btn-primary", "btn-block");
        btnLogin.setOnAction(e -> attemptLogin());
        txtPassword.setOnAction(e -> attemptLogin());
        txtPasswordVisible.setOnAction(e -> attemptLogin());

        // Nhãn báo lỗi
        lblError.getStyleClass().add("error-banner");
        lblError.setVisible(false);
        lblError.setManaged(false);

        // Cấu hình máy chủ (TitledPane thu gọn)
        HBox serverBox = new HBox(8, new Label("Máy chủ:"), txtHost, new Label(":"), txtPort);
        serverBox.setAlignment(Pos.CENTER_LEFT);
        txtHost.setPrefWidth(130);
        txtPort.setPrefWidth(65);

        serverPane.setText(String.format("▸ Cấu hình máy chủ (%s:%s)", txtHost.getText(), txtPort.getText()));
        serverPane.setContent(serverBox);
        serverPane.setExpanded(false);
        txtHost.textProperty().addListener((obs, old, val) -> updateServerPaneTitle());
        txtPort.textProperty().addListener((obs, old, val) -> updateServerPaneTitle());

        // Khối chọn nhanh tài khoản demo
        Label lblDemo = new Label("── Truy cập nhanh (demo) ──");
        lblDemo.getStyleClass().addAll("caption", "muted");
        lblDemo.setAlignment(Pos.CENTER);
        lblDemo.setMaxWidth(Double.MAX_VALUE);
        lblDemo.setStyle("-fx-padding: 6 0 2 0;");

        HBox demoRow1 = new HBox(8);
        demoRow1.setAlignment(Pos.CENTER);
        demoRow1.getChildren().addAll(
                createDemoChip("Admin BGH", "admin_bgh", "123456", "#B8872E"),
                createDemoChip("GV Lan", "gv_lan", "123456", "#14264A"),
                createDemoChip("HS Minh", "hs_minh", "123456", "#14264A")
        );

        HBox demoRow2 = new HBox(8);
        demoRow2.setAlignment(Pos.CENTER);
        demoRow2.getChildren().addAll(
                createDemoChip("Kỹ thuật Tuấn", "kt_tuan", "123456", "#2D5F8F"),
                createDemoChip("Y tế Mai", "yte_mai", "123456", "#A83A4C"),
                createDemoChip("Bảo vệ Sơn", "bv_son", "123456", "#A8561A")
        );

        formBox.getChildren().addAll(
                lblTitle, lblSub,
                lblUser, txtUsername,
                lblPass, passStack,
                btnLogin,
                lblError,
                serverPane,
                lblDemo, demoRow1, demoRow2
        );

        return formBox;
    }

    private void updateServerPaneTitle() {
        serverPane.setText(String.format("▸ Cấu hình máy chủ (%s:%s)", txtHost.getText().trim(), txtPort.getText().trim()));
    }

    private Button createDemoChip(String label, String username, String password, String dotColorHex) {
        Button chip = new Button(label);
        chip.getStyleClass().add("chip");

        Circle dot = new Circle(3.5, Color.web(dotColorHex));
        chip.setGraphic(dot);

        chip.setOnAction(e -> {
            txtUsername.setText(username);
            txtPassword.setText(password);
            txtPasswordVisible.setText(password);
            attemptLogin();
        });
        return chip;
    }

    private void attemptLogin() {
        String host = txtHost.getText().trim();
        String portStr = txtPort.getText().trim();
        String username = txtUsername.getText().trim();
        String password = isPasswordVisible ? txtPasswordVisible.getText() : txtPassword.getText();

        clearError();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            showError("Cổng kết nối không hợp lệ!");
            return;
        }

        setLoading(true);

        new Thread(() -> {
            boolean connected = SocketClientManager.getInstance().connect(host, port);
            if (!connected) {
                Platform.runLater(() -> {
                    setLoading(false);
                    showError("Không thể kết nối đến Máy chủ tại " + host + ":" + port);
                });
                return;
            }

            Packet loginPacket = new Packet(Constants.TYPE_LOGIN,
                    "LOGIN|username=" + username,
                    password.getBytes(StandardCharsets.UTF_8));
            SocketClientManager.getInstance().sendPacket(loginPacket);
        }).start();
    }

    @Override
    public void onPacketReceived(Packet packet) {
        Platform.runLater(() -> {
            if (packet.getType() == Constants.TYPE_LOGIN_RESP) {
                setLoading(false);
                Map<String, String> params = packet.parseParams();
                String status = params.get("status");

                if ("OK".equalsIgnoreCase(status)) {
                    User user = null;
                    if (packet.hasAttachment()) {
                        try {
                            String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                            user = gson.fromJson(json, User.class);
                        } catch (Exception ignored) {}
                    }

                    if (user == null) {
                        user = new User();
                        user.setUserId(Integer.parseInt(params.getOrDefault("userId", "0")));
                        user.setUsername(params.get("username"));
                        user.setRole(params.get("role"));
                        user.setSpecialty(params.get("specialty"));
                        user.setFullName(params.get("fullName"));
                        user.setPhone(params.get("phone"));
                    }

                    SocketClientManager.getInstance().removeListener(this);
                    IncidentReportApp.showMainView(user);
                } else {
                    showError(params.getOrDefault("message", "Đăng nhập thất bại! Sai tên tài khoản hoặc mật khẩu."));
                }
            } else if (packet.getType() == Constants.TYPE_ERROR) {
                setLoading(false);
                Map<String, String> params = packet.parseParams();
                showError(params.getOrDefault("message", "Lỗi xử lý từ hệ thống!"));
            }
        });
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            setLoading(false);
            showError("Mất kết nối với Server.");
        });
    }

    private void setLoading(boolean loading) {
        btnLogin.setDisable(loading);
        if (loading) {
            btnLogin.setText("Đang kết nối…");
            ProgressIndicator pi = new ProgressIndicator();
            pi.setMaxSize(16, 16);
            pi.setPrefSize(16, 16);
            btnLogin.setGraphic(pi);
        } else {
            btnLogin.setText("ĐĂNG NHẬP");
            btnLogin.setGraphic(null);
        }
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
        lblError.setManaged(true);
        txtUsername.getStyleClass().add("input-error");
        txtPassword.getStyleClass().add("input-error");
        txtPasswordVisible.getStyleClass().add("input-error");

        // Hiệu ứng rung ngang 200ms
        Timeline shake = new Timeline(
                new KeyFrame(Duration.millis(0), new KeyValue(lblError.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(40), new KeyValue(lblError.translateXProperty(), -6)),
                new KeyFrame(Duration.millis(80), new KeyValue(lblError.translateXProperty(), 6)),
                new KeyFrame(Duration.millis(120), new KeyValue(lblError.translateXProperty(), -6)),
                new KeyFrame(Duration.millis(160), new KeyValue(lblError.translateXProperty(), 6)),
                new KeyFrame(Duration.millis(200), new KeyValue(lblError.translateXProperty(), 0))
        );
        shake.play();
    }

    private void clearError() {
        lblError.setVisible(false);
        lblError.setManaged(false);
        txtUsername.getStyleClass().remove("input-error");
        txtPassword.getStyleClass().remove("input-error");
        txtPasswordVisible.getStyleClass().remove("input-error");
    }
}
