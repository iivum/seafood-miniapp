package com.seafood.admin.views.main;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "", layout = MainLayout.class)
@PageTitle("首页 | 海鲜商城商家管理")
public class HomeView extends VerticalLayout {

    public HomeView() {
        add(new H2("欢迎来到商家管理后台"));
        add(new Paragraph("请在左侧菜单选择要进行的操作。"));
    }
}
