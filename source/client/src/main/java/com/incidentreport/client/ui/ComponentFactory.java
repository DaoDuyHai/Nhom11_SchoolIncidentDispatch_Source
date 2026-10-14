package com.incidentreport.client.ui;

import com.incidentreport.common.Constants;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public class ComponentFactory {

    public static Label createSeverityBadge(int severity) {
        Label label = new Label();
        label.setPadding(new Insets(3, 8, 3, 8));
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-background-radius: 12px;");

        switch (severity) {
            case 1:
                label.setText("1 Rất thấp");
                label.setStyle(label.getStyle() + "-fx-background-color: #E3F2FD; -fx-text-fill: #1565C0;");
                break;
            case 2:
                label.setText("2 Thấp");
                label.setStyle(label.getStyle() + "-fx-background-color: #E8F5E9; -fx-text-fill: #2E7D32;");
                break;
            case 3:
                label.setText("3 Trung bình");
                label.setStyle(label.getStyle() + "-fx-background-color: #FFF8E1; -fx-text-fill: #B26A00;");
                break;
            case 4:
                label.setText("4 Cao");
                label.setStyle(label.getStyle() + "-fx-background-color: #FFE0B2; -fx-text-fill: #E65100;");
                break;
            case 5:
            default:
                label.setText("5 Khẩn cấp");
                label.setStyle(label.getStyle() + "-fx-background-color: #D32F2F; -fx-text-fill: #FFFFFF;");
                break;
        }
        return label;
    }

    public static Label createStatusBadge(String status) {
        Label label = new Label();
        label.setPadding(new Insets(3, 8, 3, 8));
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-background-radius: 12px;");

        if (status == null) status = Constants.STATUS_PENDING;

        switch (status) {
            case Constants.STATUS_ASSIGNED:
                label.setText("Đã giao");
                label.setStyle(label.getStyle() + "-fx-background-color: #FFF3CD; -fx-text-fill: #856404;");
                break;
            case Constants.STATUS_IN_PROGRESS:
                label.setText("Đang xử lý");
                label.setStyle(label.getStyle() + "-fx-background-color: #D1ECF1; -fx-text-fill: #0C5460;");
                break;
            case Constants.STATUS_RESOLVED:
                label.setText("Đã xử lý xong");
                label.setStyle(label.getStyle() + "-fx-background-color: #D4EDDA; -fx-text-fill: #155724;");
                break;
            case Constants.STATUS_CANCELLED:
                label.setText("Đã hủy");
                label.setStyle(label.getStyle() + "-fx-background-color: #E2E3E5; -fx-text-fill: #383D41;");
                break;
            case Constants.STATUS_PENDING:
            default:
                label.setText("Chờ điều phối");
                label.setStyle(label.getStyle() + "-fx-background-color: #E9ECEF; -fx-text-fill: #495057;");
                break;
        }
        return label;
    }

    public static HBox createConnectionIndicator(boolean isConnected) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        Circle circle = new Circle(5);
        Label label = new Label();
        label.setStyle("-fx-font-size: 11px;");

        if (isConnected) {
            circle.setFill(Color.web("#2E9E5B"));
            label.setText("Đã kết nối");
            label.setStyle(label.getStyle() + "-fx-text-fill: #2E9E5B;");
        } else {
            circle.setFill(Color.web("#D32F2F"));
            label.setText("Mất kết nối");
            label.setStyle(label.getStyle() + "-fx-text-fill: #D32F2F;");
        }
        box.getChildren().addAll(circle, label);
        return box;
    }
}

