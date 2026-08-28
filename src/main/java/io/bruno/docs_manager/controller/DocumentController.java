package io.bruno.docs_manager.controller;

import io.bruno.docs_manager.dto.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/documents")
public class DocumentController {

    @GetMapping
    public String getStatus() {
        return "ALIVE";
    }

    @PostMapping
    public ResponseEntity<Document> create(@RequestBody Document document) {
        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }
}
