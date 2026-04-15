package com.seafood.admin.views.product;

import com.seafood.admin.client.ProductResponse;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.shared.Registration;

public class ProductForm extends FormLayout {
    TextField name = new TextField("商品名称");
    TextField description = new TextField("商品描述");
    BigDecimalField price = new BigDecimalField("价格");
    IntegerField stock = new IntegerField("库存");
    ComboBox<String> category = new ComboBox<>("分类");
    TextField imageUrl = new TextField("图片地址");
    com.vaadin.flow.component.checkbox.Checkbox onSale = new com.vaadin.flow.component.checkbox.Checkbox("上架销售");

    Button save = new Button("保存");
    Button delete = new Button("删除");
    Button close = new Button("取消");

    Binder<ProductResponse> binder = new BeanValidationBinder<>(ProductResponse.class);

    public ProductForm() {
        addClassName("product-form");
        binder.bindInstanceFields(this);

        category.setItems("鱼类", "虾蟹", "贝类", "活鲜");

        add(name, description, price, stock, category, imageUrl, onSale, createButtonsLayout());
    }

    private HorizontalLayout createButtonsLayout() {
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        save.addClickShortcut(Key.ENTER);
        close.addClickShortcut(Key.ESCAPE);

        save.addClickListener(event -> validateAndSave());
        delete.addClickListener(event -> fireEvent(new DeleteEvent(this, binder.getBean())));
        close.addClickListener(event -> fireEvent(new CloseEvent(this)));

        return new HorizontalLayout(save, delete, close);
    }

    private void validateAndSave() {
        if (binder.isValid()) {
            fireEvent(new SaveEvent(this, binder.getBean()));
        }
    }

    public void setProduct(ProductResponse product) {
        binder.setBean(product);
    }

    // Events
    public static abstract class ProductFormEvent extends ComponentEvent<ProductForm> {
        private ProductResponse product;

        protected ProductFormEvent(ProductForm source, ProductResponse product) {
            super(source, false);
            this.product = product;
        }

        public ProductResponse getProduct() {
            return product;
        }
    }

    public static class SaveEvent extends ProductFormEvent {
        SaveEvent(ProductForm source, ProductResponse product) {
            super(source, product);
        }
    }

    public static class DeleteEvent extends ProductFormEvent {
        DeleteEvent(ProductForm source, ProductResponse product) {
            super(source, product);
        }
    }

    public static class CloseEvent extends ProductFormEvent {
        CloseEvent(ProductForm source) {
            super(source, null);
        }
    }

    public Registration addDeleteListener(ComponentEventListener<DeleteEvent> listener) {
        return addListener(DeleteEvent.class, listener);
    }

    public Registration addSaveListener(ComponentEventListener<SaveEvent> listener) {
        return addListener(SaveEvent.class, listener);
    }

    public Registration addCloseListener(ComponentEventListener<CloseEvent> listener) {
        return addListener(CloseEvent.class, listener);
    }
}
