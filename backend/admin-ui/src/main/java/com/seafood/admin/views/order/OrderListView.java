package com.seafood.admin.views.order;

import com.seafood.admin.client.OrderClient;
import com.seafood.admin.client.OrderResponse;
import com.seafood.admin.views.main.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "orders", layout = MainLayout.class)
@PageTitle("订单管理 | 海鲜商城")
public class OrderListView extends VerticalLayout {

    private final OrderClient orderClient;
    private final Grid<OrderResponse> grid = new Grid<>(OrderResponse.class);

    @Autowired
    public OrderListView(OrderClient orderClient) {
        this.orderClient = orderClient;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("padding", "28px 32px");
        
        add(createViewHeader(), grid);
        configureGrid();
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

    private void configureGrid() {
        grid.setSizeFull();
        grid.setColumns("id", "userId", "totalPrice", "status", "shippingAddress");

        grid.addColumn(order -> {
            if (order.getItems() == null || order.getItems().isEmpty()) {
                return "无商品";
            }
            return order.getItems().size() + " 件商品";
        }).setHeader("商品数量").setAutoWidth(true);

        grid.addComponentColumn(order -> {
            Button shipButton = new Button("发货");
            shipButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            shipButton.setEnabled("PAID".equals(order.getStatus()));
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
        updateList();
    }

    private void updateList() {
        grid.setItems(orderClient.getAllOrders());
    }
}