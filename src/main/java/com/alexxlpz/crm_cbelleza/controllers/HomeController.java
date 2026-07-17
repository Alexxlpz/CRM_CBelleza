package com.alexxlpz.crm_cbelleza.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/")
    public String home() {
        // Forward to the static index.html in src/main/resources/static
        return "forward:/index.html";
    }
}
