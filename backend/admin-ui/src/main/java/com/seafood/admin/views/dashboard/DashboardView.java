package com.seafood.admin.views.dashboard;

import com.seafood.admin.client.OrderClient;
import com.seafood.admin.client.OrderResponse;
import com.seafood.admin.client.ProductClient;
import com.seafood.admin.client.ProductResponse;
import com.seafood.admin.client.UserClient;
import com.seafood.admin.client.UserResponse;
import com.seafood.admin.views.main.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
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
        add(new H2("数据概览"));

        // Fetch data
        List<ProductResponse> products = productClient.getAllProducts();
        List<OrderResponse> orders = orderClient.getAllOrders();
        List<UserResponse> users = userClient.getAllUsers();

        // Calculate statistics
        int totalProducts = products.size();
        int totalOrders = orders.size();
        int totalUsers = users.size();

        long paidOrders = orders.stream().filter(o -> "PAID".equals(o.getStatus())).count();
        long shippedOrders = orders.stream().filter(o -> "SHIPPED".equals(o.getStatus())).count();
        long completedOrders = orders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).count();

        BigDecimal totalRevenue = orders.stream()
                .filter(o -> "PAID".equals(o.getStatus()) || "SHIPPED".equals(o.getStatus()) || "COMPLETED".equals(o.getStatus()))
                .map(OrderResponse::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int lowStockProducts = (int) products.stream().filter(p -> p.getStock() < 10).count();

        // Create stat cards
        add(createStatCard("商品总数", String.valueOf(totalProducts), "库存预警: " + lowStockProducts + " 件"));
        add(createStatCard("订单总数", String.valueOf(totalOrders), "已完成: " + completedOrders));
        add(createStatCard("用户总数", String.valueOf(totalUsers), "待发货: " + paidOrders));
        add(createStatCard("销售收入", "¥" + totalRevenue, "已发货: " + shippedOrders));

        // Order status breakdown
        add(new H5("订单状态分布"));
        if (totalOrders > 0) {
            add(createOrderStatusBar(orders, totalOrders));
        } else {
            add(new Paragraph("暂无订单数据"));
        }
    }

    private HorizontalLayout createStatCard(String title, String value, String subtitle) {
        VerticalLayout card = new VerticalLayout();
        card.addClassName("stat-card");
        card.setWidth("200px");
        card.setPadding(true);
        card.setAlignItems(FlexComponent.Alignment.CENTER);

        H5 titleLabel = new H5(title);
        titleLabel.getStyle().set("color", "#666");

        H2 valueLabel = new H2(value);
        valueLabel.getStyle().set("margin", "10px 0");

        Paragraph subtitleLabel = new Paragraph(subtitle);
        subtitleLabel.getStyle().set("color", "#999");

        card.add(titleLabel, valueLabel, subtitleLabel);
        card.setSpacing(false);

        HorizontalLayout layout = new HorizontalLayout(card);
        layout.setWidthFull();
        return layout;
    }

    private VerticalLayout createOrderStatusBar(List<OrderResponse> orders, int total) {
        VerticalLayout container = new VerticalLayout();
        container.setWidthFull();

        // Calculate percentages
        long pending = orders.stream().filter(o -> "PENDING".equals(o.getStatus())).count();
        long paid = orders.stream().filter(o -> "PAID".equals(o.getStatus())).count();
        long shipped = orders.stream().filter(o -> "SHIPPED".equals(o.getStatus())).count();
        long completed = orders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).count();
        long cancelled = orders.stream().filter(o -> "CANCELLED".equals(o.getStatus())).count();

        String statusText = String.format(
                "待支付: %d | 已支付: %d | 已发货: %d | 已完成: %d | 已取消: %d",
                pending, paid, shipped, completed, cancelled
        );

        container.add(new Paragraph(statusText));

        // Progress bars for each status (Vaadin ProgressBar doesn't have setLabel)
        if (paid > 0) {
            ProgressBar paidBar = new ProgressBar();
            paidBar.setValue((double) paid / total);
            paidBar.setVisible(true);
            container.add(new Span("已支付: " + paid), paidBar);
        }
        if (shipped > 0) {
            ProgressBar shippedBar = new ProgressBar();
            shippedBar.setValue((double) shipped / total);
            shippedBar.setVisible(true);
            container.add(new Span("已发货: " + shipped), shippedBar);
        }
        if (completed > 0) {
            ProgressBar completedBar = new ProgressBar();
            completedBar.setValue((double) completed / total);
            completedBar.setVisible(true);
            container.add(new Span("已完成: " + completed), completedBar);
        }

        return container;
    }
}