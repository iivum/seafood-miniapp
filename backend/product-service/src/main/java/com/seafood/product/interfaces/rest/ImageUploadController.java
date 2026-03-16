package com.seafood.product.interfaces.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/products/images")
public class ImageUploadController {

    private final String uploadDir = "uploads/images";

    public ImageUploadController() throws IOException {
        Path path = Paths.get(uploadDir);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path filePath = Paths.get(uploadDir, fileName);
            Files.copy(file.getInputStream(), filePath);

            Map<String, String> response = new HashMap<>();
            // 在实际生产中，这里应该返回一个 CDN 或 静态资源服务的 URL
            response.put("url", "/api/products/images/" + fileName);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{fileName}")
    public ResponseEntity<byte[]> getImage(@PathVariable String fileName) throws IOException {
        Path filePath = Paths.get(uploadDir, fileName);
        if (Files.exists(filePath)) {
            byte[] image = Files.readAllBytes(filePath);
            return ResponseEntity.ok().contentType(org.springframework.http.MediaType.IMAGE_JPEG).body(image);
        }
        return ResponseEntity.notFound().build();
    }
}
