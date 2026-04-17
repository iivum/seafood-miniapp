package com.seafood.admin.views.order;

import com.seafood.admin.client.ShipOrderRequest;
import com.seafood.admin.service.OrderService;
import com.seafood.admin.client.OrderResponse;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import java.util.Arrays;
import java.util.List;

public class ShipOrderDialog extends Dialog {
    private final OrderService orderService;
    private final OrderResponse order;
    private final Runnable onSuccess;

    private final ComboBox<CarrierOption> carrierSelect = new ComboBox<>("物流公司");
    private final TextField trackingNumberField = new TextField("运单号");
    private final Button shipButton = new Button("确认发货");
    private final Button cancelButton = new Button("取消");

    private static final List<CarrierOption> CARRIERS = Arrays.asList(
        new CarrierOption("SF", "顺丰速运"),
        new CarrierOption("EMS", "邮政EMS"),
        new CarrierOption("ZTO", "中通快递"),
        new CarrierOption("STO", "申通快递"),
        new CarrierOption("YTO", "圆通速递"),
        new CarrierOption("JD", "京东物流"),
        new CarrierOption("YUNDA", "韵达快递"),
        new CarrierOption("TTK", "天天快递")
    );

    public ShipOrderDialog(OrderService orderService, OrderResponse order, Runnable onSuccess) {
        this.orderService = orderService;
        this.order = order;
        this.onSuccess = onSuccess;

        setWidth("400px");
        setHeaderTitle("订单发货 - " + truncateId(order.getId()));

        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);

        carrierSelect.setItems(CARRIERS);
        carrierSelect.setItemLabelGenerator(CarrierOption::getName);
        carrierSelect.setRequired(true);
        carrierSelect.setWidthFull();

        trackingNumberField.setRequired(true);
        trackingNumberField.setWidthFull();

        shipButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        shipButton.addClickListener(e -> handleShip());

        cancelButton.addClickListener(e -> close());

        content.add(new H3("选择物流公司并填写运单号"), carrierSelect, trackingNumberField);
        add(content);
        getFooter().add(cancelButton, shipButton);
    }

    private void handleShip() {
        CarrierOption selectedCarrier = carrierSelect.getValue();
        String trackingNumber = trackingNumberField.getValue();

        if (selectedCarrier == null) {
            Notification.show("请选择物流公司", 3000, Notification.Position.MIDDLE);
            return;
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            Notification.show("请填写运单号", 3000, Notification.Position.MIDDLE);
            return;
        }

        try {
            ShipOrderRequest request = new ShipOrderRequest(
                selectedCarrier.getCode(),
                selectedCarrier.getName(),
                trackingNumber
            );
            orderService.shipOrder(order.getId(), request);
            Notification.show("订单已发货: " + truncateId(order.getId()), 3000, Notification.Position.MIDDLE);
            close();
            onSuccess.run();
        } catch (Exception ex) {
            Notification.show("发货失败: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }

    private String truncateId(String id) {
        if (id == null || id.length() <= 12) {
            return id;
        }
        return id.substring(0, 12) + "...";
    }

    public static class CarrierOption {
        private final String code;
        private final String name;

        public CarrierOption(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }
}
