package com.incidentreport.client.ui;

import com.incidentreport.common.Constants;
import com.incidentreport.common.model.IncidentTicket;
import com.incidentreport.common.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.FileInputStream;
import java.util.function.Consumer;

/**
 * Hộp thoại Chi tiết sự cố toàn diện (Incident Detail Dialog)
 * Hiển thị toàn bộ thông tin đầy đủ, rõ ràng, không bị cắt xén,
 * tích hợp các nút hành động nghiệp vụ (Chat 1-1, Tiếp nhận, Hoàn tất, Chỉ định, Hủy).
 */
public class IncidentDetailDialog extends Dialog<Void> {

    public IncidentDetailDialog(Window owner,
                                IncidentTicket ticket,
                                User currentUser,
                                Consumer<IncidentTicket> onOpenChat,
                                Consumer<IncidentTicket> onClaim,
                                Consumer<IncidentTicket> onResolve,
                                Consumer<IncidentTicket> onAssign,
                                Consumer<IncidentTicket> onCancel) {

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Chi tiết sự cố · " + ticket.getTicketCode());

        DialogPane pane = getDialogPane();
        pane.getStyleClass().add("dialog-pane");
        try {
            pane.getStylesheets().add(getClass().getResource("/css/sids-theme.css").toExternalForm());
        } catch (Exception ignored) {}

        pane.setPrefWidth(680);
        pane.setMaxWidth(720);

        // Header Panel (Nền mực xanh, font Lora serif)
        VBox headerPanel = new VBox(6);
        headerPanel.setStyle("-fx-background-color: -ink-900; -fx-padding: 16 24;");

        HBox topTitleRow = new HBox(12);
        topTitleRow.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label("CHI TIẾT SỰ CỐ · " + ticket.getTicketCode());
        lblTitle.setStyle("-fx-font-family: 'Lora', Georgia, serif; -fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: white;");

        Region hSp = new Region();
        HBox.setHgrow(hSp, Priority.ALWAYS);

        Label deptBadge = ComponentFactory.createDepartmentBadge(ticket.getTargetDepartment());
        Label statusBadge = ComponentFactory.createStatusBadge(ticket.getStatus());

        topTitleRow.getChildren().addAll(lblTitle, hSp, deptBadge, statusBadge);

        HBox metaRow = new HBox(16);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label lblRoomMeta = new Label("Phòng: " + (ticket.getRoomLocation() != null ? ticket.getRoomLocation() : "Chưa rõ"));
        lblRoomMeta.setStyle("-fx-text-fill: -brass-100; -fx-font-weight: bold; -fx-font-size: 12.5px;");

        Label lblTimeMeta = new Label("Thời gian báo: " + (ticket.getCreatedAt() != null ? ticket.getCreatedAt() : "N/A"));
        lblTimeMeta.setStyle("-fx-text-fill: #AFBBD6; -fx-font-size: 12px;");

        metaRow.getChildren().addAll(lblRoomMeta, lblTimeMeta);
        headerPanel.getChildren().addAll(topTitleRow, metaRow);

        pane.setHeader(headerPanel);

        // Body Content (Scrollable nếu màn hình nhỏ hoặc mô tả dài)
        VBox body = new VBox(16);
        body.setPadding(new Insets(16, 20, 16, 20));

        // 1. Thẻ Thông tin hiện trường & Danh mục sự cố
        VBox cardIncident = new VBox(10);
        cardIncident.getStyleClass().add("card");
        cardIncident.setPadding(new Insets(14, 16, 14, 16));

        Label sec1Title = new Label("NỘI DUNG HIỆN TRƯỜNG PHẢN ÁNH");
        sec1Title.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -brass-700;");

        HBox catRow = new HBox(8);
        catRow.setAlignment(Pos.CENTER_LEFT);
        Label lblCatTag = new Label("Danh mục:");
        lblCatTag.setStyle("-fx-font-weight: bold; -fx-text-fill: -text-600; -fx-font-size: 12.5px;");
        Label lblCatVal = new Label(ticket.getCategoryName() != null ? ticket.getCategoryName() : ticket.getCategoryCode());
        lblCatVal.setStyle("-fx-font-weight: bold; -fx-text-fill: -ink-900; -fx-font-size: 13.5px;");
        catRow.getChildren().addAll(lblCatTag, lblCatVal);

        VBox descBox = new VBox(6);
        descBox.setStyle("-fx-background-color: -paper-100; -fx-border-color: -paper-300; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12 14;");

        Label descTitle = new Label("Mô tả chi tiết của người báo:");
        descTitle.setStyle("-fx-font-weight: 600; -fx-font-size: 11.5px; -fx-text-fill: -text-600;");

        Label lblDesc = new Label(ticket.getDescription() != null && !ticket.getDescription().trim().isEmpty()
                ? ticket.getDescription() : "Không có mô tả chi tiết kèm theo.");
        lblDesc.setWrapText(true);
        lblDesc.setStyle("-fx-font-size: 13px; -fx-text-fill: -text-900; -fx-line-spacing: 3px;");
        descBox.getChildren().addAll(descTitle, lblDesc);

        cardIncident.getChildren().addAll(sec1Title, catRow, descBox);

        // Hiển thị ảnh hiện trường nếu có
        if (ticket.getImageFilePath() != null && !ticket.getImageFilePath().trim().isEmpty()) {
            File imgFile = new File(ticket.getImageFilePath());
            if (imgFile.exists()) {
                VBox imgBox = new VBox(6);
                Label lblImgTag = new Label("Hình ảnh hiện trường đính kèm:");
                lblImgTag.setStyle("-fx-font-weight: bold; -fx-font-size: 11.5px; -fx-text-fill: -text-600;");

                ImageView iv = new ImageView();
                try {
                    Image img = new Image(new FileInputStream(imgFile));
                    iv.setImage(img);
                    iv.setFitWidth(360);
                    iv.setFitHeight(200);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                    iv.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 6, 0, 0, 2);");
                } catch (Exception ex) {
                    System.err.println("Không thể đọc ảnh: " + ex.getMessage());
                }
                imgBox.getChildren().addAll(lblImgTag, iv);
                cardIncident.getChildren().add(imgBox);
            }
        }

        // 2. Thẻ Thông tin Người báo & Cán bộ tiếp nhận (2 cột song song)
        HBox columnsBox = new HBox(14);
        HBox.setHgrow(columnsBox, Priority.ALWAYS);

        // Cột Người báo cáo
        VBox colReporter = new VBox(8);
        HBox.setHgrow(colReporter, Priority.ALWAYS);
        colReporter.getStyleClass().add("card");
        colReporter.setPadding(new Insets(12, 14, 12, 14));

        Label secRepTitle = new Label("NGƯỜI BÁO CÁO");
        secRepTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -brass-700;");

        ComponentFactory.NameParts repParts = ComponentFactory.parseNameAndTitle(ticket.getReporterName());

        HBox repPersonRow = new HBox(10);
        repPersonRow.setAlignment(Pos.CENTER_LEFT);
        Node repAvatar = ComponentFactory.createInitialsAvatar(ticket.getReporterName(), false);

        VBox repTextBox = new VBox(2);
        Label lblRepName = new Label(repParts.displayName);
        lblRepName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: -text-900;");
        Label lblRepRole = new Label(repParts.roleOrTitle != null ? repParts.roleOrTitle : "Giáo viên / Học sinh");
        lblRepRole.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-600;");
        repTextBox.getChildren().addAll(lblRepName, lblRepRole);

        repPersonRow.getChildren().addAll(repAvatar, repTextBox);

        HBox repPhoneRow = new HBox(6);
        repPhoneRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon phoneIcon = ComponentFactory.createIcon("mdi2p-phone", 14, Color.web("#8A6218"));
        String phoneStr = ticket.getReporterPhone() != null && !ticket.getReporterPhone().isEmpty() ? ticket.getReporterPhone() : "Chưa cập nhật SĐT";
        Label lblRepPhone = new Label(phoneStr);
        lblRepPhone.setStyle("-fx-font-size: 12px; -fx-text-fill: -text-900; -fx-font-weight: 500;");
        repPhoneRow.getChildren().addAll(phoneIcon, lblRepPhone);

        colReporter.getChildren().addAll(secRepTitle, repPersonRow, repPhoneRow);

        // Cột Cán bộ tiếp nhận
        VBox colStaff = new VBox(8);
        HBox.setHgrow(colStaff, Priority.ALWAYS);
        colStaff.getStyleClass().add("card");
        colStaff.setPadding(new Insets(12, 14, 12, 14));

        Label secStaffTitle = new Label("CÁN BỘ PHỤ TRÁCH XỬ LÝ");
        secStaffTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -brass-700;");

        if (ticket.getAssignedStaffName() != null && !ticket.getAssignedStaffName().trim().isEmpty() &&
                !"Chưa tiếp nhận".equals(ticket.getAssignedStaffName())) {

            ComponentFactory.NameParts staffParts = ComponentFactory.parseNameAndTitle(ticket.getAssignedStaffName());

            HBox staffPersonRow = new HBox(10);
            staffPersonRow.setAlignment(Pos.CENTER_LEFT);
            Node staffAvatar = ComponentFactory.createInitialsAvatar(ticket.getAssignedStaffName(), false);

            VBox staffTextBox = new VBox(2);
            Label lblStaffName = new Label(staffParts.displayName);
            lblStaffName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: -text-900;");
            Label lblStaffRole = new Label(staffParts.roleOrTitle != null ? staffParts.roleOrTitle : "Cán bộ chuyên môn " + ticket.getTargetDepartment());
            lblStaffRole.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -dept-tech; -fx-font-weight: 600;");
            staffTextBox.getChildren().addAll(lblStaffName, lblStaffRole);

            staffPersonRow.getChildren().addAll(staffAvatar, staffTextBox);

            VBox staffTimeline = new VBox(4);
            if (ticket.getAcceptedAt() != null) {
                Label lblAcc = new Label("Tiếp nhận lúc: " + ticket.getAcceptedAt());
                lblAcc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -text-600;");
                staffTimeline.getChildren().add(lblAcc);
            }
            if (ticket.getResolvedAt() != null) {
                Label lblRes = new Label("Hoàn tất lúc: " + ticket.getResolvedAt());
                lblRes.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -chalk-600; -fx-font-weight: bold;");
                staffTimeline.getChildren().add(lblRes);
            }

            colStaff.getChildren().addAll(secStaffTitle, staffPersonRow, staffTimeline);
        } else {
            HBox emptyStaffBox = new HBox(8);
            emptyStaffBox.setAlignment(Pos.CENTER_LEFT);
            emptyStaffBox.setStyle("-fx-background-color: -paper-100; -fx-border-color: -paper-300; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 10 12;");
            FontIcon clockIcon = ComponentFactory.createIcon("mdi2c-clock-outline", 16, Color.web("#8A6218"));
            Label lblWaiting = new Label("Chưa có cán bộ tiếp nhận\nĐang trong hàng đợi chờ xử lý");
            lblWaiting.setStyle("-fx-font-style: italic; -fx-font-size: 12px; -fx-text-fill: -text-600;");
            emptyStaffBox.getChildren().addAll(clockIcon, lblWaiting);

            colStaff.getChildren().addAll(secStaffTitle, emptyStaffBox);
        }

        columnsBox.getChildren().addAll(colReporter, colStaff);

        // 3. Khối kết quả nghiệm thu / ghi chú giải quyết (nếu có)
        if (ticket.getResolutionNote() != null && !ticket.getResolutionNote().trim().isEmpty()) {
            VBox noteCard = new VBox(6);
            noteCard.getStyleClass().add("card");
            noteCard.setPadding(new Insets(12, 14, 12, 14));

            Label lblNoteTitle = new Label("KẾT QUẢ NGHIỆM THU / GHI CHÚ XỬ LÝ:");
            lblNoteTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -chalk-600;");

            Label lblNoteContent = new Label(ticket.getResolutionNote());
            lblNoteContent.setWrapText(true);
            lblNoteContent.setStyle("-fx-font-size: 12.5px; -fx-text-fill: -text-900;");

            noteCard.getChildren().addAll(lblNoteTitle, lblNoteContent);
            body.getChildren().addAll(cardIncident, columnsBox, noteCard);
        } else {
            body.getChildren().addAll(cardIncident, columnsBox);
        }

        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        sp.setPrefHeight(420);

        pane.setContent(sp);

        // Buttons Bar
        ButtonType btnCloseType = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().add(btnCloseType);

        Button btnClose = (Button) pane.lookupButton(btnCloseType);
        if (btnClose != null) {
            btnClose.getStyleClass().addAll("btn-secondary", "btn-sm");
        }

        // Custom Action Bar in ButtonBar
        ButtonBar buttonBar = (ButtonBar) pane.lookup(".button-bar");
        if (buttonBar != null) {
            // Nút "Nhắn tin 1-1"
            if (onOpenChat != null) {
                Button btnChat = new Button("Nhắn tin 1-1");
                btnChat.getStyleClass().addAll("btn-primary", "btn-sm");
                btnChat.setGraphic(ComponentFactory.createIcon("mdi2m-message-text-outline", 15, Color.WHITE));
                btnChat.setOnAction(e -> {
                    close();
                    onOpenChat.accept(ticket);
                });
                buttonBar.getButtons().add(0, btnChat);
            }

            // Nút hành động nghiệp vụ cụ thể
            if (currentUser != null) {
                String role = currentUser.getRole();
                String status = ticket.getStatus();

                // 1. Responder: Tiếp nhận nếu đang PENDING
                if (Constants.ROLE_RESPONDER.equalsIgnoreCase(role) && Constants.STATUS_PENDING.equalsIgnoreCase(status)) {
                    if (onClaim != null) {
                        Button btnClaim = new Button("Tiếp nhận ca này");
                        btnClaim.getStyleClass().addAll("btn-success", "btn-sm");
                        btnClaim.setGraphic(ComponentFactory.createIcon("mdi2h-hand-pointing-right", 15, Color.WHITE));
                        btnClaim.setOnAction(e -> {
                            close();
                            onClaim.accept(ticket);
                        });
                        buttonBar.getButtons().add(0, btnClaim);
                    }
                }

                // 2. Responder: Báo cáo hoàn tất nếu đang IN_PROGRESS và được phân công cho mình
                if (Constants.ROLE_RESPONDER.equalsIgnoreCase(role) && Constants.STATUS_IN_PROGRESS.equalsIgnoreCase(status)) {
                    if (ticket.getAssignedStaffId() != null && ticket.getAssignedStaffId() == currentUser.getUserId()) {
                        if (onResolve != null) {
                            Button btnResolve = new Button("Báo cáo hoàn tất");
                            btnResolve.getStyleClass().addAll("btn-success", "btn-sm");
                            btnResolve.setGraphic(ComponentFactory.createIcon("mdi2c-check-circle", 15, Color.WHITE));
                            btnResolve.setOnAction(e -> {
                                close();
                                onResolve.accept(ticket);
                            });
                            buttonBar.getButtons().add(0, btnResolve);
                        }
                    }
                }

                // 3. Admin: Chỉ định cán bộ nếu đang PENDING
                if (Constants.ROLE_ADMIN.equalsIgnoreCase(role) && Constants.STATUS_PENDING.equalsIgnoreCase(status)) {
                    if (onAssign != null) {
                        Button btnAssign = new Button("Chỉ định cán bộ");
                        btnAssign.getStyleClass().addAll("btn-brass", "btn-sm");
                        btnAssign.setGraphic(ComponentFactory.createIcon("mdi2a-account-arrow-right", 15, Color.web("#8A6218")));
                        btnAssign.setOnAction(e -> {
                            close();
                            onAssign.accept(ticket);
                        });
                        buttonBar.getButtons().add(0, btnAssign);
                    }
                }

                // 4. Reporter: Hủy sự cố nếu đang PENDING
                if (Constants.ROLE_REPORTER.equalsIgnoreCase(role) && Constants.STATUS_PENDING.equalsIgnoreCase(status)) {
                    if (onCancel != null) {
                        Button btnCancelAction = new Button("Hủy sự cố này");
                        btnCancelAction.getStyleClass().addAll("btn-secondary", "btn-sm");
                        btnCancelAction.setStyle("-fx-text-fill: -st-pending; -fx-border-color: -st-pending;");
                        btnCancelAction.setGraphic(ComponentFactory.createIcon("mdi2c-close-circle-outline", 15, Color.web("#A8402F")));
                        btnCancelAction.setOnAction(e -> {
                            close();
                            onCancel.accept(ticket);
                        });
                        buttonBar.getButtons().add(0, btnCancelAction);
                    }
                }
            }
        }
    }
}

