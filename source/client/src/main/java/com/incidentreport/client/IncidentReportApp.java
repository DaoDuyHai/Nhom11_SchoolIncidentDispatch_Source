package com.incidentreport.client;

import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.client.ui.*;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Ứng dụng Desktop JavaFX - Hệ thống Báo cáo & Điều phối Sự cố Trường THPT (SIDS)
 * Áp dụng JavaFX Theme "Giấy - Mực - Đồng" (DESIGN_SYSTEM.md)
 */
public class IncidentReportApp extends Application {

    private static Stage primaryStage;
    private static User currentUser;

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void showView(Parent root, double width, double height, boolean resizable) {
        if (primaryStage != null) {
            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root, width, height);
                ComponentFactory.applyTheme(scene);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
                ComponentFactory.applyTheme(scene);
                primaryStage.setWidth(width);
                primaryStage.setHeight(height);
            }
            primaryStage.setResizable(resizable);
            primaryStage.centerOnScreen();
        }
    }

    public static void showMainView(User user) {
        currentUser = user;
        Parent mainView;
        String titleRole = "";

        if (Constants.ROLE_ADMIN.equalsIgnoreCase(user.getRole())) {
            mainView = new AdminView(user);
            titleRole = "Ban Giám Hiệu (Admin)";
        } else if (Constants.ROLE_RESPONDER.equalsIgnoreCase(user.getRole())) {
            mainView = new ResponderView(user);
            titleRole = "Cán bộ Phụ trách (" + user.getSpecialty() + ")";
        } else {
            mainView = new ReporterView(user);
            titleRole = "Người báo cáo (" + user.getFullName() + ")";
        }

        if (primaryStage != null) {
            primaryStage.setTitle("SIDS - " + titleRole);
        }

        // Tối ưu kích thước thích ứng (Responsive) theo màn hình laptop 13 inch đến 16 inch:
        // - Laptop 13 inch (1366x768 hoặc 1920x1080 scaling 150% -> viewport khả dụng ~1280x720):
        //   Chiều cao tối ưu 640-670px để nằm gọn trên màn hình, không bị taskbar che.
        // - Laptop 16 inch (1920x1080 scaling 100-125% -> viewport >= 1536x864):
        //   Cửa sổ mở rộng 1180x740 hoặc phóng to toàn màn hình mượt mà.
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double targetWidth = Math.min(1180, Math.max(1040, bounds.getWidth() * 0.94));
        double targetHeight = Math.min(740, Math.max(580, bounds.getHeight() * 0.90));

        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(560);

        showView(mainView, targetWidth, targetHeight, true);
    }

    public static void logout() {
        SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_LOGOUT, "LOGOUT"));
        SocketClientManager.getInstance().disconnect();
        SocketClientManager.getInstance().clearListeners();
        currentUser = null;

        if (primaryStage != null) {
            primaryStage.setTitle("SIDS - Sổ trực sự cố trường học");
        }
        showView(new LoginView(), 880, 540, false);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("SIDS - Sổ trực sự cố trường học");

        primaryStage.setOnCloseRequest(e -> {
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_LOGOUT, "LOGOUT"));
            SocketClientManager.getInstance().disconnect();
            System.exit(0);
        });

        Scene scene = new Scene(new LoginView(), 880, 540);
        ComponentFactory.applyTheme(scene);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
