package com.incidentreport.client;

import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.client.ui.AdminView;
import com.incidentreport.client.ui.LoginView;
import com.incidentreport.client.ui.ReporterView;
import com.incidentreport.client.ui.ResponderView;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Ứng dụng Desktop JavaFX - Hệ thống Báo cáo & Điều phối Sự cố Trường THPT
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

    public static void showView(Parent root, double width, double height) {
        if (primaryStage != null) {
            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root, width, height);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
                primaryStage.setWidth(width);
                primaryStage.setHeight(height);
                primaryStage.centerOnScreen();
            }
        }
    }

    public static void showMainView(User user) {
        currentUser = user;
        Parent mainView;
        String titleRole = "";

        if (Constants.ROLE_ADMIN.equalsIgnoreCase(user.getRole())) {
            mainView = new AdminView(user);
            titleRole = "Ban Giám Hiệu (Admin/Server)";
        } else if (Constants.ROLE_RESPONDER.equalsIgnoreCase(user.getRole())) {
            mainView = new ResponderView(user);
            titleRole = "Cán bộ Phụ trách (" + user.getSpecialty() + ")";
        } else {
            mainView = new ReporterView(user);
            titleRole = "Người báo cáo (" + user.getFullName() + ")";
        }

        if (primaryStage != null) {
            primaryStage.setTitle("Hệ thống Điều phối Sự cố THPT - " + titleRole);
        }

        showView(mainView, 1120, 720);
    }

    public static void logout() {
        SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_LOGOUT, "LOGOUT"));
        SocketClientManager.getInstance().disconnect();
        currentUser = null;

        if (primaryStage != null) {
            primaryStage.setTitle("Đăng nhập - Hệ thống Điều phối Sự cố THPT");
        }
        showView(new LoginView(), 520, 560);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("Đăng nhập - Hệ thống Điều phối Sự cố THPT");

        primaryStage.setOnCloseRequest(e -> {
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_LOGOUT, "LOGOUT"));
            SocketClientManager.getInstance().disconnect();
            System.exit(0);
        });

        Scene scene = new Scene(new LoginView(), 520, 560);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
