package com.incidentreport.client;

import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.client.net.UDPHeartbeatSender;
import com.incidentreport.client.ui.LoginView;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class IncidentReportApp extends Application {

    private static Stage primaryStage;
    private static User currentUser;
    private static UDPHeartbeatSender heartbeatSender;

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void setHeartbeatSender(UDPHeartbeatSender sender) {
        heartbeatSender = sender;
    }

    public static UDPHeartbeatSender getHeartbeatSender() {
        return heartbeatSender;
    }

    public static void showView(Parent root) {
        if (primaryStage != null) {
            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root, 1080, 680);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
        }
    }

    public static void logout() {
        if (heartbeatSender != null) {
            heartbeatSender.stop();
            heartbeatSender = null;
        }
        SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_LOGOUT, "LOGOUT"));
        SocketClientManager.getInstance().disconnect();
        currentUser = null;

        showView(new LoginView());
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("Hệ thống Báo cáo & Điều phối Sự cố Trường THPT (School Incident Dispatch)");

        primaryStage.setOnCloseRequest(e -> {
            if (heartbeatSender != null) heartbeatSender.stop();
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_LOGOUT, "LOGOUT"));
            SocketClientManager.getInstance().disconnect();
            System.exit(0);
        });

        Scene scene = new Scene(new LoginView(), 520, 520);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

