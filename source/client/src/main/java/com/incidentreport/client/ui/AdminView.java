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
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
    private final ObservableList<User> staffList = FXCollections.observableArrayList();
    private final TabPane tabPane = new TabPane();
    private ChatPanel chatPanel;
    private Tab tabChat;

    // Personnel Form Controls
    private final Label lblPersonnelFormMsg = new Label();
    private TextField txtNewStaffUname;
    private PasswordField txtNewStaffPass;
    private TextField txtNewStaffName;
    private TextField txtNewStaffPhone;

    public AdminView(User user) {
        this.currentUser = user;
        initUI();
        SocketClientManager.getInstance().addListener(this);
        loadAllTickets();
        loadStaffList();

        // Tự động làm mới bảng sự cố mỗi 5 giây
        javafx.animation.Timeline autoRefresh = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), e -> {
                loadAllTickets();
                loadStaffList();
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
        SortedList<IncidentTicket> sortedTickets = new SortedList<>(allTicketsList);
        sortedTickets.comparatorProperty().bind(allTicketsTable.comparatorProperty());
        allTicketsTable.setItems(sortedTickets);
        VBox.setVgrow(allTicketsTable, Priority.ALWAYS);

        tableBox.getChildren().addAll(tableHeader, allTicketsTable);
        VBox.setVgrow(tableBox, Priority.ALWAYS);

        centerBox.getChildren().addAll(kpiBox, alertBox, tableBox);

        Tab tabDashboard = new Tab("① 📊 Tổng quan & Điều hành", centerBox);
        tabDashboard.setClosable(false);

        chatPanel = new ChatPanel(currentUser);
        tabChat = new Tab("② 💬 Kênh Trao Đổi / Chat", chatPanel);
        tabChat.setClosable(false);

        Tab tabPersonnel = new Tab("③ 👥 Quản lý nhân sự", createPersonnelTabContent());
        tabPersonnel.setClosable(false);

        tabPane.getTabs().addAll(tabDashboard, tabChat, tabPersonnel);
        setCenter(tabPane);
    }

    private Node createPersonnelTabContent() {
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.38);

        // --- BÊN TRÁI: FORM TẠO TÀI KHOẢN NHÂN SỰ ---
        VBox formCard = new VBox(12);
        formCard.setPadding(new Insets(16));
        formCard.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #D9DEE5; -fx-border-radius: 8px;");

        Label lblFormTitle = new Label("👤 TẠO TÀI KHOẢN NHÂN SỰ MỚI");
        lblFormTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        lblFormTitle.setTextFill(Color.web("#1E5AA8"));

        Label lblFormDesc = new Label("Chỉ ADMIN mới có quyền khởi tạo tài khoản RESPONDER hoặc ADMIN.");
        lblFormDesc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        lblFormDesc.setTextFill(Color.GRAY);

        txtNewStaffUname = new TextField();
        txtNewStaffUname.setPromptText("Tên đăng nhập (VD: kythuat_tuan, yte_lan)");

        txtNewStaffPass = new PasswordField();
        txtNewStaffPass.setPromptText("Mật khẩu ban đầu (tối thiểu 4 ký tự)");

        txtNewStaffName = new TextField();
        txtNewStaffName.setPromptText("Họ và tên đầy đủ");

        txtNewStaffPhone = new TextField();
        txtNewStaffPhone.setPromptText("Số điện thoại liên hệ");

        ComboBox<String> cbRole = new ComboBox<>();
        cbRole.getItems().addAll("RESPONDER (Nhân viên ứng phó)", "ADMIN (Ban Giám Hiệu)");
        cbRole.getSelectionModel().select(0);
        cbRole.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> cbSpecialty = new ComboBox<>();
        cbSpecialty.getItems().addAll(
            "Kỹ thuật / Thiết bị (TECHNICAL)",
            "Y tế học đường (MEDICAL)",
            "Bảo vệ / An ninh (SECURITY)"
        );
        cbSpecialty.getSelectionModel().select(0);
        cbSpecialty.setMaxWidth(Double.MAX_VALUE);

        cbRole.setOnAction(e -> {
            boolean isResp = cbRole.getSelectionModel().getSelectedIndex() == 0;
            cbSpecialty.setDisable(!isResp);
        });

        lblPersonnelFormMsg.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lblPersonnelFormMsg.setWrapText(true);

        Button btnCreate = new Button("➕ TẠO TÀI KHOẢN");
        btnCreate.setStyle("-fx-background-color: #1E5AA8; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 9 20; -fx-background-radius: 5px; -fx-cursor: hand;");
        btnCreate.setMaxWidth(Double.MAX_VALUE);

        btnCreate.setOnAction(e -> {
            String u = txtNewStaffUname.getText().trim();
            String p = txtNewStaffPass.getText();
            String n = txtNewStaffName.getText().trim();
            String ph = txtNewStaffPhone.getText().trim();

            if (u.isEmpty() || p.isEmpty() || n.isEmpty()) {
                lblPersonnelFormMsg.setText("Vui lòng nhập đầy đủ Tên đăng nhập, Mật khẩu và Họ tên!");
                lblPersonnelFormMsg.setTextFill(Color.web("#D32F2F"));
                return;
            }

            if (p.length() < 4) {
                lblPersonnelFormMsg.setText("Mật khẩu phải từ 4 ký tự trở lên!");
                lblPersonnelFormMsg.setTextFill(Color.web("#D32F2F"));
                return;
            }

            boolean isResp = cbRole.getSelectionModel().getSelectedIndex() == 0;
            String role = isResp ? Constants.ROLE_RESPONDER : Constants.ROLE_ADMIN;
            String specialty = Constants.SPECIALTY_NONE;
            String subRole = Constants.SUB_ROLE_PRINCIPAL;

            if (isResp) {
                int specIdx = cbSpecialty.getSelectionModel().getSelectedIndex();
                if (specIdx == 1) {
                    specialty = Constants.SPECIALTY_MEDICAL;
                    subRole = Constants.SUB_ROLE_MEDICAL_STAFF;
                } else if (specIdx == 2) {
                    specialty = Constants.SPECIALTY_SECURITY;
                    subRole = Constants.SUB_ROLE_SECURITY_GUARD;
                } else {
                    specialty = Constants.SPECIALTY_TECHNICAL;
                    subRole = Constants.SUB_ROLE_TECH_STAFF;
                }
            }

            String header = String.format(
                "ADMIN_CREATE_USER|username=%s|fullName=%s|phone=%s|role=%s|subRole=%s|specialty=%s",
                u, n, ph, role, subRole, specialty
            );
            byte[] attach = p.getBytes(StandardCharsets.UTF_8);
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_ADMIN_CREATE_USER, header, attach));

            lblPersonnelFormMsg.setText("Đang gửi yêu cầu tạo tài khoản...");
            lblPersonnelFormMsg.setTextFill(Color.web("#1E5AA8"));
        });

        GridPane formGrid = new GridPane();
        formGrid.setHgap(8);
        formGrid.setVgap(10);

        formGrid.add(new Label("Tên đăng nhập: *"), 0, 0);
        formGrid.add(txtNewStaffUname, 1, 0);
        formGrid.add(new Label("Mật khẩu: *"), 0, 1);
        formGrid.add(txtNewStaffPass, 1, 1);
        formGrid.add(new Label("Họ và tên: *"), 0, 2);
        formGrid.add(txtNewStaffName, 1, 2);
        formGrid.add(new Label("Số điện thoại:"), 0, 3);
        formGrid.add(txtNewStaffPhone, 1, 3);
        formGrid.add(new Label("Vai trò: *"), 0, 4);
        formGrid.add(cbRole, 1, 4);
        formGrid.add(new Label("Chuyên môn:"), 0, 5);
        formGrid.add(cbSpecialty, 1, 5);

        ColumnConstraints cc1 = new ColumnConstraints();
        cc1.setPrefWidth(120);
        ColumnConstraints cc2 = new ColumnConstraints();
        cc2.setHgrow(Priority.ALWAYS);
        formGrid.getColumnConstraints().addAll(cc1, cc2);

        formCard.getChildren().addAll(lblFormTitle, lblFormDesc, formGrid, btnCreate, lblPersonnelFormMsg);

        // --- BÊN PHẢI: BẢNG DANH SÁCH NHÂN SỰ ---
        VBox tableBox = new VBox(10);
        tableBox.setPadding(new Insets(16));
        tableBox.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #D9DEE5; -fx-border-radius: 8px;");

        HBox tableHeader = new HBox(12);
        tableHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblListTitle = new Label("📋 DANH SÁCH NHÂN SỰ TOÀN TRƯỜNG");
        lblListTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        lblListTitle.setTextFill(Color.web("#2C3E50"));

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnReloadStaff = new Button("🔄 Làm mới danh sách");
        btnReloadStaff.setStyle("-fx-background-color: #ECEFF1; -fx-text-fill: #37474F; -fx-font-weight: bold; -fx-padding: 6 12;");
        btnReloadStaff.setOnAction(e -> loadStaffList());

        tableHeader.getChildren().addAll(lblListTitle, sp, btnReloadStaff);

        TableView<User> personnelTable = new TableView<>();
        VBox.setVgrow(personnelTable, Priority.ALWAYS);

        TableColumn<User, String> colUname = new TableColumn<>("Tài khoản");
        colUname.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername()));
        colUname.setPrefWidth(120);
        colUname.setSortable(true);

        TableColumn<User, String> colName = new TableColumn<>("Họ và tên");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFullName()));
        colName.setPrefWidth(150);
        colName.setSortable(true);

        TableColumn<User, String> colSpec = new TableColumn<>("Chuyên môn");
        colSpec.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSpecialty()));
        colSpec.setPrefWidth(110);
        colSpec.setSortable(true);

        TableColumn<User, String> colSubRole = new TableColumn<>("Chức danh");
        colSubRole.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSubRole()));
        colSubRole.setPrefWidth(120);
        colSubRole.setSortable(true);

        TableColumn<User, String> colPhone = new TableColumn<>("Số ĐT");
        colPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPhone() != null ? d.getValue().getPhone() : ""));
        colPhone.setPrefWidth(100);
        colPhone.setSortable(true);

        TableColumn<User, String> colOnline = new TableColumn<>("Kết nối");
        colOnline.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getIsOnline() == 1 ? "🟢 Online" : "⚫ Offline"));
        colOnline.setPrefWidth(85);
        colOnline.setSortable(true);
        colOnline.setComparator((s1, s2) -> {
            boolean o1 = s1 != null && s1.contains("Online");
            boolean o2 = s2 != null && s2.contains("Online");
            return Boolean.compare(o1, o2);
        });

        TableColumn<User, String> colAvail = new TableColumn<>("Khả dụng");
        colAvail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAvailabilityStatus()));
        colAvail.setPrefWidth(95);
        colAvail.setSortable(true);

        TableColumn<User, Number> colWorkload = new TableColumn<>("Đang xử lý");
        colWorkload.setCellValueFactory(d -> new javafx.beans.property.SimpleIntegerProperty(d.getValue().getCurrentWorkload()));
        colWorkload.setPrefWidth(85);
        colWorkload.setSortable(true);
        colWorkload.setComparator(Comparator.comparingInt(Number::intValue));

        personnelTable.getColumns().addAll(colUname, colName, colSpec, colSubRole, colPhone, colOnline, colAvail, colWorkload);

        SortedList<User> sortedStaff = new SortedList<>(staffList);
        sortedStaff.comparatorProperty().bind(personnelTable.comparatorProperty());
        personnelTable.setItems(sortedStaff);
        personnelTable.getSortOrder().add(colOnline);
        colOnline.setSortType(TableColumn.SortType.DESCENDING);

        tableBox.getChildren().addAll(tableHeader, personnelTable);

        splitPane.getItems().addAll(formCard, tableBox);

        VBox wrap = new VBox(splitPane);
        wrap.setPadding(new Insets(15));
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        return wrap;
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

    private int getStatusRank(String status) {
        if (status == null) return 99;
        switch (status) {
            case Constants.STATUS_PENDING: return 1;
            case Constants.STATUS_ASSIGNED: return 2;
            case Constants.STATUS_IN_PROGRESS: return 3;
            case Constants.STATUS_RESOLVED: return 4;
            case Constants.STATUS_CANCELLED: return 5;
            default: return 10;
        }
    }

    private void initTableColumns() {
        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("Mã sự cố");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setPrefWidth(140);
        colCode.setSortable(true);

        TableColumn<IncidentTicket, String> colRoom = new TableColumn<>("Phòng");
        colRoom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRoomLocation()));
        colRoom.setPrefWidth(90);
        colRoom.setSortable(true);

        TableColumn<IncidentTicket, String> colCat = new TableColumn<>("Loại sự cố");
        colCat.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getCategoryName() != null ? d.getValue().getCategoryName() : d.getValue().getCategoryCode()));
        colCat.setPrefWidth(160);
        colCat.setSortable(true);

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
        colSev.setSortable(true);
        colSev.setComparator(Comparator.comparingInt(Number::intValue));

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
        colStatus.setSortable(true);
        colStatus.setComparator((s1, s2) -> Integer.compare(getStatusRank(s1), getStatusRank(s2)));

        TableColumn<IncidentTicket, String> colStaff = new TableColumn<>("Nhân sự phụ trách");
        colStaff.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getAssignedStaffName() != null ? d.getValue().getAssignedStaffName() : "Chưa giao"
        ));
        colStaff.setPrefWidth(150);
        colStaff.setSortable(true);

        TableColumn<IncidentTicket, Number> colCount = new TableColumn<>("Số người báo");
        colCount.setCellValueFactory(d -> new javafx.beans.property.SimpleIntegerProperty(d.getValue().getReportCount()));
        colCount.setPrefWidth(100);
        colCount.setSortable(true);
        colCount.setComparator(Comparator.comparingInt(Number::intValue));

        TableColumn<IncidentTicket, String> colTime = new TableColumn<>("Thời gian tạo");
        colTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCreatedAt()));
        colTime.setPrefWidth(150);
        colTime.setSortable(true);

        TableColumn<IncidentTicket, Void> colAssignAction = new TableColumn<>("Giao việc / Phân công");
        colAssignAction.setSortable(false);
        colAssignAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnAssign = new Button("🎯 Giao việc");
            private final Button btnReassign = new Button("🔄 Đổi người");
            private final Label lblResolved = new Label("✓ Đã xong");
            {
                btnAssign.setStyle("-fx-background-color: #1E5AA8; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-cursor: hand;");
                btnAssign.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) openAssignDialog(t);
                });

                btnReassign.setStyle("-fx-background-color: #E67E22; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-cursor: hand;");
                btnReassign.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) openAssignDialog(t);
                });

                lblResolved.setStyle("-fx-text-fill: #2E9E5B; -fx-font-weight: bold; -fx-font-size: 11px;");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        if (Constants.STATUS_PENDING.equals(t.getStatus())) {
                            setGraphic(btnAssign);
                        } else if (Constants.STATUS_ASSIGNED.equals(t.getStatus())) {
                            setGraphic(btnReassign);
                        } else {
                            setGraphic(lblResolved);
                        }
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
        colAssignAction.setPrefWidth(120);

        TableColumn<IncidentTicket, Void> colChatAction = new TableColumn<>("Trao đổi");
        colChatAction.setSortable(false);
        colChatAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnChat = new Button("💬 Chat");
            {
                btnChat.setStyle("-fx-background-color: #0D9488; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-cursor: hand;");
                btnChat.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        chatPanel.selectTicket(t.getTicketId(), t.getTicketCode() + " - Phòng " + t.getRoomLocation());
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
        colChatAction.setPrefWidth(85);

        allTicketsTable.getColumns().addAll(colCode, colRoom, colCat, colSev, colStatus, colStaff, colCount, colTime, colAssignAction, colChatAction);
        allTicketsTable.getSortOrder().add(colTime);
        colTime.setSortType(TableColumn.SortType.DESCENDING);
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

    private void loadStaffList() {
        SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_GET_STAFF_LIST, "GET_STAFF_LIST"));
    }

    private void openAssignDialog(IncidentTicket ticket) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("🎯 GIAO NHIỆM VỤ / ĐIỀU PHỐI SỰ CỐ");

        VBox root = new VBox(14);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: white;");

        // Header
        Label lblHeader = new Label("🎯 PHÂN CÔNG NHÂN SỰ XỬ LÝ SỰ CỐ");
        lblHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lblHeader.setTextFill(Color.web("#1E5AA8"));

        // Thông tin tóm tắt sự cố
        VBox infoBox = new VBox(6);
        infoBox.setStyle("-fx-background-color: #F4F6F9; -fx-padding: 12px; -fx-background-radius: 6px; -fx-border-color: #E2E8F0; -fx-border-radius: 6px;");

        Label lblCode = new Label("Mã sự cố: " + ticket.getTicketCode() + "   |   Phòng: " + ticket.getRoomLocation());
        lblCode.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        Label lblCategory = new Label("Loại sự cố: " + (ticket.getCategoryName() != null ? ticket.getCategoryName() : ticket.getCategoryCode())
            + "   |   Mức độ: " + ticket.getBaseSeverity() + "/5");

        Label lblCurrent = new Label("Hiện tại phụ trách: " + (ticket.getAssignedStaffName() != null ? ticket.getAssignedStaffName() : "Chưa giao"));
        lblCurrent.setStyle("-fx-font-style: italic; -fx-text-fill: #555;");

        infoBox.getChildren().addAll(lblCode, lblCategory, lblCurrent);

        // Chọn nhân viên
        Label lblSelect = new Label("Chọn nhân sự kỹ thuật / y tế / bảo vệ:");
        lblSelect.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        ComboBox<User> cbStaff = new ComboBox<>(staffList);
        cbStaff.setMaxWidth(Double.MAX_VALUE);
        cbStaff.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String statusStr = item.getIsOnline() == 1 ? "🟢 Online" : "⚪ Offline";
                    setText(String.format("[%s] %s  -  Tải: %d việc  (%s)",
                        item.getSpecialty(), item.getFullName(), item.getCurrentWorkload(), statusStr));
                }
            }
        });
        cbStaff.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String statusStr = item.getIsOnline() == 1 ? "🟢 Online" : "⚪ Offline";
                    setText(String.format("[%s] %s  (Tải: %d) - %s",
                        item.getSpecialty(), item.getFullName(), item.getCurrentWorkload(), statusStr));
                }
            }
        });

        // Tự động chọn nhân viên có chuyên môn phù hợp nhất
        for (User u : staffList) {
            if (u.getUserId() != (ticket.getAssignedStaffId() != null ? ticket.getAssignedStaffId() : 0)) {
                cbStaff.setValue(u);
                break;
            }
        }

        // Ghi chú điều phối
        Label lblNote = new Label("Yêu cầu chỉ đạo / Ghi chú từ Ban Giám Hiệu:");
        lblNote.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        TextField txtNote = new TextField("Ban Giám hiệu chỉ định xử lý an toàn ngay trong ca trực.");
        txtNote.setPromptText("Nhập yêu cầu chỉ đạo...");

        // Buttons
        HBox btnBox = new HBox(12);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnCancel = new Button("HỦY BỎ");
        btnCancel.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #333; -fx-font-weight: bold; -fx-padding: 8 16; -fx-cursor: hand;");
        btnCancel.setOnAction(e -> stage.close());

        Button btnConfirm = new Button("XÁC NHẬN GIAO VIỆC");
        btnConfirm.setStyle("-fx-background-color: #1E5AA8; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20; -fx-cursor: hand;");
        btnConfirm.setOnAction(e -> {
            User selected = cbStaff.getValue();
            if (selected == null) {
                new Alert(Alert.AlertType.WARNING, "Vui lòng chọn nhân viên tiếp nhận!").show();
                return;
            }
            String note = txtNote.getText().trim();
            String payload = String.format("MANUAL_ASSIGN|ticketId=%d|staffId=%d|note=%s",
                ticket.getTicketId(), selected.getUserId(), note.replace("|", "/"));
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_MANUAL_ASSIGN, payload));
            stage.close();
        });

        btnBox.getChildren().addAll(btnCancel, btnConfirm);

        root.getChildren().addAll(lblHeader, infoBox, lblSelect, cbStaff, lblNote, txtNote, btnBox);

        Scene scene = new Scene(root, 520, 370);
        stage.setScene(scene);
        stage.show();
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
                if (chatPanel != null) {
                    chatPanel.setTickets(list);
                }
            } catch (Exception ignored) {}

        } else if (packet.getType() == Constants.TYPE_GET_STAFF_LIST_RESP) {
            try {
                Type listType = new TypeToken<ArrayList<User>>(){}.getType();
                List<User> list = gson.fromJson(packet.getHeaderPayload(), listType);
                staffList.setAll(list);
            } catch (Exception ignored) {}

        } else if (packet.getType() == Constants.TYPE_ADMIN_CREATE_RESP) {
            Map<String, String> p = packet.parseParams();
            String status = p.get("status");
            String msg = p.getOrDefault("message", "");
            if ("OK".equalsIgnoreCase(status)) {
                lblPersonnelFormMsg.setText("✅ " + msg);
                lblPersonnelFormMsg.setTextFill(Color.web("#2E7D32"));
                if (txtNewStaffUname != null) txtNewStaffUname.clear();
                if (txtNewStaffPass != null) txtNewStaffPass.clear();
                if (txtNewStaffName != null) txtNewStaffName.clear();
                if (txtNewStaffPhone != null) txtNewStaffPhone.clear();
                new Alert(Alert.AlertType.INFORMATION, "✅ " + msg).show();
                loadStaffList();
            } else {
                lblPersonnelFormMsg.setText("❌ " + msg);
                lblPersonnelFormMsg.setTextFill(Color.web("#D32F2F"));
                new Alert(Alert.AlertType.ERROR, "❌ " + msg).show();
            }

        } else if (packet.getType() == Constants.TYPE_MANUAL_ASSIGN_RESP) {
            Map<String, String> p = packet.parseParams();
            String staffName = p.getOrDefault("staffName", "nhân sự");
            new Alert(Alert.AlertType.INFORMATION, "✅ Đã giao nhiệm vụ thành công cho " + staffName + "!").show();
            loadAllTickets();
            loadStaffList();

        } else if (packet.getType() == Constants.TYPE_STATUS_UPDATE) {
            loadAllTickets();
            loadStaffList();

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

