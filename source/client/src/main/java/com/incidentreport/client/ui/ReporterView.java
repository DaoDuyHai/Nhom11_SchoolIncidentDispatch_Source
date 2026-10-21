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
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ReporterView extends BorderPane implements SocketClientManager.PacketListener {

    private final User currentUser;
    private final Gson gson = new Gson();

    // Controls Form Báo cáo mới
    private final TextField txtRoom = new TextField();
    private final ComboBox<IncidentCategory> cbCategory = new ComboBox<>();
    private final ToggleGroup severityGroup = new ToggleGroup();
    private final Label lblSeverityText = new Label("3 Trung bình");
    private final TextArea taDescription = new TextArea();
    private final ImageView imgPreview = new ImageView();
    private final Button btnChooseImg = new Button("Chọn ảnh hiện trường…");
    private final Button btnClearImg = new Button("Xóa ảnh");
    private byte[] chosenImageBytes = new byte[0];

    // Table Báo cáo của tôi
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
        topBar.setStyle("-fx-background-color: #1E5AA8;");

        Label lblTitle = new Label("HỆ THỐNG ĐIỀU PHỐI SỰ CỐ THPT");
        lblTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        lblTitle.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox connBox = ComponentFactory.createConnectionIndicator(true);
        Label lblUser = new Label("👤 " + currentUser.getFullName() + " (" + currentUser.getSubRole() + ")");
        lblUser.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        lblUser.setTextFill(Color.WHITE);

        Button btnLogout = new Button("Đăng xuất");
        btnLogout.setStyle("-fx-background-color: #16437D; -fx-text-fill: white; -fx-padding: 5 12;");
        btnLogout.setOnAction(e -> IncidentReportApp.logout());

        topBar.getChildren().addAll(lblTitle, spacer, connBox, lblUser, btnLogout);
        setTop(topBar);

        // Center Content - TabPane
        Tab tabNew = new Tab("① Báo sự cố mới", createSubmitIncidentTab());
        tabNew.setClosable(false);

        Tab tabList = new Tab("② Báo cáo của tôi", createMyTicketsTab());
        tabList.setClosable(false);

        chatPanel = new ChatPanel(currentUser);
        tabChat = new Tab("③ 💬 Trao đổi / Chat", chatPanel);
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
        leftForm.setPrefWidth(450);

        // Vị trí phòng
        Label lblRoom = new Label("Vị trí (Phòng học / Khu vực) *:");
        lblRoom.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        txtRoom.setPromptText("VD: A201, LAB_CHEM1, SAN_BONG...");
        txtRoom.setStyle("-fx-font-size: 13px; -fx-padding: 6px;");

        // Loại sự cố
        Label lblCat = new Label("Loại sự cố *:");
        lblCat.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        initCategories();
        cbCategory.setMaxWidth(Double.MAX_VALUE);
        cbCategory.setStyle("-fx-font-size: 13px;");

        // Mức độ nghiêm trọng (1-5)
        Label lblSev = new Label("Mức độ nghiêm trọng *:");
        lblSev.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        HBox sevBox = new HBox(8);
        sevBox.setAlignment(Pos.CENTER_LEFT);
        for (int i = 1; i <= 5; i++) {
            ToggleButton tb = new ToggleButton(String.valueOf(i));
            tb.setToggleGroup(severityGroup);
            tb.setUserData(i);
            tb.setStyle("-fx-min-width: 35px; -fx-font-weight: bold;");
            if (i == 3) tb.setSelected(true);
            sevBox.getChildren().add(tb);
        }
        severityGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                int sev = (int) newVal.getUserData();
                lblSeverityText.setText(getSeverityName(sev));
            }
        });
        sevBox.getChildren().add(lblSeverityText);

        // Mô tả chi tiết
        Label lblDesc = new Label("Mô tả chi tiết *:");
        lblDesc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        taDescription.setPromptText("Mô tả dấu hiệu sự cố (khói, mùi khét, hỏng hóc, chấn thương...)...");
        taDescription.setPrefRowCount(4);
        taDescription.setWrapText(true);

        // Nút gửi
        Button btnSubmit = new Button("GỬI BÁO CÁO");
        btnSubmit.setStyle("-fx-background-color: #D32F2F; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 10 25; -fx-background-radius: 6;");
        btnSubmit.setOnAction(e -> doSubmitIncident());

        leftForm.getChildren().addAll(lblRoom, txtRoom, lblCat, cbCategory, lblSev, sevBox, lblDesc, taDescription, btnSubmit);

        // Cột bên phải: Ảnh hiện trường
        VBox rightImg = new VBox(10);
        rightImg.setAlignment(Pos.TOP_CENTER);
        rightImg.setPadding(new Insets(10));
        rightImg.setStyle("-fx-background-color: #F4F6F9; -fx-background-radius: 8px; -fx-border-color: #D9DEE5; -fx-border-radius: 8px;");
        rightImg.setPrefWidth(300);

        Label lblImgTitle = new Label("ẢNH HIỆN TRƯỜNG");
        lblImgTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));

        imgPreview.setFitWidth(260);
        imgPreview.setFitHeight(180);
        imgPreview.setPreserveRatio(true);
        imgPreview.setStyle("-fx-border-color: #ccc;");

        HBox imgBtnBox = new HBox(10, btnChooseImg, btnClearImg);
        imgBtnBox.setAlignment(Pos.CENTER);
        btnChooseImg.setOnAction(e -> chooseImage());
        btnClearImg.setOnAction(e -> clearImage());

        Label lblImgNote = new Label("Định dạng JPG/PNG. Tối đa 2 MB.");
        lblImgNote.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        lblImgNote.setTextFill(Color.GRAY);

        rightImg.getChildren().addAll(lblImgTitle, imgPreview, imgBtnBox, lblImgNote);

        formBox.getChildren().addAll(leftForm, rightImg);
        layout.getChildren().add(formBox);
        return new ScrollPane(layout);
    }

    private void initCategories() {
        cbCategory.getItems().addAll(
            new IncidentCategory("FACILITY", "Hỏng thiết bị lớp học (Quạt, Đèn, Máy chiếu)", "TECHNICAL", 2, 300),
            new IncidentCategory("WATER", "Sự cố Điện nước (Vỡ ống nước, Mất nước)", "TECHNICAL", 3, 300),
            new IncidentCategory("ELECTRIC", "Sự cố Điện nguy hiểm (Chập cầu dao, Rò điện)", "TECHNICAL", 4, 300),
            new IncidentCategory("MEDICAL", "Cấp cứu Y tế (Học sinh ngất xỉu, Chấn thương)", "MEDICAL", 5, 180),
            new IncidentCategory("SECURITY", "An ninh Trật tự (Xô xát, Người lạ đột nhập)", "SECURITY", 4, 180),
            new IncidentCategory("FIRE", "Cháy nổ / Rò rỉ khí độc phòng thí nghiệm", "ALL", 5, 120)
        );
        cbCategory.getSelectionModel().selectFirst();
        cbCategory.setOnAction(e -> {
            IncidentCategory cat = cbCategory.getSelectionModel().getSelectedItem();
            if (cat != null) {
                for (Toggle t : severityGroup.getToggles()) {
                    if (t.getUserData().equals(cat.getDefaultSeverity())) {
                        t.setSelected(true);
                        break;
                    }
                }
            }
        });
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

    private Node createMyTicketsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: white;");

        HBox toolBar = new HBox(10);
        Button btnRefresh = new Button("Làm mới danh sách");
        btnRefresh.setOnAction(e -> loadMyTickets());
        toolBar.getChildren().add(btnRefresh);

        // Columns
        TableColumn<IncidentTicket, String> colCode = new TableColumn<>("Mã sự cố");
        colCode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTicketCode()));
        colCode.setPrefWidth(140);
        colCode.setSortable(true);

        TableColumn<IncidentTicket, String> colRoom = new TableColumn<>("Phòng");
        colRoom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRoomLocation()));
        colRoom.setPrefWidth(90);
        colRoom.setSortable(true);

        TableColumn<IncidentTicket, String> colCat = new TableColumn<>("Loại sự cố");
        colCat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategoryName() != null ? d.getValue().getCategoryName() : d.getValue().getCategoryCode()));
        colCat.setPrefWidth(180);
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
        colSev.setPrefWidth(100);
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
        colStatus.setPrefWidth(120);
        colStatus.setSortable(true);
        colStatus.setComparator((s1, s2) -> Integer.compare(getStatusRank(s1), getStatusRank(s2)));

        TableColumn<IncidentTicket, String> colStaff = new TableColumn<>("Người tiếp nhận");
        colStaff.setCellValueFactory(d -> new SimpleStringProperty(
            d.getValue().getAssignedStaffName() != null ? d.getValue().getAssignedStaffName() : "—"
        ));
        colStaff.setPrefWidth(160);
        colStaff.setSortable(true);

        TableColumn<IncidentTicket, String> colTime = new TableColumn<>("Giờ báo");
        colTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCreatedAt()));
        colTime.setPrefWidth(160);
        colTime.setSortable(true);

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

        ticketTable.getColumns().addAll(colCode, colRoom, colCat, colSev, colStatus, colStaff, colTime, colChatAction);

        SortedList<IncidentTicket> sortedMyTickets = new SortedList<>(ticketList);
        sortedMyTickets.comparatorProperty().bind(ticketTable.comparatorProperty());
        ticketTable.setItems(sortedMyTickets);
        ticketTable.getSortOrder().add(colTime);
        colTime.setSortType(TableColumn.SortType.DESCENDING);

        VBox.setVgrow(ticketTable, Priority.ALWAYS);

        box.getChildren().addAll(toolBar, ticketTable);
        return box;
    }

    private void chooseImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh hiện trường");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh JPG/PNG", "*.jpg", "*.jpeg", "*.png"));
        File file = fc.showOpenDialog(getScene().getWindow());
        if (file != null) {
            if (file.length() > 2 * 1024 * 1024) {
                new Alert(Alert.AlertType.WARNING, "Dung lượng ảnh vượt quá 2MB! Vui lòng chọn ảnh nhỏ hơn.").show();
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

        int severity = 3;
        Toggle selSev = severityGroup.getSelectedToggle();
        if (selSev != null) severity = (int) selSev.getUserData();

        String desc = taDescription.getText().trim();
        if (desc.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Vui lòng nhập mô tả sự cố!").show();
            return;
        }

        // Đóng gói bản tin 0x02 SUBMIT_INCIDENT
        String headerPayload = String.format(
            "INCIDENT|room=%s|category=%s|severity=%d|desc=%s",
            room.toUpperCase(), cat.getCategoryCode(), severity, desc.replace("|", "/")
        );

        Packet packet = new Packet(Constants.TYPE_SUBMIT_INCIDENT, headerPayload, chosenImageBytes);
        SocketClientManager.getInstance().sendPacket(packet);

        // Reset form
        txtRoom.clear();
        taDescription.clear();
        clearImage();
    }

    private void loadMyTickets() {
        SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_LIST_MY_TICKETS, "LIST_MY_TICKETS"));
    }

    @Override
    public void onPacketReceived(Packet packet) {
        if (packet.getType() == Constants.TYPE_SUBMIT_RESP) {
            Map<String, String> p = packet.parseParams();
            String code = p.get("ticketCode");
            boolean isMerged = "1".equals(p.get("isMerged"));
            int count = Integer.parseInt(p.getOrDefault("reportCount", "1"));

            if (isMerged) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Đồng bộ Báo cáo");
                alert.setHeaderText("⚡ PHÁT HIỆN SỰ CỐ TRÙNG LẶP ĐÃ ĐƯỢC GỘP!");
                alert.setContentText(String.format(
                    "Sự cố tại vị trí này đã có người báo trước đó.\nBáo cáo của bạn đã được tự động gộp vào mã: %s.\nHiện có %d người cùng báo sự cố này!",
                    code, count
                ));
                alert.show();
            } else {
                new Alert(Alert.AlertType.INFORMATION, "✅ Gửi sự cố thành công! Mã ticket: " + code).show();
            }

            // Chuyển sang tab Báo cáo của tôi
            tabPane.getSelectionModel().select(1);
            loadMyTickets();

        } else if (packet.getType() == Constants.TYPE_LIST_TICKETS_RESP) {
            try {
                Type listType = new TypeToken<ArrayList<IncidentTicket>>(){}.getType();
                List<IncidentTicket> list = gson.fromJson(packet.getHeaderPayload(), listType);
                ticketList.setAll(list);
                if (chatPanel != null) {
                    chatPanel.setTickets(list);
                }
            } catch (Exception ignored) {}

        } else if (packet.getType() == Constants.TYPE_STATUS_UPDATE) {
            // Tự động tải lại bảng khi trạng thái thay đổi
            loadMyTickets();

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
    public void onDisconnected() {
        // Handled globally
    }

    private String getSeverityName(int s) {
        switch (s) {
            case 1: return "1 Rất thấp";
            case 2: return "2 Thấp";
            case 3: return "3 Trung bình";
            case 4: return "4 Cao";
            case 5: return "5 Khẩn cấp";
            default: return "";
        }
    }
}
