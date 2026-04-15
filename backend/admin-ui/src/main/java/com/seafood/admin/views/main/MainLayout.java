package com.seafood.admin.views.main;

import com.seafood.admin.views.dashboard.DashboardView;
import com.seafood.admin.views.order.OrderListView;
import com.seafood.admin.views.product.ProductListView;
import com.seafood.admin.views.user.UserListView;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;

public class MainLayout extends AppLayout {

    public MainLayout() {
        createHeader();
        createDrawer();
    }

    private void createHeader() {
        H1 logo = new H1("大海的味道 - 商家管理后台");
        logo.addClassNames("text-l", "m-m");

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), logo);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.addClassNames("py-0", "px-m");

        addToNavbar(header);
    }

    private void createDrawer() {
        VerticalLayout layout = new VerticalLayout(
                new RouterLink("数据概览", DashboardView.class),
                new RouterLink("商品管理", ProductListView.class),
                new RouterLink("订单管理", OrderListView.class),
                new RouterLink("用户管理", UserListView.class)
        );
        addToDrawer(layout);
    }
}