package com.incidentreport.client.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.incidentreport.client.IncidentReportApp;
import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ResponderView extends BorderPane implements SocketClientManager.PacketListener {

    private final User currentUser;
    private final Gson gson = new Gson();

    private final ComboBox<String> cbAvailability = new ComboBox<>();
    private final Label lblWorkload = new Label("Đang xử lý: 0");
    private final VBox taskCardsContainer = new VBox(12);
    private final ObservableList<IncidentTicket> myTasks = FXCollections.observableArrayList();
    private final VBox openPoolCardsContainer = new VBox(12);
    private final ObservableList<IncidentTicket> openPoolTasks = FXCollections.observableArrayList();
    private final TabPane tabPane = new TabPane();
    private Tab tabOpenPool;
    private Tab tabTasks;
    private ChatPanel chatPanel;
    private Tab tabChat;

    public ResponderView(User user) {
        this.currentUser = user;
        initUI();
        SocketClientManager.getInstance().addListener(this);
        loadMyTasks();
        loadOpenPool();

        // Tự động làm mới nhiệm vụ và hồ công việc mở mỗi 5s
        javafx.animation.Timeline autoRefresh = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), e -> {
                loadMyTasks();
                loadOpenPool();
            })
        );
        autoRefresh.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        autoRefresh.play();
    }

    private void initUI() {
        // Top Header
        HBox topBar = new HBox(15);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 20, 12, 20));
        topBar.setStyle("-fx-background-color: #1E5AA8;");

        Label lblTitle = new Label("KHU VỰC ĐIỀU PHỐI - " + currentUser.getSpecialty());
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lblTitle.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Công tắc trạng thái sẵn sàng
        cbAvailability.getItems().addAll("SẴN SÀNG (AVAILABLE)", "BẬN (BUSY)");
        cbAvailability.setValue("SẴN SÀNG (AVAILABLE)");
        cbAvailability.setStyle("-fx-font-weight: bold; -fx-background-color: #2E9E5B; -fx-text-fill: white;");
        cbAvailability.setOnAction(e -> {
            boolean isAvail = cbAvailability.getValue().startsWith("SẴN SÀNG");
            String status = isAvail ? Constants.AVAIL_AVAILABLE : Constants.AVAIL_BUSY;
            cbAvailability.setStyle(isAvail ?
                "-fx-font-weight: bold; -fx-background-color: #2E9E5B; -fx-text-fill: white;" :
                "-fx-font-weight: bold; -fx-background-color: #E8A317; -fx-text-fill: white;");
            if (IncidentReportApp.getHeartbeatSender() != null) {
                IncidentReportApp.getHeartbeatSender().setAvailability(status);
            }
        });

        lblWorkload.setTextFill(Color.WHITE);
        lblWorkload.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        Label lblHeartbeat = new Label("💓 Heartbeat 3s OK");
        lblHeartbeat.setTextFill(Color.web("#A8E6CF"));
        lblHeartbeat.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));

        Label lblUser = new Label("👤 " + currentUser.getFullName());
        lblUser.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lblUser.setTextFill(Color.WHITE);

        Button btnLogout = new Button("Đăng xuất");
        btnLogout.setStyle("-fx-background-color: #16437D; -fx-text-fill: white; -fx-padding: 5 12;");
        btnLogout.setOnAction(e -> IncidentReportApp.logout());

        topBar.getChildren().addAll(lblTitle, spacer, cbAvailability, lblWorkload, lblHeartbeat, lblUser, btnLogout);
        setTop(topBar);

        // Center Content
        tabOpenPool = new Tab("① 📋 Hồ công việc mở (Chờ tiếp nhận)", createOpenPoolTab());
        tabOpenPool.setClosable(false);

        tabTasks = new Tab("② 🛠️ Nhiệm vụ của tôi", createTasksTab());
        tabTasks.setClosable(false);

        chatPanel = new ChatPanel(currentUser);
        tabChat = new Tab("③ 💬 Trao đổi / Chat", chatPanel);
        tabChat.setClosable(false);

        tabPane.getTabs().addAll(tabOpenPool, tabTasks, tabChat);
        setCenter(tabPane);
    }

    private Node createOpenPoolTab() {
        VBox layout = new VBox(12);
        layout.setPadding(new Insets(15));
        layout.setStyle("-fx-background-color: #F4F6F9;");

        HBox toolBar = new HBox(12);
        toolBar.setAlignment(Pos.CENTER_LEFT);

        Label lblSection = new Label("CÁC SỰ CỐ ĐANG CHỜ NHÂN SỰ TIẾP NHẬN (TỰ CHỌN VIỆC)");
        lblSection.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lblSection.setTextFill(Color.web("#1E5AA8"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnRefresh = new Button("Làm mới hồ công việc");
        btnRefresh.setOnAction(e -> loadOpenPool());
        toolBar.getChildren().addAll(lblSection, spacer, btnRefresh);

        openPoolCardsContainer.setPadding(new Insets(5));
        ScrollPane scrollPane = new ScrollPane(openPoolCardsContainer);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        layout.getChildren().addAll(toolBar, scrollPane);
        return layout;
    }

    private Node createTasksTab() {
        VBox layout = new VBox(12);
        layout.setPadding(new Insets(15));
        layout.setStyle("-fx-background-color: #F4F6F9;");

        HBox toolBar = new HBox(10);
        Button btnRefresh = new Button("Làm mới nhiệm vụ");
        btnRefresh.setOnAction(e -> loadMyTasks());
        toolBar.getChildren().add(btnRefresh);

        taskCardsContainer.setPadding(new Insets(5));
        ScrollPane scrollPane = new ScrollPane(taskCardsContainer);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        layout.getChildren().addAll(toolBar, scrollPane);
        return layout;
    }

    private void renderTaskCards() {
        taskCardsContainer.getChildren().clear();

        if (myTasks.isEmpty()) {
            Label lblEmpty = new Label("Hiện chưa có nhiệm vụ nào. Bạn đang ở trạng thái sẵn sàng tiếp nhận!");
            lblEmpty.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
            lblEmpty.setTextFill(Color.GRAY);
            lblEmpty.setPadding(new Insets(30));
            taskCardsContainer.getChildren().add(lblEmpty);
            lblWorkload.setText("Đang xử lý: 0");
            return;
        }

        int activeCount = 0;
        for (IncidentTicket t : myTasks) {
            if (Constants.STATUS_ASSIGNED.equals(t.getStatus()) || Constants.STATUS_IN_PROGRESS.equals(t.getStatus())) {
                activeCount++;
            }
            taskCardsContainer.getChildren().add(createCardForTicket(t));
        }
        lblWorkload.setText("Đang xử lý: " + activeCount);
    }

    private Node createCardForTicket(IncidentTicket t) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #D9DEE5; -fx-border-radius: 8px;");

        // Top line
        HBox topLine = new HBox(10);
        topLine.setAlignment(Pos.CENTER_LEFT);

        Label lblCode = new Label(t.getTicketCode() + " - Phòng " + t.getRoomLocation());
        lblCode.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topLine.getChildren().addAll(
            lblCode, spacer,
            ComponentFactory.createSeverityBadge(t.getBaseSeverity()),
            ComponentFactory.createStatusBadge(t.getStatus())
        );

        Label lblTitle = new Label(t.getTitle() + " (" + t.getReportCount() + " người báo)");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));

        Label lblTime = new Label("Giao lúc: " + (t.getAcceptedAt() != null ? t.getAcceptedAt() : t.getCreatedAt()));
        lblTime.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        lblTime.setTextFill(Color.GRAY);

        // Buttons
        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        if (Constants.STATUS_ASSIGNED.equals(t.getStatus())) {
            Button btnStart = new Button("BẮT ĐẦU XỬ LÝ");
            btnStart.setStyle("-fx-background-color: #1E5AA8; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 14;");
            btnStart.setOnAction(e -> {
                String payload = String.format("STATUS_UPDATE|ticketId=%d|status=%s", t.getTicketId(), Constants.STATUS_IN_PROGRESS);
                SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_STATUS_UPDATE, payload));
                t.setStatus(Constants.STATUS_IN_PROGRESS);
                renderTaskCards();
            });
            actionBox.getChildren().add(btnStart);

        } else if (Constants.STATUS_IN_PROGRESS.equals(t.getStatus())) {
            Button btnResolve = new Button("HOÀN THÀNH");
            btnResolve.setStyle("-fx-background-color: #2E9E5B; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 14;");
            btnResolve.setOnAction(e -> {
                TextInputDialog dialog = new TextInputDialog("Đã sửa chữa và kiểm tra an toàn.");
                dialog.setTitle("Hoàn thành Sự cố");
                dialog.setHeaderText("Xác nhận hoàn thành xử lý sự cố " + t.getTicketCode());
                dialog.setContentText("Ghi chú xử lý:");
                Optional<String> result = dialog.showAndWait();
                result.ifPresent(note -> {
                    String payload = String.format("STATUS_UPDATE|ticketId=%d|status=%s|note=%s",
                        t.getTicketId(), Constants.STATUS_RESOLVED, note.replace("|", "/"));
                    SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_STATUS_UPDATE, payload));
                    t.setStatus(Constants.STATUS_RESOLVED);
                    renderTaskCards();
                });
            });
            actionBox.getChildren().add(btnResolve);
        }

        Button btnChat = new Button("💬 Trao đổi (Chat)");
        btnChat.setStyle("-fx-background-color: #0D9488; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;");
        btnChat.setOnAction(e -> {
            chatPanel.selectTicket(t.getTicketId(), t.getTicketCode() + " - Phòng " + t.getRoomLocation());
            tabPane.getSelectionModel().select(tabChat);
        });
        actionBox.getChildren().add(btnChat);

        card.getChildren().addAll(topLine, lblTitle, lblTime, actionBox);
        return card;
    }

    private void renderOpenPoolCards() {
        openPoolCardsContainer.getChildren().clear();

        if (openPoolTasks.isEmpty()) {
            Label lblEmpty = new Label("Hiện không có sự cố nào đang chờ tiếp nhận. Hệ thống đang hoạt động an toàn!");
            lblEmpty.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
            lblEmpty.setTextFill(Color.GRAY);
            lblEmpty.setPadding(new Insets(30));
            openPoolCardsContainer.getChildren().add(lblEmpty);
            return;
        }

        for (IncidentTicket t : openPoolTasks) {
            openPoolCardsContainer.getChildren().add(createCardForOpenPoolTicket(t));
        }
    }

    private Node createCardForOpenPoolTicket(IncidentTicket t) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #D9DEE5; -fx-border-radius: 8px;");

        HBox topLine = new HBox(10);
        topLine.setAlignment(Pos.CENTER_LEFT);

        Label lblCode = new Label(t.getTicketCode() + " - Phòng " + t.getRoomLocation());
        lblCode.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblPriority = new Label(String.format("⭐ Ưu tiên: %.1f", t.getComputedPriority()));
        lblPriority.setStyle("-fx-background-color: #FFF3CD; -fx-text-fill: #856404; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 4px;");

        topLine.getChildren().addAll(
            lblCode, spacer,
            lblPriority,
            ComponentFactory.createSeverityBadge(t.getBaseSeverity()),
            ComponentFactory.createStatusBadge(t.getStatus())
        );

        Label lblCategory = new Label("Loại sự cố: " + (t.getCategoryName() != null ? t.getCategoryName() : t.getCategoryCode())
            + "   |   Số người báo: " + t.getReportCount() + "   |   Báo lúc: " + t.getCreatedAt());
        lblCategory.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lblCategory.setTextFill(Color.web("#2C3E50"));

        Label lblTitle = new Label("\"" + t.getTitle() + "\"");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));

        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnChat = new Button("💬 Trao đổi");
        btnChat.setStyle("-fx-background-color: #0D9488; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 12; -fx-cursor: hand;");
        btnChat.setOnAction(e -> {
            chatPanel.selectTicket(t.getTicketId(), t.getTicketCode() + " - Phòng " + t.getRoomLocation());
            tabPane.getSelectionModel().select(tabChat);
        });

        Button btnClaim = new Button("👉 NHẬN VIỆC (Claim Task)");
        btnClaim.setStyle("-fx-background-color: #2E9E5B; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 16; -fx-cursor: hand;");
        btnClaim.setOnAction(e -> {
            String payload = String.format("ACK_TASK|ticketId=%d|action=ACCEPT", t.getTicketId());
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_ACK_TASK, payload));
        });

        actionBox.getChildren().addAll(btnChat, btnClaim);

        card.getChildren().addAll(topLine, lblCategory, lblTitle, actionBox);
        return card;
    }

    private void loadMyTasks() {
        SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_LIST_MY_TICKETS, "LIST_MY_TICKETS"));
    }

    private void loadOpenPool() {
        SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_GET_OPEN_POOL, "GET_OPEN_POOL"));
    }

    @Override
    public void onPacketReceived(Packet packet) {
        if (packet.getType() == Constants.TYPE_DISPATCH_PUSH) {
            // Khi có lệnh DISPATCH_PUSH từ Server -> Mở ngay Popup đếm ngược 30s!
            DispatchRequestDialog.show(packet);

        } else if (packet.getType() == Constants.TYPE_ACK_RESP) {
            Map<String, String> p = packet.parseParams();
            String status = p.get("status");
            if ("OK".equalsIgnoreCase(status)) {
                new Alert(Alert.AlertType.INFORMATION, "✅ Nhận ca xử lý thành công!").show();
                loadMyTasks();
                loadOpenPool();
                if (tabTasks != null) {
                    tabPane.getSelectionModel().select(tabTasks);
                }
            } else if ("TASK_ALREADY_TAKEN".equalsIgnoreCase(status)) {
                new Alert(Alert.AlertType.WARNING, "⚠️ Nhiệm vụ này đã có nhân viên khác tiếp nhận trước!").show();
                loadOpenPool();
            }

        } else if (packet.getType() == Constants.TYPE_GET_OPEN_POOL_RESP) {
            try {
                Type listType = new TypeToken<ArrayList<IncidentTicket>>(){}.getType();
                List<IncidentTicket> list = gson.fromJson(packet.getHeaderPayload(), listType);
                openPoolTasks.setAll(list);
                renderOpenPoolCards();
            } catch (Exception ignored) {}

        } else if (packet.getType() == Constants.TYPE_LIST_TICKETS_RESP) {
            try {
                Type listType = new TypeToken<ArrayList<IncidentTicket>>(){}.getType();
                List<IncidentTicket> list = gson.fromJson(packet.getHeaderPayload(), listType);
                myTasks.setAll(list);
                renderTaskCards();
                if (chatPanel != null) {
                    chatPanel.setTickets(list);
                }
            } catch (Exception ignored) {}

        } else if (packet.getType() == Constants.TYPE_STATUS_UPDATE) {
            loadMyTasks();
            loadOpenPool();

        } else if (packet.getType() == Constants.TYPE_CHAT_PUSH) {
            if (chatPanel != null) {
                chatPanel.onChatPush(packet);
            }
        } else if (packet.getType() == Constants.TYPE_CHAT_HISTORY_RESP) {
            if (chatPanel != null) {
                chatPanel.onChatHistoryResponse(packet);
            }
        }
    }

    @Override
    public void onDisconnected() {}
}

