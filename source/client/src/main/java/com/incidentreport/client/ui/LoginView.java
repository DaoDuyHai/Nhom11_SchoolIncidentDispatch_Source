package com.incidentreport.client.ui;

import com.incidentreport.client.IncidentReportApp;
import com.incidentreport.client.net.MulticastReceiver;
import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.client.net.UDPHeartbeatSender;
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

import java.util.Map;

public class LoginView extends VBox implements SocketClientManager.PacketListener {

    private final TextField txtHost = new TextField(Constants.DEFAULT_SERVER_HOST);
    private final TextField txtPort = new TextField(String.valueOf(Constants.TCP_PORT));
    private final TextField txtUsername = new TextField();
    private final PasswordField txtPassword = new PasswordField();
    private final Label lblError = new Label();
    private final Button btnLogin = new Button("ĐĂNG NHẬP");
    private final ProgressIndicator progress = new ProgressIndicator();

    public LoginView() {
        setAlignment(Pos.CENTER);
        setPadding(new Insets(30, 40, 30, 40));
        setSpacing(15);
        setStyle("-fx-background-color: #F4F6F9;");

        // Logo & Title
        Label lblAppTitle = new Label("HỆ THỐNG ĐIỀU PHỐI SỰ CỐ");
        lblAppTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        lblAppTitle.setTextFill(Color.web("#1E5AA8"));

        Label lblSubTitle = new Label("TRƯỜNG THPT (High School Incident Dispatch)");
        lblSubTitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        lblSubTitle.setTextFill(Color.web("#6B7785"));

        // Form Grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);

        grid.add(new Label("Máy chủ:"), 0, 0);
        HBox serverBox = new HBox(5, txtHost, new Label(":"), txtPort);
        txtHost.setPrefWidth(140);
        txtPort.setPrefWidth(65);
        grid.add(serverBox, 1, 0);

        grid.add(new Label("Tài khoản:"), 0, 1);
        txtUsername.setPromptText("Tên đăng nhập");
        txtUsername.setPrefWidth(220);
        grid.add(txtUsername, 1, 1);

        grid.add(new Label("Mật khẩu:"), 0, 2);
        txtPassword.setPromptText("Mật khẩu");
        grid.add(txtPassword, 1, 2);

        // Nút đăng nhập
        btnLogin.setStyle("-fx-background-color: #1E5AA8; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 8 20; -fx-background-radius: 6;");
        btnLogin.setMaxWidth(Double.MAX_VALUE);
        btnLogin.setOnAction(e -> attemptLogin());
        txtPassword.setOnAction(e -> attemptLogin());

        progress.setVisible(false);
        progress.setMaxSize(24, 24);

        lblError.setTextFill(Color.web("#D32F2F"));
        lblError.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lblError.setWrapText(true);

        getChildren().addAll(lblAppTitle, lblSubTitle, grid, btnLogin, progress, lblError);

        SocketClientManager.getInstance().addListener(this);
    }

    private void attemptLogin() {
        String host = txtHost.getText().trim();
        int port = Constants.TCP_PORT;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
        } catch (Exception ex) {
            lblError.setText("Cổng máy chủ không hợp lệ!");
            return;
        }

        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            lblError.setText("Vui lòng nhập đầy đủ tài khoản và mật khẩu!");
            return;
        }

        lblError.setText("");
        progress.setVisible(true);
        btnLogin.setDisable(true);
        final int targetPort = port;

        new Thread(() -> {
            boolean connected = SocketClientManager.getInstance().connect(host, targetPort);
            if (!connected) {
                javafx.application.Platform.runLater(() -> {
                    lblError.setText("Không thể kết nối tới Server tại " + host + ":" + targetPort);
                    progress.setVisible(false);
                    btnLogin.setDisable(false);
                });
                return;
            }

            // Gửi bản tin 0x01 LOGIN
            String header = String.format("LOGIN|username=%s|password=%s", username, password);
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_LOGIN, header));
        }).start();
    }

    @Override
    public void onPacketReceived(Packet packet) {
        if (packet.getType() == Constants.TYPE_LOGIN_RESP) {
            progress.setVisible(false);
            btnLogin.setDisable(false);

            Map<String, String> p = packet.parseParams();
            String status = p.get("status");

            if ("OK".equalsIgnoreCase(status)) {
                User user = new User();
                user.setUserId(Integer.parseInt(p.get("userId")));
                user.setUsername(p.get("username"));
                user.setFullName(p.get("fullName"));
                user.setRole(p.get("role"));
                user.setSubRole(p.get("subRole"));
                user.setSpecialty(p.get("specialty"));

                // Bắt đầu lắng nghe Kênh UDP Multicast khẩn cấp 230.0.0.1:9999 cho MỌI role
                MulticastReceiver multicastReceiver = new MulticastReceiver(EmergencyAlertDialog::show);
                multicastReceiver.start();

                // Nếu là RESPONDER: Bật Kênh UDP Heartbeat 3 giây/lần
                if (Constants.ROLE_RESPONDER.equals(user.getRole())) {
                    UDPHeartbeatSender heartbeatSender = new UDPHeartbeatSender(txtHost.getText().trim(), user.getUserId());
                    heartbeatSender.start();
                    IncidentReportApp.setHeartbeatSender(heartbeatSender);
                }

                // Chuyển màn hình tương ứng với Role
                SocketClientManager.getInstance().removeListener(this);
                IncidentReportApp.setCurrentUser(user);

                if (Constants.ROLE_REPORTER.equals(user.getRole())) {
                    IncidentReportApp.showView(new ReporterView(user));
                } else if (Constants.ROLE_RESPONDER.equals(user.getRole())) {
                    IncidentReportApp.showView(new ResponderView(user));
                } else {
                    IncidentReportApp.showView(new AdminView(user));
                }
            } else {
                lblError.setText(p.getOrDefault("message", "Sai tài khoản hoặc mật khẩu!"));
            }
        }
    }

    @Override
    public void onDisconnected() {
        progress.setVisible(false);
        btnLogin.setDisable(false);
        lblError.setText("Mất kết nối máy chủ!");
    }
}
