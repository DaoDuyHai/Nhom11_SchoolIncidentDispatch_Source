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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Phân hệ Người báo cáo sự cố (Giáo viên / Học sinh):
 * - Tạo báo cáo sự cố nhanh chóng kèm ảnh hiện trường.
 * - Tự động điều phối đến phòng ban chuyên môn (Kỹ thuật / Y tế / An ninh).
 * - Theo dõi danh sách sự cố và trao đổi 1-1 trực tiếp với cán bộ tiếp nhận.
 */
public class ReporterView extends BorderPane implements SocketClientManager.PacketListener {

    private final User currentUser;
    private final Gson gson = new Gson();

    // Form controls
    private final TextField txtRoom = new TextField();
    private final ComboBox<IncidentCategory> cbCategory = new ComboBox<>();
    private final TextArea taDescription = new TextArea();
    private final ImageView imgPreview = new ImageView();
    private final Button btnChooseImg = new Button("Chọn ảnh hiện trường…");
    private final Button btnClearImg = new Button("Xóa ảnh");
    private byte[] chosenImageBytes = new byte[0];

    // Table
    private final TableView<IncidentTicket> ticketTable = new TableView<>();
    private final ObservableList<IncidentTicket> ticketList = FXCollections.observableArrayList();
    private final TabPane tabPane = new TabPane();
    private ChatPanel chatPanel;
    private Tab tabChat;

    public ReporterView(User user) {
        this.currentUser = user;
        initUI();
        SocketClientManager.getInstance().addListener(this);
        loadMyTickets();
    }

    private void initUI() {
        // Top Header
        HBox topBar = new HBox(15);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 20, 12, 20));
        topBar.setStyle("-fx-background-color: #1E293B;");

        Label lblTitle = new Label("HỆ THỐNG ĐIỀU PHỐI SỰ CỐ TRƯỜNG THPT");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lblTitle.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox connBox = ComponentFactory.createConnectionIndicator(true);
        Label lblUser = new Label("👤 " + currentUser.getFullName() + " (" + currentUser.getUsername() + ")");
        lblUser.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lblUser.setTextFill(Color.WHITE);

        Button btnLogout = new Button("Đăng xuất");
        btnLogout.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-padding: 5 12; -fx-background-radius: 4;");
        btnLogout.setOnAction(e -> IncidentReportApp.logout());

        topBar.getChildren().addAll(lblTitle, spacer, connBox, lblUser, btnLogout);
        setTop(topBar);

        // Center Content - TabPane
        Tab tabNew = new Tab("① Báo sự cố mới", createSubmitIncidentTab());
        tabNew.setClosable(false);

        Tab tabList = new Tab("② Sự cố của tôi", createMyTicketsTab());
        tabList.setClosable(false);

        chatPanel = new ChatPanel(currentUser);
        tabChat = new Tab("③ 💬 Trao đổi 1-1", chatPanel);
        tabChat.setClosable(false);

        tabPane.getTabs().addAll(tabNew, tabList, tabChat);
        setCenter(tabPane);
    }

    private Node createSubmitIncidentTab() {
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20, 30, 20, 30));
        layout.setStyle("-fx-background-color: white;");

        HBox formBox = new HBox(30);
        VBox leftForm = new VBox(12);
        leftForm.setPrefWidth(460);

        Label lblRoom = new Label("Vị trí (Phòng học / Khu vực chức năng) *:");
        lblRoom.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        txtRoom.setPromptText("VD: A101, B204, LAB-LY, SAN-BONG...");
        txtRoom.setStyle("-fx-font-size: 13px; -fx-padding: 8px;");

        Label lblCat = new Label("Loại sự cố (Tự động chuyển đúng phòng ban) *:");
        lblCat.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        initCategories();
        cbCategory.setMaxWidth(Double.MAX_VALUE);
        cbCategory.setStyle("-fx-font-size: 13px;");

        Label lblDesc = new Label("Mô tả chi tiết sự cố *:");
        lblDesc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        taDescription.setPromptText("Mô tả ngắn gọn hiện trạng sự cố (VD: quạt trần rung lắc mạnh, ổ điện tóe lửa, học sinh bị sốt cao...)...");
        taDescription.setPrefRowCount(4);
        taDescription.setWrapText(true);

        Button btnSubmit = new Button("🚀 GỬI BÁO CÁO NGAY");
        btnSubmit.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 10 25; -fx-background-radius: 6;");
        btnSubmit.setOnAction(e -> doSubmitIncident());

        leftForm.getChildren().addAll(lblRoom, txtRoom, lblCat, cbCategory, lblDesc, taDescription, btnSubmit);

        // Cột bên phải: Ảnh hiện trường
        VBox rightImg = new VBox(10);
        rightImg.setAlignment(Pos.TOP_CENTER);
        rightImg.setPadding(new Insets(14));
        rightImg.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 8px; -fx-border-color: #E2E8F0; -fx-border-radius: 8px;");
        rightImg.setPrefWidth(300);

        Label lblImgTitle = new Label("ẢNH HIỆN TRƯỜNG");
        lblImgTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        imgPreview.setFitWidth(260);
        imgPreview.setFitHeight(180);
        imgPreview.setPreserveRatio(true);

        HBox imgBtnBox = new HBox(10, btnChooseImg, btnClearImg);
        imgBtnBox.setAlignment(Pos.CENTER);
        btnChooseImg.setOnAction(e -> chooseImage());
        btnClearImg.setOnAction(e -> clearImage());

        Label lblImgNote = new Label("Định dạng JPG/PNG. Tối đa 5 MB.");
        lblImgNote.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        lblImgNote.setTextFill(Color.GRAY);

        rightImg.getChildren().addAll(lblImgTitle, imgPreview, imgBtnBox, lblImgNote);

        formBox.getChildren().addAll(leftForm, rightImg);
        layout.getChildren().add(formBox);
        return new ScrollPane(layout);
    }

    private void initCategories() {
        cbCategory.getItems().addAll(
                new IncidentCategory("FACILITY", "Hỏng thiết bị lớp học (Quạt, Đèn, Máy chiếu)", Constants.SPECIALTY_TECHNICAL),
                new IncidentCategory("WATER", "Sự cố Điện nước (Vỡ ống nước, Mất nước)", Constants.SPECIALTY_TECHNICAL),
                new IncidentCategory("ELECTRIC", "Sự cố Điện nguy hiểm (Chập cầu dao, Rò điện)", Constants.SPECIALTY_TECHNICAL),
                new IncidentCategory("MEDICAL", "Cấp cứu Y tế (Học sinh ngất, Chấn thương, Sốt)", Constants.SPECIALTY_MEDICAL),
                new IncidentCategory("SECURITY", "An ninh Trật tự (Xô xát, Người lạ đột nhập, Trộm cắp)", Constants.SPECIALTY_SECURITY)
        );
        cbCategory.getSelectionModel().selectFirst();
    }

    private Node createMyTicketsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: white;");

        HBox toolBar = new HBox(10);
        Button btnRefresh = new Button("🔄 Làm mới danh sách");
        btnRefresh.setOnAction(e -> loadMyTickets());
        toolBar.getChildren().add(btnRefresh);

        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("Mã sự cố");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setPrefWidth(130);

        TableColumn<IncidentTicket, String> colRoom = new TableColumn<>("Vị trí");
        colRoom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRoomLocation()));
        colRoom.setPrefWidth(90);

        TableColumn<IncidentTicket, String> colCat = new TableColumn<>("Phân loại");
        colCat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategoryName() != null ? d.getValue().getCategoryName() : d.getValue().getCategoryCode()));
        colCat.setPrefWidth(180);

        TableColumn<IncidentTicket, String> colDept = new TableColumn<>("Phòng ban điều phối");
        colDept.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTargetDepartment()));
        colDept.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setGraphic(null);
                else setGraphic(ComponentFactory.createDepartmentBadge(item));
            }
        });
        colDept.setPrefWidth(140);

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

        TableColumn<IncidentTicket, String> colStaff = new TableColumn<>("Cán bộ xử lý");
        colStaff.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getAssignedStaffName() != null ? d.getValue().getAssignedStaffName() : "Đang chờ tiếp nhận"
        ));
        colStaff.setPrefWidth(160);

        TableColumn<IncidentTicket, String> colTime = new TableColumn<>("Thời gian báo");
        colTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCreatedAt()));
        colTime.setPrefWidth(150);

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

        ticketTable.getColumns().addAll(colCode, colRoom, colCat, colDept, colStatus, colStaff, colTime, colChatAction);
        ticketTable.setItems(ticketList);
        VBox.setVgrow(ticketTable, Priority.ALWAYS);

        box.getChildren().addAll(toolBar, ticketTable);
        return box;
    }

    private void chooseImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh hiện trường sự cố");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh JPG/PNG", "*.jpg", "*.jpeg", "*.png"));
        File file = fc.showOpenDialog(getScene().getWindow());
        if (file != null) {
            if (file.length() > 5 * 1024 * 1024) {
                new Alert(Alert.AlertType.WARNING, "Dung lượng ảnh vượt quá 5MB! Vui lòng chọn ảnh nhỏ hơn.").show();
                return;
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                chosenImageBytes = fis.readAllBytes();
                imgPreview.setImage(new Image(new ByteArrayInputStream(chosenImageBytes)));
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Lỗi đọc file ảnh: " + ex.getMessage()).show();
            }
        }
    }

    private void clearImage() {
        chosenImageBytes = new byte[0];
        imgPreview.setImage(null);
    }

    private void doSubmitIncident() {
        String room = txtRoom.getText().trim();
        if (room.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Vui lòng nhập vị trí phòng!").show();
            return;
        }

        IncidentCategory cat = cbCategory.getSelectionModel().getSelectedItem();
        if (cat == null) return;

        String desc = taDescription.getText().trim();
        if (desc.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Vui lòng nhập mô tả sự cố!").show();
            return;
        }

        String headerPayload = String.format("INCIDENT|room=%s|category=%s|desc=%s",
                room.toUpperCase(), cat.getCategoryCode(), desc.replace("|", "/"));

        Packet packet = new Packet(Constants.TYPE_SUBMIT_INCIDENT, headerPayload, chosenImageBytes);
        SocketClientManager.getInstance().sendPacket(packet);

        txtRoom.clear();
        taDescription.clear();
        clearImage();
    }

    private void loadMyTickets() {
        SocketClientManager.getInstance().sendPacket(
                new Packet(Constants.TYPE_LIST_TICKETS, "LIST_TICKETS|scope=MY"));
    }

    @Override
    public void onPacketReceived(Packet packet) {
        if (packet.getType() == Constants.TYPE_SUBMIT_RESP) {
            Map<String, String> p = packet.parseParams();
            String code = p.get("ticketCode");
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "✅ Gửi sự cố thành công! Mã ticket: " + code);
            alert.show();

            tabPane.getSelectionModel().select(1);
            loadMyTickets();

        } else if (packet.getType() == Constants.TYPE_LIST_TICKETS_RESP) {
            if (packet.hasAttachment()) {
                try {
                    String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                    Type listType = new TypeToken<List<IncidentTicket>>() {}.getType();
                    List<IncidentTicket> list = gson.fromJson(json, listType);
                    ticketList.setAll(list);
                } catch (Exception ignored) {}
            }

        } else if (packet.getType() == Constants.TYPE_STATUS_BROADCAST) {
            // Tự động cập nhật bảng sự cố khi có cán bộ tiếp nhận hoặc hoàn tất
            loadMyTickets();
        }
    }

    @Override
    public void onDisconnected() {
        // Handled globally
    }
}
