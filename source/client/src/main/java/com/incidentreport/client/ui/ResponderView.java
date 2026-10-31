package com.incidentreport.client.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.incidentreport.client.IncidentReportApp;
import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.client.util.SoundEffectManager;
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

    // Khung điều hướng Sidebar & collapsible state
    private boolean isSidebarCollapsed = false;
    private VBox sidebarContainer;
    private HBox brandBox;
    private StackPane brandCircle;
    private Button btnToggleSidebar;
    private VBox brandText;
    private VBox userDetailsBox;
    private Node userAvatarNode;
    private Button btnLogout;
    private HBox footerBox;
    private Label lblQText;
    private Region spQ;

    private final ToggleGroup navGroup = new ToggleGroup();
    private final ToggleButton navQueue = new ToggleButton();
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
        sidebarContainer = new VBox(8);
        sidebarContainer.getStyleClass().add("sidebar");
        sidebarContainer.setPadding(new Insets(16, 0, 0, 0));

        // Brand Area
        brandBox = new HBox(8);
        brandBox.setAlignment(Pos.CENTER_LEFT);
        brandBox.setPadding(new Insets(0, 12, 16, 12));

        brandCircle = new StackPane();
        brandCircle.setPrefSize(34, 34);
        brandCircle.setMinSize(34, 34);
        brandCircle.setMaxSize(34, 34);
        brandCircle.setStyle("-fx-border-color: -brass-500; -fx-border-width: 1.5; -fx-border-radius: 999; -fx-cursor: hand;");
        FontIcon schoolIcon = ComponentFactory.createIcon("mdi2s-school", 18, Color.web("#E3BE6A"));
        brandCircle.getChildren().add(schoolIcon);
        brandCircle.setOnMouseClicked(e -> toggleSidebar());

        brandText = new VBox(1);
        Label lblTitle = new Label("SIDS");
        lblTitle.getStyleClass().add("brand-title");
        Label lblSub = new Label("Sổ trực sự cố");
        lblSub.getStyleClass().add("brand-sub");
        brandText.getChildren().addAll(lblTitle, lblSub);
        HBox.setHgrow(brandText, Priority.ALWAYS);

        btnToggleSidebar = new Button();
        btnToggleSidebar.getStyleClass().setAll("btn-icon");
        btnToggleSidebar.setGraphic(ComponentFactory.createIcon("mdi2m-menu-open", 18, Color.web("#AFBBD6")));
        btnToggleSidebar.setTooltip(new Tooltip("Thu gọn menu (kéo thụt vào)"));
        btnToggleSidebar.setOnAction(e -> toggleSidebar());

        brandBox.getChildren().addAll(brandCircle, brandText, btnToggleSidebar);

        // Nav 1: Hàng đợi kèm số lượng (nav-count)
        navQueue.getStyleClass().add("nav-item");
        navQueue.setToggleGroup(navGroup);
        HBox queueGraphic = new HBox(10);
        queueGraphic.setAlignment(Pos.CENTER_LEFT);
        FontIcon inboxIcon = ComponentFactory.createIcon("mdi2i-inbox-arrow-down", 16, Color.web("#C9D3E8"));
        lblQText = new Label("Hàng đợi");
        lblQText.setStyle("-fx-text-fill: inherit;");
        spQ = new Region();
        HBox.setHgrow(spQ, Priority.ALWAYS);
        lblQueueCount.getStyleClass().add("nav-count");
        queueGraphic.getChildren().addAll(inboxIcon, lblQText, spQ, lblQueueCount);
        navQueue.setGraphic(queueGraphic);
        navQueue.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        navQueue.setTooltip(new Tooltip("Hàng đợi tiếp nhận"));

        // Nav 2: Nhiệm vụ của tôi
        navTasks.getStyleClass().add("nav-item");
        navTasks.setToggleGroup(navGroup);
        navTasks.setGraphic(ComponentFactory.createIcon("mdi2w-wrench", 16, Color.web("#C9D3E8")));
        navTasks.setTooltip(new Tooltip("Nhiệm vụ của tôi"));

        // Nav 3: Trao đổi 1-1
        navChat.getStyleClass().add("nav-item");
        navChat.setToggleGroup(navGroup);
        navChat.setGraphic(ComponentFactory.createIcon("mdi2m-message-text", 16, Color.web("#C9D3E8")));
        navChat.setTooltip(new Tooltip("Trao đổi 1-1"));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Footer User Bar
        footerBox = new HBox(8);
        footerBox.setAlignment(Pos.CENTER_LEFT);
        footerBox.getStyleClass().add("sidebar-footer");

        userAvatarNode = ComponentFactory.createInitialsAvatar(currentUser.getFullName(), false);
        userAvatarNode.setStyle(userAvatarNode.getStyle() + "; -fx-cursor: hand;");
        userAvatarNode.setOnMouseClicked(e -> ComponentFactory.showUserProfileDialog(getScene().getWindow(), currentUser));

        ComponentFactory.NameParts np = ComponentFactory.parseNameAndTitle(currentUser.getFullName());

        userDetailsBox = new VBox(2);
        userDetailsBox.setStyle("-fx-cursor: hand;");
        HBox.setHgrow(userDetailsBox, Priority.ALWAYS);
        userDetailsBox.setOnMouseClicked(e -> ComponentFactory.showUserProfileDialog(getScene().getWindow(), currentUser));

        Label lblName = new Label(np.displayName);
        lblName.getStyleClass().add("user-name");
        lblName.setTooltip(new Tooltip("Nhấp để xem hồ sơ: " + currentUser.getFullName()));

        Label deptBadge = ComponentFactory.createDepartmentBadge(currentUser.getSpecialty());
        userDetailsBox.getChildren().addAll(lblName, deptBadge);

        btnLogout = new Button();
        btnLogout.getStyleClass().setAll("btn-icon");
        FontIcon exitIcon = ComponentFactory.createIcon("mdi2l-logout", 18, Color.web("#AFBBD6"));
        btnLogout.setGraphic(exitIcon);
        btnLogout.setTooltip(new Tooltip("Đăng xuất"));
        btnLogout.setOnAction(e -> IncidentReportApp.logout());

        footerBox.getChildren().addAll(userAvatarNode, userDetailsBox, btnLogout);

        sidebarContainer.getChildren().addAll(brandBox, navQueue, navTasks, navChat, spacer, footerBox);
        return sidebarContainer;
    }

    private void toggleSidebar() {
        isSidebarCollapsed = !isSidebarCollapsed;
        applySidebarState();
    }

    private void applySidebarState() {
        if (isSidebarCollapsed) {
            sidebarContainer.setPrefWidth(64);
            sidebarContainer.setMinWidth(64);
            sidebarContainer.setMaxWidth(64);
            sidebarContainer.getStyleClass().add("sidebar-collapsed");

            // Khi thu gọn: Ẩn circle và text, hiển thị DUY NHẤT nút Hamburger Menu 38x38px căn giữa
            brandCircle.setVisible(false);
            brandCircle.setManaged(false);
            brandText.setVisible(false);
            brandText.setManaged(false);

            btnToggleSidebar.setVisible(true);
            btnToggleSidebar.setManaged(true);
            btnToggleSidebar.setPrefSize(38, 38);
            btnToggleSidebar.setMinSize(38, 38);
            btnToggleSidebar.setMaxSize(38, 38);
            btnToggleSidebar.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-background-radius: 6;");
            btnToggleSidebar.setGraphic(ComponentFactory.createIcon("mdi2m-menu", 22, Color.web("#E3BE6A")));
            btnToggleSidebar.setTooltip(new Tooltip("Mở rộng menu (kéo ra)"));

            brandBox.setAlignment(Pos.CENTER);
            brandBox.setPadding(new Insets(0, 0, 16, 0));

            userDetailsBox.setVisible(false);
            userDetailsBox.setManaged(false);

            lblQText.setVisible(false);
            lblQText.setManaged(false);
            spQ.setVisible(false);
            spQ.setManaged(false);
            lblQueueCount.setVisible(false);
            lblQueueCount.setManaged(false);
            navQueue.setAlignment(Pos.CENTER);
            navQueue.setTooltip(new Tooltip("Hàng đợi (" + lblQueueCount.getText() + " ca chờ)"));

            navTasks.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            navTasks.setAlignment(Pos.CENTER);

            navChat.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            navChat.setAlignment(Pos.CENTER);

            footerBox.getChildren().clear();
            footerBox.setAlignment(Pos.CENTER);
            footerBox.setPadding(new Insets(10, 4, 12, 4));

            VBox colBox = new VBox(8);
            colBox.setAlignment(Pos.CENTER);
            colBox.getChildren().addAll(userAvatarNode, btnLogout);
            footerBox.getChildren().add(colBox);
        } else {
            sidebarContainer.setPrefWidth(220);
            sidebarContainer.setMinWidth(220);
            sidebarContainer.setMaxWidth(260);
            sidebarContainer.getStyleClass().remove("sidebar-collapsed");

            // Khi mở rộng: Hiển thị đầy đủ Logo + Chữ + Nút thu gọn
            brandCircle.setVisible(true);
            brandCircle.setManaged(true);
            brandText.setVisible(true);
            brandText.setManaged(true);

            btnToggleSidebar.setVisible(true);
            btnToggleSidebar.setManaged(true);
            btnToggleSidebar.setPrefSize(28, 28);
            btnToggleSidebar.setMinSize(28, 28);
            btnToggleSidebar.setMaxSize(28, 28);
            btnToggleSidebar.setStyle("-fx-background-color: transparent;");
            btnToggleSidebar.setGraphic(ComponentFactory.createIcon("mdi2m-menu-open", 18, Color.web("#AFBBD6")));
            btnToggleSidebar.setTooltip(new Tooltip("Thu gọn menu (kéo thụt vào)"));

            brandBox.setAlignment(Pos.CENTER_LEFT);
            brandBox.setPadding(new Insets(0, 12, 16, 12));

            userDetailsBox.setVisible(true);
            userDetailsBox.setManaged(true);

            lblQText.setVisible(true);
            lblQText.setManaged(true);
            spQ.setVisible(true);
            spQ.setManaged(true);
            lblQueueCount.setVisible(true);
            lblQueueCount.setManaged(true);
            navQueue.setAlignment(Pos.CENTER_LEFT);
            navQueue.setTooltip(new Tooltip("Hàng đợi tiếp nhận"));

            navTasks.setContentDisplay(ContentDisplay.LEFT);
            navTasks.setAlignment(Pos.CENTER_LEFT);

            navChat.setContentDisplay(ContentDisplay.LEFT);
            navChat.setAlignment(Pos.CENTER_LEFT);

            footerBox.getChildren().clear();
            footerBox.setAlignment(Pos.CENTER_LEFT);
            footerBox.setPadding(new Insets(10, 12, 10, 12));
            footerBox.getChildren().addAll(userAvatarNode, userDetailsBox, btnLogout);
        }
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
        deptTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("MÃ");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setMinWidth(75);
        colCode.setPrefWidth(95);
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
        colRoom.setMinWidth(70);
        colRoom.setPrefWidth(85);
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
        colIncident.setMinWidth(140);
        colIncident.setPrefWidth(240);
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
                    lblC.setTooltip(new Tooltip(catName + (item.getDescription() != null ? "\n" + item.getDescription() : "")));
                    setGraphic(lblC);
                }
            }
        });

        TableColumn<IncidentTicket, String> colReporter = new TableColumn<>("NGƯỜI BÁO");
        colReporter.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getReporterName()));
        colReporter.setMinWidth(120);
        colReporter.setPrefWidth(180);
        colReporter.setCellFactory(col -> new ComponentFactory.PersonCell<>(ticket ->
                (ticket.getReporterPhone() != null && !ticket.getReporterPhone().isEmpty()) ? "ĐT: " + ticket.getReporterPhone() : ""
        ));

        // Cột thời gian chờ kèm chip cảnh báo (wait-chip / wait-chip-urgent)
        TableColumn<IncidentTicket, IncidentTicket> colWait = new TableColumn<>("CHỜ");
        colWait.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colWait.setMinWidth(80);
        colWait.setPrefWidth(95);
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
        colClaim.setMinWidth(170);
        colClaim.setPrefWidth(185);
        colClaim.setCellFactory(col -> new TableCell<>() {
            private final Button btnDetail = new Button("Xem");
            private final Button btn = new Button("Nhận sự cố");
            private final HBox btnBox = new HBox(6, btnDetail, btn);

            {
                btnDetail.getStyleClass().setAll("button", "btn-secondary", "btn-sm");
                btnDetail.setGraphic(ComponentFactory.createIcon("mdi2e-eye-outline", 13, Color.web("#14264A")));
                btnDetail.setStyle("-fx-font-size: 11.5px; -fx-padding: 3 8; -fx-cursor: hand;");
                btnDetail.setTooltip(new Tooltip("Xem chi tiết sự cố (hoặc nhấp đúp vào dòng)"));
                btnDetail.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) openDetailDialog(t);
                });

                btn.getStyleClass().setAll("button", "btn-success", "btn-sm");
                btn.setGraphic(ComponentFactory.createIcon("mdi2h-hand-pointing-right", 14, Color.WHITE));
                btn.setStyle("-fx-font-weight: bold; -fx-font-size: 11.5px; -fx-padding: 3 10; -fx-cursor: hand;");
                btn.setTooltip(new Tooltip("Tiếp nhận xử lý sự cố này"));
                btn.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        claimTicket(t.getTicketId());
                    }
                });

                btnBox.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    setGraphic(btnBox);
                    setAlignment(Pos.CENTER_LEFT);
                }
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
        myTasksTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("MÃ");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setMinWidth(75);
        colCode.setPrefWidth(95);
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
        colRoom.setMinWidth(70);
        colRoom.setPrefWidth(85);
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
        colIncident.setMinWidth(140);
        colIncident.setPrefWidth(240);
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
                    lblC.setTooltip(new Tooltip(catName + (item.getDescription() != null ? "\n" + item.getDescription() : "")));
                    setGraphic(lblC);
                }
            }
        });

        TableColumn<IncidentTicket, String> colStatus = new TableColumn<>("TRẠNG THÁI");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        colStatus.setCellFactory(col -> new ComponentFactory.StatusBadgeCell<>());
        colStatus.setMinWidth(120);
        colStatus.setPrefWidth(155);

        TableColumn<IncidentTicket, String> colReporter = new TableColumn<>("NGƯỜI BÁO");
        colReporter.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getReporterName()));
        colReporter.setMinWidth(120);
        colReporter.setPrefWidth(180);
        colReporter.setCellFactory(col -> new ComponentFactory.PersonCell<>(ticket ->
                (ticket.getReporterPhone() != null && !ticket.getReporterPhone().isEmpty()) ? "ĐT: " + ticket.getReporterPhone() : ""
        ));

        TableColumn<IncidentTicket, Void> colChatAction = new TableColumn<>("TRAO ĐỔI");
        colChatAction.setMinWidth(130);
        colChatAction.setPrefWidth(145);
        colChatAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnDetail = new Button("Xem");
            private final Button btnChat = new Button("Chat");
            private final HBox btnBox = new HBox(6, btnDetail, btnChat);

            {
                btnDetail.getStyleClass().setAll("button", "btn-secondary", "btn-sm");
                btnDetail.setGraphic(ComponentFactory.createIcon("mdi2e-eye-outline", 13, Color.web("#14264A")));
                btnDetail.setStyle("-fx-font-size: 11.5px; -fx-padding: 3 8; -fx-cursor: hand;");
                btnDetail.setTooltip(new Tooltip("Xem chi tiết sự cố (hoặc nhấp đúp vào dòng)"));
                btnDetail.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) openDetailDialog(t);
                });

                btnChat.getStyleClass().setAll("button", "btn-brass", "btn-sm");
                btnChat.setGraphic(ComponentFactory.createIcon("mdi2m-message-text-outline", 13, Color.web("#8A6218")));
                btnChat.setStyle("-fx-font-size: 11.5px; -fx-padding: 3 8; -fx-cursor: hand;");
                btnChat.setTooltip(new Tooltip("Nhắn tin trao đổi 1-1"));
                btnChat.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        chatPanel.openTicketChat(t);
                        navTasks.setSelected(false);
                        navChat.setSelected(true);
                        switchTab(2, "Trao đổi 1-1", "Kênh hội thoại trực tiếp với giáo viên/học sinh báo sự cố.");
                    }
                });

                btnBox.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    setGraphic(btnBox);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        TableColumn<IncidentTicket, Void> colResolve = new TableColumn<>("XỬ LÝ XONG");
        colResolve.setMinWidth(120);
        colResolve.setPrefWidth(135);
        colResolve.setCellFactory(col -> new TableCell<>() {
            private final Button btnDone = new Button("Hoàn thành");
            private final Label doneChip = new Label("Đã xong");

            {
                btnDone.getStyleClass().setAll("button", "btn-success", "btn-sm");
                btnDone.setGraphic(ComponentFactory.createIcon("mdi2c-check-circle-outline", 14, Color.WHITE));
                btnDone.setStyle("-fx-font-weight: bold; -fx-font-size: 11.5px; -fx-padding: 3 10; -fx-cursor: hand;");
                btnDone.setTooltip(new Tooltip("Báo cáo hoàn tất sự cố"));
                btnDone.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        promptResolveTicket(t);
                    }
                });

                doneChip.getStyleClass().addAll("badge", "badge-resolved");
                doneChip.setGraphic(ComponentFactory.createIcon("mdi2c-check", 13, Color.web("#2F6F55")));
                doneChip.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 3 8;");
                doneChip.setGraphicTextGap(4);
                doneChip.setTooltip(new Tooltip("Sự cố đã được xử lý hoàn tất"));
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
                    } else if (t != null && Constants.STATUS_RESOLVED.equalsIgnoreCase(t.getStatus())) {
                        setGraphic(doneChip);
                    } else {
                        setGraphic(null);
                    }
                    setAlignment(Pos.CENTER_LEFT);
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
        IncidentTicket target = deptPendingList.stream()
                .filter(t -> t.getTicketId() == ticketId)
                .findFirst().orElse(null);

        Dialog<ButtonType> confirmDialog = new Dialog<>();
        if (getScene() != null && getScene().getWindow() != null) {
            confirmDialog.initOwner(getScene().getWindow());
        }
        confirmDialog.setTitle("Tiếp nhận sự cố");
        ComponentFactory.applyTheme(confirmDialog.getDialogPane());
        confirmDialog.getDialogPane().setMinWidth(520);
        confirmDialog.getDialogPane().setPrefWidth(560);
        String code = (target != null && target.getTicketCode() != null) ? target.getTicketCode() : ("#" + ticketId);
        confirmDialog.setHeaderText("Xác nhận tiếp nhận xử lý " + code + "?");

        VBox box = new VBox(10);
        box.setPadding(new Insets(14, 18, 14, 18));
        box.setPrefWidth(520);
        box.setMaxWidth(Double.MAX_VALUE);
        if (target != null) {
            Label l1 = new Label("• Vị trí: Phòng " + target.getRoomLocation());
            l1.setStyle("-fx-font-weight: bold; -fx-font-size: 12.5px; -fx-text-fill: -text-900; -fx-text-overrun: clip;");
            l1.setWrapText(true);
            l1.setMinHeight(Region.USE_PREF_SIZE);
            l1.setMaxWidth(Double.MAX_VALUE);

            Label l2 = new Label("• Sự cố: " + (target.getCategoryName() != null ? target.getCategoryName() : target.getCategoryCode()));
            l2.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-700; -fx-text-overrun: clip;");
            l2.setWrapText(true);
            l2.setMinHeight(Region.USE_PREF_SIZE);
            l2.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().addAll(l1, l2);
        }
        Label l3 = new Label("Sau khi tiếp nhận, sự cố sẽ chuyển sang ĐANG XỬ LÝ và giao cho bạn.");
        l3.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-600; -fx-text-overrun: clip;");
        l3.setWrapText(true);
        l3.setMinHeight(Region.USE_PREF_SIZE);
        l3.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().add(l3);
        confirmDialog.getDialogPane().setContent(box);

        ButtonType btnClaim = new ButtonType("Tiếp nhận ngay", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("Để sau", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmDialog.getDialogPane().getButtonTypes().addAll(btnCancel, btnClaim);

        Button okBtn = (Button) confirmDialog.getDialogPane().lookupButton(btnClaim);
        if (okBtn != null) {
            ButtonBar.setButtonUniformSize(okBtn, false);
            okBtn.setMinWidth(Region.USE_PREF_SIZE);
            okBtn.getStyleClass().setAll("button", "btn-success");
            okBtn.setStyle("-fx-font-size: 12.5px; -fx-padding: 8 20; -fx-cursor: hand; -fx-min-width: -1; -fx-pref-width: -1; -fx-text-overrun: clip;");
        }
        Button cancelBtn = (Button) confirmDialog.getDialogPane().lookupButton(btnCancel);
        if (cancelBtn != null) {
            ButtonBar.setButtonUniformSize(cancelBtn, false);
            cancelBtn.setMinWidth(Region.USE_PREF_SIZE);
            cancelBtn.getStyleClass().setAll("button", "btn-secondary");
            cancelBtn.setStyle("-fx-font-size: 12.5px; -fx-padding: 8 20; -fx-cursor: hand; -fx-min-width: -1; -fx-pref-width: -1; -fx-text-overrun: clip;");
        }

        confirmDialog.setOnShown(e -> Platform.runLater(() -> {
            if (confirmDialog.getDialogPane().getScene() != null && confirmDialog.getDialogPane().getScene().getWindow() != null) {
                confirmDialog.getDialogPane().getScene().getWindow().sizeToScene();
            }
        }));

        Optional<ButtonType> ans = confirmDialog.showAndWait();
        if (!ans.isPresent() || ans.get() != btnClaim) {
            return;
        }

        Packet packet = new Packet(Constants.TYPE_CLAIM_TASK, "CLAIM_TASK|ticketId=" + ticketId);
        SocketClientManager.getInstance().sendPacket(packet);
    }

    private void promptResolveTicket(IncidentTicket ticket) {
        Dialog<String> dialog = new Dialog<>();
        if (getScene() != null && getScene().getWindow() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.setTitle("Nghiệm thu sự cố");
        ComponentFactory.applyTheme(dialog.getDialogPane());
        dialog.getDialogPane().setMinWidth(520);
        dialog.getDialogPane().setPrefWidth(560);

        dialog.setHeaderText("Xác nhận hoàn thành xử lý sự cố " + ticket.getTicketCode());

        VBox content = new VBox(10);
        content.setPadding(new Insets(14, 18, 14, 18));
        content.setPrefWidth(520);
        content.setMaxWidth(Double.MAX_VALUE);
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
        if (okBtn != null) {
            ButtonBar.setButtonUniformSize(okBtn, false);
            okBtn.setMinWidth(Region.USE_PREF_SIZE);
            okBtn.getStyleClass().setAll("button", "btn-success");
            okBtn.setStyle("-fx-font-size: 12.5px; -fx-padding: 8 20; -fx-cursor: hand; -fx-min-width: -1; -fx-pref-width: -1; -fx-text-overrun: clip;");
        }
        Button cancelBtn = (Button) dialog.getDialogPane().lookupButton(btnCancel);
        if (cancelBtn != null) {
            ButtonBar.setButtonUniformSize(cancelBtn, false);
            cancelBtn.setMinWidth(Region.USE_PREF_SIZE);
            cancelBtn.getStyleClass().setAll("button", "btn-secondary");
            cancelBtn.setStyle("-fx-font-size: 12.5px; -fx-padding: 8 20; -fx-cursor: hand; -fx-min-width: -1; -fx-pref-width: -1; -fx-text-overrun: clip;");
        }

        dialog.setOnShown(e -> Platform.runLater(() -> {
            if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
            }
        }));

        dialog.setResultConverter(b -> (b == btnComplete) ? taNote.getText().trim() : null);

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(note -> {
            String payload = String.format("STATUS_UPDATE|ticketId=%d|status=%s|note=%s",
                    ticket.getTicketId(), Constants.STATUS_RESOLVED, note.replace("|", "/"));
            SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_STATUS_UPDATE, payload));
            SoundEffectManager.playSuccess();
            ComponentFactory.showToast(this, "Đã hoàn tất sự cố " + ticket.getTicketCode() + " thành công!", true);
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
                    SoundEffectManager.playSuccess();
                    ComponentFactory.showToast(this, "Đã tiếp nhận sự cố thành công!", true);
                    loadDeptTickets();
                    loadMyTasks();
                    navQueue.setSelected(false);
                    navTasks.setSelected(true);
                    switchTab(1, "Nhiệm vụ của tôi", "Các ca bạn đang trực tiếp xử lý hoặc đã hoàn tất.");
                } else {
                    SoundEffectManager.playWarning();
                    ComponentFactory.showToast(this, "Sự cố này đã được cán bộ khác tiếp nhận.", false);
                    ComponentFactory.showErrorAlert(getScene() != null ? getScene().getWindow() : null, "Tiếp nhận không thành công", "Sự cố đã được xử lý", "Sự cố này vừa được một cán bộ khác tiếp nhận trước bạn.");
                    loadDeptTickets();
                }

            } else if (packet.getType() == Constants.TYPE_ERROR) {
                Map<String, String> p = packet.parseParams();
                String msg = p.getOrDefault("message", "Thao tác không thành công!");
                SoundEffectManager.playWarning();
                ComponentFactory.showToast(this, "Lỗi từ máy chủ: " + msg, false);
                ComponentFactory.showErrorAlert(getScene() != null ? getScene().getWindow() : null, "Từ chối thao tác", "Không thể thực hiện yêu cầu", msg);
                loadDeptTickets();
                loadMyTasks();

            } else if (packet.getType() == Constants.TYPE_DISPATCH_NOTIFY) {
                Map<String, String> params = packet.parseParams();
                String code = params.getOrDefault("code", "Mới");
                String room = params.getOrDefault("room", "");
                String cat = params.getOrDefault("category", "Sự cố");
                String desc = params.getOrDefault("desc", "");
                String assignedByAdmin = params.getOrDefault("assignedByAdmin", "false");
                String note = params.getOrDefault("note", "");

                // 1. Phát chuông cảnh báo nổi bật & Banner thông báo thời gian thực không thể bỏ lỡ
                SoundEffectManager.playNotification();

                if ("true".equalsIgnoreCase(assignedByAdmin)) {
                    String title = "🎯 BẠN ĐƯỢC CHỈ ĐỊNH XỬ LÝ CA MỚI!";
                    String msg = "Ban Giám Hiệu chỉ định bạn phụ trách ca " + code + (room.isEmpty() ? "" : " tại Phòng " + room) + ".\n" + (note.isEmpty() ? "Vui lòng tiếp nhận và xử lý ngay." : "Ghi chú BGH: " + note);
                    ComponentFactory.showUrgentNotification(this, title, msg, "ASSIGN");
                } else {
                    String title = "🚨 CÓ SỰ CỐ MỚI CẦN GIẢI QUYẾT!";
                    String msg = "Ca " + code + (room.isEmpty() ? "" : " · Phòng " + room) + " (" + cat + ")" + (desc.isEmpty() ? "" : "\nMô tả: " + desc);
                    ComponentFactory.showUrgentNotification(this, title, msg, "DISPATCH");
                }

                // 2. Chèn trực tiếp vé mới vào đầu hàng đợi để cập nhật tức thì trên UI
                if (packet.hasAttachment()) {
                    try {
                        IncidentTicket newTicket = gson.fromJson(new String(packet.getAttachment(), StandardCharsets.UTF_8), IncidentTicket.class);
                        if (newTicket != null) {
                            if (Constants.STATUS_PENDING.equalsIgnoreCase(newTicket.getStatus())) {
                                boolean exists = deptPendingList.stream().anyMatch(t -> t.getTicketId() == newTicket.getTicketId());
                                if (!exists) {
                                    deptPendingList.add(0, newTicket);
                                    lblQueueCount.setText(String.valueOf(deptPendingList.size()));
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // 3. Tải lại danh sách từ server để đảm bảo dữ liệu đồng bộ
                loadDeptTickets();
                loadMyTasks();

            } else if (packet.getType() == Constants.TYPE_STATUS_BROADCAST) {
                Map<String, String> p = packet.parseParams();
                String st = p.get("status");
                String staff = p.getOrDefault("staffName", "");
                String code = p.getOrDefault("ticketCode", "");
                String room = p.getOrDefault("room", "");

                if (Constants.STATUS_IN_PROGRESS.equalsIgnoreCase(st)) {
                    if (currentUser == null || !staff.equals(currentUser.getFullName())) {
                        SoundEffectManager.playNotification();
                        ComponentFactory.showToast(this, "🤝 Cán bộ " + staff + " đã tiếp nhận ca " + code + (room.isEmpty() ? "" : " (Phòng " + room + ")"), true);
                    }
                } else if (Constants.STATUS_RESOLVED.equalsIgnoreCase(st)) {
                    if (currentUser == null || !staff.equals(currentUser.getFullName())) {
                        SoundEffectManager.playSuccess();
                        ComponentFactory.showToast(this, "🎉 Ca " + code + (room.isEmpty() ? "" : " (Phòng " + room + ")") + " đã hoàn tất bởi " + staff, true);
                    }
                } else if (Constants.STATUS_CANCELLED.equalsIgnoreCase(st)) {
                    SoundEffectManager.playWarning();
                    ComponentFactory.showToast(this, "Sự cố " + code + " đã bị hủy bởi người tạo.", false);
                }

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
