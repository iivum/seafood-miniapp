package com.seafood.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RootController {

    @GetMapping("/")
    public String redirectToAdmin(RedirectAttributes attributes) {
        return "redirect:/admin/";
    }
}
