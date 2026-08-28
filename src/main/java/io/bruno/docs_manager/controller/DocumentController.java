package io.bruno.docs_manager.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/docs")
public class DocumentController {

    @GetMapping
    public String getStatus() {
        return "ALIVE";
    }

}
