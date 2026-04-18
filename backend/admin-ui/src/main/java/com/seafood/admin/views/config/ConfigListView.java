package com.seafood.admin.views.config;

import com.seafood.admin.client.ConfigClient;
import com.seafood.admin.client.ConfigPropertyResponse;
import com.seafood.admin.client.SaveConfigPropertyRequest;
import com.seafood.admin.views.main.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Route(value = "config", layout = MainLayout.class)
@PageTitle("配置中心 | 海鲜商城")
public class ConfigListView extends VerticalLayout {

    private final ConfigClient configClient;
    private final Grid<ConfigPropertyResponse> grid = new Grid<>(ConfigPropertyResponse.class);
    private final ConfigForm form;
    private final Select<String> serviceSelect = new Select<>();
    private final Select<String> profileSelect = new Select<>();
    private final TextField searchField = new TextField();

    @Autowired
    public ConfigListView(ConfigClient configClient) {
        this.configClient = configClient;
        this.form = new ConfigForm();
        form.setWidth("30em");
        form.addSaveListener(this::saveConfig);
        form.addDeleteListener(this::deleteConfig);
        form.addCloseListener(e -> closeEditor());
        form.addEncryptListener(this::encryptValue);

        setSizeFull();
        configureServiceSelect();
        configureProfileSelect();
        configureGrid();
        add(new H2("配置中心"), getToolbar(), getContent());
        updateList();
        closeEditor();
    }

    private Component getContent() {
        HorizontalLayout content = new HorizontalLayout(grid, form);
        content.setFlexGrow(2, grid);
        content.setFlexGrow(1, form);
        content.addClassNames("content");
        content.setSizeFull();
        return content;
    }

    private void configureServiceSelect() {
        serviceSelect.setLabel("服务");
        serviceSelect.setItems("gateway", "product-service", "order-service", "user-service", "admin-ui", "application");
        serviceSelect.setValue("gateway");
        serviceSelect.setWidth("12em");
        serviceSelect.addValueChangeListener(e -> updateList());
    }

    private void configureProfileSelect() {
        profileSelect.setLabel("环境");
        profileSelect.setItems("docker", "native", "dev", "prod");
        profileSelect.setValue("docker");
        profileSelect.setWidth("8em");
        profileSelect.addValueChangeListener(e -> updateList());
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(ConfigPropertyResponse::getKey).setHeader("配置项").setAutoWidth(true).setSortable(true);
        grid.addColumn(p -> {
            if (p.isEncrypted()) {
                return "****** (已加密)";
            }
            String val = p.getValue();
            if (val != null && val.length() > 50) {
                return val.substring(0, 47) + "...";
            }
            return val;
        }).setHeader("值").setAutoWidth(true);
        grid.addColumn(p -> p.isEncrypted() ? "是" : "否").setHeader("加密").setAutoWidth(true);
        grid.addColumn(ConfigPropertyResponse::getUpdatedAt).setHeader("更新时间").setAutoWidth(true);

        grid.getColumns().forEach(col -> col.setAutoWidth(true));
        grid.setSortableColumns("key");
        grid.asSingleSelect().addValueChangeListener(event -> editConfig(event.getValue()));
    }

    private HorizontalLayout getToolbar() {
        Button addConfigButton = new Button("添加配置");
        addConfigButton.getStyle()
                .set("background", "linear-gradient(135deg, #4CAF50 0%, #66BB6A 100%)")
                .set("border-radius", "8px")
                .set("box-shadow", "0 2px 8px rgba(76, 175, 80, 0.3)");
        addConfigButton.addClickListener(click -> addConfig());

        Button refreshButton = new Button("刷新");
        refreshButton.getStyle()
                .set("background", "linear-gradient(135deg, #2196F3 0%, #42A5F5 100%)")
                .set("border-radius", "8px")
                .set("box-shadow", "0 2px 8px rgba(33, 150, 243, 0.3)");
        refreshButton.addClickListener(click -> updateList());

        searchField.setPlaceholder("搜索配置项...");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.getStyle()
                .set("border-radius", "8px")
                .set("--lumo-border-radius", "8px");
        searchField.addValueChangeListener(e -> updateList());

        HorizontalLayout toolbar = new HorizontalLayout(serviceSelect, profileSelect, addConfigButton, refreshButton, searchField);
        toolbar.addClassName("toolbar");
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.START);
        toolbar.setSpacing(true);
        return toolbar;
    }

    public void editConfig(ConfigPropertyResponse config) {
        if (config == null) {
            closeEditor();
        } else {
            form.setConfig(config);
            form.setVisible(true);
            addClassName("editing");
        }
    }

    private void closeEditor() {
        form.setConfig(null);
        form.setVisible(false);
        removeClassName("editing");
    }

    private void addConfig() {
        grid.asSingleSelect().clear();
        ConfigPropertyResponse newConfig = new ConfigPropertyResponse();
        newConfig.setServiceName(serviceSelect.getValue());
        newConfig.setProfile(profileSelect.getValue());
        newConfig.setLabel("main");
        editConfig(newConfig);
    }

    private void saveConfig(ConfigForm.SaveEvent event) {
        ConfigPropertyResponse config = event.getConfig();
        SaveConfigPropertyRequest request = new SaveConfigPropertyRequest(
                config.getServiceName(),
                config.getProfile(),
                config.getLabel(),
                config.getKey(),
                config.getValue(),
                config.isEncrypted()
        );

        try {
            configClient.saveProperty(request);
            Notification.show("配置保存成功");
            updateList();
            closeEditor();
        } catch (Exception e) {
            Notification.show("保存失败: " + e.getMessage());
        }
    }

    private void deleteConfig(ConfigForm.DeleteEvent event) {
        ConfigPropertyResponse config = event.getConfig();
        try {
            configClient.deleteProperty(
                    config.getServiceName(),
                    config.getProfile(),
                    config.getLabel(),
                    config.getKey()
            );
            Notification.show("配置已删除");
            updateList();
            closeEditor();
        } catch (Exception e) {
            Notification.show("删除失败: " + e.getMessage());
        }
    }

    private void encryptValue(ConfigForm.EncryptEvent event) {
        try {
            var response = configClient.encrypt(java.util.Map.of("plaintext", event.getPlaintext()));
            String encrypted = response.get("encrypted");
            form.setEncryptedValue(encrypted);
            Notification.show("加密成功");
        } catch (Exception e) {
            Notification.show("加密失败: " + e.getMessage());
        }
    }

    private void updateList() {
        String serviceName = serviceSelect.getValue();
        String profile = profileSelect.getValue();

        List<ConfigPropertyResponse> configs;
        try {
            configs = configClient.getProperties(serviceName, profile, "main");
        } catch (Exception e) {
            Notification.show("获取配置失败: " + e.getMessage());
            configs = List.of();
        }

        // Filter by search term
        String searchTerm = searchField.getValue();
        if (searchTerm != null && !searchTerm.isBlank()) {
            String lowerSearch = searchTerm.toLowerCase();
            configs = configs.stream()
                    .filter(c -> c.getKey() != null && c.getKey().toLowerCase().contains(lowerSearch))
                    .collect(java.util.stream.Collectors.toList());
        }

        grid.setItems(configs);
    }
}
