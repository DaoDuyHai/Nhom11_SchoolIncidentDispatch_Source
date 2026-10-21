package com.incidentreport.client.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.incidentreport.client.IncidentReportApp;
import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phân hệ Cán bộ tiếp nhận & Xử lý sự cố (Responder):
 * - Hàng đợi phòng ban (Nhận ngay khi online hoặc tải tồn đọng khi vừa đăng nhập).
 * - Tiếp nhận (Claim) một chạm.
 * - Danh sách nhiệm vụ đang phụ trách & nút Hoàn tất xử lý sự cố.
 * - Kênh chat 1-1 riêng biệt với người báo cáo (tự động khóa khi giải quyết xong).
 */
public class ResponderView extends BorderPane implements SocketClientManager.PacketListener {

    private final User currentUser;
    private final Gson gson = new Gson();

    private final ObservableList<IncidentTicket> deptPendingList = FXCollections.observableArrayList();
    private final TableView<IncidentTicket> deptTable = new TableView<>();

    private final ObservableList<IncidentTicket> myTasksList = FXCollections.observableArrayList();
    private final TableView<IncidentTicket> myTasksTable = new TableView<>();

    private final TabPane tabPane = new TabPane();
    private Tab tabDeptQueue;
    private Tab tabMyTasks;
    private ChatPanel chatPanel;
    private Tab tabChat;

    public ResponderView(User user) {
        this.currentUser = user;
        initUI();
        SocketClientManager.getInstance().addListener(this);
        loadDeptTickets();
        loadMyTasks();
    }

    private void initUI() {
        // Top Header
        HBox topBar = new HBox(15);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 20, 12, 20));
        topBar.setStyle("-fx-background-color: #0F172A;");

        Label lblTitle = new Label("PHÒNG CHUYÊN TRÁCH: " + currentUser.getSpecialty());
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lblTitle.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox connBox = ComponentFactory.createConnectionIndicator(true);
        Label lblUser = new Label("👤 " + currentUser.getFullName() + " [" + currentUser.getUsername() + "]");
        lblUser.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lblUser.setTextFill(Color.WHITE);

        Button btnLogout = new Button("Đăng xuất");
        btnLogout.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-padding: 5 12; -fx-background-radius: 4;");
        btnLogout.setOnAction(e -> IncidentReportApp.logout());

        topBar.getChildren().addAll(lblTitle, spacer, connBox, lblUser, btnLogout);
        setTop(topBar);

        // Center Content - TabPane
        tabDeptQueue = new Tab("① 📥 Hàng đợi sự cố phòng ban", createDeptQueueTab());
        tabDeptQueue.setClosable(false);

        tabMyTasks = new Tab("② 🛠️ Nhiệm vụ của tôi", createMyTasksTab());
        tabMyTasks.setClosable(false);

        chatPanel = new ChatPanel(currentUser);
        tabChat = new Tab("③ 💬 Trao đổi 1-1", chatPanel);
        tabChat.setClosable(false);

        tabPane.getTabs().addAll(tabDeptQueue, tabMyTasks, tabChat);
        setCenter(tabPane);
    }

    private Node createDeptQueueTab() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));
        layout.setStyle("-fx-background-color: white;");

        HBox toolBar = new HBox(12);
        toolBar.setAlignment(Pos.CENTER_LEFT);

        Label lblDesc = new Label("CÁC SỰ CỐ ĐƯỢC HỆ THỐNG TỰ ĐỘNG ĐIỀU PHỐI TỚI PHÒNG: " + currentUser.getSpecialty());
        lblDesc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lblDesc.setTextFill(Color.web("#1E293B"));

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRefresh = new Button("🔄 Làm mới hàng đợi");
        btnRefresh.setOnAction(e -> loadDeptTickets());
        toolBar.getChildren().addAll(lblDesc, sp, btnRefresh);

        // Columns
        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("Mã sự cố");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setPrefWidth(120);

        TableColumn<IncidentTicket, String> colRoom = new TableColumn<>("Vị trí");
        colRoom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRoomLocation()));
        colRoom.setPrefWidth(90);

        TableColumn<IncidentTicket, String> colCat = new TableColumn<>("Phân loại");
        colCat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategoryName() != null ? d.getValue().getCategoryName() : d.getValue().getCategoryCode()));
        colCat.setPrefWidth(170);

        TableColumn<IncidentTicket, String> colDesc = new TableColumn<>("Mô tả sự cố");
        colDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        colDesc.setPrefWidth(220);

        TableColumn<IncidentTicket, String> colReporter = new TableColumn<>("Người báo");
        colReporter.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getReporterName() + (d.getValue().getReporterPhone() != null && !d.getValue().getReporterPhone().isEmpty() ? " (" + d.getValue().getReporterPhone() + ")" : "")
        ));
        colReporter.setPrefWidth(180);

        TableColumn<IncidentTicket, String> colTime = new TableColumn<>("Thời gian báo");
        colTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCreatedAt()));
        colTime.setPrefWidth(150);

        TableColumn<IncidentTicket, Void> colClaim = new TableColumn<>("Tiếp nhận");
        colClaim.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("👉 TIẾP NHẬN");
            {
                btn.setStyle("-fx-background-color: #16A34A; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 4; -fx-cursor: hand;");
                btn.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        claimTicket(t.getTicketId());
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else setGraphic(btn);
            }
        });
        colClaim.setPrefWidth(110);

        deptTable.getColumns().addAll(colCode, colRoom, colCat, colDesc, colReporter, colTime, colClaim);
        deptTable.setItems(deptPendingList);
        VBox.setVgrow(deptTable, Priority.ALWAYS);

        layout.getChildren().addAll(toolBar, deptTable);
        return layout;
    }

    private Node createMyTasksTab() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));
        layout.setStyle("-fx-background-color: white;");

        HBox toolBar = new HBox(12);
        toolBar.setAlignment(Pos.CENTER_LEFT);

        Label lblDesc = new Label("CÁC SỰ CỐ ĐANG PHỤ TRÁCH HOẶC ĐÃ HOÀN TẤT");
        lblDesc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lblDesc.setTextFill(Color.web("#1E293B"));

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRefresh = new Button("🔄 Làm mới nhiệm vụ");
        btnRefresh.setOnAction(e -> loadMyTasks());
        toolBar.getChildren().addAll(lblDesc, sp, btnRefresh);

        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("Mã sự cố");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setPrefWidth(120);

        TableColumn<IncidentTicket, String> colRoom = new TableColumn<>("Vị trí");
        colRoom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRoomLocation()));
        colRoom.setPrefWidth(90);

        TableColumn<IncidentTicket, String> colCat = new TableColumn<>("Phân loại");
        colCat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategoryName() != null ? d.getValue().getCategoryName() : d.getValue().getCategoryCode()));
        colCat.setPrefWidth(160);

        TableColumn<IncidentTicket, String> colStatus = new TableColumn<>("Trạng thái");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setGraphic(null);
                else setGraphic(ComponentFactory.createStatusBadge(item));
            }
        });
        colStatus.setPrefWidth(120);

        TableColumn<IncidentTicket, String> colReporter = new TableColumn<>("Người báo");
        colReporter.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getReporterName() + (d.getValue().getReporterPhone() != null && !d.getValue().getReporterPhone().isEmpty() ? " (" + d.getValue().getReporterPhone() + ")" : "")
        ));
        colReporter.setPrefWidth(180);

        TableColumn<IncidentTicket, Void> colChatAction = new TableColumn<>("Trao đổi 1-1");
        colChatAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnChat = new Button("💬 Chat 1-1");
            {
                btnChat.setStyle("-fx-background-color: #0284C7; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 4; -fx-cursor: hand;");
                btnChat.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        chatPanel.openTicketChat(t);
                        tabPane.getSelectionModel().select(tabChat);
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else setGraphic(btnChat);
            }
        });
        colChatAction.setPrefWidth(100);

        TableColumn<IncidentTicket, Void> colResolve = new TableColumn<>("Xử lý xong");
        colResolve.setCellFactory(col -> new TableCell<>() {
            private final Button btnDone = new Button("✅ Hoàn tất");
            {
                btnDone.setStyle("-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 4; -fx-cursor: hand;");
                btnDone.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        promptResolveTicket(t);
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (Constants.STATUS_IN_PROGRESS.equalsIgnoreCase(t.getStatus())) {
                        setGraphic(btnDone);
                    } else {
                        setGraphic(new Label("✓"));
                    }
                }
            }
        });
        colResolve.setPrefWidth(100);

        myTasksTable.getColumns().addAll(colCode, colRoom, colCat, colStatus, colReporter, colChatAction, colResolve);
        myTasksTable.setItems(myTasksList);
        VBox.setVgrow(myTasksTable, Priority.ALWAYS);

        layout.getChildren().addAll(toolBar, myTasksTable);
        return layout;
    }

    private void claimTicket(int ticketId) {
        Packet packet = new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId);
        SocketClientManager.getInstance().sendPacket(packet);
    }

    private void promptResolveTicket(IncidentTicket ticket) {
        TextInputDialog dialog = new TextInputDialog("Đã sửa chữa và kiểm tra an toàn.");
        dialog.setTitle("Hoàn tất Sự cố");
        dialog.setHeaderText("Xác nhận hoàn thành xử lý sự cố " + ticket.getTicketCode());
        dialog.setContentText("Ghi chú nghiệm thu:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(note -> {
            String payload = String.format("STATUS_UPDATE|ticketId=%d|status=%s|note=%s",
                    ticket.getTicketId(), Constants.STATUS_RESOLVED, note.replace("|", "/"));
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_STATUS_UPDATE, payload));
            ticket.setStatus(Constants.STATUS_RESOLVED);
            myTasksTable.refresh();
        });
    }

    private void loadDeptTickets() {
        SocketClientManager.getInstance().sendPacket(
                new Packet(Constants.TYPE_LIST_TICKETS, "LIST_TICKETS|scope=DEPARTMENT"));
    }

    private void loadMyTasks() {
        SocketClientManager.getInstance().sendPacket(
                new Packet(Constants.TYPE_LIST_TICKETS, "LIST_TICKETS|scope=MY"));
    }

    @Override
    public void onPacketReceived(Packet packet) {
        if (packet.getType() == Constants.TYPE_DISPATCH_NOTIFY) {
            // Nhận báo cáo mới được đẩy về phòng ban theo thời gian thực (Server Push)
            Map<String, String> p = packet.parseParams();
            String code = p.get("code");
            String room = p.get("room");
            String desc = p.get("desc");

            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("📢 SỰ CỐ MỚI ĐƯỢC ĐIỀU PHỐI ĐẾN PHÒNG");
                alert.setHeaderText("Sự cố " + code + " tại phòng " + room);
                alert.setContentText("Mô tả: " + desc + "\nVui lòng xem tab Hàng đợi phòng ban để tiếp nhận!");
                alert.show();
            });

            loadDeptTickets();

        } else if (packet.getType() == Constants.TYPE_CLAIM_RESP) {
            Map<String, String> p = packet.parseParams();
            String status = p.get("status");
            if ("OK".equalsIgnoreCase(status)) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "✅ Bạn đã tiếp nhận sự cố thành công!");
                alert.show();
                loadDeptTickets();
                loadMyTasks();
                tabPane.getSelectionModel().select(tabMyTasks);
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING, "⚠️ " + p.getOrDefault("message", "Sự cố đã được người khác tiếp nhận!"));
                alert.show();
                loadDeptTickets();
            }

        } else if (packet.getType() == Constants.TYPE_LIST_TICKETS_RESP) {
            if (packet.hasAttachment()) {
                try {
                    String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                    Type listType = new TypeToken<List<IncidentTicket>>() {}.getType();
                    List<IncidentTicket> list = gson.fromJson(json, listType);

                    // Phân biệt dữ liệu trả về cho Hàng đợi hoặc Nhiệm vụ
                    Map<String, String> p = packet.parseParams();
                    // Nếu là danh sách tất cả của department: lọc ra những ticket PENDING
                    if (list != null) {
                        deptPendingList.setAll(list.stream().filter(t -> Constants.STATUS_PENDING.equalsIgnoreCase(t.getStatus())).toList());
                        myTasksList.setAll(list.stream().filter(t -> currentUser.getUserId() == (t.getAssignedStaffId() != null ? t.getAssignedStaffId() : 0)).toList());
                    }
                } catch (Exception ignored) {}
            }

        } else if (packet.getType() == Constants.TYPE_STATUS_BROADCAST) {
            loadDeptTickets();
            loadMyTasks();
        }
    }

    @Override
    public void onDisconnected() {}
}
