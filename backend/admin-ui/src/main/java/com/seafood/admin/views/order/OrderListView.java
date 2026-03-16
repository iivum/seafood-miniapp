package com.seafood.admin.views.order;

import com.seafood.admin.client.OrderClient;
import com.seafood.admin.client.OrderResponse;
import com.seafood.admin.views.main.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
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
        add(new H2("订单列表"), grid);
        configureGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setColumns("id", "userId", "totalPrice", "status", "shippingAddress");
        
        grid.addComponentColumn(order -> {
            Button shipButton = new Button("发货");
            shipButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            shipButton.setEnabled("PAID".equals(order.getStatus()));
            shipButton.addClickListener(click -> {
                Notification.show("订单 " + order.getId() + " 已发货 (模拟)");
                updateList();
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
