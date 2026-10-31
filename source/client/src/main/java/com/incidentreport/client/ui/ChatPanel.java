package com.incidentreport.client.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.client.util.SoundEffectManager;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.ChatMessage;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Giao diện Khung Chat 1-1 Chuyên Dụng Tái Thiết Kế (DESIGN_SYSTEM SIDS):
 * - Bảng màu SIDS: Giấy - Mực - Đồng
 * - Cột trái (320px .chat-sidebar): Danh sách hội thoại với avatar icon, mã vé mono, unread dot (.conv-unread).
 * - Cột phải: Nền vở kẻ ngang (.chat-canvas), header lịch thiệp (.chat-header).
 * - Bong bóng chat: .bubble-in (trắng, viền giấy) & .bubble-out (mực, chữ trắng), thời gian & tên nằm ngoài (.bubble-meta).
 * - Banner thông báo: .closed-banner khi ca đã đóng, .audit-banner khi Admin ở chế độ kiểm toán.
 */
public class ChatPanel extends BorderPane implements SocketClientManager.PacketListener {

    private final User currentUser;
    private final Gson gson = new Gson();

    private IncidentTicket activeTicket = null;
    private final ObservableList<IncidentTicket> conversationMasterList = FXCollections.observableArrayList();
    private final FilteredList<IncidentTicket> conversationFilteredList = new FilteredList<>(conversationMasterList, t -> true);
    private final Set<Integer> unreadTicketIds = new HashSet<>();
    private int lastSenderId = -1;

    // Left Panel: Conversation List Controls
    private final ListView<IncidentTicket> convListView = new ListView<>();
    private final TextField txtSearchConv = new TextField();
    private final ComboBox<String> cbConvFilter = new ComboBox<>();
    private final Label lblConvCount = new Label("0");

    // Right Panel: Chat Detail Controls
    private final Label lblTicketTitle = new Label("Chọn hội thoại để bắt đầu");
    private final Label lblTicketSub = new Label("Hệ thống sẽ tải toàn bộ lịch sử trao đổi 1-1 giữa người báo và cán bộ xử lý.");
    private final HBox badgeBox = new HBox(8);
    private final HBox closedBanner = new HBox(8);
    private final HBox auditBanner = new HBox(8);

    private final VBox messagesBox = new VBox();
    private final ScrollPane scrollPane = new ScrollPane(messagesBox);
    private final TextField txtInput = new TextField();
    private final Button btnSend = new Button();
    private final HBox inputBox = new HBox(10);
    private final StackPane bottomContainer = new StackPane();

    // Placeholder view when no ticket is selected
    private final VBox emptyChatState = new VBox(12);

    public ChatPanel(User currentUser) {
        this.currentUser = currentUser;
        initUI();
        SocketClientManager.getInstance().addListener(this);
    }

    private void initUI() {
        getStyleClass().add("content-area");

        SplitPane splitPane = new SplitPane();
        splitPane.setStyle("-fx-box-border: transparent; -fx-background-color: transparent;");

        Node leftPane = createConversationSidebar();
        Node rightPane = createChatDetailPane();

        SplitPane.setResizableWithParent(leftPane, false);
        SplitPane.setResizableWithParent(rightPane, true);

        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(0.32);

        setCenter(splitPane);
        setDisabledState(true);
    }

    private Node createConversationSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().add("chat-sidebar");
        sidebar.setPadding(new Insets(14, 12, 14, 12));
        sidebar.setMinWidth(220);
        sidebar.setPrefWidth(340);
        sidebar.setMaxWidth(800);

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label("Hội thoại");
        lblTitle.setStyle("-fx-font-family: 'Lora', Georgia, serif; -fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -ink-900;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        lblConvCount.getStyleClass().add("nav-count");

        header.getChildren().addAll(lblTitle, sp, lblConvCount);

        // Search Field
        txtSearchConv.setPromptText("Tìm mã, phòng, người báo...");
        txtSearchConv.getStyleClass().add("search-field");
        txtSearchConv.textProperty().addListener((obs, old, val) -> applyConvFilter());

        // Filter Dropdown
        cbConvFilter.getItems().addAll("Tất cả hội thoại", "Đang xử lý (Active)", "Đã hoàn tất (Closed)");
        cbConvFilter.getSelectionModel().select(0);
        cbConvFilter.setMaxWidth(Double.MAX_VALUE);
        cbConvFilter.setOnAction(e -> applyConvFilter());

        // Conversation List
        convListView.setItems(conversationFilteredList);
        convListView.getStyleClass().add("conv-list");
        VBox.setVgrow(convListView, Priority.ALWAYS);

        // Triệt tiêu hoàn toàn thanh cuộn ngang không cần thiết
        convListView.skinProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                for (Node node : convListView.lookupAll(".scroll-bar")) {
                    if (node instanceof ScrollBar sb && sb.getOrientation() == Orientation.HORIZONTAL) {
                        sb.setVisible(false);
                        sb.setManaged(false);
                        sb.setPrefHeight(0);
                        sb.setMaxHeight(0);
                        sb.visibleProperty().addListener((o, wasVis, isVis) -> {
                            if (isVis) sb.setVisible(false);
                        });
                    }
                }
            });
        });

        Label emptyLabel = new Label("Không có cuộc hội thoại nào");
        emptyLabel.setStyle("-fx-text-fill: -text-400; -fx-font-style: italic; -fx-font-size: 12px;");
        convListView.setPlaceholder(emptyLabel);

        convListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(IncidentTicket t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(6, 8, 6, 8));
                    row.maxWidthProperty().bind(convListView.widthProperty().subtract(16));

                    // Left Category / Department Avatar Icon
                    StackPane iconBox = createDeptIconCircle(t.getTargetDepartment());

                    // Center Details
                    VBox center = new VBox(2);
                    center.setMinWidth(0);
                    HBox.setHgrow(center, Priority.ALWAYS);

                    // Dòng 1: Tiêu đề danh mục sự cố + Huy hiệu trạng thái bên phải
                    HBox topRow = new HBox(6);
                    topRow.setAlignment(Pos.CENTER_LEFT);

                    String catTitle = t.getCategoryName();
                    if (catTitle == null || catTitle.trim().isEmpty()) {
                        catTitle = (t.getTitle() != null && !t.getTitle().trim().isEmpty()) ? t.getTitle() : "Sự cố " + t.getCategoryCode();
                    }
                    Label lblCatTitle = new Label(catTitle);
                    lblCatTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12.5px; -fx-text-fill: -ink-900;");
                    lblCatTitle.setMinWidth(0);
                    lblCatTitle.setMaxWidth(Double.MAX_VALUE);
                    lblCatTitle.setTextOverrun(OverrunStyle.ELLIPSIS);
                    HBox.setHgrow(lblCatTitle, Priority.ALWAYS);

                    Label statusBadge = ComponentFactory.createStatusBadge(t.getStatus());

                    topRow.getChildren().addAll(lblCatTitle, statusBadge);

                    // Dòng 2: Mã ticket · Phòng học · Đối tác hội thoại
                    String partnerText;
                    if (Constants.ROLE_REPORTER.equalsIgnoreCase(currentUser.getRole())) {
                        String st = t.getAssignedStaffName() != null ? t.getAssignedStaffName() : "Chờ tiếp nhận";
                        ComponentFactory.NameParts np = ComponentFactory.parseNameAndTitle(st);
                        partnerText = "Cán bộ: " + np.displayName;
                    } else if (Constants.ROLE_RESPONDER.equalsIgnoreCase(currentUser.getRole())) {
                        ComponentFactory.NameParts np = ComponentFactory.parseNameAndTitle(t.getReporterName());
                        partnerText = "Báo bởi: " + np.displayName;
                    } else {
                        ComponentFactory.NameParts repNp = ComponentFactory.parseNameAndTitle(t.getReporterName());
                        ComponentFactory.NameParts stNp = ComponentFactory.parseNameAndTitle(t.getAssignedStaffName() != null ? t.getAssignedStaffName() : "Chưa nhận");
                        partnerText = repNp.displayName + " ⇄ " + stNp.displayName;
                    }

                    HBox subRow = new HBox(5);
                    subRow.setAlignment(Pos.CENTER_LEFT);

                    Label lblCode = new Label(t.getTicketCode());
                    lblCode.setStyle("-fx-font-family: 'JetBrains Mono', Consolas, monospace; -fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: -brass-700;");

                    Label lblDot1 = new Label("·");
                    lblDot1.setStyle("-fx-text-fill: -text-400; -fx-font-weight: bold; -fx-font-size: 11px;");

                    Label lblRoom = new Label("P." + (t.getRoomLocation() != null ? t.getRoomLocation() : "N/A"));
                    lblRoom.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: -text-900;");

                    Label lblDot2 = new Label("·");
                    lblDot2.setStyle("-fx-text-fill: -text-400; -fx-font-weight: bold; -fx-font-size: 11px;");

                    Label lblPartner = new Label(partnerText);
                    lblPartner.setStyle("-fx-font-size: 11px; -fx-text-fill: -text-500;");
                    lblPartner.setMinWidth(0);
                    lblPartner.setMaxWidth(Double.MAX_VALUE);
                    lblPartner.setTextOverrun(OverrunStyle.ELLIPSIS);
                    HBox.setHgrow(lblPartner, Priority.ALWAYS);

                    subRow.getChildren().addAll(lblCode, lblDot1, lblRoom, lblDot2, lblPartner);

                    center.getChildren().addAll(topRow, subRow);
                    row.getChildren().addAll(iconBox, center);

                    // Unread indicator dot
                    if (unreadTicketIds.contains(t.getTicketId())) {
                        Region unreadDot = new Region();
                        unreadDot.getStyleClass().add("conv-unread");
                        row.getChildren().add(unreadDot);
                    }

                    setGraphic(row);
                }
            }
        });

        convListView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                unreadTicketIds.remove(selected.getTicketId());
                convListView.refresh();
                openTicketChat(selected);
            }
        });

        sidebar.getChildren().addAll(header, txtSearchConv, cbConvFilter, convListView);
        return sidebar;
    }

    private StackPane createDeptIconCircle(String dept) {
        StackPane box = new StackPane();
        Circle circle = new Circle(14);

        String iconLiteral = "mdi2f-flash";
        Color iconColor = Color.web("#2D5F8F");

        if (Constants.SPECIALTY_MEDICAL.equalsIgnoreCase(dept) || "MEDICAL".equalsIgnoreCase(dept)) {
            circle.setFill(Color.web("#FBEAE8"));
            iconLiteral = "mdi2m-medical-bag";
            iconColor = Color.web("#A8402F");
        } else if (Constants.SPECIALTY_SECURITY.equalsIgnoreCase(dept) || "SECURITY".equalsIgnoreCase(dept)) {
            circle.setFill(Color.web("#E5F5EC"));
            iconLiteral = "mdi2s-shield-account";
            iconColor = Color.web("#2F6F55");
        } else {
            // Technical
            circle.setFill(Color.web("#E3ECF5"));
            iconLiteral = "mdi2f-flash";
            iconColor = Color.web("#2D5F8F");
        }

        Node icon = ComponentFactory.createIcon(iconLiteral, 14, iconColor);
        box.getChildren().addAll(circle, icon);
        box.setMinSize(28, 28);
        box.setMaxSize(28, 28);
        return box;
    }

    private Node createChatDetailPane() {
        BorderPane detailPane = new BorderPane();
        detailPane.setMinWidth(320);

        // 1. Top Header
        HBox topBox = new HBox(12);
        topBox.getStyleClass().add("chat-header");
        topBox.setAlignment(Pos.CENTER_LEFT);

        VBox titleCol = new VBox(3);
        HBox.setHgrow(titleCol, Priority.ALWAYS);

        lblTicketTitle.setStyle("-fx-font-family: 'Lora', Georgia, serif; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: -ink-900;");
        lblTicketSub.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-600;");

        titleCol.getChildren().addAll(lblTicketTitle, lblTicketSub);
        badgeBox.setAlignment(Pos.CENTER_RIGHT);

        topBox.getChildren().addAll(titleCol, badgeBox);
        detailPane.setTop(topBox);

        // 2. Messages Canvas (Repeating Notebook Paper Background)
        messagesBox.setPadding(new Insets(16, 20, 16, 20));
        messagesBox.setSpacing(4);

        scrollPane.getStyleClass().addAll("chat-canvas", "chat-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);

        // Setup empty state placeholder
        emptyChatState.setAlignment(Pos.CENTER);
        emptyChatState.setPadding(new Insets(60, 20, 60, 20));
        Node emptyIcon = ComponentFactory.createIcon("mdi2m-message-text-outline", 48, Color.web("#B8872E"));
        Label lblEmptyTitle = new Label("Chưa chọn cuộc trao đổi");
        lblEmptyTitle.setStyle("-fx-font-family: 'Lora', Georgia, serif; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -ink-900;");
        Label lblEmptySub = new Label("Vui lòng chọn một sự cố từ danh sách bên trái để xem trao đổi 1-1.");
        lblEmptySub.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-400;");
        emptyChatState.getChildren().addAll(emptyIcon, lblEmptyTitle, lblEmptySub);

        StackPane centerContainer = new StackPane();
        centerContainer.getStyleClass().add("chat-canvas");
        centerContainer.getChildren().addAll(scrollPane, emptyChatState);

        detailPane.setCenter(centerContainer);

        // 3. Bottom Area: Input Bar or Banner
        setupBottomControls();
        detailPane.setBottom(bottomContainer);

        return detailPane;
    }

    private void setupBottomControls() {
        // Closed Banner
        closedBanner.getStyleClass().add("closed-banner");
        closedBanner.setAlignment(Pos.CENTER);
        Node lockIcon = ComponentFactory.createIcon("mdi2l-lock-outline", 16, Color.web("#8A6218"));
        Label lblClosed = new Label("Sự cố đã hoàn tất. Kênh trao đổi 1-1 đã đóng.");
        lblClosed.setStyle("-fx-text-fill: -brass-700; -fx-font-weight: bold; -fx-font-size: 12.5px;");
        closedBanner.getChildren().addAll(lockIcon, lblClosed);
        closedBanner.setVisible(false);
        closedBanner.setManaged(false);

        // Audit Banner for Admin
        auditBanner.getStyleClass().add("audit-banner");
        auditBanner.setAlignment(Pos.CENTER);
        Node eyeIcon = ComponentFactory.createIcon("mdi2s-shield-search", 16, Color.web("#8A6218"));
        Label lblAudit = new Label("Chế độ kiểm toán: Chỉ xem nội dung, không thể gửi tin nhắn.");
        lblAudit.setStyle("-fx-text-fill: -brass-700; -fx-font-weight: bold; -fx-font-size: 12.5px;");
        auditBanner.getChildren().addAll(eyeIcon, lblAudit);
        auditBanner.setVisible(false);
        auditBanner.setManaged(false);

        // Input Box
        inputBox.getStyleClass().add("chat-input-bar");
        inputBox.setAlignment(Pos.CENTER);

        txtInput.setPromptText("Nhập tin nhắn trao đổi 1-1 (Nhấn Enter để gửi)...");
        txtInput.getStyleClass().add("chat-input");
        HBox.setHgrow(txtInput, Priority.ALWAYS);

        btnSend.getStyleClass().add("btn-send");
        btnSend.setGraphic(ComponentFactory.createIcon("mdi2s-send", 16, Color.WHITE));
        btnSend.setTooltip(new Tooltip("Gửi tin nhắn"));

        btnSend.setOnAction(e -> sendMessage());
        txtInput.setOnAction(e -> sendMessage());

        inputBox.getChildren().addAll(txtInput, btnSend);

        // Container stack
        VBox bottomVBox = new VBox(6);
        bottomVBox.getChildren().addAll(closedBanner, auditBanner, inputBox);
        bottomContainer.getChildren().add(bottomVBox);

        // If current user is ADMIN, permanently configure for Audit mode
        if (Constants.ROLE_ADMIN.equalsIgnoreCase(currentUser.getRole())) {
            auditBanner.setVisible(true);
            auditBanner.setManaged(true);
            inputBox.setVisible(false);
            inputBox.setManaged(false);
        }
    }

    private void applyConvFilter() {
        String keyword = txtSearchConv.getText() != null ? txtSearchConv.getText().trim().toLowerCase() : "";
        String filter = cbConvFilter.getValue();

        conversationFilteredList.setPredicate(t -> {
            if (t == null) return false;

            // Status filter
            if (filter != null) {
                if (filter.contains("Active")) {
                    if (Constants.STATUS_RESOLVED.equalsIgnoreCase(t.getStatus()) || Constants.STATUS_CANCELLED.equalsIgnoreCase(t.getStatus())) {
                        return false;
                    }
                } else if (filter.contains("Closed")) {
                    if (!Constants.STATUS_RESOLVED.equalsIgnoreCase(t.getStatus()) && !Constants.STATUS_CANCELLED.equalsIgnoreCase(t.getStatus())) {
                        return false;
                    }
                }
            }

            // Keyword filter
            if (!keyword.isEmpty()) {
                boolean mCode = t.getTicketCode() != null && t.getTicketCode().toLowerCase().contains(keyword);
                boolean mRoom = t.getRoomLocation() != null && t.getRoomLocation().toLowerCase().contains(keyword);
                boolean mCat = t.getCategoryName() != null && t.getCategoryName().toLowerCase().contains(keyword);
                boolean mRep = t.getReporterName() != null && t.getReporterName().toLowerCase().contains(keyword);
                boolean mStaff = t.getAssignedStaffName() != null && t.getAssignedStaffName().toLowerCase().contains(keyword);
                boolean mDesc = t.getDescription() != null && t.getDescription().toLowerCase().contains(keyword);
                if (!mCode && !mRoom && !mCat && !mRep && !mStaff && !mDesc) return false;
            }

            return true;
        });

        lblConvCount.setText(String.valueOf(conversationFilteredList.size()));
    }

    public void setConversations(List<IncidentTicket> tickets) {
        Platform.runLater(() -> {
            if (tickets == null) {
                conversationMasterList.clear();
            } else {
                conversationMasterList.setAll(tickets);
            }
            applyConvFilter();

            // Auto-select first ticket if none currently active
            if (activeTicket == null && !conversationFilteredList.isEmpty()) {
                convListView.getSelectionModel().select(0);
            }
        });
    }

    public void openTicketChat(IncidentTicket ticket) {
        if (ticket == null) return;
        this.activeTicket = ticket;
        this.lastSenderId = -1;
        messagesBox.getChildren().clear();

        emptyChatState.setVisible(false);
        emptyChatState.setManaged(false);

        // Sync selection on left list
        for (IncidentTicket t : conversationMasterList) {
            if (t.getTicketId() == ticket.getTicketId()) {
                convListView.getSelectionModel().select(t);
                break;
            }
        }

        lblTicketTitle.setText(String.format("[%s] · %s · %s",
                ticket.getTicketCode(),
                ticket.getRoomLocation() != null ? ticket.getRoomLocation() : "Chưa rõ phòng",
                ticket.getCategoryName() != null ? ticket.getCategoryName() : ticket.getCategoryCode()));

        badgeBox.getChildren().clear();
        badgeBox.getChildren().addAll(
                ComponentFactory.createDepartmentBadge(ticket.getTargetDepartment()),
                ComponentFactory.createStatusBadge(ticket.getStatus())
        );

        String partnerInfo;
        if (Constants.ROLE_REPORTER.equalsIgnoreCase(currentUser.getRole())) {
            partnerInfo = "Cán bộ tiếp nhận: " + (ticket.getAssignedStaffName() != null ? ticket.getAssignedStaffName() : "Đang chờ phân công");
        } else if (Constants.ROLE_RESPONDER.equalsIgnoreCase(currentUser.getRole())) {
            partnerInfo = "Người báo cáo: " + ticket.getReporterName() + (ticket.getReporterPhone() != null && !ticket.getReporterPhone().isEmpty() ? " · ĐT: " + ticket.getReporterPhone() : "");
        } else {
            partnerInfo = String.format("Kiểm toán giám sát: %s ⇄ %s", ticket.getReporterName(),
                    ticket.getAssignedStaffName() != null ? ticket.getAssignedStaffName() : "Chưa nhận");
        }
        lblTicketSub.setText(partnerInfo);

        boolean isResolved = Constants.STATUS_RESOLVED.equalsIgnoreCase(ticket.getStatus())
                || Constants.STATUS_CANCELLED.equalsIgnoreCase(ticket.getStatus());

        if (Constants.ROLE_ADMIN.equalsIgnoreCase(currentUser.getRole())) {
            auditBanner.setVisible(true);
            auditBanner.setManaged(true);
            closedBanner.setVisible(false);
            closedBanner.setManaged(false);
            inputBox.setVisible(false);
            inputBox.setManaged(false);
            setDisabledState(true);
        } else if (isResolved) {
            closedBanner.setVisible(true);
            closedBanner.setManaged(true);
            auditBanner.setVisible(false);
            auditBanner.setManaged(false);
            inputBox.setVisible(true);
            inputBox.setManaged(true);
            setDisabledState(true);
            txtInput.setPromptText("Kênh chat đã đóng do sự cố hoàn tất.");
        } else {
            closedBanner.setVisible(false);
            closedBanner.setManaged(false);
            auditBanner.setVisible(false);
            auditBanner.setManaged(false);
            inputBox.setVisible(true);
            inputBox.setManaged(true);
            setDisabledState(false);
            txtInput.setPromptText("Nhập tin nhắn trao đổi 1-1 (Nhấn Enter để gửi)...");
        }

        // Request chat history from Server
        Packet req = new Packet(Constants.TYPE_CHAT_HISTORY, "CHAT_HISTORY|ticketId=" + ticket.getTicketId());
        SocketClientManager.getInstance().sendPacket(req);
    }

    private void sendMessage() {
        if (activeTicket == null) return;
        String text = txtInput.getText() != null ? txtInput.getText().trim() : "";
        if (text.isEmpty()) return;

        txtInput.clear();
        Packet packet = new Packet(Constants.TYPE_CHAT_SEND,
                "CHAT_SEND|ticketId=" + activeTicket.getTicketId(),
                text.getBytes(StandardCharsets.UTF_8));
        SocketClientManager.getInstance().sendPacket(packet);
        SoundEffectManager.playChat();
    }

    @Override
    public void onPacketReceived(Packet packet) {
        Platform.runLater(() -> {
            if (packet.getType() == Constants.TYPE_CHAT_PUSH) {
                Map<String, String> params = packet.parseParams();
                int tId = Integer.parseInt(params.getOrDefault("ticketId", "0"));

                String content = packet.hasAttachment()
                        ? new String(packet.getAttachment(), StandardCharsets.UTF_8)
                        : params.get("content");

                ChatMessage msg = new ChatMessage();
                msg.setTicketId(tId);
                msg.setSenderId(Integer.parseInt(params.getOrDefault("senderId", "0")));
                msg.setSenderName(params.get("senderName"));
                msg.setSenderRole(params.get("senderRole"));
                msg.setContent(content);
                msg.setSentAt(params.get("time"));

                boolean isMe = (msg.getSenderId() == currentUser.getUserId());
                if (!isMe) {
                    SoundEffectManager.playChat();
                }

                if (activeTicket != null && tId == activeTicket.getTicketId()) {
                    addMessageBubble(msg);
                } else {
                    unreadTicketIds.add(tId);
                    convListView.refresh();
                }

            } else if (packet.getType() == Constants.TYPE_CHAT_HISTORY_RESP) {
                Map<String, String> params = packet.parseParams();
                int tId = Integer.parseInt(params.getOrDefault("ticketId", "0"));
                if (activeTicket != null && tId == activeTicket.getTicketId() && packet.hasAttachment()) {
                    try {
                        String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                        Type listType = new TypeToken<List<ChatMessage>>() {}.getType();
                        List<ChatMessage> list = gson.fromJson(json, listType);
                        messagesBox.getChildren().clear();
                        lastSenderId = -1;
                        if (list != null) {
                            for (ChatMessage m : list) {
                                addMessageBubble(m);
                            }
                        }
                    } catch (Exception ignored) {}
                }

            } else if (packet.getType() == Constants.TYPE_STATUS_BROADCAST) {
                Map<String, String> params = packet.parseParams();
                int tId = Integer.parseInt(params.getOrDefault("ticketId", "0"));
                String newStatus = params.get("status");

                // Update ticket status in list
                for (IncidentTicket t : conversationMasterList) {
                    if (t.getTicketId() == tId) {
                        t.setStatus(newStatus);
                        break;
                    }
                }
                convListView.refresh();

                if (activeTicket != null && tId == activeTicket.getTicketId()) {
                    activeTicket.setStatus(newStatus);
                    badgeBox.getChildren().clear();
                    badgeBox.getChildren().addAll(
                            ComponentFactory.createDepartmentBadge(activeTicket.getTargetDepartment()),
                            ComponentFactory.createStatusBadge(newStatus)
                    );

                    if (Constants.STATUS_RESOLVED.equalsIgnoreCase(newStatus) || Constants.STATUS_CANCELLED.equalsIgnoreCase(newStatus)) {
                        if (!Constants.ROLE_ADMIN.equalsIgnoreCase(currentUser.getRole())) {
                            closedBanner.setVisible(true);
                            closedBanner.setManaged(true);
                            setDisabledState(true);
                            txtInput.setPromptText("Sự cố đã hoàn tất. Kênh trao đổi đã đóng.");
                        }
                    }
                }
            }
        });
    }

    private void addMessageBubble(ChatMessage msg) {
        boolean isMe = (msg.getSenderId() == currentUser.getUserId());
        boolean sameSenderAsLast = (msg.getSenderId() == lastSenderId);
        lastSenderId = msg.getSenderId();

        VBox bubbleGroup = new VBox(2);
        bubbleGroup.setMaxWidth(440);

        // Bubble body
        VBox bubble = new VBox();
        bubble.setMaxWidth(420);

        Label lblText = new Label(msg.getContent());
        lblText.setWrapText(true);
        lblText.setMaxWidth(400);

        bubble.getChildren().add(lblText);

        // Bubble meta (Name & Time outside bubble)
        String timeStr = msg.getSentAt() != null ? ComponentFactory.formatShortTime(msg.getSentAt()) : "";
        Label lblMeta = new Label();
        lblMeta.getStyleClass().add("bubble-meta");

        String senderDisplayName = isMe ? currentUser.getFullName() : (msg.getSenderName() != null ? msg.getSenderName() : "Đối tác");

        // Tạo Mini Avatar con con tròn xoe (28x28) sinh động cho cả 2 bên
        Node miniAvatar = ComponentFactory.createInitialsAvatar(senderDisplayName, isMe);
        miniAvatar.setStyle(isMe
                ? "-fx-min-width: 28px; -fx-min-height: 28px; -fx-max-width: 28px; -fx-max-height: 28px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-color: -ink-800; -fx-text-fill: white; -fx-background-radius: 999; -fx-alignment: center;"
                : "-fx-min-width: 28px; -fx-min-height: 28px; -fx-max-width: 28px; -fx-max-height: 28px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-color: #E6DFC9; -fx-text-fill: #14264A; -fx-background-radius: 999; -fx-alignment: center;");
        Tooltip.install(miniAvatar, new Tooltip(senderDisplayName));

        if (isMe) {
            bubble.getStyleClass().add("bubble-out");
            lblMeta.setText(timeStr);
            bubbleGroup.setAlignment(Pos.TOP_RIGHT);
        } else {
            bubble.getStyleClass().add("bubble-in");
            String senderPrefix = (msg.getSenderName() != null ? msg.getSenderName() : "Người dùng");
            lblMeta.setText(senderPrefix + " · " + timeStr);
            bubbleGroup.setAlignment(Pos.TOP_LEFT);
        }

        // Add bubble and meta outside
        bubbleGroup.getChildren().addAll(bubble, lblMeta);

        HBox row = new HBox(8);
        if (isMe) {
            row.setAlignment(Pos.BOTTOM_RIGHT);
            row.getChildren().addAll(bubbleGroup, miniAvatar);
        } else {
            row.setAlignment(Pos.BOTTOM_LEFT);
            row.getChildren().addAll(miniAvatar, bubbleGroup);
        }

        // Spacing: 3px between same sender, 12px between different senders
        VBox.setMargin(row, new Insets(sameSenderAsLast ? 3 : 12, 0, 3, 0));

        messagesBox.getChildren().add(row);

        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private void setDisabledState(boolean disabled) {
        txtInput.setDisable(disabled);
        btnSend.setDisable(disabled);
    }

    @Override
    public void onDisconnected() {
        setDisabledState(true);
    }
}
