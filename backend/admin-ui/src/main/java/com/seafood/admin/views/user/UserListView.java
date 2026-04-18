package com.seafood.admin.views.user;

import com.seafood.admin.client.UserClient;
import com.seafood.admin.client.UserResponse;
import com.seafood.admin.views.main.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

@Route(value = "users", layout = MainLayout.class)
@PageTitle("用户管理 | 海鲜商城")
public class UserListView extends VerticalLayout {

    private final UserClient userClient;
    private final Grid<UserResponse> grid = new Grid<>(UserResponse.class);
    private TextField searchField = new TextField();

    @Autowired
    public UserListView(UserClient userClient) {
        this.userClient = userClient;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
            .set("padding", "28px 32px")
            .set("background", "#F7F8FC");
        
        add(createViewHeader(), createFilterBar(), grid);
        configureGrid();
        updateList();
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

    private HorizontalLayout createFilterBar() {
        HorizontalLayout filterBar = new HorizontalLayout();
        filterBar.addClassName("filter-row");
        filterBar.setWidthFull();
        filterBar.setAlignItems(FlexComponent.Alignment.CENTER);
        
        // Search field
        searchField.setPlaceholder("搜索用户昵称/OpenID...");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> updateList());
        
        filterBar.add(searchField);
        return filterBar;
    }

    private void configureGrid() {
        grid.setSizeFull();
        
        // ID column
        grid.addColumn(UserResponse::getId).setHeader("用户ID").setAutoWidth(true).setSortable(true);
        
        // Avatar column with preview
        grid.addComponentColumn(user -> {
            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                Image img = new Image(user.getAvatarUrl(), "头像");
                img.setWidth("40px");
                img.setHeight("40px");
                img.getElement().getStyle().set("border-radius", "50%").set("object-fit", "cover");
                return img;
            }
            Div placeholder = new Div();
            placeholder.setText("无");
            placeholder.getStyle().set("width", "40px").set("height", "40px")
                .set("border-radius", "50%").set("background", "#e8f4fb")
                .set("display", "flex").set("align-items", "center")
                .set("justify-content", "center").set("color", "#8aaabf")
                .set("font-size", "12px");
            return placeholder;
        }).setHeader("头像").setAutoWidth(true);
        
        // Nickname column
        grid.addColumn(UserResponse::getNickname).setHeader("昵称").setAutoWidth(true).setSortable(true);
        
        // OpenID column
        grid.addColumn(UserResponse::getOpenId).setHeader("OpenID").setAutoWidth(true);
        
        // Phone column
        grid.addColumn(UserResponse::getPhone).setHeader("手机号").setAutoWidth(true);
        
        // Role column - formatted
        grid.addColumn(user -> formatRole(user.getRole())).setHeader("角色").setAutoWidth(true).setSortable(true);
        
        // Action column with set/remove admin
        grid.addComponentColumn(user -> {
            HorizontalLayout buttons = new HorizontalLayout();
            buttons.setSpacing(true);
            
            // Set as admin button
            Button adminButton = new Button("设为管理员");
            adminButton.getStyle()
                .set("background", "linear-gradient(135deg, #4ECDC4 0%, #6FE3DB 100%)")
                .set("border-radius", "8px")
                .set("color", "#ffffff")
                .set("box-shadow", "0 2px 8px rgba(78, 205, 196, 0.3)");
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
            
            // Remove admin button
            Button removeAdminButton = new Button("取消管理员");
            removeAdminButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            removeAdminButton.setEnabled("ADMIN".equals(user.getRole()));
            removeAdminButton.addClickListener(click -> {
                try {
                    userClient.updateUserRole(user.getId(), "USER");
                    Notification.show("用户 " + user.getNickname() + " 已取消管理员");
                    updateList();
                } catch (Exception e) {
                    Notification.show("操作失败: " + e.getMessage());
                }
            });
            
            buttons.add(adminButton, removeAdminButton);
            return buttons;
        }).setHeader("操作");

        grid.getColumns().forEach(col -> col.setAutoWidth(true));
        
        // Enable sorting
        grid.setSortableColumns("id", "nickname", "role");
    }

    private String formatRole(String role) {
        if (role == null) return "-";
        switch (role) {
            case "ADMIN": return "管理员";
            case "USER": return "普通用户";
            case "MERCHANT": return "商户";
            default: return role;
        }
    }

    private void updateList() {
        var users = userClient.getAllUsers();
        
        // Filter by search term
        String searchTerm = searchField.getValue();
        if (searchTerm != null && !searchTerm.isBlank()) {
            String lowerSearch = searchTerm.toLowerCase();
            users = users.stream()
                .filter(u -> 
                    (u.getNickname() != null && u.getNickname().toLowerCase().contains(lowerSearch)) ||
                    (u.getOpenId() != null && u.getOpenId().toLowerCase().contains(lowerSearch)) ||
                    (u.getId() != null && u.getId().toLowerCase().contains(lowerSearch)))
                .collect(Collectors.toList());
        }
        
        grid.setItems(users);
    }
}
