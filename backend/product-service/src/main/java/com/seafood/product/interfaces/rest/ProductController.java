package com.seafood.product.interfaces.rest;

import com.seafood.product.application.ProductApplicationService;
import com.seafood.product.domain.model.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Product Management", description = "APIs for managing seafood products")
public class ProductController {
    private final ProductApplicationService productApplicationService;

    @PostMapping
    @Operation(
        summary = "Create a new product",
        description = "Creates a new seafood product with the provided details",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Product details",
            required = true,
            content = @Content(schema = @Schema(implementation = CreateProductRequest.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
        }
    )
    public ResponseEntity<Product> createProduct(@RequestBody CreateProductRequest request) {
        Product product = productApplicationService.createProduct(request.getName(), request.getDescription(), request.getPrice(), request.getStock(), request.getCategory(), request.getImageUrl());
        return ResponseEntity.ok(product);
    }

    @GetMapping
    @Operation(
        summary = "List all products",
        description = "Returns a list of all available seafood products",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successful operation", content = @Content(schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "204", description = "No products found")
        }
    )
    public ResponseEntity<List<Product>> listAllProducts() {
        return ResponseEntity.ok(productApplicationService.listAllProducts());
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get product by ID",
        description = "Returns a specific product by its ID",
        parameters = @Parameter(name = "id", description = "Product ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
        }
    )
    public ResponseEntity<Product> getProductById(@PathVariable String id) {
        return productApplicationService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{category}")
    @Operation(
        summary = "Find products by category",
        description = "Returns all products in a specific category",
        parameters = @Parameter(name = "category", description = "Product category", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Products found"),
            @ApiResponse(responseCode = "204", description = "No products found in this category")
        }
    )
    public ResponseEntity<List<Product>> findByCategory(@PathVariable String category) {
        return ResponseEntity.ok(productApplicationService.findByCategory(category));
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a product",
        description = "Deletes a product by its ID",
        parameters = @Parameter(name = "id", description = "Product ID", required = true),
        responses = {
            @ApiResponse(responseCode = "204", description = "Product deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
        }
    )
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        productApplicationService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}