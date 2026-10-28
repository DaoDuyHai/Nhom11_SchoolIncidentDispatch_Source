package com.incidentreport.client;

import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.client.ui.*;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
                primaryStage.centerOnScreen();
            }
            primaryStage.setResizable(resizable);
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

        showView(mainView, 1180, 740, true);
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
