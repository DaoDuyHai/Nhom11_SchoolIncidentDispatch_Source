package com.incidentreport.client.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.incidentreport.client.IncidentReportApp;
import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.*;
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
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phân hệ Quản trị & Điều hành Trung tâm (Admin == Server):
 * 1. Thống kê đa kỳ (Ngày, Tháng, Quý, Năm, Tất cả) & Xuất báo cáo CSV chuẩn Excel.
 * 2. Giám sát toàn bộ sự cố trường học & Phân công cán bộ trực tiếp.
 * 3. Quyền độc quyền khởi tạo và cấp phát tài khoản toàn trường.
 * 4. Kiểm toán toàn bộ tin nhắn trao đổi 1-1 giữa người báo và người xử lý.
 */
public class AdminView extends BorderPane implements SocketClientManager.PacketListener {

    private final User currentUser;
    private final Gson gson = new Gson();

    // Tab 1: Statistics
    private final ComboBox<String> cbTimeRange = new ComboBox<>();
    private final Label lblTotal = new Label("0");
    private final Label lblPending = new Label("0");
    private final Label lblInProgress = new Label("0");
    private final Label lblResolved = new Label("0");
    private final VBox deptStatsContainer = new VBox(6);
    private final VBox topRoomsContainer = new VBox(6);
    private StatisticReport currentStatsReport = null;

    // Tab 2: All Tickets
    private final TableView<IncidentTicket> allTicketsTable = new TableView<>();
    private final ObservableList<IncidentTicket> allTicketsList = FXCollections.observableArrayList();
    private final ComboBox<String> cbStatusFilter = new ComboBox<>();

    // Tab 3: User Management
    private final TableView<User> userTable = new TableView<>();
    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private final TextField txtNewUsername = new TextField();
    private final PasswordField txtNewPassword = new PasswordField();
    private final TextField txtNewFullName = new TextField();
    private final TextField txtNewPhone = new TextField();
    private final ComboBox<String> cbNewRole = new ComboBox<>();
    private final ComboBox<String> cbNewSpecialty = new ComboBox<>();
    private final Label lblUserFormMsg = new Label();

    // Tab 4: Chat Audit
    private ChatPanel auditChatPanel;

    private final TabPane tabPane = new TabPane();
    private Tab tabStats;
    private Tab tabTickets;
    private Tab tabUsers;
    private Tab tabChatAudit;

    public AdminView(User user) {
        this.currentUser = user;
        initUI();
        SocketClientManager.getInstance().addListener(this);

        loadStatistics("ALL");
        loadAllTickets();
        loadAllUsers();
    }

    private void initUI() {
        // Top Header
        HBox topBar = new HBox(15);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 20, 12, 20));
        topBar.setStyle("-fx-background-color: #0F172A;");

        Label lblTitle = new Label("BẢNG ĐIỀU HÀNH & QUẢN TRỊ TRUNG TÂM (SERVER / ADMIN)");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lblTitle.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox connBox = ComponentFactory.createConnectionIndicator(true);
        Label lblUser = new Label("👑 " + currentUser.getFullName() + " [ADMIN]");
        lblUser.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lblUser.setTextFill(Color.WHITE);

        Button btnLogout = new Button("Đăng xuất");
        btnLogout.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-padding: 5 12; -fx-background-radius: 4;");
        btnLogout.setOnAction(e -> IncidentReportApp.logout());

        topBar.getChildren().addAll(lblTitle, spacer, connBox, lblUser, btnLogout);
        setTop(topBar);

        // Center Content - TabPane
        tabStats = new Tab("① 📊 Thống kê & Xuất báo cáo", createStatisticsTab());
        tabStats.setClosable(false);

        tabTickets = new Tab("② 📋 Giám sát & Phân công sự cố", createIncidentOversightTab());
        tabTickets.setClosable(false);

        tabUsers = new Tab("③ 👥 Quản lý & Cấp tài khoản", createUserManagementTab());
        tabUsers.setClosable(false);

        auditChatPanel = new ChatPanel(currentUser);
        tabChatAudit = new Tab("④ 💬 Kiểm toán tin nhắn 1-1", auditChatPanel);
        tabChatAudit.setClosable(false);

        tabPane.getTabs().addAll(tabStats, tabTickets, tabUsers, tabChatAudit);
        setCenter(tabPane);
    }

    // =========================================================================
    // TAB 1: THỐNG KÊ ĐA CHIỀU & XUẤT CSV
    // =========================================================================
    private Node createStatisticsTab() {
        VBox layout = new VBox(14);
        layout.setPadding(new Insets(16));
        layout.setStyle("-fx-background-color: #F8FAFC;");

        // Top Controls: Time filter & Export CSV
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(12, 16, 12, 16));
        filterBar.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #E2E8F0; -fx-border-radius: 8px;");

        Label lblFilter = new Label("Kỳ thống kê:");
        lblFilter.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        cbTimeRange.getItems().addAll("Toàn thời gian (ALL)", "Hôm nay (DAY)", "Tháng này (MONTH)", "Quý này (QUARTER)", "Năm nay (YEAR)");
        cbTimeRange.getSelectionModel().select(0);
        cbTimeRange.setOnAction(e -> {
            String sel = cbTimeRange.getValue();
            String range = "ALL";
            if (sel.contains("DAY")) range = "DAY";
            else if (sel.contains("MONTH")) range = "MONTH";
            else if (sel.contains("QUARTER")) range = "QUARTER";
            else if (sel.contains("YEAR")) range = "YEAR";
            loadStatistics(range);
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnExportCSV = new Button("📥 XUẤT BÁO CÁO CSV");
        btnExportCSV.setStyle("-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 6; -fx-cursor: hand;");
        btnExportCSV.setOnAction(e -> exportStatisticsToCSV());

        filterBar.getChildren().addAll(lblFilter, cbTimeRange, sp, btnExportCSV);

        // Metric Cards
        HBox kpiBox = new HBox(12);
        kpiBox.getChildren().addAll(
                createKpiCard("TỔNG SỰ CỐ", lblTotal, "#2563EB"),
                createKpiCard("CHỜ TIẾP NHẬN", lblPending, "#EF4444"),
                createKpiCard("ĐANG XỬ LÝ", lblInProgress, "#F59E0B"),
                createKpiCard("ĐÃ HOÀN TẤT", lblResolved, "#10B981")
        );

        // Details split: Dept breakdown and Top rooms
        HBox detailsBox = new HBox(14);
        VBox deptCard = new VBox(10);
        deptCard.setPadding(new Insets(14));
        deptCard.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #E2E8F0; -fx-border-radius: 8px;");
        HBox.setHgrow(deptCard, Priority.ALWAYS);

        Label lblDeptTitle = new Label("🏢 PHÂN BỔ SỰ CỐ THEO PHÒNG BAN");
        lblDeptTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lblDeptTitle.setTextFill(Color.web("#0F172A"));
        deptCard.getChildren().addAll(lblDeptTitle, deptStatsContainer);

        VBox roomCard = new VBox(10);
        roomCard.setPadding(new Insets(14));
        roomCard.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #E2E8F0; -fx-border-radius: 8px;");
        HBox.setHgrow(roomCard, Priority.ALWAYS);

        Label lblRoomTitle = new Label("📍 TOP PHÒNG HỌC / VỊ TRÍ XẢY RA SỰ CỐ NHIỀU NHẤT");
        lblRoomTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lblRoomTitle.setTextFill(Color.web("#0F172A"));
        roomCard.getChildren().addAll(lblRoomTitle, topRoomsContainer);

        detailsBox.getChildren().addAll(deptCard, roomCard);

        layout.getChildren().addAll(filterBar, kpiBox, detailsBox);
        return new ScrollPane(layout);
    }

    private Node createKpiCard(String title, Label valueLabel, String colorHex) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setStyle(String.format("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #E2E8F0; -fx-border-radius: 8px; -fx-border-top-color: %s; -fx-border-top-width: 4px;", colorHex));
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lblTitle = new Label(title);
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        lblTitle.setTextFill(Color.web("#64748B"));

        valueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        valueLabel.setTextFill(Color.web(colorHex));

        card.getChildren().addAll(lblTitle, valueLabel);
        return card;
    }

    private void renderStatistics(StatisticReport report) {
        this.currentStatsReport = report;
        lblTotal.setText(String.valueOf(report.getTotalTickets()));
        lblPending.setText(String.valueOf(report.getPendingCount()));
        lblInProgress.setText(String.valueOf(report.getInProgressCount()));
        lblResolved.setText(String.valueOf(report.getResolvedCount()));

        deptStatsContainer.getChildren().clear();
        if (report.getCountByDepartment() != null && !report.getCountByDepartment().isEmpty()) {
            for (Map.Entry<String, Integer> entry : report.getCountByDepartment().entrySet()) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                Label badge = ComponentFactory.createDepartmentBadge(entry.getKey());
                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);
                Label lblCnt = new Label(entry.getValue() + " sự cố");
                lblCnt.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                row.getChildren().addAll(badge, sp, lblCnt);
                deptStatsContainer.getChildren().add(row);
            }
        } else {
            deptStatsContainer.getChildren().add(new Label("Chưa có dữ liệu phát sinh trong kỳ"));
        }

        topRoomsContainer.getChildren().clear();
        if (report.getTopIncidentRooms() != null && !report.getTopIncidentRooms().isEmpty()) {
            int rank = 1;
            for (Map<String, Object> r : report.getTopIncidentRooms()) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                Label lblRank = new Label("#" + rank);
                lblRank.setStyle("-fx-background-color: #E2E8F0; -fx-padding: 2 6; -fx-background-radius: 4; -fx-font-weight: bold;");
                Label lblRoom = new Label(String.valueOf(r.get("room")));
                lblRoom.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);
                Label lblCnt = new Label(String.valueOf(r.get("count")) + " lần");
                lblCnt.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
                row.getChildren().addAll(lblRank, lblRoom, sp, lblCnt);
                topRoomsContainer.getChildren().add(row);
                rank++;
            }
        } else {
            topRoomsContainer.getChildren().add(new Label("Chưa có địa điểm phát sinh sự cố"));
        }
    }

    private void exportStatisticsToCSV() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Lưu Báo Cáo Sự Cố (CSV)");
        fc.setInitialFileName("Bao_Cao_Su_Co_" + System.currentTimeMillis() + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Comma Separated (*.csv)", "*.csv"));
        File file = fc.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            // Write UTF-8 BOM for Microsoft Excel compatibility
            pw.write('\ufeff');

            pw.println("BÁO CÁO THỐNG KÊ SỰ CỐ TRƯỜNG THPT");
            pw.printf("Kỳ báo cáo: %s\n", cbTimeRange.getValue());
            pw.printf("Tổng sự cố: %s, Chờ xử lý: %s, Đang xử lý: %s, Đã hoàn tất: %s\n\n",
                    lblTotal.getText(), lblPending.getText(), lblInProgress.getText(), lblResolved.getText());

            pw.println("Mã sự cố,Vị trí phòng,Loại sự cố,Phòng ban điều phối,Trạng thái,Người báo cáo,SĐT người báo,Cán bộ xử lý,Thời gian báo,Thời gian giải quyết,Ghi chú");

            for (IncidentTicket t : allTicketsList) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        cleanCsv(t.getTicketCode()),
                        cleanCsv(t.getRoomLocation()),
                        cleanCsv(t.getCategoryName() != null ? t.getCategoryName() : t.getCategoryCode()),
                        cleanCsv(t.getTargetDepartment()),
                        cleanCsv(t.getStatus()),
                        cleanCsv(t.getReporterName()),
                        cleanCsv(t.getReporterPhone()),
                        cleanCsv(t.getAssignedStaffName() != null ? t.getAssignedStaffName() : "Chưa tiếp nhận"),
                        cleanCsv(t.getCreatedAt()),
                        cleanCsv(t.getResolvedAt() != null ? t.getResolvedAt() : ""),
                        cleanCsv(t.getResolutionNote() != null ? t.getResolutionNote() : "")
                );
            }
            pw.flush();
            new Alert(Alert.AlertType.INFORMATION, "🎉 Đã xuất file CSV báo cáo thành công tại: " + file.getAbsolutePath()).show();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Lỗi khi xuất file: " + ex.getMessage()).show();
        }
    }

    private String cleanCsv(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }

    // =========================================================================
    // TAB 2: GIÁM SÁT TOÀN DIỆN & PHÂN CÔNG SỰ CỐ
    // =========================================================================
    private Node createIncidentOversightTab() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(15));
        layout.setStyle("-fx-background-color: white;");

        HBox toolBar = new HBox(12);
        toolBar.setAlignment(Pos.CENTER_LEFT);

        Label lblFilter = new Label("Lọc trạng thái:");
        lblFilter.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        cbStatusFilter.getItems().addAll("Tất cả", "Chờ tiếp nhận (PENDING)", "Đang xử lý (IN_PROGRESS)", "Đã hoàn tất (RESOLVED)");
        cbStatusFilter.getSelectionModel().select(0);
        cbStatusFilter.setOnAction(e -> filterTickets());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRefresh = new Button("🔄 Làm mới");
        btnRefresh.setOnAction(e -> loadAllTickets());

        toolBar.getChildren().addAll(lblFilter, cbStatusFilter, sp, btnRefresh);

        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("Mã sự cố");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setPrefWidth(120);

        TableColumn<IncidentTicket, String> colRoom = new TableColumn<>("Vị trí");
        colRoom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRoomLocation()));
        colRoom.setPrefWidth(90);

        TableColumn<IncidentTicket, String> colCat = new TableColumn<>("Loại sự cố");
        colCat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategoryName() != null ? d.getValue().getCategoryName() : d.getValue().getCategoryCode()));
        colCat.setPrefWidth(160);

        TableColumn<IncidentTicket, String> colDept = new TableColumn<>("Phòng ban");
        colDept.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTargetDepartment()));
        colDept.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setGraphic(null);
                else setGraphic(ComponentFactory.createDepartmentBadge(item));
            }
        });
        colDept.setPrefWidth(110);

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
        colReporter.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getReporterName()));
        colReporter.setPrefWidth(160);

        TableColumn<IncidentTicket, String> colStaff = new TableColumn<>("Cán bộ xử lý");
        colStaff.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getAssignedStaffName() != null ? d.getValue().getAssignedStaffName() : "Chưa tiếp nhận"
        ));
        colStaff.setPrefWidth(160);

        TableColumn<IncidentTicket, Void> colActions = new TableColumn<>("Hành động Admin");
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnAssign = new Button("👉 Chỉ định");
            private final Button btnChat = new Button("💬 Xem Chat");
            private final HBox btnBox = new HBox(6, btnAssign, btnChat);
            {
                btnAssign.setStyle("-fx-background-color: #F59E0B; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
                btnChat.setStyle("-fx-background-color: #0284C7; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");

                btnAssign.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) openManualAssignDialog(t);
                });

                btnChat.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        auditChatPanel.openTicketChat(t);
                        tabPane.getSelectionModel().select(tabChatAudit);
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else setGraphic(btnBox);
            }
        });
        colActions.setPrefWidth(190);

        allTicketsTable.getColumns().addAll(colCode, colRoom, colCat, colDept, colStatus, colReporter, colStaff, colActions);
        allTicketsTable.setItems(allTicketsList);
        VBox.setVgrow(allTicketsTable, Priority.ALWAYS);

        layout.getChildren().addAll(toolBar, allTicketsTable);
        return layout;
    }

    private void filterTickets() {
        // Simple reload
        loadAllTickets();
    }

    private void openManualAssignDialog(IncidentTicket ticket) {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Chỉ định Cán bộ Xử lý Trực tiếp");
        dialog.setHeaderText(String.format("Phân công sự cố %s (%s) cho cán bộ phòng ban:",
                ticket.getTicketCode(), ticket.getTargetDepartment()));

        ButtonType btnOk = new ButtonType("Xác nhận chỉ định", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnOk, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        ComboBox<User> cbStaff = new ComboBox<>();
        // Lấy danh sách responders cùng department
        for (User u : userList) {
            if (Constants.ROLE_RESPONDER.equalsIgnoreCase(u.getRole())) {
                if (ticket.getTargetDepartment() == null || "ALL".equalsIgnoreCase(ticket.getTargetDepartment())
                        || ticket.getTargetDepartment().equalsIgnoreCase(u.getSpecialty()) || "ALL".equalsIgnoreCase(u.getSpecialty())) {
                    cbStaff.getItems().add(u);
                }
            }
        }
        if (!cbStaff.getItems().isEmpty()) {
            cbStaff.getSelectionModel().selectFirst();
        }

        TextField txtNote = new TextField("Admin BGH chỉ định xử lý ngay");

        content.getChildren().addAll(new Label("Chọn cán bộ tiếp nhận:"), cbStaff, new Label("Ghi chú chỉ định:"), txtNote);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(b -> (b == btnOk) ? cbStaff.getValue() : null);
        Optional<User> result = dialog.showAndWait();
        result.ifPresent(staff -> {
            Packet p = new Packet(Constants.TYPE_MANUAL_ASSIGN,
                    String.format("MANUAL_ASSIGN|ticketId=%d|staffId=%d|note=%s",
                            ticket.getTicketId(), staff.getUserId(), txtNote.getText().replace("|", "/")));
            SocketClientManager.getInstance().sendPacket(p);
        });
    }

    // =========================================================================
    // TAB 3: QUẢN LÝ & CẤP TÀI KHOẢN DUY NHẤT BỞI ADMIN
    // =========================================================================
    private Node createUserManagementTab() {
        HBox layout = new HBox(16);
        layout.setPadding(new Insets(16));
        layout.setStyle("-fx-background-color: #F8FAFC;");

        // Form bên trái: Cấp tài khoản mới
        VBox formCard = new VBox(12);
        formCard.setPrefWidth(360);
        formCard.setPadding(new Insets(16));
        formCard.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #E2E8F0; -fx-border-radius: 8px;");

        Label lblFormTitle = new Label("➕ CẤP TÀI KHOẢN MỚI");
        lblFormTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        lblFormTitle.setTextFill(Color.web("#0F172A"));

        Label lblFormSub = new Label("Chỉ Admin mới có quyền tạo và cấp tài khoản cho người dùng.");
        lblFormSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        txtNewUsername.setPromptText("Tên đăng nhập (VD: kt_hung)");
        txtNewPassword.setPromptText("Mật khẩu ban đầu");
        txtNewPassword.setText("123456");
        txtNewFullName.setPromptText("Họ và tên đầy đủ");
        txtNewPhone.setPromptText("Số điện thoại liên hệ");

        cbNewRole.getItems().addAll(Constants.ROLE_REPORTER, Constants.ROLE_RESPONDER, Constants.ROLE_ADMIN);
        cbNewRole.getSelectionModel().select(Constants.ROLE_RESPONDER);
        cbNewRole.setMaxWidth(Double.MAX_VALUE);

        cbNewSpecialty.getItems().addAll(
                Constants.SPECIALTY_TECHNICAL,
                Constants.SPECIALTY_MEDICAL,
                Constants.SPECIALTY_SECURITY,
                Constants.SPECIALTY_NONE,
                Constants.SPECIALTY_ALL
        );
        cbNewSpecialty.getSelectionModel().select(Constants.SPECIALTY_TECHNICAL);
        cbNewSpecialty.setMaxWidth(Double.MAX_VALUE);

        cbNewRole.setOnAction(e -> {
            boolean isResp = Constants.ROLE_RESPONDER.equalsIgnoreCase(cbNewRole.getValue());
            cbNewSpecialty.setDisable(!isResp);
        });

        Button btnCreate = new Button("CẤP TÀI KHOẢN");
        btnCreate.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 9 18; -fx-background-radius: 6; -fx-cursor: hand;");
        btnCreate.setMaxWidth(Double.MAX_VALUE);
        btnCreate.setOnAction(e -> doCreateUser());

        lblUserFormMsg.setStyle("-fx-font-size: 12px;");
        lblUserFormMsg.setVisible(false);

        formCard.getChildren().addAll(lblFormTitle, lblFormSub,
                new Label("Tên đăng nhập:"), txtNewUsername,
                new Label("Mật khẩu:"), txtNewPassword,
                new Label("Họ và tên:"), txtNewFullName,
                new Label("Số điện thoại:"), txtNewPhone,
                new Label("Vai trò (Role):"), cbNewRole,
                new Label("Phòng ban chuyên trách:"), cbNewSpecialty,
                btnCreate, lblUserFormMsg);

        // Bảng danh sách tài khoản bên phải
        VBox tableCard = new VBox(10);
        tableCard.setPadding(new Insets(16));
        tableCard.setStyle("-fx-background-color: white; -fx-background-radius: 8px; -fx-border-color: #E2E8F0; -fx-border-radius: 8px;");
        HBox.setHgrow(tableCard, Priority.ALWAYS);

        HBox tableTop = new HBox(12);
        tableTop.setAlignment(Pos.CENTER_LEFT);
        Label lblUsersTitle = new Label("DANH SÁCH TÀI KHOẢN TRÊN HỆ THỐNG");
        lblUsersTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRefreshUsers = new Button("🔄 Làm mới");
        btnRefreshUsers.setOnAction(e -> loadAllUsers());
        tableTop.getChildren().addAll(lblUsersTitle, sp, btnRefreshUsers);

        TableColumn<User, String> colUname = new TableColumn<>("Username");
        colUname.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername()));
        colUname.setPrefWidth(120);

        TableColumn<User, String> colName = new TableColumn<>("Họ và tên");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFullName()));
        colName.setPrefWidth(180);

        TableColumn<User, String> colRole = new TableColumn<>("Vai trò");
        colRole.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRole()));
        colRole.setPrefWidth(120);

        TableColumn<User, String> colSpec = new TableColumn<>("Phòng ban");
        colSpec.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSpecialty()));
        colSpec.setPrefWidth(130);

        TableColumn<User, String> colPhone = new TableColumn<>("Số ĐT");
        colPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPhone() != null ? d.getValue().getPhone() : ""));
        colPhone.setPrefWidth(110);

        TableColumn<User, String> colOnline = new TableColumn<>("Trạng thái");
        colOnline.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getIsOnline() == 1 ? "🟢 Đang Online" : "⚪ Ngoại tuyến"));
        colOnline.setPrefWidth(120);

        userTable.getColumns().addAll(colUname, colName, colRole, colSpec, colPhone, colOnline);
        userTable.setItems(userList);
        VBox.setVgrow(userTable, Priority.ALWAYS);

        tableCard.getChildren().addAll(tableTop, userTable);

        layout.getChildren().addAll(formCard, tableCard);
        return layout;
    }

    private void doCreateUser() {
        String u = txtNewUsername.getText().trim();
        String p = txtNewPassword.getText().trim();
        String n = txtNewFullName.getText().trim();
        String ph = txtNewPhone.getText().trim();
        String r = cbNewRole.getValue();
        String s = cbNewSpecialty.getValue();

        if (u.isEmpty() || p.isEmpty() || n.isEmpty()) {
            lblUserFormMsg.setText("❌ Vui lòng nhập đầy đủ Username, Password và Họ tên!");
            lblUserFormMsg.setTextFill(Color.web("#DC2626"));
            lblUserFormMsg.setVisible(true);
            return;
        }

        String header = String.format("ADMIN_CREATE_USER|username=%s|fullName=%s|role=%s|specialty=%s|phone=%s",
                u, n, r, s, ph);
        Packet packet = new Packet(Constants.TYPE_ADMIN_CREATE_USER, header, p.getBytes(StandardCharsets.UTF_8));
        SocketClientManager.getInstance().sendPacket(packet);
    }

    private void loadStatistics(String timeRange) {
        SocketClientManager.getInstance().sendPacket(
                new Packet(Constants.TYPE_ADMIN_GET_STATS, "GET_STATS|timeRange=" + timeRange));
    }

    private void loadAllTickets() {
        SocketClientManager.getInstance().sendPacket(
                new Packet(Constants.TYPE_LIST_TICKETS, "LIST_TICKETS|scope=ALL"));
    }

    private void loadAllUsers() {
        SocketClientManager.getInstance().sendPacket(
                new Packet(Constants.TYPE_ADMIN_GET_USERS, "ADMIN_GET_USERS"));
    }

    @Override
    public void onPacketReceived(Packet packet) {
        if (packet.getType() == Constants.TYPE_ADMIN_GET_STATS_RESP) {
            if (packet.hasAttachment()) {
                try {
                    String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                    StatisticReport report = gson.fromJson(json, StatisticReport.class);
                    Platform.runLater(() -> renderStatistics(report));
                } catch (Exception ignored) {}
            }

        } else if (packet.getType() == Constants.TYPE_LIST_TICKETS_RESP) {
            if (packet.hasAttachment()) {
                try {
                    String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                    Type listType = new TypeToken<List<IncidentTicket>>() {}.getType();
                    List<IncidentTicket> list = gson.fromJson(json, listType);
                    allTicketsList.setAll(list);
                } catch (Exception ignored) {}
            }

        } else if (packet.getType() == Constants.TYPE_ADMIN_GET_USERS_RESP) {
            if (packet.hasAttachment()) {
                try {
                    String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                    Type listType = new TypeToken<List<User>>() {}.getType();
                    List<User> list = gson.fromJson(json, listType);
                    userList.setAll(list);
                } catch (Exception ignored) {}
            }

        } else if (packet.getType() == Constants.TYPE_ADMIN_CREATE_RESP) {
            Map<String, String> p = packet.parseParams();
            String status = p.get("status");
            if ("OK".equalsIgnoreCase(status)) {
                lblUserFormMsg.setText("✅ Đã cấp mới tài khoản: " + p.get("username"));
                lblUserFormMsg.setTextFill(Color.web("#16A34A"));
                lblUserFormMsg.setVisible(true);
                txtNewUsername.clear();
                txtNewFullName.clear();
                txtNewPhone.clear();
                loadAllUsers();
            } else {
                lblUserFormMsg.setText("❌ " + p.getOrDefault("message", "Lỗi tạo tài khoản!"));
                lblUserFormMsg.setTextFill(Color.web("#DC2626"));
                lblUserFormMsg.setVisible(true);
            }

        } else if (packet.getType() == Constants.TYPE_MANUAL_ASSIGN_RESP) {
            loadAllTickets();
            new Alert(Alert.AlertType.INFORMATION, "✅ Đã phân công sự cố thành công!").show();

        } else if (packet.getType() == Constants.TYPE_DISPATCH_NOTIFY || packet.getType() == Constants.TYPE_STATUS_BROADCAST) {
            loadAllTickets();
            String sel = cbTimeRange.getValue();
            String range = "ALL";
            if (sel != null && sel.contains("DAY")) range = "DAY";
            loadStatistics(range);
        }
    }

    @Override
    public void onDisconnected() {}
}
