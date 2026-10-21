package com.incidentreport.client.ui;

import com.google.gson.Gson;
import com.incidentreport.client.IncidentReportApp;
import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Giao diện Đăng nhập tập trung (Admin/Server quản lý tài khoản độc quyền):
 * - Không có tự đăng ký, không có quên mật khẩu.
 * - Hỗ trợ đăng nhập nhanh bằng tài khoản mẫu phục vụ kiểm thử tiện lợi trên NetBeans.
 */
public class LoginView extends VBox implements SocketClientManager.PacketListener {

    private final TextField txtHost = new TextField(Constants.DEFAULT_SERVER_HOST);
    private final TextField txtPort = new TextField(String.valueOf(Constants.TCP_PORT));
    private final TextField txtUsername = new TextField("admin_bgh");
    private final PasswordField txtPassword = new PasswordField();
    private final Label lblError = new Label();
    private final Button btnLogin = new Button("ĐĂNG NHẬP");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Gson gson = new Gson();

    public LoginView() {
        setAlignment(Pos.CENTER);
        setPadding(new Insets(30, 40, 30, 40));
        setSpacing(14);
        setStyle("-fx-background-color: #F8FAFC;");

        // Header Title
        Label lblAppTitle = new Label("HỆ THỐNG ĐIỀU PHỐI SỰ CỐ");
        lblAppTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        lblAppTitle.setTextFill(Color.web("#0F172A"));

        Label lblSubTitle = new Label("TRƯỜNG THPT - PHÂN HỆ ĐIỀU HÀNH & GIÁM SÁT");
        lblSubTitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        lblSubTitle.setTextFill(Color.web("#64748B"));

        // Form Container
        VBox formCard = new VBox(12);
        formCard.setMaxWidth(380);
        formCard.setPadding(new Insets(24));
        formCard.setStyle("-fx-background-color: white; -fx-background-radius: 10px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 12, 0, 0, 4);");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);

        grid.add(new Label("Máy chủ:"), 0, 0);
        HBox serverBox = new HBox(6, txtHost, new Label(":"), txtPort);
        txtHost.setPrefWidth(140);
        txtPort.setPrefWidth(65);
        grid.add(serverBox, 1, 0);

        grid.add(new Label("Tài khoản:"), 0, 1);
        txtUsername.setPromptText("Tên đăng nhập");
        txtUsername.setPrefWidth(220);
        grid.add(txtUsername, 1, 1);

        grid.add(new Label("Mật khẩu:"), 0, 2);
        txtPassword.setPromptText("Mật khẩu (mặc định: 123456)");
        txtPassword.setText("123456");
        grid.add(txtPassword, 1, 2);

        btnLogin.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 9 20; -fx-background-radius: 6;");
        btnLogin.setMaxWidth(Double.MAX_VALUE);
        btnLogin.setOnAction(e -> attemptLogin());
        txtPassword.setOnAction(e -> attemptLogin());

        lblError.setTextFill(Color.web("#DC2626"));
        lblError.setStyle("-fx-font-size: 12px;");
        lblError.setVisible(false);
        lblError.setWrapText(true);

        progress.setVisible(false);
        progress.setMaxSize(24, 24);

        formCard.getChildren().addAll(grid, lblError, btnLogin, progress);

        // Nút chọn nhanh tài khoản mẫu để test trên NetBeans
        Label lblFastSelect = new Label("⚡ Chọn nhanh tài khoản mẫu (Kiểm thử NetBeans):");
        lblFastSelect.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B; -fx-font-weight: bold;");

        HBox quickBox1 = new HBox(6);
        quickBox1.setAlignment(Pos.CENTER);
        Button btnAdmin = createQuickBtn("Admin BGH", "admin_bgh", "123456");
        Button btnTeacher = createQuickBtn("GV Lan", "gv_lan", "123456");
        Button btnStudent = createQuickBtn("HS Minh", "hs_minh", "123456");
        quickBox1.getChildren().addAll(btnAdmin, btnTeacher, btnStudent);

        HBox quickBox2 = new HBox(6);
        quickBox2.setAlignment(Pos.CENTER);
        Button btnTech = createQuickBtn("Kỹ thuật Tuấn", "kt_tuan", "123456");
        Button btnMed = createQuickBtn("Y tế Mai", "yte_mai", "123456");
        Button btnSec = createQuickBtn("Bảo vệ Sơn", "bv_son", "123456");
        quickBox2.getChildren().addAll(btnTech, btnMed, btnSec);

        Label lblPolicy = new Label("🔒 Chính sách hệ thống: Tài khoản do Admin/Server cấp phát trực tiếp.");
        lblPolicy.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        getChildren().addAll(lblAppTitle, lblSubTitle, formCard, lblFastSelect, quickBox1, quickBox2, lblPolicy);

        SocketClientManager.getInstance().addListener(this);
    }

    private Button createQuickBtn(String text, String username, String pass) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #1E293B; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4;");
        btn.setOnAction(e -> {
            txtUsername.setText(username);
            txtPassword.setText(pass);
            attemptLogin();
        });
        return btn;
    }

    private void attemptLogin() {
        String host = txtHost.getText().trim();
        String portStr = txtPort.getText().trim();
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();

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
        lblError.setVisible(false);

        new Thread(() -> {
            boolean connected = SocketClientManager.getInstance().connect(host, port);
            if (!connected) {
                javafx.application.Platform.runLater(() -> {
                    setLoading(false);
                    showError("Không thể kết nối đến Máy chủ Server tại " + host + ":" + port);
                });
                return;
            }

            Packet loginPacket = new Packet(Constants.TYPE_LOGIN,
                    String.format("LOGIN|username=%s|password=%s", username, password));
            SocketClientManager.getInstance().sendPacket(loginPacket);
        }).start();
    }

    @Override
    public void onPacketReceived(Packet packet) {
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
                showError(params.getOrDefault("message", "Đăng nhập thất bại!"));
            }
        } else if (packet.getType() == Constants.TYPE_ERROR) {
            setLoading(false);
            Map<String, String> params = packet.parseParams();
            showError(params.getOrDefault("message", "Lỗi xử lý từ hệ thống!"));
        }
    }

    @Override
    public void onDisconnected() {
        setLoading(false);
        showError("Mất kết nối với Server.");
    }

    private void setLoading(boolean loading) {
        btnLogin.setDisable(loading);
        progress.setVisible(loading);
    }

    private void showError(String msg) {
        lblError.setText(msg);
        lblError.setVisible(true);
    }
}
