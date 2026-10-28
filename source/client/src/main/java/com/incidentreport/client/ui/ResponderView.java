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
import org.kordamp.ikonli.javafx.FontIcon;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phân hệ Cán bộ tiếp nhận & Xử lý sự cố (ResponderView) tái thiết kế theo DESIGN_SYSTEM.md:
 * - Sidebar mực xanh 232px, hiển thị số ca chờ trên Nav Item "Hàng đợi (N)".
 * - Bảng Hàng đợi có viền màu theo phòng ban (row-tech/medical/security), chip cảnh báo thời gian chờ (wait-chip-urgent).
 * - Nút "Tiếp nhận" màu xanh bảng, cơ chế báo toast khi bị giành ca.
 * - Bảng Nhiệm vụ có lọc nhanh SegmentedControl, nút Hoàn tất icon check-bold.
 */
public class ResponderView extends StackPane implements SocketClientManager.PacketListener {

    private final User currentUser;
    private final Gson gson = new Gson();

    // Sidebar navigation
    private final ToggleGroup navGroup = new ToggleGroup();
    private final ToggleButton navQueue = new ToggleButton("Hàng đợi");
    private final Label lblQueueCount = new Label("0");
    private final ToggleButton navTasks = new ToggleButton("Nhiệm vụ của tôi");
    private final ToggleButton navChat = new ToggleButton("Trao đổi 1-1");

    // Topbar
    private final Label lblPageTitle = new Label("Hàng đợi tiếp nhận");
    private final Label lblPageSub = new Label();
    private final HBox connIndicator = ComponentFactory.createConnectionIndicator(true);

    // TabPane ẩn
    private final TabPane tabPane = new TabPane();
    private ChatPanel chatPanel;

    // Hàng đợi phòng ban (Tab 1)
    private final ObservableList<IncidentTicket> deptPendingList = FXCollections.observableArrayList();
    private final TableView<IncidentTicket> deptTable = new TableView<>();

    // Nhiệm vụ của tôi (Tab 2)
    private final ObservableList<IncidentTicket> myTasksList = FXCollections.observableArrayList();
    private final FilteredList<IncidentTicket> filteredMyTasks = new FilteredList<>(myTasksList, t -> true);
    private final TableView<IncidentTicket> myTasksTable = new TableView<>();
    private final ToggleGroup segGroup = new ToggleGroup();

    public ResponderView(User user) {
        this.currentUser = user;
        lblPageSub.setText("Sự cố thuộc chuyên môn " + currentUser.getSpecialty() + " đang chờ cán bộ xử lý.");
        initShell();
        SocketClientManager.getInstance().addListener(this);
        loadDeptTickets();
        loadMyTasks();
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
        Tab tab1 = new Tab("Queue", createDeptQueueTab());
        Tab tab2 = new Tab("Tasks", createMyTasksTab());
        chatPanel = new ChatPanel(currentUser);
        Tab tab3 = new Tab("Chat", chatPanel);

        tabPane.getTabs().addAll(tab1, tab2, tab3);
        tabPane.getStyleClass().add("hidden-tabs");
        contentPane.setCenter(tabPane);

        rootPane.setCenter(contentPane);
        getChildren().add(rootPane);

        // Navigation actions
        navQueue.setOnAction(e -> switchTab(0, "Hàng đợi tiếp nhận", "Sự cố thuộc chuyên môn " + currentUser.getSpecialty() + " đang chờ cán bộ xử lý."));
        navTasks.setOnAction(e -> switchTab(1, "Nhiệm vụ của tôi", "Các ca bạn đang trực tiếp xử lý hoặc đã hoàn tất."));
        navChat.setOnAction(e -> switchTab(2, "Trao đổi 1-1", "Kênh hội thoại trực tiếp với giáo viên/học sinh báo sự cố."));
        navQueue.setSelected(true);
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

        // Nav 1: Hàng đợi kèm số lượng (nav-count)
        navQueue.getStyleClass().add("nav-item");
        navQueue.setToggleGroup(navGroup);
        HBox queueGraphic = new HBox(10);
        queueGraphic.setAlignment(Pos.CENTER_LEFT);
        FontIcon inboxIcon = ComponentFactory.createIcon("mdi2i-inbox-arrow-down", 16, Color.web("#C9D3E8"));
        Label lblQText = new Label("Hàng đợi");
        lblQText.setStyle("-fx-text-fill: inherit;");
        Region spQ = new Region();
        HBox.setHgrow(spQ, Priority.ALWAYS);
        lblQueueCount.getStyleClass().add("nav-count");
        queueGraphic.getChildren().addAll(inboxIcon, lblQText, spQ, lblQueueCount);
        navQueue.setGraphic(queueGraphic);
        navQueue.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        // Nav 2: Nhiệm vụ của tôi
        navTasks.getStyleClass().add("nav-item");
        navTasks.setToggleGroup(navGroup);
        navTasks.setGraphic(ComponentFactory.createIcon("mdi2w-wrench", 16, Color.web("#C9D3E8")));

        // Nav 3: Trao đổi 1-1
        navChat.getStyleClass().add("nav-item");
        navChat.setToggleGroup(navGroup);
        navChat.setGraphic(ComponentFactory.createIcon("mdi2m-message-text", 16, Color.web("#C9D3E8")));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Footer User Bar
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("sidebar-footer");

        Node avatar = ComponentFactory.createInitialsAvatar(currentUser.getFullName(), false);

        ComponentFactory.NameParts np = ComponentFactory.parseNameAndTitle(currentUser.getFullName());

        VBox userDetails = new VBox(2);
        HBox.setHgrow(userDetails, Priority.ALWAYS);

        Label lblName = new Label(np.displayName);
        lblName.getStyleClass().add("user-name");
        lblName.setTooltip(new Tooltip(currentUser.getFullName()));

        Label deptBadge = ComponentFactory.createDepartmentBadge(currentUser.getSpecialty());
        userDetails.getChildren().addAll(lblName, deptBadge);

        Button btnLogout = new Button();
        btnLogout.getStyleClass().setAll("btn-icon");
        FontIcon exitIcon = ComponentFactory.createIcon("mdi2l-logout", 18, Color.web("#AFBBD6"));
        btnLogout.setGraphic(exitIcon);
        btnLogout.setTooltip(new Tooltip("Đăng xuất"));
        btnLogout.setOnAction(e -> IncidentReportApp.logout());

        footer.getChildren().addAll(avatar, userDetails, btnLogout);

        sidebar.getChildren().addAll(brand, navQueue, navTasks, navChat, spacer, footer);
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
    // TAB 1: HÀNG ĐỢI SỰ CỐ PHÒNG BAN (DESIGN_SYSTEM.md Mục 5.4)
    // =========================================================================
    private Node createDeptQueueTab() {
        VBox layout = new VBox(12);
        layout.getStyleClass().add("page-body");

        HBox toolBar = new HBox(12);
        toolBar.setAlignment(Pos.CENTER_LEFT);

        Label lblSection = new Label("CÁC SỰ CỐ CHỜ TIẾP NHẬN");
        lblSection.getStyleClass().addAll("caption", "muted");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRefresh = new Button("Làm mới hàng đợi");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setGraphic(ComponentFactory.createIcon("mdi2r-refresh", 16, Color.web("#14264A")));
        btnRefresh.setOnAction(e -> loadDeptTickets());

        toolBar.getChildren().addAll(lblSection, sp, btnRefresh);

        // Row factory: Viền màu phòng ban 3px bên trái
        deptTable.setRowFactory(tv -> new TableRow<>() {
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

        // Columns
        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("MÃ");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setPrefWidth(120);
        colCode.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
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
        colIncident.setPrefWidth(175);
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

        TableColumn<IncidentTicket, String> colReporter = new TableColumn<>("NGƯỜI BÁO");
        colReporter.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getReporterName()));
        colReporter.setPrefWidth(190);
        colReporter.setCellFactory(col -> new ComponentFactory.PersonCell<>(ticket ->
                (ticket.getReporterPhone() != null && !ticket.getReporterPhone().isEmpty()) ? "ĐT: " + ticket.getReporterPhone() : ""
        ));

        // Cột thời gian chờ kèm chip cảnh báo (wait-chip / wait-chip-urgent)
        TableColumn<IncidentTicket, IncidentTicket> colWait = new TableColumn<>("CHỜ");
        colWait.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colWait.setPrefWidth(110);
        colWait.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(IncidentTicket item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    long minutes = calculateMinutesPassed(item.getCreatedAt());
                    Label chip = new Label(minutes + " phút");
                    chip.getStyleClass().add("wait-chip");

                    boolean isUrgent = (minutes >= 10);
                    if (Constants.SPECIALTY_MEDICAL.equalsIgnoreCase(item.getTargetDepartment())
                            || Constants.SPECIALTY_SECURITY.equalsIgnoreCase(item.getTargetDepartment())) {
                        if (minutes >= 3) isUrgent = true;
                    }

                    if (isUrgent) {
                        chip.getStyleClass().add("wait-chip-urgent");
                    }
                    setGraphic(chip);
                    setTooltip(new Tooltip("Thời gian báo: " + item.getCreatedAt()));
                }
            }
        });

        TableColumn<IncidentTicket, Void> colClaim = new TableColumn<>("THAO TÁC");
        colClaim.setPrefWidth(165);
        colClaim.setCellFactory(col -> new TableCell<>() {
            private final Button btnDetail = new Button();
            private final Button btn = new Button("Tiếp nhận");
            private final HBox btnBox = new HBox(6, btnDetail, btn);

            {
                btnDetail.getStyleClass().setAll("btn-icon");
                btnDetail.setGraphic(ComponentFactory.createIcon("mdi2e-eye-outline", 16, Color.web("#14264A")));
                btnDetail.setTooltip(new Tooltip("Xem chi tiết sự cố (hoặc nhấp đúp vào dòng)"));
                btnDetail.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) openDetailDialog(t);
                });

                btn.getStyleClass().addAll("btn-success", "btn-sm");
                btn.setGraphic(ComponentFactory.createIcon("mdi2h-hand-pointing-right", 14, Color.WHITE));
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
                else setGraphic(btnBox);
            }
        });

        deptTable.getColumns().addAll(colCode, colRoom, colIncident, colReporter, colWait, colClaim);
        deptTable.setRowFactory(tv -> {
            TableRow<IncidentTicket> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDetailDialog(row.getItem());
                }
            });
            return row;
        });
        deptTable.setItems(deptPendingList);
        VBox.setVgrow(deptTable, Priority.ALWAYS);

        // Empty placeholder
        VBox placeholder = new VBox(10);
        placeholder.setAlignment(Pos.CENTER);
        FontIcon emptyIcon = ComponentFactory.createIcon("mdi2i-inbox-outline", 44, Color.web("#D9CDB3"));
        Label lblEmpty = new Label("Hàng đợi đang trống");
        lblEmpty.getStyleClass().add("h2");
        Label lblEmptySub = new Label("Hiện không có sự cố nào thuộc phòng " + currentUser.getSpecialty() + " chờ tiếp nhận.");
        lblEmptySub.getStyleClass().add("muted");
        placeholder.getChildren().addAll(emptyIcon, lblEmpty, lblEmptySub);
        deptTable.setPlaceholder(placeholder);

        layout.getChildren().addAll(toolBar, deptTable);
        return layout;
    }

    // =========================================================================
    // TAB 2: NHIỆM VỤ CỦA TÔI (DESIGN_SYSTEM.md Mục 5.4)
    // =========================================================================
    private Node createMyTasksTab() {
        VBox layout = new VBox(12);
        layout.getStyleClass().add("page-body");

        HBox toolBar = new HBox(12);
        toolBar.setAlignment(Pos.CENTER_LEFT);

        HBox segGroupContainer = new HBox(2);
        segGroupContainer.getStyleClass().add("segment-group");

        ToggleButton segAll = createSegment("Tất cả", true);
        ToggleButton segProgress = createSegment("Đang xử lý", false);
        ToggleButton segResolved = createSegment("Đã hoàn tất", false);

        segAll.setOnAction(e -> applyTaskFilter(null));
        segProgress.setOnAction(e -> applyTaskFilter(Constants.STATUS_IN_PROGRESS));
        segResolved.setOnAction(e -> applyTaskFilter(Constants.STATUS_RESOLVED));

        segGroupContainer.getChildren().addAll(segAll, segProgress, segResolved);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRefresh = new Button("Làm mới");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setGraphic(ComponentFactory.createIcon("mdi2r-refresh", 16, Color.web("#14264A")));
        btnRefresh.setOnAction(e -> loadMyTasks());

        toolBar.getChildren().addAll(segGroupContainer, sp, btnRefresh);

        // Columns
        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("MÃ");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setPrefWidth(120);
        colCode.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
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
        colIncident.setPrefWidth(175);
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

        TableColumn<IncidentTicket, String> colStatus = new TableColumn<>("TRẠNG THÁI");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        colStatus.setCellFactory(col -> new ComponentFactory.StatusBadgeCell<>());
        colStatus.setPrefWidth(130);

        TableColumn<IncidentTicket, String> colReporter = new TableColumn<>("NGƯỜI BÁO");
        colReporter.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getReporterName()));
        colReporter.setPrefWidth(190);
        colReporter.setCellFactory(col -> new ComponentFactory.PersonCell<>(ticket ->
                (ticket.getReporterPhone() != null && !ticket.getReporterPhone().isEmpty()) ? "ĐT: " + ticket.getReporterPhone() : ""
        ));

        TableColumn<IncidentTicket, Void> colChatAction = new TableColumn<>("TRAO ĐỔI");
        colChatAction.setPrefWidth(130);
        colChatAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnDetail = new Button();
            private final Button btnChat = new Button("Chat");
            private final HBox btnBox = new HBox(6, btnDetail, btnChat);

            {
                btnDetail.getStyleClass().setAll("btn-icon");
                btnDetail.setGraphic(ComponentFactory.createIcon("mdi2e-eye-outline", 16, Color.web("#14264A")));
                btnDetail.setTooltip(new Tooltip("Xem chi tiết sự cố (hoặc nhấp đúp vào dòng)"));
                btnDetail.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) openDetailDialog(t);
                });

                btnChat.getStyleClass().addAll("btn-secondary", "btn-sm");
                btnChat.setGraphic(ComponentFactory.createIcon("mdi2m-message-text-outline", 14, Color.web("#14264A")));
                btnChat.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        chatPanel.openTicketChat(t);
                        navTasks.setSelected(false);
                        navChat.setSelected(true);
                        switchTab(2, "Trao đổi 1-1", "Kênh hội thoại trực tiếp với giáo viên/học sinh báo sự cố.");
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

        TableColumn<IncidentTicket, Void> colResolve = new TableColumn<>("XỬ LÝ XONG");
        colResolve.setPrefWidth(125);
        colResolve.setCellFactory(col -> new TableCell<>() {
            private final Button btnDone = new Button("Hoàn tất");
            private final FontIcon checkIcon = ComponentFactory.createIcon("mdi2c-check-bold", 18, Color.web("#2F6F55"));

            {
                btnDone.getStyleClass().addAll("btn-success", "btn-sm");
                btnDone.setGraphic(ComponentFactory.createIcon("mdi2c-check-bold", 14, Color.WHITE));
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
                    if (t != null && Constants.STATUS_IN_PROGRESS.equalsIgnoreCase(t.getStatus())) {
                        setGraphic(btnDone);
                    } else {
                        setGraphic(checkIcon);
                    }
                }
            }
        });

        myTasksTable.getColumns().addAll(colCode, colRoom, colIncident, colStatus, colReporter, colChatAction, colResolve);
        myTasksTable.setRowFactory(tv -> {
            TableRow<IncidentTicket> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDetailDialog(row.getItem());
                }
            });
            return row;
        });
        myTasksTable.setItems(filteredMyTasks);
        VBox.setVgrow(myTasksTable, Priority.ALWAYS);

        // Empty placeholder
        VBox placeholder = new VBox(10);
        placeholder.setAlignment(Pos.CENTER);
        FontIcon emptyIcon = ComponentFactory.createIcon("mdi2w-wrench-outline", 44, Color.web("#D9CDB3"));
        Label lblEmpty = new Label("Chưa có nhiệm vụ nào");
        lblEmpty.getStyleClass().add("h2");
        Label lblEmptySub = new Label("Khi bạn tiếp nhận sự cố từ hàng đợi, nhiệm vụ sẽ hiển thị tại đây.");
        lblEmptySub.getStyleClass().add("muted");
        placeholder.getChildren().addAll(emptyIcon, lblEmpty, lblEmptySub);
        myTasksTable.setPlaceholder(placeholder);

        layout.getChildren().addAll(toolBar, myTasksTable);
        return layout;
    }

    private ToggleButton createSegment(String text, boolean selected) {
        ToggleButton btn = new ToggleButton(text);
        btn.getStyleClass().add("segment");
        btn.setToggleGroup(segGroup);
        btn.setSelected(selected);
        return btn;
    }

    private void applyTaskFilter(String status) {
        filteredMyTasks.setPredicate(t -> {
            if (t == null) return false;
            if (status == null) return true;
            return status.equalsIgnoreCase(t.getStatus());
        });
    }

    private void openDetailDialog(IncidentTicket ticket) {
        if (ticket == null) return;
        IncidentDetailDialog dialog = new IncidentDetailDialog(
                getScene().getWindow(),
                ticket,
                currentUser,
                t -> {
                    chatPanel.openTicketChat(t);
                    navQueue.setSelected(false);
                    navTasks.setSelected(false);
                    navChat.setSelected(true);
                    switchTab(2, "Trao đổi 1-1", "Kênh hội thoại trực tiếp với giáo viên/học sinh báo sự cố.");
                },
                t -> claimTicket(t.getTicketId()),
                this::promptResolveTicket,
                null,
                null
        );
        dialog.showAndWait();
    }

    private void claimTicket(int ticketId) {
        Packet packet = new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId);
        SocketClientManager.getInstance().sendPacket(packet);
    }

    private void promptResolveTicket(IncidentTicket ticket) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Nghiệm thu sự cố");
        ComponentFactory.applyTheme(dialog.getDialogPane());

        dialog.setHeaderText("Xác nhận hoàn thành xử lý sự cố " + ticket.getTicketCode());

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));
        Label lblPrompt = new Label("Ghi chú nghiệm thu:");
        lblPrompt.getStyleClass().add("field-label");

        TextArea taNote = new TextArea("Đã sửa chữa và kiểm tra an toàn.");
        taNote.setPrefRowCount(3);
        taNote.setWrapText(true);
        content.getChildren().addAll(lblPrompt, taNote);

        dialog.getDialogPane().setContent(content);

        ButtonType btnComplete = new ButtonType("Xác nhận hoàn tất", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnCancel, btnComplete);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(btnComplete);
        if (okBtn != null) okBtn.getStyleClass().setAll("btn-success");
        Button cancelBtn = (Button) dialog.getDialogPane().lookupButton(btnCancel);
        if (cancelBtn != null) cancelBtn.getStyleClass().setAll("btn-secondary");

        dialog.setResultConverter(b -> (b == btnComplete) ? taNote.getText().trim() : null);

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(note -> {
            String payload = String.format("STATUS_UPDATE|ticketId=%d|status=%s|note=%s",
                    ticket.getTicketId(), Constants.STATUS_RESOLVED, note.replace("|", "/"));
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_STATUS_UPDATE, payload));
            ComponentFactory.showToast(this, "Đã hoàn tất sự cố " + ticket.getTicketCode(), true);
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

    private long calculateMinutesPassed(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return 0;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            if (timeStr.contains(".")) {
                timeStr = timeStr.substring(0, timeStr.indexOf("."));
            }
            LocalDateTime created = LocalDateTime.parse(timeStr.trim(), formatter);
            return Math.max(0, Duration.between(created, LocalDateTime.now()).toMinutes());
        } catch (Exception e) {
            return 1;
        }
    }

    @Override
    public void onPacketReceived(Packet packet) {
        Platform.runLater(() -> {
            if (packet.getType() == Constants.TYPE_LIST_TICKETS_RESP) {
                if (packet.hasAttachment()) {
                    try {
                        String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                        Type listType = new TypeToken<List<IncidentTicket>>() {}.getType();
                        List<IncidentTicket> list = gson.fromJson(json, listType);

                        Map<String, String> params = packet.parseParams();
                        String scope = params.get("scope");

                        if ("DEPARTMENT".equalsIgnoreCase(scope)) {
                            if (list != null) {
                                // Chỉ hiển thị các sự cố PENDING chờ tiếp nhận trong Hàng đợi
                                List<IncidentTicket> pendingOnly = list.stream()
                                        .filter(t -> Constants.STATUS_PENDING.equalsIgnoreCase(t.getStatus()))
                                        .toList();
                                deptPendingList.setAll(pendingOnly);
                                lblQueueCount.setText(String.valueOf(pendingOnly.size()));
                            }
                        } else if ("MY".equalsIgnoreCase(scope)) {
                            if (list != null) {
                                myTasksList.setAll(list);
                                if (chatPanel != null) {
                                    chatPanel.setConversations(list);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

            } else if (packet.getType() == Constants.TYPE_CLAIM_RESP) {
                Map<String, String> params = packet.parseParams();
                String status = params.get("status");
                if ("OK".equalsIgnoreCase(status)) {
                    ComponentFactory.showToast(this, "Đã tiếp nhận sự cố thành công!", true);
                    loadDeptTickets();
                    loadMyTasks();
                    navQueue.setSelected(false);
                    navTasks.setSelected(true);
                    switchTab(1, "Nhiệm vụ của tôi", "Các ca bạn đang trực tiếp xử lý hoặc đã hoàn tất.");
                } else {
                    ComponentFactory.showToast(this, "Sự cố này đã được cán bộ khác tiếp nhận.", false);
                    loadDeptTickets();
                }

            } else if (packet.getType() == Constants.TYPE_DISPATCH_NOTIFY) {
                Map<String, String> params = packet.parseParams();
                String code = params.getOrDefault("code", "Mới");
                String room = params.getOrDefault("room", "");

                // 1. Toast cảnh báo tức thì khi có sự cố mới tới phòng ban
                ComponentFactory.showToast(this, "Có ca sự cố mới: " + code + (room.isEmpty() ? "" : " · Phòng " + room), true);

                // 2. Chèn trực tiếp vé mới vào đầu hàng đợi để cập nhật tức thì trên UI
                if (packet.hasAttachment()) {
                    try {
                        IncidentTicket newTicket = gson.fromJson(new String(packet.getAttachment(), StandardCharsets.UTF_8), IncidentTicket.class);
                        if (newTicket != null && Constants.STATUS_PENDING.equalsIgnoreCase(newTicket.getStatus())) {
                            boolean exists = deptPendingList.stream().anyMatch(t -> t.getTicketId() == newTicket.getTicketId());
                            if (!exists) {
                                deptPendingList.add(0, newTicket);
                                lblQueueCount.setText(String.valueOf(deptPendingList.size()));
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // 3. Tải lại danh sách từ server để đảm bảo dữ liệu đồng bộ
                loadDeptTickets();

            } else if (packet.getType() == Constants.TYPE_STATUS_BROADCAST) {
                loadDeptTickets();
                loadMyTasks();
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
