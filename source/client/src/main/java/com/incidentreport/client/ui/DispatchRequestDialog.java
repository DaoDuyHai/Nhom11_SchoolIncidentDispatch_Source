package com.incidentreport.client.ui;

import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.common.Constants;
import com.incidentreport.common.packet.Packet;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.util.Map;

public class DispatchRequestDialog {

    private static Stage activeDialog = null;

    public static void show(Packet packet) {
        // Đảm bảo chỉ mở tối đa 1 Popup tại 1 thời điểm trên màn hình nhân viên
        if (activeDialog != null && activeDialog.isShowing()) {
            activeDialog.close();
            activeDialog = null;
        }

        Map<String, String> p = packet.parseParams();
        int ticketId = Integer.parseInt(p.getOrDefault("ticketId", "0"));
        String code = p.getOrDefault("code", "INC-XXXX");
        String room = p.getOrDefault("room", "Phòng ?");
        String catName = p.getOrDefault("categoryName", "Sự cố");
        int severity = Integer.parseInt(p.getOrDefault("severity", "3"));
        String title = p.getOrDefault("title", "");
        int count = Integer.parseInt(p.getOrDefault("count", "1"));
        int timeoutSec = Integer.parseInt(p.getOrDefault("ackTimeout", "30"));

        Stage stage = new Stage();
        activeDialog = stage;
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("🔔 ĐIỀU PHỐI SỰ CỐ MỚI");
        stage.setAlwaysOnTop(true);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: white; -fx-border-color: #1E5AA8; -fx-border-width: 2px;");

        // Header
        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Label lblTitle = new Label("🔔 CÓ SỰ CỐ MỚI CẦN XỬ LÝ");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lblTitle.setTextFill(Color.web("#1E5AA8"));
        top.getChildren().addAll(lblTitle, ComponentFactory.createSeverityBadge(severity));

        // Details
        VBox infoBox = new VBox(6);
        infoBox.setStyle("-fx-background-color: #F4F6F9; -fx-padding: 12px; -fx-background-radius: 6px;");

        Label lblCode = new Label("Mã sự cố: " + code + "  |  Phòng: " + room);
        lblCode.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        Label lblCategory = new Label("Danh mục: " + catName + "  |  Số người báo: " + count);
        Label lblDesc = new Label("\"" + title + "\"");
        lblDesc.setWrapText(true);
        lblDesc.setStyle("-fx-font-style: italic; -fx-text-fill: #333;");

        infoBox.getChildren().addAll(lblCode, lblCategory, lblDesc);

        // Ảnh hiện trường đính kèm (nếu có)
        if (packet.hasAttachment()) {
            try {
                Image img = new Image(new ByteArrayInputStream(packet.getAttachmentBytes()));
                ImageView iv = new ImageView(img);
                iv.setFitWidth(220);
                iv.setFitHeight(140);
                iv.setPreserveRatio(true);
                infoBox.getChildren().add(iv);
            } catch (Exception ignored) {}
        }

        // Countdown timer bar
        Label lblTimer = new Label("Thời gian còn lại để nhận việc: " + timeoutSec + " giây");
        ProgressBar progressBar = new ProgressBar(1.0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: #2E9E5B;");

        int[] remaining = {timeoutSec};
        Timeline timeline = new Timeline();
        timeline.setCycleCount(timeoutSec);
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1), event -> {
            remaining[0]--;
            lblTimer.setText("Thời gian còn lại để nhận việc: " + remaining[0] + " giây");
            double progress = (double) remaining[0] / timeoutSec;
            progressBar.setProgress(progress);
            if (remaining[0] <= 10) {
                progressBar.setStyle("-fx-accent: #D32F2F;");
            }
            if (remaining[0] <= 0) {
                timeline.stop();
                stage.close();
                String payload = String.format("ACK_TASK|ticketId=%d|action=REJECT|reason=Hết thời gian phản hồi (Timeout)", ticketId);
                SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_ACK_TASK, payload));
            }
        }));
        timeline.play();

        // Xử lý khi nhấn nút X đóng cửa sổ
        stage.setOnCloseRequest(e -> {
            timeline.stop();
            String payload = String.format("ACK_TASK|ticketId=%d|action=REJECT|reason=Đóng cửa sổ nhận việc", ticketId);
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_ACK_TASK, payload));
        });

        // Action Buttons
        HBox actions = new HBox(15);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button btnReject = new Button("TỪ CHỐI");
        btnReject.setStyle("-fx-background-color: #E2E3E5; -fx-text-fill: #333; -fx-font-weight: bold; -fx-padding: 8 16;");
        btnReject.setOnAction(e -> {
            timeline.stop();
            stage.close();
            String payload = String.format("ACK_TASK|ticketId=%d|action=REJECT|reason=Nhân viên bận", ticketId);
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_ACK_TASK, payload));
        });

        Button btnAccept = new Button("NHẬN NHIỆM VỤ");
        btnAccept.setStyle("-fx-background-color: #2E9E5B; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20;");
        btnAccept.setOnAction(e -> {
            timeline.stop();
            stage.close();
            String payload = String.format("ACK_TASK|ticketId=%d|action=ACCEPT", ticketId);
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_ACK_TASK, payload));
        });

        actions.getChildren().addAll(btnReject, btnAccept);

        root.getChildren().addAll(top, infoBox, lblTimer, progressBar, actions);

        Scene scene = new Scene(root, 460, packet.hasAttachment() ? 440 : 280);
        stage.setScene(scene);
        stage.show();
    }
}
