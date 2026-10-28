package com.incidentreport.client.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.incidentreport.client.IncidentReportApp;
import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.StatisticReport;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phân hệ Quản trị & Điều hành Trung tâm (AdminView) tái thiết kế theo DESIGN_SYSTEM.md:
 * - Sidebar mực xanh 232px, điều khiển 4 tab ẩn: Thống kê, Giám sát, Tài khoản, Kiểm toán chat.
 * - Thẻ KPI phong cách Giấy - Mực - Đồng, phân bổ thanh bar tự dựng, huy chương top 1-3.
 * - Bộ lọc 3 tiêu chí mỏng nhẹ, hộp thoại Chỉ định cán bộ hiện avatar và số ca đang làm.
 * - Quản lý tài khoản chia nhóm rõ ràng, chấm Online/Ngoại tuyến chuẩn, không dùng emoji.
 */
public class AdminView extends StackPane implements SocketClientManager.PacketListener {

    private final User currentUser;
    private final Gson gson = new Gson();

    // Sidebar navigation
    private final ToggleGroup navGroup = new ToggleGroup();
    private final ToggleButton navStats = new ToggleButton("Thống kê");
    private final ToggleButton navOversight = new ToggleButton("Giám sát & phân công");
    private final ToggleButton navUsers = new ToggleButton("Tài khoản");
    private final ToggleButton navChatAudit = new ToggleButton("Kiểm toán chat");

    // Topbar
    private final Label lblPageTitle = new Label("Thống kê sự cố");
    private final Label lblPageSub = new Label("Tổng hợp số liệu điều phối cơ sở vật chất toàn trường.");
    private final HBox connIndicator = ComponentFactory.createConnectionIndicator(true);

    // TabPane ẩn
    private final TabPane tabPane = new TabPane();
    private ChatPanel auditChatPanel;

    // Tab 1: Statistics
    private String currentTimeRange = "ALL";
    private final ToggleGroup statSegGroup = new ToggleGroup();
    private final Label lblTotal = new Label("0");
    private final Label lblPending = new Label("0");
    private final Label lblInProgress = new Label("0");
    private final Label lblResolved = new Label("0");
    private final VBox deptBarsContainer = new VBox(10);
    private final VBox topRoomsContainer = new VBox(8);
    private StatisticReport currentStatsReport = null;

    // Tab 2: All Tickets Oversight
    private final TableView<IncidentTicket> allTicketsTable = new TableView<>();
    private final ObservableList<IncidentTicket> allTicketsList = FXCollections.observableArrayList();
    private final FilteredList<IncidentTicket> filteredTickets = new FilteredList<>(allTicketsList, t -> true);
    private final TextField txtSearchTicket = new TextField();
    private final ComboBox<String> cbStatusFilter = new ComboBox<>();
    private final ComboBox<String> cbDeptFilter = new ComboBox<>();
    private final Label lblFilterCount = new Label();

    // Tab 3: User Management
    private final TableView<User> userTable = new TableView<>();
    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private final TextField txtNewUsername = new TextField();
    private final PasswordField txtNewPassword = new PasswordField();
    private final TextField txtNewFullName = new TextField();
    private final TextField txtNewPhone = new TextField();
    private final ComboBox<String> cbNewRole = new ComboBox<>();
    private final ComboBox<String> cbNewSpecialty = new ComboBox<>();

    public AdminView(User user) {
        this.currentUser = user;
        initShell();
        SocketClientManager.getInstance().addListener(this);

        loadStatistics("ALL");
        loadAllTickets();
        loadAllUsers();
    }

    private void initShell() {
        BorderPane rootPane = new BorderPane();

        // 1. Sidebar trái (232px)
        VBox sidebar = createSidebar();
        rootPane.setLeft(sidebar);

        // 2. Nội dung trung tâm: Topbar + TabPane.hidden-tabs
        BorderPane contentPane = new BorderPane();
        HBox topbar = createTopbar();
        contentPane.setTop(topbar);

        // Khởi tạo các Tab
        Tab tab1 = new Tab("Stats", createStatisticsTab());
        Tab tab2 = new Tab("Oversight", createIncidentOversightTab());
        Tab tab3 = new Tab("Users", createUserManagementTab());
        auditChatPanel = new ChatPanel(currentUser);
        Tab tab4 = new Tab("Audit", auditChatPanel);

        tabPane.getTabs().addAll(tab1, tab2, tab3, tab4);
        tabPane.getStyleClass().add("hidden-tabs");
        contentPane.setCenter(tabPane);

        rootPane.setCenter(contentPane);
        getChildren().add(rootPane);

        // Navigation
        navStats.setOnAction(e -> switchTab(0, "Thống kê sự cố", "Tổng hợp số liệu điều phối cơ sở vật chất toàn trường."));
        navOversight.setOnAction(e -> switchTab(1, "Giám sát & phân công", "Toàn bộ sự cố trường học, hỗ trợ lọc đa tiêu chí và can thiệp chỉ định."));
        navUsers.setOnAction(e -> switchTab(2, "Quản lý tài khoản", "Cấp mới tài khoản và theo dõi trạng thái đăng nhập toàn trường."));
        navChatAudit.setOnAction(e -> switchTab(3, "Kiểm toán hội thoại 1-1", "Chế độ kiểm toán: theo dõi đầy đủ nội dung trao đổi giữa hai bên."));
        navStats.setSelected(true);
    }

    private void switchTab(int index, String title, String sub) {
        tabPane.getSelectionModel().select(index);
        lblPageTitle.setText(title);
        lblPageSub.setText(sub);
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16, 0, 0, 0));

        // Brand
        HBox brand = new HBox(10);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(0, 16, 16, 16));

        StackPane brandCircle = new StackPane();
        brandCircle.setPrefSize(34, 34);
        brandCircle.setStyle("-fx-border-color: -brass-500; -fx-border-width: 1.5; -fx-border-radius: 999;");
        FontIcon schoolIcon = ComponentFactory.createIcon("mdi2s-school", 18, Color.web("#E3BE6A"));
        brandCircle.getChildren().add(schoolIcon);

        VBox brandText = new VBox(1);
        Label lblTitle = new Label("SIDS");
        lblTitle.getStyleClass().add("brand-title");
        Label lblSub = new Label("Sổ trực sự cố");
        lblSub.getStyleClass().add("brand-sub");
        brandText.getChildren().addAll(lblTitle, lblSub);

        brand.getChildren().addAll(brandCircle, brandText);

        // Nav Items
        navStats.getStyleClass().add("nav-item");
        navStats.setToggleGroup(navGroup);
        navStats.setGraphic(ComponentFactory.createIcon("mdi2c-chart-box", 16, Color.web("#C9D3E8")));

        navOversight.getStyleClass().add("nav-item");
        navOversight.setToggleGroup(navGroup);
        navOversight.setGraphic(ComponentFactory.createIcon("mdi2c-clipboard-text-search", 16, Color.web("#C9D3E8")));

        navUsers.getStyleClass().add("nav-item");
        navUsers.setToggleGroup(navGroup);
        navUsers.setGraphic(ComponentFactory.createIcon("mdi2a-account-group", 16, Color.web("#C9D3E8")));

        navChatAudit.getStyleClass().add("nav-item");
        navChatAudit.setToggleGroup(navGroup);
        navChatAudit.setGraphic(ComponentFactory.createIcon("mdi2m-message-text", 16, Color.web("#C9D3E8")));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Footer User Bar
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("sidebar-footer");

        Node avatar = ComponentFactory.createInitialsAvatar(currentUser.getFullName(), true);

        ComponentFactory.NameParts np = ComponentFactory.parseNameAndTitle(currentUser.getFullName());

        VBox userDetails = new VBox(2);
        HBox.setHgrow(userDetails, Priority.ALWAYS);

        Label lblName = new Label(np.displayName);
        lblName.getStyleClass().add("user-name");
        lblName.setTooltip(new Tooltip(currentUser.getFullName()));

        String subRole = np.roleOrTitle != null ? np.roleOrTitle + " · ADMIN" : "ADMIN";
        Label badgeAdmin = new Label(subRole);
        badgeAdmin.setStyle("-fx-background-color: -brass-100; -fx-text-fill: -brass-700; -fx-padding: 1 6; -fx-background-radius: 4; -fx-font-size: 10px; -fx-font-weight: bold;");
        userDetails.getChildren().addAll(lblName, badgeAdmin);

        Button btnLogout = new Button();
        btnLogout.getStyleClass().setAll("btn-icon");
        FontIcon exitIcon = ComponentFactory.createIcon("mdi2l-logout", 18, Color.web("#AFBBD6"));
        btnLogout.setGraphic(exitIcon);
        btnLogout.setTooltip(new Tooltip("Đăng xuất"));
        btnLogout.setOnAction(e -> IncidentReportApp.logout());

        footer.getChildren().addAll(avatar, userDetails, btnLogout);

        sidebar.getChildren().addAll(brand, navStats, navOversight, navUsers, navChatAudit, spacer, footer);
        return sidebar;
    }

    private HBox createTopbar() {
        HBox topbar = new HBox(16);
        topbar.setAlignment(Pos.CENTER_LEFT);
        topbar.getStyleClass().add("topbar");

        VBox titleBox = new VBox(2);
        lblPageTitle.getStyleClass().add("h1");
        lblPageSub.getStyleClass().add("muted");
        lblPageSub.setStyle("-fx-font-size: 12px;");
        titleBox.getChildren().addAll(lblPageTitle, lblPageSub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        topbar.getChildren().addAll(titleBox, sp, connIndicator);
        return topbar;
    }

    // =========================================================================
    // TAB 1: THỐNG KÊ & XUẤT BÁO CÁO (DESIGN_SYSTEM.md Mục 5.5)
    // =========================================================================
    private Node createStatisticsTab() {
        VBox layout = new VBox(16);
        layout.getStyleClass().add("page-body");

        // Action row: Segmented control + Export button
        HBox headerActionRow = new HBox(12);
        headerActionRow.setAlignment(Pos.CENTER_LEFT);

        HBox segGroupContainer = new HBox(2);
        segGroupContainer.getStyleClass().add("segment-group");

        ToggleButton segAll = createStatSegment("Tất cả", true, "ALL");
        ToggleButton segDay = createStatSegment("Hôm nay", false, "DAY");
        ToggleButton segMonth = createStatSegment("Tháng này", false, "MONTH");
        ToggleButton segQuarter = createStatSegment("Quý này", false, "QUARTER");
        ToggleButton segYear = createStatSegment("Năm nay", false, "YEAR");

        segGroupContainer.getChildren().addAll(segAll, segDay, segMonth, segQuarter, segYear);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnExportCSV = new Button("Xuất CSV");
        btnExportCSV.getStyleClass().addAll("btn-brass");
        btnExportCSV.setGraphic(ComponentFactory.createIcon("mdi2d-download", 16, Color.web("#8A6218")));
        btnExportCSV.setOnAction(e -> exportStatisticsToCSV());

        headerActionRow.getChildren().addAll(segGroupContainer, sp, btnExportCSV);

        // 4 Thẻ KPI (.kpi)
        HBox kpiBox = new HBox(14);
        kpiBox.getChildren().addAll(
                createKpiCard("TỔNG SỰ CỐ", lblTotal, "mdi2c-clipboard-text", "kpi-total"),
                createKpiCard("CHỜ TIẾP NHẬN", lblPending, "mdi2c-clock-alert-outline", "kpi-pending"),
                createKpiCard("ĐANG XỬ LÝ", lblInProgress, "mdi2w-wrench", "kpi-progress"),
                createKpiCard("ĐÃ HOÀN TẤT", lblResolved, "mdi2c-check-circle-outline", "kpi-resolved")
        );

        // 2 Thẻ chi tiết: Phân bổ phòng ban & Top phòng học
        HBox detailsBox = new HBox(16);

        // Card 1: Theo phòng ban
        VBox deptCard = new VBox(14);
        deptCard.getStyleClass().add("card");
        HBox.setHgrow(deptCard, Priority.ALWAYS);

        Label lblDeptTitle = new Label("Theo phòng ban");
        lblDeptTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13.5px; -fx-text-fill: -text-900;");
        deptCard.getChildren().addAll(lblDeptTitle, deptBarsContainer);

        // Card 2: Top vị trí
        VBox roomCard = new VBox(14);
        roomCard.getStyleClass().add("card");
        HBox.setHgrow(roomCard, Priority.ALWAYS);

        Label lblRoomTitle = new Label("Vị trí hay xảy ra sự cố");
        lblRoomTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13.5px; -fx-text-fill: -text-900;");
        roomCard.getChildren().addAll(lblRoomTitle, topRoomsContainer);

        detailsBox.getChildren().addAll(deptCard, roomCard);

        layout.getChildren().addAll(headerActionRow, kpiBox, detailsBox);

        ScrollPane scroll = new ScrollPane(layout);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private ToggleButton createStatSegment(String text, boolean selected, String range) {
        ToggleButton btn = new ToggleButton(text);
        btn.getStyleClass().add("segment");
        btn.setToggleGroup(statSegGroup);
        btn.setSelected(selected);
        btn.setOnAction(e -> {
            this.currentTimeRange = range;
            loadStatistics(range);
        });
        return btn;
    }

    private Node createKpiCard(String labelText, Label valueLabel, String iconLiteral, String kpiStyleClass) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().addAll("kpi", kpiStyleClass);
        HBox.setHgrow(card, Priority.ALWAYS);

        StackPane iconCircle = new StackPane();
        iconCircle.getStyleClass().add("kpi-icon");
        FontIcon icon = ComponentFactory.createIcon(iconLiteral, 20, Color.web("#14264A"));
        iconCircle.getChildren().add(icon);

        VBox textBox = new VBox(2);
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("kpi-label");
        valueLabel.getStyleClass().add("kpi-value");

        textBox.getChildren().addAll(lbl, valueLabel);
        card.getChildren().addAll(iconCircle, textBox);
        return card;
    }

    private void renderStatistics(StatisticReport report) {
        this.currentStatsReport = report;
        lblTotal.setText(String.valueOf(report.getTotalTickets()));
        lblPending.setText(String.valueOf(report.getPendingCount()));
        lblInProgress.setText(String.valueOf(report.getInProgressCount()));
        lblResolved.setText(String.valueOf(report.getResolvedCount()));

        // Thanh bar phòng ban tự dựng
        deptBarsContainer.getChildren().clear();
        int total = Math.max(1, report.getTotalTickets());

        if (report.getCountByDepartment() != null && !report.getCountByDepartment().isEmpty()) {
            for (Map.Entry<String, Integer> entry : report.getCountByDepartment().entrySet()) {
                String dept = entry.getKey();
                int cnt = entry.getValue();
                double ratio = (double) cnt / total;

                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);

                Label badge = ComponentFactory.createDepartmentBadge(dept);
                badge.setPrefWidth(85);

                StackPane barTrack = new StackPane();
                barTrack.getStyleClass().add("bar-track");
                HBox.setHgrow(barTrack, Priority.ALWAYS);

                Region barFill = new Region();
                String fillClass = "bar-fill-tech";
                if ("MEDICAL".equalsIgnoreCase(dept)) fillClass = "bar-fill-medical";
                else if ("SECURITY".equalsIgnoreCase(dept)) fillClass = "bar-fill-security";
                barFill.getStyleClass().addAll("bar-track", fillClass);

                barFill.prefWidthProperty().bind(barTrack.widthProperty().multiply(ratio));
                StackPane.setAlignment(barFill, Pos.CENTER_LEFT);
                barTrack.getChildren().add(barFill);

                Label lblCnt = new Label(String.valueOf(cnt));
                lblCnt.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: -text-900;");

                row.getChildren().addAll(badge, barTrack, lblCnt);
                deptBarsContainer.getChildren().add(row);
            }
        } else {
            Label empty = new Label("Chưa có sự cố phát sinh trong kỳ.");
            empty.getStyleClass().add("muted");
            deptBarsContainer.getChildren().add(empty);
        }

        // Top vị trí kèm huy chương
        topRoomsContainer.getChildren().clear();
        if (report.getTopIncidentRooms() != null && !report.getTopIncidentRooms().isEmpty()) {
            int rank = 1;
            for (Map<String, Object> r : report.getTopIncidentRooms()) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);

                Label lblRank = new Label(String.valueOf(rank));
                lblRank.getStyleClass().add("rank");
                if (rank == 1) lblRank.getStyleClass().add("rank-1");
                else if (rank == 2) lblRank.getStyleClass().add("rank-2");
                else if (rank == 3) lblRank.getStyleClass().add("rank-3");

                Label lblRoom = new Label(String.valueOf(r.get("room")));
                lblRoom.setStyle("-fx-font-weight: 500; -fx-font-size: 13px; -fx-text-fill: -text-900;");

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                int countVal = ((Number) r.get("count")).intValue();
                Label lblCnt = new Label(countVal + " lần");
                lblCnt.setStyle("-fx-font-weight: bold; -fx-font-size: 12.5px; -fx-text-fill: " + (countVal >= 5 ? "#A8402F;" : "-text-600;"));

                row.getChildren().addAll(lblRank, lblRoom, sp, lblCnt);
                topRoomsContainer.getChildren().add(row);
                rank++;
            }
        } else {
            Label empty = new Label("Chưa có địa điểm phát sinh sự cố.");
            empty.getStyleClass().add("muted");
            topRoomsContainer.getChildren().add(empty);
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
            pw.write('\ufeff'); // UTF-8 BOM
            pw.println("BÁO CÁO THỐNG KÊ SỰ CỐ TRƯỜNG THPT");
            pw.printf("Kỳ báo cáo: %s\n", currentTimeRange);
            pw.printf("Tổng sự cố: %s, Chờ xử lý: %s, Đang xử lý: %s, Đã hoàn tất: %s\n\n",
                    lblTotal.getText(), lblPending.getText(), lblInProgress.getText(), lblResolved.getText());

            pw.println("Mã sự cố,Vị trí phòng,Loại sự cố,Phòng ban điều phối,Trạng thái,Người báo cáo,SĐT người báo,Cán bộ xử lý,Thời gian báo,Thời gian giải quyết,Ghi chú");

            List<IncidentTicket> exportList = (currentStatsReport != null && currentStatsReport.getTicketList() != null && !currentStatsReport.getTicketList().isEmpty())
                    ? currentStatsReport.getTicketList()
                    : filteredTickets;

            for (IncidentTicket t : exportList) {
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
            ComponentFactory.showToast(this, "Đã xuất báo cáo CSV thành công!", true);
        } catch (Exception ex) {
            ComponentFactory.showToast(this, "Lỗi khi xuất file: " + ex.getMessage(), false);
        }
    }

    private String cleanCsv(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }

    // =========================================================================
    // TAB 2: GIÁM SÁT TOÀN DIỆN & PHÂN CÔNG (DESIGN_SYSTEM.md Mục 5.6)
    // =========================================================================
    private Node createIncidentOversightTab() {
        VBox layout = new VBox(12);
        layout.getStyleClass().add("page-body");

        // Filter bar Card
        HBox filterCard = new HBox(12);
        filterCard.setAlignment(Pos.CENTER_LEFT);
        filterCard.getStyleClass().add("card");
        filterCard.setPadding(new Insets(10, 16, 10, 16));

        txtSearchTicket.setPromptText("Tìm mã, phòng, người báo, cán bộ...");
        txtSearchTicket.getStyleClass().add("search-field");
        txtSearchTicket.setPrefWidth(220);
        txtSearchTicket.textProperty().addListener((obs, oldVal, newVal) -> applyTicketFilter());

        cbStatusFilter.getItems().setAll("Tất cả trạng thái", "Chờ tiếp nhận (PENDING)", "Đang xử lý (IN_PROGRESS)", "Đã hoàn tất (RESOLVED)", "Đã hủy (CANCELLED)");
        cbStatusFilter.getSelectionModel().select(0);
        cbStatusFilter.setOnAction(e -> applyTicketFilter());

        cbDeptFilter.getItems().setAll("Tất cả phòng ban", "Kỹ thuật (TECHNICAL)", "Y tế (MEDICAL)", "Bảo vệ (SECURITY)");
        cbDeptFilter.getSelectionModel().select(0);
        cbDeptFilter.setOnAction(e -> applyTicketFilter());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        lblFilterCount.getStyleClass().addAll("caption", "muted");

        Button btnRefresh = new Button();
        btnRefresh.getStyleClass().setAll("btn-icon");
        btnRefresh.setGraphic(ComponentFactory.createIcon("mdi2r-refresh", 18, Color.web("#14264A")));
        btnRefresh.setTooltip(new Tooltip("Làm mới danh sách"));
        btnRefresh.setOnAction(e -> loadAllTickets());

        filterCard.getChildren().addAll(txtSearchTicket, cbStatusFilter, cbDeptFilter, sp, lblFilterCount, btnRefresh);

        // Columns
        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("MÃ");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setPrefWidth(105);
        colCode.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    setText(item);
                    getStyleClass().add("mono");
                }
            }
        });

        TableColumn<IncidentTicket, String> colRoom = new TableColumn<>("PHÒNG");
        colRoom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRoomLocation()));
        colRoom.setPrefWidth(95);
        colRoom.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    setText(item);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: -text-900;");
                }
            }
        });

        TableColumn<IncidentTicket, IncidentTicket> colIncident = new TableColumn<>("SỰ CỐ");
        colIncident.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colIncident.setPrefWidth(170);
        colIncident.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(IncidentTicket item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String catName = item.getCategoryName() != null ? item.getCategoryName() : item.getCategoryCode();
                    Label lblC = new Label(catName);
                    lblC.setStyle("-fx-font-weight: 600; -fx-text-fill: -text-900; -fx-font-size: 12.5px;");
                    setGraphic(lblC);
                }
            }
        });

        TableColumn<IncidentTicket, String> colDept = new TableColumn<>("PHÒNG BAN");
        colDept.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTargetDepartment()));
        colDept.setCellFactory(col -> new ComponentFactory.DeptBadgeCell<>());
        colDept.setPrefWidth(110);

        TableColumn<IncidentTicket, String> colStatus = new TableColumn<>("TRẠNG THÁI");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        colStatus.setCellFactory(col -> new ComponentFactory.StatusBadgeCell<>());
        colStatus.setPrefWidth(130);

        TableColumn<IncidentTicket, String> colReporter = new TableColumn<>("NGƯỜI BÁO");
        colReporter.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getReporterName()));
        colReporter.setPrefWidth(190);
        colReporter.setCellFactory(col -> new ComponentFactory.PersonCell<>());

        TableColumn<IncidentTicket, String> colStaff = new TableColumn<>("CÁN BỘ XỬ LÝ");
        colStaff.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAssignedStaffName()));
        colStaff.setPrefWidth(190);
        colStaff.setCellFactory(col -> new ComponentFactory.PersonCell<>());

        TableColumn<IncidentTicket, Void> colActions = new TableColumn<>("THAO TÁC");
        colActions.setPrefWidth(150);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnDetail = new Button();
            private final Button btnAssign = new Button("Chỉ định");
            private final Button btnChat = new Button();
            private final HBox btnBox = new HBox(6, btnDetail, btnAssign, btnChat);

            {
                btnDetail.getStyleClass().setAll("btn-icon");
                btnDetail.setGraphic(ComponentFactory.createIcon("mdi2e-eye-outline", 16, Color.web("#14264A")));
                btnDetail.setTooltip(new Tooltip("Xem chi tiết sự cố (hoặc nhấp đúp vào dòng)"));
                btnDetail.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) openDetailDialog(t);
                });

                btnAssign.getStyleClass().addAll("btn-brass", "btn-sm");
                btnAssign.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) openManualAssignDialog(t);
                });

                btnChat.getStyleClass().setAll("btn-icon");
                btnChat.setGraphic(ComponentFactory.createIcon("mdi2m-message-text-outline", 16, Color.web("#14264A")));
                btnChat.setTooltip(new Tooltip("Xem lịch sử trao đổi 1-1"));
                btnChat.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        auditChatPanel.openTicketChat(t);
                        navOversight.setSelected(false);
                        navChatAudit.setSelected(true);
                        switchTab(3, "Kiểm toán hội thoại 1-1", "Chế độ kiểm toán: theo dõi đầy đủ nội dung trao đổi giữa hai bên.");
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
                    boolean canAssign = (t != null && !Constants.STATUS_RESOLVED.equalsIgnoreCase(t.getStatus())
                            && !Constants.STATUS_CANCELLED.equalsIgnoreCase(t.getStatus()));
                    btnAssign.setVisible(canAssign);
                    btnAssign.setManaged(canAssign);
                    setGraphic(btnBox);
                }
            }
        });

        allTicketsTable.getColumns().addAll(colCode, colRoom, colIncident, colDept, colStatus, colReporter, colStaff, colActions);
        allTicketsTable.setRowFactory(tv -> {
            TableRow<IncidentTicket> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDetailDialog(row.getItem());
                }
            });
            return row;
        });
        allTicketsTable.setItems(filteredTickets);
        VBox.setVgrow(allTicketsTable, Priority.ALWAYS);

        // Row factory
        allTicketsTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(IncidentTicket item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-tech", "row-medical", "row-security");
                if (item != null && !empty) {
                    String dept = item.getTargetDepartment();
                    if (Constants.SPECIALTY_TECHNICAL.equalsIgnoreCase(dept)) {
                        getStyleClass().add("row-tech");
                    } else if (Constants.SPECIALTY_MEDICAL.equalsIgnoreCase(dept)) {
                        getStyleClass().add("row-medical");
                    } else if (Constants.SPECIALTY_SECURITY.equalsIgnoreCase(dept)) {
                        getStyleClass().add("row-security");
                    }
                }
            }
        });

        layout.getChildren().addAll(filterCard, allTicketsTable);
        return layout;
    }

    private void applyTicketFilter() {
        String keyword = txtSearchTicket.getText() != null ? txtSearchTicket.getText().trim().toLowerCase() : "";
        String statusSel = cbStatusFilter.getValue();
        String deptSel = cbDeptFilter.getValue();

        filteredTickets.setPredicate(t -> {
            if (t == null) return false;

            if (statusSel != null && !statusSel.contains("Tất cả")) {
                if (statusSel.contains("PENDING") && !Constants.STATUS_PENDING.equalsIgnoreCase(t.getStatus())) return false;
                if (statusSel.contains("IN_PROGRESS") && !Constants.STATUS_IN_PROGRESS.equalsIgnoreCase(t.getStatus())) return false;
                if (statusSel.contains("RESOLVED") && !Constants.STATUS_RESOLVED.equalsIgnoreCase(t.getStatus())) return false;
                if (statusSel.contains("CANCELLED") && !Constants.STATUS_CANCELLED.equalsIgnoreCase(t.getStatus())) return false;
            }

            if (deptSel != null && !deptSel.contains("Tất cả")) {
                if (deptSel.contains("TECHNICAL") && !Constants.SPECIALTY_TECHNICAL.equalsIgnoreCase(t.getTargetDepartment())) return false;
                if (deptSel.contains("MEDICAL") && !Constants.SPECIALTY_MEDICAL.equalsIgnoreCase(t.getTargetDepartment())) return false;
                if (deptSel.contains("SECURITY") && !Constants.SPECIALTY_SECURITY.equalsIgnoreCase(t.getTargetDepartment())) return false;
            }

            if (!keyword.isEmpty()) {
                boolean matchCode = t.getTicketCode() != null && t.getTicketCode().toLowerCase().contains(keyword);
                boolean matchRoom = t.getRoomLocation() != null && t.getRoomLocation().toLowerCase().contains(keyword);
                boolean matchReporter = t.getReporterName() != null && t.getReporterName().toLowerCase().contains(keyword);
                boolean matchStaff = t.getAssignedStaffName() != null && t.getAssignedStaffName().toLowerCase().contains(keyword);
                boolean matchDesc = t.getDescription() != null && t.getDescription().toLowerCase().contains(keyword);
                if (!matchCode && !matchRoom && !matchReporter && !matchStaff && !matchDesc) return false;
            }

            return true;
        });

        lblFilterCount.setText(String.format("Hiển thị %d / %d sự cố", filteredTickets.size(), allTicketsList.size()));
    }

    private void openDetailDialog(IncidentTicket ticket) {
        if (ticket == null) return;
        IncidentDetailDialog dialog = new IncidentDetailDialog(
                getScene().getWindow(),
                ticket,
                currentUser,
                t -> {
                    auditChatPanel.openTicketChat(t);
                    navOversight.setSelected(false);
                    navChatAudit.setSelected(true);
                    switchTab(3, "Kiểm toán hội thoại 1-1", "Chế độ kiểm toán: theo dõi đầy đủ nội dung trao đổi giữa hai bên.");
                },
                null,
                null,
                this::openManualAssignDialog,
                null
        );
        dialog.showAndWait();
    }

    private void openManualAssignDialog(IncidentTicket ticket) {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Chỉ định cán bộ");
        ComponentFactory.applyTheme(dialog.getDialogPane());

        dialog.setHeaderText("Chỉ định cán bộ xử lý " + ticket.getTicketCode());

        VBox content = new VBox(10);
        content.setPadding(new Insets(12));
        content.setPrefWidth(400);

        Label lblSubtitle = new Label(String.format("Vị trí: %s · Phòng chuyên trách: %s",
                ticket.getRoomLocation(), ticket.getTargetDepartment()));
        lblSubtitle.getStyleClass().add("muted");

        Label lblSelectStaff = new Label("Chọn cán bộ tiếp nhận:");
        lblSelectStaff.getStyleClass().add("field-label");

        ComboBox<User> cbStaff = new ComboBox<>();
        cbStaff.setMaxWidth(Double.MAX_VALUE);

        // Lọc cán bộ cùng chuyên môn
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

        // Custom CellFactory hiển thị Avatar + Tên + Đèn Online
        cbStaff.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    Node av = ComponentFactory.createInitialsAvatar(u.getFullName(), false);
                    VBox tb = new VBox(1);
                    Label name = new Label(u.getFullName());
                    name.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
                    Label spec = new Label(u.getSpecialty());
                    spec.getStyleClass().add("muted");
                    spec.setStyle("-fx-font-size: 10.5px;");
                    tb.getChildren().addAll(name, spec);

                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);

                    Label online = new Label(u.getIsOnline() == 1 ? "Online" : "Ngoại tuyến");
                    online.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (u.getIsOnline() == 1 ? "#2F6F55;" : "#8C8677;"));

                    row.getChildren().addAll(av, tb, sp, online);
                    setGraphic(row);
                    setText(null);
                }
            }
        });

        Label lblNote = new Label("Ghi chú chỉ định:");
        lblNote.getStyleClass().add("field-label");
        TextField txtNote = new TextField("Admin BGH chỉ định xử lý ngay");

        content.getChildren().addAll(lblSubtitle, lblSelectStaff, cbStaff, lblNote, txtNote);
        dialog.getDialogPane().setContent(content);

        ButtonType btnConfirm = new ButtonType("Xác nhận chỉ định", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnCancel, btnConfirm);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(btnConfirm);
        if (okBtn != null) okBtn.getStyleClass().setAll("btn-primary");
        Button cancelBtn = (Button) dialog.getDialogPane().lookupButton(btnCancel);
        if (cancelBtn != null) cancelBtn.getStyleClass().setAll("btn-secondary");

        dialog.setResultConverter(b -> (b == btnConfirm) ? cbStaff.getValue() : null);

        Optional<User> result = dialog.showAndWait();
        result.ifPresent(staff -> {
            Packet p = new Packet(Constants.TYPE_MANUAL_ASSIGN,
                    String.format("MANUAL_ASSIGN|ticketId=%d|staffId=%d|note=%s",
                            ticket.getTicketId(), staff.getUserId(), txtNote.getText().replace("|", "/")));
            SocketClientManager.getInstance().sendPacket(p);
            ComponentFactory.showToast(this, "Đã chỉ định ca cho " + staff.getFullName(), true);
        });
    }

    // =========================================================================
    // TAB 3: QUẢN LÝ & CẤP TÀI KHOẢN (DESIGN_SYSTEM.md Mục 5.7)
    // =========================================================================
    private Node createUserManagementTab() {
        HBox layout = new HBox(16);
        layout.getStyleClass().add("page-body");

        // Form Card (Left 360px)
        VBox formCard = new VBox(10);
        formCard.getStyleClass().add("card");
        formCard.setPrefWidth(360);
        formCard.setMinWidth(360);
        formCard.setMaxWidth(360);

        Label lblFormTitle = new Label("Cấp tài khoản mới");
        lblFormTitle.getStyleClass().add("h2");

        Label lblFormSub = new Label("Chỉ Admin mới có quyền tạo và cấp tài khoản.");
        lblFormSub.getStyleClass().add("muted");
        lblFormSub.setStyle("-fx-font-size: 11.5px;");

        // Thông tin đăng nhập
        Label lblSecAuth = new Label("THÔNG TIN ĐĂNG NHẬP");
        lblSecAuth.getStyleClass().addAll("caption", "muted");

        Label lblUser = new Label("Tên đăng nhập *");
        lblUser.getStyleClass().add("field-label");
        txtNewUsername.setPromptText("VD: kt_hung, gv_thu");

        Label lblPass = new Label("Mật khẩu ban đầu *");
        lblPass.getStyleClass().add("field-label");
        txtNewPassword.setText("123456");

        Region divider = new Region();
        divider.getStyleClass().add("divider");

        // Thông tin cá nhân
        Label lblSecProfile = new Label("THÔNG TIN CÁ NHÂN");
        lblSecProfile.getStyleClass().addAll("caption", "muted");

        Label lblName = new Label("Họ và tên đầy đủ *");
        lblName.getStyleClass().add("field-label");
        txtNewFullName.setPromptText("VD: Nguyễn Văn Hùng");

        Label lblPhone = new Label("Số điện thoại");
        lblPhone.getStyleClass().add("field-label");
        txtNewPhone.setPromptText("VD: 0903 123 456");

        // Role & Specialty side by side
        Label lblRole = new Label("Vai trò & Phòng ban chuyên trách");
        lblRole.getStyleClass().add("field-label");

        cbNewRole.getItems().setAll(Constants.ROLE_RESPONDER, Constants.ROLE_REPORTER, Constants.ROLE_ADMIN);
        cbNewRole.getSelectionModel().select(0);
        cbNewRole.setMaxWidth(Double.MAX_VALUE);

        cbNewSpecialty.getItems().setAll(
                Constants.SPECIALTY_TECHNICAL,
                Constants.SPECIALTY_MEDICAL,
                Constants.SPECIALTY_SECURITY,
                Constants.SPECIALTY_NONE,
                Constants.SPECIALTY_ALL
        );
        cbNewSpecialty.getSelectionModel().select(0);
        cbNewSpecialty.setMaxWidth(Double.MAX_VALUE);

        cbNewRole.setOnAction(e -> {
            boolean isResp = Constants.ROLE_RESPONDER.equalsIgnoreCase(cbNewRole.getValue());
            cbNewSpecialty.setDisable(!isResp);
        });

        HBox roleSpecBox = new HBox(8, cbNewRole, cbNewSpecialty);
        HBox.setHgrow(cbNewRole, Priority.ALWAYS);
        HBox.setHgrow(cbNewSpecialty, Priority.ALWAYS);

        Button btnCreate = new Button("CẤP TÀI KHOẢN");
        btnCreate.getStyleClass().addAll("btn-primary", "btn-block");
        btnCreate.setOnAction(e -> doCreateUser());

        formCard.getChildren().addAll(
                lblFormTitle, lblFormSub,
                lblSecAuth, lblUser, txtNewUsername, lblPass, txtNewPassword,
                divider,
                lblSecProfile, lblName, txtNewFullName, lblPhone, txtNewPhone,
                lblRole, roleSpecBox,
                btnCreate
        );

        // Table Card (Right)
        VBox tableCard = new VBox(12);
        tableCard.getStyleClass().add("card");
        HBox.setHgrow(tableCard, Priority.ALWAYS);

        HBox tableTop = new HBox(12);
        tableTop.setAlignment(Pos.CENTER_LEFT);

        Label lblUsersTitle = new Label("DANH SÁCH TÀI KHOẢN TOÀN TRƯỜNG");
        lblUsersTitle.getStyleClass().add("caption");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRefreshUsers = new Button("Làm mới");
        btnRefreshUsers.getStyleClass().add("btn-secondary");
        btnRefreshUsers.setGraphic(ComponentFactory.createIcon("mdi2r-refresh", 16, Color.web("#14264A")));
        btnRefreshUsers.setOnAction(e -> loadAllUsers());

        tableTop.getChildren().addAll(lblUsersTitle, sp, btnRefreshUsers);

        // Columns
        TableColumn<User, String> colUser = new TableColumn<>("NGƯỜI DÙNG");
        colUser.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFullName()));
        colUser.setPrefWidth(220);
        colUser.setCellFactory(col -> new ComponentFactory.PersonCell<>(user -> "@" + user.getUsername()));

        TableColumn<User, String> colRole = new TableColumn<>("VAI TRÒ");
        colRole.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRole()));
        colRole.setPrefWidth(120);
        colRole.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setGraphic(null);
                } else {
                    Label b = new Label(role);
                    b.getStyleClass().add("dept");
                    if (Constants.ROLE_ADMIN.equalsIgnoreCase(role)) {
                        b.setStyle("-fx-background-color: -brass-100; -fx-text-fill: -brass-700;");
                    } else if (Constants.ROLE_REPORTER.equalsIgnoreCase(role)) {
                        b.setStyle("-fx-background-color: -ink-100; -fx-text-fill: -ink-900;");
                    } else {
                        b.setStyle("-fx-background-color: -dept-tech-bg; -fx-text-fill: -dept-tech;");
                    }
                    setGraphic(b);
                }
            }
        });

        TableColumn<User, String> colSpec = new TableColumn<>("PHÒNG BAN");
        colSpec.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSpecialty()));
        colSpec.setPrefWidth(110);
        colSpec.setCellFactory(col -> new ComponentFactory.DeptBadgeCell<>());

        TableColumn<User, String> colPhone = new TableColumn<>("SỐ ĐT");
        colPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPhone() != null ? d.getValue().getPhone() : "-"));
        colPhone.setPrefWidth(120);

        TableColumn<User, Integer> colOnline = new TableColumn<>("TRẠNG THÁI");
        colOnline.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getIsOnline()));
        colOnline.setPrefWidth(120);
        colOnline.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer online, boolean empty) {
                super.updateItem(online, empty);
                if (empty || online == null) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(6);
                    box.setAlignment(Pos.CENTER_LEFT);
                    Circle dot = new Circle(4);
                    Label lbl = new Label();
                    lbl.setStyle("-fx-font-size: 11.5px;");
                    if (online == 1) {
                        dot.setFill(Color.web("#2F6F55"));
                        lbl.setText("Online");
                        lbl.setStyle("-fx-text-fill: -chalk-600; -fx-font-weight: 500;");
                    } else {
                        dot.setFill(Color.web("#8C8677"));
                        lbl.setText("Ngoại tuyến");
                        lbl.setStyle("-fx-text-fill: -text-400;");
                    }
                    box.getChildren().addAll(dot, lbl);
                    setGraphic(box);
                }
            }
        });

        userTable.getColumns().addAll(colUser, colRole, colSpec, colPhone, colOnline);
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
            ComponentFactory.showToast(this, "Vui lòng nhập đầy đủ Username, Password và Họ tên!", false);
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
        Platform.runLater(() -> {
            if (packet.getType() == Constants.TYPE_ADMIN_GET_STATS_RESP) {
                if (packet.hasAttachment()) {
                    try {
                        String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                        StatisticReport report = gson.fromJson(json, StatisticReport.class);
                        renderStatistics(report);
                    } catch (Exception ignored) {}
                }

            } else if (packet.getType() == Constants.TYPE_LIST_TICKETS_RESP) {
                if (packet.hasAttachment()) {
                    try {
                        String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                        Type listType = new TypeToken<List<IncidentTicket>>() {}.getType();
                        List<IncidentTicket> list = gson.fromJson(json, listType);
                        if (list != null) {
                            allTicketsList.setAll(list);
                            applyTicketFilter();
                            if (auditChatPanel != null) {
                                auditChatPanel.setConversations(list);
                            }
                        }
                    } catch (Exception ignored) {}
                }

            } else if (packet.getType() == Constants.TYPE_ADMIN_GET_USERS_RESP) {
                if (packet.hasAttachment()) {
                    try {
                        String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                        Type listType = new TypeToken<List<User>>() {}.getType();
                        List<User> list = gson.fromJson(json, listType);
                        if (list != null) {
                            userList.setAll(list);
                        }
                    } catch (Exception ignored) {}
                }

            } else if (packet.getType() == Constants.TYPE_ADMIN_CREATE_RESP) {
                Map<String, String> p = packet.parseParams();
                String status = p.get("status");
                if ("OK".equalsIgnoreCase(status)) {
                    ComponentFactory.showToast(this, "Đã cấp mới tài khoản: " + p.get("username"), true);
                    txtNewUsername.clear();
                    txtNewFullName.clear();
                    txtNewPhone.clear();
                    loadAllUsers();
                } else {
                    ComponentFactory.showToast(this, p.getOrDefault("message", "Lỗi tạo tài khoản!"), false);
                }

            } else if (packet.getType() == Constants.TYPE_MANUAL_ASSIGN_RESP) {
                loadAllTickets();
                ComponentFactory.showToast(this, "Đã phân công sự cố thành công!", true);

            } else if (packet.getType() == Constants.TYPE_DISPATCH_NOTIFY || packet.getType() == Constants.TYPE_STATUS_BROADCAST) {
                if (packet.getType() == Constants.TYPE_DISPATCH_NOTIFY) {
                    Map<String, String> p = packet.parseParams();
                    String code = p.getOrDefault("code", "Mới");
                    String room = p.getOrDefault("room", "");
                    ComponentFactory.showToast(this, "Có ca sự cố mới báo: " + code + (room.isEmpty() ? "" : " · Phòng " + room), true);
                }
                loadAllTickets();
                loadStatistics(currentTimeRange);
            }
        });
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            connIndicator.getChildren().clear();
            Circle dot = new Circle(4, Color.web("#A8402F"));
            Label l = new Label("Mất kết nối");
            l.getStyleClass().setAll("conn-lost");
            connIndicator.getChildren().addAll(dot, l);
        });
    }
}
