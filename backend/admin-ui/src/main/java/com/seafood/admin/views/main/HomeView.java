package com.seafood.admin.views.main;

import com.seafood.admin.views.dashboard.DashboardView;
import com.seafood.admin.views.order.OrderListView;
import com.seafood.admin.views.product.ProductListView;
import com.seafood.admin.views.user.UserListView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "", layout = MainLayout.class)
@PageTitle("首页 | 海鲜商城商家管理")
public class HomeView extends VerticalLayout {

    public HomeView() {
        setSizeFull();
        setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.CENTER);

        add(new H2("欢迎来到商家管理后台"));

        Paragraph desc = new Paragraph("请在左侧菜单选择要进行的操作，或点击下方快速链接");
        add(desc);

        HorizontalLayout quickLinks = new HorizontalLayout();
        quickLinks.setSpacing(true);

        Button dashboardBtn = new Button("数据概览");
        dashboardBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dashboardBtn.addClickListener(e -> dashboardBtn.getUI().ifPresent(ui -> ui.navigate(DashboardView.class)));

        Button productsBtn = new Button("商品管理");
        productsBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        productsBtn.addClickListener(e -> productsBtn.getUI().ifPresent(ui -> ui.navigate(ProductListView.class)));

        Button ordersBtn = new Button("订单管理");
        ordersBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        ordersBtn.addClickListener(e -> ordersBtn.getUI().ifPresent(ui -> ui.navigate(OrderListView.class)));

        Button usersBtn = new Button("用户管理");
        usersBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        usersBtn.addClickListener(e -> usersBtn.getUI().ifPresent(ui -> ui.navigate(UserListView.class)));

        quickLinks.add(dashboardBtn, productsBtn, ordersBtn, usersBtn);
        add(quickLinks);

        add(new H4("功能说明"));
        add(createFeatureCard("数据概览", "查看商品、订单、用户统计数据和订单状态分布"));
        add(createFeatureCard("商品管理", "添加、编辑、删除商品，设置上下架状态"));
        add(createFeatureCard("订单管理", "查看订单列表，处理发货操作"));
        add(createFeatureCard("用户管理", "查看用户列表，设置管理员权限"));
    }

    private VerticalLayout createFeatureCard(String title, String description) {
        VerticalLayout card = new VerticalLayout();
        card.addClassName("feature-card");
        card.setWidth("400px");
        card.setPadding(true);

        H4 titleLabel = new H4(title);
        titleLabel.getStyle().set("color", "#1976d2");
        Paragraph descLabel = new Paragraph(description);
        descLabel.getStyle().set("color", "#666");

        card.add(titleLabel, descLabel);
        return card;
    }
}