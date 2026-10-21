package com.incidentreport.client.ui;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.common.Constants;
import com.incidentreport.common.packet.Packet;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Modal Khoi phuc mat khau su dung ma OTP truyen qua socket UDP.
 * Luong ky thuat:
 * 1. Mo DatagramSocket(0) lang nghe cong UDP cuc bo.
 * 2. Gui request TCP FORGOT_PASSWORD kem ten user va cong UDP.
 * 3. Server tao OTP 6 so, luu bo nho dem 60s va ban goi tin UDP ve.
 * 4. Client nhan UDP, hien popup thong bao mo phong tin nhan thiet bi.
 * 5. Nguoi dung nhap mat khau moi, gui request TCP RESET_PASSWORD xac nhan.
 */
public class ForgotPasswordDialog extends Stage implements SocketClientManager.PacketListener {

    private final String serverHost;
    private final int serverPort;
    private final Consumer<String> onSuccessCallback;

    private final TextField txtUsername = new TextField();
    private final Button btnSendOtp = new Button("Gửi mã OTP qua UDP");

    private final TextField txtOtp = new TextField();
    private final PasswordField txtNewPassword = new PasswordField();
    private final PasswordField txtConfirmPassword = new PasswordField();
    private final Button btnReset = new Button("ĐỔI MẬT KHẨU");
    private final Button btnClose = new Button("Đóng");

    private final Label lblUdpStatus = new Label();
    private final Label lblMessage = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();

    private DatagramSocket udpSocket = null;
    private Thread udpListenerThread = null;

    public ForgotPasswordDialog(Stage owner, String host, int port, String prefilledUsername, Consumer<String> onSuccessCallback) {
        this.serverHost = host;
        this.serverPort = port;
        this.onSuccessCallback = onSuccessCallback;

        if (prefilledUsername != null && !prefilledUsername.trim().isEmpty()) {
            txtUsername.setText(prefilledUsername.trim());
        }

        initModality(Modality.WINDOW_MODAL);
        if (owner != null) initOwner(owner);
        setTitle("Khôi phục mật khẩu (Xác thực OTP qua UDP Socket)");

        initUI();
        SocketClientManager.getInstance().addListener(this);
        setOnCloseRequest(e -> cleanup());
    }

    private void initUI() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20, 25, 20, 25));
        root.setStyle("-fx-background-color: #F8F9FA;");
        root.setAlignment(Pos.CENTER);

        Label lblTitle = new Label("KHÔI PHỤC MẬT KHẨU");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lblTitle.setTextFill(Color.web("#1E5AA8"));

        Label lblSub = new Label("Xác thực danh tính bằng mã OTP 6 chữ số gửi qua UDP Socket");
        lblSub.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        lblSub.setTextFill(Color.web("#6C757D"));

        // Step 1: Nhap Username va lay OTP
        TitledPane paneStep1 = new TitledPane();
        paneStep1.setText("Bước 1: Nhận mã xác thực OTP");
        paneStep1.setCollapsible(false);

        GridPane grid1 = new GridPane();
        grid1.setHgap(10);
        grid1.setVgap(10);
        grid1.setPadding(new Insets(10));

        grid1.add(new Label("Tên đăng nhập:"), 0, 0);
        txtUsername.setPromptText("Tài khoản cần lấy lại MK");
        txtUsername.setPrefWidth(200);
        grid1.add(txtUsername, 1, 0);

        btnSendOtp.setStyle("-fx-background-color: #0288D1; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4;");
        btnSendOtp.setOnAction(e -> requestOtpOverUdp());
        grid1.add(btnSendOtp, 2, 0);

        lblUdpStatus.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        lblUdpStatus.setTextFill(Color.web("#455A64"));
        grid1.add(lblUdpStatus, 1, 1, 2, 1);

        paneStep1.setContent(grid1);

        // Step 2: Nhap OTP va Mat khau moi
        TitledPane paneStep2 = new TitledPane();
        paneStep2.setText("Bước 2: Đặt lại mật khẩu mới");
        paneStep2.setCollapsible(false);

        GridPane grid2 = new GridPane();
        grid2.setHgap(10);
        grid2.setVgap(10);
        grid2.setPadding(new Insets(10));

        grid2.add(new Label("Mã OTP nhận được: *"), 0, 0);
        txtOtp.setPromptText("6 chữ số (hiệu lực 60s)");
        txtOtp.setPrefWidth(200);
        grid2.add(txtOtp, 1, 0);

        grid2.add(new Label("Mật khẩu mới: *"), 0, 1);
        txtNewPassword.setPromptText("Tối thiểu 4 ký tự");
        grid2.add(txtNewPassword, 1, 1);

        grid2.add(new Label("Xác nhận mật khẩu: *"), 0, 2);
        txtConfirmPassword.setPromptText("Nhập lại mật khẩu mới");
        grid2.add(txtConfirmPassword, 1, 2);

        paneStep2.setContent(grid2);

        // Bottom status & buttons
        progress.setVisible(false);
        progress.setMaxSize(20, 20);

        lblMessage.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lblMessage.setWrapText(true);

        btnReset.setStyle("-fx-background-color: #2E7D32; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 5;");
        btnReset.setOnAction(e -> doResetPassword());

        btnClose.setStyle("-fx-background-color: #6C757D; -fx-text-fill: white; -fx-padding: 8 20; -fx-background-radius: 5;");
        btnClose.setOnAction(e -> {
            cleanup();
            close();
        });

        HBox btnBox = new HBox(12, btnReset, btnClose, progress);
        btnBox.setAlignment(Pos.CENTER);

        root.getChildren().addAll(lblTitle, lblSub, paneStep1, paneStep2, lblMessage, btnBox);

        Scene scene = new Scene(root, 480, 520);
        setScene(scene);
    }

    private void requestOtpOverUdp() {
        String username = txtUsername.getText().trim();
        if (username.isEmpty()) {
            showMessage("Vui lòng nhập tên tài khoản cần khôi phục mật khẩu", true);
            return;
        }

        btnSendOtp.setDisable(true);
        progress.setVisible(true);
        showMessage("", false);

        // Đóng socket UDP cũ nếu có
        closeUdpSocket();

        try {
            // Bước 1: Mở một DatagramSocket tại port ngẫu nhiên khả dụng trên máy Client
            udpSocket = new DatagramSocket(0);
            int localUdpPort = udpSocket.getLocalPort();
            lblUdpStatus.setText("📡 Đang lắng nghe gói tin UDP tại cổng: " + localUdpPort + "...");

            // Bật luồng ngầm lắng nghe gói tin UDP chứa mã OTP từ Server
            udpListenerThread = new Thread(() -> listenForUdpOtp(localUdpPort), "UDP-OTP-Listener");
            udpListenerThread.setDaemon(true);
            udpListenerThread.start();

            // Bước 2: Gửi request TCP FORGOT_PASSWORD lên Server thông báo UDP port
            new Thread(() -> {
                boolean connected = SocketClientManager.getInstance().connect(serverHost, serverPort);
                if (!connected) {
                    Platform.runLater(() -> {
                        btnSendOtp.setDisable(false);
                        progress.setVisible(false);
                        lblUdpStatus.setText("");
                        showMessage("Không thể kết nối tới máy chủ!", true);
                        closeUdpSocket();
                    });
                    return;
                }

                String header = String.format("FORGOT_PASSWORD|username=%s|udpPort=%d", username, localUdpPort);
                SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_FORGOT_PASSWORD, header));
            }).start();

        } catch (SocketException e) {
            btnSendOtp.setDisable(false);
            progress.setVisible(false);
            showMessage("Không thể mở UDP Socket: " + e.getMessage(), true);
        }
    }

    private void listenForUdpOtp(int port) {
        try {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            udpSocket.setSoTimeout(65000); // Chờ tối đa 65 giây
            udpSocket.receive(packet);

            String content = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            System.out.println("📥 [UDP OTP RECEIVED] " + content);

            // Phân tích nội dung UDP: OTP|code=123456|username=..|expiresIn=60|message=..
            String otpCode = "";
            String[] tokens = content.split("\\|");
            for (String token : tokens) {
                if (token.startsWith("code=")) {
                    otpCode = token.substring("code=".length());
                    break;
                }
            }

            final String receivedCode = otpCode;
            Platform.runLater(() -> {
                progress.setVisible(false);
                btnSendOtp.setDisable(false);
                lblUdpStatus.setText("✅ Đã nhận thành công mã OTP qua UDP Port " + port);

                if (!receivedCode.isEmpty()) {
                    txtOtp.setText(receivedCode);
                }

                // Hiển thị Popup mô phỏng nhận thông báo/SMS trên thiết bị
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Mô phỏng thiết bị nhận mã OTP");
                alert.setHeaderText("📲 Bạn vừa nhận được tin nhắn xác thực qua UDP Socket!");
                alert.setContentText(
                    "• Mã OTP của bạn là: " + receivedCode + "\n" +
                    "• Thời gian hiệu lực: 60 giây\n" +
                    "• Nhận tại: " + packet.getAddress().getHostAddress() + ":" + port + "\n\n" +
                    "(Mã OTP đã được tự động điền vào ô xác nhận. Vui lòng nhập mật khẩu mới để tiếp tục)."
                );
                alert.show();

                txtNewPassword.requestFocus();
            });

        } catch (Exception e) {
            if (udpSocket != null && !udpSocket.isClosed()) {
                Platform.runLater(() -> {
                    progress.setVisible(false);
                    btnSendOtp.setDisable(false);
                    lblUdpStatus.setText("❌ Hết thời gian chờ nhận OTP (Timeout 60s)");
                });
            }
        } finally {
            closeUdpSocket();
        }
    }

    private void doResetPassword() {
        String username = txtUsername.getText().trim();
        String otp = txtOtp.getText().trim();
        String newPassword = txtNewPassword.getText();
        String confirmPassword = txtConfirmPassword.getText();

        if (username.isEmpty() || otp.isEmpty() || newPassword.isEmpty()) {
            showMessage("Vui lòng nhập tên tài khoản, mã OTP và mật khẩu mới!", true);
            return;
        }

        if (newPassword.length() < 4) {
            showMessage("Mật khẩu mới phải từ 4 ký tự trở lên!", true);
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showMessage("Mật khẩu xác nhận không trùng khớp!", true);
            return;
        }

        btnReset.setDisable(true);
        progress.setVisible(true);
        showMessage("", false);

        new Thread(() -> {
            boolean connected = SocketClientManager.getInstance().connect(serverHost, serverPort);
            if (!connected) {
                Platform.runLater(() -> {
                    btnReset.setDisable(false);
                    progress.setVisible(false);
                    showMessage("Không thể kết nối tới máy chủ!", true);
                });
                return;
            }

            String header = String.format("RESET_PASSWORD|username=%s|otp=%s", username, otp);
            byte[] attach = newPassword.getBytes(StandardCharsets.UTF_8);
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_RESET_PASSWORD, header, attach));
        }).start();
    }

    @Override
    public void onPacketReceived(Packet packet) {
        if (packet.getType() == Constants.TYPE_FORGOT_RESP) {
            Map<String, String> p = packet.parseParams();
            String status = p.get("status");
            String message = p.getOrDefault("message", "");
            if (!"OK".equalsIgnoreCase(status)) {
                progress.setVisible(false);
                btnSendOtp.setDisable(false);
                showMessage(message, true);
                closeUdpSocket();
            }
        } else if (packet.getType() == Constants.TYPE_RESET_RESP) {
            btnReset.setDisable(false);
            progress.setVisible(false);

            Map<String, String> p = packet.parseParams();
            String status = p.get("status");
            String message = p.getOrDefault("message", "");

            if ("OK".equalsIgnoreCase(status)) {
                showMessage("Đổi mật khẩu thành công!", false);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Đặt lại mật khẩu thành công");
                alert.setHeaderText("Mật khẩu của bạn đã được cập nhật!");
                alert.setContentText("Bạn có thể đăng nhập ngay với mật khẩu mới vừa tạo.");
                alert.showAndWait();

                if (onSuccessCallback != null) {
                    onSuccessCallback.accept(txtUsername.getText().trim());
                }
                cleanup();
                close();
            } else {
                showMessage(message.isEmpty() ? "Đặt lại mật khẩu thất bại!" : message, true);
            }
        }
    }

    private void showMessage(String text, boolean isError) {
        lblMessage.setText(text);
        lblMessage.setTextFill(isError ? Color.web("#D32F2F") : Color.web("#2E7D32"));
    }

    private void closeUdpSocket() {
        if (udpSocket != null) {
            try {
                if (!udpSocket.isClosed()) udpSocket.close();
            } catch (Exception ignored) {}
            udpSocket = null;
        }
    }

    private void cleanup() {
        closeUdpSocket();
        if (udpListenerThread != null) {
            udpListenerThread.interrupt();
        }
        SocketClientManager.getInstance().removeListener(this);
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            btnReset.setDisable(false);
            btnSendOtp.setDisable(false);
            progress.setVisible(false);
            showMessage("Mất kết nối máy chủ!", true);
        });
    }
}

