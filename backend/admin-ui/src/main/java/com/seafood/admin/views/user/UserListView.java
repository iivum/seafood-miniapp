package com.seafood.admin.views.user;

import com.seafood.admin.client.UserClient;
import com.seafood.admin.client.UserResponse;
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

@Route(value = "users", layout = MainLayout.class)
@PageTitle("用户管理 | 海鲜商城")
public class UserListView extends VerticalLayout {

    private final UserClient userClient;
    private final Grid<UserResponse> grid = new Grid<>(UserResponse.class);

    @Autowired
    public UserListView(UserClient userClient) {
        this.userClient = userClient;
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
        
        H2 title = new H2("用户管理");
        title.getStyle().set("margin", "0").set("font-size", "1.5rem").set("font-weight", "700").set("color", "#0c1929");
        
        Div subtitle = new Div();
        subtitle.setText("查看和管理商城用户");
        subtitle.getStyle().set("font-size", "0.85rem").set("color", "#8aaabf").set("margin-top", "4px");
        
        VerticalLayout titleArea = new VerticalLayout(title, subtitle);
        titleArea.setMargin(false);
        titleArea.setSpacing(false);
        
        header.add(titleArea);
        return header;
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setColumns("id", "nickname", "openId", "role");

        grid.addColumn(user -> {
            return user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty() ? "有" : "无";
        }).setHeader("头像").setAutoWidth(true);

        grid.addComponentColumn(user -> {
            Button adminButton = new Button("设为管理员");
            adminButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            adminButton.setEnabled(!"ADMIN".equals(user.getRole()));
            adminButton.addClickListener(click -> {
                try {
                    userClient.updateUserRole(user.getId(), "ADMIN");
                    Notification.show("用户 " + user.getNickname() + " 已设为管理员");
                    updateList();
                } catch (Exception e) {
                    Notification.show("设置失败: " + e.getMessage());
                }
            });
            return adminButton;
        }).setHeader("操作");

        grid.getColumns().forEach(col -> col.setAutoWidth(true));
        updateList();
    }

    private void updateList() {
        grid.setItems(userClient.getAllUsers());
    }
}