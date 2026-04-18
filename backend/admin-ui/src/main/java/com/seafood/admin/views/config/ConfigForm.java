package com.seafood.admin.views.config;

import com.seafood.admin.client.ConfigPropertyResponse;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.shared.Registration;

public class ConfigForm extends FormLayout {

    Select<String> serviceName = new Select<>();
    Select<String> profile = new Select<>();
    TextField label = new TextField("Label");
    TextField key = new TextField("配置项");
    TextField value = new TextField("值");
    Checkbox encrypted = new Checkbox("加密");

    Button save = new Button("保存");
    Button delete = new Button("删除");
    Button close = new Button("取消");
    Button encryptBtn = new Button("🔒 加密");

    Binder<ConfigPropertyResponse> binder = new BeanValidationBinder<>(ConfigPropertyResponse.class);

    public ConfigForm() {
        addClassName("config-form");
        binder.bindInstanceFields(this);

        serviceName.setLabel("服务");
        serviceName.setItems("gateway", "product-service", "order-service", "user-service", "admin-ui", "application");
        serviceName.setWidthFull();

        profile.setLabel("环境");
        profile.setItems("docker", "native", "dev", "prod");
        profile.setWidthFull();

        label.setValue("main");
        label.setReadOnly(true);

        key.setWidthFull();
        value.setWidthFull();

        encrypted.addValueChangeListener(e -> encryptBtn.setVisible(e.getValue()));
        encryptBtn.setVisible(false);
        encryptBtn.getStyle()
                .set("background", "linear-gradient(135deg, #FF9800 0%, #FFB74D 100%)")
                .set("border-radius", "6px")
                .set("color", "#fff");

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.getStyle()
                .set("background", "linear-gradient(135deg, #4CAF50 0%, #66BB6A 100%)")
                .set("border-radius", "8px");
        save.addClickShortcut(Key.ENTER);

        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        delete.getStyle().set("border-radius", "8px");
        delete.setVisible(false);

        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        close.getStyle().set("border-radius", "8px");
        close.addClickShortcut(Key.ESCAPE);

        save.addClickListener(click -> validateAndSave());
        delete.addClickListener(click -> fireEvent(new DeleteEvent(this, binder.getBean())));
        close.addClickListener(click -> fireEvent(new CloseEvent(this)));
        encryptBtn.addClickListener(click -> fireEvent(new EncryptEvent(this, value.getValue())));

        HorizontalLayout buttonLayout = new HorizontalLayout(save, delete, close, encryptBtn);
        buttonLayout.setSpacing(true);

        add(serviceName, profile, label, key, value, encrypted, buttonLayout);
    }

    public void setConfig(ConfigPropertyResponse config) {
        binder.setBean(config);
        encryptBtn.setVisible(config != null && config.isEncrypted());
        delete.setVisible(config != null && config.getId() != null);
    }

    public void setEncryptedValue(String encryptedValue) {
        value.setValue(encryptedValue);
        encrypted.setValue(true);
    }

    private void validateAndSave() {
        try {
            binder.writeBean(binder.getBean());
            fireEvent(new SaveEvent(this, binder.getBean()));
        } catch (ValidationException e) {
            // Validation errors will be shown on the form
        }
    }

    // Events
    public static abstract class ConfigFormEvent extends ComponentEvent<ConfigForm> {
        private final ConfigPropertyResponse config;

        protected ConfigFormEvent(ConfigForm source, ConfigPropertyResponse config) {
            super(source, false);
            this.config = config;
        }

        public ConfigPropertyResponse getConfig() {
            return config;
        }
    }

    public static class SaveEvent extends ConfigFormEvent {
        public SaveEvent(ConfigForm source, ConfigPropertyResponse config) {
            super(source, config);
        }
    }

    public static class DeleteEvent extends ConfigFormEvent {
        public DeleteEvent(ConfigForm source, ConfigPropertyResponse config) {
            super(source, config);
        }
    }

    public static class CloseEvent extends ConfigFormEvent {
        public CloseEvent(ConfigForm source) {
            super(source, null);
        }
    }

    public static class EncryptEvent extends ComponentEvent<ConfigForm> {
        private final String plaintext;

        public EncryptEvent(ConfigForm source, String plaintext) {
            super(source, false);
            this.plaintext = plaintext;
        }

        public String getPlaintext() {
            return plaintext;
        }
    }

    public Registration addSaveListener(ComponentEventListener<SaveEvent> listener) {
        return addListener(SaveEvent.class, listener);
    }

    public Registration addDeleteListener(ComponentEventListener<DeleteEvent> listener) {
        return addListener(DeleteEvent.class, listener);
    }

    public Registration addCloseListener(ComponentEventListener<CloseEvent> listener) {
        return addListener(CloseEvent.class, listener);
    }

    public Registration addEncryptListener(ComponentEventListener<EncryptEvent> listener) {
        return addListener(EncryptEvent.class, listener);
    }
}
