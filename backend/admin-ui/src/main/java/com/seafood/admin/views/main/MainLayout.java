package com.seafood.admin.views.main;

import com.seafood.admin.views.dashboard.DashboardView;
import com.seafood.admin.views.order.OrderListView;
import com.seafood.admin.views.product.ProductListView;
import com.seafood.admin.views.user.UserListView;
import com.seafood.admin.views.config.ConfigListView;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
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
        // Home button to return to dashboard
        Button homeButton = new Button(new Icon(VaadinIcon.HOME));
        homeButton.addClassNames("home-button");
        homeButton.getStyle()
            .set("background", "transparent")
            .set("border", "none")
            .set("color", "#ffffff")
            .set("cursor", "pointer")
            .set("padding", "8px")
            .set("border-radius", "4px")
            .set("margin-right", "12px");
        homeButton.addClickListener(e -> {
            getUI().ifPresent(ui -> ui.navigate(DashboardView.class));
        });

        H1 logo = new H1("🐠 大海的味道");
        logo.getStyle()
            .set("font-size", "1.15rem")
            .set("font-weight", "800")
            .set("letter-spacing", "0.01em")
            .set("margin", "0")
            .set("color", "#ffffff");

        H1 subtitle = new H1("商家管理后台");
        subtitle.getStyle()
            .set("font-size", "0.8rem")
            .set("font-weight", "400")
            .set("opacity", "0.7")
            .set("margin", "0 0 0 8px")
            .set("color", "#ffffff");

        HorizontalLayout brand = new HorizontalLayout(homeButton, logo, subtitle);
        brand.setAlignItems(FlexComponent.Alignment.CENTER);

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), brand);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.addClassNames("px-m");
        header.getStyle()
            .set("background", "linear-gradient(135deg, #1A1A2E 0%, #2D3452 100%)")
            .set("min-height", "56px");

        addToNavbar(header);
    }

    private void createDrawer() {
        VerticalLayout logoArea = new VerticalLayout();
        logoArea.setPadding(false);
        logoArea.setSpacing(false);
        logoArea.setAlignItems(FlexComponent.Alignment.START);
        logoArea.addClassName("drawer-logo");

        RouterLink dashboard = createNavLink(
            new Icon(VaadinIcon.DASHBOARD), "数据概览", DashboardView.class);
        RouterLink products = createNavLink(
            new Icon(VaadinIcon.PACKAGE), "商品管理", ProductListView.class);
        RouterLink orders = createNavLink(
            new Icon(VaadinIcon.CART), "订单管理", OrderListView.class);
        RouterLink users = createNavLink(
            new Icon(VaadinIcon.USERS), "用户管理", UserListView.class);
        RouterLink config = createNavLink(
            new Icon(VaadinIcon.COG), "配置中心", ConfigListView.class);

        VerticalLayout nav = new VerticalLayout(dashboard, products, orders, users, config);
        nav.setPadding(false);
        nav.setSpacing(true);
        nav.setPadding(true);
        nav.getStyle().set("gap", "4px");

        VerticalLayout drawer = new VerticalLayout(logoArea, nav);
        drawer.setSizeFull();
        drawer.setPadding(false);
        drawer.getStyle()
            .set("height", "100%")
            .set("padding-top", "12px");

        addToDrawer(drawer);
    }

    private <T extends com.vaadin.flow.component.Component> RouterLink createNavLink(Icon icon, String label, Class<T> target) {
        icon.setSize("18px");
        icon.getStyle().set("margin-right", "10px");
        RouterLink link = new RouterLink();
        link.add(icon, new com.vaadin.flow.component.Text(label));
        link.setRoute(target);
        link.getStyle()
            .set("display", "flex")
            .set("align-items", "center")
            .set("padding", "10px 20px")
            .set("border-radius", "8px")
            .set("color", "#2D3436")
            .set("text-decoration", "none")
            .set("font-weight", "500")
            .set("font-size", "0.9rem")
            .set("transition", "all 0.2s ease")
            .set("margin", "2px 8px");
        return link;
    }
}
