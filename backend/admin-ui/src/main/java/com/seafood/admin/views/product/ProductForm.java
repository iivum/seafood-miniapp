package com.seafood.admin.views.product;

import com.seafood.admin.client.ProductResponse;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.shared.Registration;

public class ProductForm extends FormLayout {
    TextField name = new TextField("商品名称");
    TextField description = new TextField("商品描述");
    BigDecimalField price = new BigDecimalField("价格");
    IntegerField stock = new IntegerField("库存");
    ComboBox<String> category = new ComboBox<>("分类");
    TextField imageUrl = new TextField("图片地址");
    Div imagePreview = new Div();
    com.vaadin.flow.component.checkbox.Checkbox onSale = new com.vaadin.flow.component.checkbox.Checkbox("上架销售");

    Button save = new Button("保存");
    Button delete = new Button("删除");
    Button close = new Button("取消");

    Binder<ProductResponse> binder = new BeanValidationBinder<>(ProductResponse.class);

    public ProductForm() {
        addClassName("product-form");
        binder.bindInstanceFields(this);

        category.setItems("鱼类", "虾蟹", "贝类", "藻类");

        // Image preview
        imagePreview.setWidth("200px");
        imagePreview.setHeight("150px");
        imagePreview.getStyle()
            .set("border", "1px solid #dce9f0")
            .set("border-radius", "12px")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("background", "#f8fbfd")
            .set("overflow", "hidden")
            .set("margin-top", "8px");
        imagePreview.getElement().setProperty("innerHTML", "<span style='color:#8aaabf;font-size:0.85rem'>图片预览</span>");

        // Update image preview when URL changes
        imageUrl.addValueChangeListener(e -> {
            String url = e.getValue();
            if (url != null && !url.isBlank()) {
                updateImagePreview(url);
            } else {
                imagePreview.getElement().setProperty("innerHTML", "<span style='color:#8aaabf;font-size:0.85rem'>图片预览</span>");
            }
        });

        add(name, description, price, stock, category, imageUrl, imagePreview, onSale, createButtonsLayout());
    }

    private void updateImagePreview(String url) {
        try {
            Image img = new Image(url, "商品图片");
            img.setWidth("100%");
            img.setHeight("100%");
            img.getElement().getStyle().set("object-fit", "cover");
            imagePreview.removeAll();
            imagePreview.add(img);
        } catch (Exception e) {
            imagePreview.getElement().setProperty("innerHTML", "<span style='color:#c62828;font-size:0.85rem'>图片加载失败</span>");
        }
    }

    private HorizontalLayout createButtonsLayout() {
        // Gradient save button with shadow and hover effect
        save.getStyle()
            .set("background", "linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%)")
            .set("box-shadow", "0 4px 12px rgba(255, 107, 107, 0.35)")
            .set("border-radius", "8px");
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
        // Validate required fields
        if (name.isEmpty()) {
            name.setErrorMessage("商品名称不能为空");
            name.setInvalid(true);
            return;
        }
        name.setInvalid(false);
        
        if (price.isEmpty()) {
            price.setErrorMessage("价格不能为空");
            price.setInvalid(true);
            return;
        }
        price.setInvalid(false);
        
        if (stock.isEmpty()) {
            stock.setErrorMessage("库存不能为空");
            stock.setInvalid(true);
            return;
        }
        stock.setInvalid(false);
        
        if (category.isEmpty()) {
            category.setErrorMessage("分类不能为空");
            category.setInvalid(true);
            return;
        }
        category.setInvalid(false);

        if (binder.isValid()) {
            fireEvent(new SaveEvent(this, binder.getBean()));
        }
    }

    public void setProduct(ProductResponse product) {
        binder.setBean(product);
        // Update image preview if URL exists
        if (product != null && product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            updateImagePreview(product.getImageUrl());
        } else {
            imagePreview.getElement().setProperty("innerHTML", "<span style='color:#8aaabf;font-size:0.85rem'>图片预览</span>");
        }
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
