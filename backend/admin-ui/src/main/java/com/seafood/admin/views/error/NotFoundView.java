package com.seafood.admin.views.error;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.HasErrorParameter;
import com.vaadin.flow.component.button.ButtonVariant;

public class NotFoundView extends VerticalLayout implements HasErrorParameter<NotFoundView.NotFoundException> {

    @Override
    public int setErrorParameter(BeforeEnterEvent event, ErrorParameter<NotFoundException> parameter) {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        getStyle()
            .set("background", "linear-gradient(135deg, #1A1A2E 0%, #2D3452 50%, #4ECDC4 100%)")
            .set("min-height", "100vh");

        Div container = new Div();
        container.getStyle()
            .set("text-align", "center")
            .set("padding", "40px")
            .set("background", "rgba(255,255,255,0.12)")
            .set("border-radius", "16px")
            .set("border", "1px solid rgba(255,255,255,0.15)")
            .set("backdrop-filter", "blur(10px)")
            .set("box-shadow", "0 8px 32px rgba(0,0,0,0.2)");

        H1 errorCode = new H1("404");
        errorCode.getStyle()
            .set("font-size", "6rem")
            .set("font-weight", "800")
            .set("color", "#ffffff")
            .set("margin", "0")
            .set("text-shadow", "2px 2px 4px rgba(0,0,0,0.3)");

        Div errorTitle = new Div();
        errorTitle.setText("页面未找到");
        errorTitle.getStyle()
            .set("font-size", "1.5rem")
            .set("font-weight", "600")
            .set("color", "rgba(255,255,255,0.9)")
            .set("margin", "16px 0");

        Paragraph errorDesc = new Paragraph("抱歉，您访问的页面不存在或已被移除");
        errorDesc.getStyle()
            .set("font-size", "0.95rem")
            .set("color", "rgba(255,255,255,0.7)")
            .set("margin", "0 0 24px 0");

        Button homeButton = new Button("返回管理后台首页");
        homeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        homeButton.getStyle()
            .set("padding", "12px 24px")
            .set("font-size", "1rem");
        homeButton.addClickListener(e -> {
            getUI().ifPresent(ui -> ui.navigate("/admin/dashboard"));
        });

        container.add(errorCode, errorTitle, errorDesc, homeButton);
        add(container);
        
        return 0; // Indicates the error was handled
    }

    public static class NotFoundException extends Exception {
        public NotFoundException() {
            super("Page not found");
        }
    }
}
