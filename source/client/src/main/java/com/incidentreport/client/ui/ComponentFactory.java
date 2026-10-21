package com.incidentreport.client.ui;

import com.incidentreport.common.Constants;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public class ComponentFactory {

    public static Label createDepartmentBadge(String department) {
        Label label = new Label();
        label.setPadding(new Insets(3, 8, 3, 8));
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-background-radius: 12px;");

        if (department == null) department = Constants.SPECIALTY_TECHNICAL;

        switch (department) {
            case Constants.SPECIALTY_TECHNICAL:
                label.setText("KỸ THUẬT");
                label.setStyle(label.getStyle() + "-fx-background-color: #E3F2FD; -fx-text-fill: #1565C0;");
                break;
            case Constants.SPECIALTY_MEDICAL:
                label.setText("Y TẾ");
                label.setStyle(label.getStyle() + "-fx-background-color: #FCE4EC; -fx-text-fill: #C2185B;");
                break;
            case Constants.SPECIALTY_SECURITY:
                label.setText("AN NINH");
                label.setStyle(label.getStyle() + "-fx-background-color: #FFF3E0; -fx-text-fill: #E65100;");
                break;
            default:
                label.setText(department);
                label.setStyle(label.getStyle() + "-fx-background-color: #EDE7F6; -fx-text-fill: #512DA8;");
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
            case Constants.STATUS_IN_PROGRESS:
                label.setText("Đang xử lý");
                label.setStyle(label.getStyle() + "-fx-background-color: #FFF3CD; -fx-text-fill: #856404;");
                break;
            case Constants.STATUS_RESOLVED:
                label.setText("Đã hoàn tất");
                label.setStyle(label.getStyle() + "-fx-background-color: #D4EDDA; -fx-text-fill: #155724;");
                break;
            case Constants.STATUS_CANCELLED:
                label.setText("Đã hủy");
                label.setStyle(label.getStyle() + "-fx-background-color: #E2E3E5; -fx-text-fill: #383D41;");
                break;
            case Constants.STATUS_PENDING:
            default:
                label.setText("Chờ tiếp nhận");
                label.setStyle(label.getStyle() + "-fx-background-color: #FFEBEE; -fx-text-fill: #C62828;");
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
            label.setText("Đã kết nối Server");
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
