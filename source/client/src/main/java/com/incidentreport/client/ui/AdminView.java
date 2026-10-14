package com.incidentreport.client.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.incidentreport.client.IncidentReportApp;
import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AdminView extends BorderPane implements SocketClientManager.PacketListener {

    private final User currentUser;
    private final Gson gson = new Gson();

    private final Label lblTotal = new Label("0");
    private final Label lblPending = new Label("0");
    private final Label lblInProgress = new Label("0");
    private final Label lblResolved = new Label("0");

    private final ComboBox<String> cbAlertLevel = new ComboBox<>();
    private final TextField txtAlertMsg = new TextField();

    private final TableView<IncidentTicket> allTicketsTable = new TableView<>();
    private final ObservableList<IncidentTicket> allTicketsList = FXCollections.observableArrayList();

    public AdminView(User user) {
        this.currentUser = user;
        initUI();
        SocketClientManager.getInstance().addListener(this);
        loadAllTickets();

        // Tự động làm mới bảng sự cố mỗi 5 giây
        javafx.animation.Timeline autoRefresh = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), e -> loadAllTickets())
        );
        autoRefresh.setCycleCount(javafx.animation.Timeline.INDEFINITE);
        autoRefresh.play();
    }

    private void initUI() {
        // Top Header
        HBox topBar = new HBox(15);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 20, 12, 20));
        topBar.setStyle("-fx-background-color: #2C3E50;");

        Label lblTitle = new Label("BẢNG ĐIỀU KHIỂN BAN GIÁM HIỆU (ADMIN)");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lblTitle.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblUser = new Label("👤 " + currentUser.getFullName() + " (" + currentUser.getSubRole() + ")");
        lblUser.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lblUser.setTextFill(Color.WHITE);

        Button btnLogout = new Button("Đăng xuất");
        btnLogout.setStyle("-fx-background-color: #34495E; -fx-text-fill: white; -fx-padding: 5 12;");
        btnLogout.setOnAction(e -> IncidentReportApp.logout());

        topBar.getChildren().addAll(lblTitle, spacer, lblUser, btnLogout);
        setTop(topBar);

        // Center Content
        VBox centerBox = new VBox(15);
        centerBox.setPadding(new Insets(15));
        centerBox.setStyle("-fx-background-color: #F4F6F9;");

        // 1. KPI Cards
        HBox kpiBox = new HBox(15);
        kpiBox.getChildren().addAll(
            createKpiCard("TỔNG SỐ SỰ CỐ", lblTotal, "#1E5AA8"),
            createKpiCard("CHỜ ĐIỀU PHỐI", lblPending, "#E8A317"),
            createKpiCard("ĐANG XỬ LÝ", lblInProgress, "#3498DB"),
            createKpiCard("ĐÃ HOÀN THÀNH", lblResolved, "#2E9E5B")
        );

        // 2. Multicast Emergency Broadcast Panel
        VBox alertBox = new VBox(10);
        alertBox.setPadding(new Insets(12, 16, 12, 16));
        alertBox.setStyle("-fx-background-color: #FFEBEE; -fx-border-color: #D32F2F; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        Label lblAlertTitle = new Label("🚨 PHÁT CẢNH BÁO KHẨN CẤP TOÀN TRƯỜNG (UDP MULTICAST 230.0.0.1:9999)");
        lblAlertTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lblAlertTitle.setTextFill(Color.web("#D32F2F"));

        HBox alertControlBox = new HBox(10);
        alertControlBox.setAlignment(Pos.CENTER_LEFT);

        cbAlertLevel.getItems().addAll("CRITICAL (Nguy hiểm cao nhất)", "EVACUATION (Sơ tán khẩn cấp)", "DRILL (Diễn tập)", "ALL_CLEAR (An toàn)");
        cbAlertLevel.setValue("CRITICAL (Nguy hiểm cao nhất)");

        txtAlertMsg.setPromptText("Nhập nội dung cảnh báo phát thanh tới toàn bộ màn hình phòng học...");
        HBox.setHgrow(txtAlertMsg, Priority.ALWAYS);

        Button btnBroadcast = new Button("PHÁT BÁO ĐỘNG NGAY");
        btnBroadcast.setStyle("-fx-background-color: #D32F2F; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 7 16;");
        btnBroadcast.setOnAction(e -> doBroadcastAlert());

        alertControlBox.getChildren().addAll(cbAlertLevel, txtAlertMsg, btnBroadcast);
        alertBox.getChildren().addAll(lblAlertTitle, alertControlBox);

        // 3. All Tickets Table
        VBox tableBox = new VBox(8);
        HBox tableHeader = new HBox(10);
        tableHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblTable = new Label("DANH SÁCH TOÀN BỘ SỰ CỐ");
        lblTable.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        Button btnRefresh = new Button("Làm mới dữ liệu");
        btnRefresh.setOnAction(e -> loadAllTickets());
        tableHeader.getChildren().addAll(lblTable, btnRefresh);

        initTableColumns();
        allTicketsTable.setItems(allTicketsList);
        VBox.setVgrow(allTicketsTable, Priority.ALWAYS);

        tableBox.getChildren().addAll(tableHeader, allTicketsTable);
        VBox.setVgrow(tableBox, Priority.ALWAYS);

        centerBox.getChildren().addAll(kpiBox, alertBox, tableBox);
        setCenter(centerBox);
    }

    private VBox createKpiCard(String title, Label valLabel, String colorHex) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12));
        card.setPrefWidth(220);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #D9DEE5; -fx-border-radius: 8px;");

        Label lbl = new Label(title);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        lbl.setTextFill(Color.GRAY);

        valLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        valLabel.setTextFill(Color.web(colorHex));

        card.getChildren().addAll(lbl, valLabel);
        return card;
    }

    private void initTableColumns() {
        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("Mã sự cố");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setPrefWidth(140);

        TableColumn<IncidentTicket, String> colRoom = new TableColumn<>("Phòng");
        colRoom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRoomLocation()));
        colRoom.setPrefWidth(90);

        TableColumn<IncidentTicket, String> colCat = new TableColumn<>("Loại sự cố");
        colCat.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getCategoryName() != null ? d.getValue().getCategoryName() : d.getValue().getCategoryCode()));
        colCat.setPrefWidth(160);

        TableColumn<IncidentTicket, Number> colSev = new TableColumn<>("Mức độ");
        colSev.setCellValueFactory(d -> new javafx.beans.property.SimpleIntegerProperty(d.getValue().getBaseSeverity()));
        colSev.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setGraphic(null);
                else setGraphic(ComponentFactory.createSeverityBadge(item.intValue()));
            }
        });
        colSev.setPrefWidth(95);

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
        colStatus.setPrefWidth(110);

        TableColumn<IncidentTicket, String> colStaff = new TableColumn<>("Nhân sự phụ trách");
        colStaff.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getAssignedStaffName() != null ? d.getValue().getAssignedStaffName() : "Chưa giao"
        ));
        colStaff.setPrefWidth(150);

        TableColumn<IncidentTicket, Number> colCount = new TableColumn<>("Số người báo");
        colCount.setCellValueFactory(d -> new javafx.beans.property.SimpleIntegerProperty(d.getValue().getReportCount()));
        colCount.setPrefWidth(100);

        TableColumn<IncidentTicket, String> colTime = new TableColumn<>("Thời gian tạo");
        colTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCreatedAt()));
        colTime.setPrefWidth(150);

        allTicketsTable.getColumns().addAll(colCode, colRoom, colCat, colSev, colStatus, colStaff, colCount, colTime);
    }

    private void doBroadcastAlert() {
        String msg = txtAlertMsg.getText().trim();
        if (msg.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Vui lòng nhập nội dung phát thanh cảnh báo!").show();
            return;
        }

        String rawLevel = cbAlertLevel.getValue();
        String level = rawLevel.split(" ")[0]; // Lấy CRITICAL, EVACUATION...

        String payload = String.format("ALERT|level=%s|message=%s", level, msg.replace("|", "/"));
        SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_MULTICAST_ALERT, payload));

        txtAlertMsg.clear();
        new Alert(Alert.AlertType.INFORMATION, "✅ Đã phát cảnh báo khẩn cấp tới kênh UDP Multicast!").show();
    }

    private void loadAllTickets() {
        SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_GET_ALL_TICKETS, "GET_ALL"));
    }

    private void updateKpis() {
        int total = allTicketsList.size();
        int pending = 0, inProg = 0, resolved = 0;

        for (IncidentTicket t : allTicketsList) {
            if (Constants.STATUS_PENDING.equals(t.getStatus())) pending++;
            else if (Constants.STATUS_ASSIGNED.equals(t.getStatus()) || Constants.STATUS_IN_PROGRESS.equals(t.getStatus())) inProg++;
            else if (Constants.STATUS_RESOLVED.equals(t.getStatus())) resolved++;
        }

        lblTotal.setText(String.valueOf(total));
        lblPending.setText(String.valueOf(pending));
        lblInProgress.setText(String.valueOf(inProg));
        lblResolved.setText(String.valueOf(resolved));
    }

    @Override
    public void onPacketReceived(Packet packet) {
        if (packet.getType() == Constants.TYPE_LIST_TICKETS_RESP) {
            try {
                Type listType = new TypeToken<ArrayList<IncidentTicket>>(){}.getType();
                List<IncidentTicket> list = gson.fromJson(packet.getHeaderPayload(), listType);
                allTicketsList.setAll(list);
                updateKpis();
            } catch (Exception ignored) {}

        } else if (packet.getType() == Constants.TYPE_STATUS_UPDATE) {
            loadAllTickets();
        }
    }

    @Override
    public void onDisconnected() {}
}

