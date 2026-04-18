package com.seafood.admin.views.order;

import com.seafood.admin.client.OrderClient;
import com.seafood.admin.client.OrderResponse;
import com.seafood.admin.views.main.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Route(value = "orders", layout = MainLayout.class)
@PageTitle("订单管理 | 海鲜商城")
public class OrderListView extends VerticalLayout {

    private final OrderClient orderClient;
    private final Grid<OrderResponse> grid = new Grid<>(OrderResponse.class);
    private ComboBox<String> statusFilter = new ComboBox<>();
    private TextField searchField = new TextField();

    // Status options for filter
    private static final List<String> STATUS_OPTIONS = List.of(
        "全部状态", "待支付", "已支付", "已发货", "已完成", "已取消", "已退款"
    );
    
    // Map display status to internal status
    private static final java.util.Map<String, String> STATUS_MAP = java.util.Map.of(
        "待支付", "PENDING_PAYMENT",
        "已支付", "PAID",
        "已发货", "SHIPPED",
        "已完成", "DELIVERED",
        "已取消", "CANCELLED",
        "已退款", "REFUNDED"
    );

    @Autowired
    public OrderListView(OrderClient orderClient) {
        this.orderClient = orderClient;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
            .set("padding", "28px 32px")
            .set("background", "#F7F8FC");

        add(createViewHeader(), createFilterBar(), grid);
        configureGrid();
        updateList();
    }

    private HorizontalLayout createViewHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.addClassName("view-header");
        
        H2 title = new H2("订单管理");
        title.getStyle().set("margin", "0").set("font-size", "1.5rem").set("font-weight", "700").set("color", "#0c1929");
        
        Div subtitle = new Div();
        subtitle.setText("管理所有订单，处理发货请求");
        subtitle.getStyle().set("font-size", "0.85rem").set("color", "#8aaabf").set("margin-top", "4px");
        
        VerticalLayout titleArea = new VerticalLayout(title, subtitle);
        titleArea.setMargin(false);
        titleArea.setSpacing(false);
        
        header.add(titleArea);
        return header;
    }

    private HorizontalLayout createFilterBar() {
        HorizontalLayout filterBar = new HorizontalLayout();
        filterBar.addClassName("filter-row");
        filterBar.setWidthFull();
        filterBar.setAlignItems(FlexComponent.Alignment.CENTER);
        
        // Status filter
        statusFilter.setLabel("订单状态");
        statusFilter.setItems(STATUS_OPTIONS);
        statusFilter.setValue("全部状态");
        statusFilter.setClearButtonVisible(true);
        statusFilter.addValueChangeListener(e -> updateList());
        
        // Search field
        searchField.setPlaceholder("搜索订单号/用户ID...");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> updateList());
        
        filterBar.add(statusFilter, searchField);
        return filterBar;
    }

    private void configureGrid() {
        grid.setSizeFull();
        
        // Order ID column
        grid.addColumn(OrderResponse::getId).setHeader("订单ID").setAutoWidth(true).setSortable(true);
        
        // Order number column
        grid.addColumn(OrderResponse::getOrderNumber).setHeader("订单号").setAutoWidth(true).setSortable(true);
        
        // User ID column
        grid.addColumn(OrderResponse::getUserId).setHeader("用户ID").setAutoWidth(true).setSortable(true);
        
        // Total price - formatted
        grid.addColumn(order -> formatPrice(order.getTotalPrice())).setHeader("总价").setAutoWidth(true).setSortable(true);
        
        // Status - formatted display
        grid.addColumn(OrderResponse::getDisplayStatus).setHeader("状态").setAutoWidth(true).setSortable(true);
        
        // Shipping address - formatted
        grid.addColumn(order -> formatShippingAddress(order)).setHeader("收货地址").setAutoWidth(true);
        
        // Item count
        grid.addColumn(order -> {
            if (order.getItems() == null || order.getItems().isEmpty()) {
                return "无商品";
            }
            return order.getItems().size() + " 件商品";
        }).setHeader("商品数量").setAutoWidth(true);

        // Action column
        grid.addComponentColumn(order -> {
            Button shipButton = new Button("发货");
            shipButton.getStyle()
                .set("background", "linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%)")
                .set("border-radius", "8px")
                .set("color", "#ffffff")
                .set("box-shadow", "0 2px 8px rgba(255, 107, 107, 0.3)");
            shipButton.setEnabled("PAID".equals(order.getDisplayStatus()));
            shipButton.addClickListener(click -> {
                try {
                    orderClient.updateOrderStatus(order.getId(), "SHIPPED");
                    Notification.show("订单 " + order.getId() + " 已发货");
                    updateList();
                } catch (Exception e) {
                    Notification.show("发货失败: " + e.getMessage());
                }
            });
            return shipButton;
        }).setHeader("操作");

        grid.getColumns().forEach(col -> col.setAutoWidth(true));
        
        // Enable sorting
        grid.setSortableColumns("id", "orderNumber", "userId", "totalPrice", "displayStatus");
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "¥0.00";
        return "¥" + price.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }

    private String formatShippingAddress(OrderResponse order) {
        if (order.getShippingAddress() == null) {
            return "-";
        }
        OrderResponse.AddressResponse addr = order.getShippingAddress();
        StringBuilder sb = new StringBuilder();
        if (addr.getReceiverName() != null) {
            sb.append(addr.getReceiverName());
        }
        if (addr.getCity() != null && addr.getDistrict() != null) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(addr.getCity()).append(addr.getDistrict());
        }
        if (addr.getAddress() != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(addr.getAddress());
        }
        return sb.length() > 0 ? sb.toString() : "-";
    }

    private void updateList() {
        var orders = orderClient.getAllOrders();
        
        // Filter by status
        String statusFilterValue = statusFilter.getValue();
        if (statusFilterValue != null && !statusFilterValue.equals("全部状态")) {
            String internalStatus = STATUS_MAP.get(statusFilterValue);
            if (internalStatus != null) {
                orders = orders.stream()
                    .filter(o -> internalStatus.equals(o.getDisplayStatus()))
                    .collect(Collectors.toList());
            }
        }
        
        // Filter by search term
        String searchTerm = searchField.getValue();
        if (searchTerm != null && !searchTerm.isBlank()) {
            String lowerSearch = searchTerm.toLowerCase();
            orders = orders.stream()
                .filter(o -> 
                    (o.getId() != null && o.getId().toLowerCase().contains(lowerSearch)) ||
                    (o.getOrderNumber() != null && o.getOrderNumber().toLowerCase().contains(lowerSearch)) ||
                    (o.getUserId() != null && o.getUserId().toLowerCase().contains(lowerSearch)))
                .collect(Collectors.toList());
        }
        
        grid.setItems(orders);
    }
}
