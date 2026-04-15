package com.seafood.admin.views.main;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Route(value = "", layout = MainLayout.class)
@PageTitle("首页 | 海鲜商城商家管理")
public class HomeView extends VerticalLayout {

    public HomeView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
            .set("background", "#f0f7fa")
            .set("min-height", "100vh")
            .set("padding", "28px 32px");

        // Welcome hero
        add(createHero());

        // Section: Quick Actions
        add(createSectionHeader("快速入口", "选择以下模块快速开始工作"));
        add(createQuickActions());

        // Section: Feature Overview
        add(createSectionHeader("功能模块", "商家管理后台核心功能一览"));
        add(createFeatureGrid());
    }

    private VerticalLayout createHero() {
        VerticalLayout hero = new VerticalLayout();
        hero.addClassName("welcome-hero");
        hero.setSpacing(false);
        hero.setPadding(false);
        hero.setWidthFull();
        hero.getStyle()
            .set("margin-bottom", "32px");

        H2 greeting = new H2("欢迎回来，管理员 👋");
        greeting.getStyle()
            .set("color", "#ffffff")
            .set("font-size", "1.75rem")
            .set("font-weight", "800")
            .set("margin", "0 0 8px 0");

        Paragraph time = new Paragraph(
            "🕐 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 E"))
        );
        time.getStyle()
            .set("color", "rgba(255,255,255,0.7)")
            .set("font-size", "0.85rem")
            .set("margin", "0 0 20px 0");

        Paragraph desc = new Paragraph(
            "大海的味道海鲜商城商家管理后台 · 实时掌控商品、订单与用户数据"
        );
        desc.getStyle()
            .set("color", "rgba(255,255,255,0.6)")
            .set("font-size", "0.9rem")
            .set("margin", "0");

        hero.add(greeting, time, desc);
        return hero;
    }

    private HorizontalLayout createSectionHeader(String title, String subtitle) {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setSpacing(true);
        header.getStyle()
            .set("margin-bottom", "16px")
            .set("padding-bottom", "12px")
            .set("border-bottom", "2px solid #dce9f0");

        H2 h2 = new H2(title);
        h2.getStyle()
            .set("font-size", "1.35rem")
            .set("font-weight", "700")
            .set("color", "#0c1929")
            .set("margin", "0");

        Paragraph sub = new Paragraph(subtitle);
        sub.getStyle()
            .set("color", "#8aaabf")
            .set("font-size", "0.82rem")
            .set("margin", "0 0 0 auto");

        header.add(h2, sub);
        return header;
    }

    private VerticalLayout createQuickActions() {
        VerticalLayout container = new VerticalLayout();
        container.setSpacing(false);
        container.setPadding(false);
        container.setWidthFull();
        container.getStyle().set("margin-bottom", "36px");

        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setSpacing(true);
        row.getStyle().set("flex-wrap", "wrap").set("gap", "12px");

        row.add(
            createQuickBtn("📊", "数据概览", "#0096c7", "dashboard"),
            createQuickBtn("🐟", "商品管理", "#023e6a", "products"),
            createQuickBtn("📦", "订单管理", "#0077b6", "orders"),
            createQuickBtn("👥", "用户管理", "#00b4d8", "users")
        );
        container.add(row);
        return container;
    }

    private Button createQuickBtn(String emoji, String label, String color, String route) {
        Button btn = new Button();
        btn.addClassName("quick-action-btn");
        btn.getElement().setProperty("innerHTML", emoji + " " + label);
        btn.getStyle()
            .set("background", "linear-gradient(135deg, " + color + ", #023e6a)")
            .set("color", "#ffffff")
            .set("font-size", "0.9rem")
            .set("font-weight", "600")
            .set("padding", "14px 24px")
            .set("border-radius", "10px")
            .set("border", "none")
            .set("cursor", "pointer")
            .set("box-shadow", "0 3px 12px rgba(0,100,160,0.25)")
            .set("transition", "all 0.2s ease")
            .set("font-family", "inherit");
        btn.addClickListener(e -> btn.getUI().ifPresent(ui -> ui.navigate(route)));
        return btn;
    }

    private VerticalLayout createFeatureGrid() {
        VerticalLayout grid = new VerticalLayout();
        grid.setPadding(false);
        grid.setSpacing(true);
        grid.setWidthFull();
        grid.getStyle().set("margin-bottom", "24px");

        HorizontalLayout row1 = new HorizontalLayout();
        row1.setWidthFull();
        row1.setSpacing(true);
        row1.getStyle().set("gap", "16px").set("flex-wrap", "wrap");

        row1.add(
            createFeatureCard("📊", "数据概览", "查看商品、订单、用户统计数据和订单状态分布", "dashboard"),
            createFeatureCard("🐟", "商品管理", "添加、编辑、删除商品，设置上下架状态", "products")
        );

        HorizontalLayout row2 = new HorizontalLayout();
        row2.setWidthFull();
        row2.setSpacing(true);
        row2.getStyle().set("gap", "16px").set("flex-wrap", "wrap");

        row2.add(
            createFeatureCard("📦", "订单管理", "查看订单列表，处理发货操作", "orders"),
            createFeatureCard("👥", "用户管理", "查看用户列表，设置管理员权限", "users")
        );

        grid.add(row1, row2);
        return grid;
    }

    private VerticalLayout createFeatureCard(String emoji, String title, String description, String route) {
        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(false);
        card.addClassName("feature-card");

        Paragraph icon = new Paragraph(emoji);
        icon.getStyle()
            .set("font-size", "1.8rem")
            .set("margin", "0 0 8px 0");

        H4 titleLabel = new H4(title);
        titleLabel.getStyle()
            .set("color", "#0096c7")
            .set("font-size", "1rem")
            .set("font-weight", "700")
            .set("margin", "0 0 6px 0");

        Paragraph descLabel = new Paragraph(description);
        descLabel.getStyle()
            .set("color", "#6b8fa3")
            .set("font-size", "0.82rem")
            .set("line-height", "1.5")
            .set("margin", "0 0 14px 0");

        card.add(icon, titleLabel, descLabel);
        card.getElement().addEventListener("click", e ->
            card.getUI().ifPresent(ui -> ui.navigate(route))
        );
        card.getElement().getStyle().set("cursor", "pointer");
        return card;
    }
}