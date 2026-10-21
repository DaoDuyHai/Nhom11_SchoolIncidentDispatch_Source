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
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Giao diện Khung Chat 1-1 Chuyên Dụng (Dedicated 1-on-1 Chat Panel):
 * - Chỉ mở cho Người báo cáo (Reporter) và Người nhận báo cáo (Responder) trao đổi trực tiếp.
 * - Khi sự cố hoàn tất (RESOLVED), phiên chat trên Client sẽ tự động khóa và xóa/đóng lại.
 * - Toàn bộ dữ liệu được lưu vĩnh viễn trên Server để Admin kiểm toán.
 */
public class ChatPanel extends BorderPane implements SocketClientManager.PacketListener {

    private final User currentUser;
    private final Gson gson = new Gson();

    private IncidentTicket activeTicket = null;
    private final List<ChatMessage> messageList = new ArrayList<>();

    // UI Controls
    private final Label lblTicketTitle = new Label("Chưa chọn sự cố để trao đổi");
    private final Label lblTicketSub = new Label("Vui lòng chọn một sự cố từ danh sách");
    private final Label lblResolvedNotice = new Label("🏁 Sự cố đã hoàn tất. Kênh chat 1-1 đã đóng và kết thúc.");
    private final VBox messagesBox = new VBox(10);
    private final ScrollPane scrollPane = new ScrollPane(messagesBox);
    private final TextField txtInput = new TextField();
    private final Button btnSend = new Button("Gửi ✈");
    private final HBox inputBox = new HBox(8);

    public ChatPanel(User currentUser) {
        this.currentUser = currentUser;
        initUI();
        SocketClientManager.getInstance().addListener(this);
    }

    private void initUI() {
        setStyle("-fx-background-color: #F8FAFC;");

        // Top Header
        VBox topBox = new VBox(4);
        topBox.setPadding(new Insets(12, 16, 12, 16));
        topBox.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0; -fx-border-width: 0 0 1 0;");

        lblTicketTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        lblTicketTitle.setTextFill(Color.web("#0F172A"));

        lblTicketSub.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        lblTicketSub.setTextFill(Color.web("#64748B"));

        lblResolvedNotice.setStyle("-fx-background-color: #DEF7EC; -fx-text-fill: #03543F; -fx-padding: 6 12; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: 12px;");
        lblResolvedNotice.setVisible(false);
        lblResolvedNotice.setManaged(false);

        topBox.getChildren().addAll(lblTicketTitle, lblTicketSub, lblResolvedNotice);
        setTop(topBox);

        // Center Messages Area
        messagesBox.setPadding(new Insets(16));
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        setCenter(scrollPane);

        // Bottom Input Area
        inputBox.setPadding(new Insets(12, 16, 12, 16));
        inputBox.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0; -fx-border-width: 1 0 0 0;");
        inputBox.setAlignment(Pos.CENTER);

        txtInput.setPromptText("Nhập tin nhắn trao đổi 1-1...");
        txtInput.setStyle("-fx-background-radius: 20; -fx-padding: 8 16; -fx-border-color: #CBD5E1; -fx-border-radius: 20;");
        HBox.setHgrow(txtInput, Priority.ALWAYS);

        btnSend.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 8 18;");
        btnSend.setOnAction(e -> sendMessage());
        txtInput.setOnAction(e -> sendMessage());

        inputBox.getChildren().addAll(txtInput, btnSend);
        setBottom(inputBox);

        setDisabledState(true);
    }

    public void openTicketChat(IncidentTicket ticket) {
        if (ticket == null) return;
        this.activeTicket = ticket;
        messageList.clear();
        messagesBox.getChildren().clear();

        lblTicketTitle.setText(String.format("💬 Trao đổi 1-1: [%s] %s - %s",
                ticket.getTicketCode(), ticket.getRoomLocation(),
                ticket.getCategoryName() != null ? ticket.getCategoryName() : ticket.getCategoryCode()));

        String partnerInfo = "";
        if (Constants.ROLE_REPORTER.equalsIgnoreCase(currentUser.getRole())) {
            partnerInfo = "Cán bộ phụ trách: " + (ticket.getAssignedStaffName() != null ? ticket.getAssignedStaffName() : "Đang chờ tiếp nhận");
        } else if (Constants.ROLE_RESPONDER.equalsIgnoreCase(currentUser.getRole())) {
            partnerInfo = "Người báo cáo: " + ticket.getReporterName() + (ticket.getReporterPhone() != null ? " (" + ticket.getReporterPhone() + ")" : "");
        } else {
            partnerInfo = String.format("Admin Giám Sát: %s ⇄ %s", ticket.getReporterName(),
                    ticket.getAssignedStaffName() != null ? ticket.getAssignedStaffName() : "Chưa tiếp nhận");
        }
        lblTicketSub.setText(partnerInfo);

        boolean isResolved = Constants.STATUS_RESOLVED.equalsIgnoreCase(ticket.getStatus())
                || Constants.STATUS_CANCELLED.equalsIgnoreCase(ticket.getStatus());

        if (isResolved) {
            lblResolvedNotice.setVisible(true);
            lblResolvedNotice.setManaged(true);
            setDisabledState(true);
            txtInput.setPromptText("Sự cố đã giải quyết xong. Kênh chat 1-1 đã đóng.");
        } else {
            lblResolvedNotice.setVisible(false);
            lblResolvedNotice.setManaged(false);
            setDisabledState(false);
            txtInput.setPromptText("Nhập tin nhắn trao đổi 1-1...");
        }

        // Tải lịch sử tin nhắn từ Server
        Packet req = new Packet(Constants.TYPE_CHAT_HISTORY, "CHAT_HISTORY|ticketId=" + ticket.getTicketId());
        SocketClientManager.getInstance().sendPacket(req);
    }

    private void sendMessage() {
        if (activeTicket == null) return;
        String text = txtInput.getText().trim();
        if (text.isEmpty()) return;

        txtInput.clear();
        Packet packet = new Packet(Constants.TYPE_CHAT_SEND,
                "CHAT_SEND|ticketId=" + activeTicket.getTicketId(),
                text.getBytes(StandardCharsets.UTF_8));
        SocketClientManager.getInstance().sendPacket(packet);
    }

    @Override
    public void onPacketReceived(Packet packet) {
        if (activeTicket == null) return;

        if (packet.getType() == Constants.TYPE_CHAT_PUSH) {
            Map<String, String> params = packet.parseParams();
            int tId = Integer.parseInt(params.getOrDefault("ticketId", "0"));
            if (tId == activeTicket.getTicketId()) {
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

                addMessageBubble(msg);
            }
        } else if (packet.getType() == Constants.TYPE_CHAT_HISTORY_RESP) {
            Map<String, String> params = packet.parseParams();
            int tId = Integer.parseInt(params.getOrDefault("ticketId", "0"));
            if (tId == activeTicket.getTicketId() && packet.hasAttachment()) {
                try {
                    String json = new String(packet.getAttachment(), StandardCharsets.UTF_8);
                    Type listType = new TypeToken<List<ChatMessage>>() {}.getType();
                    List<ChatMessage> list = gson.fromJson(json, listType);
                    messagesBox.getChildren().clear();
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
            if (tId == activeTicket.getTicketId()) {
                activeTicket.setStatus(newStatus);
                if (Constants.STATUS_RESOLVED.equalsIgnoreCase(newStatus)) {
                    // Tự động đóng kênh chat theo đúng yêu cầu
                    lblResolvedNotice.setVisible(true);
                    lblResolvedNotice.setManaged(true);
                    setDisabledState(true);
                    txtInput.setPromptText("Sự cố đã giải quyết xong. Kênh chat 1-1 đã đóng.");
                    // Thông báo một bong bóng kết thúc phiên
                    Label banner = new Label("🏁 Kênh trao đổi 1-1 đã hoàn tất và kết thúc tại đây.");
                    banner.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-style: italic; -fx-padding: 8 16; -fx-background-radius: 8;");
                    HBox bannerBox = new HBox(banner);
                    bannerBox.setAlignment(Pos.CENTER);
                    messagesBox.getChildren().add(bannerBox);
                }
            }
        }
    }

    private void addMessageBubble(ChatMessage msg) {
        boolean isMe = (msg.getSenderId() == currentUser.getUserId());

        VBox bubble = new VBox(3);
        bubble.setMaxWidth(450);
        bubble.setPadding(new Insets(8, 14, 8, 14));

        Label lblSender = new Label(msg.getSenderName() + (msg.getSenderRole() != null ? " [" + msg.getSenderRole() + "]" : ""));
        lblSender.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));

        Label lblText = new Label(msg.getContent());
        lblText.setWrapText(true);
        lblText.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));

        Label lblTime = new Label(msg.getSentAt() != null ? msg.getSentAt() : "");
        lblTime.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10));

        if (isMe) {
            bubble.setStyle("-fx-background-color: #2563EB; -fx-background-radius: 14 14 2 14;");
            lblSender.setTextFill(Color.web("#DBEAFE"));
            lblText.setTextFill(Color.WHITE);
            lblTime.setTextFill(Color.web("#BFDBFE"));
        } else {
            bubble.setStyle("-fx-background-color: #E2E8F0; -fx-background-radius: 14 14 14 2;");
            lblSender.setTextFill(Color.web("#1E293B"));
            lblText.setTextFill(Color.web("#0F172A"));
            lblTime.setTextFill(Color.web("#64748B"));
        }

        bubble.getChildren().addAll(lblSender, lblText, lblTime);

        HBox row = new HBox(bubble);
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
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
