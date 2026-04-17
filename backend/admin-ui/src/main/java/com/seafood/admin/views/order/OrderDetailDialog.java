package com.seafood.admin.views.order;

import com.seafood.admin.client.OrderHistoryResponse;
import com.seafood.admin.client.OrderItemResponse;
import com.seafood.admin.client.OrderResponse;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.LocalDateRenderer;
import com.vaadin.flow.data.renderer.TextRenderer;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Dialog for displaying detailed order information.
 * Shows order items, pricing, shipping address, tracking, and history.
 */
public class OrderDetailDialog extends Dialog {

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public OrderDetailDialog(OrderResponse order) {
        setWidth("600px");
        setHeaderTitle("订单详情 - " + truncateId(order.getId()));

        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);
        content.setWidthFull();

        // Order Header Section
        content.add(createSectionHeader("订单信息"));
        content.add(createInfoGrid(Map.of(
            "订单号", order.getOrderNumber() != null ? order.getOrderNumber() : "-",
            "订单状态", formatStatus(order.getStatus()),
            "创建时间", formatDateTime(order.getCreatedAt()),
            "支付时间", formatDateTime(order.getPaidAt()),
            "发货时间", formatDateTime(order.getShippedAt()),
            "收货时间", formatDateTime(order.getDeliveredAt())
        )));

        // Order Items Section
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            content.add(createSectionHeader("商品明细"));
            content.add(createItemsTable(order.getItems()));
        }

        // Pricing Section
        content.add(createSectionHeader("费用明细"));
        content.add(createInfoGrid(Map.of(
            "商品总价", formatPrice(order.getTotalPrice()),
            "运费", formatPrice(order.getShippingFee()),
            "优惠", "-" + formatPrice(order.getDiscountAmount()),
            "实付金额", formatPrice(order.getFinalPrice())
        )));

        // Shipping Address Section
        if (order.getShippingAddress() != null) {
            content.add(createSectionHeader("收货地址"));
            OrderResponse.AddressResponse addr = order.getShippingAddress();
            String fullAddress = String.format("%s %s %s %s",
                addr.getCity() != null ? addr.getCity() : "",
                addr.getDistrict() != null ? addr.getDistrict() : "",
                addr.getAddress() != null ? addr.getAddress() : "",
                addr.getPostalCode() != null ? addr.getPostalCode() : "").trim();
            content.add(createInfoGrid(Map.of(
                "收货人", addr.getReceiverName() != null ? addr.getReceiverName() : "-",
                "联系电话", addr.getPhone() != null ? addr.getPhone() : "-",
                "详细地址", fullAddress
            )));
        }

        // Tracking Section (if shipped)
        if (order.getTrackingNumber() != null && !order.getTrackingNumber().isBlank()) {
            content.add(createSectionHeader("物流信息"));
            content.add(createInfoGrid(Map.of(
                "物流公司", order.getCarrierName() != null ? order.getCarrierName() : "-",
                "运单号", order.getTrackingNumber()
            )));

            // Add tracking URL hint
            Div trackingHint = new Div();
            trackingHint.setText("可在 " + getCarrierName(order.getCarrierCode()) + " 官网查询物流进度");
            trackingHint.getStyle().set("font-size", "0.85rem").set("color", "#666");
            content.add(trackingHint);
        }

        // Payment Info Section
        if (order.getTransactionId() != null && !order.getTransactionId().isBlank()) {
            content.add(createSectionHeader("支付信息"));
            content.add(createInfoGrid(Map.of(
                "交易单号", order.getTransactionId()
            )));
        }

        // Order Note
        if (order.getNote() != null && !order.getNote().isBlank()) {
            content.add(createSectionHeader("订单备注"));
            Div noteDiv = new Div();
            noteDiv.setText(order.getNote());
            noteDiv.getStyle().set("padding", "8px").set("background", "#f5f5f5").set("border-radius", "4px");
            content.add(noteDiv);
        }

        // Order History Timeline
        if (order.getOrderHistory() != null && !order.getOrderHistory().isEmpty()) {
            content.add(createSectionHeader("订单历史"));
            content.add(createHistoryTimeline(order.getOrderHistory()));
        }

        add(content);
    }

    private H4 createSectionHeader(String title) {
        H4 header = new H4(title);
        header.getStyle().set("margin", "16px 0 8px 0").set("font-size", "1rem").set("color", "#333");
        return header;
    }

    private VerticalLayout createInfoGrid(Map<String, String> info) {
        VerticalLayout grid = new VerticalLayout();
        grid.setPadding(false);
        grid.setSpacing(true);
        grid.getStyle().set("background", "#f9f9f9").set("padding", "12px").set("border-radius", "4px");

        for (Map.Entry<String, String> entry : info.entrySet()) {
            Div row = new Div();
            row.getStyle().set("display", "flex").set("justify-content", "space-between").set("gap", "24px");

            Div label = new Div();
            label.setText(entry.getKey() + ":");
            label.getStyle().set("color", "#666").set("font-weight", "500");

            Div value = new Div();
            value.setText(entry.getValue());
            value.getStyle().set("color", "#333");

            row.add(label, value);
            grid.add(row);
        }

        return grid;
    }

    private VerticalLayout createItemsTable(List<OrderItemResponse> items) {
        VerticalLayout table = new VerticalLayout();
        table.setPadding(false);
        table.setSpacing(false);
        table.getStyle().set("border", "1px solid #ddd").set("border-radius", "4px").set("overflow", "hidden");

        // Header row
        Div headerRow = new Div();
        headerRow.getStyle()
            .set("display", "flex")
            .set("background", "#f5f5f5")
            .set("padding", "8px 12px")
            .set("font-weight", "600")
            .set("font-size", "0.85rem");
        headerRow.add(createCell("商品", 3), createCell("单价", 1), createCell("数量", 1), createCell("小计", 1));
        table.add(headerRow);

        // Item rows
        for (OrderItemResponse item : items) {
            Div row = new Div();
            row.getStyle()
                .set("display", "flex")
                .set("padding", "8px 12px")
                .set("border-top", "1px solid #eee")
                .set("font-size", "0.85rem")
                .set("align-items", "center");

            String productName = item.getProductName() != null ? item.getProductName() : "-";
            String price = formatPrice(item.getPrice());
            String qty = String.valueOf(item.getQuantity());
            String subtotal = formatPrice(item.getSubtotal());

            row.add(createCell(productName, 3), createCell(price, 1), createCell(qty, 1), createCell(subtotal, 1));
            table.add(row);
        }

        return table;
    }

    private Div createCell(String text, int flexGrow) {
        Div cell = new Div();
        cell.setText(text);
        cell.getStyle().set("flex", String.valueOf(flexGrow));
        return cell;
    }

    private VerticalLayout createHistoryTimeline(List<OrderHistoryResponse> history) {
        VerticalLayout timeline = new VerticalLayout();
        timeline.setPadding(false);
        timeline.setSpacing(true);

        for (OrderHistoryResponse entry : history) {
            Div entryDiv = new Div();
            entryDiv.getStyle()
                .set("display", "flex")
                .set("gap", "12px")
                .set("padding", "6px 0")
                .set("font-size", "0.85rem")
                .set("border-left", "2px solid #ddd")
                .set("padding-left", "12px")
                .set("margin-left", "6px");

            Div dot = new Div();
            dot.getStyle()
                .set("width", "8px")
                .set("height", "8px")
                .set("border-radius", "50%")
                .set("background", "#0076d7")
                .set("margin-top", "6px")
                .set("flex-shrink", "0");

            Div content = new Div();
            content.getStyle().set("flex", "1");

            Div desc = new Div();
            desc.setText(entry.getDescription());
            desc.getStyle().set("color", "#333");

            Div time = new Div();
            time.setText(formatDateTime(entry.getTimestamp()));
            time.getStyle().set("color", "#999").set("font-size", "0.8rem").set("margin-top", "2px");

            content.add(desc, time);
            entryDiv.add(dot, content);
            timeline.add(entryDiv);
        }

        return timeline;
    }

    private String formatStatus(String status) {
        if (status == null) return "-";
        return switch (status) {
            case "PENDING_PAYMENT" -> "待支付";
            case "PAID" -> "已支付";
            case "SHIPPED" -> "已发货";
            case "DELIVERED" -> "已完成";
            case "CANCELLED" -> "已取消";
            case "REFUNDED" -> "已退款";
            default -> status;
        };
    }

    private String formatDateTime(java.time.LocalDateTime dateTime) {
        if (dateTime == null) return "-";
        return dateTime.format(DATETIME_FORMAT);
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "¥0.00";
        return "¥" + price.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }

    private String getCarrierName(String code) {
        if (code == null) return "物流公司";
        return switch (code) {
            case "SF" -> "顺丰速运";
            case "EMS" -> "邮政EMS";
            case "ZTO" -> "中通快递";
            case "STO" -> "申通快递";
            case "YTO" -> "圆通速递";
            case "JD" -> "京东物流";
            case "YUNDA" -> "韵达快递";
            case "TTK" -> "天天快递";
            default -> "物流公司";
        };
    }

    private String truncateId(String id) {
        if (id == null || id.length() <= 12) {
            return id != null ? id : "";
        }
        return id.substring(0, 12) + "...";
    }
}
