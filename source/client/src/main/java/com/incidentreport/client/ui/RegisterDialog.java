package com.incidentreport.client.ui;

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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Modal Dang ky tai khoan cong cong danh cho Hoc sinh va Giao vien (REPORTER).
 */
public class RegisterDialog extends Stage implements SocketClientManager.PacketListener {

    private final String serverHost;
    private final int serverPort;
    private final Consumer<String> onSuccessCallback;

    private final TextField txtUsername = new TextField();
    private final PasswordField txtPassword = new PasswordField();
    private final PasswordField txtConfirmPassword = new PasswordField();
    private final TextField txtFullName = new TextField();
    private final TextField txtPhone = new TextField();
    private final ComboBox<String> cbSubRole = new ComboBox<>();

    private final Label lblMessage = new Label();
    private final Button btnRegister = new Button("ĐĂNG KÝ");
    private final Button btnCancel = new Button("Hủy");
    private final ProgressIndicator progress = new ProgressIndicator();

    public RegisterDialog(Stage owner, String host, int port, Consumer<String> onSuccessCallback) {
        this.serverHost = host;
        this.serverPort = port;
        this.onSuccessCallback = onSuccessCallback;

        initModality(Modality.WINDOW_MODAL);
        if (owner != null) initOwner(owner);
        setTitle("Đăng ký tài khoản Người Báo Cáo (Reporter)");

        initUI();
        SocketClientManager.getInstance().addListener(this);
        setOnCloseRequest(e -> cleanup());
    }

    private void initUI() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20, 25, 20, 25));
        root.setStyle("-fx-background-color: #F8F9FA;");
        root.setAlignment(Pos.CENTER);

        Label lblTitle = new Label("ĐĂNG KÝ TÀI KHOẢN MỚI");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lblTitle.setTextFill(Color.web("#1E5AA8"));

        Label lblSub = new Label("Chỉ áp dụng cho Học sinh và Giáo viên trong trường");
        lblSub.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        lblSub.setTextFill(Color.web("#6C757D"));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);

        int row = 0;

        grid.add(new Label("Tên đăng nhập: *"), 0, row);
        txtUsername.setPromptText("Ví dụ: hs_nam, gv_huong");
        txtUsername.setPrefWidth(220);
        grid.add(txtUsername, 1, row++);

        grid.add(new Label("Mật khẩu: *"), 0, row);
        txtPassword.setPromptText("Tối thiểu 4 ký tự");
        grid.add(txtPassword, 1, row++);

        grid.add(new Label("Nhập lại mật khẩu: *"), 0, row);
        txtConfirmPassword.setPromptText("Xác nhận mật khẩu");
        grid.add(txtConfirmPassword, 1, row++);

        grid.add(new Label("Họ và tên: *"), 0, row);
        txtFullName.setPromptText("Họ và tên đầy đủ");
        grid.add(txtFullName, 1, row++);

        grid.add(new Label("Số điện thoại:"), 0, row);
        txtPhone.setPromptText("Số điện thoại liên hệ");
        grid.add(txtPhone, 1, row++);

        grid.add(new Label("Đối tượng: *"), 0, row);
        cbSubRole.getItems().addAll("Học sinh (STUDENT)", "Giáo viên (TEACHER)");
        cbSubRole.getSelectionModel().select(0);
        cbSubRole.setMaxWidth(Double.MAX_VALUE);
        grid.add(cbSubRole, 1, row++);

        grid.add(new Label("Vai trò cố định:"), 0, row);
        Label lblRole = new Label("REPORTER (Người báo cáo sự cố)");
        lblRole.setStyle("-fx-font-weight: bold; -fx-text-fill: #2E7D32;");
        grid.add(lblRole, 1, row++);

        // Progress & Message
        progress.setVisible(false);
        progress.setMaxSize(20, 20);

        lblMessage.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lblMessage.setWrapText(true);

        // Buttons
        btnRegister.setStyle("-fx-background-color: #1E5AA8; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 5;");
        btnRegister.setOnAction(e -> doRegister());

        btnCancel.setStyle("-fx-background-color: #6C757D; -fx-text-fill: white; -fx-padding: 8 18; -fx-background-radius: 5;");
        btnCancel.setOnAction(e -> {
            cleanup();
            close();
        });

        HBox btnBox = new HBox(12, btnRegister, btnCancel, progress);
        btnBox.setAlignment(Pos.CENTER);

        root.getChildren().addAll(lblTitle, lblSub, grid, lblMessage, btnBox);

        Scene scene = new Scene(root, 420, 460);
        setScene(scene);
    }

    private void doRegister() {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();
        String confirmPassword = txtConfirmPassword.getText();
        String fullName = txtFullName.getText().trim();
        String phone = txtPhone.getText().trim();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            showMessage("Vui lòng điền đầy đủ các mục có dấu *", true);
            return;
        }

        if (username.contains(" ") || username.length() < 3) {
            showMessage("Tên đăng nhập phải từ 3 ký tự và không chứa khoảng trắng", true);
            return;
        }

        if (password.length() < 4) {
            showMessage("Mật khẩu phải từ 4 ký tự trở lên", true);
            return;
        }

        if (!password.equals(confirmPassword)) {
            showMessage("Mật khẩu xác nhận không trùng khớp", true);
            return;
        }

        String subRole = cbSubRole.getSelectionModel().getSelectedIndex() == 1
                ? Constants.SUB_ROLE_TEACHER
                : Constants.SUB_ROLE_STUDENT;

        btnRegister.setDisable(true);
        progress.setVisible(true);
        showMessage("", false);

        new Thread(() -> {
            boolean connected = SocketClientManager.getInstance().connect(serverHost, serverPort);
            if (!connected) {
                Platform.runLater(() -> {
                    btnRegister.setDisable(false);
                    progress.setVisible(false);
                    showMessage("Không thể kết nối tới máy chủ!", true);
                });
                return;
            }

            String header = String.format("REGISTER|username=%s|fullName=%s|phone=%s|subRole=%s",
                    username, fullName, phone, subRole);
            byte[] attach = password.getBytes(StandardCharsets.UTF_8);
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_REGISTER, header, attach));
        }).start();
    }

    @Override
    public void onPacketReceived(Packet packet) {
        if (packet.getType() == Constants.TYPE_REGISTER_RESP) {
            btnRegister.setDisable(false);
            progress.setVisible(false);

            Map<String, String> p = packet.parseParams();
            String status = p.get("status");
            String message = p.getOrDefault("message", "");

            if ("OK".equalsIgnoreCase(status)) {
                showMessage("Đăng ký thành công!", false);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Đăng ký thành công");
                alert.setHeaderText("Tài khoản đã được tạo thành công!");
                alert.setContentText("Bạn có thể dùng tài khoản '" + txtUsername.getText().trim() + "' để đăng nhập ngay bây giờ.");
                alert.showAndWait();

                if (onSuccessCallback != null) {
                    onSuccessCallback.accept(txtUsername.getText().trim());
                }
                cleanup();
                close();
            } else {
                showMessage(message.isEmpty() ? "Đăng ký thất bại!" : message, true);
            }
        }
    }

    private void showMessage(String text, boolean isError) {
        lblMessage.setText(text);
        lblMessage.setTextFill(isError ? Color.web("#D32F2F") : Color.web("#2E7D32"));
    }

    private void cleanup() {
        SocketClientManager.getInstance().removeListener(this);
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            btnRegister.setDisable(false);
            progress.setVisible(false);
            showMessage("Mất kết nối máy chủ!", true);
        });
    }
}

