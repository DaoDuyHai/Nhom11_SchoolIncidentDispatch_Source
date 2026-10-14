package com.incidentreport.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class EmergencyAlertDialog {

    public static void show(String alertLevel, String message) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("🚨 CẢNH BÁO KHẨN CẤP TOÀN TRƯỜNG");
        dialog.setAlwaysOnTop(true);

        VBox layout = new VBox(15);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: #D32F2F; -fx-background-radius: 8px;");

        Label lblTitle = new Label("🚨 BÁO ĐỘNG KHẨN CẤP TỪ BAN GIÁM HIỆU");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        lblTitle.setTextFill(Color.WHITE);

        Label lblLevel = new Label("CẤP ĐỘ: " + alertLevel.toUpperCase());
        lblLevel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        lblLevel.setTextFill(Color.YELLOW);

        Label lblMsg = new Label(message);
        lblMsg.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 15));
        lblMsg.setTextFill(Color.WHITE);
        lblMsg.setWrapText(true);
        lblMsg.setMaxWidth(400);

        Button btnDismiss = new Button("ĐÃ HIỂU VÀ CHẤP HÀNH");
        btnDismiss.setStyle("-fx-background-color: white; -fx-text-fill: #D32F2F; -fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 5;");
        btnDismiss.setOnAction(e -> dialog.close());

        layout.getChildren().addAll(lblTitle, lblLevel, lblMsg, btnDismiss);

        Scene scene = new Scene(layout, 480, 260);
        dialog.setScene(scene);
        dialog.show();
    }
}

