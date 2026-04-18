package com.seafood.admin.views.product;

import com.seafood.admin.client.CreateProductRequest;
import com.seafood.admin.client.ProductClient;
import com.seafood.admin.client.ProductResponse;
import com.seafood.admin.views.main.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "products", layout = MainLayout.class)
@PageTitle("商品管理 | 海鲜商城")
public class ProductListView extends VerticalLayout {

    private final ProductClient productClient;
    private final Grid<ProductResponse> grid = new Grid<>(ProductResponse.class);
    private final ProductForm form;
    private TextField searchField = new TextField();

    @Autowired
    public ProductListView(ProductClient productClient) {
        this.productClient = productClient;
        this.form = new ProductForm();

        setSizeFull();
        configureGrid();
        configureForm();

        add(new H2("商品管理"), getToolbar(), getContent());
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

    private void configureForm() {
        form.setWidth("25em");
        form.addSaveListener(this::saveProduct);
        form.addDeleteListener(this::deleteProduct);
        form.addCloseListener(e -> closeEditor());
    }

    private void saveProduct(ProductForm.SaveEvent event) {
        ProductResponse product = event.getProduct();
        CreateProductRequest request = new CreateProductRequest();
        request.setName(product.getName());
        request.setDescription(product.getDescription());
        request.setPrice(product.getPrice());
        request.setStock(product.getStock());
        request.setCategory(product.getCategory());
        request.setImageUrl(product.getImageUrl());
        request.setOnSale(product.isOnSale());

        if (product.getId() == null || product.getId().isEmpty()) {
            productClient.createProduct(request);
            Notification.show("商品创建成功");
        } else {
            productClient.updateProduct(product.getId(), request);
            Notification.show("商品更新成功");
        }
        updateList();
        closeEditor();
    }

    private void deleteProduct(ProductForm.DeleteEvent event) {
        productClient.deleteProduct(event.getProduct().getId());
        Notification.show("商品已删除");
        updateList();
        closeEditor();
    }

    private void configureGrid() {
        grid.setSizeFull();
        
        // Add ID column first for distinguishing same-name products
        grid.addColumn(ProductResponse::getId).setHeader("商品ID").setAutoWidth(true).setSortable(true);
        grid.addColumn("name").setHeader("商品名称").setAutoWidth(true).setSortable(true);
        grid.addColumn("category").setHeader("分类").setAutoWidth(true).setSortable(true);
        grid.addColumn("price").setHeader("价格").setAutoWidth(true).setSortable(true);
        grid.addColumn("stock").setHeader("库存").setAutoWidth(true).setSortable(true);
        grid.addColumn("onSale").setHeader("上架").setAutoWidth(true);

        grid.getColumns().forEach(col -> col.setAutoWidth(true));

        // Enable sorting
        grid.setSortableColumns("id", "name", "category", "price", "stock");

        grid.asSingleSelect().addValueChangeListener(event -> editProduct(event.getValue()));
    }

    private HorizontalLayout getToolbar() {
        Button addProductButton = new Button("添加商品");
        addProductButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addProductButton.addClickListener(click -> addProduct());

        // Search field
        searchField.setPlaceholder("搜索商品...");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> updateList());

        HorizontalLayout toolbar = new HorizontalLayout(addProductButton, searchField);
        toolbar.addClassName("toolbar");
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.START);
        return toolbar;
    }

    public void editProduct(ProductResponse product) {
        if (product == null) {
            closeEditor();
        } else {
            form.setProduct(product);
            form.setVisible(true);
            addClassName("editing");
        }
    }

    private void closeEditor() {
        form.setProduct(null);
        form.setVisible(false);
        removeClassName("editing");
    }

    private void addProduct() {
        grid.asSingleSelect().clear();
        editProduct(new ProductResponse());
    }

    private void updateList() {
        var products = productClient.getAllProducts();
        
        // Filter by search term
        String searchTerm = searchField.getValue();
        if (searchTerm != null && !searchTerm.isBlank()) {
            String lowerSearch = searchTerm.toLowerCase();
            products = products.stream()
                .filter(p -> 
                    (p.getName() != null && p.getName().toLowerCase().contains(lowerSearch)) ||
                    (p.getCategory() != null && p.getCategory().toLowerCase().contains(lowerSearch)) ||
                    (p.getId() != null && p.getId().toLowerCase().contains(lowerSearch)))
                .collect(java.util.stream.Collectors.toList());
        }
        
        grid.setItems(products);
    }
}
