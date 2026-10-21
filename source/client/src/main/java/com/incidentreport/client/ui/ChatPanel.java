package com.incidentreport.client.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.incidentreport.client.net.SocketClientManager;
import com.incidentreport.common.Constants;
import com.incidentreport.common.model.ChatMessage;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.User;
import com.incidentreport.common.packet.Packet;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Giao diện Khung Chat Hiện Đại (Modern Real-time Chat Panel):
 * Hỗ trợ trao đổi tức thời giữa Client (Reporter), Kỹ thuật viên (Responder) và Quản lý (Admin).
 * Cho phép chuyển đổi linh hoạt giữa Kênh chung toàn trường và Kênh riêng từng sự cố.
 */
public class ChatPanel extends BorderPane {

    private final User currentUser;
    private final Gson gson = new Gson();

    private int activeTicketId = 0; // 0 = Kênh chung toàn trường
    private String activeRoomTitle = "Kênh Chung Toàn Trường";

    // UI Controls
    private final VBox channelListContainer = new VBox(6);
    private final VBox messagesBox = new VBox(12);
    private final ScrollPane scrollPane = new ScrollPane(messagesBox);
    private final Label lblRoomHeader = new Label("📢 Kênh Chung Toàn Trường");
    private final Label lblRoomSub = new Label("Trao đổi mở giữa Ban Giám Hiệu, Kỹ thuật viên và Giáo viên / Học sinh");
    private final TextField txtInput = new TextField();
    private final Button btnSend = new Button("Gửi ✈");

    // Cache danh sách vé sự cố và danh sách tin nhắn theo phòng
    private final List<IncidentTicket> currentTickets = new ArrayList<>();
    private final Map<Integer, List<ChatMessage>> roomMessagesCache = new HashMap<>();
    private final Set<Integer> unreadRooms = new HashSet<>();

    public ChatPanel(User user) {
        this.currentUser = user;
        initUI();
        // Mặc định nạp lịch sử Kênh Chung
        requestHistory(0);
    }

    private void initUI() {
        setStyle("-fx-background-color: #F8FAFC;");

        // 1. THANH ĐIỀU HƯỚNG PHÒNG CHAT BÊN TRÁI (LEFT SIDEBAR)
        VBox leftSidebar = new VBox(10);
        leftSidebar.setPrefWidth(260);
        leftSidebar.setMinWidth(220);
        leftSidebar.setMaxWidth(300);
        leftSidebar.setPadding(new Insets(12));
        leftSidebar.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0; -fx-border-width: 0 1 0 0;");

        HBox sidebarHeader = new HBox(8);
        sidebarHeader.setAlignment(Pos.CENTER_LEFT);
        Label lblChannels = new Label("PHÒNG TRAO ĐỔI");
        lblChannels.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lblChannels.setTextFill(Color.web("#64748B"));

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button btnRefreshChannels = new Button("🔄");
        btnRefreshChannels.setTooltip(new Tooltip("Làm mới danh sách phòng"));
        btnRefreshChannels.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 2 6;");
        btnRefreshChannels.setOnAction(e -> renderChannelList());

        sidebarHeader.getChildren().addAll(lblChannels, sp, btnRefreshChannels);

        ScrollPane channelScroll = new ScrollPane(channelListContainer);
        channelScroll.setFitToWidth(true);
        channelScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(channelScroll, Priority.ALWAYS);

        leftSidebar.getChildren().addAll(sidebarHeader, channelScroll);
        setLeft(leftSidebar);

        // 2. KHU VỰC CHAT CHÍNH (CENTER CHAT AREA)
        VBox chatMainArea = new VBox();
        chatMainArea.setStyle("-fx-background-color: #F8FAFC;");

        // Header của phòng chat đang chọn
        HBox chatTopBar = new HBox(12);
        chatTopBar.setAlignment(Pos.CENTER_LEFT);
        chatTopBar.setPadding(new Insets(12, 18, 12, 18));
        chatTopBar.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(2);
        lblRoomHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        lblRoomHeader.setTextFill(Color.web("#0F172A"));

        lblRoomSub.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        lblRoomSub.setTextFill(Color.web("#64748B"));

        titleBox.getChildren().addAll(lblRoomHeader, lblRoomSub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnReloadChat = new Button("Tải lại tin nhắn");
        btnReloadChat.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #334155; -fx-font-weight: bold; -fx-padding: 5 12; -fx-background-radius: 6px; -fx-cursor: hand;");
        btnReloadChat.setOnAction(e -> requestHistory(activeTicketId));

        chatTopBar.getChildren().addAll(titleBox, spacer, btnReloadChat);

        // Khung hiển thị tin nhắn (Messages ScrollView)
        messagesBox.setPadding(new Insets(16));
        messagesBox.setStyle("-fx-background-color: #F8FAFC;");
        messagesBox.setFillWidth(true);

        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #F8FAFC; -fx-background-color: #F8FAFC; -fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Khung nhập tin nhắn bên dưới (Input Bar)
        HBox inputBar = new HBox(10);
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setPadding(new Insets(12, 16, 12, 16));
        inputBar.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0; -fx-border-width: 1 0 0 0;");

        txtInput.setPromptText("Nhập nội dung tin nhắn trao đổi... (Nhấn Enter để gửi)");
        txtInput.setFont(Font.font("Segoe UI", 13));
        txtInput.setStyle("-fx-padding: 9 12; -fx-background-radius: 8px; -fx-border-color: #CBD5E1; -fx-border-radius: 8px;");
        HBox.setHgrow(txtInput, Priority.ALWAYS);

        txtInput.setOnAction(e -> sendMessage());

        btnSend.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        btnSend.setStyle("-fx-background-color: #1E5AA8; -fx-text-fill: white; -fx-padding: 9 18; -fx-background-radius: 8px; -fx-cursor: hand;");
        btnSend.setOnAction(e -> sendMessage());

        inputBar.getChildren().addAll(txtInput, btnSend);

        chatMainArea.getChildren().addAll(chatTopBar, scrollPane, inputBar);
        setCenter(chatMainArea);

        renderChannelList();
    }

    /**
     * Cập nhật danh sách các sự cố để tạo kênh chat tương ứng
     */
    public void setTickets(List<IncidentTicket> tickets) {
        currentTickets.clear();
        if (tickets != null) {
            currentTickets.addAll(tickets);
        }
        renderChannelList();
    }

    /**
     * Chọn nhanh một phòng chat sự cố từ bên ngoài (ví dụ bấm nút Chat ở bảng sự cố)
     */
    public void selectTicket(int ticketId, String displayTitle) {
        this.activeTicketId = ticketId;
        this.activeRoomTitle = (displayTitle != null && !displayTitle.isEmpty()) ? displayTitle : ("Sự Cố #" + ticketId);
        lblRoomHeader.setText("🎫 " + activeRoomTitle);
        lblRoomSub.setText("Phòng trao đổi riêng của sự cố #" + ticketId + " (Client người báo, Kỹ thuật viên phụ trách & Quản lý)");
        unreadRooms.remove(ticketId);

        renderChannelList();
        renderCachedMessagesOrFetch();
        txtInput.requestFocus();
    }

    /**
     * Chuyển về Kênh Chung Toàn Trường
     */
    public void selectGeneralChannel() {
        this.activeTicketId = 0;
        this.activeRoomTitle = "Kênh Chung Toàn Trường";
        lblRoomHeader.setText("📢 Kênh Chung Toàn Trường");
        lblRoomSub.setText("Trao đổi mở giữa Ban Giám Hiệu, Kỹ thuật viên và Giáo viên / Học sinh");
        unreadRooms.remove(0);

        renderChannelList();
        renderCachedMessagesOrFetch();
        txtInput.requestFocus();
    }

    private void renderChannelList() {
        channelListContainer.getChildren().clear();

        // 1. Kênh chung toàn trường
        HBox itemGeneral = createChannelItem("📢 Kênh Toàn Trường", "Toàn trường", 0, activeTicketId == 0);
        channelListContainer.getChildren().add(itemGeneral);

        // 2. Danh mục các sự cố
        if (!currentTickets.isEmpty()) {
            Separator sep = new Separator();
            sep.setPadding(new Insets(6, 0, 6, 0));
            channelListContainer.getChildren().add(sep);

            Label lblSection = new Label("CÁC SỰ CỐ");
            lblSection.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            lblSection.setTextFill(Color.web("#94A3B8"));
            channelListContainer.getChildren().add(lblSection);

            for (IncidentTicket t : currentTickets) {
                String title = t.getTicketCode() + " (" + t.getRoomLocation() + ")";
                String subtitle = t.getTitle();
                HBox itemTicket = createChannelItem("🎫 " + title, subtitle, t.getTicketId(), activeTicketId == t.getTicketId());
                channelListContainer.getChildren().add(itemTicket);
            }
        }
    }

    private HBox createChannelItem(String title, String subtitle, int ticketId, boolean isActive) {
        HBox item = new HBox(8);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(8, 10, 8, 10));

        if (isActive) {
            item.setStyle("-fx-background-color: #EFF6FF; -fx-background-radius: 6px; -fx-border-color: #3B82F6; -fx-border-width: 0 0 0 3px; -fx-cursor: hand;");
        } else {
            item.setStyle("-fx-background-color: transparent; -fx-background-radius: 6px; -fx-cursor: hand;");
        }

        VBox textBox = new VBox(2);
        Label lblT = new Label(title);
        lblT.setFont(Font.font("Segoe UI", isActive ? FontWeight.BOLD : FontWeight.NORMAL, 12));
        lblT.setTextFill(isActive ? Color.web("#1E40AF") : Color.web("#1E293B"));

        Label lblSub = new Label(subtitle);
        lblSub.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10));
        lblSub.setTextFill(Color.web("#64748B"));
        lblSub.setMaxWidth(160);

        textBox.getChildren().addAll(lblT, lblSub);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        item.getChildren().add(textBox);

        if (unreadRooms.contains(ticketId)) {
            Circle unreadDot = new Circle(4, Color.web("#EF4444"));
            item.getChildren().add(unreadDot);
        }

        item.setOnMouseClicked(e -> {
            if (ticketId == 0) {
                selectGeneralChannel();
            } else {
                selectTicket(ticketId, title.replace("🎫 ", ""));
            }
        });

        item.setOnMouseEntered(e -> {
            if (!isActive) item.setStyle("-fx-background-color: #F1F5F9; -fx-background-radius: 6px; -fx-cursor: hand;");
        });
        item.setOnMouseExited(e -> {
            if (!isActive) item.setStyle("-fx-background-color: transparent; -fx-background-radius: 6px; -fx-cursor: hand;");
        });

        return item;
    }

    private void renderCachedMessagesOrFetch() {
        List<ChatMessage> cached = roomMessagesCache.get(activeTicketId);
        if (cached != null) {
            displayMessages(cached);
        } else {
            messagesBox.getChildren().clear();
            Label lblLoading = new Label("Đang tải tin nhắn...");
            lblLoading.setFont(Font.font("Segoe UI", 12));
            lblLoading.setTextFill(Color.GRAY);
            messagesBox.getChildren().add(lblLoading);
            requestHistory(activeTicketId);
        }
    }

    private void requestHistory(int ticketId) {
        String payload = "CHAT_HISTORY|ticketId=" + ticketId;
        SocketClientManager.getInstance().sendPacket(new Packet(Constants.TYPE_CHAT_HISTORY, payload));
    }

    private void sendMessage() {
        String text = txtInput.getText();
        if (text == null || text.trim().isEmpty()) return;

        text = text.trim();
        txtInput.clear();

        // Gửi qua TCP Socket Framing 0xCAFE với Attachment nhị phân UTF-8
        String header = "CHAT_SEND|ticketId=" + activeTicketId;
        byte[] contentBytes = text.getBytes(StandardCharsets.UTF_8);

        Packet packet = new Packet(Constants.TYPE_CHAT_SEND, header, contentBytes);
        SocketClientManager.getInstance().sendPacket(packet);
    }

    /**
     * Nhận Server-Push tin nhắn mới
     */
    public void onChatPush(Packet packet) {
        Map<String, String> p = packet.parseParams();
        int ticketId = 0;
        try { ticketId = Integer.parseInt(p.get("ticketId")); } catch (Exception ignored) {}

        String content = "";
        if (packet.hasAttachment()) {
            content = new String(packet.getAttachmentBytes(), StandardCharsets.UTF_8);
        }

        int msgId = 0;
        try { msgId = Integer.parseInt(p.get("msgId")); } catch (Exception ignored) {}
        int senderId = 0;
        try { senderId = Integer.parseInt(p.get("senderId")); } catch (Exception ignored) {}

        ChatMessage msg = new ChatMessage(
            msgId,
            ticketId > 0 ? ticketId : null,
            senderId,
            p.get("senderName"),
            p.get("senderRole"),
            content,
            p.get("sentAt")
        );

        // Lưu vào cache
        roomMessagesCache.computeIfAbsent(ticketId, k -> new ArrayList<>()).add(msg);

        if (ticketId == activeTicketId) {
            appendMessageBubble(msg);
            Platform.runLater(() -> scrollPane.setVvalue(1.0));
        } else {
            unreadRooms.add(ticketId);
            renderChannelList();
        }
    }

    /**
     * Nhận phản hồi lịch sử tin nhắn
     */
    public void onChatHistoryResponse(Packet packet) {
        Map<String, String> p = packet.parseParams();
        int ticketId = 0;
        try { ticketId = Integer.parseInt(p.get("ticketId")); } catch (Exception ignored) {}

        if (packet.hasAttachment()) {
            String json = new String(packet.getAttachmentBytes(), StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<ChatMessage>>(){}.getType();
            List<ChatMessage> list = gson.fromJson(json, listType);

            if (list == null) list = new ArrayList<>();
            roomMessagesCache.put(ticketId, list);

            if (ticketId == activeTicketId) {
                displayMessages(list);
            }
        }
    }

    private void displayMessages(List<ChatMessage> messages) {
        messagesBox.getChildren().clear();

        if (messages.isEmpty()) {
            Label lblEmpty = new Label("Chưa có tin nhắn nào trong phòng này. Hãy bắt đầu trao đổi!");
            lblEmpty.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
            lblEmpty.setTextFill(Color.GRAY);
            lblEmpty.setPadding(new Insets(20));
            messagesBox.getChildren().add(lblEmpty);
            return;
        }

        for (ChatMessage msg : messages) {
            messagesBox.getChildren().add(createMessageBubble(msg));
        }

        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private void appendMessageBubble(ChatMessage msg) {
        // Nếu trước đó đang hiển thị label rỗng thì xóa đi
        if (messagesBox.getChildren().size() == 1 && messagesBox.getChildren().get(0) instanceof Label) {
            messagesBox.getChildren().clear();
        }
        messagesBox.getChildren().add(createMessageBubble(msg));
    }

    private Node createMessageBubble(ChatMessage msg) {
        boolean isMe = (msg.getSenderId() == currentUser.getUserId());

        HBox row = new HBox(10);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 0, 2, 0));

        VBox bubbleBox = new VBox(4);
        bubbleBox.setMaxWidth(480);

        // Header thông tin người gửi nếu là người khác
        if (!isMe) {
            HBox senderInfo = new HBox(6);
            senderInfo.setAlignment(Pos.CENTER_LEFT);

            Label lblSender = new Label(msg.getSenderName());
            lblSender.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            lblSender.setTextFill(Color.web("#1E293B"));

            Label badgeRole = createRoleBadge(msg.getSenderRole());
            senderInfo.getChildren().addAll(lblSender, badgeRole);
            bubbleBox.getChildren().add(senderInfo);
        }

        // Nội dung tin nhắn
        Label lblContent = new Label(msg.getContent());
        lblContent.setFont(Font.font("Segoe UI", 13));
        lblContent.setWrapText(true);

        // Timestamp
        String timeDisplay = formatTime(msg.getSentAt());
        Label lblTime = new Label(timeDisplay);
        lblTime.setFont(Font.font("Segoe UI", 10));

        VBox cardContent = new VBox(4);
        cardContent.setPadding(new Insets(8, 12, 8, 12));

        if (isMe) {
            cardContent.setStyle("-fx-background-color: #1E5AA8; -fx-background-radius: 12px 12px 2px 12px;");
            lblContent.setTextFill(Color.WHITE);
            lblTime.setTextFill(Color.web("#E2E8F0"));
            lblTime.setAlignment(Pos.CENTER_RIGHT);
        } else {
            cardContent.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0; -fx-border-radius: 12px 12px 12px 2px; -fx-background-radius: 12px 12px 12px 2px;");
            lblContent.setTextFill(Color.web("#0F172A"));
            lblTime.setTextFill(Color.web("#64748B"));
            lblTime.setAlignment(Pos.CENTER_LEFT);
        }

        cardContent.getChildren().addAll(lblContent, lblTime);
        bubbleBox.getChildren().add(cardContent);

        row.getChildren().add(bubbleBox);
        return row;
    }

    private Label createRoleBadge(String role) {
        Label badge = new Label();
        badge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
        badge.setPadding(new Insets(2, 6, 2, 6));

        if (Constants.ROLE_ADMIN.equalsIgnoreCase(role)) {
            badge.setText("👑 Quản lý / BGH");
            badge.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #991B1B; -fx-background-radius: 10px;");
        } else if (Constants.ROLE_RESPONDER.equalsIgnoreCase(role)) {
            badge.setText("🔧 Kỹ thuật viên");
            badge.setStyle("-fx-background-color: #D1FAE5; -fx-text-fill: #065F46; -fx-background-radius: 10px;");
        } else {
            badge.setText("👤 Người báo");
            badge.setStyle("-fx-background-color: #E0F2FE; -fx-text-fill: #0369A1; -fx-background-radius: 10px;");
        }
        return badge;
    }

    private String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            // raw dạng: "2026-10-16 21:30:15.123"
            if (raw.length() >= 16) {
                return raw.substring(11, 16); // "21:30"
            }
        } catch (Exception ignored) {}
        return raw;
    }
}
