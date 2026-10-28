package com.incidentreport.client.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.incidentreport.client.IncidentReportApp;
import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentCategory;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phân hệ Người báo cáo (ReporterView) tái thiết kế theo DESIGN_SYSTEM.md:
 * - Khung Sidebar trái 232px mực xanh (#14264A), điều khiển TabPane ẩn (hidden-tabs).
 * - Báo sự cố dạng Card: 5 thẻ ToggleButton loại sự cố, kéo thả ảnh hiện trường (Dropzone).
 * - Bảng sự cố thoáng đãng, gộp cột, dùng 3 TableCell chuẩn (StatusBadge, DeptBadge, PersonCell).
 * - Nút Hủy dạng icon ghost, hộp thoại xác nhận phong cách "Giấy - Mực - Đồng".
 */
public class ReporterView extends StackPane implements SocketClientManager.PacketListener {

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
    private HBox footerBox;

    private final ToggleGroup navGroup = new ToggleGroup();
    private final ToggleButton navNew = new ToggleButton("Báo sự cố mới");
    private final ToggleButton navList = new ToggleButton("Sự cố của tôi");
    private final ToggleButton navChat = new ToggleButton("Trao đổi 1-1");

    // Topbar header
    private final Label lblPageTitle = new Label("Báo sự cố mới");
    private final Label lblPageSub = new Label("Gửi thông tin hư hỏng, sự vụ để nhà trường điều phối.");
    private final HBox connIndicator = ComponentFactory.createConnectionIndicator(true);

    // TabPane ẩn
    private final TabPane tabPane = new TabPane();
    private ChatPanel chatPanel;

    // Form controls (Tab 1)
    private final TextField txtRoom = new TextField();
    private final ToggleGroup catTileGroup = new ToggleGroup();
    private IncidentCategory selectedCategory = null;
    private final TextArea taDescription = new TextArea();
    private final Button btnSubmit = new Button("GỬI BÁO CÁO");

    // Ảnh hiện trường (Tab 1)
    private final StackPane dropZone = new StackPane();
    private final ImageView imgPreview = new ImageView();
    private final VBox dropPlaceholder = new VBox(6);
    private final HBox imgMetaBar = new HBox(8);
    private final Label lblImgFileName = new Label();
    private final Label lblImgSize = new Label();
    private final Button btnRemoveImg = new Button();
    private byte[] chosenImageBytes = new byte[0];

    // Table controls (Tab 2)
    private final ObservableList<IncidentTicket> ticketList = FXCollections.observableArrayList();
    private final FilteredList<IncidentTicket> filteredTickets = new FilteredList<>(ticketList, t -> true);
    private final TableView<IncidentTicket> ticketTable = new TableView<>();
    private final ToggleGroup segGroup = new ToggleGroup();

    public ReporterView(User user) {
        this.currentUser = user;
        initShell();
        SocketClientManager.getInstance().addListener(this);
        loadMyTickets();
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
        Tab tab1 = new Tab("New", createSubmitIncidentTab());
        Tab tab2 = new Tab("List", createMyTicketsTab());
        chatPanel = new ChatPanel(currentUser);
        Tab tab3 = new Tab("Chat", chatPanel);

        tabPane.getTabs().addAll(tab1, tab2, tab3);
        tabPane.getStyleClass().add("hidden-tabs");
        contentPane.setCenter(tabPane);

        rootPane.setCenter(contentPane);
        getChildren().add(rootPane);

        // Kết nối Navigation
        navNew.setOnAction(e -> switchTab(0, "Báo sự cố mới", "Gửi thông tin hư hỏng, sự vụ để nhà trường điều phối."));
        navList.setOnAction(e -> switchTab(1, "Sự cố của tôi", "Theo dõi tiến độ xử lý các sự cố bạn đã gửi."));
        navChat.setOnAction(e -> switchTab(2, "Trao đổi 1-1", "Kênh hội thoại trực tiếp giữa người báo và cán bộ tiếp nhận."));
        navNew.setSelected(true);
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

        // Nav items
        navNew.getStyleClass().add("nav-item");
        navNew.setToggleGroup(navGroup);
        navNew.setGraphic(ComponentFactory.createIcon("mdi2f-file-plus", 16, Color.web("#C9D3E8")));
        navNew.setTooltip(new Tooltip("Báo sự cố mới"));

        navList.getStyleClass().add("nav-item");
        navList.setToggleGroup(navGroup);
        navList.setGraphic(ComponentFactory.createIcon("mdi2f-format-list-bulleted", 16, Color.web("#C9D3E8")));
        navList.setTooltip(new Tooltip("Sự cố của tôi"));

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

        Node avatar = ComponentFactory.createInitialsAvatar(currentUser.getFullName(), true);
        ComponentFactory.NameParts np = ComponentFactory.parseNameAndTitle(currentUser.getFullName());

        userDetailsBox = new VBox(2);
        HBox.setHgrow(userDetailsBox, Priority.ALWAYS);

        Label lblName = new Label(np.displayName);
        lblName.getStyleClass().add("user-name");
        lblName.setTooltip(new Tooltip(currentUser.getFullName()));

        String subRole = np.roleOrTitle != null ? np.roleOrTitle : "Người báo cáo";
        Label lblRole = new Label(subRole);
        lblRole.getStyleClass().add("user-role");
        userDetailsBox.getChildren().addAll(lblName, lblRole);

        Button btnLogout = new Button();
        btnLogout.getStyleClass().setAll("btn-icon");
        FontIcon exitIcon = ComponentFactory.createIcon("mdi2l-logout", 18, Color.web("#AFBBD6"));
        btnLogout.setGraphic(exitIcon);
        btnLogout.setTooltip(new Tooltip("Đăng xuất"));
        btnLogout.setOnAction(e -> IncidentReportApp.logout());

        footerBox.getChildren().addAll(avatar, userDetailsBox, btnLogout);

        sidebarContainer.getChildren().addAll(brandBox, navNew, navList, navChat, spacer, footerBox);
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

            navNew.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            navNew.setAlignment(Pos.CENTER);

            navList.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            navList.setAlignment(Pos.CENTER);

            navChat.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            navChat.setAlignment(Pos.CENTER);

            footerBox.setAlignment(Pos.CENTER);
            footerBox.setPadding(new Insets(10, 4, 10, 4));
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

            navNew.setContentDisplay(ContentDisplay.LEFT);
            navNew.setAlignment(Pos.CENTER_LEFT);

            navList.setContentDisplay(ContentDisplay.LEFT);
            navList.setAlignment(Pos.CENTER_LEFT);

            navChat.setContentDisplay(ContentDisplay.LEFT);
            navChat.setAlignment(Pos.CENTER_LEFT);

            footerBox.setAlignment(Pos.CENTER_LEFT);
            footerBox.setPadding(new Insets(10, 12, 10, 12));
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
    // TAB 1: BÁO SỰ CỐ MỚI (DESIGN_SYSTEM.md Mục 5.2)
    // =========================================================================
    private Node createSubmitIncidentTab() {
        HBox mainBox = new HBox(20);
        mainBox.getStyleClass().add("page-body");
        mainBox.setAlignment(Pos.TOP_LEFT);

        // 1. Thẻ thông tin sự cố (Left Card)
        VBox leftCard = new VBox(14);
        leftCard.getStyleClass().add("card");
        HBox.setHgrow(leftCard, Priority.ALWAYS);

        // Vị trí
        Label lblRoom = new Label("Vị trí (Phòng học / Khu vực chức năng) *");
        lblRoom.getStyleClass().add("field-label");

        txtRoom.setPromptText("VD: A101, B204, LAB-LY, SAN-BONG...");

        // Gợi ý nhanh
        HBox roomChips = new HBox(6);
        roomChips.setAlignment(Pos.CENTER_LEFT);
        Label lblChipTitle = new Label("Gợi ý nhanh:");
        lblChipTitle.getStyleClass().addAll("caption", "muted");
        roomChips.getChildren().add(lblChipTitle);
        for (String r : new String[]{"A101", "A201", "B204", "LAB-LY", "SAN-BONG"}) {
            Button chip = new Button(r);
            chip.getStyleClass().add("chip");
            chip.setOnAction(e -> txtRoom.setText(r));
            roomChips.getChildren().add(chip);
        }

        // Loại sự cố (5 ToggleButton thẻ)
        Label lblCat = new Label("Loại sự cố (Tự động chuyển đúng phòng ban) *");
        lblCat.getStyleClass().add("field-label");

        VBox tilesBox = createCategoryTiles();

        // Mô tả
        Label lblDesc = new Label("Mô tả chi tiết sự cố *");
        lblDesc.getStyleClass().add("field-label");
        taDescription.setPromptText("Mô tả ngắn gọn dấu hiệu sự cố (VD: quạt trần rung lắc mạnh, ổ điện tóe lửa, học sinh sốt cao...)...");
        taDescription.setPrefRowCount(4);
        taDescription.setWrapText(true);

        // Hàng nút dưới cùng
        HBox actionRow = new HBox(10);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        Button btnReset = new Button("Xóa form");
        btnReset.getStyleClass().add("btn-secondary");
        btnReset.setOnAction(e -> resetForm());

        btnSubmit.getStyleClass().addAll("btn-primary");
        btnSubmit.setPrefHeight(40);
        btnSubmit.setOnAction(e -> doSubmitIncident());

        actionRow.getChildren().addAll(btnReset, btnSubmit);

        leftCard.getChildren().addAll(lblRoom, txtRoom, roomChips, lblCat, tilesBox, lblDesc, taDescription, actionRow);

        // 2. Thẻ Ảnh hiện trường (Right Card 320px)
        VBox rightCard = new VBox(12);
        rightCard.getStyleClass().add("card");
        rightCard.setPrefWidth(320);
        rightCard.setMinWidth(320);
        rightCard.setMaxWidth(320);

        Label lblImgTitle = new Label("ẢNH HIỆN TRƯỜNG");
        lblImgTitle.getStyleClass().add("caption");

        setupDropZone();

        rightCard.getChildren().addAll(lblImgTitle, dropZone, imgMetaBar);

        mainBox.getChildren().addAll(leftCard, rightCard);

        ScrollPane scroll = new ScrollPane(mainBox);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private VBox createCategoryTiles() {
        VBox box = new VBox(8);

        IncidentCategory[] cats = new IncidentCategory[]{
                new IncidentCategory("FACILITY", "Hỏng thiết bị lớp học (Quạt, Đèn, Máy chiếu)", Constants.SPECIALTY_TECHNICAL),
                new IncidentCategory("WATER", "Sự cố Điện nước (Vỡ ống nước, Mất nước)", Constants.SPECIALTY_TECHNICAL),
                new IncidentCategory("ELECTRIC", "Sự cố Điện nguy hiểm (Chập cầu dao, Rò điện)", Constants.SPECIALTY_TECHNICAL),
                new IncidentCategory("MEDICAL", "Cấp cứu Y tế (Học sinh ngất, Chấn thương, Sốt)", Constants.SPECIALTY_MEDICAL),
                new IncidentCategory("SECURITY", "An ninh Trật tự (Xô xát, Người lạ đột nhập)", Constants.SPECIALTY_SECURITY)
        };

        for (IncidentCategory cat : cats) {
            ToggleButton tile = new ToggleButton();
            tile.getStyleClass().add("type-tile");
            tile.setToggleGroup(catTileGroup);

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);

            String iconLiteral = "mdi2w-wrench";
            if ("WATER".equals(cat.getCategoryCode())) iconLiteral = "mdi2w-water";
            else if ("ELECTRIC".equals(cat.getCategoryCode())) iconLiteral = "mdi2f-flash";
            else if ("MEDICAL".equals(cat.getCategoryCode())) iconLiteral = "mdi2m-medical-bag";
            else if ("SECURITY".equals(cat.getCategoryCode())) iconLiteral = "mdi2s-shield-account";

            FontIcon icon = ComponentFactory.createIcon(iconLiteral, 18, Color.web("#14264A"));
            Label lblName = new Label(cat.getCategoryName());
            lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 12.5px; -fx-text-fill: -text-900;");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Label badge = ComponentFactory.createDepartmentBadge(cat.getRequiredSpecialty());

            row.getChildren().addAll(icon, lblName, sp, badge);
            tile.setGraphic(row);
            tile.setMaxWidth(Double.MAX_VALUE);

            tile.setOnAction(e -> {
                if (tile.isSelected()) {
                    selectedCategory = cat;
                    updateSubmitButtonState();
                }
            });

            box.getChildren().add(tile);
        }

        // Chọn cái đầu tiên mặc định
        if (!catTileGroup.getToggles().isEmpty()) {
            catTileGroup.getToggles().get(0).setSelected(true);
            selectedCategory = cats[0];
            updateSubmitButtonState();
        }

        return box;
    }

    private void updateSubmitButtonState() {
        if (selectedCategory != null &&
                (Constants.SPECIALTY_MEDICAL.equalsIgnoreCase(selectedCategory.getRequiredSpecialty())
                        || Constants.SPECIALTY_SECURITY.equalsIgnoreCase(selectedCategory.getRequiredSpecialty()))) {
            btnSubmit.getStyleClass().setAll("btn-danger-solid");
            btnSubmit.setText("GỬI BÁO CÁO KHẨN");
        } else {
            btnSubmit.getStyleClass().setAll("btn-primary");
            btnSubmit.setText("GỬI BÁO CÁO");
        }
    }

    private void setupDropZone() {
        dropZone.setPrefSize(280, 200);
        dropZone.setMinSize(280, 200);
        dropZone.getStyleClass().add("dropzone");
        dropZone.setCursor(javafx.scene.Cursor.HAND);

        // Placeholder ban đầu
        dropPlaceholder.setAlignment(Pos.CENTER);
        FontIcon imgIcon = ComponentFactory.createIcon("mdi2i-image-plus", 32, Color.web("#8C8677"));
        Label lblDropText = new Label("Kéo thả ảnh vào đây");
        lblDropText.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: -text-600;");
        Label lblDropSub = new Label("hoặc bấm để chọn file");
        lblDropSub.getStyleClass().addAll("muted");
        lblDropSub.setStyle("-fx-font-size: 11.5px;");
        Label lblDropNote = new Label("JPG/PNG · tối đa 5 MB");
        lblDropNote.getStyleClass().addAll("hint");

        dropPlaceholder.getChildren().addAll(imgIcon, lblDropText, lblDropSub, lblDropNote);

        imgPreview.setFitWidth(260);
        imgPreview.setFitHeight(180);
        imgPreview.setPreserveRatio(true);
        imgPreview.setVisible(false);

        dropZone.getChildren().addAll(dropPlaceholder, imgPreview);

        // Bấm chuột để chọn file
        dropZone.setOnMouseClicked(e -> chooseImage());

        // Kéo thả file ảnh
        dropZone.setOnDragOver(event -> {
            if (event.getGestureSource() != dropZone && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
                if (!dropZone.getStyleClass().contains("dropzone-active")) {
                    dropZone.getStyleClass().add("dropzone-active");
                }
            }
            event.consume();
        });

        dropZone.setOnDragExited(event -> {
            dropZone.getStyleClass().remove("dropzone-active");
            event.consume();
        });

        dropZone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                File file = db.getFiles().get(0);
                processImageFile(file);
                success = true;
            }
            event.setDropCompleted(success);
            dropZone.getStyleClass().remove("dropzone-active");
            event.consume();
        });

        // Thanh thông tin ảnh dưới dropzone
        imgMetaBar.setAlignment(Pos.CENTER_LEFT);
        imgMetaBar.setVisible(false);
        imgMetaBar.setManaged(false);

        lblImgFileName.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: -text-900;");
        lblImgSize.getStyleClass().add("muted");
        lblImgSize.setStyle("-fx-font-size: 11px;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        btnRemoveImg.getStyleClass().setAll("btn-icon");
        btnRemoveImg.setGraphic(ComponentFactory.createIcon("mdi2c-close", 16, Color.web("#A8402F")));
        btnRemoveImg.setTooltip(new Tooltip("Xóa ảnh"));
        btnRemoveImg.setOnAction(e -> clearImage());

        imgMetaBar.getChildren().addAll(lblImgFileName, lblImgSize, sp, btnRemoveImg);
    }

    private void chooseImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh hiện trường sự cố");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh JPG/PNG", "*.jpg", "*.jpeg", "*.png"));
        File file = fc.showOpenDialog(getScene().getWindow());
        if (file != null) {
            processImageFile(file);
        }
    }

    private void processImageFile(File file) {
        if (!file.getName().toLowerCase().matches(".*\\.(jpg|jpeg|png)$")) {
            ComponentFactory.showToast(this, "Chỉ chấp nhận định dạng ảnh JPG hoặc PNG!", false);
            return;
        }

        if (file.length() > 5 * 1024 * 1024) {
            ComponentFactory.showToast(this, "Dung lượng ảnh vượt quá 5MB!", false);
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            chosenImageBytes = fis.readAllBytes();
            imgPreview.setImage(new Image(new ByteArrayInputStream(chosenImageBytes)));
            imgPreview.setVisible(true);
            dropPlaceholder.setVisible(false);

            lblImgFileName.setText(file.getName());
            lblImgSize.setText(String.format("· %.1f MB", file.length() / (1024.0 * 1024.0)));
            imgMetaBar.setVisible(true);
            imgMetaBar.setManaged(true);
        } catch (Exception ex) {
            ComponentFactory.showToast(this, "Lỗi đọc file ảnh: " + ex.getMessage(), false);
        }
    }

    private void clearImage() {
        chosenImageBytes = new byte[0];
        imgPreview.setImage(null);
        imgPreview.setVisible(false);
        dropPlaceholder.setVisible(true);
        imgMetaBar.setVisible(false);
        imgMetaBar.setManaged(false);
    }

    private void resetForm() {
        txtRoom.clear();
        taDescription.clear();
        clearImage();
    }

    private void doSubmitIncident() {
        String room = txtRoom.getText().trim();
        if (room.isEmpty()) {
            ComponentFactory.showToast(this, "Vui lòng nhập vị trí phòng học!", false);
            txtRoom.requestFocus();
            return;
        }

        if (selectedCategory == null) {
            ComponentFactory.showToast(this, "Vui lòng chọn loại sự cố!", false);
            return;
        }

        String desc = taDescription.getText().trim();
        if (desc.isEmpty()) {
            ComponentFactory.showToast(this, "Vui lòng nhập mô tả sự cố!", false);
            taDescription.requestFocus();
            return;
        }

        String headerPayload = String.format("INCIDENT|room=%s|category=%s|desc=%s",
                room.toUpperCase(), selectedCategory.getCategoryCode(), desc.replace("|", "/"));

        Packet packet = new Packet(Constants.TYPE_SUBMIT_INCIDENT, headerPayload, chosenImageBytes);
        SocketClientManager.getInstance().sendPacket(packet);

        resetForm();
    }

    // =========================================================================
    // TAB 2: SỰ CỐ CỦA TÔI (DESIGN_SYSTEM.md Mục 5.3)
    // =========================================================================
    private Node createMyTicketsTab() {
        VBox layout = new VBox(12);
        layout.getStyleClass().add("page-body");

        // Segmented filter bar
        HBox toolBar = new HBox(12);
        toolBar.setAlignment(Pos.CENTER_LEFT);

        HBox segGroupContainer = new HBox(2);
        segGroupContainer.getStyleClass().add("segment-group");

        ToggleButton segAll = createSegment("Tất cả", true);
        ToggleButton segPending = createSegment("Chờ xử lý", false);
        ToggleButton segProgress = createSegment("Đang xử lý", false);
        ToggleButton segResolved = createSegment("Đã hoàn tất", false);

        segAll.setOnAction(e -> applyFilter(null));
        segPending.setOnAction(e -> applyFilter(Constants.STATUS_PENDING));
        segProgress.setOnAction(e -> applyFilter(Constants.STATUS_IN_PROGRESS));
        segResolved.setOnAction(e -> applyFilter(Constants.STATUS_RESOLVED));

        segGroupContainer.getChildren().addAll(segAll, segPending, segProgress, segResolved);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRefresh = new Button("Làm mới");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setGraphic(ComponentFactory.createIcon("mdi2r-refresh", 16, Color.web("#14264A")));
        btnRefresh.setOnAction(e -> loadMyTickets());

        toolBar.getChildren().addAll(segGroupContainer, sp, btnRefresh);

        // Bảng dữ liệu gộp cột
        ticketTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("MÃ");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setMinWidth(85);
        colCode.setPrefWidth(95);
        colCode.setMaxWidth(110);
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
        colRoom.setMinWidth(75);
        colRoom.setPrefWidth(85);
        colRoom.setMaxWidth(100);
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
        colIncident.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(d.getValue()));
        colIncident.setMinWidth(180);
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
                    setGraphic(lblC);
                }
            }
        });

        TableColumn<IncidentTicket, String> colDept = new TableColumn<>("PHÒNG BAN");
        colDept.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTargetDepartment()));
        colDept.setCellFactory(col -> new ComponentFactory.DeptBadgeCell<>());
        colDept.setMinWidth(105);
        colDept.setPrefWidth(115);
        colDept.setMaxWidth(125);

        TableColumn<IncidentTicket, String> colStatus = new TableColumn<>("TRẠNG THÁI");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        colStatus.setCellFactory(col -> new ComponentFactory.StatusBadgeCell<>());
        colStatus.setMinWidth(150);
        colStatus.setPrefWidth(160);
        colStatus.setMaxWidth(180);

        TableColumn<IncidentTicket, String> colStaff = new TableColumn<>("CÁN BỘ XỬ LÝ");
        colStaff.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAssignedStaffName()));
        colStaff.setCellFactory(col -> new ComponentFactory.PersonCell<>());
        colStaff.setMinWidth(160);
        colStaff.setPrefWidth(200);

        TableColumn<IncidentTicket, String> colTime = new TableColumn<>("THỜI GIAN");
        colTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCreatedAt()));
        colTime.setMinWidth(110);
        colTime.setPrefWidth(120);
        colTime.setMaxWidth(135);
        colTime.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(ComponentFactory.formatShortTime(item));
                    setStyle("-fx-font-size: 12px; -fx-text-fill: -text-600;");
                    setTooltip(new Tooltip("Thời gian tạo: " + item));
                }
            }
        });

        TableColumn<IncidentTicket, Void> colActions = new TableColumn<>("THAO TÁC");
        colActions.setMinWidth(125);
        colActions.setPrefWidth(130);
        colActions.setMaxWidth(140);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnDetail = new Button();
            private final Button btnChat = new Button();
            private final Button btnCancel = new Button();
            private final HBox btnBox = new HBox(6, btnDetail, btnChat, btnCancel);

            {
                btnDetail.getStyleClass().setAll("btn-icon");
                btnDetail.setGraphic(ComponentFactory.createIcon("mdi2e-eye-outline", 16, Color.web("#14264A")));
                btnDetail.setTooltip(new Tooltip("Xem chi tiết sự cố (hoặc nhấp đúp vào dòng)"));
                btnDetail.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) openDetailDialog(t);
                });

                btnChat.getStyleClass().setAll("btn-icon");
                btnChat.setGraphic(ComponentFactory.createIcon("mdi2m-message-text-outline", 16, Color.web("#14264A")));
                btnChat.setTooltip(new Tooltip("Nhắn tin trao đổi 1-1"));

                btnCancel.getStyleClass().setAll("btn-icon");
                btnCancel.setGraphic(ComponentFactory.createIcon("mdi2c-close-circle-outline", 16, Color.web("#A8402F")));
                btnCancel.setTooltip(new Tooltip("Hủy sự cố này"));

                btnChat.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        chatPanel.openTicketChat(t);
                        navList.setSelected(false);
                        navChat.setSelected(true);
                        switchTab(2, "Trao đổi 1-1", "Kênh hội thoại trực tiếp giữa người báo và cán bộ tiếp nhận.");
                    }
                });

                btnCancel.setOnAction(e -> {
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    if (t != null) {
                        confirmCancelTicket(t);
                    }
                });

                btnBox.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    btnBox.getChildren().clear();
                    btnBox.getChildren().addAll(btnDetail, btnChat);
                    IncidentTicket t = getTableView().getItems().get(getIndex());
                    boolean canCancel = (t != null && Constants.STATUS_PENDING.equalsIgnoreCase(t.getStatus()));
                    if (canCancel) {
                        btnBox.getChildren().add(btnCancel);
                    }
                    setGraphic(btnBox);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        ticketTable.getColumns().addAll(colCode, colRoom, colIncident, colDept, colStatus, colStaff, colTime, colActions);
        ticketTable.setRowFactory(tv -> {
            TableRow<IncidentTicket> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDetailDialog(row.getItem());
                }
            });
            return row;
        });
        ticketTable.setItems(filteredTickets);
        VBox.setVgrow(ticketTable, Priority.ALWAYS);

        // Empty placeholder
        VBox placeholder = new VBox(10);
        placeholder.setAlignment(Pos.CENTER);
        FontIcon emptyIcon = ComponentFactory.createIcon("mdi2c-clipboard-text-outline", 44, Color.web("#D9CDB3"));
        Label lblEmpty = new Label("Chưa có sự cố nào");
        lblEmpty.getStyleClass().add("h2");
        Label lblEmptySub = new Label("Khi bạn báo sự cố, nó sẽ xuất hiện tại đây.");
        lblEmptySub.getStyleClass().add("muted");
        placeholder.getChildren().addAll(emptyIcon, lblEmpty, lblEmptySub);
        ticketTable.setPlaceholder(placeholder);

        layout.getChildren().addAll(toolBar, ticketTable);
        return layout;
    }

    private ToggleButton createSegment(String text, boolean selected) {
        ToggleButton btn = new ToggleButton(text);
        btn.getStyleClass().add("segment");
        btn.setToggleGroup(segGroup);
        btn.setSelected(selected);
        return btn;
    }

    private void applyFilter(String status) {
        filteredTickets.setPredicate(t -> {
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
                    navList.setSelected(false);
                    navChat.setSelected(true);
                    switchTab(2, "Trao đổi 1-1", "Kênh hội thoại trực tiếp giữa người báo và cán bộ tiếp nhận.");
                },
                null,
                null,
                null,
                this::confirmCancelTicket
        );
        dialog.showAndWait();
    }

    private void confirmCancelTicket(IncidentTicket ticket) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Hủy sự cố");
        ComponentFactory.applyTheme(dialog.getDialogPane());

        dialog.setHeaderText("Hủy sự cố " + ticket.getTicketCode() + "?");
        dialog.setContentText("Sự cố tại phòng " + ticket.getRoomLocation() + " chưa có cán bộ nhận.\nSau khi hủy, bạn không thể mở lại sự cố này.");

        ButtonType btnKeep = new ButtonType("Giữ lại", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType btnConfirm = new ButtonType("Hủy sự cố", ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane().getButtonTypes().addAll(btnKeep, btnConfirm);

        // Định dạng nút
        Button okButton = (Button) dialog.getDialogPane().lookupButton(btnConfirm);
        if (okButton != null) {
            okButton.getStyleClass().setAll("btn-danger-solid");
        }
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(btnKeep);
        if (cancelButton != null) {
            cancelButton.getStyleClass().setAll("btn-secondary");
        }

        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == btnConfirm) {
            Packet p = new Packet(Constants.TYPE_STATUS_UPDATE,
                    String.format("STATUS_UPDATE|ticketId=%d|status=%s|note=Nguoi bao chu dong huy",
                            ticket.getTicketId(), Constants.STATUS_CANCELLED));
            SocketClientManager.getInstance().sendPacket(p);
            ticket.setStatus(Constants.STATUS_CANCELLED);
            ticketTable.refresh();
            ComponentFactory.showToast(this, "Đã hủy sự cố " + ticket.getTicketCode(), true);
        }
    }

    private void loadMyTickets() {
        SocketClientManager.getInstance().sendPacket(
                new Packet(Constants.TYPE_LIST_TICKETS, "LIST_TICKETS|scope=MY"));
    }

    @Override
    public void onPacketReceived(Packet packet) {
        Platform.runLater(() -> {
            if (packet.getType() == Constants.TYPE_SUBMIT_RESP) {
                Map<String, String> p = packet.parseParams();
                String code = p.get("ticketCode");
                ComponentFactory.showToast(this, "Đã gửi sự cố " + code, true);

                // Chuyển sang Tab Sự cố của tôi
                navNew.setSelected(false);
                navList.setSelected(true);
                switchTab(1, "Sự cố của tôi", "Theo dõi tiến độ xử lý các sự cố bạn đã gửi.");
                loadMyTickets();

            } else if (packet.getType() == Constants.TYPE_LIST_TICKETS_RESP) {
                if (packet.hasAttachment()) {
                    try {
                        String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                        Type listType = new TypeToken<List<IncidentTicket>>() {}.getType();
                        List<IncidentTicket> list = gson.fromJson(json, listType);
                        if (list != null) {
                            ticketList.setAll(list);
                            if (chatPanel != null) {
                                chatPanel.setConversations(list);
                            }
                        }
                    } catch (Exception ignored) {}
                }

            } else if (packet.getType() == Constants.TYPE_STATUS_BROADCAST) {
                loadMyTickets();
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
