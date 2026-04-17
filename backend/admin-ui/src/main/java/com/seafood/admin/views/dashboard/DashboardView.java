package com.seafood.admin.views.dashboard;

import com.seafood.admin.client.OrderClient;
import com.seafood.admin.client.OrderResponse;
import com.seafood.admin.client.ProductClient;
import com.seafood.admin.client.ProductResponse;
import com.seafood.admin.client.UserClient;
import com.seafood.admin.client.UserResponse;
import com.seafood.admin.views.main.MainLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

@Route(value = "dashboard", layout = MainLayout.class)
@PageTitle("数据概览 | 海鲜商城")
public class DashboardView extends VerticalLayout {

    @Autowired
    public DashboardView(ProductClient productClient, OrderClient orderClient, UserClient userClient) {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
            .set("background", "#f0f7fa")
            .set("min-height", "100vh")
            .set("padding", "28px 32px");

        // Section header
        add(createSectionHeader("📊 数据概览", "实时统计商城运营数据"));

        // Stat cards row
        HorizontalLayout statsRow = new HorizontalLayout();
        statsRow.setWidthFull();
        statsRow.setSpacing(true);
        statsRow.getStyle()
            .set("gap", "16px")
            .set("flex-wrap", "wrap")
            .set("margin-bottom", "32px");

        // Fetch data
        List<ProductResponse> products = productClient.getAllProducts();
        List<OrderResponse> orders = orderClient.getAllOrders();
        List<UserResponse> users = userClient.getAllUsers();

        int totalProducts = products.size();
        int totalOrders = orders.size();
        int totalUsers = users.size();

        long paidOrders = orders.stream().filter(o -> "PAID".equals(o.getDisplayStatus())).count();
        long shippedOrders = orders.stream().filter(o -> "SHIPPED".equals(o.getDisplayStatus())).count();
        long completedOrders = orders.stream().filter(o -> "COMPLETED".equals(o.getDisplayStatus())).count();
        long pendingOrders = orders.stream().filter(o -> "PENDING".equals(o.getDisplayStatus())).count();

        BigDecimal totalRevenue = orders.stream()
                .filter(o -> "PAID".equals(o.getDisplayStatus()) || "SHIPPED".equals(o.getDisplayStatus()) || "COMPLETED".equals(o.getDisplayStatus()))
                .map(OrderResponse::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int lowStockProducts = (int) products.stream().filter(p -> p.getStock() < 10).count();

        statsRow.add(
            createStatCard("🐟", "商品总数", String.valueOf(totalProducts),
                lowStockProducts > 0 ? "⚠️ " + lowStockProducts + " 件库存预警" : "库存充足",
                "#0096c7"),
            createStatCard("📦", "订单总数", String.valueOf(totalOrders),
                "已完成 " + completedOrders + " | 待发货 " + paidOrders,
                "#023e6a"),
            createStatCard("👥", "用户总数", String.valueOf(totalUsers),
                "管理后台活跃用户",
                "#0077b6"),
            createStatCard("💰", "销售收入", "¥" + totalRevenue,
                "已发货 " + shippedOrders + " 笔",
                "#023e6a")
        );
        add(statsRow);

        // Order status section
        add(createSectionHeader("📈 订单状态分布", "各状态订单数量与占比"));
        add(createOrderStatusSection(orders, totalOrders));
    }

    private HorizontalLayout createSectionHeader(String title, String subtitle) {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle()
            .set("margin-bottom", "20px")
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

    private VerticalLayout createStatCard(String emoji, String title, String value,
                                          String subtitle, String accentColor) {
        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(false);
        card.addClassName("stat-card");
        card.getStyle()
            .set("border-top", "4px solid " + accentColor);

        Paragraph icon = new Paragraph(emoji);
        icon.getStyle()
            .set("font-size", "1.6rem")
            .set("margin", "0 0 10px 0")
            .set("display", "block");

        Span titleLabel = new Span(title);
        titleLabel.getStyle()
            .set("font-size", "0.72rem")
            .set("font-weight", "700")
            .set("color", "#6b8fa3")
            .set("text-transform", "uppercase")
            .set("letter-spacing", "0.08em")
            .set("display", "block")
            .set("margin-bottom", "8px");

        Span valueLabel = new Span(value);
        valueLabel.getStyle()
            .set("font-size", "1.85rem")
            .set("font-weight", "800")
            .set("color", "#0c1929")
            .set("line-height", "1")
            .set("display", "block")
            .set("margin-bottom", "8px");

        Paragraph subLabel = new Paragraph(subtitle);
        subLabel.getStyle()
            .set("font-size", "0.75rem")
            .set("color", "#8aaabf")
            .set("margin", "0")
            .set("display", "block");

        card.add(icon, titleLabel, valueLabel, subLabel);
        return card;
    }

    private VerticalLayout createOrderStatusSection(List<OrderResponse> orders, int total) {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.setSpacing(true);
        section.setPadding(true);
        section.getStyle()
            .set("background", "#ffffff")
            .set("border-radius", "14px")
            .set("border", "1px solid #dce9f0")
            .set("box-shadow", "0 2px 12px rgba(0,40,70,0.08)");

        // Summary row
        HorizontalLayout summaryRow = new HorizontalLayout();
        summaryRow.setWidthFull();
        summaryRow.setSpacing(true);
        summaryRow.getStyle().set("flex-wrap", "wrap").set("gap", "12px");

        summaryRow.add(
            createStatusBadge("PENDING", "⏳ 待支付", "#f57f17", orders),
            createStatusBadge("PAID", "✅ 已支付", "#2e7d32", orders),
            createStatusBadge("SHIPPED", "🚚 已发货", "#1565c0", orders),
            createStatusBadge("COMPLETED", "🎉 已完成", "#0096c7", orders),
            createStatusBadge("CANCELLED", "❌ 已取消", "#c62828", orders)
        );
        section.add(summaryRow);

        // Progress bars
        if (total > 0) {
            long paid = orders.stream().filter(o -> "PAID".equals(o.getDisplayStatus())).count();
            long shipped = orders.stream().filter(o -> "SHIPPED".equals(o.getDisplayStatus())).count();
            long completed = orders.stream().filter(o -> "COMPLETED".equals(o.getDisplayStatus())).count();

            if (paid > 0) section.add(createStatusBar("已支付", paid, total, "#2e7d32"));
            if (shipped > 0) section.add(createStatusBar("已发货", shipped, total, "#1565c0"));
            if (completed > 0) section.add(createStatusBar("已完成", completed, total, "#0096c7"));
        } else {
            Paragraph empty = new Paragraph("📭 暂无订单数据");
            empty.getStyle().set("color", "#8aaabf").set("font-size", "0.9rem");
            section.add(empty);
        }
        return section;
    }

    private VerticalLayout createStatusBadge(String status, String label,
                                             String color, List<OrderResponse> orders) {
        long count = orders.stream().filter(o -> status.equals(o.getDisplayStatus())).count();
        VerticalLayout badge = new VerticalLayout();
        badge.setSpacing(false);
        badge.setPadding(false);
        badge.setAlignItems(FlexComponent.Alignment.CENTER);
        badge.getStyle()
            .set("background", color + "15")
            .set("border", "1px solid " + color + "40")
            .set("border-radius", "10px")
            .set("padding", "14px 18px")
            .set("min-width", "100px")
            .set("text-align", "center");

        Paragraph labelP = new Paragraph(label);
        labelP.getStyle()
            .set("font-size", "0.75rem")
            .set("font-weight", "600")
            .set("color", color)
            .set("margin", "0 0 4px 0");

        Span countSpan = new Span(String.valueOf(count));
        countSpan.getStyle()
            .set("font-size", "1.5rem")
            .set("font-weight", "800")
            .set("color", color)
            .set("display", "block")
            .set("line-height", "1");

        badge.add(labelP, countSpan);
        return badge;
    }

    private HorizontalLayout createStatusBar(String label, long count, int total, String color) {
        double pct = (double) count / total;
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setSpacing(true);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.getStyle().set("margin", "6px 0");

        Span labelSpan = new Span(label + " (" + count + ")");
        labelSpan.getStyle()
            .set("font-size", "0.82rem")
            .set("color", "#1e3a4c")
            .set("font-weight", "600")
            .set("min-width", "80px");

        ProgressBar bar = new ProgressBar();
        bar.setValue(pct);
        bar.setHeight("10px");
        bar.getStyle()
            .set("flex", "1")
            .set("--lumo-primary-color", color)
            .set("border-radius", "6px");

        Span pctSpan = new Span(String.format("%.0f%%", pct * 100));
        pctSpan.getStyle()
            .set("font-size", "0.78rem")
            .set("color", color)
            .set("font-weight", "700")
            .set("min-width", "38px")
            .set("text-align", "right");

        row.add(labelSpan, bar, pctSpan);
        return row;
    }
}